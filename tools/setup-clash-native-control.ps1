$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$clashDirectory = $env:CODEX_POCKET_CLASH_DIR
if (!$clashDirectory) { $clashDirectory = Join-Path $env:APPDATA 'io.github.clash-verge-rev.clash-verge-rev' }
$configPath = Join-Path $clashDirectory 'verge.yaml'
$configText = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8
$shortcut = 'toggle_system_proxy,Control+Alt+Shift+F24'
if ($configText.Contains($shortcut)) { Write-Output 'Native control shortcut is already configured.'; exit 0 }
if ($configText -match 'Control\+Alt\+Shift\+F24') { throw 'The dedicated shortcut is already assigned to another action.' }
if ($configText -match '(?m)^enable_global_hotkey:[ \t]*false') { throw 'Global shortcuts are disabled in Clash Verge.' }
Add-Type -Path (Join-Path $PSScriptRoot 'ClashNativeControl.cs')
$session = [System.Diagnostics.Process]::GetCurrentProcess().SessionId
$verge = Get-Process -Name 'clash-verge' -ErrorAction SilentlyContinue | Where-Object { $_.SessionId -eq $session } | Select-Object -First 1
$executable = if ($verge) { $verge.Path } else { $env:CLASH_VERGE_EXE }
if (!$executable) { throw 'Start Clash Verge first, or set CLASH_VERGE_EXE to your installation path.' }
if (!(Test-Path -LiteralPath $executable)) { throw 'Cannot find Clash Verge executable.' }
if ($verge) {
    $window = [CodexPocket.ClashNativeControl]::FindHotkeyWindow($verge.Id)
    if ($window -eq [IntPtr]::Zero) { throw 'Cannot find the Clash Verge background event window.' }
    [CodexPocket.ClashNativeControl]::RequestExit($window)
    if (!$verge.WaitForExit(10000)) { throw 'Clash Verge did not exit; no configuration was changed.' }
}
$configText = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8
$statePath = Join-Path $projectPath 'state'
New-Item -ItemType Directory -Path $statePath -Force | Out-Null
$backupPath = Join-Path $statePath ('verge-before-native-control-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.yaml')
Copy-Item -LiteralPath $configPath -Destination $backupPath
if ($configText -match '(?m)^hotkeys:[ \t]*(null|\[\])?[ \t]*\r?$') {
    $configText = [regex]::Replace($configText, '(?m)^hotkeys:[ \t]*(null|\[\])?[ \t]*\r?$', "hotkeys:`n- $shortcut")
} elseif ($configText -match '(?m)^hotkeys:') {
    Start-Process -FilePath $executable -WindowStyle Hidden | Out-Null
    throw 'Unsupported inline shortcut configuration; the app was restarted without changes.'
} else { $configText = $configText.TrimEnd() + "`nhotkeys:`n- $shortcut`n" }
try {
    [System.IO.File]::WriteAllText($configPath, $configText, (New-Object System.Text.UTF8Encoding($false)))
    Start-Process -FilePath $executable -WindowStyle Hidden | Out-Null
    Write-Output 'Clash Verge restarted with native proxy control configured.'
} catch {
    Copy-Item -LiteralPath $backupPath -Destination $configPath -Force
    Start-Process -FilePath $executable -WindowStyle Hidden | Out-Null
    throw
}
