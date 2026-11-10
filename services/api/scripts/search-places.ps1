param(
    [Parameter(Mandatory=$true)][string]$Query,
    [ValidateSet('keyword','address')][string]$Type='keyword',
    [ValidateRange(1,45)][int]$Page=1,
    [ValidateRange(1,15)][int]$Size=10,
    [string]$BaseUrl='http://127.0.0.1:8000'
)
$ErrorActionPreference='Stop'
$apiRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$config=@{}
foreach ($envPath in @((Join-Path $apiRoot '../../.env'),(Join-Path $apiRoot '.env'))) {
    if (Test-Path -LiteralPath $envPath) {
        foreach ($line in [IO.File]::ReadAllLines($envPath)) {
            if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $config[$Matches[1]]=$Matches[2] }
        }
    }
}
$token=[Environment]::GetEnvironmentVariable('API_SENDER_TOKEN')
if (-not $token) { $token=$config['API_SENDER_TOKEN'] }
if (-not $token) { throw 'Configure a local API_SENDER_TOKEN, or set it to your signed-in access_token for this process.' }
$url=$BaseUrl.TrimEnd('/')+'/api/v2/places?query='+[Uri]::EscapeDataString($Query)+'&type='+$Type+'&page='+$Page+'&size='+$Size
try {
    Invoke-RestMethod -Uri $url -Headers @{Authorization="Bearer $token"}
} catch {
    throw 'Place search failed. Check server restart, user token and the server-only KAKAO_REST_API_KEY. No provider key was printed.'
}
