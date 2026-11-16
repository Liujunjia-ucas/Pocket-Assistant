import { randomUUID, createHash } from 'node:crypto';
import { mkdirSync, existsSync, statSync, realpathSync, openSync, fstatSync, readSync, closeSync, renameSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { join, resolve, relative, isAbsolute, basename, extname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

export const MAX_ARTIFACT = 100 * 1024 * 1024;
const extensions = 'png|jpe?g|webp|gif|bmp|docx?|pdf|xlsx?|pptx?|odt|ods|odp|rtf|txt|md|csv|tsv|json|html?|svg|zip|7z|tar|gz|mp3|mp4|wav|py|js|ts|css|xml|yaml|yml|ipynb';
const ending = new RegExp(`\\.(?:${extensions})$`, 'i');
const mimeTypes = { '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', '.doc': 'application/msword', '.pdf': 'application/pdf', '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', '.xls': 'application/vnd.ms-excel', '.pptx': 'application/vnd.openxmlformats-officedocument.presentationml.presentation', '.zip': 'application/zip', '.txt': 'text/plain', '.csv': 'text/csv', '.md': 'text/markdown' };
const error = (message, status = 404) => Object.assign(new Error(message), { status });
const inside = (root, path) => { const r=relative(root,path);return r===''||(!r.startsWith('..'+sep)&&r!=='..'&&!isAbsolute(r)); };
// Deny common secret/config directories even if an owner chooses a broad root.
const sensitive = path => path.split(/[\\/]+/).some(part =>
  /^(?:\.git|\.ssh|\.aws|\.azure|\.gnupg|\.codex|\.config|\.gradle|node_modules)$/i.test(part) ||
  /^\.env(?:\.|$)/i.test(part) ||
  /^(?:pairing|token|credentials?|secrets?|passwords?|auth)(?:[._-]|$)/i.test(part) ||
  /^(?:id_rsa|id_ed25519|keystore|signing)(?:[._-]|$)/i.test(part) ||
  /\.(?:pem|key|jks|keystore|p12|pfx)$/i.test(part));

// Only local references already present in completed assistant/tool results are
// registered. Clients can request opaque IDs, never arbitrary computer paths.
export function localReferences(text, cwd) {
  const candidates = [];
  for (const m of text.matchAll(/"(?:\\.|[^"\\\r\n])*"/g)) { try { candidates.push(JSON.parse(m[0])); } catch {} }
  for (const m of text.matchAll(/`([^`\r\n]+)`|\]\(<?([^\r\n]+?)>?\)|(?:^|\n)\s*([^\r\n]+)/g)) candidates.push(m[1] || m[2] || m[3]);
  const bare = new RegExp(`(?:[a-zA-Z]:[\\\\/]|file:\\/\\/\\/|sandbox:\\/|\\/)[^\\r\\n<>\x22\x60]*?\\.(?:${extensions})(?=$|[\\s)\\]>,，。；;\x22\x60])`, 'gi');
  const localText = text.replace(/(?:https?|data):[^\s<>"`]+/gi, '');
  for (const m of localText.matchAll(bare)) candidates.push(m[0]);
  const paths = new Set();
  for (let candidate of candidates.slice(0, 100)) {
    if (typeof candidate !== 'string') continue;
    candidate = candidate.trim().replace(/^<|>$/g, '').replace(/#(?:L\d+.*|\d+.*)$/, '');
    if (/^https?:|^data:|^\\\\|^\/\//i.test(candidate)) continue;
    if (candidate.startsWith('sandbox:')) candidate = candidate.slice(8);
    try { candidate = candidate.startsWith('file:') ? fileURLToPath(candidate) : decodeURIComponent(candidate); } catch { continue; }
    if (/^\/[A-Za-z]:[\\/]/.test(candidate)) candidate = candidate.slice(1);
    if (!ending.test(candidate) || candidate.includes('\0') || candidate.length > 2048) continue;
    // JSON escaping can also appear in unparsed terminal text.
    if (/^[A-Za-z]:/.test(candidate)) candidate = candidate.replace(/\\\\/g, '\\');
    if (!isAbsolute(candidate) && !cwd) continue;
    paths.add(isAbsolute(candidate) ? resolve(candidate) : resolve(cwd, candidate));
  }
  return [...paths].slice(0, 24);
}

function imageMime(prefix) {
  if (prefix.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) return 'image/png';
  if (prefix[0] === 255 && prefix[1] === 216 && prefix[2] === 255) return 'image/jpeg';
  if (/^GIF8[79]a/.test(prefix.toString('ascii'))) return 'image/gif';
  if (prefix.toString('ascii', 0, 4) === 'RIFF' && prefix.toString('ascii', 8, 12) === 'WEBP') return 'image/webp';
  if (prefix.toString('ascii', 0, 2) === 'BM') return 'image/bmp';
  return null;
}

export class ArtifactStore {
  constructor(stateDir, {allowedRoots=[],deniedRoots=[]}={}) {
    this.root = join(stateDir, 'artifacts'); mkdirSync(this.root, { recursive: true });
    this.allowedRoots=allowedRoots.map(p=>realpathSync(p));
    this.deniedRoots=[realpathSync(stateDir),...deniedRoots.filter(existsSync).map(p=>realpathSync(p))];
    this.memo = new Map(); this.items = new Map();
  }
  allowedDirectory(path) {
    try {const actual=realpathSync(path);return statSync(actual).isDirectory()&&!sensitive(actual)&&this.allowedRoots.some(r=>inside(r,actual))&&!this.deniedRoots.some(r=>inside(r,actual));}
    catch{return false;}
  }
  allowedFile(path,cwd) {
    try {const actual=realpathSync(path),base=realpathSync(cwd);return this.allowedDirectory(base)&&inside(base,actual)&&!sensitive(actual)&&ending.test(actual)&&!this.deniedRoots.some(r=>inside(r,actual));}
    catch{return false;}
  }
  register(path,cwd=this.allowedRoots[0]) {
    let temporary;
    try {
      const actual = realpathSync(path), stat = statSync(actual);
      if (!this.allowedFile(actual,cwd)||!stat.isFile()||stat.nlink!==1||stat.size<1||stat.size>MAX_ARTIFACT) return null;
      const key = createHash('sha256').update(JSON.stringify([actual, stat.size, stat.mtimeMs])).digest('hex');
      if (this.memo.has(key)) return this.memo.get(key);
      // Store an immutable copy: source replacement/symlinks cannot later expose
      // a different file, and restarting the service keeps old downloads valid.
      const index = join(this.root, key + '.json');
      if (existsSync(index)) {
        const meta = JSON.parse(readFileSync(index, 'utf8'));
        try { if (this.get(meta.id)) { this.memo.set(key, meta); return meta; } } catch {}
      }
      const id = randomUUID(), destination = join(this.root, id);
      temporary = destination + '.part';
      // Read through one opened descriptor and reject replacement/hardlink races.
      const source=openSync(actual,'r');
      let bytes;
      try {const opened=fstatSync(source);if(opened.dev!==stat.dev||opened.ino!==stat.ino||opened.nlink!==1||opened.size!==stat.size) return null;
        bytes=Buffer.alloc(opened.size);let offset=0;while(offset<bytes.length){const n=readSync(source,bytes,offset,bytes.length-offset,offset);if(!n)throw new Error('Source changed');offset+=n;}
        const after=fstatSync(source);if(after.size!==opened.size||after.mtimeMs!==opened.mtimeMs)throw new Error('Source changed');
      }finally{closeSync(source);}
      writeFileSync(temporary,bytes,{mode:0o600,flag:'wx'});
      const copied = statSync(temporary);
      if (copied.size !== stat.size || copied.size > MAX_ARTIFACT) throw new Error('File changed while copying');
      const prefix = Buffer.alloc(16), fd = openSync(temporary, 'r');
      try { readSync(fd, prefix, 0, 16, 0); } finally { closeSync(fd); }
      const image = imageMime(prefix), extension = extname(actual).toLowerCase();
      const meta = { id, name: basename(actual), size: copied.size, kind: image ? 'image' : 'file', mime: image || mimeTypes[extension] || 'application/octet-stream', source: 'generated' };
      renameSync(temporary, destination); temporary = null;
      writeFileSync(join(this.root, id + '.json'), JSON.stringify({...meta,sourcePath:actual,workspace:realpathSync(cwd)}),{mode:0o600});
      writeFileSync(index + '.tmp', JSON.stringify(meta)); renameSync(index + '.tmp', index);
      this.memo.set(key, meta); return meta;
    } catch { return null; }
    finally { if (temporary) rmSync(temporary, { force: true }); }
  }
  get(id) {
    if (typeof id !== 'string' || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(id)) throw error('无效的生成文件编号', 400);
    const metaPath = join(this.root, id + '.json'), path = join(this.root, id);
    if (!existsSync(metaPath) || !existsSync(path)) throw error('生成文件已失效，请重新生成或让模型再次提供文件。');
    const meta = JSON.parse(readFileSync(metaPath, 'utf8'));
    if(!this.allowedFile(meta.sourcePath,meta.workspace))throw error('Export is no longer permitted',403);
    const {sourcePath,workspace,...publicMeta}=meta;
    return { ...publicMeta, path };
  }
  decorate(thread) {
    if (!thread) return thread;
    if(!this.allowedDirectory(thread.cwd))return thread;
    const seen = new Set(); let total = 0;
    // Prefer final replies when a tool and the reply reference the same file.
    const found = new Map();
    const ordered = [...thread.items].sort((a, b) => (a.type === 'agentMessage' ? 0 : 1) - (b.type === 'agentMessage' ? 0 : 1));
    for (const item of ordered) {
      if (!['agentMessage', 'commandExecution', 'mcpToolCall', 'imageGeneration', 'imageView'].includes(item.type) || item.status === 'inProgress') continue;
      // Shell commands contain scripts and input paths: inspect result output only.
      const content = item.type === 'commandExecution' || item.type === 'mcpToolCall' ? item.output || '' : item.text || '';
      const text = typeof content === 'string' ? content : JSON.stringify(content);
      const signature = createHash('sha256').update(JSON.stringify([thread.cwd, text])).digest('hex');
      let entry = this.items.get(signature);
      if (!entry || (entry.retry && Date.now() - entry.time > 3000)) {
        const references = localReferences(text, thread.cwd), files = references.map(p => this.register(p,thread.cwd)).filter(Boolean);
        entry = { files, retry: files.length !== references.length, time: Date.now() }; this.items.set(signature, entry);
        if (this.items.size > 2000) this.items.delete(this.items.keys().next().value);
      }
      const files = entry.files.filter(file => {
        const key = `${item.turnId || ''}:${file.id}`;
        if (seen.has(key) || total >= 64) return false;
        seen.add(key); total++; return true;
      }).slice(0, 8);
      if (files.length) found.set(item.id, files);
    }
    return { ...thread, items: thread.items.map(item => found.has(item.id) ? { ...item, artifacts: found.get(item.id) } : item) };
  }
}
