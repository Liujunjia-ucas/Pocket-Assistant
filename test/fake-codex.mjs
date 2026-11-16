// Deterministic protocol fixture. No model calls, credentials or computer commands.
import { createInterface } from 'node:readline';
import { randomUUID } from 'node:crypto';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
let turnCounter = 0, active = null;
const config = {};
for(let i=2;i<process.argv.length;i++)if(process.argv[i]==='-c'){const value=process.argv[++i],equals=value.indexOf('=');try{config[value.slice(0,equals)]=JSON.parse(value.slice(equals+1));}catch{}}
const model=config.model, provider=config.model_provider;
const store=join(process.env.CODEX_POCKET_STATE_DIR,'fixture-threads.json');
function threads(){return existsSync(store)?JSON.parse(readFileSync(store,'utf8')):{};}
function save(thread){const all=threads();all[thread.id]=thread;writeFileSync(store,JSON.stringify(all));}
const output = value => process.stdout.write(JSON.stringify(value) + '\n');
const event = (method, params) => output({ method, params });
function finish(text = '已完成') {
  if (!active) return;
  const { threadId, turnId } = active;
  const item = { id: `agent-${turnId}`, type: 'agentMessage', text };
  const thread=threads()[threadId], turn=thread.turns.find(t=>t.id===turnId);turn.items.push(item);turn.status='completed';save(thread);
  event('item/started', { threadId, turnId, item: { ...item, text: '' } });
  event('item/agentMessage/delta', { threadId, turnId, itemId: item.id, delta: text });
  event('item/completed', { threadId, turnId, item });
  event('turn/completed', { threadId, turn: { id: turnId, status: 'completed' } }); active = null;
}
createInterface({ input: process.stdin }).on('line', line => {
  const m = JSON.parse(line), p = m.params || {};
  if (!m.method) { if (m.id === 'approve-1') finish(m.result.decision === 'accept' ? '允许后完成' : '已拒绝'); if (m.id === 'question-1') finish(m.result.answers.choice.answers.join(',')); return; }
  let result = {};
  switch (m.method) {
    case 'initialize': result = { userAgent: 'fake-codex' }; break;
    case 'initialized': return;
    case 'account/read': result = { account: { type: 'chatgpt' }, requiresOpenaiAuth: true }; break;
    case 'model/list': result = { data: [{ id: 'fixture', model: 'fixture', displayName: 'Fixture' }] }; break;
    case 'thread/list': result = { data: Object.values(threads()), nextCursor: null }; break;
    case 'thread/start': {
      const thread = { id: randomUUID(), name: '测试会话', cwd: p.cwd, turns: [], model, modelProvider:provider }; save(thread); result = { thread }; break;
    }
    case 'thread/resume': case 'thread/read': result = { thread: threads()[p.threadId] }; break;
    case 'thread/fork':{const source=threads()[p.threadId];const thread={...source,id:randomUUID(),model,modelProvider:provider};save(thread);result={thread};break;}
    case 'turn/start': {
      const turnId = randomUUID(), threadId = p.threadId, text = p.input[0].text;
      active = { threadId, turnId };
      const thread=threads()[threadId];thread.turns.push({id:turnId,status:'inProgress',items:[{id:`user-${turnId}`,type:'userMessage',content:p.input}]});save(thread);
      event('turn/started', { threadId, turn: { id: turnId, status: 'inProgress' } });
      event('item/completed', { threadId, turnId, item: { id: `user-${turnId}`, type: 'userMessage', content: p.input } });
      result = { turn: { id: turnId, status: 'inProgress' } };
      if(text==='model-check')finish(`${model}@${provider}`);
      else if(text==='generated-artifacts'){
        const image=join(process.env.CODEX_POCKET_CWD,'generated photo.png');
        const document=join(process.env.CODEX_POCKET_CWD,'generated report.docx');
        writeFileSync(image,Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS1kAAAAASUVORK5CYII=','base64'));
        writeFileSync(document,Buffer.from('PK\x03\x04 document fixture'));
        finish(`![生成图片](<${image}>)\n[下载 Word](<${document}>)`);
      }
      else if(text.startsWith('inspect-attachments')){
        const images=p.input.filter(v=>v.type==='localImage');
        const filePath=text.match(/^"document.txt"：(.*)$/m)?.[1];
        finish(`images=${images.length}; imageExists=${images.every(v=>existsSync(v.path))}; file=${filePath?readFileSync(JSON.parse(filePath),'utf8'):'none'}`);
      }
      else if (text === 'approval') output({ id: 'approve-1', method: 'item/commandExecution/requestApproval', params: { threadId, turnId, itemId: 'cmd', command: 'echo test', reason: '测试审批' } });
      else if (text === 'question') output({ id: 'question-1', method: 'item/tool/requestUserInput', params: { threadId, turnId, itemId: 'question', questions: [{ id: 'choice', question: '请选择', options: [{ label: 'A' }] }] } });
      else if (text === 'fast') finish('极速完成');
      else if (text !== 'hold') setTimeout(() => finish('中文流式回复 ✓'), 40);
      break;
    }
    case 'turn/interrupt':
      {const thread=threads()[p.threadId];const turn=thread.turns.find(t=>t.id===p.turnId);if(turn){turn.status='interrupted';save(thread);}}
      event('turn/completed', { threadId: p.threadId, turn: { id: p.turnId, status: 'interrupted' } }); active = null; break;
    default: output({ id: m.id, error: { code: -32601, message: m.method } }); return;
  }
  output({ id: m.id, result });
});
