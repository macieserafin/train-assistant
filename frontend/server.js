import http from 'node:http';
import { readFile } from 'node:fs/promises';

const files = {
  '/': ['index.html', 'text/html; charset=utf-8'],
  '/index.html': ['index.html', 'text/html; charset=utf-8'],
};

for (const name of ['api.js', 'app.js', 'journey.js', 'results.js', 'station-picker.js', 'storage.js']) {
  files[`/js/${name}`] = [`js/${name}`, 'text/javascript; charset=utf-8'];
}

for (const name of ['base.css', 'journey.css', 'results.css', 'stations.css', 'style.css']) {
  files[`/css/${name}`] = [`css/${name}`, 'text/css; charset=utf-8'];
}

// Ten sam adres dla strony i API — przeglądarka nie potrzebuje konfiguracji CORS.
const server = http.createServer(async (req, res) => {
  const path = new URL(req.url, 'http://localhost').pathname;
  if (path.startsWith('/api/')) {
    const upstream = http.request({
      hostname: '127.0.0.1', port: 8080, path: req.url, method: req.method,
      headers: { accept: 'application/json' },
    }, (response) => {
      res.writeHead(response.statusCode, { 'Content-Type': response.headers['content-type'] || 'application/json' });
      response.pipe(res);
    });
    upstream.setTimeout(30000, () => upstream.destroy());
    upstream.on('error', () => {
      if (!res.headersSent) {
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'Uruchom backend na porcie 8080.' }));
      } else res.destroy();
    });
    req.pipe(upstream);
    return;
  }
  const file = files[path];
  if (!file) { res.writeHead(404); res.end('Nie znaleziono strony.'); return; }
  try {
    const body = await readFile(new URL(file[0], import.meta.url));
    res.writeHead(200, { 'Content-Type': file[1], 'Cache-Control': 'no-store' });
    res.end(body);
  } catch {
    res.writeHead(500);
    res.end('Nie udało się wczytać strony.');
  }
});
server.listen(5173, '127.0.0.1', () => console.log('Frontend: http://localhost:5173'));
