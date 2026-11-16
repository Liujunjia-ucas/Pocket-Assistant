param([string]$FixturePath, [string]$Action)
$ErrorActionPreference = 'Stop'
$fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
$global:fixtureResultPath = "$FixturePath.result"
$global:fixtureRegistry = @{
    ProxyEnable = [int]$fixture.enabled
    ProxyServer = [string]$fixture.server
    ProxyOverride = 'localhost;100.*;<local>'
    AutoConfigURL = ''
}
$global:fixtureVergeEnabled = if ($null -ne $fixture.vergeEnabled) { [bool]$fixture.vergeEnabled } else { [bool]$fixture.enabled }
$global:fixtureToggles = 0
$global:fixtureNoAck = [bool]$fixture.noAck
$listener = $null
try {
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $fixturePort = $listener.LocalEndpoint.Port
    if ($fixture.offline) { $listener.Stop(); $listener = $null }
    $global:fixtureEndpoint = "127.0.0.1:$fixturePort"
    if ($global:fixtureRegistry.ProxyServer -eq 'fixture') { $global:fixtureRegistry.ProxyServer = $global:fixtureEndpoint }
    $env:CODEX_POCKET_CLASH_DIR = Split-Path -Parent $FixturePath
    $global:fixtureConfigPath = Join-Path $env:CODEX_POCKET_CLASH_DIR 'verge.yaml'
    $fixtureConfig = "verge_mixed_port: $fixturePort`nproxy_host: 127.0.0.1`nenable_tun_mode: false`nenable_system_proxy: $($global:fixtureVergeEnabled.ToString().ToLowerInvariant())`n"
    if (!$fixture.missingShortcut) { $fixtureConfig += "hotkeys:`n- toggle_system_proxy,Control+Alt+Shift+F24`n" }
    Set-Content -LiteralPath $global:fixtureConfigPath -Value $fixtureConfig -Encoding UTF8
    function global:Get-ItemProperty {
        param([string]$LiteralPath)
        if ($LiteralPath -ne 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings') { throw 'Unexpected registry path' }
        return [pscustomobject]$global:fixtureRegistry.Clone()
    }
    function global:Get-Process {
        param([string]$Name, [string]$ErrorAction)
        if ($Name -ne 'clash-verge') { throw 'Unexpected process query' }
        return [pscustomobject]@{ Id = 123; SessionId = [System.Diagnostics.Process]::GetCurrentProcess().SessionId }
    }
    function global:New-ItemProperty { throw 'Native helper must never write the registry directly' }
    function global:Remove-ItemProperty { throw 'Native helper must never write the registry directly' }
    function global:Save-Fixture {
        @{ registry = $global:fixtureRegistry; toggles = $global:fixtureToggles; vergeEnabled = $global:fixtureVergeEnabled } | ConvertTo-Json | Set-Content -LiteralPath $global:fixtureResultPath -Encoding UTF8
    }
    # This fake type prevents any real Windows messages or keyboard input.
    Add-Type -TypeDefinition @"
using System;
namespace CodexPocket {
    public static class ClashNativeControl {
        public static Action OnToggle;
        public static IntPtr FindHotkeyWindow(int processId) { return new IntPtr(1); }
        public static void Toggle(IntPtr window) { OnToggle(); }
    }
}
"@
    [CodexPocket.ClashNativeControl]::OnToggle = [Action]{
        $global:fixtureToggles++
        if (!$global:fixtureNoAck) {
            $global:fixtureVergeEnabled = !$global:fixtureVergeEnabled
            $global:fixtureRegistry.ProxyEnable = [int]$global:fixtureVergeEnabled
            $global:fixtureRegistry.ProxyServer = $global:fixtureEndpoint
            $updated = Get-Content -LiteralPath $global:fixtureConfigPath -Raw -Encoding UTF8
            $updated = [regex]::Replace($updated, '(?m)^enable_system_proxy:.*$', "enable_system_proxy: $($global:fixtureVergeEnabled.ToString().ToLowerInvariant())")
            Set-Content -LiteralPath $global:fixtureConfigPath -Value $updated -Encoding UTF8
        }
        Save-Fixture
    }
    Save-Fixture
    & (Join-Path $PSScriptRoot '..\tools\clash-proxy.ps1') -Action $Action
} finally {
    if ($listener) { $listener.Stop() }
}
