// Panduan sesi untuk pengguna — dipisah dari komponen supaya bisa diuji.
//
// Dua hal yang dijawab modul ini:
//
// 1. **Relay TURN**: apa yang harus dikatakan ketika kredensial relay tidak
//    ada. Kegagalan ini dulu berakhir sebagai daftar kosong tanpa pesan sama
//    sekali (`try { … } catch { return [] }`), padahal justru pengguna di
//    belakang CGNAT/NAT simetris yang paling bergantung padanya.
// 2. **Tersambung tapi belum ada gambar**: apa langkah berikutnya. Sesi yang
//    "connected" menurut ICE/DTLS belum berarti ada frame yang datang;
//    pengguna tidak boleh menatap layar gelap tanpa penjelasan dan tanpa
//    tombol yang bisa ditekan.
//
// Keputusan "apa yang dikatakan dan apa tindakannya" adalah logika, bukan
// tata letak — jadi ia hidup di sini, teruji, bukan tersebar di JSX.

import type { SessionStats } from './rtc';

/// Sebab relay tidak tersedia, sebagai kalimat yang bisa dibaca pengguna.
/// Kode mentah tetap dipakai sebagai cadangan supaya sebab baru dari server
/// tidak pernah hilang diam-diam.
const RELAY_REASON: Record<string, string> = {
  'no-credentials': 'server menolak permintaan tanpa token perangkat',
  'token-invalid': 'token perangkat ditolak server (mungkin sudah kedaluwarsa)',
  'ticket-invalid': 'tiket perangkat tidak sah untuk relay',
  'ticket-revoked': 'sesi akun dicabut server',
  'turn-forbidden': 'kredensial relay ditolak server',
  'turn-not-configured': 'server belum dikonfigurasi TURN',
  'turn-auth-unavailable': 'server otorisasi sedang tidak bisa dihubungi',
  'providers-failed': 'semua penyedia relay tidak menjawab',
  'no-servers': 'server tidak mengirim daftar relay',
  'bad-response': 'balasan server tidak dikenali',
  network: 'jaringan ke server signaling gagal',
};

export function relayReasonText(reason?: string | null): string {
  if (!reason) return 'sebab tidak diketahui';
  return RELAY_REASON[reason] ?? reason;
}

/// Kalimat siap-tampil untuk baris "Relay TURN" di panel Statistik.
export function relayStatusText(
  state?: SessionStats['relayState'],
  servers?: number,
): string {
  if (state === 'ready') {
    return `${servers ?? 0} server siap — dipakai bila jalur langsung gagal`;
  }
  if (state === 'unavailable') return 'Tidak tersedia — lihat catatan di bawah';
  return 'Belum diperiksa';
}

export interface FrameGuidance {
  /// Judul singkat: apa yang terjadi.
  title: string;
  /// Penjelasan: kenapa, dan apa yang bisa dicoba. Tanpa istilah teknis
  /// yang tidak membantu (nama berkas/fungsi tidak pernah masuk UI).
  detail: string;
  /// Label tombol tindakan pertama.
  action: string;
  /// Benar bila penyebab yang paling mungkin adalah relay yang tidak ada.
  relayRelated: boolean;
}

/// Panduan saat sesi tersambung tetapi belum ada frame video.
/// `null` = tidak ada yang perlu dikatakan.
export function frameGuidance(
  stats: Pick<SessionStats, 'noFrameWarning' | 'relayState' | 'relayReason'> | null | undefined,
): FrameGuidance | null {
  if (!stats?.noFrameWarning) return null;
  if (stats.relayState === 'unavailable') {
    return {
      title: 'Tersambung tapi belum ada gambar',
      detail:
        `Relay TURN juga tidak tersedia (${relayReasonText(stats.relayReason)}). ` +
        'Di jaringan yang butuh relay, jalur gambar tidak akan pernah terbentuk. ' +
        'Coba pilih layar lain, periksa PC host tetap menyala, lalu sambung ulang.',
      action: 'Ganti layar',
      relayRelated: true,
    };
  }
  return {
    title: 'Tersambung tapi belum ada gambar',
    detail:
      'Koneksi tersambung tetapi PC host belum mengirim gambar. Coba pilih layar lain ' +
      'di panel sesi, pastikan PC host tidak terkunci, dan aplikasi host berjalan di ' +
      'sesi yang sedang aktif (host yang hidup di sesi RDP lama atau layar terkunci ' +
      'mengirim gambar hitam), lalu sambung ulang.',
    action: 'Ganti layar',
    relayRelated: false,
  };
}

/// Sebab layar hitam yang terbaca dari kesehatan capture host (capture.json
/// lewat panel): dipakai banner ketika frame datang tetapi semuanya hitam.
export function captureHealthText(blackFrames?: boolean, sessionMismatch?: boolean): string | null {
  if (sessionMismatch) {
    return 'Host berjalan di sesi yang berbeda dari layar aktif — gambar hitam. Jalankan ulang aplikasi host dari sesi yang aktif.';
  }
  if (blackFrames) {
    return 'Layar host terkunci atau di secure desktop — gambar hitam. Buka kunci PC host.';
  }
  return null;
}
