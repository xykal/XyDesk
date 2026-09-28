import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/store.dart';
import '../../core/tokens.dart';
import '../../webrtc/rtc_service.dart';
import '../../webrtc/session_transport.dart';
import 'control_mapping_page.dart';
import 'panel_audio.dart';
import 'panel_controls.dart';
import 'panel_session.dart';
import 'panel_stream.dart';
import 'panel_widgets.dart';
import 'session_settings.dart';
export 'session_settings.dart';

class SessionControlPanel extends ConsumerStatefulWidget {
  const SessionControlPanel({
    super.key,
    required this.deviceName,
    required this.state,
    required this.onChanged,
    required this.onClose,
    required this.onDisconnect,
    this.initialSection = SessionPanelSection.stream,
    this.transport = const TransportState(),
    this.rtc,
    this.elapsedSec = 0,
    this.isGuestSession = false,
    this.guestSessionTotal = 0,
  });

  final String deviceName;
  final SessionSettings state;
  final ValueChanged<SessionSettings> onChanged;
  final VoidCallback onClose;
  final VoidCallback onDisconnect;
  final SessionPanelSection initialSection;

  /// Status transport nyata — dipakai panel Stream supaya tidak menampilkan
  /// teks dummy statis saat koneksi gagal/offline.
  final TransportState transport;

  /// Sesi WebRTC yang sedang jalan. Dari sini panel membaca statistik nyata
  /// (resolusi, fps, bitrate, ping) dan daftar layar host. Null berarti belum
  /// ada sesi, dan panel menampilkan tanda strip — bukan angka contoh.
  final RtcService? rtc;

  /// Detik elapsed sejak sesi tersambung. Ditampilkan di tab Sesi.
  final int elapsedSec;

  /// Apakah ini sesi tamu (tanpa login). Tamu punya batas waktu 2 jam.
  final bool isGuestSession;

  /// Total durasi sesi untuk tamu (dalam detik). Biasanya 7200 (2 jam).
  final int guestSessionTotal;

  @override
  ConsumerState<SessionControlPanel> createState() =>
      _SessionControlPanelState();
}

class _SessionControlPanelState extends ConsumerState<SessionControlPanel> {
  late SessionPanelSection _section;

  @override
  void initState() {
    super.initState();
    _section = widget.initialSection;
  }

  @override
  void didUpdateWidget(covariant SessionControlPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.initialSection != widget.initialSection) {
      _section = widget.initialSection;
    }
  }

  void _update(SessionSettings next) {
    final previous = widget.state;
    widget.onChanged(next);
    if (next.pcAudioRequested != previous.pcAudioRequested) {
      ref
          .read(settingsProvider.notifier)
          .setAudioEnabled(next.pcAudioRequested);
    }
    if (next.microphoneRequested != previous.microphoneRequested) {
      ref
          .read(settingsProvider.notifier)
          .setMicPassthrough(next.microphoneRequested);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Material(
      color: Colors.transparent,
      child: Container(
        decoration: BoxDecoration(
          color: c.overlay.withValues(alpha: 0.98),
          border: Border(
            left: BorderSide(color: c.textLow.withValues(alpha: 0.24)),
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.38),
              blurRadius: 24,
              offset: const Offset(-8, 0),
            ),
          ],
        ),
        child: SafeArea(
          left: false,
          child: Column(
            children: [
              _PanelHeader(
                deviceName: widget.deviceName,
                transport: widget.transport,
                onClose: widget.onClose,
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: ExperienceSelector(
                  value: widget.state.experience,
                  onChanged: (value) =>
                      _update(widget.state.copyWith(experience: value)),
                ),
              ),
              _SectionTabs(
                value: _section,
                onChanged: (value) => setState(() => _section = value),
              ),
              const SizedBox(height: 1),
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
                  child: switch (_section) {
                    SessionPanelSection.stream => StreamPanel(
                      transport: widget.transport,
                      rtc: widget.rtc,
                    ),
                    SessionPanelSection.audio => AudioPanel(
                      state: widget.state,
                      onChanged: _update,
                    ),
                    SessionPanelSection.controls => ControlsPanel(
                      state: widget.state,
                      onChanged: _update,
                    ),
                    SessionPanelSection.session => SessionPanel(
                      deviceName: widget.deviceName,
                      transport: widget.transport,
                      rtc: widget.rtc,
                      elapsedSec: widget.elapsedSec,
                      isGuestSession: widget.isGuestSession,
                      guestSessionTotal: widget.guestSessionTotal,
                      onDisconnect: widget.onDisconnect,
                    ),
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PanelHeader extends StatelessWidget {
  const _PanelHeader({
    required this.deviceName,
    required this.transport,
    required this.onClose,
  });

  final String deviceName;
  final TransportState transport;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final live = transport.live;
    final (dot, status) = switch (transport.status) {
      TransportStatus.connected => (c.successText, 'Tersambung'),
      TransportStatus.pairing => (c.warningText, 'Menghubungi PC'),
      TransportStatus.negotiating => (c.warningText, 'Menyiapkan koneksi'),
      TransportStatus.hostBusy => (c.warningText, 'PC sedang dipakai'),
      TransportStatus.peerOffline => (c.dangerText, 'PC tidak online'),
      TransportStatus.rejected => (c.dangerText, 'Pairing ditolak'),
      TransportStatus.error => (c.dangerText, 'Koneksi gagal'),
      TransportStatus.ended => (c.textLow, 'Sesi selesai'),
      TransportStatus.preview => (c.textLow, 'Belum tersambung'),
    };
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 14, 8, 10),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: live ? c.accentSoft : c.raised,
              borderRadius: BorderRadius.circular(R.sm),
            ),
            child: Icon(
              LucideIcons.monitor,
              size: 19,
              color: live ? c.accent : c.textMid,
            ),
          ),
          const SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  deviceName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: c.textHi,
                  ),
                ),
                const SizedBox(height: 3),
                Row(
                  children: [
                    Container(
                      width: 6,
                      height: 6,
                      decoration: BoxDecoration(
                        color: dot,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Flexible(
                      child: Text(
                        status,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(fontSize: 11.5, color: c.textLow),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Tutup',
            onPressed: onClose,
            icon: Icon(LucideIcons.x, size: 20, color: c.textMid),
          ),
        ],
      ),
    );
  }
}

class ExperienceSelector extends StatelessWidget {
  const ExperienceSelector({
    super.key,
    required this.value,
    required this.onChanged,
    this.compact = false,
  });

  final SessionExperience value;
  final ValueChanged<SessionExperience> onChanged;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Segmented<SessionExperience>(
      value: value,
      height: compact ? 38 : 44,
      entries: const [
        SegmentEntry(
          value: SessionExperience.gaming,
          label: 'Gaming',
          icon: LucideIcons.gamepad2,
        ),
        SegmentEntry(
          value: SessionExperience.desktop,
          label: 'Desktop',
          icon: LucideIcons.mouse,
        ),
      ],
      onChanged: onChanged,
    );
  }
}

class _SectionTabs extends StatelessWidget {
  const _SectionTabs({required this.value, required this.onChanged});

  final SessionPanelSection value;
  final ValueChanged<SessionPanelSection> onChanged;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    const tabs = [
      (SessionPanelSection.stream, 'Gambar', LucideIcons.monitor),
      (SessionPanelSection.audio, 'Suara', LucideIcons.volume2),
      (SessionPanelSection.controls, 'Kontrol', LucideIcons.gamepad2),
      (SessionPanelSection.session, 'Sesi', LucideIcons.info),
    ];
    // Empat tab dengan lebar sama: tidak perlu digeser-geser, dan posisinya
    // tidak berpindah saat label berubah panjang.
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 2, 12, 8),
      child: Container(
        height: 46,
        padding: const EdgeInsets.all(3),
        decoration: BoxDecoration(
          color: c.raised,
          borderRadius: BorderRadius.circular(R.md),
        ),
        child: Row(
          children: [
            for (final tab in tabs)
              Expanded(
                child: InkWell(
                  onTap: () => onChanged(tab.$1),
                  borderRadius: BorderRadius.circular(R.sm),
                  child: AnimatedContainer(
                    duration: D.fast,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: value == tab.$1
                          ? c.accentSoft
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(R.sm),
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          tab.$3,
                          size: 16,
                          color: value == tab.$1 ? c.accent : c.textMid,
                        ),
                        const SizedBox(height: 3),
                        Text(
                          tab.$2,
                          style: TextStyle(
                            fontSize: 10.5,
                            fontWeight: value == tab.$1
                                ? FontWeight.w600
                                : FontWeight.w500,
                            color: value == tab.$1 ? c.accent : c.textMid,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Pembungkus halaman pemetaan kontrol.
class ControlMappingPageWrapper extends StatelessWidget {
  const ControlMappingPageWrapper({super.key});

  @override
  Widget build(BuildContext context) {
    return const ControlMappingPage();
  }
}
