'use strict';
// Token gateway RDP — HMAC-SHA256 dengan kedaluwarsa, pola sama dengan
// skema `ts.purpose.sig` milik signaling XyDesk (purpose terpisah: "gw").
//
// Payload token MENENTUKAN target (host/port) dan ikut ditandatangani,
// sehingga pemegang token tidak bisa mengarahkan gateway ke sembarang host
// (proteksi SSRF) dan tidak bisa memperpanjang masa berlaku.

const crypto = require('crypto');

const PURPOSE = 'gw.v1';

/** payload: { host, port, exp (detik epoch), sub (label opsional) } */
function sign(payload, secret) {
  if (!payload || typeof payload.host !== 'string' || !payload.host) throw new Error('host wajib');
  const port = Number(payload.port);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('port tidak sah');
  const exp = Number(payload.exp);
  if (!Number.isFinite(exp)) throw new Error('exp wajib');
  const body = Buffer.from(JSON.stringify({
    p: PURPOSE,
    h: payload.host.trim(),
    t: port,
    x: exp,
    s: String(payload.sub || ''),
  })).toString('base64url');
  const mac = crypto.createHmac('sha256', String(secret)).update(body).digest('base64url');
  return body + '.' + mac;
}

/** Kembalikan payload bila token sah & belum kedaluwarsa; selain itu throw. */
function verify(token, secret, nowSec = Math.floor(Date.now() / 1000)) {
  if (typeof token !== 'string' || !token.includes('.')) throw new Error('bentuk token tidak sah');
  const [body, mac] = token.split('.');
  const want = crypto.createHmac('sha256', String(secret)).update(body).digest('base64url');
  const a = Buffer.from(mac);
  const b = Buffer.from(want);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) throw new Error('tanda tangan tidak cocok');
  let payload;
  try {
    payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
  } catch {
    throw new Error('payload tidak sah');
  }
  if (payload.p !== PURPOSE) throw new Error('purpose tidak dikenal');
  if (Number(payload.x) < nowSec) throw new Error('token kedaluwarsa');
  return { host: String(payload.h), port: Number(payload.t), exp: Number(payload.x), sub: String(payload.s || '') };
}

module.exports = { sign, verify, PURPOSE };
