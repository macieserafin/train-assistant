import { cp, mkdir, rm, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = new URL('../', import.meta.url);
const envFile = new URL('.env', root);
if (existsSync(envFile)) process.loadEnvFile(fileURLToPath(envFile));

const ios = process.argv.includes('--ios');
const apiBaseUrl = (process.env.API_BASE_URL || '').trim().replace(/\/+$/, '');
if (ios && !apiBaseUrl) {
  throw new Error('Ustaw API_BASE_URL w frontend/.env na adres HTTPS backendu na VPS-ie.');
}
if (apiBaseUrl) {
  const url = new URL(apiBaseUrl);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('API_BASE_URL musi być adresem HTTPS bez ścieżki, danych logowania, parametrów ani /api.');
  }
}

// Kopiujemy tylko publiczne zasoby, nigdy .env, serwer ani node_modules.
const dist = new URL('dist/', root);
await rm(dist, { recursive: true, force: true });
await mkdir(dist, { recursive: true });
for (const entry of ['index.html', 'css', 'js']) {
  await cp(new URL(entry, root), new URL(entry, dist), { recursive: true });
}
await writeFile(new URL('js/config.js', dist), `export const apiBaseUrl = ${JSON.stringify(apiBaseUrl)};\n`);
console.log(`Frontend gotowy w dist; API: ${apiBaseUrl || '/api (ten sam serwer)'}`);
