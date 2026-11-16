import { existsSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

// Read only the provider settings we need. Credentials stay in environment variables.
function providerFrom(file, provider) {
  if (!existsSync(file)) return null;
  let section = '', values = {};
  const allowed = new Set(['name', 'base_url', 'env_key', 'wire_api', 'requires_openai_auth', 'supports_websockets']);
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const table = line.match(/^\s*\[([^\]]+)\]/);
    if (table) { section = table[1]; continue; }
    if (section !== `model_providers.${provider}`) continue;
    const entry = line.match(/^\s*(\w+)\s*=\s*(.*?)\s*$/);
    if (!entry || !allowed.has(entry[1])) continue;
    const raw = entry[2].replace(/\s+#.*$/, '');
    try { values[entry[1]] = JSON.parse(raw); } catch { if (/^'[^']*'$/.test(raw)) values[entry[1]] = raw.slice(1, -1); }
  }
  return values.base_url && values.name ? values : null;
}

export function configuredModels() {
  const home = process.env.CODEX_POCKET_CONFIG_DIR || process.env.CODEX_HOME || join(homedir(), '.codex');
  const defaultConfig=join(home,'config.toml');
  const header=existsSync(defaultConfig)?readFileSync(defaultConfig,'utf8').split(/^\s*\[/m)[0]:'';
  const setting=name=>{const m=header.match(new RegExp('^\\s*'+name+'\\s*=\\s*"([^"\\r\\n]+)"','m'));return m?.[1];};
  const definitions = process.env.CODEX_POCKET_MODELS_FILE
    ? JSON.parse(readFileSync(process.env.CODEX_POCKET_MODELS_FILE,'utf8'))
    : [{id:setting('model')||'configured-model',label:'Configured Codex model',shortName:'Codex',provider:setting('model_provider')||'openai',file:'config.toml',color:'#4969EE'}];
  if(!Array.isArray(definitions)||!definitions.length||definitions.length>32)throw new Error('Invalid model definitions');
  return definitions.map(d => {
    if(!/^[a-zA-Z0-9_.-]+\.toml$/.test(d.file)||!d.id||!d.provider)throw new Error('Invalid model definition');
    const settings = providerFrom(join(home, d.file), d.provider)||(d.provider==='openai'?{}:null);
    const available = !!settings && (d.provider==='openai'&&!settings.base_url
      ? (!!process.env.OPENAI_API_KEY||existsSync(join(home,'auth.json')))
      : (!settings.env_key || !!process.env[settings.env_key]));
    return { ...d, settings, available, unavailableReason: !settings ? '电脑缺少对应的 Codex 服务配置' : !available ? `电脑服务未读取到 ${settings.env_key}，请从已配置的 CMD 重新启动服务` : '' };
  });
}
export function publicModel(m) { const { settings, file, ...safe } = m; return safe; }
export function modelParams(m) {
  if (!m) throw Object.assign(new Error('请选择电脑上已配置的 Codex 模型'), { status: 400 });
  if (!m.available) throw Object.assign(new Error(m.unavailableReason), { status: 400 });
  return { model: m.id, modelProvider: m.provider, config: Object.keys(m.settings).length ? { [`model_providers.${m.provider}`]: m.settings } : {} };
}
