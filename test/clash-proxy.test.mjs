import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ClashProxy } from '../server/clash-proxy.mjs';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';

test('proxy requests accept only explicit boolean states and fixed helper actions', async () => {
  const actions = [];
  let enabled = false;
  const proxy = new ClashProxy({ platform: 'win32', invoke: async action => {
    actions.push(action);
    const next = action === 'Status' ? enabled : action === 'Enable';
    const changed = next !== enabled; enabled = next;
    return { available: true, enabled, canControl: true, changed, reason: '' };
  } });
  assert.equal((await proxy.status()).enabled, false);
  for (const invalid of [undefined, null, 'true', 'Disable', 1, {}, []]) {
    await assert.rejects(proxy.set(invalid), error => error.status === 400);
  }
  assert.deepEqual(actions, ['Status']);
  assert.equal((await proxy.set(true)).changed, true);
  assert.equal((await proxy.set(true)).changed, false);
  assert.equal((await proxy.set(false)).enabled, false);
  assert.deepEqual(actions, ['Status', 'Enable', 'Enable', 'Disable']);
});

test('proxy failures are actionable without leaking helper output or credentials', async () => {
  for (const code of ['not_installed', 'not_running', 'other_proxy', 'pac_enabled', 'native_unavailable', 'sync_timeout']) {
    const proxy = new ClashProxy({ platform: 'win32', invoke: async () => {
      throw Object.assign(new Error('private command line'), { stdout: JSON.stringify({ error: code, token: 'private-token' }) });
    } });
    await assert.rejects(proxy.set(true), error => error.status === 503 && !/private/.test(error.message));
  }
  const malformed = new ClashProxy({ platform: 'win32', invoke: async () => { throw new Error('private-token'); } });
  await assert.rejects(malformed.status(), error => !error.message.includes('private-token'));
});

test('unsupported computers cannot invoke a proxy helper', async () => {
  const proxy = new ClashProxy({ platform: 'linux', invoke: async () => { throw new Error('must not run'); } });
  assert.equal((await proxy.status()).canControl, false);
  await assert.rejects(proxy.set(true), error => error.status === 503);
});

test('Windows native helper synchronizes Verge and Windows, repairs old mismatches and requires acknowledgment', { skip: process.platform !== 'win32' }, async () => {
  const execute = promisify(execFile);
  const cases = [
    { enabled: false, server: 'old:1234', action: 'Enable', expected: true, changed: true },
    { enabled: true, server: 'fixture', action: 'Disable', expected: false, changed: true },
    { enabled: true, server: 'fixture', action: 'Enable', expected: true, changed: false },
    { enabled: true, server: 'fixture', action: 'Status', expected: true, changed: false },
    { enabled: false, server: 'old:1234', action: 'Enable', noAck: true, error: 'sync_timeout', expected: false },
    { enabled: true, server: 'other:1234', action: 'Disable', error: 'other_proxy', expected: true },
    { enabled: false, server: 'old:1234', action: 'Enable', offline: true, error: 'not_running', expected: false },
    { enabled: true, server: 'fixture', action: 'Disable', offline: true, expected: false, changed: true },
    { enabled: true, vergeEnabled: false, server: 'fixture', action: 'Enable', expected: true, changed: true, toggles: 1 },
    { enabled: true, vergeEnabled: false, server: 'fixture', action: 'Disable', expected: false, changed: true, toggles: 2 },
    { enabled: false, vergeEnabled: true, server: 'fixture', action: 'Enable', expected: true, changed: true, toggles: 2 },
    { enabled: false, server: 'fixture', action: 'Enable', missingShortcut: true, error: 'native_unavailable', expected: false },
  ];
  for (const fixture of cases) {
    const dir = mkdtempSync(join(tmpdir(), 'pocket-proxy-fixture-'));
    try {
      const path = join(dir, 'fixture.json'); writeFileSync(path, JSON.stringify(fixture));
      let stdout;
      try {
        ({ stdout } = await execute(join(process.env.SystemRoot, 'System32/WindowsPowerShell/v1.0/powershell.exe'),
          ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', resolve('test/clash-proxy-windows-fixture.ps1'), '-FixturePath', path, '-Action', fixture.action],
          { windowsHide: true, timeout: 15000 }));
      } catch (error) { stdout = error.stdout; }
      const response = JSON.parse(stdout.trim().replace(/^\uFEFF/, ''));
      const saved = JSON.parse(readFileSync(path + '.result', 'utf8').replace(/^\uFEFF/, ''));
      assert.equal(Boolean(saved.registry.ProxyEnable), fixture.expected, JSON.stringify(fixture));
      assert.equal(saved.registry.ProxyOverride, 'localhost;100.*;<local>');
      if (fixture.error) {
        assert.equal(response.error, fixture.error);
        assert.equal(saved.toggles, fixture.noAck ? 1 : 0, 'Never blindly retry a toggle with missing acknowledgment');
      } else {
        assert.equal(response.enabled, fixture.expected);
        assert.equal(response.changed, fixture.changed);
        assert.equal(response.vergeEnabled, fixture.expected);
        assert.equal(response.synchronized, true);
        assert.equal(saved.vergeEnabled, fixture.expected);
        assert.equal(saved.toggles, fixture.toggles ?? (fixture.changed ? 1 : 0));
      }
    } finally { rmSync(dir, { recursive: true, force: true }); }
  }
});
