import test from 'node:test';
import assert from 'node:assert/strict';

import {
  ChatRoom,
  HISTORY_SIZE,
  MAX_TEXT,
  MIN_GAP_MS,
  RATE_BURST,
  RATE_WINDOW_MS,
  avatarHue,
  buildMessage,
  displayName,
  parseIncoming,
  pickName,
  rateCheck,
  sanitizeText,
  trimHistory,
} from '../src/chat.js';

test('sanitasi membuang karakter kontrol dan pembalik arah teks', () => {
  assert.equal(sanitizeText('halo\u0000 dunia'), 'halo dunia');
  assert.equal(sanitizeText('a\u202Eb'), 'ab');
  assert.equal(sanitizeText('\u200Bkosong semu\u200B'), 'kosong semu');
  assert.equal(sanitizeText('tab\tdi tengah'), 'tabdi tengah');
});

test('sanitasi merapatkan spasi, membatasi baris, dan memangkas panjang', () => {
  assert.equal(sanitizeText('a      b'), 'a b');
  assert.equal(sanitizeText('1\n2\n3\n4\n5\n6\n7\n8'), '1\n2\n3\n4\n5\n6');
  assert.equal(sanitizeText('x'.repeat(MAX_TEXT + 50)).length, MAX_TEXT);
  assert.equal(sanitizeText('   '), '');
  assert.equal(sanitizeText('\n\n\n'), '');
  assert.equal(sanitizeText(null), '');
  assert.equal(sanitizeText(42), '');
});

test('nama tampilan tidak pernah membocorkan alamat email lengkap', () => {
  assert.equal(displayName('budi.santoso@xydesk.my.id'), 'budi.santoso');
  assert.equal(displayName('a+tag@mail.com'), 'atag');
  assert.equal(displayName('@kosong.com'), 'anon');
  assert.equal(displayName(undefined), 'anon');
  assert.ok(!displayName('budi@xydesk.my.id').includes('@'));
});

test('nama profil dipakai bila layak, kalau tidak jatuh ke email', () => {
  assert.equal(pickName('Budi Santoso', 'b@x.id'), 'Budi Santoso');
  assert.equal(pickName('   ', 'b@x.id'), 'b');
  assert.equal(pickName(null, 'b@x.id'), 'b');
  assert.equal(pickName('Bu\u0000di', 'b@x.id'), 'Budi');
  assert.equal(pickName('N'.repeat(40), 'b@x.id').length, 24);
});

test('warna avatar stabil dan selalu dalam rentang derajat', () => {
  const a = avatarHue('budi@xydesk.my.id');
  assert.equal(a, avatarHue('budi@xydesk.my.id'));
  assert.notEqual(a, avatarHue('siti@xydesk.my.id'));
  for (const email of ['a@b.c', '', 'panjang.sekali@contoh.co.id']) {
    const hue = avatarHue(email);
    assert.ok(hue >= 0 && hue < 360, `hue ${hue} di luar rentang`);
  }
});

test('rem laju: jarak minimum antar pesan ditegakkan', () => {
  const first = rateCheck({ stamps: [] }, 1_000);
  assert.equal(first.allowed, true);

  const tooFast = rateCheck(first.state, 1_000 + MIN_GAP_MS - 1);
  assert.equal(tooFast.allowed, false);
  assert.equal(tooFast.reason, 'terlalu-cepat');
  assert.ok(tooFast.retryMs > 0);

  const okay = rateCheck(first.state, 1_000 + MIN_GAP_MS);
  assert.equal(okay.allowed, true);
});

test('rem laju: burst dibatasi lalu pulih setelah jendela lewat', () => {
  let state = { stamps: [] };
  let now = 0;
  for (let i = 0; i < RATE_BURST; i += 1) {
    now += MIN_GAP_MS;
    const r = rateCheck(state, now);
    assert.equal(r.allowed, true, `pesan ke-${i + 1} seharusnya lolos`);
    state = r.state;
  }
  now += MIN_GAP_MS;
  const blocked = rateCheck(state, now);
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.reason, 'terlalu-banyak');

  const later = rateCheck(state, now + RATE_WINDOW_MS);
  assert.equal(later.allowed, true);
  assert.equal(later.state.stamps.length, 1);
});

test('riwayat tidak pernah melebihi batas dan menyimpan yang terbaru', () => {
  let history = [];
  for (let i = 0; i < HISTORY_SIZE + 20; i += 1) {
    history = trimHistory(history, { id: String(i) });
  }
  assert.equal(history.length, HISTORY_SIZE);
  assert.equal(history[history.length - 1].id, String(HISTORY_SIZE + 19));
  assert.equal(trimHistory(undefined, { id: 'x' }).length, 1);
});

test('pesan siar tidak memuat email mentah', () => {
  const msg = buildMessage({ id: 'u1', email: 'budi@xydesk.my.id', name: '', text: 'halo', at: 5 });
  const serialized = JSON.stringify(msg);
  assert.ok(!serialized.includes('@xydesk.my.id'));
  assert.equal(msg.from, 'budi');
  assert.equal(msg.type, 'msg');
  assert.equal(msg.at, 5);
});

test('amplop masuk hanya menerima msg dan ping', () => {
  assert.deepEqual(parseIncoming(JSON.stringify({ type: 'ping' })), { ok: true, kind: 'ping' });
  assert.deepEqual(parseIncoming(JSON.stringify({ type: 'msg', text: ' halo ' })), { ok: true, kind: 'msg', text: 'halo' });
  assert.equal(parseIncoming(JSON.stringify({ type: 'msg', text: '   ' })).error, 'kosong');
  assert.equal(parseIncoming(JSON.stringify({ type: 'kick' })).error, 'jenis-tidak-dikenal');
  assert.equal(parseIncoming('bukan json').error, 'bukan-json');
  assert.equal(parseIncoming(JSON.stringify([1, 2])).error, 'bukan-objek');
  assert.equal(parseIncoming('x'.repeat(MAX_TEXT * 4 + 1)).error, 'terlalu-panjang');
  assert.equal(parseIncoming(null).error, 'bukan-teks');
});

// ── Durable Object dengan ctx/ws tiruan ─────────────────────────────────────

function fakeSocket() {
  const sent = [];
  let attachment = null;
  return {
    sent,
    closed: null,
    send(text) { sent.push(JSON.parse(text)); },
    close(code, reason) { this.closed = { code, reason }; },
    serializeAttachment(value) { attachment = value; },
    deserializeAttachment() { return attachment; },
  };
}

function fakeCtx(sockets) {
  const store = new Map();
  return {
    getWebSockets: () => sockets,
    storage: {
      async get(key) { return store.get(key); },
      async put(key, value) { store.set(key, value); },
    },
  };
}

test('pesan tersimpan di riwayat dan disiarkan ke semua soket', async () => {
  const author = fakeSocket();
  const lain = fakeSocket();
  const room = new ChatRoom(fakeCtx([author, lain]), {});
  author.serializeAttachment({ email: 'budi@xydesk.my.id', name: '', stamps: [] });

  await room.webSocketMessage(author, JSON.stringify({ type: 'msg', text: 'halo semua' }));

  assert.equal(lain.sent.length, 1);
  assert.equal(lain.sent[0].text, 'halo semua');
  assert.equal(lain.sent[0].from, 'budi');
  assert.equal((await room.history()).length, 1);
});

test('soket tanpa identitas ditutup, bukan disiarkan', async () => {
  const anon = fakeSocket();
  const room = new ChatRoom(fakeCtx([anon]), {});
  anon.serializeAttachment({ stamps: [] });

  await room.webSocketMessage(anon, JSON.stringify({ type: 'msg', text: 'halo' }));

  assert.ok(anon.closed, 'soket tanpa email harus ditutup');
  assert.equal((await room.history()).length, 0);
});

test('pesan kedua yang terlalu cepat ditolak dan tidak masuk riwayat', async () => {
  const author = fakeSocket();
  const room = new ChatRoom(fakeCtx([author]), {});
  author.serializeAttachment({ email: 'budi@xydesk.my.id', name: '', stamps: [] });

  await room.webSocketMessage(author, JSON.stringify({ type: 'msg', text: 'satu' }));
  await room.webSocketMessage(author, JSON.stringify({ type: 'msg', text: 'dua' }));

  const history = await room.history();
  assert.equal(history.length, 1);
  const errors = author.sent.filter(m => m.type === 'error');
  assert.equal(errors.length, 1);
  assert.equal(errors[0].reason, 'terlalu-cepat');
});

test('ping dibalas pong tanpa menyentuh riwayat', async () => {
  const ws = fakeSocket();
  const room = new ChatRoom(fakeCtx([ws]), {});
  ws.serializeAttachment({ email: 'budi@xydesk.my.id', name: '', stamps: [] });

  await room.webSocketMessage(ws, JSON.stringify({ type: 'ping' }));

  assert.equal(ws.sent[0].type, 'pong');
  assert.equal((await room.history()).length, 0);
});

test('permintaan tanpa identitas internal ditolak 401', async () => {
  const room = new ChatRoom(fakeCtx([]), {});
  const res = await room.fetch(new Request('https://chat/history'));
  assert.equal(res.status, 401);
});

test('riwayat dapat dibaca lewat HTTP internal', async () => {
  const room = new ChatRoom(fakeCtx([]), {});
  const res = await room.fetch(new Request('https://chat/history', {
    headers: { 'x-xydesk-email': 'budi@xydesk.my.id' },
  }));
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { messages: [] });
});

test('non-websocket di /ws ditolak 426', async () => {
  const room = new ChatRoom(fakeCtx([]), {});
  const res = await room.fetch(new Request('https://chat/ws', {
    headers: { 'x-xydesk-email': 'budi@xydesk.my.id' },
  }));
  assert.equal(res.status, 426);
});
