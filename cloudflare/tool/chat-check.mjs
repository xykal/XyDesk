// Uji chat global ujung-ke-ujung di runtime workerd asli (Miniflare), bukan
// tiruan.
//
// Tes unit di test/chat.test.js membuktikan aturannya; berkas ini
// membuktikan bahwa aturan itu benar-benar berlaku saat dirangkai: Worker →
// AUTH_STORE (verifikasi JWT) → Durable Object ChatRoom → WebSocket, dengan
// dua peserta yang betul-betul saling mengirim pesan.
//
// Jalankan: npm run test:chat  (butuh .wrangler/runtime-test hasil dry-run)

import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const wranglerRequire = createRequire(require.resolve('wrangler/package.json'));
const { Miniflare, convertV4MiniflareOptions } = wranglerRequire('miniflare');

const secret = 'chat-runtime-test-only';
const options = {
  workers: [{
    name: 'chat',
    modules: true,
    scriptPath: fileURLToPath(new URL('../.wrangler/runtime-test/entry.js', import.meta.url)),
    compatibilityDate: '2026-08-17',
    bindings: {
      AUTH_SECRET: secret,
      XYDESK_SECRET: secret,
      XYDESK_DEV: 'true',
      CORS_ORIGINS: 'https://app.xydesk.my.id',
    },
    durableObjects: {
      AUTH_STORE: { className: 'AuthStore', useSQLite: true },
      HUB: { className: 'Hub', useSQLite: true },
      CHAT: { className: 'ChatRoom', useSQLite: true },
    },
  }],
};

const mf = new Miniflare(convertV4MiniflareOptions ? convertV4MiniflareOptions(options) : options);

/** Daftar akun lewat jalur OTP asli; dev mode mengembalikan kodenya. */
async function signIn(email, name) {
  const asked = await mf.dispatchFetch('https://signal.xydesk.my.id/auth/request-otp', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, name }),
  });
  assert.equal(asked.status, 200, `request-otp ${email}`);
  const { dev_otp: otp } = await asked.json();
  assert.ok(otp, 'dev_otp harus ada saat XYDESK_DEV=true');

  const verified = await mf.dispatchFetch('https://signal.xydesk.my.id/auth/verify-otp', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, otp }),
  });
  assert.equal(verified.status, 200, `verify-otp ${email}`);
  const body = await verified.json();
  assert.ok(body.token, 'token JWT harus terbit');
  return body.token;
}

/** Buka WebSocket chat dan kumpulkan pesannya. */
async function joinChat(token) {
  const res = await mf.dispatchFetch(`https://signal.xydesk.my.id/chat/ws?token=${token}`, {
    headers: { Upgrade: 'websocket' },
  });
  if (res.status !== 101) {
    console.error('badan balasan:', await res.text());
  }
  assert.equal(res.status, 101, 'upgrade WebSocket harus diterima');
  const ws = res.webSocket;
  assert.ok(ws, 'response harus membawa webSocket');
  const inbox = [];
  ws.addEventListener('message', event => {
    inbox.push(JSON.parse(event.data));
  });
  ws.accept();
  return { ws, inbox };
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

async function waitFor(check, label, timeoutMs = 3000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = check();
    if (value) return value;
    await sleep(25);
  }
  throw new Error(`timeout menunggu: ${label}`);
}

let failures = 0;
function pass(label) {
  console.log(`  ok  ${label}`);
}
function fail(label, error) {
  failures += 1;
  console.error(`  FAIL ${label}: ${error.message}`);
}
async function step(label, body) {
  try {
    await body();
    pass(label);
  } catch (error) {
    fail(label, error);
  }
}

try {
  console.log('chat-check: runtime workerd, dua peserta sungguhan');

  // ── Gerbang sebelum ada peserta ──
  await step('tanpa token: 401', async () => {
    const res = await mf.dispatchFetch('https://signal.xydesk.my.id/chat/history');
    assert.equal(res.status, 401);
  });

  await step('token palsu: 401', async () => {
    const res = await mf.dispatchFetch('https://signal.xydesk.my.id/chat/history', {
      headers: { Authorization: 'Bearer palsu.palsu.palsu' },
    });
    assert.equal(res.status, 401);
  });

  await step('origin asing ditolak 403', async () => {
    const res = await mf.dispatchFetch('https://signal.xydesk.my.id/chat/history', {
      headers: { Origin: 'https://jahat.example' },
    });
    assert.equal(res.status, 403);
  });

  const budiToken = await signIn('budi@xydesk.my.id', 'Budi Santoso');
  const sitiToken = await signIn('siti@xydesk.my.id');

  await step('riwayat awal kosong untuk akun sah', async () => {
    const res = await mf.dispatchFetch('https://signal.xydesk.my.id/chat/history', {
      headers: { Authorization: `Bearer ${budiToken}` },
    });
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { messages: [] });
  });

  // ── Dua peserta masuk ruang ──
  const budi = await joinChat(budiToken);
  const siti = await joinChat(sitiToken);

  await step('keduanya menerima sambutan dengan nama masing-masing', async () => {
    const a = await waitFor(() => budi.inbox.find(m => m.type === 'welcome'), 'welcome budi');
    const b = await waitFor(() => siti.inbox.find(m => m.type === 'welcome'), 'welcome siti');
    assert.equal(a.you, 'Budi Santoso', 'nama profil dipakai bila ada');
    assert.equal(b.you, 'siti', 'tanpa nama profil, jatuh ke bagian sebelum @');
    assert.equal(a.tier, 'free');
  });

  await step('jumlah orang di ruang terlihat', async () => {
    const presence = await waitFor(
      () => budi.inbox.filter(m => m.type === 'presence').pop(),
      'presence',
    );
    assert.equal(presence.online, 2);
  });

  await step('pesan Budi sampai ke Siti', async () => {
    budi.ws.send(JSON.stringify({ type: 'msg', text: 'halo semua' }));
    const got = await waitFor(() => siti.inbox.find(m => m.type === 'msg'), 'msg di siti');
    assert.equal(got.text, 'halo semua');
    assert.equal(got.from, 'Budi Santoso');
    assert.equal(got.tier, 'free');
    assert.ok(Number.isInteger(got.hue) && got.hue >= 0 && got.hue < 360);
  });

  await step('alamat email tidak pernah ikut tersiar', async () => {
    const semua = JSON.stringify([...budi.inbox, ...siti.inbox]);
    assert.ok(!semua.includes('budi@xydesk.my.id'), 'email pengirim bocor');
    assert.ok(!semua.includes('siti@xydesk.my.id'), 'email penerima bocor');
    assert.ok(!semua.includes('@xydesk.my.id'), 'ada alamat email di payload');
  });

  await step('rem laju menahan pesan kedua yang terlalu cepat', async () => {
    budi.ws.send(JSON.stringify({ type: 'msg', text: 'dua' }));
    const error = await waitFor(() => budi.inbox.find(m => m.type === 'error'), 'error rem laju');
    assert.equal(error.reason, 'terlalu-cepat');
    assert.ok(error.retryMs > 0);
    assert.equal(siti.inbox.filter(m => m.type === 'msg').length, 1, 'pesan tertahan ikut tersiar');
  });

  await step('pesan kosong dan jenis asing ditolak tanpa menyentuh ruang', async () => {
    siti.ws.send(JSON.stringify({ type: 'msg', text: '   ' }));
    const kosong = await waitFor(
      () => siti.inbox.find(m => m.type === 'error' && m.reason === 'kosong'),
      'error kosong',
    );
    assert.equal(kosong.reason, 'kosong');
    siti.ws.send(JSON.stringify({ type: 'kick', id: 'budi' }));
    await waitFor(
      () => siti.inbox.find(m => m.type === 'error' && m.reason === 'jenis-tidak-dikenal'),
      'error jenis',
    );
  });

  await step('ping dibalas pong', async () => {
    siti.ws.send(JSON.stringify({ type: 'ping' }));
    const pong = await waitFor(() => siti.inbox.find(m => m.type === 'pong'), 'pong');
    assert.ok(pong.at > 0);
  });

  await step('setelah jeda, pesan berikutnya diterima lagi', async () => {
    await sleep(800);
    budi.ws.send(JSON.stringify({ type: 'msg', text: 'sudah boleh lagi' }));
    await waitFor(
      () => siti.inbox.find(m => m.type === 'msg' && m.text === 'sudah boleh lagi'),
      'pesan kedua',
    );
  });

  await step('karakter kontrol dibersihkan sebelum tersiar', async () => {
    siti.ws.send(JSON.stringify({ type: 'msg', text: 'a\u202Eb\u0000 c' }));
    const got = await waitFor(
      () => budi.inbox.find(m => m.type === 'msg' && m.from === 'siti'),
      'pesan siti',
    );
    assert.equal(got.text, 'ab c');
  });

  await step('peserta baru menerima riwayat yang sudah ada', async () => {
    const tamuToken = await signIn('tamu@xydesk.my.id', 'Tamu Baru');
    const tamu = await joinChat(tamuToken);
    const welcome = await waitFor(() => tamu.inbox.find(m => m.type === 'welcome'), 'welcome tamu');
    assert.equal(welcome.messages.length, 3, 'tiga pesan yang lolos rem laju');
    assert.equal(welcome.messages[0].text, 'halo semua');
    assert.equal(welcome.online, 3);
    tamu.ws.close();
  });

  await step('riwayat lewat HTTP sama dengan yang disiarkan', async () => {
    const res = await mf.dispatchFetch('https://signal.xydesk.my.id/chat/history', {
      headers: { Authorization: `Bearer ${budiToken}` },
    });
    const body = await res.json();
    assert.equal(body.messages.length, 3);
    assert.ok(body.messages.every(m => m.tier === 'free'));
    assert.ok(!JSON.stringify(body).includes('@xydesk.my.id'));
  });

  budi.ws.close();
  siti.ws.close();
} finally {
  await mf.dispose();
}

if (failures) {
  console.error(`chat-check: ${failures} pemeriksaan GAGAL`);
  process.exit(1);
}
console.log('chat-check: semua pemeriksaan lulus');
