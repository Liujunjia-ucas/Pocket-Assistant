param([ValidateSet('Status', 'Enable', 'Disable')][string]$Action = 'Status')
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$settingsKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings'
$clashDirectory = $env:CODEX_POCKET_CLASH_DIR
if (!$clashDirectory) { $clashDirectory = Join-Path $env:APPDATA 'io.github.clash-verge-rev.clash-verge-rev' }
$configPath = Join-Path $clashDirectory 'verge.yaml'
function Read-Setting([string]$name) {
    $match = [regex]::Match($script:configText, '(?m)^' + [regex]::Escape($name) + ':[ \t]*([^#\r\n]+)')
    return $match.Groups[1].Value.Trim().Trim('"', "'")
}
function Read-Status {
    $script:configText = if (Test-Path -LiteralPath $configPath) { Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 } else { '' }
    $portText = Read-Setting 'verge_mixed_port'
    $proxyPort = if ($portText -match '^\d{1,5}$') { [int]$portText } else { 7897 }
    if ($proxyPort -lt 1 -or $proxyPort -gt 65535) { $proxyPort = 7897 }
    $endpoint = "127.0.0.1:$proxyPort"
    $proxyListening = $false
    $tcpClient = New-Object System.Net.Sockets.TcpClient
    try {
        $connection = $tcpClient.ConnectAsync('127.0.0.1', $proxyPort)
        if ($connection.Wait(500)) { $proxyListening = $tcpClient.Connected }
    } catch {} finally { $tcpClient.Dispose() }
    $current = Get-ItemProperty -LiteralPath $settingsKey
    $systemEnabled = [bool]$current.ProxyEnable
    $servers = @(([string]$current.ProxyServer).Split(';') | Where-Object { $_.Trim() } | ForEach-Object { ($_.Trim() -replace '^\w+=', '').ToLowerInvariant() })
    $isClashProxy = $servers.Count -gt 0 -and @($servers | Where-Object { $_ -ne $endpoint -and $_ -ne "localhost:$proxyPort" }).Count -eq 0
    $enabled = [bool]($systemEnabled -and $isClashProxy)
    $vergeEnabled = (Read-Setting 'enable_system_proxy') -eq 'true'
    $script:nativeWindow = [IntPtr]::Zero
    $reason = ''
    if (!$script:configText) { $reason = 'not_installed' }
    else {
        if (!("CodexPocket.ClashNativeControl" -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'ClashNativeControl.cs') }
        $session = [System.Diagnostics.Process]::GetCurrentProcess().SessionId
        $verge = Get-Process -Name 'clash-verge' -ErrorAction SilentlyContinue | Where-Object { $_.SessionId -eq $session } | Select-Object -First 1
        if ($verge) { $script:nativeWindow = [CodexPocket.ClashNativeControl]::FindHotkeyWindow($verge.Id) }
        $hasShortcut = $script:configText -match '(?m)^-[ \t]+["'']?toggle_system_proxy,Control\+Alt\+Shift\+F24["'']?[ \t]*\r?$'
        if ((Read-Setting 'proxy_auto_config') -eq 'true' -or $current.AutoConfigURL) { $reason = 'pac_enabled' }
        elseif ($systemEnabled -and !$isClashProxy) { $reason = 'other_proxy' }
        elseif (!$verge) { $reason = 'not_running' }
        elseif (!$hasShortcut -or (Read-Setting 'enable_global_hotkey') -eq 'false' -or $script:nativeWindow -eq [IntPtr]::Zero) { $reason = 'native_unavailable' }
    }
    return [ordered]@{
        available = [bool]$script:configText
        enabled = $enabled
        vergeEnabled = $vergeEnabled
        synchronized = $enabled -eq $vergeEnabled
        systemProxyEnabled = $systemEnabled
        proxyListening = $proxyListening
        canControl = !$reason
        endpoint = $endpoint
        tunEnabled = (Read-Setting 'enable_tun_mode') -eq 'true'
        control = 'clash-verge-native'
        reason = $reason
    }
}
function Invoke-NativeState([bool]$target) {
    [CodexPocket.ClashNativeControl]::Toggle($script:nativeWindow)
    $deadline = [DateTime]::UtcNow.AddSeconds(4)
    do {
        Start-Sleep -Milliseconds 100
        $actual = Read-Status
        if ($actual.vergeEnabled -eq $target -and $actual.enabled -eq $target) { return $actual }
    } while ([DateTime]::UtcNow -lt $deadline)
    # Do not blindly toggle again: an unacknowledged action may complete later.
    throw 'sync_timeout'
}
try {
    $status = Read-Status
    $status.changed = $false
    if ($Action -ne 'Status') {
        if ($status.reason) { throw $status.reason }
        $requested = $Action -eq 'Enable'
        if ($requested -and !$status.proxyListening) { throw 'not_running' }
        if ($status.vergeEnabled -ne $requested -or $status.enabled -ne $requested) {
            if ($status.vergeEnabled -eq $requested) {
                # Repair an old registry-only mismatch using Verge's own actions.
                $status = Invoke-NativeState (!$requested)
            }
            $status = Invoke-NativeState $requested
            $status.changed = $true
        }
    }
    $status | ConvertTo-Json -Compress
} catch {
    $known = @('not_installed', 'not_running', 'other_proxy', 'pac_enabled', 'native_unavailable', 'sync_timeout')
    $code = if ($_.Exception.Message -in $known) { $_.Exception.Message } else { 'native_unavailable' }
    @{error = $code} | ConvertTo-Json -Compress
    exit 1
}
