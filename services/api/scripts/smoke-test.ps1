param([string]$BaseUrl = 'http://127.0.0.1:8000')
$ErrorActionPreference = 'Stop'
$apiRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $env:JAVA_HOME) {
    foreach ($candidate in @('C:\Program Files\Java\jdk-21', 'C:\Program Files\Android\Android Studio\jbr')) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe')) { $env:JAVA_HOME=$candidate; break }
    }
}
& (Join-Path $PSScriptRoot 'make-sample.ps1')
Push-Location $apiRoot
try {
    & (Join-Path $apiRoot 'gradlew.bat') ':integration-client:run' "--args=$BaseUrl"
    if ($LASTEXITCODE -ne 0) { throw 'V2 integration failed. Check server version, URL, account settings and build output.' }
} finally { Pop-Location }
