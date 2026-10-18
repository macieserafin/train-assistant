import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cp, mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';

async function fixture(t) {
  const root = await mkdtemp(join(tmpdir(), 'train-assistant-build-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  for (const name of ['scripts', 'index.html', 'css', 'js', 'package.json']) {
    await cp(new URL(`../${name}`, import.meta.url), join(root, name), { recursive: true });
  }
  return root;
}

function build(root, apiBaseUrl, ...args) {
  return spawnSync(process.execPath, ['scripts/build.js', ...args], {
    cwd: root,
    env: { ...process.env, API_BASE_URL: apiBaseUrl },
    encoding: 'utf8',
  });
}

test('WWW bez adresu API buduje kompletny frontend z adresami względnymi', async (t) => {
  const root = await fixture(t);
  const result = build(root, '');
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual((await readdir(join(root, 'dist'))).sort(), ['css', 'index.html', 'js']);
  const config = await import(pathToFileURL(join(root, 'dist/js/config.js')));
  assert.equal(config.apiBaseUrl, '');
  assert.match(await readFile(join(root, 'dist/index.html'), 'utf8'), /js\/app.js/);
  await readFile(join(root, 'dist/css/style.css'));
});

test('iOS wysyła zapytania do skonfigurowanego VPS i zachowuje parametry oraz signal', async (t) => {
  const root = await fixture(t);
  const result = build(root, 'https://api.example.com/', '--ios');
  assert.equal(result.status, 0, result.stderr);
  const requests = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    requests.push({ url: new URL(url), options });
    return { ok: true, json: async () => ({ proposals: [] }) };
  });
  const api = await import(pathToFileURL(join(root, 'dist/js/api.js')));
  const controller = new AbortController();
  await api.searchStations('Gdańsk Główny', controller.signal);
  await api.searchConnections('1', '2', '2026-10-18T08:00', 'all', controller.signal);
  assert.equal(requests[0].url.origin, 'https://api.example.com');
  assert.equal(requests[0].url.pathname, '/api/stations');
  assert.equal(requests[0].url.searchParams.get('search'), 'Gdańsk Główny');
  assert.equal(requests[1].url.pathname, '/api/connections');
  assert.equal(requests[1].url.searchParams.get('mode'), 'all');
  assert.equal(requests[1].options.signal, controller.signal);
});

test('iOS odrzuca brak API, HTTP i adres zawierający /api lub dane logowania', async (t) => {
  const root = await fixture(t);
  for (const value of ['', 'http://api.example.com', 'https://api.example.com/api', 'https://user:secret@api.example.com']) {
    assert.notEqual(build(root, value, '--ios').status, 0, value);
  }
});
