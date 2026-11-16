export function textOf(content) {
  return (content || []).map(c => c.text || (c.type === 'image' || c.type === 'localImage' ? '[图片]' : '')).filter(Boolean).join('\n');
}

export function displayItem(item, turnId) {
  const out = { id: item.id, type: item.type, status: item.status || 'completed', turnId };
  switch (item.type) {
    case 'userMessage': out.text = textOf(item.content); break;
    case 'agentMessage': out.text = item.text || ''; break;
    case 'commandExecution':
      out.text = item.command || ''; out.output = (item.aggregatedOutput || '').slice(-24000); out.exitCode = item.exitCode; break;
    case 'fileChange':
      out.text = (item.changes || []).map(c => `${c.kind?.type || '修改'}  ${c.path}\n${c.diff || ''}`).join('\n').slice(-30000); break;
    case 'plan': out.text = item.text || ''; break;
    case 'mcpToolCall': out.text = `${item.server || ''} / ${item.tool || ''}`; out.output = JSON.stringify(item.result || item.error || '').slice(-16000); break;
    case 'webSearch': out.text = item.query || JSON.stringify(item.action || {}); break;
    case 'imageView': out.text = item.path || ''; break;
    case 'imageGeneration': out.text = item.savedPath || item.result || item.revisedPrompt || ''; break;
    default: out.text = item.text || ''; break;
  }
  return out;
}

export class SessionState {
  constructor() { this.threads = new Map(); this.approvals = new Map(); this.revision = 1; this.userMessages = {}; }
  thread(id) {
    if (!this.threads.has(id)) this.threads.set(id, { id, title: '', cwd: '', items: [], activeTurn: null, lastCompletedTurn: null, error: '', status: 'idle', hydrated: false });
    return this.threads.get(id);
  }
  hydrate(thread) {
    const state = this.thread(thread.id);
    state.title = thread.name || thread.preview || '新会话'; state.cwd = thread.cwd || '';
    state.model = thread.model; state.modelProvider = thread.modelProvider;
    state.items = []; state.activeTurn = null; state.error = ''; state.status = 'idle';
    for (const turn of thread.turns || []) {
      for (const item of turn.items || []) state.items.push(displayItem(item, turn.id));
      if (turn.status === 'inProgress') { state.activeTurn = turn.id; state.status = 'running'; }
      if (turn.error) state.error = turn.error.message || JSON.stringify(turn.error);
    }
    state.hydrated = true;
    this.revision++;
    return state;
  }
  apply(message) {
    const { method, params: p = {} } = message;
    if (message.id !== undefined) {
      const key = message.approvalKey || String(message.id);
      this.approvals.set(key, { id: key, rpcId: message.id, clientId: message.clientId, method, params: p });
      this.revision++; return;
    }
    if (method === 'serverRequest/resolved') {
      this.approvals.delete(message.clientId ? `${message.clientId}:${p.requestId}` : String(p.requestId)); this.revision++; return;
    }
    const id = p.threadId || p.thread?.id;
    if (!id) return;
    const s = this.thread(id);
    if (method === 'thread/started') { s.cwd = p.thread.cwd; s.title = p.thread.name || p.thread.preview || '新会话'; }
    if (method === 'turn/started') { s.activeTurn = p.turn.id; s.status = 'running'; s.error = ''; }
    if (method === 'turn/completed') {
      s.lastCompletedTurn = p.turn.id;
      s.activeTurn = null; s.status = p.turn.status || 'completed';
      if (p.turn.error) s.error = p.turn.error.message || JSON.stringify(p.turn.error);
      for (const [key, a] of this.approvals) if (a.params.turnId === p.turn.id) this.approvals.delete(key);
    }
    if (method === 'error') s.error = p.error?.message || p.message || 'Codex 执行失败';
    if (method === 'item/started' || method === 'item/completed') {
      const item = displayItem(p.item, p.turnId), index = s.items.findIndex(i => i.id === item.id);
      if (index < 0) s.items.push(item); else s.items[index] = item;
    }
    if (method === 'item/agentMessage/delta' || method === 'item/commandExecution/outputDelta' || method === 'item/plan/delta') {
      let item = s.items.find(i => i.id === p.itemId);
      if (!item) { item = { id: p.itemId, type: method.includes('commandExecution') ? 'commandExecution' : 'agentMessage', text: '', status: 'inProgress' }; s.items.push(item); }
      if (method.includes('outputDelta')) item.output = ((item.output || '') + p.delta).slice(-24000);
      else item.text = ((item.text || '') + p.delta).slice(-120000);
    }
    if (method === 'turn/plan/updated') s.plan = p.plan;
    if (method === 'thread/tokenUsage/updated') s.tokenUsage = p.tokenUsage;
    this.revision++;
  }
  snapshot(id) {
    const thread = id ? this.threads.get(id) : null;
    return {
      revision: this.revision,
      thread: thread ? { ...thread, items: thread.items.slice(-200).map(item => item.type === 'userMessage' && this.userMessages[`${id}:${item.turnId}`] ? { ...item, ...this.userMessages[`${id}:${item.turnId}`] } : item).filter(i => i.text || i.output || i.attachments?.length) } : null,
      approvals: [...this.approvals.values()].filter(a => !id || a.params.threadId === id).map(({ rpcId, ...rest }) => rest),
    };
  }
}
