#requires -Version 5.1
<#
.SYNOPSIS
Checks the draft message API: upload -> list -> download.
.DESCRIPTION
Uses synthetic test data. Creates one server message per invocation.
Tokens are read from process environment variables, never printed.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][uri]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$AudioPath,
    [Parameter(Mandatory = $true)][string]$AudioContentType,
    [Parameter(Mandatory = $true)][string]$RecipientId,
    [Parameter(Mandatory = $true)][string]$PlaceName,
    [Parameter(Mandatory = $true)][double]$Latitude,
    [Parameter(Mandatory = $true)][double]$Longitude,
    [Parameter(Mandatory = $true)][string]$ScheduledDate,
    [string]$SenderTokenEnv = 'MALTTUK_SENDER_TOKEN',
    [string]$RecipientTokenEnv = 'MALTTUK_RECIPIENT_TOKEN',
    [ValidateRange(1, 300)][int]$TimeoutSeconds = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

function Send-CheckedRequest {
    param(
        [System.Net.Http.HttpClient]$Client,
        [string]$Method,
        [string]$Url,
        [string]$Token,
        [int]$ExpectedStatus,
        [System.Net.Http.HttpContent]$Content
    )
    $script:observedStatus = 0
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method), $Url)
    $response = $null
    try {
        if (-not [string]::IsNullOrWhiteSpace($Token)) {
            $request.Headers.Authorization =
                [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
        }
        if ($null -ne $Content) { $request.Content = $Content }
        $response = $Client.SendAsync($request).GetAwaiter().GetResult()
        $script:observedStatus = [int]$response.StatusCode
        if ([int]$response.StatusCode -ne $ExpectedStatus) {
            throw "Expected HTTP $ExpectedStatus, received $([int]$response.StatusCode)."
        }
        # Return the live response. The caller disposes it after reading.
        $result = $response
        $response = $null
        return $result
    }
    finally {
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose()
    }
}

$client = $null
$multipart = $null
$response = $null
$stage = 'input validation'
$script:observedStatus = 0
$downloadPath = $null
try {
    if (-not $BaseUrl.IsAbsoluteUri -or $BaseUrl.Scheme -notin @('http', 'https') -or
        $BaseUrl.UserInfo -or $BaseUrl.Query -or $BaseUrl.Fragment) {
        throw 'BaseUrl must be an HTTP(S) server URL without credentials, query or fragment.'
    }
    if ([string]::IsNullOrWhiteSpace($RecipientId) -or
        [string]::IsNullOrWhiteSpace($PlaceName)) {
        throw 'RecipientId and PlaceName are required.'
    }
    if ([double]::IsNaN($Latitude) -or $Latitude -lt -90 -or $Latitude -gt 90 -or
        [double]::IsNaN($Longitude) -or $Longitude -lt -180 -or $Longitude -gt 180) {
        throw 'Coordinates are outside valid ranges.'
    }
    $date = [datetime]::MinValue
    if (-not [datetime]::TryParseExact($ScheduledDate, 'yyyy-MM-dd',
        [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::None, [ref]$date)) {
        throw 'ScheduledDate must be a valid yyyy-MM-dd date.'
    }
    $file = Get-Item -LiteralPath $AudioPath
    if ($file.PSIsContainer -or $file.Length -eq 0 -or $file.Length -gt 32MB) {
        throw 'Provide a nonempty sample audio file no larger than 32 MiB.'
    }
    $mediaType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($AudioContentType)
    if ($mediaType.MediaType -notlike 'audio/*' -and
        $mediaType.MediaType -ne 'application/ogg') {
        throw 'AudioContentType must be an audio MIME type or application/ogg.'
    }

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseCookies = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [timespan]::FromSeconds($TimeoutSeconds)
    $client.MaxResponseContentBufferSize = 32MB
    $senderToken = [Environment]::GetEnvironmentVariable($SenderTokenEnv)
    $recipientToken = [Environment]::GetEnvironmentVariable($RecipientTokenEnv)
    $messagesUrl = $BaseUrl.AbsoluteUri.TrimEnd('/') + '/api/v1/messages'

    $stage = 'register'
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $fields = [ordered]@{
        recipientId = $RecipientId
        placeName = $PlaceName
        latitude = $Latitude.ToString([Globalization.CultureInfo]::InvariantCulture)
        longitude = $Longitude.ToString([Globalization.CultureInfo]::InvariantCulture)
        scheduledDate = $ScheduledDate
    }
    foreach ($field in $fields.GetEnumerator()) {
        $multipart.Add([System.Net.Http.StringContent]::new([string]$field.Value), $field.Key)
    }
    $audio = [System.Net.Http.StreamContent]::new([IO.File]::OpenRead($file.FullName))
    # Attach immediately so disposing multipart also closes the file on failure.
    $multipart.Add($audio, 'audio', 'sample' + $file.Extension)
    $audio.Headers.ContentType = $mediaType
    $response = Send-CheckedRequest -Client $client -Method POST -Url $messagesUrl -Token $senderToken -ExpectedStatus 201 -Content $multipart
    $created = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    $idProperty = $created.PSObject.Properties['messageId']
    if ($null -eq $idProperty -or $idProperty.Value -isnot [string] -or
        [string]::IsNullOrWhiteSpace($idProperty.Value)) {
        throw 'Registration response must contain a nonempty string messageId.'
    }
    $messageId = $idProperty.Value
    $response.Dispose()
    $response = $null
    Write-Output '[PASS] register: HTTP 201 and messageId'

    $stage = 'list'
    $response = Send-CheckedRequest -Client $client -Method GET -Url $messagesUrl -Token $recipientToken -ExpectedStatus 200
    $listing = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    $itemsProperty = $listing.PSObject.Properties['items']
    if ($null -eq $itemsProperty -or $itemsProperty.Value -isnot [array]) {
        throw 'List response must contain an items array.'
    }
    $found = $false
    foreach ($item in $itemsProperty.Value) {
        if ($null -ne $item) {
            $property = $item.PSObject.Properties['messageId']
            if ($null -ne $property -and $property.Value -is [string] -and
                $property.Value -ceq $messageId) { $found = $true }
        }
    }
    if (-not $found) { throw 'New messageId was not found in recipient list.' }
    $response.Dispose()
    $response = $null
    Write-Output '[PASS] list: newly registered message found'

    $stage = 'download'
    $audioUrl = $messagesUrl + '/' + [uri]::EscapeDataString($messageId) + '/audio'
    $response = Send-CheckedRequest -Client $client -Method GET -Url $audioUrl -Token $recipientToken -ExpectedStatus 200
    $type = $response.Content.Headers.ContentType
    if ($null -eq $type -or ($type.MediaType -notlike 'audio/*' -and
        $type.MediaType -ne 'application/ogg')) {
        throw 'Download must declare an audio MIME type or application/ogg.'
    }
    $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    if ($bytes.Length -eq 0) { throw 'Downloaded audio is empty.' }
    $outputDir = Join-Path ([IO.Path]::GetTempPath()) ('malttuk-smoke-' + [guid]::NewGuid().ToString('N'))
    [IO.Directory]::CreateDirectory($outputDir) | Out-Null
    $downloadPath = Join-Path $outputDir 'downloaded-audio.bin'
    [IO.File]::WriteAllBytes($downloadPath, $bytes)
    Write-Output '[PASS] download: HTTP 200, audio MIME type, nonempty body'
    Write-Output ("Downloaded file: " + $downloadPath)
    Write-Output 'API flow passed. Vehicle playback remains a separate manual check.'
    exit 0
}
catch {
    # Do not print server bodies, URLs, tokens or exception internals.
    if ($null -ne $downloadPath -and (Test-Path -LiteralPath $downloadPath)) {
        Remove-Item -LiteralPath $downloadPath
    }
    [Console]::Error.WriteLine("[FAIL] $stage (last HTTP status: $script:observedStatus; 0 means no response). Check the draft contract, test credentials, connectivity and inputs.")
    exit 1
}
finally {
    if ($null -ne $response) { $response.Dispose() }
    if ($null -ne $multipart) { $multipart.Dispose() }
    if ($null -ne $client) { $client.Dispose() }
}
