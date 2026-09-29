import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';
import '../../webrtc/rtc_service.dart';
import '../../webrtc/session_transport.dart';
import 'media_capabilities.dart';
import 'panel_widgets.dart';
import '../../core/l10n_bridge.dart';

class SessionPanel extends StatelessWidget {
  const SessionPanel({
    super.key,
    required this.deviceName,
    required this.transport,
    required this.onDisconnect,
    this.rtc,
    this.elapsedSec = 0,
    this.isGuestSession = false,
    this.guestSessionTotal = 0,
  });

  final String deviceName;
  final TransportState transport;
  final RtcService? rtc;
  final int elapsedSec;
  final bool isGuestSession;
  final int guestSessionTotal;
  final VoidCallback onDisconnect;

  static String _fmtDurasi(int totalSec) {
    final h = totalSec ~/ 3600;
    final m = (totalSec % 3600) ~/ 60;
    final s = totalSec % 60;
    if (h > 0) {
      return '${h}j ${m.toString().padLeft(2, '0')}m ${s.toString().padLeft(2, '0')}d';
    }
    return '${m.toString().padLeft(2, '0')}m ${s.toString().padLeft(2, '0')}d';
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final service = rtc;

    // Hitung sisa waktu untuk sesi tamu
    final remaining = isGuestSession
        ? (guestSessionTotal - elapsedSec).clamp(0, guestSessionTotal)
        : 0;
    final isCritical = isGuestSession && remaining <= 300 && remaining > 0;
    final isExpired = isGuestSession && remaining <= 0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Countdown card untuk sesi tamu
        if (isGuestSession) ...[
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: isExpired
                  ? c.danger.withValues(alpha: 0.12)
                  : isCritical
                  ? c.warning.withValues(alpha: 0.12)
                  : c.raised,
              borderRadius: BorderRadius.circular(R.md),
              border: Border.all(
                color: isExpired
                    ? c.danger.withValues(alpha: 0.5)
                    : isCritical
                    ? c.warning.withValues(alpha: 0.5)
                    : c.textLow.withValues(alpha: 0.16),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      isExpired
                          ? LucideIcons.circleX
                          : isCritical
                          ? LucideIcons.timer
                          : LucideIcons.clock,
                      size: 18,
                      color: isExpired
                          ? c.danger
                          : isCritical
                          ? c.warning
                          : c.textMid,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      isExpired
                          ? context.tr('ss_guest_ended')
                          : isCritical
                          ? context.tr('ss_guest_soon')
                          : context.tr('ss_guest'),
                      style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w600,
                        color: isExpired
                            ? c.danger
                            : isCritical
                            ? c.warning
                            : c.textHi,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'TOTAL',
                            style: TextStyle(
                              fontSize: 10,
                              color: c.textLow,
                              letterSpacing: 0.5,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            _fmtDurasi(guestSessionTotal),
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: c.textHi,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Container(
                      width: 1,
                      height: 32,
                      color: c.textLow.withValues(alpha: 0.2),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'SISA',
                            style: TextStyle(
                              fontSize: 10,
                              color: isExpired
                                  ? c.danger
                                  : isCritical
                                  ? c.warning
                                  : c.textLow,
                              letterSpacing: 0.5,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            isExpired ? '00m 00d' : _fmtDurasi(remaining),
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: isExpired
                                  ? c.danger
                                  : isCritical
                                  ? c.warning
                                  : c.accent,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                if (isExpired) ...[
                  const SizedBox(height: 10),
                  Text(
                    context.tr('ss_guest_ended_body'),
                    style: TextStyle(
                      fontSize: 11,
                      color: c.danger,
                      height: 1.4,
                    ),
                  ),
                ] else if (isCritical) ...[
                  const SizedBox(height: 10),
                  Text(
                    context.tr('ss_guest_soon_body'),
                    style: TextStyle(
                      fontSize: 11,
                      color: c.warning,
                      height: 1.4,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 16),
        ],
        SectionTitle(title: context.tr('ss_this_session')),
        StreamBuilder<SessionStats>(
          initialData: service?.stats,
          stream: service?.statsStream,
          builder: (context, snapshot) {
            final st = snapshot.data;
            return PanelCard(
              child: Column(
                children: [
                  InfoRow(
                    icon: LucideIcons.monitor,
                    title: context.tr('ss_device'),
                    value: deviceName,
                  ),
                  const CardGap(),
                  InfoRow(
                    icon: LucideIcons.clock,
                    title: context.tr('ss_duration'),
                    value: transport.live ? _fmtDurasi(elapsedSec) : '—',
                  ),
                  const CardGap(),
                  InfoRow(
                    icon: LucideIcons.wifi,
                    title: context.tr('ss_link'),
                    value: switch (transport.status) {
                      TransportStatus.connected => context.tr('ss_direct'),
                      TransportStatus.pairing => context.tr('ss_pairing'),
                      TransportStatus.negotiating => context.tr('ss_negotiating'),
                      TransportStatus.hostBusy => context.tr('ss_busy'),
                      TransportStatus.peerOffline => context.tr('ss_offline'),
                      TransportStatus.rejected => context.tr('ss_rejected'),
                      TransportStatus.error => context.tr('ss_failed'),
                      TransportStatus.ended => context.tr('ss_closed'),
                      TransportStatus.preview => context.tr('ss_not_connected'),
                    },
                  ),
                  const CardGap(),
                  InfoRow(
                    icon: LucideIcons.video,
                    title: context.tr('ss_video'),
                    value: st?.hasVideo == true
                        ? '${st!.resolutionLabel} - ${st.fpsLabel}'
                        : context.tr('ss_no_video'),
                  ),
                  const CardGap(),
                  InfoRow(
                    icon: LucideIcons.volume2,
                    title: context.tr('ss_audio_from_pc'),
                    value: st?.audioLabel ?? context.tr('ss_no_audio'),
                  ),
                  const CardGap(),
                  InfoRow(
                    icon: LucideIcons.mic,
                    title: context.tr('ss_mic_to_pc'),
                    value: service?.micEnabled == true ? context.tr('ss_on') : context.tr('ss_off'),
                  ),
                ],
              ),
            );
          },
        ),
        const SizedBox(height: 16),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: OutlinedButton.icon(
            onPressed: onDisconnect,
            icon: const Icon(LucideIcons.power, size: 18),
            label: Text(context.tr('ss_disconnect')),
            style: OutlinedButton.styleFrom(
              foregroundColor: context.c.danger,
              side: BorderSide(color: context.c.danger.withValues(alpha: 0.55)),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(R.md),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class CapabilityCard extends StatelessWidget {
  const CapabilityCard({super.key, required this.capabilities});

  final SessionMediaCapabilities capabilities;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: c.warning.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(R.md),
        border: Border.all(color: c.warning.withValues(alpha: 0.4)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(LucideIcons.activity, size: 19, color: c.warning),
          const SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        context.tr('ss_audio_engine'),
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: c.textHi,
                        ),
                      ),
                    ),
                    if (capabilities.freeDuringBeta)
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 7,
                          vertical: 3,
                        ),
                        decoration: BoxDecoration(
                          color: c.success.withValues(alpha: 0.14),
                          borderRadius: BorderRadius.circular(R.sm),
                        ),
                        child: Text(
                          context.tr('ss_free_beta'),
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 0.4,
                            color: c.success,
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 5),
                Text(
                  context.tr('ss_audio_note'),
                  style: TextStyle(
                    fontSize: 11.5,
                    height: 1.45,
                    color: c.textMid,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
