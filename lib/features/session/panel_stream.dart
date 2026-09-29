import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/store.dart';
import '../../core/tokens.dart';
import '../../webrtc/rtc_service.dart';
import '../../webrtc/session_transport.dart';
import 'panel_widgets.dart';
import '../../core/l10n_bridge.dart';

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
        SectionTitle(
          title: context.tr('st_live_stats'),
          subtitle: context.tr('st_live_sub'),
        ),
        if (service == null)
          PanelCard(
            child: Text(
              context.tr('st_no_session'),
              style: const TextStyle(fontSize: 13, height: 1.6),
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
                      title: context.tr('st_resolution'),
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
                      title: context.tr('st_bitrate'),
                      value: st.bitrateLabel,
                    ),
                    const CardGapLarge(),
                    InfoRow(
                      icon: LucideIcons.wifi,
                      title: context.tr('st_ping'),
                      value: st.rttLabel,
                    ),
                    const CardGapLarge(),
                    // Dua komponen latensi sisi client yang bisa diukur
                    // langsung dari getStats. Ping saja tidak menjelaskan
                    // "lag" — frame bisa menunggu lebih lama di buffer
                    // decoder daripada di jaringan.
                    InfoRow(
                      icon: LucideIcons.timer,
                      title: context.tr('st_buffer'),
                      value: st.jitterBufferLabel,
                    ),
                    const CardGapLarge(),
                    InfoRow(
                      icon: LucideIcons.cpu,
                      title: context.tr('st_decode'),
                      value: st.decodeLabel,
                    ),
                    const CardGapLarge(),
                    InfoRow(
                      icon: LucideIcons.triangleAlert,
                      title: context.tr('st_loss'),
                      value: st.lossLabel,
                    ),
                    const CardGapLarge(),
                    // Relay = jalan keluar terakhir saat NAT menolak jalur
                    // langsung. Ketiadaannya dulu tidak terlihat sama sekali;
                    // sekarang angkanya/sebabnya ikut di baris ini.
                    InfoRow(
                      icon: LucideIcons.info,
                      title: context.tr('st_turn'),
                      value: st.relayLabel,
                    ),
                    const CardGapLarge(),
                    InfoRow(
                      icon: LucideIcons.cpu,
                      title: context.tr('st_codec'),
                      value: st.codec ?? '-',
                    ),
                  ],
                ),
              );
            },
          ),
        const SizedBox(height: 20),
        if (service != null) _DisplayPicker(rtc: service),

        SectionTitle(
          title: context.tr('st_quality'),
          subtitle: context.tr('st_quality_sub'),
        ),
        PanelCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Segmented<StreamQuality>(
                value: settings.quality,
                entries: [
                  SegmentEntry(
                    value: StreamQuality.auto,
                    label: context.tr('st_auto'),
                  ),
                  SegmentEntry(
                    value: StreamQuality.medium,
                    label: context.tr('st_medium'),
                  ),
                  SegmentEntry(
                    value: StreamQuality.high,
                    label: context.tr('st_high'),
                  ),
                  SegmentEntry(
                    value: StreamQuality.ultra,
                    label: context.tr('st_ultra'),
                  ),
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
        SectionTitle(
          title: context.tr('st_bitrate'),
          subtitle: context.tr('st_bitrate_sub'),
        ),
        PanelCard(
          child: Column(
            children: [
              Segmented<int>(
                value: settings.bitrateMbps,
                entries: [
                  SegmentEntry(value: 0, label: context.tr('st_auto')),
                  const SegmentEntry(value: 8, label: '8'),
                  const SegmentEntry(value: 15, label: '15'),
                  const SegmentEntry(value: 25, label: '25'),
                  const SegmentEntry(value: 50, label: '50'),
                ],
                onChanged: (b) =>
                    ref.read(settingsProvider.notifier).setBitrateMbps(b),
              ),
              const SizedBox(height: 12),
              InfoRow(
                icon: LucideIcons.maximize,
                title: context.tr('st_resolution'),
                value: settings.resolution,
              ),
              const CardGapLarge(),
              InfoRow(
                icon: LucideIcons.fileVideo,
                title: context.tr('st_codec'),
                value: settings.codec.split(' ')[0],
              ),
              const CardGapLarge(),
              SliderRow(
                label: context.tr('st_custom_bitrate'),
                valueLabel: settings.bitrateMbps == 0
                    ? context.tr('st_auto')
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
              TransportStatus.pairing => context.tr('st_pairing'),
              TransportStatus.negotiating => context.tr('st_negotiating'),
              TransportStatus.rejected => context.tr('st_rejected'),
              TransportStatus.peerOffline => context.tr('st_offline'),
              TransportStatus.hostBusy => context.tr('st_busy'),
              TransportStatus.ended => context.tr('st_ended'),
              TransportStatus.error => context.tr('st_failed'),
              TransportStatus.connected => context.tr('st_connected'),
              TransportStatus.preview => context.tr('st_not_connected'),
            },
            body: transport.message ?? context.tr('st_start_from_list'),
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
            SectionTitle(
              title: context.tr('st_pc_screen'),
              subtitle: context.tr('st_multi_monitor'),
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
