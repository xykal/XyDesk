import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../../core/permissions.dart';

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/devlog.dart';
import '../../core/l10n_bridge.dart';
import '../../core/pip_controller.dart';
import '../../core/session_preview.dart';
import '../../core/store.dart';
import '../../core/tokens.dart';
import '../../webrtc/auto_preset.dart';
import '../../webrtc/input_codec.dart';
import '../../webrtc/rtc_service.dart';
import '../../webrtc/session_transport.dart';
import '../../webrtc/vk_codes.dart';
import '../devices/device_model.dart';
import 'session_panels.dart';
import 'virtual_keyboard.dart';
import '../../core/display_control.dart';
import 'gaming_controls.dart';
import 'session_keyboard.dart';
import 'session_rail.dart';
import 'session_surface.dart';

/// Adaptive remote-session surface.
///
/// Gaming and Desktop are two views of one session instead of separate pages.
/// The always-visible HUD is intentionally small; advanced controls live in a
/// single readable end panel rather than the former eight-category side rail.
class SessionPage extends ConsumerStatefulWidget {
  const SessionPage({
    super.key,
    required this.deviceName,
    required this.deviceId,
    this.password = '',
    this.initialTransport,
  });

  final String deviceName;

  final String deviceId;

  /// Password pairing host. Kosong = coba pairing tanpa password (host
  /// menolak bila mensyaratkan) atau tampilkan preview untuk mode tamu.
  final String password;

  /// Transport yang sudah dibuat dan sedang berjalan (pairing sudah diterima).
  /// Kalau null, SessionPage membuat transport baru dan memulai pairing sendiri.
  final SessionTransport? initialTransport;

  @override
  ConsumerState<SessionPage> createState() => _SessionPageState();
}

class _SessionPageState extends ConsumerState<SessionPage>
    with WidgetsBindingObserver {
  bool _overlayVisible = true;
  bool _panelVisible = false;
  bool _keyboardVisible = false;
  SessionPanelSection _panelSection = SessionPanelSection.stream;
  int _panelRevision = 0;
  Timer? _idleTimer;
  Timer? _captureTimer;

  /// Dicatat ke `RepaintBoundary` membungkus permukaan video remote, dipakai
  /// untuk menangkap cuplikan "layar terakhir" saat sesi berjalan/putus.
  final GlobalKey _videoKey = GlobalKey();

  /// Lebar piksel kartu pratinjau yang disimpan (dibatasi supaya muat).
  static const _previewWidth = 720;
  late SessionSettings _settings;

  Store get _store => ref.read(storeProvider);

  /// Aliran isi papan klip PC (balasan permintaan 0x09).
  StreamSubscription<String>? _clipboardSub;

  /// Aliran meta dari host (hardware info, display list).
  StreamSubscription<HostMeta>? _metaSub;

  /// Aliran statistik sesi (FPS, bitrate, noFrameWarning).
  StreamSubscription<SessionStats>? _statsSub;

  /// Preset video otomatis (resolusi + FPS) — lihat webrtc/auto_preset.dart.
  /// Preferensi bitrate/quality dikirim sekali saat meta host pertama tiba;
  /// sebelum 6.8.7 tidak ada yang dikirim sama sekali.
  final AutoPreset _autoPreset = AutoPreset();
  bool _videoPrefsSent = false;
  String? _lastEncoder;
  SessionStats? _lastStats;

  KbLayout _keyboardLayout = KbLayout.split;
  double _keyboardOpacity = 0.95;
  late final SessionTransport _transport;

  /// Waktu saat sesi menjadi live (connected). Null = belum tersambung.
  DateTime? _sessionStartedAt;

  /// Timer penghitung durasi sesi (diperbarui tiap detik saat live).
  Timer? _durationTimer;

  /// Detik elapsed sejak sesi tersambung — dipakai menampilkan durasi
  /// di panel kontrol.
  int _elapsedSec = 0;

  /// Total durasi sesi untuk tamu (2 jam = 7200 detik). Null untuk login user.
  static const int _guestSessionTotal = 2 * 60 * 60;

  /// Apakah ini sesi tamu (tanpa login).
  bool get _isGuestSession => ref.read(authProvider).isGuest;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    final preferences = ref.read(settingsProvider);
    _transport =
        widget.initialTransport ??
        SessionTransport(jwt: ref.read(authProvider).token);
    _transport.addListener(_onTransportChanged);
    ref.listenManual(settingsProvider, _onStreamPrefsChanged);
    // Kalau transport sudah disediakan dari halaman Connect (pairing sudah
    // diterima), tidak perlu start ulang — cukup dengarkan perubahannya.
    if (widget.initialTransport == null) {
      unawaited(
        _transport.start(hostId: widget.deviceId, password: widget.password),
      );
    }
    _settings = SessionSettings(
      pcAudioRequested: preferences.audioEnabled,
      microphoneRequested: preferences.micPassthrough,
      haptics: preferences.haptics,
      pointerLock: preferences.relativeMouseMode,
    );
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    // Selama sesi, pengguna sering hanya menonton — tanpa sentuhan, Android
    // memadamkan layar dan sesi terlihat seolah putus.
    if (preferences.keepScreenOn) {
      unawaited(DisplayControl.setKeepScreenOn(true));
    }
    DevLog.i(
      'sesi',
      'Membuka sesi ke ${widget.deviceName}',
      'id=${widget.deviceId} transport=${widget.initialTransport != null ? "existing" : "new"}',
    );
    // FIX: jangan Loading Connection di session screen — kalau belum benar-benar
    // terhubung jangan masuk session screen (sudah dicek di ConnectPage).
    // Kalau initialTransport sudah negotiating/connected, langsung live.
    // Kalau gagal, jangan pernah masuk session — pop dengan error.
    if (widget.initialTransport != null) {
      final s = widget.initialTransport!.state;
      if (s.status == TransportStatus.negotiating ||
          s.status == TransportStatus.connected) {
        DevLog.i(
          'sesi',
          'Transport existing sudah ${s.status} — langsung live',
        );
      } else if (s.status == TransportStatus.rejected ||
          s.status == TransportStatus.peerOffline ||
          s.status == TransportStatus.hostBusy ||
          s.status == TransportStatus.error) {
        // Gagal — jangan masuk session screen, pop dengan error
        DevLog.w(
          'sesi',
          'Transport existing gagal ${s.status} — tidak masuk session',
        );
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  s.message ?? 'Gagal terhubung ke ${widget.deviceName}',
                ),
              ),
            );
            Navigator.of(context).pop();
          }
        });
      } else {}
    } else {}
    _restartIdleTimer();

    // Tangkap cuplikan "layar terakhir" secara berkala selama sesi live,
    // supaya halaman detail PC punya gambar terbaru meski sesi berakhir
    // tanpa sempat menangkap satu kali pun di akhir.
    _captureTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      _captureFrame();
    });
  }

  /// Tangkap satu frame permukaan video ke penyimpanan lokal (per perangkat).
  Future<void> _captureFrame() async {
    // Hanya saat transport benar-benar live; kalau belum, jangan buang waktu
    // menangkap placeholder/teks.
    if (!_transport.state.live) return;
    final boundary =
        _videoKey.currentContext?.findRenderObject() as RenderRepaintBoundary?;
    if (boundary == null) return;
    try {
      // gambar sudah dibatasi lebar agar muat di SharedPreferences (base64).
      final ratio = (_previewWidth / boundary.size.width).clamp(0.1, 1.0);
      final image = await boundary.toImage(pixelRatio: ratio.toDouble());
      try {
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        if (data != null) {
          await saveSessionPreview(
            _store,
            widget.deviceId,
            data.buffer.asUint8List(),
          );
        }
      } finally {
        image.dispose();
      }
    } catch (e) {
      // Kalau platform texture tidak bisa ditangkap (lihat session_preview.dart),
      // jangan sampai merusak sesi — cukup catat.
      DevLog.w('sesi', 'Tangkapan pratinjau gagal', '$e');
    }
  }

  /// Tangkapan terakhir yang pasti, dipanggil tepat sebelum sesi ditutup.
  Future<void> _captureFinalFrame() async {
    await _captureFrame();
  }

  void _onTransportChanged() {
    if (!mounted) return;
    final s = _transport.state;
    DevLog.i(
      'sesi',
      'Transport',
      '${s.status}${s.message == null ? '' : ' - ${s.message}'}',
    );
    // Papan klip PC hanya bisa diterima setelah kanal datanya terbuka, yang
    // terjadinya bersamaan dengan sesi menjadi live.
    if (s.live && _clipboardSub == null) {
      final rtc = _transport.rtc;
      if (rtc != null) {
        _clipboardSub = rtc.clipboardStream.listen(_onClipboardFromHost);
        // Listen ke meta stream untuk hardware info.
        _metaSub = rtc.hostMetaStream.listen(_onHostMeta);
        // Listen ke stats stream untuk no-frame watchdog warning.
        _statsSub = rtc.statsStream.listen((st) {
          if (mounted && (_lastStats?.noFrameWarning != st.noFrameWarning)) {
            setState(() => _lastStats = st);
          } else {
            _lastStats = st;
          }
          _driveAutoPreset(st);
        });
      }
    }
    // Saat transport tidak lagi live (retry, disconnect, dll), bersihkan
    // subscription lama supaya bisa dipasang ulang saat live kembali.
    // Tanpa ini, _clipboardSub tetap non-null (subscription dari RtcService
    // lama yang sudah ditutup) dan subscription ke stream baru tidak pernah
    // dibuat — clipboard dari host tidak pernah sampai ke perangkat.
    if (!s.live && _clipboardSub != null) {
      unawaited(_clipboardSub!.cancel());
      _clipboardSub = null;
    }
    if (!s.live && _metaSub != null) {
      unawaited(_metaSub!.cancel());
      _metaSub = null;
    }
    if (!s.live) {
      _videoPrefsSent = false;
      _lastEncoder = null;
    }
    if (!s.live && _statsSub != null) {
      unawaited(_statsSub!.cancel());
      _statsSub = null;
      _lastStats = null;
    }
    // Mulai hitung durasi sesi saat pertama kali live.
    if (s.live && _sessionStartedAt == null) {
      _sessionStartedAt = DateTime.now();
      _durationTimer?.cancel();
      _durationTimer = Timer.periodic(const Duration(seconds: 1), (_) {
        if (!mounted || _sessionStartedAt == null) return;
        final elapsed = DateTime.now().difference(_sessionStartedAt!).inSeconds;
        if (elapsed != _elapsedSec) {
          setState(() => _elapsedSec = elapsed);
        }
      });
    }
    // Stop penghitung saat sesi berakhir.
    if (!s.live && _sessionStartedAt != null) {
      _durationTimer?.cancel();
      _durationTimer = null;
    }
    // Begitu live, HUD disembunyikan supaya layar remote bersih; pengguna
    // memunculkannya lewat handle kecil di tepi kanan.
    if (s.live && _overlayVisible) {
      _overlayVisible = false;
      _idleTimer?.cancel();
    }
    // Jika sesi aktif dan app di-background, masuk PiP mode
    if (s.live && !mounted) {
      PipController.instance.enterPipMode();
    }
    setState(() {});
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    // Saat app di-minimize/paused dan sesi masih live, masuk PiP mode
    if (state == AppLifecycleState.paused && _transport.state.live) {
      PipController.instance.enterPipMode();
      DevLog.i('sesi', 'Masuk PiP mode', 'Sesi aktif saat app di-background');
    }
    // Saat app di-resume, keluar PiP mode (jika aktif)
    if (state == AppLifecycleState.resumed &&
        PipController.instance.isInPipMode) {
      PipController.instance.exitPipMode();
    }
  }

  /// Terima meta dari host — simpan hardware info ke device repo.
  void _onHostMeta(HostMeta meta) {
    // Update device dengan hardware info dari host.
    unawaited(_updateDeviceWithHardwareInfo(meta));
    _applyVideoPrefs(meta);
  }

  /// Sisi terpanjang layar HP dalam piksel fisik — plafon resolusi otomatis.
  int _clientLongEdgePx() {
    final size = View.of(context).physicalSize;
    final edge = size.longestSide.round();
    return edge > 0 ? edge : 1280;
  }

  AutoInput _autoInput(HostMeta? meta) => AutoInput(
    clientLongEdgePx: _clientLongEdgePx(),
    hostLevel: meta?.videoLevel,
    fpsLimit: meta?.fpsLimit,
    encoder: meta?.encoder,
  );

  /// Kirim quality/bitrate sekali, lalu resolusi+FPS: dari preset Auto
  /// (AutoPreset) atau nilai tetap milik preset Medium/High/Ultra.
  void _applyVideoPrefs(HostMeta meta) {
    final rtc = _transport.rtc;
    if (rtc == null) return;
    final prefs = ref.read(settingsProvider);
    final now = DateTime.now().millisecondsSinceEpoch;
    if (!_videoPrefsSent) {
      _videoPrefsSent = true;
      rtc.sendVideoPrefs(
        quality: prefs.quality.index,
        bitrateMbps: prefs.bitrateMbps,
      );
      if (prefs.quality == StreamQuality.auto) {
        _pushDecision(rtc, _autoPreset.initial(_autoInput(meta), now));
      } else {
        rtc.sendVideoPrefs(
          mode: prefs.quality.videoMode,
          fps: prefs.quality.fps,
        );
        DevLog.i(
          'video',
          'Preset tetap dikirim',
          '${prefs.quality.resolution} ${prefs.bitrateMbps} Mbps',
        );
      }
    } else if (prefs.quality == StreamQuality.auto &&
        meta.encoder != null &&
        meta.encoder != _lastEncoder) {
      // Encoder host baru ketahuan di frame pertama (NVENC malas).
      final d = _autoPreset.update(_autoInput(meta), now);
      if (d != null) _pushDecision(rtc, d);
    }
    _lastEncoder = meta.encoder;
  }

  void _driveAutoPreset(SessionStats st) {
    final rtc = _transport.rtc;
    if (rtc == null || !_videoPrefsSent) return;
    if (ref.read(settingsProvider).quality != StreamQuality.auto) return;
    final d = _autoPreset.update(
      _autoInput(rtc.hostMeta).copyWith(
        rttMs: st.rttMs,
        recentLossPct: st.packetLossPercent,
        jitterBufferMs: st.jitterBufferMs,
        decodeMs: st.decodeMs,
        deliveredFps: st.fps,
      ),
      DateTime.now().millisecondsSinceEpoch,
    );
    if (d != null) _pushDecision(rtc, d);
  }

  /// Pengguna mengganti preset/bitrate di panel saat sesi berjalan.
  void _onStreamPrefsChanged(AppSettings? prev, AppSettings next) {
    final rtc = _transport.rtc;
    if (rtc == null || !_videoPrefsSent) return;
    if (prev?.quality == next.quality &&
        prev?.bitrateMbps == next.bitrateMbps) {
      return;
    }
    rtc.sendVideoPrefs(
      quality: next.quality.index,
      bitrateMbps: next.bitrateMbps,
    );
    if (prev?.quality == next.quality) return;
    if (next.quality == StreamQuality.auto) {
      _pushDecision(
        rtc,
        _autoPreset.initial(
          _autoInput(rtc.hostMeta),
          DateTime.now().millisecondsSinceEpoch,
        ),
      );
    } else {
      rtc.autoDecision = null;
      rtc.sendVideoPrefs(mode: next.quality.videoMode, fps: next.quality.fps);
    }
  }

  void _pushDecision(RtcService rtc, AutoDecision d) {
    rtc.autoDecision = d;
    rtc.sendVideoPrefs(mode: d.mode, fps: d.fps);
    DevLog.i('video', 'Preset otomatis', '$d — ${d.reason}');
    if (mounted) setState(() {});
  }

  /// Update device di repo dengan hardware info dari HostMeta.
  Future<void> _updateDeviceWithHardwareInfo(HostMeta meta) async {
    // Konversi HostDisplay ke DisplayInfo.
    final displays = meta.displays
        .map(
          (d) => DisplayInfo(
            index: d.index,
            name: d.name.isEmpty ? 'Monitor ${d.index + 1}' : d.name,
            width: d.width,
            height: d.height,
            refreshRate: d.refreshRate,
            isPrimary: d.isPrimary,
          ),
        )
        .toList();

    // Resolusi monitor UTAMA menurut host. Bila host tidak melaporkan monitor
    // sama sekali (width 0 / daftar kosong), nilainya null — layar detail akan
    // menulis "Tidak terdeteksi", bukan 1920×1080 karangan.
    DisplayInfo? primary;
    for (final d in displays) {
      if (d.isPrimary) {
        primary = d;
        break;
      }
    }
    primary ??= displays.isEmpty ? null : displays.first;
    final String? resolutionLabel;
    if (primary == null || primary.width == 0 || primary.height == 0) {
      resolutionLabel = null;
    } else if (primary.refreshRate != null && primary.refreshRate! > 1) {
      // 1 Hz dipakai sebagian driver virtual untuk "tidak dilaporkan".
      resolutionLabel =
          '${primary.width}×${primary.height} @ ${primary.refreshRate} Hz';
    } else {
      resolutionLabel = '${primary.width}×${primary.height}';
    }

    DevLog.i(
      'sesi',
      'Hardware info diterima dari host',
      'GPU=${meta.gpu}, RAM=${meta.ram}, Displays=${displays.length}',
    );

    // Update device di repo dengan hardware info.
    await ref
        .read(deviceRepoProvider.notifier)
        .updateHardwareInfo(
          widget.deviceId,
          specsReported: meta.hardwareReported,
          resolution: resolutionLabel,
          motherboard: meta.motherboard,
          cpu: meta.cpu,
          gpu: meta.gpu,
          ram: meta.ram,
          storage: meta.storage,
          displays: displays,
        );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _transport.removeListener(_onTransportChanged);
    _transport.dispose();
    _idleTimer?.cancel();
    _captureTimer?.cancel();
    _durationTimer?.cancel();
    SystemChrome.setPreferredOrientations(DeviceOrientation.values);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    // Flag layar-tetap-menyala milik window, bukan halaman. Kalau tidak
    // dicabut di sini, ia akan tetap aktif di seluruh aplikasi setelah sesi
    // ditutup dan diam-diam menghabiskan baterai.
    unawaited(DisplayControl.setKeepScreenOn(false));
    _clipboardSub?.cancel();
    _metaSub?.cancel();
    _statsSub?.cancel();
    DevLog.i('sesi', 'Menutup sesi');
    super.dispose();
  }

  /// Kirim satu kombinasi tombol (modifier sticky + tombol utama) ke host.
  void _sendKeyCombo(String label, Set<String> modifiers) {
    if (!_transport.state.live) return;
    final vk = vkForLabel(label);
    if (vk == null) return;
    final modVks = modifiers
        .map(vkForLabel)
        .whereType<int>()
        .toList(growable: false);
    for (final m in modVks) {
      _transport.sendInput(InputCodec.key(m, down: true));
    }
    _transport.sendInput(InputCodec.key(vk, down: true));
    _transport.sendInput(InputCodec.key(vk, down: false));
    for (final m in modVks.reversed) {
      _transport.sendInput(InputCodec.key(m, down: false));
    }
  }

  /// Kirim teks bebas ke host (0x06 TEXT) — dipakai papan ketik sistem.
  void _sendText(String text) {
    if (!_transport.state.live) {
      _showUnavailable('Keyboard butuh sesi yang tersambung.');
      return;
    }
    // TEXT mengirim sebagai utf8 dan host mengetik apa adanya, tidak
    // tergantung tata letak keyboard host. `sendText` memecah tempelan
    // panjang menjadi beberapa pesan — satu pesan utuh akan dipotong host.
    _transport.sendText(text);
  }

  /// Kirim isi papan klip perangkat ini ke PC (0x08 CLIPBOARD_SET).
  ///
  /// Dulu ini memakai 0x06 TEXT, yang berarti host MENGETIKKAN teksnya ke
  /// jendela yang sedang aktif. Itu berguna, tetapi bukan yang dijanjikan
  /// pengaturan "Sinkronisasi papan klip" — dan berbahagia kalau jendela
  /// yang aktif bukan yang pengguna maksud. Sekarang isinya betul-betul
  /// menjadi papan klip PC, lalu pengguna menekan Ctrl+V sendiri.
  Future<void> _sendClipboard() async {
    if (!_transport.state.live) {
      _showUnavailable('Clipboard butuh sesi yang tersambung.');
      return;
    }
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text;
    if (text == null || text.isEmpty) {
      _showUnavailable('Clipboard kosong.');
      return;
    }
    // Batas ukuran diurus kodek (64 KiB, dipotong di batas karakter).
    _transport.sendInput(InputCodec.clipboardSet(text));
    _showUnavailable('Tersalin ke papan klip PC — tekan Ctrl+V di sana.');
  }

  /// Minta PC mengirim isi papan klipnya ke ponsel (0x09 CLIPBOARD_REQ).
  ///
  /// Sengaja model tarik: host tidak punya pengamat papan klip Windows,
  /// jadi arah PC → HP tidak bisa dijanjikan otomatis tanpa berbohong.
  /// Hasilnya datang ke [_onClipboardFromHost].
  Future<void> _requestClipboard() async {
    if (!_transport.state.live) {
      _showUnavailable('Clipboard butuh sesi yang tersambung.');
      return;
    }
    _transport.sendInput(InputCodec.clipboardRequest());
    _showUnavailable('Meminta isi papan klip PC…');
  }

  /// Isi papan klip PC yang diminta — tulis ke papan klip perangkat ini.
  Future<void> _onClipboardFromHost(String text) async {
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          text.isEmpty
              ? 'Papan klip PC kosong, atau isinya bukan teks.'
              : 'Isi papan klip PC sudah ada di ponsel kamu.',
        ),
      ),
    );
  }

  void _leaveSession() {
    // Tangkap cuplikan "layar terakhir" sekali lagi tepat sebelum sesi
    // ditutup, lalu simpan untuk halaman detail PC.
    unawaited(_captureFinalFrame());
    // Offline previews are deliberately not added to remote-session history.
    // History should begin only after a real negotiated transport is active.
    unawaited(_transport.shutdown());
    Navigator.of(context).maybePop();
  }

  void _restartIdleTimer() {
    _idleTimer?.cancel();
    _idleTimer = Timer(D.idleHide, () {
      if (mounted && !_panelVisible && !_keyboardVisible) {
        setState(() => _overlayVisible = false);
      }
    });
  }

  void _wake() {
    if (!_overlayVisible) setState(() => _overlayVisible = true);
    _restartIdleTimer();
  }

  void _openPanel([SessionPanelSection section = SessionPanelSection.stream]) {
    setState(() {
      _panelSection = section;
      _panelRevision++;
      _panelVisible = true;
      _overlayVisible = true;
    });
    _idleTimer?.cancel();
  }

  void _closePanel() {
    setState(() => _panelVisible = false);
    _restartIdleTimer();
  }

  void _showKeyboard() {
    setState(() {
      _keyboardVisible = true;
      _panelVisible = false;
      _overlayVisible = true;
    });
    _idleTimer?.cancel();
  }

  /// Aktif/nonaktifkan pemutaran audio sistem host (transceiver direction —
  /// tanpa negosiasi ulang, tidak memutus sesi).
  Future<void> _toggleAudioForward() async {
    final rtc = _transport.rtc;
    if (rtc == null) return;
    final next = !_settings.pcAudioRequested;
    await rtc.setAudioForwardEnabled(next);
    if (!mounted) return;
    setState(() => _settings = _settings.copyWith(pcAudioRequested: next));
  }

  /// Aktif/nonaktifkan mic perangkat → host (meminta izin saat pertama kali).
  Future<void> _toggleMic() async {
    final rtc = _transport.rtc;
    if (rtc == null) return;
    if (_settings.microphoneRequested) {
      await rtc.disableMic();
      if (!mounted) return;
      setState(
        () => _settings = _settings.copyWith(microphoneRequested: false),
      );
      return;
    }
    // Izin diminta DI SINI, bukan saat aplikasi mulai: dialog yang muncul
    // tepat saat pengguna menekan toggle mic adalah dialog yang dipahami
    // konteksnya. Tanpa ini Android tidak pernah menampilkan apa pun dan
    // enableMic gagal dengan pesan yang tidak menunjuk ke mana pun.
    final diizinkan = await Izin.mikrofon();
    if (!mounted) return;
    if (!diizinkan) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Izin mikrofon dibutuhkan untuk mengirim suara ke PC. '
            'Bila sudah ditolak permanen, buka pengaturan aplikasi.',
          ),
        ),
      );
      return;
    }
    final err = await rtc.enableMic();
    if (!mounted) return;
    if (err != null) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(err)));
      return;
    }
    setState(() => _settings = _settings.copyWith(microphoneRequested: true));
  }

  @override
  Widget build(BuildContext context) {
    // Jangan pernah tampilkan Loading Connection di
    // session screen. Validasi pairing sudah di ConnectPage sebelum push.
    // SessionPage langsung live — placeholder RemoteScreenPlaceholder
    // menampilkan status transport asli (MENGHUBUNGI, NEGOSIASI, GAGAL) bila
    // belum live, bukan loading palsu. Tidak ada lagi _ConnectingView.

    return Scaffold(
      backgroundColor: AppColors.bgDark,
      body: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxHeight < 440;
          final panelWidth = (constraints.maxWidth * 0.52)
              .clamp(380.0, 480.0)
              .toDouble();
          return Stack(
            children: [
              Positioned.fill(
                // RepaintBoundary membungkus permukaan video supaya frame
                // "layar terakhir" bisa ditangkap (lihat _captureFrame).
                child: RepaintBoundary(
                  key: _videoKey,
                  child: _transport.state.live
                      ? RemoteVideoSurface(
                          transport: _transport,
                          relativeMouse: _settings.pointerLock,
                          // Saat live, tap adalah klik kiri murni — kontrol
                          // dibuka lewat rail di tepi kanan, bukan tap layar.
                          onWake: () {},
                        )
                      : GestureDetector(
                          behavior: HitTestBehavior.opaque,
                          onTap: _wake,
                          child: RemoteScreenPlaceholder(
                            experience: _settings.experience,
                            transport: _transport.state,
                            onRetry: () => _transport.retry(
                              hostId: widget.deviceId,
                              password: widget.password,
                            ),
                          ),
                        ),
                ),
              ),
              // Pemutar audio remote (suara sistem host) — 1×1 transparan.
              // Wajib berada di pohon widget agar track Opus diputar.
              if (_transport.state.live)
                Positioned(
                  left: 0,
                  top: 0,
                  child: IgnorePointer(
                    child: Opacity(
                      opacity: 0,
                      child: SizedBox(
                        width: 1,
                        height: 1,
                        child: RTCVideoView(
                          _transport.rtc!.audioRenderer,
                          objectFit:
                              RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
                        ),
                      ),
                    ),
                  ),
                ),
              // Watchdog banner: terhubung tapi belum ada frame video setelah 10 detik.
              if (_transport.state.live &&
                  (_lastStats?.noFrameWarning ?? false))
                Positioned(
                  top: MediaQuery.paddingOf(context).top + 12,
                  left: 20,
                  right: 20,
                  child: Center(
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 8,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xEB0D0716),
                        borderRadius: BorderRadius.circular(R.lg),
                        border: Border.all(color: const Color(0x59A78BFA)),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.4),
                            blurRadius: 14,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(
                            LucideIcons.circleAlert,
                            size: 16,
                            color: Color(0xFFA78BFA),
                          ),
                          const SizedBox(width: Gap.sm),
                          Flexible(
                            child: Text(
                              // Sebab yang paling mungkin disebut apa adanya:
                              // relay tidak tersedia menjelaskan kenapa sesi
                              // bisa "tersambung" tetapi tidak pernah bergambar
                              // di jaringan yang butuh relay.
                              _lastStats?.relayUnavailable == true
                                  ? 'Belum ada gambar — relay TURN juga tidak tersedia (${relayReasonLabel(_lastStats?.relayReason)}). Periksa PC host dan jaringan.'
                                  : 'Belum ada gambar (periksa PC host)',
                              style: const TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w500,
                                color: Colors.white,
                              ),
                            ),
                          ),
                          const SizedBox(width: Gap.md),
                          InkWell(
                            onTap: () {
                              setState(() {
                                _panelSection = SessionPanelSection.stream;
                                _panelVisible = true;
                              });
                            },
                            borderRadius: BorderRadius.circular(R.sm),
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 10,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                color: AppColors.accentDark,
                                borderRadius: BorderRadius.circular(R.sm),
                              ),
                              child: const Text(
                                'Pilih Layar',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w600,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              if (!_keyboardVisible &&
                  !_panelVisible &&
                  _settings.experience == SessionExperience.gaming &&
                  _settings.showGamingControls)
                GamingControls(
                  compact: compact,
                  onKey: (vk, down) {
                    if (_transport.state.live) {
                      _transport.sendInput(InputCodec.key(vk, down: down));
                    }
                  },
                ),
              // Satu-satunya HUD yang tersisa: rail tipis menempel di tepi
              // kanan. Tidak ada lagi bar melayang di tengah atas maupun
              // tengah bawah yang menutupi gambar PC.
              if (!_keyboardVisible)
                Positioned(
                  top: 0,
                  bottom: 0,
                  right: 0,
                  child: Center(
                    child: SessionRail(
                      expanded: _overlayVisible,
                      compact: compact,
                      showClipboard:
                          _settings.experience == SessionExperience.desktop,
                      audioRequested: _settings.pcAudioRequested,
                      microphoneRequested: _settings.microphoneRequested,
                      onToggleExpanded: () {
                        if (_overlayVisible) {
                          setState(() => _overlayVisible = false);
                          _idleTimer?.cancel();
                        } else {
                          _wake();
                        }
                      },
                      onAudio: _toggleAudioForward,
                      onMicrophone: _toggleMic,
                      onKeyboard: _showKeyboard,
                      onClipboard: _sendClipboard,
                      onClipboardPull: _requestClipboard,
                      onSettings: () => _openPanel(),
                      onDisconnect: _confirmDisconnect,
                    ),
                  ),
                ),
              AnimatedPositioned(
                duration: D.panel,
                curve: D.curve,
                top: 0,
                bottom: 0,
                right: _panelVisible ? 0 : -panelWidth - 24,
                width: panelWidth,
                child: SessionControlPanel(
                  key: ValueKey((_panelSection, _panelRevision)),
                  initialSection: _panelSection,
                  deviceName: widget.deviceName,
                  state: _settings,
                  transport: _transport.state,
                  rtc: _transport.rtc,
                  elapsedSec: _elapsedSec,
                  isGuestSession: _isGuestSession,
                  guestSessionTotal: _guestSessionTotal,
                  onChanged: (value) => setState(() => _settings = value),
                  onClose: _closePanel,
                  onDisconnect: _confirmDisconnect,
                ),
              ),
              AnimatedPositioned(
                duration: D.sheet,
                curve: D.curve,
                left: 0,
                right: 0,
                bottom: _keyboardVisible ? 0 : -360,
                child: _settings.keyboardSource == KeyboardSource.system
                    ? SystemKeyboard(
                        onText: _sendText,
                        onKey: (vk, down) {
                          if (_transport.state.live) {
                            _transport.sendInput(
                              InputCodec.key(vk, down: down),
                            );
                          }
                        },
                        onDismiss: () {
                          setState(() => _keyboardVisible = false);
                          _restartIdleTimer();
                        },
                      )
                    : VirtualKeyboard(
                        layout: _keyboardLayout,
                        opacity: _keyboardOpacity,
                        onLayoutChanged: (value) =>
                            setState(() => _keyboardLayout = value),
                        onOpacityChanged: (value) =>
                            setState(() => _keyboardOpacity = value),
                        onKeyWithModifiers: _sendKeyCombo,
                        onDismiss: () {
                          setState(() => _keyboardVisible = false);
                          _restartIdleTimer();
                        },
                      ),
              ),
            ],
          );
        },
      ),
    );
  }

  void _showUnavailable(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text(message), duration: const Duration(seconds: 2)),
      );
    _wake();
  }

  Future<void> _confirmDisconnect() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          context.tr('session_disconnect_confirm'),
          style: const TextStyle(fontSize: 16),
        ),
        content: const Text(
          'Preview sesi akan ditutup. PC host tetap menyala dan dapat dipilih '
          'kembali kapan saja.',
          style: TextStyle(fontSize: 13, height: 1.5),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Batal'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(
              context.tr('session_disconnect_action'),
              style: const TextStyle(color: AppColors.danger),
            ),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) _leaveSession();
  }
}
