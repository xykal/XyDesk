import 'package:flutter/material.dart';

import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';
import '../../webrtc/input_codec.dart';
import '../../webrtc/session_transport.dart';
import '../../widgets/brand.dart';
import 'session_panels.dart';

/// Permukaan video sungguhan + input trackpad.
///
/// Gestur (mode desktop/trackpad):
///   - seret satu jari  -> gerak pointer relatif
///   - ketuk            -> klik kiri
///   - seret dua jari   -> scroll
class RemoteVideoSurface extends StatelessWidget {
  const RemoteVideoSurface({
    super.key,
    required this.transport,
    required this.relativeMouse,
    required this.onWake,
  });

  final SessionTransport transport;
  final bool relativeMouse;
  final VoidCallback onWake;

  void _click(int button) {
    transport.sendInput(InputCodec.mouseButton(button, down: true));
    transport.sendInput(InputCodec.mouseButton(button, down: false));
  }

  @override
  Widget build(BuildContext context) {
    final renderer = transport.rtc?.renderer;
    if (renderer == null) return const ColoredBox(color: Colors.black);
    return LayoutBuilder(
      builder: (context, constraints) => GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () {
          onWake();
          _click(MouseButton.left);
        },
        onSecondaryTap: () => _click(MouseButton.right),
        // Satu handler scale untuk dua gestur (pan & scale tidak boleh
        // dipasang bersamaan di GestureDetector yang sama):
        //   1 jari  = gerak pointer (rel/abs sesuai mode),
        //   2 jari  = scroll trackpad (WHEEL_DELTA konvensi Windows).
        onScaleUpdate: (d) {
          if (d.pointerCount >= 2) {
            final dy = (d.focalPointDelta.dy * 5).round();
            final dx = (d.focalPointDelta.dx * 5).round();
            if (dx != 0 || dy != 0) {
              transport.sendInput(InputCodec.scroll(dx, dy));
            }
            return;
          }
          if (relativeMouse) {
            transport.sendInput(
              InputCodec.mouseMoveRel(
                d.focalPointDelta.dx.round(),
                d.focalPointDelta.dy.round(),
              ),
            );
          } else {
            // Mode absolut: posisi jari dipetakan langsung ke layar host
            // (fraksi 0..1 dari permukaan video).
            transport.sendInput(
              InputCodec.mouseMoveAbs(
                d.localFocalPoint.dx / constraints.maxWidth,
                d.localFocalPoint.dy / constraints.maxHeight,
              ),
            );
          }
        },
        child: RTCVideoView(
          renderer,
          objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitContain,
        ),
      ),
    );
  }
}

class RemoteScreenPlaceholder extends StatelessWidget {
  const RemoteScreenPlaceholder({
    super.key,
    required this.experience,
    required this.transport,
    this.onRetry,
  });

  final SessionExperience experience;
  final TransportState transport;

  /// Dipanggil tombol "Coba lagi" (hanya tampil pada status error).
  final VoidCallback? onRetry;

  String get _statusLabel => switch (transport.status) {
    TransportStatus.preview => 'PREVIEW • TRANSPORT OFFLINE',
    TransportStatus.pairing => 'MENGHUBUNGI HOST…',
    TransportStatus.negotiating => 'NEGOSIASI KONEKSI…',
    TransportStatus.connected => 'TERSAMBUNG',
    TransportStatus.rejected => 'PAIRING DITOLAK',
    TransportStatus.peerOffline => 'HOST TIDAK ONLINE',
    TransportStatus.hostBusy => 'PERANGKAT SEDANG DIPAKAI',
    TransportStatus.ended => 'SESI BERAKHIR',
    TransportStatus.error => 'KONEKSI GAGAL',
  };

  /// Konten saat status error: kegagalan ditampilkan terang-terangan
  /// (bukan ilustrasi dekoratif) + tombol coba lagi.
  List<Widget> _errorContent() {
    return [
      Container(
        width: 72,
        height: 72,
        decoration: BoxDecoration(
          color: AppColors.danger.withValues(alpha: 0.16),
          shape: BoxShape.circle,
          border: Border.all(
            color: AppColors.danger.withValues(alpha: 0.45),
            width: 1.5,
          ),
        ),
        child: const Icon(
          LucideIcons.alertTriangle,
          size: 34,
          color: AppColors.danger,
        ),
      ),
      const SizedBox(height: 14),
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
        decoration: BoxDecoration(
          color: AppColors.danger.withValues(alpha: 0.14),
          borderRadius: BorderRadius.circular(R.sm),
          border: Border.all(color: AppColors.danger.withValues(alpha: 0.4)),
        ),
        child: const Text(
          'KONEKSI GAGAL',
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.8,
            color: AppColors.danger,
          ),
        ),
      ),
      const SizedBox(height: 10),
      Text(
        transport.message ?? 'Tidak dapat terhubung ke host.',
        textAlign: TextAlign.center,
        style: const TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w600,
          color: Color(0xD9FFFFFF),
        ),
      ),
      const SizedBox(height: 16),
      FilledButton(
        onPressed: onRetry,
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.danger.withValues(alpha: 0.9),
          foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 10),
        ),
        child: const Text(
          'Coba lagi',
          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5),
        ),
      ),
    ];
  }

  /// Konten saat host sibuk: koneksi DITOLAK karena sesi lain sedang
  /// berjalan. Bukan error teknis — pengguna diarahkan menunggu.
  List<Widget> _busyContent() {
    return [
      Container(
        width: 72,
        height: 72,
        decoration: BoxDecoration(
          color: AppColors.warning.withValues(alpha: 0.16),
          shape: BoxShape.circle,
          border: Border.all(
            color: AppColors.warning.withValues(alpha: 0.45),
            width: 1.5,
          ),
        ),
        child: const Icon(
          LucideIcons.userX,
          size: 32,
          color: AppColors.warning,
        ),
      ),
      const SizedBox(height: 14),
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
        decoration: BoxDecoration(
          color: AppColors.warning.withValues(alpha: 0.14),
          borderRadius: BorderRadius.circular(R.sm),
          border: Border.all(color: AppColors.warning.withValues(alpha: 0.4)),
        ),
        child: const Text(
          'DITOLAK • SEDANG DIPAKAI',
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.8,
            color: AppColors.warning,
          ),
        ),
      ),
      const SizedBox(height: 10),
      Text(
        transport.message ?? 'Perangkat sedang dipakai sesi lain.',
        textAlign: TextAlign.center,
        style: const TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w600,
          color: Color(0xD9FFFFFF),
        ),
      ),
      const SizedBox(height: 16),
      FilledButton(
        onPressed: onRetry,
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.warning.withValues(alpha: 0.9),
          foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 10),
        ),
        child: const Text(
          'Coba lagi',
          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5),
        ),
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF1B1E26), Color(0xFF0E1015), Color(0xFF171920)],
        ),
      ),
      child: Stack(
        children: [
          const Positioned.fill(
            child: CustomPaint(painter: _AmbientGridPainter()),
          ),
          Center(
            child: Transform.translate(
              offset: const Offset(0, -4),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: transport.status == TransportStatus.error
                    ? _errorContent()
                    : transport.status == TransportStatus.hostBusy
                    ? _busyContent()
                    : [
                        Opacity(
                          opacity: 0.52,
                          child: Image.asset(
                            experience == SessionExperience.gaming
                                ? Img.gaming
                                : Img.screen,
                            width: 112,
                            height: 112,
                            fit: BoxFit.contain,
                            filterQuality: FilterQuality.high,
                          ),
                        ),
                        const SizedBox(height: 10),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 9,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.warning.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(R.sm),
                            border: Border.all(
                              color: AppColors.warning.withValues(alpha: 0.32),
                            ),
                          ),
                          child: Text(
                            _statusLabel,
                            style: const TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w700,
                              letterSpacing: 0.8,
                              color: AppColors.warning,
                            ),
                          ),
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          'Layar remote akan tampil di sini',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: Color(0xA6FFFFFF),
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          transport.message ??
                              'HUD dapat dipreview, tetapi belum mengirim input atau audio.',
                          style: const TextStyle(
                            fontSize: 10.5,
                            color: Color(0x66FFFFFF),
                          ),
                        ),
                      ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _AmbientGridPainter extends CustomPainter {
  const _AmbientGridPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withValues(alpha: 0.018)
      ..strokeWidth = 1;
    const spacing = 54.0;
    for (double x = 0; x < size.width; x += spacing) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
    }
    for (double y = 0; y < size.height; y += spacing) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
