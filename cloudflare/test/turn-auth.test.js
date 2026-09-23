// Kontrak penolakan `/turn-ice` dan enforcement ban/revoke pada jalur relay.
//
// Kenapa diuji sampai ke bentuk balasan: relay TURN yang hilang tanpa pesan
// adalah kegagalan yang paling mahal di proyek ini. Host dan client hanya
// bisa melaporkan "relay tidak tersedia" bila endpoint memberi tahu SEBABNYA
// — token basi, perangkat salah, akun dicabut, penyedia belum dikonfigurasi,
// atau server otorisasi sedang mati. Empat sebab pertama adalah keputusan,
// yang kelima bukan; menyamakan keduanya membuat gangguan sesaat terbaca
// sebagai akun dinonaktifkan.

import test from 'node:test';
import assert from 'node:assert/strict';

import worker, { signSignalToken } from '../src/worker.js';
import { signBoundTicket } from '../src/bound_ticket.js';
import { resetTurnCache } from '../src/turn.js';

const SECRET = 'turn-auth-test-secret';
const ADMIN = 'turn-auth-test-admin';
const STATIC_SECRET = 'turn-auth-test-static';

const memberPrincipal = () => ({
  sub: 'account-uuid',
  guest: false,
  ver: 1,
  expiresAt: Math.floor(Date.now() / 1000) + 3600,
});

/// Bindings tiruan. `principalStatus` menentukan jawaban AuthStore untuk
/// `check-principal` — 200 berarti masih sah, 401/403 berarti dicabut.
function bindings({ principalStatus = 200, principalThrows = false, turn = true } = {}) {
  return {
    XYDESK_SECRET: SECRET,
    ADMIN_SECRET: ADMIN,
    ...(turn ? { TURN_STATIC_URLS: 'turn:relay.example:3478', TURN_STATIC_SECRET: STATIC_SECRET } : {}),
    AUTH_STORE: {
      idFromName: (name) => name,
      get: () => ({
        fetch: async (request) => {
          if (principalThrows) throw new Error('authstore-down');
          if (String(request.url).includes('check-principal')) {
            return new Response(principalStatus === 200 ? '{"ok":true}' : '{"error":"unauthorized"}', {
              status: principalStatus,
            });
          }
          return new Response('{}', { status: principalStatus });
        },
      }),
    },
  };
}

function get(path, env, headers = {}) {
  resetTurnCache();
  return worker.fetch(new Request(`https://signal.example${path}`, { headers }), env);
}

test('tanpa token: sebeb disebut, bukan sekadar 403 kosong', async () => {
  const res = await get('/turn-ice?id=client-one', bindings());
  assert.equal(res.status, 403);
  const body = await res.json();
  assert.equal(body.error, 'turn-forbidden');
  assert.equal(body.reason, 'no-credentials');
  assert.match(body.hint, /signal-token/);
});

test('token client tidak bisa dipakai meminta TURN role host', async () => {
  const token = await signSignalToken('client-one', 'client', SECRET);
  const res = await get(`/turn-ice?id=client-one&role=host&token=${token}`, bindings());
  assert.equal(res.status, 403);
  assert.equal((await res.json()).reason, 'token-invalid');
});

test('token signaling kedaluwarsa ditolak dengan alasan yang bisa ditindaklanjuti', async () => {
  const stale = Math.floor(Date.now() / 1000) - 301;
  const token = await signSignalToken('client-one', 'client', SECRET, stale);
  const res = await get(`/turn-ice?id=client-one&token=${token}`, bindings());
  assert.equal(res.status, 403);
  const body = await res.json();
  assert.equal(body.reason, 'token-invalid');
  assert.match(body.hint, /token baru/);
});

test('tiket terikat ditolak bila diminta sebagai host', async () => {
  const ticket = await signBoundTicket('client-one', memberPrincipal(), SECRET);
  const res = await get(`/turn-ice?id=client-one&role=host&token=${ticket}`, bindings());
  assert.equal(res.status, 403);
  assert.equal((await res.json()).reason, 'ticket-invalid');
});

test('akun yang dicabut tidak mendapat kredensial relay — alasan dibedakan dari token basi', async () => {
  const ticket = await signBoundTicket('client-one', memberPrincipal(), SECRET);
  const res = await get(`/turn-ice?id=client-one&token=${ticket}`, bindings({ principalStatus: 401 }));
  assert.equal(res.status, 403);
  const body = await res.json();
  assert.equal(body.reason, 'ticket-revoked');
  assert.match(body.hint, /Login ulang/);
});

test('gangguan AuthStore bukan penolakan: 503, bukan 403', async () => {
  const ticket = await signBoundTicket('client-one', memberPrincipal(), SECRET);
  const res = await get(`/turn-ice?id=client-one&token=${ticket}`, bindings({ principalThrows: true }));
  assert.equal(res.status, 503);
  assert.equal((await res.json()).error, 'turn-auth-unavailable');
});

test('penolakan tidak membocorkan rincian kripto apa pun', async () => {
  const res = await get('/turn-ice?id=client-one', bindings());
  const body = await res.json();
  assert.deepEqual(Object.keys(body).sort(), ['error', 'hint', 'reason']);
});

test('token host yang sah mendapat kredensial, host dan client sama-sama dilayani', async () => {
  for (const role of ['host', 'client']) {
    const token = await signSignalToken('client-one', role, SECRET);
    const res = await get(`/turn-ice?id=client-one&role=${role}&token=${token}`, bindings());
    assert.equal(res.status, 200, `role ${role} harus dilayani`);
    const body = await res.json();
    assert.ok(body.iceServers.length > 0);
    assert.equal(body.degraded, false);
  }
});

test('penyedia belum dikonfigurasi tetap 503 dengan petunjuk, sesudah auth lolos', async () => {
  const token = await signSignalToken('client-one', 'client', SECRET);
  const res = await get(`/turn-ice?id=client-one&token=${token}`, bindings({ turn: false }));
  assert.equal(res.status, 503);
  const body = await res.json();
  assert.equal(body.error, 'turn-not-configured');
  assert.match(body.hint, /TURN_STATIC_URLS/);
});

test('header X-Admin tetap jalan tanpa token perangkat', async () => {
  const res = await get('/turn-ice', bindings(), { 'X-Admin': ADMIN });
  assert.equal(res.status, 200);
});

test('X-Admin salah tidak dianggap admin', async () => {
  const res = await get('/turn-ice', bindings(), { 'X-Admin': 'bukan-admin' });
  assert.equal(res.status, 403);
  assert.equal((await res.json()).reason, 'no-credentials');
});

// ── Enforcement ban/revoke pada jalur yang menerbitkan tiket baru ────────
// Tiket yang sudah beredar diperiksa di setiap pemakaian (bound-ticket.test.js).
// Yang dijaga di sini adalah pintu masuknya: akun yang sudah dicabut tidak
// boleh mendapat tiket BARU, karena tiket baru berarti lima menit akses lagi.

test('akun dicabut tidak bisa menukar JWT menjadi tiket signaling baru', async () => {
  const env = bindings();
  env.AUTH_STORE.get = () => ({
    fetch: async (request) =>
      String(request.url).includes('authorize-session')
        ? new Response('{"error":"unauthorized"}', { status: 401 })
        : new Response('{}', { status: 200 }),
  });
  const res = await worker.fetch(
    new Request('https://signal.example/signal-token?id=client-one', {
      headers: { Authorization: 'Bearer jwt-lama-milik-akun-dicabut' },
    }),
    env,
  );
  assert.equal(res.status, 401);
  assert.equal(await res.text(), 'unauthorized');
});

test('AuthStore mati saat menukar tiket: 503, bukan tiket kadaluarsa diam-diam', async () => {
  const env = bindings();
  env.AUTH_STORE.get = () => ({ fetch: async () => { throw new Error('down'); } });
  const res = await worker.fetch(
    new Request('https://signal.example/signal-token?id=client-one', {
      headers: { Authorization: 'Bearer jwt-apa-pun' },
    }),
    env,
  );
  assert.equal(res.status, 503);
});
