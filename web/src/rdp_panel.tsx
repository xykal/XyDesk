// Panel Koneksi RDP di dalam sesi web.
//
// Alur: client mengirim permintaan `rdp-open` lewat data channel WebRTC
// bernama "rdp" (terenkripsi end-to-end, TIDAK melewati server signaling) ke
// host Windows yang sedang dipasangkan. Host menjalankan `mstsc` dari dalam
// jaringannya sendiri, sehingga IP target RDP tidak pernah terekspos ke
// internet — inilah jaminan keamanan yang diminta pemilik (2026-11-06).
//
// Kata sandi hanya hidup selama permintaan berjalan: tidak disimpan di
// localStorage, tidak ditulis ke file .rdp, dan di host hanya singgah di
// Credential Manager beberapa detik sebelum dibersihkan.

import { useState } from 'react';

export interface RdpRequest {
  host: string;
  port: number;
  user: string;
  pass: string;
  width: number;
  height: number;
}

export interface RdpStatus {
  ok: boolean;
  reason?: string;
}

const HOST_RE = /^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$/;
const USER_RE = /^[A-Za-z0-9 ._\\@-]{1,128}$/;

/** Validasi isian RDP. Mengembalikan pesan kesalahan (bahasa Indonesia) atau null bila sah. */
export function validateRdpForm(v: {
  host: string;
  port: string;
  user: string;
  pass: string;
}): string | null {
  const host = v.host.trim();
  if (!host) return 'Isi alamat host RDP (nama PC atau IP).';
  if (
    !HOST_RE.test(host) ||
    host.includes('..') ||
    host.startsWith('-') ||
    host.endsWith('-') ||
    host.startsWith('.') ||
    host.endsWith('.') ||
    host.includes('.-') ||
    host.includes('-.')
  )
    return 'Alamat host hanya boleh huruf, angka, titik, dan tanda minus.';
  const port = Number(v.port);
  if (!Number.isInteger(port) || port < 1 || port > 65535) return 'Port harus 1–65535.';
  const user = v.user.trim();
  if (!user) return 'Isi username (bisa DOMAIN\\username).';
  if (!USER_RE.test(user)) return 'Username mengandung karakter yang tidak diizinkan.';
  if (v.pass.length > 512) return 'Kata sandi terlalu panjang.';
  return null;
}

/** Susun isi berkas .rdp untuk fallback unduhan (TANPA kata sandi). */
export function buildRdpFile(req: Pick<RdpRequest, 'host' | 'port' | 'user' | 'width' | 'height'>): string {
  return [
    `full address:s:${req.host}:${req.port}`,
    'prompt for credentials:i:1',
    'authentication level:i:2',
    'redirectclipboard:i:1',
    'redirectprinters:i:0',
    'screen mode id:i:1',
    `desktopwidth:i:${req.width}`,
    `desktopheight:i:${req.height}`,
    'session bpp:i:32',
    'compression:i:1',
    'videoplaybackmode:i:1',
    '',
  ].join('\r\n');
}

export function RdpPanel({
  onOpen,
  status,
  busy,
}: {
  onOpen: (req: RdpRequest) => void;
  status: RdpStatus | null;
  busy: boolean;
}) {
  const [host, setHost] = useState('');
  const [port, setPort] = useState('3389');
  const [user, setUser] = useState('');
  const [pass, setPass] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = () => {
    const err = validateRdpForm({ host, port, user, pass });
    if (err) {
      setError(err);
      return;
    }
    setError(null);
    onOpen({ host: host.trim(), port: Number(port), user: user.trim(), pass, width: 1920, height: 1080 });
  };

  const unduhRdp = () => {
    const err = validateRdpForm({ host, port, user, pass: 'x' });
    if (err) {
      setError(err);
      return;
    }
    setError(null);
    const body = buildRdpFile({
      host: host.trim(),
      port: Number(port),
      user: user.trim(),
      width: 1920,
      height: 1080,
    });
    const blob = new Blob([body], { type: 'application/x-rdp' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `${host.trim().replace(/[^\w.-]+/g, '_')}.rdp`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  return (
    <div className="rdp-panel">
      <p className="spanel-note">
        Koneksi RDP dibuka <b>dari PC yang sedang tersambung</b> — IP target tidak pernah terekspos
        ke internet, dan kredensial dikirim end-to-end (tidak melewati server).
      </p>
      <label className="spanel-field">
        <span>Alamat host RDP</span>
        <input
          value={host}
          onChange={(e) => setHost(e.target.value)}
          placeholder="192.168.1.10 atau pc-kantor"
          inputMode="url"
          autoComplete="off"
          spellCheck={false}
        />
      </label>
      <label className="spanel-field">
        <span>Port</span>
        <input value={port} onChange={(e) => setPort(e.target.value)} inputMode="numeric" />
      </label>
      <label className="spanel-field">
        <span>Username</span>
        <input
          value={user}
          onChange={(e) => setUser(e.target.value)}
          placeholder="DOMAIN\\username"
          autoComplete="off"
          spellCheck={false}
        />
      </label>
      <label className="spanel-field">
        <span>Kata sandi</span>
        <span className="rdp-pass-row">
          <input
            type={showPass ? 'text' : 'password'}
            value={pass}
            onChange={(e) => setPass(e.target.value)}
            autoComplete="new-password"
          />
          <button
            type="button"
            className="spanel-mini"
            aria-label={showPass ? 'Sembunyikan kata sandi' : 'Tampilkan kata sandi'}
            onClick={() => setShowPass((v) => !v)}
          >
            {showPass ? '🙈' : '👁'}
          </button>
        </span>
      </label>
      {error && (
        <p role="alert" className="spanel-error">
          {error}
        </p>
      )}
      {status && (
        <p role="status" className={status.ok ? 'spanel-ok' : 'spanel-error'}>
          {status.ok
            ? 'Perintah diterima host. Jendela RDP akan muncul di layar PC — lihat lewat sesi ini.'
            : `Gagal: ${status.reason ?? 'tidak diketahui'}`}
        </p>
      )}
      <div className="rdp-actions">
        <button type="button" className="spanel-primary" onClick={submit} disabled={busy}>
          {busy ? 'Mengirim…' : 'Buka RDP dari PC'}
        </button>
        <button type="button" className="spanel-mini" onClick={unduhRdp}>
          Unduh file .rdp
        </button>
      </div>
    </div>
  );
}
