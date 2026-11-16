import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { SessionState } from '../server/state.mjs';

test('live state preserves command output, completes turns and expires approvals', () => {
  const state = new SessionState();
  state.apply({ method: 'turn/started', params: { threadId: 'a', turn: { id: 't' } } });
  state.apply({ method: 'item/commandExecution/outputDelta', params: { threadId: 'a', itemId: 'i', delta: '中文输出' } });
  state.apply({ id: 9, method: 'item/commandExecution/requestApproval', params: { threadId: 'a', turnId: 't' } });
  assert.equal(state.snapshot('a').approvals.length, 1);
  assert.equal(state.snapshot('a').thread.items[0].output, '中文输出');
  state.apply({ method: 'turn/completed', params: { threadId: 'a', turn: { id: 't', status: 'interrupted' } } });
  assert.equal(state.snapshot('a').thread.activeTurn, null);
  assert.equal(state.snapshot('a').approvals.length, 0);
});

test('HTTP bridge: auth, sessions, streaming, retries, interruption and approvals', async t => {
  const dir = mkdtempSync(join(tmpdir(), 'codex-pocket-test-'));
  for(const [file,provider] of [['config.toml','custom'],['kimi.config.toml','kimi'],['chatgpt.config.toml','penguin_api']])writeFileSync(join(dir,file),`[model_providers.${provider}]\nname = "Fixture"\nbase_url = "http://127.0.0.1:9"\nenv_key = "POCKET_TEST_KEY"\nwire_api = "responses"\n`);
  writeFileSync(join(dir,'models.json'),JSON.stringify([
    {id:'deepseek-flash',label:'Fixture one',provider:'custom',file:'config.toml'},
    {id:'kimi-k3.1-pro',label:'Fixture two',provider:'kimi',file:'kimi.config.toml'},
    {id:'gpt-6.1-sol',label:'Fixture three',provider:'penguin_api',file:'chatgpt.config.toml'}
  ]));
  const workspace=join(dir,'workspace');mkdirSync(workspace);const runtime=join(dir,'runtime');
  const port = 19000 + Math.floor(Math.random() * 20000);
  const child = spawn(process.execPath, ['server/main.mjs'], {
    cwd: resolve('.'), windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, CODEX_POCKET_MODELS_FILE:join(dir,'models.json'), CODEX_POCKET_CWD:workspace, CODEX_POCKET_ALLOW_PROXY_CONTROL:'1', CODEX_POCKET_HOST: '127.0.0.1', CODEX_POCKET_PORT: String(port), CODEX_POCKET_STATE_DIR: runtime, CODEX_CLI_JS: resolve('test/fake-codex.mjs'), CODEX_POCKET_CONFIG_DIR:dir, CODEX_POCKET_CLASH_DIR:join(dir,'missing-clash'), POCKET_TEST_KEY:'fixture-not-a-secret' },
  });
  let logs = ''; child.stdout.on('data', c => logs += c); child.stderr.on('data', c => logs += c);
  t.after(async () => { child.kill(); await Promise.race([new Promise(r => child.once('exit', r)), delay(3000)]); rmSync(dir, { recursive: true, force: true }); });
  let token;
  for (let i = 0; i < 120; i++) {
    try { token = readFileSync(join(runtime, 'token.txt'), 'utf8').trim(); if (logs.includes('Codex 已就绪')) break; } catch {}
    if (child.exitCode !== null) throw new Error(logs);
    await delay(100);
  }
  assert.match(logs, /Codex 已就绪/, logs);
  const request = async (path, data, options = {}) => {
    const response = await fetch(`http://127.0.0.1:${port}${path}`, {
      method: data === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...options.headers },
      ...(data === undefined ? {} : { body: JSON.stringify(data) }), signal: AbortSignal.timeout(5000),
    });
    return { status: response.status, data: await response.json() };
  };
  assert.equal((await request('/api/status', undefined, { headers: { Authorization: 'Bearer wrong' } })).status, 401);
  assert.equal((await request('/api/status', undefined, { headers: { Origin: 'https://evil.example' } })).status, 403);
  assert.equal((await request('/api/lab/status',undefined,{headers:{Authorization:'Bearer wrong'}})).status,401);
  assert.equal((await request('/api/lab/memory',undefined,{headers:{Authorization:'Bearer wrong'}})).status,401);
  assert.equal((await request('/api/lab/memory',{action:'clear'},{headers:{Origin:'https://evil.example'}})).status,403);
  assert.equal((await request('/api/lab/status')).status,410);
  assert.equal((await request('/api/proxy', { enabled: true }, { headers: { Authorization: 'Bearer wrong' } })).status, 401);
  assert.equal((await request('/api/proxy', { enabled: true }, { headers: { Origin: 'https://evil.example' } })).status, 403);
  assert.equal((await request('/api/proxy', { enabled: 'true' })).status, 400);
  assert.equal((await request('/api/proxy', {})).status, 400);
  assert.equal((await request('/api/proxy')).data.available, false);
  assert.equal((await request('/api/proxy', { enabled: true })).status, 503);
  assert.equal((await request('/api/status')).data.authenticated, true);
  assert.deepEqual((await request('/api/models')).data.data.map(m=>m.id),['deepseek-flash','kimi-k3.1-pro','gpt-6.1-sol']);
  assert.equal((await request('/api/threads', { cwd: workspace, model:'unknown-model' })).status,400);
  assert.equal((await request('/api/threads', { cwd: 'nonexistent' })).status, 400);
  assert.equal((await request('/api/threads',{cwd:dir})).status,403,'No project access outside allowed workspace');
  const created = await request('/api/threads', { cwd: workspace, readOnly: true });
  assert.equal(created.status,200,JSON.stringify(created.data));
  const id = created.data.thread.id, path = `/api/threads/${id}`;
  assert.equal(created.data.thread.readOnly, true);
  assert.equal((await request(path + '/open', {})).data.thread.id, id);
  assert.equal((await request('/api/threads')).data.data.length, 1);
  const stream = await request(path + '/messages', { text: 'hello', requestId: '1' });
  const retry = await request(path + '/messages', { text: 'hello', requestId: '1' });
  assert.deepEqual(retry.data, stream.data);
  await delay(150);
  let snapshot = (await request(path)).data;
  assert.equal(snapshot.thread.activeTurn, null);
  assert.equal(snapshot.thread.items.at(-1).text, '中文流式回复 ✓');
  // Completion can arrive before the RPC response, and must not be overwritten.
  await request(path + '/messages', { text: 'fast', requestId: '2' });
  assert.equal((await request(path)).data.thread.activeTurn, null);
  await request(path + '/messages', { text: 'hold', requestId: '3' });
  assert.equal((await request('/api/proxy', { enabled: false })).status, 409);
  assert.equal((await request(path + '/messages', { text: 'other', requestId: '4' })).status, 409);
  await request(path + '/interrupt', {});
  assert.equal((await request(path)).data.thread.status, 'interrupted');
  await request(path + '/messages', { text: 'approval', requestId: '5' });
  snapshot = (await request(path)).data;
  assert.equal(snapshot.approvals[0].params.command, 'echo test');
  const approvalPath='/api/approvals/'+encodeURIComponent(snapshot.approvals[0].id);
  assert.equal((await request(approvalPath, { decision: 'acceptForSession' })).status, 400);
  await request(approvalPath, { decision: 'accept' });
  await delay(100);
  assert.equal((await request(path)).data.thread.items.at(-1).text, '允许后完成');
  assert.equal((await request(approvalPath, { decision: 'accept' })).status, 409);
  await request(path + '/messages', { text: 'question', requestId: '6' });
  const questionPath='/api/approvals/'+encodeURIComponent((await request(path)).data.approvals[0].id);
  assert.equal((await request(questionPath, { answers: {} })).status, 400);
  await request(questionPath, { answers: { choice: ['A'] } });
  await delay(100);
  assert.equal((await request(path)).data.thread.items.at(-1).text, 'A');
  const oldRevision = (await request(path)).data.revision;
  const event = request(`/api/events?after=${oldRevision}&threadId=${id}`);
  await request(path + '/messages', { text: 'fast', requestId: '7' });
  assert.ok((await event).data.revision > oldRevision);
  const upload=async(name,kind,bytes)=>{
    const r=await fetch(`http://127.0.0.1:${port}/api/uploads?name=${encodeURIComponent(name)}&kind=${kind}`,{method:'POST',headers:{Authorization:`Bearer ${token}`},body:bytes,signal:AbortSignal.timeout(10000)});return{status:r.status,data:await r.json()};
  };
  assert.equal((await upload('bad.png','image',Buffer.from('not an image'))).status,400);
  assert.equal((await upload('too-big.bin','file',Buffer.alloc(25*1024*1024+1))).status,413);
  const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS1kAAAAASUVORK5CYII=','base64');
  const image=(await upload('photo.png','image',png)).data;
  const file=(await upload('document.txt','file',Buffer.from('附件 sentinel UTF-8 ✓'))).data;
  const traversal=(await upload('../../token.txt','file',Buffer.from('safe'))).data;
  assert.ok(!traversal.name.includes('/'));
  const downloaded=await fetch(`http://127.0.0.1:${port}/api/uploads/${image.id}/content`,{headers:{Authorization:`Bearer ${token}`}});
  assert.deepEqual(Buffer.from(await downloaded.arrayBuffer()),png);
  assert.equal((await request(path+'/messages',{text:'invalid',requestId:'invalid-path',attachments:['../../token.txt']})).status,400);
  assert.equal((await request(path+'/messages',{text:'invalid',requestId:'too-many',attachments:Array(6).fill(file.id)})).status,400);
  await request(path+'/messages',{text:'inspect-attachments',requestId:'with-files',attachments:[image.id,file.id]});
  snapshot=(await request(path)).data;
  assert.equal(snapshot.thread.items.at(-1).text,'images=1; imageExists=true; file=附件 sentinel UTF-8 ✓');
  const visible=snapshot.thread.items.filter(i=>i.type==='userMessage').at(-1);
  assert.equal(visible.text,'inspect-attachments');assert.equal(visible.attachments.length,2);assert.ok(!visible.text.includes('codex-pocket-attachments'));
  let current=id;
  await request(path+'/messages',{text:'generated-artifacts',requestId:'generated-artifacts'});
  const generated=(await request(path)).data.thread.items.at(-1).artifacts;
  assert.equal(generated.length,2); assert.equal(generated[0].kind,'image'); assert.equal(generated[1].kind,'file');
  const artifactUrl=`http://127.0.0.1:${port}/api/artifacts/${generated[0].id}/content`;
  assert.equal((await fetch(artifactUrl)).status,401);
  assert.equal((await fetch(artifactUrl,{headers:{Authorization:`Bearer ${token}`,Origin:'https://evil.example'}})).status,403);
  const outputImage=await fetch(artifactUrl,{headers:{Authorization:`Bearer ${token}`}});
  assert.equal(outputImage.headers.get('content-type'),'image/png');
  assert.deepEqual(Buffer.from(await outputImage.arrayBuffer()),png);
  const outputDoc=await fetch(`http://127.0.0.1:${port}/api/artifacts/${generated[1].id}/content`,{headers:{Authorization:`Bearer ${token}`}});
  assert.match(outputDoc.headers.get('content-disposition'),/generated%20report.docx/);
  assert.deepEqual(Buffer.from(await outputDoc.arrayBuffer()),Buffer.from('PK\x03\x04 document fixture'));
  assert.equal((await request('/api/artifacts/not-a-file-id/content')).status,400);
  for(const [model,provider] of [['kimi-k3.1-pro','kimi'],['gpt-6.1-sol','penguin_api'],['deepseek-flash','custom']]){
    const switched=(await request(`/api/threads/${current}/model`,{model})).data;
    assert.equal(switched.thread.model,model);assert.equal(switched.thread.modelProvider,provider);assert.equal(switched.thread.readOnly,true);
    assert.ok(switched.thread.items.some(i=>i.text==='inspect-attachments'&&i.attachments?.length===2),'Attachment metadata must survive model switching');
    assert.ok(switched.thread.items.some(i=>i.artifacts?.length===2),'Generated files must survive model switching');
    current=switched.thread.id;
    await request(`/api/threads/${current}/messages`,{text:'model-check',requestId:'model-check-'+model});
    assert.equal((await request(`/api/threads/${current}`)).data.thread.items.at(-1).text,`${model}@${provider}`);
  }
  const defaultReadonly=await request('/api/threads',{cwd:workspace});
  assert.equal(defaultReadonly.data.thread.readOnly,true,'New remote sessions default to read-only');
  for(let i=0;i<20;i++)assert.equal((await request('/api/status',undefined,{headers:{Authorization:'Bearer wrong'}})).status,401);
  assert.equal((await request('/api/status',undefined,{headers:{Authorization:'Bearer wrong'}})).status,429,'Repeated pairing failures are limited');
});
