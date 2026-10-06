// Chat global XyDesk — satu ruang untuk semua pengguna yang sudah masuk.
//
// Bentuknya sengaja kecil: satu Durable Object, WebSocket hibernasi, riwayat
// pendek di storage. Yang TIDAK ada dan memang tidak direncanakan: pesan
// pribadi, lampiran, dan riwayat tak terbatas — tiga hal itu menuntut
// moderasi dan penyimpanan yang jauh lebih serius daripada nilai yang
// diberikannya untuk sebuah chat lobi.
//
// Semua aturan (sanitasi, rem laju, pemangkasan riwayat, nama tampilan)
// ditulis sebagai fungsi murni di bagian atas supaya bisa diuji dengan
// `node --test` tanpa runtime Workers.

// ── Batas ───────────────────────────────────────────────────────────────────
export const MAX_TEXT = 400;          // karakter per pesan
export const HISTORY_SIZE = 50;       // pesan yang disimpan & dikirim saat join
export const RATE_WINDOW_MS = 10_000; // jendela rem laju
export const RATE_BURST = 5;          // pesan per jendela
export const MIN_GAP_MS = 700;        // jarak minimum antar pesan satu orang
export const MAX_CONNECTIONS = 400;   // batas sambungan serentak di satu ruang

/**
 * Bersihkan teks pesan.
 *
 * Mengembalikan string kosong bila pesan tidak layak kirim. Aturannya:
 * - karakter kontrol (termasuk \r, \t, dan karakter tak terlihat) dibuang;
 *   \n dipertahankan tetapi dibatasi;
 * - spasi beruntun dirapatkan supaya "seni ASCII" tidak memenuhi layar;
 * - maksimum 6 baris dan MAX_TEXT karakter;
 * - spasi di tepi dipangkas.
 */
export function sanitizeText(input) {
  if (typeof input !== 'string') return '';
  // Buang karakter kontrol kecuali newline, plus penanda arah teks yang bisa
  // dipakai membalik tampilan nama orang lain (U+202A..U+202E, U+2066..U+2069).
  let text = input.replace(/[\u0000-\u0009\u000B-\u001F\u007F\u200B-\u200F\u202A-\u202E\u2066-\u2069]/g, '');
  text = text.replace(/\n{3,}/g, '\n\n');
  text = text
    .split('\n')
    .slice(0, 6)
    .map(line => line.replace(/[ \t]{2,}/g, ' ').trim())
    .join('\n')
    .trim();
  if (text.length > MAX_TEXT) text = text.slice(0, MAX_TEXT).trim();
  return text;
}

/**
 * Nama tampilan dari email: bagian sebelum @, dipotong, tanpa membocorkan
 * alamat lengkap ke semua orang di ruang.
 */
export function displayName(email) {
  if (typeof email !== 'string') return 'anon';
  const local = email.split('@')[0] || '';
  const clean = local.replace(/[^\p{L}\p{N}._-]/gu, '').slice(0, 24);
  return clean || 'anon';
}

/**
 * Nama yang dipakai di ruang: nama profil bila ada dan layak, kalau tidak
 * jatuh ke bagian lokal email. Dibersihkan dengan aturan yang sama supaya
 * nama profil tidak bisa menyelundupkan karakter kontrol atau emoji panjang.
 */
export function pickName(name, email) {
  const raw = typeof name === 'string' ? name : '';
  const clean = raw
    .replace(/[\u0000-\u001F\u007F\u200B-\u200F\u202A-\u202E\u2066-\u2069]/g, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
    .slice(0, 24)
    .trim();
  return clean || displayName(email);
}

/** Warna avatar yang stabil per pengguna — murni turunan email, bukan acak. */
export function avatarHue(email) {
  const text = typeof email === 'string' ? email : '';
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  }
  return hash % 360;
}

/**
 * Rem laju per pengguna: maksimal RATE_BURST pesan per RATE_WINDOW_MS, dan
 * minimal MIN_GAP_MS antar pesan. Mengembalikan state baru plus keputusan,
 * jadi fungsinya murni dan mudah diuji.
 */
export function rateCheck(state, nowMs) {
  const stamps = Array.isArray(state?.stamps) ? state.stamps : [];
  const recent = stamps.filter(at => nowMs - at < RATE_WINDOW_MS);
  const last = recent.length ? recent[recent.length - 1] : -Infinity;
  if (nowMs - last < MIN_GAP_MS) {
    return { allowed: false, reason: 'terlalu-cepat', retryMs: MIN_GAP_MS - (nowMs - last), state: { stamps: recent } };
  }
  if (recent.length >= RATE_BURST) {
    const retryMs = RATE_WINDOW_MS - (nowMs - recent[0]);
    return { allowed: false, reason: 'terlalu-banyak', retryMs, state: { stamps: recent } };
  }
  return { allowed: true, reason: '', retryMs: 0, state: { stamps: [...recent, nowMs] } };
}

/** Simpan hanya HISTORY_SIZE pesan terakhir. */
export function trimHistory(history, message) {
  const list = Array.isArray(history) ? history : [];
  const next = [...list, message];
  return next.length > HISTORY_SIZE ? next.slice(next.length - HISTORY_SIZE) : next;
}

/** Bentuk pesan yang dikirim ke semua orang. Tidak pernah memuat email penuh. */
export function buildMessage({ id, email, name, text, at }) {
  return {
    type: 'msg',
    id,
    from: pickName(name, email),
    hue: avatarHue(email),
    text,
    at,
  };
}

/**
 * Periksa amplop masuk dari client. Mengembalikan `{ ok, kind, text, error }`.
 * Hanya dua jenis yang diterima: `msg` dan `ping`.
 */
export function parseIncoming(raw) {
  if (typeof raw !== 'string') return { ok: false, error: 'bukan-teks' };
  if (raw.length > MAX_TEXT * 4) return { ok: false, error: 'terlalu-panjang' };
  let value;
  try {
    value = JSON.parse(raw);
  } catch {
    return { ok: false, error: 'bukan-json' };
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) return { ok: false, error: 'bukan-objek' };
  if (value.type === 'ping') return { ok: true, kind: 'ping' };
  if (value.type !== 'msg') return { ok: false, error: 'jenis-tidak-dikenal' };
  const text = sanitizeText(value.text);
  if (!text) return { ok: false, error: 'kosong' };
  return { ok: true, kind: 'msg', text };
}

// ── Durable Object ──────────────────────────────────────────────────────────

export class ChatRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  sockets() {
    try {
      return this.ctx.getWebSockets ? this.ctx.getWebSockets() : [];
    } catch {
      return [];
    }
  }

  async history() {
    const stored = await this.ctx.storage.get('history');
    return Array.isArray(stored) ? stored : [];
  }

  async fetch(request) {
    const url = new URL(request.url);

    // Worker sudah memverifikasi JWT dan menitipkan identitasnya di header.
    // DO tidak pernah memercayai body untuk soal identitas.
    const email = request.headers.get('x-xydesk-email') || '';
    const name = request.headers.get('x-xydesk-name') || '';
    if (!email) return json({ error: 'unauthorized' }, 401);

    if (url.pathname === '/history') {
      return json({ messages: await this.history() });
    }

    if (request.headers.get('Upgrade') !== 'websocket') {
      return json({ error: 'expected-websocket' }, 426);
    }
    if (this.sockets().length >= MAX_CONNECTIONS) {
      return json({ error: 'ruang-penuh' }, 503);
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ email, name, stamps: [], since: Date.now() });

    try {
      server.send(JSON.stringify({
        type: 'welcome',
        you: pickName(name, email),
        hue: avatarHue(email),
        online: this.sockets().length,
        messages: await this.history(),
      }));
    } catch {
      // Socket bisa tertutup sebelum sempat menerima; bukan kesalahan fatal.
    }
    this.broadcastPresence();
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws, raw) {
    let meta;
    try {
      meta = ws.deserializeAttachment() || {};
    } catch {
      meta = {};
    }
    const email = meta.email || '';
    if (!email) {
      try { ws.close(1008, 'tanpa identitas'); } catch {}
      return;
    }

    const parsed = parseIncoming(typeof raw === 'string' ? raw : '');
    if (!parsed.ok) {
      send(ws, { type: 'error', reason: parsed.error });
      return;
    }
    if (parsed.kind === 'ping') {
      send(ws, { type: 'pong', at: Date.now() });
      return;
    }

    const now = Date.now();
    const check = rateCheck({ stamps: meta.stamps }, now);
    if (!check.allowed) {
      ws.serializeAttachment({ ...meta, stamps: check.state.stamps });
      send(ws, { type: 'error', reason: check.reason, retryMs: Math.max(0, Math.round(check.retryMs)) });
      return;
    }
    ws.serializeAttachment({ ...meta, stamps: check.state.stamps });

    const message = buildMessage({ id: crypto.randomUUID(), email, name: meta.name, text: parsed.text, at: now });
    await this.ctx.storage.put('history', trimHistory(await this.history(), message));
    this.broadcast(message);
  }

  async webSocketClose() {
    this.broadcastPresence();
  }

  async webSocketError() {
    this.broadcastPresence();
  }

  broadcast(payload) {
    const text = JSON.stringify(payload);
    for (const ws of this.sockets()) {
      try { ws.send(text); } catch {}
    }
  }

  broadcastPresence() {
    this.broadcast({ type: 'presence', online: this.sockets().length });
  }
}

function send(ws, payload) {
  try { ws.send(JSON.stringify(payload)); } catch {}
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}
