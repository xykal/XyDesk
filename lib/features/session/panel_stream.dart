import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/store.dart';
import '../../core/tokens.dart';
import '../../webrtc/rtc_service.dart';
import '../../webrtc/session_transport.dart';
import 'panel_widgets.dart';

class StreamPanel extends ConsumerWidget {
  const StreamPanel({
    super.key,
    this.transport = const TransportState(),
    this.rtc,
  });

  final TransportState transport;
  final RtcService? rtc;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    final service = rtc;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SectionTitle(
          title: 'Live Stats',
          subtitle: 'Realtime from connection — ms, fps, bitrate',
        ),
        if (service == null)
          const PanelCard(
            child: Text(
              'Belum ada sesi. Angka kualitas muncul begitu PC tersambung.',
              style: TextStyle(fontSize: 13, height: 1.6),
            ),
          )
        else
          StreamBuilder<SessionStats>(
            initialData: service.stats,
            stream: service.statsStream,
            builder: (context, snapshot) {
              final st = snapshot.data ?? const SessionStats();
              return PanelCard(
                child: Column(
                  children: [
                    InfoRow(
                      icon: LucideIcons.monitor,
                      title: 'Resolution',
                      value: st.resolutionLabel,
                    ),
                    const CardGapLarge(),
                    InfoRow(
                      icon: LucideIcons.activity,
                      title: 'FPS',
                      value: st.fpsLabel,
                    ),
                    const CardGapLarge(),
                    InfoRow(
                      icon: LucideIcons.gauge,
                      title: 'Bitrate',
                      value: st.bitrateLabel,
                    ),
                    const CardGapLarge(),
                    InfoRow(
                      icon: LucideIcons.wifi,
                      title: 'Ping (realtime)',
                      value: st.rttLabel,
                    ),
                    const CardGapLarge(),
                    // Dua komponen latensi sisi client yang bisa diukur
                    // langsung dari getStats. Ping saja tidak menjelaskan
                    // "lag" — frame bisa menunggu lebih lama di buffer
                    // decoder daripada di jaringan.
                    InfoRow(
                      icon: LucideIcons.timer,
                      title: 'Buffer video',
                      value: st.jitterBufferLabel,
                    ),
                    const CardGapLarge(),
                    InfoRow(
                      icon: LucideIcons.cpu,
                      title: 'Decode / frame',
                      value: st.decodeLabel,
                    ),
                    const CardGapLarge(),
                    InfoRow(
                      icon: LucideIcons.triangleAlert,
                      title: 'Packet loss',
                      value: st.lossLabel,
                    ),
                    const CardGapLarge(),
                    // Relay = jalan keluar terakhir saat NAT menolak jalur
                    // langsung. Ketiadaannya dulu tidak terlihat sama sekali;
                    // sekarang angkanya/sebabnya ikut di baris ini.
                    InfoRow(
                      icon: LucideIcons.info,
                      title: 'Relay TURN',
                      value: st.relayLabel,
                    ),
                    const CardGapLarge(),
                    InfoRow(
                      icon: LucideIcons.cpu,
                      title: 'Codec',
                      value: st.codec ?? '-',
                    ),
                  ],
                ),
              );
            },
          ),
        const SizedBox(height: 20),
        if (service != null) _DisplayPicker(rtc: service),

        const SectionTitle(
          title: 'Quality',
          subtitle: 'Auto adapts to network — or pick fixed',
        ),
        PanelCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Segmented<StreamQuality>(
                value: settings.quality,
                entries: const [
                  SegmentEntry(value: StreamQuality.auto, label: 'Auto'),
                  SegmentEntry(value: StreamQuality.medium, label: 'Medium'),
                  SegmentEntry(value: StreamQuality.high, label: 'High'),
                  SegmentEntry(value: StreamQuality.ultra, label: 'Ultra'),
                ],
                onChanged: (q) =>
                    ref.read(settingsProvider.notifier).setQuality(q),
              ),
              const SizedBox(height: 10),
              if (settings.quality == StreamQuality.auto &&
                  service?.autoDecision != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Text(
                    'Sekarang ${service!.autoDecision} — ${service.autoDecision!.reason}',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: context.c.textHi,
                    ),
                  ),
                ),
              Text(
                settings.quality.desc,
                style: TextStyle(
                  fontSize: 11,
                  color: context.c.textLow,
                  height: 1.4,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // Bitrate selector — Auto + fixed values
        const SectionTitle(
          title: 'Bitrate',
          subtitle: 'Auto = host decides, or manual limit',
        ),
        PanelCard(
          child: Column(
            children: [
              Segmented<int>(
                value: settings.bitrateMbps,
                entries: const [
                  SegmentEntry(value: 0, label: 'Auto'),
                  SegmentEntry(value: 8, label: '8'),
                  SegmentEntry(value: 15, label: '15'),
                  SegmentEntry(value: 25, label: '25'),
                  SegmentEntry(value: 50, label: '50'),
                ],
                onChanged: (b) =>
                    ref.read(settingsProvider.notifier).setBitrateMbps(b),
              ),
              const SizedBox(height: 12),
              InfoRow(
                icon: LucideIcons.maximize,
                title: 'Resolution',
                value: settings.resolution,
              ),
              const CardGapLarge(),
              InfoRow(
                icon: LucideIcons.fileVideo,
                title: 'Codec',
                value: settings.codec.split(' ')[0],
              ),
              const CardGapLarge(),
              SliderRow(
                label: 'Custom bitrate limit',
                valueLabel: settings.bitrateMbps == 0
                    ? 'Auto'
                    : '${settings.bitrateMbps} Mbps',
                value: settings.bitrateMbps == 0
                    ? 0.5
                    : ((settings.bitrateMbps - 5) / 45).clamp(0.0, 1.0),
                onChanged: (v) => ref
                    .read(settingsProvider.notifier)
                    .setBitrateMbps(v < 0.05 ? 0 : (5 + v * 45).round()),
              ),
            ],
          ),
        ),
        if (!transport.live) ...[
          const SizedBox(height: 20),
          StatusCard(
            icon: transport.status == TransportStatus.error
                ? LucideIcons.wifiOff
                : LucideIcons.info,
            title: switch (transport.status) {
              TransportStatus.pairing => 'Sedang menghubungi PC',
              TransportStatus.negotiating => 'Sedang menyiapkan koneksi',
              TransportStatus.rejected => 'PC menolak sambungan',
              TransportStatus.peerOffline => 'PC tidak online',
              TransportStatus.hostBusy => 'PC sedang dipakai sesi lain',
              TransportStatus.ended => 'Sesi sudah ditutup',
              TransportStatus.error => 'Koneksi gagal',
              TransportStatus.connected => 'Tersambung',
              TransportStatus.preview => 'Belum tersambung',
            },
            body:
                transport.message ??
                'Belum ada sesi berjalan. Mulai dari daftar perangkat.',
          ),
        ],
      ],
    );
  }
}

/// Pemilih layar host. Dulu chip ini melayang di bawah layar sesi dan menutupi
/// gambar; sekarang tinggal di panel bersama pengaturan gambar lainnya.
class _DisplayPicker extends StatelessWidget {
  const _DisplayPicker({required this.rtc});

  final RtcService rtc;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return StreamBuilder<HostMeta>(
      initialData: rtc.hostMeta,
      stream: rtc.hostMetaStream,
      builder: (context, snapshot) {
        final displays = snapshot.data?.displays ?? const <HostDisplay>[];
        if (displays.length < 2) return const SizedBox.shrink();
        final wanted = snapshot.data?.wantedDisplay ?? 0;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SectionTitle(
              title: 'Layar PC',
              subtitle: 'PC ini punya lebih dari satu monitor.',
            ),
            PanelCard(
              child: Column(
                children: [
                  for (final d in displays) ...[
                    if (d != displays.first) const CardGap(),
                    InkWell(
                      onTap: () => rtc.selectDisplay(d.index),
                      borderRadius: BorderRadius.circular(R.sm),
                      child: Row(
                        children: [
                          Icon(
                            d.index == wanted
                                ? LucideIcons.circleCheck
                                : LucideIcons.circle,
                            size: 17,
                            color: d.index == wanted ? c.accent : c.textLow,
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              d.name.isEmpty ? 'Layar ${d.index + 1}' : d.name,
                              style: TextStyle(fontSize: 13, color: c.textHi),
                            ),
                          ),
                          Text(
                            '${d.width} x ${d.height}',
                            style: TextStyle(fontSize: 11.5, color: c.textLow),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 16),
          ],
        );
      },
    );
  }
}
