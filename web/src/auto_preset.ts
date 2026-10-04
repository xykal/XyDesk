/// Preset otomatis: memilih resolusi + FPS untuk pengguna yang tidak mau
/// mengatur apa pun. Bitrate tetap urusan `AdaptiveVideo`.
///
/// Prinsip (Indonesian / English):
/// - Mulai ringan (720p30), naik bertahap hanya saat terbukti stabil.
///   Start light, climb only after the link proves stable.
/// - Jangan kirim lebih banyak piksel daripada layar client bisa tampilkan.
///   Never send more pixels than the client screen can show.
/// - Prioritaskan 720p dulu; 1080p baru dicoba setelah jalur 720p stabil.
///   Prefer 720p first; try 1080p only after the 720p path proves stable.
/// - Encoder software (openh264) tidak pernah diminta 60 FPS, dan 1080p
///   hanya kalau latensi terukur masih sehat. Software encoders are never
///   asked for 60 FPS; 1080p only while measured latency stays healthy.
/// - Setiap perubahan = encoder di-restart di host, jadi perubahan jarang:
///   turun cepat, naik pelan, berhenti mencoba setelah dua kali gagal.
///   Every change restarts the host encoder: back off fast, promote slowly,
///   stop retrying after two failed promotions.

export type AutoResolution = '720p' | '1080p';
export type AutoFps = 30 | 60;

export interface AutoDecision {
  resolution: AutoResolution;
  fps: AutoFps;
  /** Indeks tangga (0 = paling ringan). */
  tier: number;
  /** Penjelasan singkat untuk UI, bahasa Indonesia. */
  reason: string;
}

export interface AutoInput {
  /** Sisi terpanjang layar client dalam piksel fisik (CSS px × devicePixelRatio). */
  clientLongEdgePx: number;
  /** `meta.video.level` host: 31 = hanya 720p, 40/51 = 1080p boleh. */
  hostLevel?: number;
  /** `meta.video.fpsLimit` host. */
  fpsLimit?: number;
  /** `meta.encoder` host: 'nvenc' | 'mft' | 'openh264' | lainnya. */
  encoder?: string;
  /** Statistik sesaat dari getStats / probe latensi. */
  rttMs?: number;
  recentLossPct?: number;
  jitterBufferMs?: number;
  /** Waktu decode rata-rata per frame (ms) dari getStats. */
  decodeMs?: number;
  /** FPS yang benar-benar tiba di client. */
  deliveredFps?: number;
  /** Estimasi glass-to-glass (ms) dari probe latensi, bila ada. */
  glassMs?: number;
}

const TIERS: ReadonlyArray<{ resolution: AutoResolution; fps: AutoFps }> = [
  { resolution: '720p', fps: 30 },
  { resolution: '720p', fps: 60 },
  { resolution: '1080p', fps: 30 },
  { resolution: '1080p', fps: 60 },
];
const STABLE_PROMOTION_MS = 20000;
const REPROMOTION_AFTER_DEMOTE_MS = 60000;

export function isHardwareEncoder(encoder: string | undefined): boolean {
  return encoder === 'nvenc' || encoder === 'mft' || encoder === 'amf' || encoder === 'qsv';
}

function tierAllowed(input: AutoInput, tier: number): boolean {
  const preset = TIERS[tier];
  if (!preset) return false;
  if (preset.fps === 60 && (!isHardwareEncoder(input.encoder) || (input.fpsLimit ?? 30) < 60)) return false;
  if (preset.resolution === '1080p' && (input.clientLongEdgePx < 1600 || (input.hostLevel ?? 31) < 40)) return false;
  return true;
}

function nextAllowedTier(input: AutoInput, currentTier: number, capTier: number): number | null {
  for (let tier = currentTier + 1; tier <= capTier; tier += 1) {
    if (tierAllowed(input, tier)) return tier;
  }
  return null;
}

function previousAllowedTier(input: AutoInput, currentTier: number): number {
  for (let tier = currentTier - 1; tier >= 0; tier -= 1) {
    if (tierAllowed(input, tier)) return tier;
  }
  return 0;
}

/** Tier tertinggi yang masuk akal untuk kombinasi layar client + host ini. */
export function ceilingTier(input: AutoInput): { tier: number; reason: string } {
  const wants1080 = input.clientLongEdgePx >= 1600 && (input.hostLevel ?? 31) >= 40;
  const hw = isHardwareEncoder(input.encoder);
  const fps60 = hw && (input.fpsLimit ?? 30) >= 60;
  if (!wants1080) {
    const why = input.clientLongEdgePx < 1600
      ? 'layar perangkat ini tidak butuh lebih dari 720p'
      : 'host/browser membatasi ke 720p';
    return fps60 ? { tier: 1, reason: why } : { tier: 0, reason: hw ? why : `${why}; encoder software` };
  }
  if (fps60) return { tier: 3, reason: 'encoder hardware dan jalur 720p stabil' };
  return { tier: 2, reason: hw ? 'host membatasi 30 FPS; coba 1080p setelah stabil' : 'encoder software: 1080p 30 FPS setelah stabil' };
}

/** Bukti bahwa tier sekarang terlalu berat untuk jalur/encoder saat ini. */
export function overloaded(input: AutoInput, current: AutoDecision, baselineRttMs: number): string | null {
  if ((input.recentLossPct ?? 0) > 2) return 'kehilangan paket';
  if ((input.jitterBufferMs ?? 0) > 90) return 'antrean jitter tinggi';
  // Decoder client (HP tanpa H264 hardware) yang butuh >20 ms/frame akan
  // menumpuk antrean sendiri; tidak ada gunanya mengirim piksel lebih banyak.
  if ((input.decodeMs ?? 0) > 20) return 'decode client lambat';
  const rtt = input.rttMs ?? 0;
  if (rtt > 0 && Number.isFinite(baselineRttMs) && rtt > baselineRttMs + 80 && rtt > baselineRttMs * 1.4) return 'RTT membengkak';
  const fps = input.deliveredFps;
  if (Number.isFinite(fps) && fps! > 0 && fps! < current.fps * 0.75) return `host hanya sanggup ${Math.round(fps!)} FPS`;
  const glass = input.glassMs;
  // Ambang latensi: RTT/2 adalah bagian yang tidak bisa dihindari; sisanya
  // (capture+encode+decode+render) di atas 90 ms berarti tier terlalu berat.
  if (Number.isFinite(glass) && glass! - rtt / 2 > 90) return 'latensi pemrosesan tinggi';
  return null;
}

export class AutoPreset {
  current: AutoDecision = { ...TIERS[0], tier: 0, reason: 'mulai ringan' };
  private baselineRtt = Infinity;
  private stableSince = 0;
  private lastChange = 0;
  private failedPromotions = 0;
  private lastDemotedTier = -1;

  reset(now = 0): AutoDecision {
    this.current = { ...TIERS[0], tier: 0, reason: 'mulai ringan' };
    this.baselineRtt = Infinity;
    this.stableSince = now;
    this.lastChange = now;
    this.failedPromotions = 0;
    this.lastDemotedTier = -1;
    return this.current;
  }

  /**
   * Keputusan awal begitu `meta` host tiba — sebelum ada statistik.
   * Semua encoder mulai dari 720p30 supaya web tidak memaksa HD sebelum
   * jalur, decoder, dan host terbukti stabil.
   */
  initial(_input: AutoInput, now: number): AutoDecision {
    return this.reset(now);
  }

  /** Dipanggil tiap detik; mengembalikan keputusan baru hanya bila berubah. */
  update(input: AutoInput, now: number): AutoDecision | null {
    const rtt = input.rttMs ?? 0;
    if (rtt > 0 && Number.isFinite(rtt)) this.baselineRtt = Math.min(this.baselineRtt, rtt);
    const cap = ceilingTier(input);
    const why = overloaded(input, this.current, this.baselineRtt);
    if (why) this.stableSince = now;

    // Plafon turun (mis. encoder berubah ke software) → ikut turun segera.
    if (this.current.tier > cap.tier) return this.apply(cap.tier, cap.reason, now);

    if (why) {
      if (this.current.tier === 0 || now - this.lastChange < 5000) return null;
      this.failedPromotions += 1;
      this.lastDemotedTier = this.current.tier;
      return this.apply(previousAllowedTier(input, this.current.tier), `turun: ${why}`, now);
    }

    if (this.current.tier >= cap.tier) return null;
    if (this.failedPromotions >= 2) return null;
    const nextTier = nextAllowedTier(input, this.current.tier, cap.tier);
    if (nextTier === null) return null;
    const need = this.lastDemotedTier === nextTier ? REPROMOTION_AFTER_DEMOTE_MS : STABLE_PROMOTION_MS;
    if (now - this.stableSince < need || now - this.lastChange < need) return null;
    return this.apply(nextTier, cap.reason, now);
  }

  private apply(tier: number, reason: string, now: number): AutoDecision {
    this.current = { ...TIERS[tier], tier, reason };
    this.lastChange = now;
    this.stableSince = now;
    return this.current;
  }
}

export function describeDecision(d: AutoDecision): string {
  return `${d.resolution} · ${d.fps} FPS`;
}
