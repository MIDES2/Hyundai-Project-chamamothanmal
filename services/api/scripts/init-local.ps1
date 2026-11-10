$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$localEnv = Join-Path $repositoryRoot '.env'
if (Test-Path -LiteralPath $localEnv) {
    Write-Host 'Existing root .env was preserved. Fill missing API_* values using .env.example.'
    exit 0
}
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}
$contents = [IO.File]::ReadAllText((Join-Path $repositoryRoot '.env.example'))
foreach ($key in @('API_DB_PASSWORD', 'API_SENDER_TOKEN', 'API_RECIPIENT_TOKEN', 'API_OTHER_RECIPIENT_TOKEN')) {
    $secret = New-LocalSecret
    $contents = [regex]::Replace($contents, ('(?m)^' + $key + '=\s*$'), ($key + '=' + $secret))
}
[IO.File]::WriteAllText($localEnv, $contents, (New-Object Text.UTF8Encoding($false)))
Write-Host 'Created private root .env with random prototype credentials. Values were not printed.'
