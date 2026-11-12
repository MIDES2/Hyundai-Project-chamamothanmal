#requires -Version 5.1
[CmdletBinding()]
param([string]$JdkHome = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
if ($JdkHome) {
    $compiler = Join-Path $JdkHome 'bin/javac.exe'
    $runtime = Join-Path $JdkHome 'bin/java.exe'
}
else {
    $compiler = (Get-Command javac -ErrorAction Stop).Source
    $runtime = Join-Path (Split-Path $compiler) 'java.exe'
}
if (-not (Test-Path -LiteralPath $compiler) -or
    -not (Test-Path -LiteralPath $runtime)) {
    throw 'Specify a JDK 11+ directory with -JdkHome, or configure JAVA_HOME.'
}

$source = Join-Path $PSScriptRoot 'src/main/java/ai/malttuk/security/MessageAccessPolicy.java'
$test = Join-Path $PSScriptRoot 'src/test/java/ai/malttuk/security/MessageAccessPolicyTest.java'
# build/ is ignored by the repository. Each invocation gets a separate directory.
$output = Join-Path $PSScriptRoot ('build/test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $output -Force | Out-Null
& $compiler --release 11 -encoding UTF-8 -Xlint:all -Werror -d $output $source $test
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
& $runtime -cp $output ai.malttuk.security.MessageAccessPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Message access policy tests failed.' }
