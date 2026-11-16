import http from 'node:http';
import { execFileSync } from 'node:child_process';
import { randomBytes, timingSafeEqual, createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync, statSync, createReadStream, renameSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve, isAbsolute } from 'node:path';
import { hostname } from 'node:os';
import { Codex } from './codex.mjs';
import { SessionState } from './state.mjs';
import { configuredModels, publicModel, modelParams } from './models.mjs';
import { UploadStore } from './uploads.mjs';
import { ClashProxy } from './clash-proxy.mjs';
import { ArtifactStore } from './artifacts.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const stateDir = process.env.CODEX_POCKET_STATE_DIR || join(root, 'state'); mkdirSync(stateDir, { recursive: true });
const readState = (name, fallback = {}) => { const p = join(stateDir, name + '.json'); return existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : fallback; };
function saveState(name, data) { const p = join(stateDir, name + '.json'); writeFileSync(p + '.tmp', JSON.stringify(data)); renameSync(p + '.tmp', p); }
const tokenFile = join(stateDir, 'token.txt');
if (!existsSync(tokenFile)) writeFileSync(tokenFile, randomBytes(32).toString('hex'), { mode: 0o600 });
const token = readFileSync(tokenFile, 'utf8').trim();
if (token.length < 32) throw new Error('配对令牌至少需要 32 个字符，请删除 state/token.txt 后重新启动。');
function tailscaleIP() {
  for (const command of [process.env.TAILSCALE_BINARY, 'tailscale', 'C:\\Program Files\\Tailscale\\tailscale.exe'].filter(Boolean)) {
    try { const ip = execFileSync(command, ['ip', '-4'], { encoding: 'utf8', windowsHide: true, timeout: 5000 }).trim().split(/\r?\n/)[0]; if (/^100\./.test(ip)) return ip; } catch {}
  }
  throw new Error('请先连接电脑端 Tailscale。本地测试可设置 CODEX_POCKET_HOST=127.0.0.1。');
}
const host = process.env.CODEX_POCKET_HOST || '127.0.0.1';
if (!(host === '127.0.0.1' || /^100\.(?:6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3}$/.test(host))) throw new Error('服务只能绑定回环或 Tailscale IPv4 地址。');
const port = Number(process.env.CODEX_POCKET_PORT || 8787), defaultCwd = process.env.CODEX_POCKET_CWD || join(root,'workspace');
mkdirSync(defaultCwd,{recursive:true});
const models = configuredModels(), clients = new Map(), starting = new Map();
const state = new SessionState(), uploads = new UploadStore(stateDir);
const artifacts = new ArtifactStore(stateDir,{allowedRoots:[defaultCwd],deniedRoots:[stateDir]});
const clashProxy = new ClashProxy();
const proxyControl=process.env.CODEX_POCKET_ALLOW_PROXY_CONTROL==='1';
let proxyChanging = false;
state.userMessages = readState('user-messages');
const modes = readState('modes'), selections = readState('selections');
const waiters = new Set(), locks = new Set(), opened = new Map(), sent = new Map();
const failure = (message, status = 400) => Object.assign(new Error(message), { status });
const authFailures=new Map();
let notificationTimer, serviceReady = false, serviceError = '';
function changed() { if (!notificationTimer) notificationTimer = setTimeout(() => { notificationTimer = null; for (const finish of [...waiters]) finish(); }, 80); }
function selected(id) { return models.find(m => m.id === id); }
const defaultModel = () => models.find(m => m.available) || models[0];
function bind(client, model) {
  client.on('message', message => {
    if (message.id !== undefined && !['item/commandExecution/requestApproval', 'item/fileChange/requestApproval', 'item/tool/requestUserInput'].includes(message.method)) {
      client.rejectRequest(message.id, '手机端暂不支持此类交互，请在电脑端处理。');
      if (message.params?.threadId) state.thread(message.params.threadId).error = `暂不支持的交互：${message.method}`;
      state.revision++; changed(); return;
    }
    state.apply({ ...message, clientId: model.id, ...(message.id !== undefined ? { approvalKey: `${model.id}:${message.id}` } : {}) }); changed();
  });
  client.on('status', () => { state.revision++; changed(); });
}
async function getClient(model = defaultModel()) {
  modelParams(model);
  if (clients.get(model.id)?.ready) return clients.get(model.id);
  if (starting.has(model.id)) return starting.get(model.id);
  const params = modelParams(model);
  const client = new Codex({ cwd: defaultCwd, overrides: { ...params.config, model: model.id, model_provider: model.provider } });
  bind(client, model); clients.set(model.id, client);
  const promise = client.start().then(() => client).catch(e => { client.stop(); throw e; }).finally(() => starting.delete(model.id));
  starting.set(model.id, promise); return promise;
}
function threadModel(thread) {
  return selected(selections[thread.id]) || models.find(m => m.id === thread.model && m.provider === thread.modelProvider) || models.find(m => m.provider === thread.modelProvider) || defaultModel();
}
function decorate(thread, model, readOnly) {
  if (thread.modelProvider && thread.modelProvider !== model.provider) throw failure('Codex 返回的服务与所选模型不一致，已阻止继续发送', 502);
  const t = state.hydrate(thread); t.readOnly = !!readOnly; t.model = model.id; t.modelLabel = model.label; t.modelProvider = model.provider;
  selections[t.id] = model.id; modes[t.id] = !!readOnly; saveState('selections', selections); saveState('modes', modes);
  opened.set(t.id, model.id); changed(); return t;
}
async function openThread(id) {
  if (opened.has(id) && clients.get(opened.get(id))?.ready) return;
  const reader = await getClient(); const info = await reader.request('thread/read', { threadId: id, includeTurns: false });
  checkCwd(info.thread.cwd);
  const model = threadModel(info.thread), client = await getClient(model);
  const result = await client.request('thread/resume', { threadId: id, sandbox: modes[id] ? 'read-only' : 'workspace-write', approvalPolicy: 'on-request', approvalsReviewer: 'user' });
  decorate(result.thread, model, modes[id]);
}
function snapshot(id) { const value = state.snapshot(id); return { ...value, thread: artifacts.decorate(value.thread), ready: serviceReady, error: serviceError }; }
function json(res, code, value) {
  if (res.destroyed) return;
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); res.end(JSON.stringify(value));
}
function authenticated(req) {
  const input = Buffer.from((req.headers.authorization || '').replace(/^Bearer /, '')), expected = Buffer.from(token);
  return input.length === expected.length && timingSafeEqual(input, expected);
}
async function body(req) {
  const chunks = []; let size = 0;
  for await (const chunk of req) { size += chunk.length; if (size > 131072) throw failure('消息过长（最多 128 KB）', 413); chunks.push(chunk); }
  try { return JSON.parse(Buffer.concat(chunks).toString() || '{}'); } catch { throw failure('JSON 格式无效'); }
}
function checkCwd(cwd) {
  if (typeof cwd !== 'string' || !isAbsolute(cwd) || !existsSync(cwd) || !statSync(cwd).isDirectory()) throw failure('工作目录必须是电脑上已存在的绝对目录');
  if(!artifacts.allowedDirectory(cwd))throw failure('目录不在允许的工作区内',403);
  return cwd;
}
async function exclusive(id, action) {
  if (locks.has(id)) throw failure('此会话正在处理请求，请稍后重试', 409);
  locks.add(id); try { return await action(); } finally { locks.delete(id); }
}
const server = http.createServer(async (req, res) => {
  try {
    const remote=req.socket.remoteAddress||'unknown',now=Date.now();
    let failures=authFailures.get(remote);
    if(failures&&failures.until<=now){authFailures.delete(remote);failures=null;}
    if(failures?.count>=20)return json(res,429,{error:'Too many failed pairing attempts. Retry after one minute.'});
    if (!authenticated(req)) {
      if(!failures){if(authFailures.size>=1024)authFailures.delete(authFailures.keys().next().value);failures={count:0,until:now+60000};authFailures.set(remote,failures);}
      failures.count++;return json(res, 401, { error: '配对令牌无效，请检查电脑上的 pairing.txt' });
    }
    authFailures.delete(remote);
    if (req.headers.origin) return json(res, 403, { error: '仅允许原生客户端连接' });
    const url = new URL(req.url, 'http://localhost'), path = url.pathname;
    if (req.method === 'GET' && path === '/api/status') return json(res, 200, { version: '2.6.8', ready: serviceReady, error: serviceError, computer: hostname(), defaultCwd, address: `http://${host}:${port}`, authenticated: models.some(m => m.available), models: models.map(publicModel) });
    if(path.startsWith('/api/lab/')) return json(res,410,{error:'Community edition uses phone-side companion APIs.'});
    if (path === '/api/proxy' && !proxyControl) return json(res,403,{error:'Proxy control is disabled by the computer owner.'});
    if (req.method === 'GET' && path === '/api/proxy') return json(res, 200, await clashProxy.status());
    if (req.method === 'POST' && path === '/api/proxy') {
      const b = await body(req);
      if (typeof b.enabled !== 'boolean') throw failure('enabled 必须是 true 或 false。');
      if (proxyChanging || locks.size || starting.size || [...state.threads.values()].some(t => t.activeTurn) || state.approvals.size) throw failure('请等待电脑上的任务完成，或先停止任务，再切换代理。', 409);
      proxyChanging = true;
      try {
        const status = await clashProxy.set(b.enabled);
        if (status.changed) {
          // New Codex processes must read the updated Windows system proxy.
          // Existing conversations remain saved and resume on their next request.
          for (const client of clients.values()) client.stop();
          clients.clear(); state.revision++; changed();
        }
        return json(res, 200, status);
      } finally { proxyChanging = false; }
    }
    if (proxyChanging) return json(res, 503, { error: '电脑代理正在切换，请稍后重试。' });
    if (req.method === 'GET' && path === '/api/models') return json(res, 200, { data: models.map(m => ({ ...publicModel(m), model: m.id, displayName: m.label })) });
    const artifactDownload = path.match(/^\/api\/artifacts\/([^/]+)\/content$/);
    if (req.method === 'GET' && artifactDownload) {
      const file = artifacts.get(artifactDownload[1]);
      res.writeHead(200, { 'Content-Type': file.mime, 'Content-Length': file.size, 'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(file.name)}`, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
      createReadStream(file.path).on('error', () => res.destroy()).pipe(res); return;
    }
    if (req.method === 'POST' && path === '/api/uploads') return json(res, 200, await uploads.receive(req, url));
    const download = path.match(/^\/api\/uploads\/([^/]+)\/content$/);
    if (req.method === 'GET' && download) {
      const file = uploads.get(download[1]);
      res.writeHead(200, { 'Content-Type': file.mime, 'Content-Length': file.size, 'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(file.name)}`, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
      createReadStream(file.path).on('error', () => res.destroy()).pipe(res); return;
    }
    if (!serviceReady) return json(res, 503, { error: serviceError || 'Codex 正在启动，请稍后重连' });
    if (req.method === 'GET' && path === '/api/events') {
      const id = url.searchParams.get('threadId'), after = Number(url.searchParams.get('after') || 0);
      if (state.revision > after) return json(res, 200, snapshot(id));
      const finish = () => { clearTimeout(timer); waiters.delete(finish); json(res, 200, snapshot(id)); };
      const timer = setTimeout(finish, 20000); waiters.add(finish);
      res.on('close', () => { clearTimeout(timer); waiters.delete(finish); }); return;
    }
    if (req.method === 'GET' && path === '/api/threads') {
      const client = await getClient();
      const result=await client.request('thread/list', { limit: 40, modelProviders: [], sortKey: 'updated_at', ...(url.searchParams.get('cursor') ? { cursor: url.searchParams.get('cursor') } : {}) });
      result.data=(result.data||[]).filter(thread=>artifacts.allowedDirectory(thread.cwd));
      return json(res,200,result);
    }
    if (req.method === 'POST' && path === '/api/threads') {
      const b = await body(req), model = b.model ? selected(b.model) : defaultModel(); modelParams(model);
      const cwd = checkCwd(b.cwd || defaultCwd), client = await getClient(model);
      const readOnly=b.readOnly!==false;
      const result = await client.request('thread/start', { cwd, sandbox: readOnly ? 'read-only' : 'workspace-write', approvalPolicy: 'on-request', approvalsReviewer: 'user' });
      decorate(result.thread, model, readOnly); return json(res, 200, snapshot(result.thread.id));
    }
    const match = path.match(/^\/api\/threads\/([^/]+)(?:\/(open|messages|interrupt|model))?$/);
    if (match) {
      const id = decodeURIComponent(match[1]), action = match[2];
      if (req.method === 'POST' && action === 'open') return await exclusive(id, async () => { await openThread(id); return json(res, 200, snapshot(id)); });
      if (req.method === 'GET' && !action) {
        if (!state.thread(id).hydrated) { const client = await getClient(); const result = await client.request('thread/read', { threadId: id, includeTurns: true }); checkCwd(result.thread.cwd); const t = state.hydrate(result.thread), m = threadModel(result.thread); t.model = m.id; t.modelLabel = m.label; t.readOnly = !!modes[id]; }
        return json(res, 200, snapshot(id));
      }
      if (req.method === 'POST' && action === 'model') {
        const b = await body(req), model = selected(b.model); modelParams(model);
        return await exclusive(id, async () => {
          await openThread(id); const old = state.thread(id);
          if (old.activeTurn) throw failure('请等待当前任务完成，或停止后再切换模型', 409);
          if (old.model === model.id) return json(res, 200, snapshot(id));
          const client = await getClient(model);
          // Providers belong to separate app-server processes. A fork carries the
          // full conversation into the selected provider and preserves the source.
          const params = { sandbox: old.readOnly ? 'read-only' : 'workspace-write', approvalPolicy: 'on-request', approvalsReviewer: 'user' };
          const hasHistory = old.items.some(i => i.type === 'userMessage');
          const result = await client.request(hasHistory ? 'thread/fork' : 'thread/start', { ...params, ...(hasHistory ? { threadId: id } : { cwd: old.cwd }) });
          const next = result.thread.id;
          for (const [key, value] of Object.entries(state.userMessages)) if (key.startsWith(id + ':')) state.userMessages[next + key.slice(id.length)] = value;
          saveState('user-messages', state.userMessages); decorate(result.thread, model, old.readOnly);
          return json(res, 200, { ...snapshot(next), previousThreadId: id });
        });
      }
      if (req.method === 'POST' && action === 'messages') {
        const b = await body(req), ids = b.attachments || [];
        if (typeof b.text !== 'string' || b.text.length > 60000 || (!b.text.trim() && !ids.length) || typeof b.requestId !== 'string' || !b.requestId || b.requestId.length > 100) throw failure('请输入消息或添加附件，并提供有效的请求编号');
        return await exclusive(id, async () => {
          const key = `${id}:${b.requestId}`, hash = createHash('sha256').update(JSON.stringify([b.text, ids])).digest('hex');
          if (sent.has(key)) { if (sent.get(key).hash !== hash) throw failure('请求编号已用于其他消息', 409); return json(res, 200, sent.get(key).result); }
          if (!opened.has(id)) throw failure('请先打开此会话', 409);
          if (state.thread(id).activeTurn) throw failure('Codex 正在执行，请等待完成或先停止', 409);
          await openThread(id);
          const prepared = uploads.inputs(ids, b.text), client = await getClient(selected(opened.get(id)));
          const result = await client.request('turn/start', { threadId: id, input: prepared.input });
          state.userMessages[`${id}:${result.turn.id}`] = { text: b.text, attachments: prepared.attachments }; saveState('user-messages', state.userMessages);
          if (result.turn.status === 'inProgress' && state.thread(id).lastCompletedTurn !== result.turn.id) { state.thread(id).activeTurn = result.turn.id; state.thread(id).status = 'running'; }
          const reply = { ok: true, turnId: result.turn.id }; sent.set(key, { hash, result: reply }); if (sent.size > 5000) sent.delete(sent.keys().next().value);
          state.revision++; changed(); return json(res, 200, reply);
        });
      }
      if (req.method === 'POST' && action === 'interrupt') {
        const turnId = state.thread(id).activeTurn;
        if (turnId) { const client = await getClient(selected(opened.get(id))); await client.request('turn/interrupt', { threadId: id, turnId }); }
        return json(res, 200, { ok: true });
      }
    }
    const approval = path.match(/^\/api\/approvals\/([^/]+)$/);
    if (req.method === 'POST' && approval) {
      const key = decodeURIComponent(approval[1]), a = state.approvals.get(key), b = await body(req);
      if (!a) throw failure('此请求已处理或已过期', 409);
      let result;
      if (a.method === 'item/tool/requestUserInput') {
        const answers = {};
        for (const q of a.params.questions || []) { const answer = b.answers?.[q.id]; if (!Array.isArray(answer) || !answer.every(v => typeof v === 'string') || !answer.length) throw failure('请回答所有问题'); answers[q.id] = { answers: answer }; }
        result = { answers };
      } else { if (!['accept', 'decline', 'cancel'].includes(b.decision)) throw failure('无效的确认选项'); result = { decision: b.decision }; }
      const client = clients.get(a.clientId); if (!client?.ready) throw failure('对应模型已断开，请重连', 503);
      client.respond(a.rpcId, result); state.approvals.delete(key); state.revision++; changed(); return json(res, 200, { ok: true });
    }
    json(res, 404, { error: '接口不存在' });
  } catch (e) { json(res, e.status || 500, { error: e.message }); }
});
server.requestTimeout = 180000; server.headersTimeout = 15000;server.maxConnections=64;
server.on('error', e => { console.error(`服务启动失败：${e.message}`); for (const c of clients.values()) c.stop(); process.exitCode = 1; });
server.listen(port, host, () => {
  writeFileSync(join(process.env.CODEX_POCKET_STATE_DIR || root, 'pairing.txt'), `Pocket Assistant 配对信息（请勿公开）\n\n电脑地址：http://${host}:${port}\n配对令牌：${token}\n\n手机打开 Tailscale，连接同一网络，然后在 App 的连接设置中填写以上信息。\n`, { mode: 0o600 });
  console.log(`Pocket Assistant Community 1.0.0：http://${host}:${port}\n配对信息：${join(root, 'pairing.txt')}\n按 Ctrl+C 停止。`);
  getClient().then(() => { serviceReady = true; console.log('Codex 已就绪。'); state.revision++; changed(); }).catch(e => { serviceError = e.message; console.error(`Codex 启动失败：${e.message}`); });
});
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => {  for (const c of clients.values()) c.stop(); server.close(); server.closeAllConnections(); process.exit(0); });
