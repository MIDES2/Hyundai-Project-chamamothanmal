param([string]$BaseUrl = 'http://127.0.0.1:8000')
$ErrorActionPreference = 'Stop'
$apiRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repositoryRoot = (Resolve-Path (Join-Path $apiRoot '../..')).Path
$config = @{}
# Match Spring's root .env -> module .env precedence. OS variables override files.
foreach ($envPath in @((Join-Path $repositoryRoot '.env'), (Join-Path $apiRoot '.env'))) {
    if (Test-Path -LiteralPath $envPath) {
        foreach ($line in [IO.File]::ReadAllLines($envPath)) {
            if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $config[$Matches[1]] = $Matches[2] }
        }
    }
}
foreach ($key in @('API_SENDER_TOKEN', 'API_RECIPIENT_TOKEN', 'API_OTHER_RECIPIENT_TOKEN')) {
    $osValue = [Environment]::GetEnvironmentVariable($key)
    if ($osValue) { $config[$key] = $osValue }
    if (-not $config[$key]) { throw "Missing local $key. Run init-local.ps1 first." }
}
& (Join-Path $PSScriptRoot 'make-sample.ps1')
$sample = Join-Path $apiRoot 'uploads/sample.wav'
$output = Join-Path $apiRoot 'uploads/smoke-response.json'
$download = Join-Path $apiRoot 'uploads/smoke-download.wav'
$health = Invoke-RestMethod -Uri "$BaseUrl/health"
if ($health.status -ne 'UP') { throw 'Server health failed.' }
$requestId = [guid]::NewGuid().ToString()
$today = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, 'Korea Standard Time').ToString('yyyy-MM-dd')
function Submit-Sample {
    $status = & curl.exe --silent --show-error --output $output --write-out '%{http_code}' `
        --header "Authorization: Bearer $($config['API_SENDER_TOKEN'])" `
        --header "Idempotency-Key: $requestId" `
        --form 'recipientId=demo-recipient' --form 'placeName=Synthetic test place' `
        --form 'latitude=37.0' --form 'longitude=127.0' --form "scheduledDate=$today" `
        --form "audio=@$sample;type=audio/wav" "$BaseUrl/api/v1/messages"
    if ($LASTEXITCODE -ne 0) { throw 'Upload transport failed.' }
    return $status
}
if ((Submit-Sample) -ne '201') { throw 'Upload did not return 201. Inspect the local smoke-response.json.' }
$created = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
$id = $created.messageId
if ((Submit-Sample) -ne '200') { throw 'Retry was not idempotent.' }
$recipientHeaders = @{ Authorization = "Bearer $($config['API_RECIPIENT_TOKEN'])" }
$inbox = Invoke-RestMethod -Uri "$BaseUrl/api/v1/messages" -Headers $recipientHeaders
if (-not ($inbox.items.messageId -contains $id)) { throw 'Registered ID is not in the recipient inbox.' }
Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/v1/messages/$id/audio" -Headers $recipientHeaders -OutFile $download
if ((Get-FileHash -LiteralPath $sample).Hash -ne (Get-FileHash -LiteralPath $download).Hash) { throw 'Downloaded bytes differ.' }
$denied = & curl.exe --silent --show-error --output $output --write-out '%{http_code}' `
    --header "Authorization: Bearer $($config['API_OTHER_RECIPIENT_TOKEN'])" "$BaseUrl/api/v1/messages/$id/audio"
if ($LASTEXITCODE -ne 0 -or $denied -ne '404') { throw 'Other recipient access was not denied.' }
$startBody = @{eventId=[guid]::NewGuid().ToString();status='PLAYING';expectedVersion=0;source='MANUAL'} | ConvertTo-Json
$start = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/messages/$id/playback-events" -Headers $recipientHeaders -ContentType 'application/json' -Body $startBody
$completeBody = @{eventId=[guid]::NewGuid().ToString();status='COMPLETED';expectedVersion=$start.version;source='MANUAL'} | ConvertTo-Json
$complete = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/messages/$id/playback-events" -Headers $recipientHeaders -ContentType 'application/json' -Body $completeBody
$retry = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/messages/$id/playback-events" -Headers $recipientHeaders -ContentType 'application/json' -Body $completeBody
if ($complete.version -ne 2 -or $retry.version -ne 2) { throw 'Playback completion retry was not idempotent.' }
Write-Host 'PASS: register, inbox, exact audio download, unauthorized recipient denial, upload/event retries, playback completion.'
Write-Host 'Only synthetic test content was used. No credentials were printed.'
