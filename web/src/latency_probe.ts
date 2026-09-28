/// Probe latensi sisi client — angka yang bisa diambil OTOMATIS tanpa
/// mengubah host, dari `HTMLVideoElement.requestVideoFrameCallback`.
///
/// Per frame yang benar-benar ditampilkan, browser memberi:
///   - `receiveTime`         : saat paket terakhir frame ini tiba di jaringan
///   - `expectedDisplayTime` : saat frame dijadwalkan tampil di layar
///   - `captureTime`         : saat frame ditangkap di HOST — HANYA ada bila
///                             pengirim menyertakan ekstensi RTP abs-capture-time.
///                             webrtc-rs di host belum mengirimkannya, jadi
///                             kolom ini biasanya kosong. Kalau suatu saat ada,
///                             probe langsung memakainya = glass-to-glass sejati
///                             minus refresh layar.
///
/// Yang dihitung di sini:
///   receive → display  = expectedDisplayTime - receiveTime
///                        (jitter buffer + decode + antre compositor)
///   capture → display  = expectedDisplayTime - captureTime (bila tersedia)
///
/// Estimasi glass-to-glass = RTT/2 + receive→display. Itu BATAS BAWAH karena
/// capture+encode di host tidak terlihat dari sini; label di UI harus jujur
/// menyebutnya "tanpa capture/encode host". Angka host ada di log
/// `xydesk-host` (`encode_ms`) dan dijumlahkan manual — lihat docs/LATENCY.md.
///
/// Modul ini sengaja bebas DOM di bagian statistik supaya bisa diuji di Node.

export interface FrameTiming {
  /** DOMHighResTimeStamp — waktu frame dijadwalkan tampil. */
  expectedDisplayTime: number;
  /** Waktu paket terakhir frame diterima. Kosong bila browser tidak memberi. */
  receiveTime?: number;
  /** Waktu capture di host (butuh abs-capture-time dari pengirim). */
  captureTime?: number;
  /** Durasi decode+proses yang dilaporkan browser (detik). */
  processingDuration?: number;
  rtpTimestamp?: number;
}

export interface Percentiles {
  p50: number;
  p95: number;
  max: number;
}

export interface LatencySummary {
  /** Jumlah frame dalam jendela. 0 = belum ada data. */
  samples: number;
  /** Frame yang datang tanpa `receiveTime` (browser tidak mendukung). */
  unsupported: number;
  receiveToDisplay?: Percentiles;
  captureToDisplay?: Percentiles;
  processing?: Percentiles;
  /** Interval antar-frame yang ditampilkan — jitter presentasi. */
  frameInterval?: Percentiles;
}

export interface GlassToGlassEstimate {
  /** Total estimasi (ms). */
  totalMs: number;
  /** Rincian komponen yang benar-benar terukur. */
  parts: { networkOneWayMs: number; receiveToDisplayMs: number; hostEncodeMs?: number };
  /** `measured` = ada captureTime dari host; `lower-bound` = tanpa capture/encode host. */
  confidence: 'measured' | 'lower-bound';
}

const clamp = (n: number) => (Number.isFinite(n) && n >= 0 ? n : undefined);

export function percentiles(values: readonly number[]): Percentiles | undefined {
  const sorted = values.filter(v => Number.isFinite(v)).sort((a, b) => a - b);
  if (!sorted.length) return undefined;
  const at = (q: number) => sorted[Math.min(sorted.length - 1, Math.max(0, Math.ceil(sorted.length * q) - 1))];
  return { p50: at(0.5), p95: at(0.95), max: sorted[sorted.length - 1] };
}

/// Jendela geser frame terakhir. Default 240 frame ≈ 4 detik @60 fps —
/// cukup untuk p95 yang stabil tapi masih merespons perubahan jaringan.
export class LatencyProbe {
  private readonly receiveToDisplay: number[] = [];
  private readonly captureToDisplay: number[] = [];
  private readonly processing: number[] = [];
  private readonly frameInterval: number[] = [];
  private lastDisplay = 0;
  private unsupported = 0;
  private total = 0;

  constructor(private readonly windowSize = 240) {}

  /** Catat satu frame yang ditampilkan. Aman dipanggil dari rVFC callback. */
  push(meta: FrameTiming): void {
    this.total++;
    const display = meta.expectedDisplayTime;
    if (!Number.isFinite(display)) return;

    if (this.lastDisplay > 0) this.add(this.frameInterval, display - this.lastDisplay);
    this.lastDisplay = display;

    const r2d = meta.receiveTime === undefined ? undefined : clamp(display - meta.receiveTime);
    if (r2d === undefined) this.unsupported++;
    else this.add(this.receiveToDisplay, r2d);

    if (meta.captureTime !== undefined) {
      const c2d = clamp(display - meta.captureTime);
      if (c2d !== undefined) this.add(this.captureToDisplay, c2d);
    }
    if (meta.processingDuration !== undefined) {
      const p = clamp(meta.processingDuration * 1000);
      if (p !== undefined) this.add(this.processing, p);
    }
  }

  summary(): LatencySummary {
    return {
      samples: Math.min(this.total, this.windowSize),
      unsupported: this.unsupported,
      receiveToDisplay: percentiles(this.receiveToDisplay),
      captureToDisplay: percentiles(this.captureToDisplay),
      processing: percentiles(this.processing),
      frameInterval: percentiles(this.frameInterval),
    };
  }

  reset(): void {
    this.receiveToDisplay.length = 0;
    this.captureToDisplay.length = 0;
    this.processing.length = 0;
    this.frameInterval.length = 0;
    this.lastDisplay = 0;
    this.unsupported = 0;
    this.total = 0;
  }

  private add(list: number[], v: number): void {
    list.push(v);
    if (list.length > this.windowSize) list.splice(0, list.length - this.windowSize);
  }
}

/// Gabungkan komponen yang ada menjadi satu angka. Tidak pernah mengarang:
/// bila hanya RTT dan receive→display yang ada, hasilnya diberi label
/// `lower-bound`, bukan "latency".
export function estimateGlassToGlass(input: {
  rttMs?: number;
  summary: LatencySummary;
  hostEncodeMs?: number;
}): GlassToGlassEstimate | undefined {
  const { summary } = input;
  if (summary.captureToDisplay) {
    return {
      totalMs: summary.captureToDisplay.p50,
      parts: { networkOneWayMs: (input.rttMs ?? 0) / 2, receiveToDisplayMs: summary.receiveToDisplay?.p50 ?? 0, hostEncodeMs: input.hostEncodeMs },
      confidence: 'measured',
    };
  }
  if (!summary.receiveToDisplay) return undefined;
  const networkOneWayMs = (input.rttMs ?? 0) / 2;
  const receiveToDisplayMs = summary.receiveToDisplay.p50;
  const hostEncodeMs = input.hostEncodeMs;
  return {
    totalMs: networkOneWayMs + receiveToDisplayMs + (hostEncodeMs ?? 0),
    parts: { networkOneWayMs, receiveToDisplayMs, hostEncodeMs },
    confidence: 'lower-bound',
  };
}

/// Laporan JSON yang bisa diunduh pengguna dan ditempel ke issue — supaya
/// laporan "lag" datang dengan angka, bukan perasaan.
export function buildLatencyReport(input: {
  summary: LatencySummary;
  estimate?: GlassToGlassEstimate;
  stats?: Record<string, unknown> | null;
  userAgent?: string;
  version?: string;
  now?: Date;
}): Record<string, unknown> {
  const pick = (keys: string[]) =>
    Object.fromEntries(keys.filter(k => input.stats && input.stats[k] !== undefined).map(k => [k, input.stats![k]]));
  return {
    schema: 'xydesk-latency-report/1',
    generatedAt: (input.now ?? new Date()).toISOString(),
    version: input.version ?? null,
    userAgent: input.userAgent ?? null,
    method: {
      receiveToDisplay: 'requestVideoFrameCallback: expectedDisplayTime - receiveTime',
      captureToDisplay: 'requestVideoFrameCallback: expectedDisplayTime - captureTime (butuh abs-capture-time dari host)',
      estimate: 'RTT/2 + receive→display (+ encode host bila diketahui). lower-bound = tanpa capture/encode host.',
    },
    latency: input.summary,
    estimate: input.estimate ?? null,
    connection: pick(['width', 'height', 'fps', 'mbps', 'rttMs', 'jitterMs', 'jitterBufferMs', 'decodeMs', 'lossPct', 'recentLossPct', 'codec', 'transportPath', 'transportProtocol', 'framesDropped', 'freezeCount']),
  };
}

// ── Bagian DOM: hanya dipanggil dari browser ────────────────────────────────

type VideoFrameMetadata = FrameTiming & { presentationTime?: number };
type RvfcVideo = HTMLVideoElement & {
  requestVideoFrameCallback?: (cb: (now: number, meta: VideoFrameMetadata) => void) => number;
  cancelVideoFrameCallback?: (handle: number) => void;
};

/// Pasang probe ke elemen video. Mengembalikan fungsi pelepas. Bila browser
/// tidak punya rVFC (Firefox lama), probe tetap ada tapi `samples` = 0 dan
/// UI menampilkan "tidak didukung" — bukan angka palsu.
export function attachLatencyProbe(video: HTMLVideoElement, probe: LatencyProbe): () => void {
  const v = video as RvfcVideo;
  if (typeof v.requestVideoFrameCallback !== 'function') return () => {};
  let handle = 0;
  let alive = true;
  const tick = (_now: number, meta: VideoFrameMetadata) => {
    if (!alive) return;
    probe.push(meta);
    handle = v.requestVideoFrameCallback!(tick);
  };
  handle = v.requestVideoFrameCallback(tick);
  return () => {
    alive = false;
    v.cancelVideoFrameCallback?.(handle);
  };
}

export function supportsLatencyProbe(): boolean {
  return typeof HTMLVideoElement !== 'undefined' && 'requestVideoFrameCallback' in HTMLVideoElement.prototype;
}
