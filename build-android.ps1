param(
    [string]$GradleExecutable = 'gradle',
    [string]$SdkRoot = $env:ANDROID_HOME,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if ($SdkRoot) { $env:ANDROID_HOME=$SdkRoot; $env:ANDROID_SDK_ROOT=$SdkRoot }
$buildArgs=@('--no-daemon','assembleRelease','lintRelease')
if ($Offline) { $buildArgs+='--offline' }
& $GradleExecutable @buildArgs
if ($LASTEXITCODE -ne 0) { throw 'Release build or lint failed' }
Write-Host 'Unsigned, non-debuggable APK: app/build/outputs/apk/release/app-release-unsigned.apk'
Write-Host 'Sign locally using your own private keystore before installing or publishing. Never commit signing keys.'
