'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { sign, verify } = require('../token');

const SECRET = 'rahasia-uji-panjang-sekali-1234';
const now = 1_700_000_000;

test('tanda tangan sah lolos dan payload kembali utuh', () => {
  const tok = sign({ host: '192.168.1.5', port: 3389, exp: now + 300, sub: 'uji' }, SECRET);
  const p = verify(tok, SECRET, now);
  assert.equal(p.host, '192.168.1.5');
  assert.equal(p.port, 3389);
  assert.equal(p.sub, 'uji');
});

test('token kedaluwarsa ditolak', () => {
  const tok = sign({ host: 'h', port: 3389, exp: now - 1 }, SECRET);
  assert.throws(() => verify(tok, SECRET, now), /kedaluwarsa/);
});

test('payload yang dimodifikasi gagal verifikasi', () => {
  const tok = sign({ host: '10.0.0.1', port: 3389, exp: now + 60 }, SECRET);
  const [body, mac] = tok.split('.');
  const data = JSON.parse(Buffer.from(body, 'base64url').toString());
  data.h = '10.6.6.6'; // coba alihkan target (SSRF)
  const bad = Buffer.from(JSON.stringify(data)).toString('base64url') + '.' + mac;
  assert.throws(() => verify(bad, SECRET, now), /tanda tangan/);
});

test('secret berbeda ditolak', () => {
  const tok = sign({ host: 'h', port: 3389, exp: now + 60 }, SECRET);
  assert.throws(() => verify(tok, 'secret-lain-sama-sama-panjang', now), /tanda tangan/);
});

test('port & host tidak sah ditolak saat menandatangani', () => {
  assert.throws(() => sign({ host: '', port: 3389, exp: now + 60 }, SECRET), /host/);
  assert.throws(() => sign({ host: 'h', port: 70000, exp: now + 60 }, SECRET), /port/);
  assert.throws(() => sign({ host: 'h', port: 3389 }, SECRET), /exp/);
});

test('bentuk token ngawur ditolak', () => {
  assert.throws(() => verify('asal-asalan', SECRET, now), /bentuk/);
  assert.throws(() => verify('a.b', SECRET, now), /tanda tangan/);
});
