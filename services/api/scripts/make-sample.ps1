param([string]$OutputPath = (Join-Path $PSScriptRoot '../uploads/sample.wav'))
$ErrorActionPreference = 'Stop'
# One second of synthetic silence, PCM 16-bit mono 16kHz; no personal recording.
$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($resolvedOutput)) | Out-Null
$sampleRate = 16000
$payloadBytes = $sampleRate * 2
$stream = [IO.File]::Open($resolvedOutput, [IO.FileMode]::Create)
$writer = New-Object IO.BinaryWriter($stream)
try {
    $writer.Write([Text.Encoding]::ASCII.GetBytes('RIFF'))
    $writer.Write([int](36 + $payloadBytes))
    $writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
    $writer.Write([int]16)
    $writer.Write([int16]1)
    $writer.Write([int16]1)
    $writer.Write([int]$sampleRate)
    $writer.Write([int]($sampleRate * 2))
    $writer.Write([int16]2)
    $writer.Write([int16]16)
    $writer.Write([Text.Encoding]::ASCII.GetBytes('data'))
    $writer.Write([int]$payloadBytes)
    $writer.Write((New-Object byte[] $payloadBytes))
} finally { $writer.Dispose() }
Write-Host 'Created synthetic WAV in the local ignored uploads folder.'
