import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync, truncateSync, symlinkSync, linkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { ArtifactStore, localReferences, MAX_ARTIFACT } from '../server/artifacts.mjs';
import { SessionState } from '../server/state.mjs';

const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS1kAAAAASUVORK5CYII=','base64');
function fixture(t) {
  const dir = mkdtempSync(join(tmpdir(), 'pocket-artifacts-')); t.after(() => rmSync(dir, { recursive: true, force: true }));
  const outputs = join(dir, 'My outputs 中文'); mkdirSync(outputs);
  const image = join(outputs, '测试图片.png'), document = join(outputs, '数学报告.docx');
  writeFileSync(image, png); writeFileSync(document, Buffer.from('PK\x03\x04 document fixture'));
  return { dir, image, document, store: new ArtifactStore(join(dir,'state'),{allowedRoots:[dir]}) };
}
test('local references cover Markdown, plain paths, JSON and encoded URIs without remote requests', t => {
  const { dir, image, document } = fixture(t);
  for (const text of ['`' + image + '`', `![图片](${image.replaceAll(' ', '%20')})`, JSON.stringify({ files: [image] }), '生成完成：' + image, pathToFileURL(image).href]) {
    assert.ok(localReferences(text, dir).includes(image), text);
  }
  assert.ok(localReferences(`[下载 Word](<${document}>)`, dir).includes(document));
  assert.ok(localReferences('`report.docx`', dir).includes(resolve(dir, 'report.docx')));
  assert.equal(localReferences('![x](https://private.invalid/a.png)\nhttp://127.0.0.1/secret.docx\ndata:image/png;base64,AA', dir).some(path=>path.includes('private.invalid')||path.includes('secret.docx')), false);
});
test('completed outputs expose immutable authenticated IDs and preserve formats across service restarts', t => {
  const { dir, image, document, store } = fixture(t);
  const thread = { cwd: dir, items: [
    { id:'user',type:'userMessage',text:image },
    { id:'command',type:'commandExecution',text:document,output:JSON.stringify({files:[image]}),status:'completed' },
    { id:'reply',type:'agentMessage',text:`![预览](${image})\n[Word](<${document}>)`,status:'completed' },
    { id:'partial',type:'agentMessage',text:document,status:'inProgress' },
  ] };
  const result = store.decorate(thread);
  assert.equal(result.items[0].artifacts, undefined); assert.equal(result.items[1].artifacts, undefined);
  assert.equal(result.items[3].artifacts, undefined);
  const files = result.items[2].artifacts; assert.equal(files.length,2);
  assert.equal(files[0].kind,'image');assert.equal(files[0].mime,'image/png');
  assert.equal(files[1].kind,'file');assert.match(files[1].mime,/wordprocessingml/);
  for (const file of files) { assert.equal(file.path,undefined); assert.match(file.id,/^[0-9a-f-]{36}$/); }
  const oldId = files[0].id; writeFileSync(image, Buffer.from('source replaced'));
  assert.deepEqual(readFileSync(store.get(oldId).path),png);
  const restarted = new ArtifactStore(join(dir,'state'),{allowedRoots:[dir]}); assert.deepEqual(readFileSync(restarted.get(oldId).path),png);
  assert.throws(()=>restarted.get('../token.txt'),error=>error.status===400);
  assert.throws(()=>restarted.get('00000000-0000-0000-0000-000000000000'),error=>error.status===404);
});

test('exports reject paths outside workspace, private state, secrets, symlinks and hardlinks',t=>{
  const {dir,store,image}=fixture(t);
  const outside=mkdtempSync(join(tmpdir(),'pocket-private-'));t.after(()=>rmSync(outside,{recursive:true,force:true}));
  const privateFile=join(outside,'report.txt');writeFileSync(privateFile,'private fixture');
  assert.equal(store.register(privateFile),null);
  for(const name of ['.env','.env.local','credentials.json','token.txt','pairing.txt','signing.properties','private.pem']){
    const file=join(dir,name);writeFileSync(file,'not a real credential');assert.equal(store.register(file),null,name);
  }
  for(const name of ['.git','.ssh','.aws','.codex','state']){
    const folder=join(dir,name);mkdirSync(folder,{recursive:true});const file=join(folder,'report.json');writeFileSync(file,'private fixture');assert.equal(store.register(file),null,name);
  }
  const sub=join(dir,'narrow');mkdirSync(sub);assert.equal(store.register(image,sub),null);
  try{const link=join(dir,'linked.txt');symlinkSync(privateFile,link);assert.equal(store.register(link),null);}catch(e){if(e.code!=='EPERM')throw e;t.diagnostic('OS did not grant symlink creation; realpath traversal tested separately');}
  if(process.platform==='win32'){const junction=join(dir,'linked-directory');symlinkSync(outside,junction,'junction');assert.equal(store.register(join(junction,'report.txt')),null,'Directory junction cannot escape workspace');}
  const hard=join(dir,'hardlink.txt');linkSync(privateFile,hard);assert.equal(store.register(hard),null);
  const noRoots=new ArtifactStore(join(dir,'no-roots'));assert.equal(noRoots.register(image),null);
  const narrowed=new ArtifactStore(join(dir,'state'),{allowedRoots:[sub]});
  assert.equal(narrowed.allowedDirectory(dir),false);
});
test('tool-only Seedream outputs and native generated images appear without final links', t => {
  const { dir, image, store } = fixture(t);
  const state = new SessionState();
  state.hydrate({id:'a',cwd:dir,turns:[{id:'turn',status:'completed',items:[{id:'gen',type:'imageGeneration',status:'completed',savedPath:image}]}]});
  assert.equal(store.decorate(state.snapshot('a').thread).items[0].artifacts[0].kind,'image');
  assert.equal(store.decorate({cwd:dir,items:[{id:'cmd',type:'commandExecution',status:'completed',text:'python generate_image.py',output:JSON.stringify({files:[image]})}]}).items[0].artifacts[0].kind,'image');
});
test('missing, non-file, oversized, and fake images cannot break conversation rendering', t => {
  const { dir, image, store } = fixture(t);
  assert.equal(store.register(join(dir,'missing.docx')),null);assert.equal(store.register(dir),null);
  const large=join(dir,'large.zip');writeFileSync(large,'x');truncateSync(large,MAX_ARTIFACT+1);assert.equal(store.register(large),null);
  const fake=join(dir,'fake.png');writeFileSync(fake,'<script>not an image</script>');
  const result=store.register(fake);assert.equal(result.kind,'file');assert.equal(result.mime,'application/octet-stream');
  const thread={cwd:dir,items:[{id:'one',type:'agentMessage',status:'completed',text:`[x](${image})`}]};
  assert.deepEqual(store.decorate(thread),store.decorate(thread));
});
