#!/usr/bin/env node
'use strict';
// Gateway RDP darurat XyDesk — browser -> WebSocket -> guacd -> RDP target.
//
// Alur token (dua lapis, kunci AES TIDAK pernah ke browser):
//   1. Operator mencetak token HMAC target-terkunci (mint.js) untuk pengguna.
//   2. Halaman klien POST /session {token, username, password} — bridge
//      memverifikasi HMAC (host/port target ikut ditandatangani, anti-SSRF)
//      lalu MEMBUAT token guacamole-lite (AES-256-CBC) di sisi server.
//   3. Klien membuka WebSocket dengan token AES sekali pakai itu;
//      guacamole-lite mendekripsinya, bridge memvalidasi TTL, lalu guacd
//      berbicara RDP ke target.
//
// Kredensial RDP hanya hidup di memori proses (request body + token AES) —
// tidak pernah disimpan atau ditulis ke log. TLS: reverse proxy (wss://).

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const GuacamoleLite = require('guacamole-lite');
const { verify } = require('./token');

const PORT = Number(process.env.PORT || 8080);
const SECRET = process.env.GATEWAY_SECRET || '';
const GUACD_HOST = process.env.GUACD_HOST || '127.0.0.1';
const GUACD_PORT = Number(process.env.GUACD_PORT || 4822);
const MAX_SESSIONS = Number(process.env.MAX_SESSIONS || 8);
const SESSION_TTL = Number(process.env.SESSION_TTL || 300); // detik, berlaku untuk token WS

if (!SECRET || SECRET.length < 16) {
  console.error('GATEWAY_SECRET wajib diisi (>=16 karakter). Contoh: openssl rand -hex 32');
  process.exit(1);
}

const aesKey = crypto.createHash('sha256').update(SECRET).digest(); // 32 byte untuk AES-256
const log = (msg) => console.log(`[gateway] ${new Date().toISOString()} ${msg}`);

// ---- Halaman klien mini + guacamole-common-js dari node_modules ----------
const PUB = path.join(__dirname, 'public');
const GCJS = path.join(__dirname, 'node_modules', 'guacamole-common-js', 'dist', 'esm', 'guacamole-common.min.js');
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css' };

// Token AES guacamole-lite dibuat HANYA di sini (format mengikuti
// guacamole-lite/lib/Server.js#decryptToken: base64 JSON {iv, value}).
function mintWsToken(connectionSettings) {
  const iv = crypto.randomBytes(16);
  const cipher = crypto.createCipheriv('aes-256-cbc', aesKey, iv);
  let value = cipher.update(JSON.stringify(connectionSettings), 'utf8', 'base64');
  value += cipher.final('base64');
  return Buffer.from(JSON.stringify({ iv: iv.toString('base64'), value })).toString('base64');
}

function readBody(req, limit = 16 * 1024) {
  return new Promise((resolve, reject) => {
    let n = 0;
    const chunks = [];
    req.on('data', (c) => {
      n += c.length;
      if (n > limit) {
        reject(new Error('body terlalu besar'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = (req.url || '/').split('?')[0];
  try {
    if (url === '/' || url === '/index.html') return send(res, path.join(PUB, 'index.html'));
    if (url === '/guacamole-common.js') return send(res, GCJS);
    if (url === '/healthz') {
      res.writeHead(200, { 'content-type': 'application/json' });
      return res.end(JSON.stringify({ ok: true, sessions: liveSessions }));
    }
    if (url === '/session' && req.method === 'POST') {
      const raw = await readBody(req);
      let body;
      try {
        body = JSON.parse(raw);
      } catch {
        res.writeHead(400, { 'content-type': 'application/json' });
        return res.end(JSON.stringify({ error: 'body tidak sah' }));
      }
      try {
        const t = verify(String(body.token || ''), SECRET); // target terkunci + TTL
        if (liveSessions >= MAX_SESSIONS) throw new Error('sesi gateway penuh');
        const wsToken = mintWsToken({
          connection: {
            type: 'rdp',
            settings: {
              hostname: t.host,
              port: String(t.port),
              username: String(body.username || ''),
              password: String(body.password || ''),
              'ignore-cert': 'true',
              'enable-wallpaper': 'false',
              'enable-font-smoothing': 'true',
            },
          },
          exp: Math.floor(Date.now() / 1000) + SESSION_TTL,
          target: `${t.host}:${t.port}`,
        });
        log(`sesi disiapkan -> ${t.host}:${t.port}`);
        res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
        return res.end(JSON.stringify({ token: wsToken }));
      } catch (e) {
        log(`sesi ditolak: ${e.message}`);
        res.writeHead(401, { 'content-type': 'application/json' });
        return res.end(JSON.stringify({ error: e.message }));
      }
    }
    res.writeHead(404);
    res.end('tidak ditemukan');
  } catch (e) {
    res.writeHead(500, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ error: 'galat server' }));
  }
});

function send(res, file) {
  fs.readFile(file, (err, buf) => {
    if (err) {
      res.writeHead(404);
      return res.end('tidak ditemukan');
    }
    res.writeHead(200, { 'content-type': MIME[path.extname(file)] || 'application/octet-stream', 'cache-control': 'no-store' });
    res.end(buf);
  });
}

// ---- guacamole-lite menempel di http server yang sama --------------------
let liveSessions = 0;

const guacd = new GuacamoleLite(
  { server }, // ws mengambil alih upgrade pada server ini
  { host: GUACD_HOST, port: GUACD_PORT },
  {
    log: {
      level: 'ERROR',
      stdLog: () => {},
      errorLog: (m) => log(`guacd: ${m}`),
    },
    crypt: { key: aesKey }, // key tidak pernah dibagikan ke klien
    processConnectionSettings: (settings, callback) => {
      // Lapis kedua: token WS harus belum kedaluwarsa (ditanam saat /session).
      const exp = Number(settings && settings.exp);
      if (!Number.isFinite(exp) || exp < Date.now() / 1000) {
        return callback(new Error('token sesi kedaluwarsa'));
      }
      liveSessions += 1;
      log(`sesi dibuka -> ${settings.target || '?'} (aktif: ${liveSessions})`);
      callback(undefined, settings);
    },
    sessionRegistry: {
      onOpen: () => {},
      onClose: () => {
        liveSessions = Math.max(0, liveSessions - 1);
      },
    },
  },
);

guacd.on('close', () => {
  liveSessions = Math.max(0, liveSessions - 1);
});

server.listen(PORT, '0.0.0.0', () => log(`gateway siap di :${PORT} (guacd ${GUACD_HOST}:${GUACD_PORT})`));
