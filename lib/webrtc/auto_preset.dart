/// Preset otomatis resolusi + FPS — padanan persis `web/src/auto_preset.ts`.
/// Kalau mengubah aturan di sini, ubah juga di web dan kedua berkas tesnya.
///
/// Prinsip: mulai ringan (720p30), naik bertahap hanya saat terbukti stabil,
/// jangan kirim lebih banyak piksel daripada layar client, encoder software
/// tidak pernah diminta 60 FPS, dan setiap perubahan (= restart encoder di
/// host) dibuat jarang: turun cepat, naik pelan, berhenti setelah dua kali
/// gagal.
library;

class AutoDecision {
  const AutoDecision({
    required this.mode,
    required this.fps,
    required this.tier,
    required this.reason,
  });

  /// 0 = 720p, 1 = 1080p (kode protokol 0x0C).
  final int mode;
  final int fps;
  final int tier;
  final String reason;

  String get resolutionLabel => mode == 1 ? '1080p' : '720p';

  @override
  String toString() => '$resolutionLabel · $fps FPS';
}

class AutoInput {
  const AutoInput({
    required this.clientLongEdgePx,
    this.hostLevel,
    this.fpsLimit,
    this.encoder,
    this.rttMs,
    this.recentLossPct,
    this.jitterBufferMs,
    this.decodeMs,
    this.deliveredFps,
    this.glassMs,
  });

  final int clientLongEdgePx;
  final int? hostLevel;
  final int? fpsLimit;
  final String? encoder;
  final double? rttMs;
  final double? recentLossPct;
  final double? jitterBufferMs;
  final double? decodeMs;
  final double? deliveredFps;
  final double? glassMs;

  AutoInput copyWith({
    double? rttMs,
    double? recentLossPct,
    double? jitterBufferMs,
    double? decodeMs,
    double? deliveredFps,
    double? glassMs,
    String? encoder,
  }) => AutoInput(
    clientLongEdgePx: clientLongEdgePx,
    hostLevel: hostLevel,
    fpsLimit: fpsLimit,
    encoder: encoder ?? this.encoder,
    rttMs: rttMs ?? this.rttMs,
    recentLossPct: recentLossPct ?? this.recentLossPct,
    jitterBufferMs: jitterBufferMs ?? this.jitterBufferMs,
    decodeMs: decodeMs ?? this.decodeMs,
    deliveredFps: deliveredFps ?? this.deliveredFps,
    glassMs: glassMs ?? this.glassMs,
  );
}

const _tiers = <(int, int)>[(0, 30), (1, 30), (0, 60), (1, 60)];

bool isHardwareEncoder(String? encoder) =>
    encoder == 'nvenc' ||
    encoder == 'mft' ||
    encoder == 'amf' ||
    encoder == 'qsv';

/// Tier tertinggi yang masuk akal untuk layar client + host ini.
(int, String) ceilingTier(AutoInput input) {
  final wants1080 =
      input.clientLongEdgePx >= 1600 && (input.hostLevel ?? 31) >= 40;
  final hw = isHardwareEncoder(input.encoder);
  final fps60 = hw && (input.fpsLimit ?? 30) >= 60;
  if (!wants1080) {
    final why = input.clientLongEdgePx < 1600
        ? 'layar perangkat ini tidak butuh lebih dari 720p'
        : 'host/browser membatasi ke 720p';
    if (fps60) return (2, why);
    return (0, hw ? why : '$why; encoder software');
  }
  if (fps60) return (3, 'encoder hardware dan layar tajam');
  return (1, hw ? 'host membatasi 30 FPS' : 'encoder software: 1080p 30 FPS');
}

/// Bukti tier sekarang terlalu berat; null bila sehat.
String? overloaded(AutoInput input, AutoDecision current, double baselineRtt) {
  if ((input.recentLossPct ?? 0) > 2) return 'kehilangan paket';
  if ((input.jitterBufferMs ?? 0) > 90) return 'antrean jitter tinggi';
  // Decoder client yang butuh >20 ms/frame menumpuk antrean sendiri.
  if ((input.decodeMs ?? 0) > 20) return 'decode client lambat';
  final rtt = input.rttMs ?? 0;
  if (rtt > 0 &&
      baselineRtt.isFinite &&
      rtt > baselineRtt + 80 &&
      rtt > baselineRtt * 1.4) {
    return 'RTT membengkak';
  }
  final fps = input.deliveredFps;
  if (fps != null && fps > 0 && fps < current.fps * 0.75) {
    return 'host hanya sanggup ${fps.round()} FPS';
  }
  final glass = input.glassMs;
  if (glass != null && glass - rtt / 2 > 90) return 'latensi pemrosesan tinggi';
  return null;
}

class AutoPreset {
  AutoDecision current = _decision(0, 'mulai ringan');
  double _baselineRtt = double.infinity;
  int _stableSince = 0;
  int _lastChange = 0;
  int _failedPromotions = 0;
  int _lastDemotedTier = -1;

  static AutoDecision _decision(int tier, String reason) => AutoDecision(
    mode: _tiers[tier].$1,
    fps: _tiers[tier].$2,
    tier: tier,
    reason: reason,
  );

  AutoDecision reset([int now = 0]) {
    current = _decision(0, 'mulai ringan');
    _baselineRtt = double.infinity;
    _stableSince = now;
    _lastChange = now;
    _failedPromotions = 0;
    _lastDemotedTier = -1;
    return current;
  }

  /// Keputusan awal saat meta host tiba: hardware langsung ke 1080p30 bila
  /// layarnya layak, software tetap 720p30.
  AutoDecision initial(AutoInput input, int now) {
    reset(now);
    final (capTier, capReason) = ceilingTier(input);
    final tier = isHardwareEncoder(input.encoder) ? capTier.clamp(0, 1) : 0;
    current = _decision(tier, tier == 0 ? 'mulai ringan' : capReason);
    return current;
  }

  /// Dipanggil tiap detik (now dalam ms); non-null hanya bila berubah.
  AutoDecision? update(AutoInput input, int now) {
    final rtt = input.rttMs ?? 0;
    if (rtt > 0 && rtt.isFinite && rtt < _baselineRtt) _baselineRtt = rtt;
    final (capTier, capReason) = ceilingTier(input);
    final why = overloaded(input, current, _baselineRtt);
    if (why != null) _stableSince = now;

    if (current.tier > capTier) return _apply(capTier, capReason, now);

    if (why != null) {
      if (current.tier == 0 || now - _lastChange < 5000) return null;
      _failedPromotions += 1;
      _lastDemotedTier = current.tier;
      return _apply(current.tier - 1, 'turun: $why', now);
    }

    if (current.tier >= capTier) return null;
    if (_failedPromotions >= 2) return null;
    final need = _lastDemotedTier == current.tier + 1 ? 60000 : 12000;
    if (now - _stableSince < need || now - _lastChange < need) return null;
    return _apply(current.tier + 1, capReason, now);
  }

  AutoDecision _apply(int tier, String reason, int now) {
    current = _decision(tier, reason);
    _lastChange = now;
    _stableSince = now;
    return current;
  }
}
