// Kontrak `turnIce()` di sisi klien web.
//
// Yang dijaga di sini bukan hanya "berhasil atau tidak", tetapi sebabnya.
// Dulu semua kegagalan menyusut jadi daftar kosong yang sama, dan akibatnya
// relay TURN yang hilang tidak pernah bisa dilaporkan ke pengguna.

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { transformWithOxc } from 'vite';

// Nilai `import.meta.env` (Vite) diganti literal supaya modul aslinya bisa
// dijalankan di vm Node dengan fetch tiruan — logika yang diuji tetap kode
// aslinya, bukan salinannya.
const source = readFileSync(new URL('../src/api.ts', import.meta.url), 'utf8')
  .replace(/import\.meta\.env\.VITE_SIGNAL_API/g, "'https://signal.test'")
  .replace(/import\.meta\.env\.VITE_SIGNAL_WS/g, 'undefined')
  .replace(/import\.meta\.env\.DEV/g, 'false')
  .replace(/^export /gm, '');
const { code } = await transformWithOxc(`${source}\nexports.turnIce = turnIce;`, 'api.ts');

function loadTurnIce(fetchImpl) {
  const exports = {};
  vm.runInNewContext(code, {
    exports, fetch: fetchImpl, Response, console, TextEncoder, TextDecoder, Uint8Array, URL,
  });
  return exports.turnIce;
}

const jsonResponse = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

test('kredensial diteruskan apa adanya, entri tanpa urls dibuang', async () => {
  const turnIce = loadTurnIce(async () =>
    jsonResponse({
      iceServers: [
        { urls: 'turn:relay.example:3478', username: 'u', credential: 'c' },
        { credential: 'tanpa-urls' },
      ],
      ttl: 86400,
    }),
  );
  const result = await turnIce('client-one', 'token');
  assert.equal(result.ok, true);
  assert.equal(result.reason, 'ok');
  assert.equal(result.servers.length, 1);
  assert.equal(result.servers[0].urls, 'turn:relay.example:3478');
});

test('penolakan 403 membawa sebab dan saran dari server', async () => {
  const hint = 'Token signaling tidak valid, sudah lewat 5 menit.';
  const turnIce = loadTurnIce(async () =>
    jsonResponse({ error: 'turn-forbidden', reason: 'token-invalid', hint }, 403),
  );
  const result = await turnIce('client-one', 'token-basi');
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'token-invalid');
  assert.equal(result.hint, hint);
  assert.equal(result.servers.length, 0);
});

test('penyedia belum dikonfigurasi terbedakan dari penolakan', async () => {
  const turnIce = loadTurnIce(async () =>
    jsonResponse({ error: 'turn-not-configured', hint: 'Isi TURN_STATIC_URLS.' }, 503),
  );
  const result = await turnIce('client-one', 'token');
  assert.equal(result.reason, 'turn-not-configured');
  assert.match(result.hint, /TURN_STATIC_URLS/);
});

test('gangguan otorisasi bukan penolakan akun', async () => {
  const turnIce = loadTurnIce(async () =>
    jsonResponse({ error: 'turn-auth-unavailable', hint: 'coba lagi' }, 503),
  );
  assert.equal((await turnIce('client-one', 'token')).reason, 'turn-auth-unavailable');
});

test('semua penyedia gagal: degraded dari server jadi sebab relay-failed', async () => {
  const turnIce = loadTurnIce(async () => jsonResponse({ iceServers: [], ttl: 3600, providers: [], degraded: true }));
  const result = await turnIce('client-one', 'token');
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'providers-failed');
});

test('balasan 200 tanpa daftar relay bukan kegagalan jaringan', async () => {
  const turnIce = loadTurnIce(async () => jsonResponse({}));
  assert.equal((await turnIce('client-one', 'token')).reason, 'no-servers');
});

test('jaringan mati menyebut dirinya sendiri, bukan daftar kosong', async () => {
  const turnIce = loadTurnIce(async () => {
    throw new Error('offline');
  });
  const result = await turnIce('client-one', 'token');
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'network');
  assert.match(result.hint, /server signaling/);
});

test('balasan bukan JSON tidak melempar ke pemanggil', async () => {
  const turnIce = loadTurnIce(async () => new Response('<html>gateway</html>', { status: 502 }));
  const result = await turnIce('client-one', 'token');
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'http-502');
});
