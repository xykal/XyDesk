// Klien API XyDesk — bicara ke Worker yang sama dengan aplikasi
// mobile/desktop (auth OTP/Google, signal-token, TURN, WebSocket signaling).
//
// Di produksi frontend dan backend berbeda origin, jadi semua panggilan
// memakai BASE absolut. Saat dev, Vite mem-proxy /api ke Worker produksi.

const PROD_BASE = (import.meta.env.VITE_SIGNAL_API as string | undefined) ?? 'https://signal.xydesk.my.id';
export const API_BASE = import.meta.env.DEV ? '/api' : PROD_BASE;
export const WS_URL =
  (import.meta.env.VITE_SIGNAL_WS as string | undefined) ??
  (import.meta.env.DEV
    ? `wss://${new URL(PROD_BASE).host}/ws`
    : `${PROD_BASE.replace('https://', 'wss://')}/ws`);

export interface UserProfile {
  id?: string;
  email: string;
  name?: string | null;
  picture?: string | null;
}

export interface AuthSession {
  token: string;
  user?: UserProfile;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new ApiError(
      res.status,
      (data as { error?: string }).error ?? 'unknown',
      (data as { message?: string }).message ?? 'Permintaan gagal.',
    );
  }
  return data as T;
}

export function requestOtp(email: string, name?: string) {
  return post<{ expires_in: number; resend_in: number }>(
    '/auth/request-otp',
    { email, ...(name ? { name } : {}) },
  );
}

export function createGuestSession(refresh?:string) {
  return post<{ token: string; refresh?:string; guest: true }>('/auth/guest', refresh?{refresh}:{});
}

export function verifyOtp(email: string, otp: string) {
  return post<AuthSession>('/auth/verify-otp', { email, otp });
}

/// Login dengan Google ID token (dari Google Identity Services di browser).
export function signInWithGoogle(idToken: string) {
  return post<AuthSession>('/auth/google', { id_token: idToken });
}

export async function me(token: string) {
  const res = await fetch(`${API_BASE}/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new ApiError(res.status, 'unauthorized', 'Sesi berakhir.');
  return (await res.json()) as { user: UserProfile };
}

/// Tukar JWT sesi menjadi token signaling 5 menit untuk deviceId ini.
export async function signalToken(token: string, deviceId: string) {
  const res = await fetch(
    `${API_BASE}/signal-token?id=${encodeURIComponent(deviceId)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!res.ok) {
    throw new ApiError(res.status, 'signal-token', 'Gagal mendapat izin signaling.');
  }
  return (await res.text()).trim();
}

/// Ganti nama tampilan profil.
export async function updateProfileName(token: string, name: string) {
  const res = await fetch(`${API_BASE}/auth/profile`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ name }),
  });
  if (!res.ok) throw new ApiError(res.status, 'profile', 'Gagal menyimpan nama.');
  return (await res.json()) as { user: UserProfile };
}

/// Hapus akun permanen.
export async function deleteAccount(token: string) {
  const res = await fetch(`${API_BASE}/auth/delete`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new ApiError(res.status, 'delete', 'Gagal menghapus akun.');
}

/// Hasil permintaan kredensial TURN — termasuk sebab bila tidak ada relay.
///
/// Dulu fungsi ini mengembalikan `RTCIceServer[]`, dan setiap kegagalan
/// (403, 503, jaringan mati) menyusut jadi daftar kosong yang sama. Akibatnya
/// sesi berjalan dengan STUN saja tanpa satu pun pesan: pengguna di belakang
/// CGNAT/NAT simetris hanya melihat "menyambung…" yang tidak pernah selesai,
/// dan operator tidak punya apa pun untuk dibaca. Sekarang sebabnya ikut
/// dikembalikan supaya bisa ditampilkan apa adanya.
export interface TurnIceResult {
  servers: RTCIceServer[];
  ok: boolean;
  /// `ok` · `no-servers` · `providers-failed` · `turn-not-configured` ·
  /// `turn-forbidden` · `turn-auth-unavailable` · `http-<status>` · `network`.
  reason: string;
  hint?: string;
}

export async function turnIce(
  deviceId: string,
  token: string,
): Promise<TurnIceResult> {
  try {
    const res = await fetch(
      `${API_BASE}/turn-ice?id=${encodeURIComponent(deviceId)}&token=${encodeURIComponent(token)}`,
    );
    const body = (await res.json().catch(() => ({}))) as {
      iceServers?: RTCIceServer[];
      error?: string;
      reason?: string;
      hint?: string;
      degraded?: boolean;
    };
    const servers = (body.iceServers ?? []).filter((server) => server.urls);
    if (!res.ok) {
      return {
        servers: [],
        ok: false,
        // `reason` lebih rinci daripada `error` (mis. token-invalid vs
        // turn-forbidden); kalau server lama hanya mengirim `error`,
        // nilainya tetap terpakai.
        reason: body.reason ?? body.error ?? `http-${res.status}`,
        hint: body.hint,
      };
    }
    if (!servers.length) {
      // 200 dengan daftar kosong: penyedia sudah dikonfigurasi tetapi tidak
      // satu pun menjawab. Beda sebab, beda tindakan.
      return { servers, ok: false, reason: body.degraded ? 'providers-failed' : 'no-servers' };
    }
    return { servers, ok: true, reason: 'ok' };
  } catch {
    return {
      servers: [],
      ok: false,
      reason: 'network',
      hint: 'Tidak bisa menghubungi server signaling untuk mengambil kredensial relay.',
    };
  }
}
