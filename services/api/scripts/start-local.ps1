$ErrorActionPreference = 'Stop'
$apiRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $env:JAVA_HOME) {
    foreach ($candidate in @('C:\Program Files\Java\jdk-21', 'C:\Program Files\Android\Android Studio\jbr')) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe')) {
            $env:JAVA_HOME = $candidate
            break
        }
    }
}
Push-Location $apiRoot
try {
    & (Join-Path $apiRoot 'gradlew.bat') bootRun
    if ($LASTEXITCODE -ne 0) { throw 'Server stopped with an error. Check JDK, local .env, port and build output.' }
} finally { Pop-Location }
