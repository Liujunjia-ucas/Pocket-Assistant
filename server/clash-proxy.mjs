import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const execute = promisify(execFile);
const reasons = {
  unsupported: '此功能需要 Windows 电脑端服务。',
  not_installed: '未找到 Clash Verge 配置，请先在电脑上安装并运行 Clash Verge。',
  not_running: 'Clash Verge 代理端口尚未就绪，请先在电脑上启动 Clash Verge。',
  other_proxy: '电脑正在使用其他代理，无法用 Clash Verge 开关覆盖；请先在电脑端切换。',
  pac_enabled: '当前使用 PAC 自动配置，请先在 Clash Verge 中关闭 PAC 模式。',
  native_unavailable: 'Clash Verge 原生控制尚未就绪，请在电脑端运行 tools/setup-clash-native-control.ps1，或确认 Clash Verge 已启动。',
  sync_timeout: 'Clash Verge 尚未确认开关与系统代理同步，请刷新状态后再操作。',
};
const fail = (message, status = 503) => Object.assign(new Error(message), { status });

export class ClashProxy {
  constructor({ invoke, platform = process.platform } = {}) {
    this.platform = platform;
    this.invoke = invoke || (async action => {
      const command = join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
      const helper = fileURLToPath(new URL('../tools/clash-proxy.ps1', import.meta.url));
      const { stdout } = await execute(command, ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', helper, '-Action', action], {
        windowsHide: true, timeout: 12000, maxBuffer: 65536, encoding: 'utf8',
      });
      return JSON.parse(stdout.trim().replace(/^\uFEFF/, ''));
    });
  }
  async run(action) {
    if (this.platform !== 'win32') return { available: false, enabled: false, canControl: false, reason: 'unsupported', message: reasons.unsupported };
    let result;
    try { result = await this.invoke(action); }
    catch (error) {
      let code;
      try { code = JSON.parse(error.stdout.trim()).error; } catch {}
      throw fail(reasons[code] || '无法读取或更新电脑系统代理，请检查电脑端服务权限和 Clash Verge。');
    }
    if (result.error) throw fail(reasons[result.error] || 'Clash Verge 未能确认代理状态，请刷新后重试。');
    return { ...result, message: reasons[result.reason] || '' };
  }
  status() { return this.run('Status'); }
  async set(enabled) {
    if (typeof enabled !== 'boolean') throw fail('enabled 必须是 true 或 false。', 400);
    if (this.platform !== 'win32') throw fail(reasons.unsupported);
    return this.run(enabled ? 'Enable' : 'Disable');
  }
}
