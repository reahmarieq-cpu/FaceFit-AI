param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AdbPath="adb",
    [string]$OutputDirectory=(Join-Path $PSScriptRoot "captures")
)
$ErrorActionPreference="Stop"
if ($Serial -notmatch '^[A-Za-z0-9._:-]+$') { throw "Invalid device serial" }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp=Get-Date -Format "yyyyMMdd-HHmmss"
$remoteConfig="/data/local/tmp/facefit-vto-timing.pbtxt"
$remoteTrace="/data/misc/perfetto-traces/facefit-vto-$stamp.pftrace"
function Invoke-Adb([string[]]$Arguments) {
    & $AdbPath -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $($Arguments[0])" }
}
$previousLogLevel=(& $AdbPath -s $Serial shell getprop log.tag.FaceFitVTOTiming).Trim()
if ($previousLogLevel -notmatch '^(VERBOSE|DEBUG|INFO|WARN|ERROR|ASSERT|SUPPRESS|V|D|I|W|E|A|S)$') { $previousLogLevel="INFO" }
try {
    Invoke-Adb -Arguments @("shell","setprop","log.tag.FaceFitVTOTiming","VERBOSE")
    Invoke-Adb -Arguments @("push",(Join-Path $PSScriptRoot "frametimeline.pbtxt"),$remoteConfig)
    Write-Host "Reopen VTO now to start the second diagnostic session. Keep your face visible."
    Read-Host "Press Enter when ready to record 15 seconds" | Out-Null
    Invoke-Adb -Arguments @("shell","perfetto","--txt","-c",$remoteConfig,"-o",$remoteTrace)
    Invoke-Adb -Arguments @("pull",$remoteTrace,(Join-Path $OutputDirectory "$stamp.pftrace"))
    & $AdbPath -s $Serial logcat -d -s FaceFitVTOTiming FaceFitVTODiag |
        Set-Content -LiteralPath (Join-Path $OutputDirectory "$stamp.log")
    if ($LASTEXITCODE -ne 0) { throw "Logcat failed" }
} finally {
    & $AdbPath -s $Serial shell setprop log.tag.FaceFitVTOTiming $previousLogLevel | Out-Null
}
