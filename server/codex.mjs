import { spawn, execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { createInterface } from 'node:readline';
import { EventEmitter } from 'node:events';

export function codexCommand() {
  if (process.env.CODEX_CLI_JS) return { command: process.execPath, args: [process.env.CODEX_CLI_JS] };
  if (process.env.CODEX_BINARY) return { command: process.env.CODEX_BINARY, args: [] };
  if (process.platform !== 'win32') return { command: 'codex', args: [] };
  const roots = [join(process.env.APPDATA || '', 'npm', 'node_modules', '@openai', 'codex')];
  try {
    for (const file of execFileSync('where.exe', ['codex.cmd'], { encoding: 'utf8', windowsHide: true }).trim().split(/\r?\n/)) {
      roots.push(join(dirname(file), 'node_modules', '@openai', 'codex'));
    }
  } catch {}
  for (const root of roots) {
    const triple = process.arch === 'arm64' ? 'aarch64-pc-windows-msvc' : 'x86_64-pc-windows-msvc';
    const native = join(root, '..', `codex-win32-${process.arch}`, 'vendor', triple, 'bin', 'codex.exe');
    if (existsSync(native)) return { command: native, args: [] };
    const cli = join(root, 'bin', 'codex.js');
    if (existsSync(cli)) return { command: process.execPath, args: [cli] };
  }
  throw new Error('未找到 Codex。请在 CMD 中确认 codex --version 可以运行，或设置 CODEX_BINARY 为 codex.exe 的完整路径。');
}

export class Codex extends EventEmitter {
  constructor({ command, args = [], cwd, overrides = {} } = {}) {
    super();
    this.launch = command ? { command, args } : codexCommand();
    this.cwd = cwd;
    this.overrides = overrides;
    this.pending = new Map();
    this.nextId = 1;
    this.ready = false;
    this.lastError = '';
    this.stderr = '';
  }
  async start() {
    const toml = value => typeof value === 'object' && value !== null && !Array.isArray(value)
      ? `{${Object.entries(value).map(([k, v]) => `${JSON.stringify(k)} = ${toml(v)}`).join(', ')}}`
      : Array.isArray(value) ? `[${value.map(toml).join(', ')}]` : JSON.stringify(value);
    const flags = Object.entries(this.overrides).flatMap(([key, value]) => ['-c', `${key}=${toml(value)}`]);
    this.child = spawn(this.launch.command, [...this.launch.args, ...flags, 'app-server', '--listen', 'stdio://'], {
      cwd: this.cwd, env: process.env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'], shell: false,
    });
    this.child.on('error', e => this.fail(e));
    this.child.on('exit', (code, signal) => this.fail(new Error(`Codex 进程已退出 (${signal || code})。${this.stderr.slice(-800)}`)));
    this.child.stdin.on('error', e => this.fail(e));
    this.child.stderr.on('data', chunk => { this.stderr = (this.stderr + chunk.toString()).slice(-8000); });
    const lines = createInterface({ input: this.child.stdout });
    lines.on('line', line => {
      let message;
      try { message = JSON.parse(line); } catch { return; }
      if (message.method) this.emit('message', message);
      else if (this.pending.has(message.id)) {
        const entry = this.pending.get(message.id);
        this.pending.delete(message.id);
        clearTimeout(entry.timer);
        message.error ? entry.reject(new Error(message.error.message)) : entry.resolve(message.result);
      }
    });
    await this.request('initialize', { clientInfo: { name: 'codex_pocket', title: 'Codex Pocket', version: '1.0.0' }, capabilities: { experimentalApi: true } });
    this.write({ method: 'initialized', params: {} });
    this.ready = true;
    this.emit('status');
  }
  fail(error) {
    this.ready = false;
    this.lastError = error.message;
    for (const entry of this.pending.values()) { clearTimeout(entry.timer); entry.reject(error); }
    this.pending.clear();
    this.emit('status');
  }
  write(message) {
    if (!this.child || this.child.killed || this.child.stdin.destroyed) throw new Error(this.lastError || 'Codex 尚未启动');
    this.child.stdin.write(JSON.stringify({ jsonrpc: '2.0', ...message }) + '\n');
  }
  request(method, params = {}) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error(`${method} 超时，请重连后检查会话状态`)); }, 60000);
      this.pending.set(id, { resolve, reject, timer });
      try { this.write({ id, method, params }); } catch (e) { clearTimeout(timer); this.pending.delete(id); reject(e); }
    });
  }
  respond(id, result) { this.write({ id, result }); }
  rejectRequest(id, reason) { this.write({ id, error: { code: -32601, message: reason } }); }
  stop() { this.child?.kill(); }
}
