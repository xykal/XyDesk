#!/usr/bin/env node
'use strict';
// Cetak token gateway untuk satu target. HANYA dijalankan operator di server.
//   node mint.js --host 192.168.1.5 --port 3389 --ttl 300 [--sub label]
const { sign } = require('./token');

const args = process.argv.slice(2);
const get = (k, d) => {
  const i = args.indexOf('--' + k);
  return i >= 0 && args[i + 1] ? args[i + 1] : d;
};

const secret = process.env.GATEWAY_SECRET || '';
if (!secret) {
  console.error('GATEWAY_SECRET belum diisi di lingkungan.');
  process.exit(1);
}
const host = get('host');
const port = Number(get('port', '3389'));
const ttl = Number(get('ttl', '300'));
const sub = get('sub', '');

try {
  const exp = Math.floor(Date.now() / 1000) + ttl;
  const token = sign({ host, port, exp, sub }, secret);
  console.log(token);
} catch (e) {
  console.error('Gagal: ' + e.message);
  process.exit(1);
}
