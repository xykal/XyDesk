import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';
import 'media_capabilities.dart';
import 'panel_session.dart';
import 'panel_widgets.dart';
import 'session_panels.dart';
import 'session_settings.dart';
import '../../core/l10n_bridge.dart';

class AudioPanel extends StatelessWidget {
  const AudioPanel({super.key, required this.state, required this.onChanged});

  final SessionSettings state;
  final ValueChanged<SessionSettings> onChanged;

  static const _capabilities = SessionMediaCapabilities.currentBuild;

  void _showDeviceStatus(
    BuildContext context, {
    required String title,
    required String current,
    required String requirement,
  }) {
    final c = context.c;
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(title, style: const TextStyle(fontSize: 16)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              current,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: c.textHi,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Jalur audio aktif sejak rilis 6.1. $requirement',
              style: TextStyle(fontSize: 12, height: 1.5, color: c.textMid),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(context.tr('au_got_it')),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const CapabilityCard(capabilities: _capabilities),
        const SizedBox(height: 18),
        SectionTitle(
          title: context.tr('au_pc_to_device'),
          subtitle: context.tr('au_pc_to_device_sub'),
        ),
        PanelCard(
          child: Column(
            children: [
              ToggleRow(
                icon: LucideIcons.volume2,
                title: context.tr('au_request_pc'),
                subtitle: state.pcAudioRequested
                    ? context.tr('au_request_pc_on')
                    : context.tr('au_not_requested'),
                value: state.pcAudioRequested,
                onChanged: (value) =>
                    onChanged(state.copyWith(pcAudioRequested: value)),
              ),
              const CardGap(),
              DeviceRow(
                icon: LucideIcons.monitor,
                title: context.tr('au_host_source'),
                value: context.tr('au_win_default'),
                onTap: () => _showDeviceStatus(
                  context,
                  title: context.tr('au_pick_pc_source'),
                  current: context.tr('au_win_default'),
                  requirement: context.tr('au_pick_pc_source_req'),
                ),
              ),
              const CardGap(),
              DeviceRow(
                icon: LucideIcons.volume2,
                title: context.tr('au_device_output'),
                value: context.tr('au_auto'),
                onTap: () => _showDeviceStatus(
                  context,
                  title: context.tr('au_pick_device_output'),
                  current: context.tr('au_auto'),
                  requirement: context.tr('au_pick_device_output_req'),
                ),
              ),
              const CardGap(),
              SliderRow(
                label: context.tr('au_remote_volume'),
                valueLabel: '${(state.remoteVolume * 100).round()}%',
                value: state.remoteVolume,
                onChanged: (value) =>
                    onChanged(state.copyWith(remoteVolume: value)),
              ),
              const CardGap(),
              ToggleRow(
                icon: LucideIcons.activity,
                title: context.tr('au_stereo'),
                subtitle: context.tr('au_stereo_sub'),
                value: state.stereoAudio,
                onChanged: (value) =>
                    onChanged(state.copyWith(stereoAudio: value)),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        Text(
          context.tr('au_latency_mode'),
          style: TextStyle(
            fontSize: 10.5,
            letterSpacing: 0.7,
            fontWeight: FontWeight.w600,
            color: context.c.textLow,
          ),
        ),
        const SizedBox(height: 8),
        Segmented<AudioLatencyMode>(
          value: state.audioLatencyMode,
          entries: [
            SegmentEntry(
              value: AudioLatencyMode.lowLatency,
              label: context.tr('au_mode_gaming'),
            ),
            SegmentEntry(
              value: AudioLatencyMode.balanced,
              label: context.tr('au_mode_balanced'),
            ),
            SegmentEntry(
              value: AudioLatencyMode.quality,
              label: context.tr('au_mode_quality'),
            ),
          ],
          onChanged: (value) =>
              onChanged(state.copyWith(audioLatencyMode: value)),
        ),
        const SizedBox(height: 22),
        SectionTitle(
          title: context.tr('au_mic_to_win'),
          subtitle: context.tr('au_mic_to_win_sub'),
        ),
        PanelCard(
          child: Column(
            children: [
              ToggleRow(
                icon: LucideIcons.mic,
                title: context.tr('au_request_mic'),
                subtitle: state.microphoneRequested
                    ? context.tr('au_request_mic_on')
                    : context.tr('au_mic_not_requested'),
                value: state.microphoneRequested,
                onChanged: (value) =>
                    onChanged(state.copyWith(microphoneRequested: value)),
              ),
              const CardGap(),
              DeviceRow(
                icon: LucideIcons.smartphone,
                title: context.tr('au_input'),
                value: context.tr('au_phone_default_mic'),
                onTap: () => _showDeviceStatus(
                  context,
                  title: context.tr('au_pick_mic'),
                  current: context.tr('au_phone_default_mic'),
                  requirement: context.tr('au_pick_mic_req'),
                ),
              ),
              const CardGap(),
              DeviceRow(
                icon: LucideIcons.monitor,
                title: context.tr('au_win_output'),
                value: context.tr('au_virtual_mic_short'),
                onTap: () => _showDeviceStatus(
                  context,
                  title: context.tr('au_win_target'),
                  current: context.tr('au_virtual_mic'),
                  requirement: context.tr('au_virtual_mic_req'),
                ),
              ),
              const CardGap(),
              const InactiveLevelMeter(),
              const CardGap(),
              SliderRow(
                label: context.tr('au_input_gain'),
                valueLabel: '${((state.microphoneGain - 0.5) * 24).round()} dB',
                value: state.microphoneGain,
                onChanged: (value) =>
                    onChanged(state.copyWith(microphoneGain: value)),
              ),
              const CardGap(),
              SliderRow(
                label: context.tr('au_send_level'),
                valueLabel: '${(state.microphoneSendLevel * 100).round()}%',
                value: state.microphoneSendLevel,
                onChanged: (value) =>
                    onChanged(state.copyWith(microphoneSendLevel: value)),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        SectionTitle(
          title: context.tr('au_processing'),
          subtitle: context.tr('au_processing_sub'),
        ),
        PanelCard(
          child: Column(
            children: [
              ToggleRow(
                icon: LucideIcons.activity,
                title: context.tr('au_ns'),
                subtitle: context.tr('au_ns_sub'),
                value: state.noiseSuppression,
                onChanged: (value) =>
                    onChanged(state.copyWith(noiseSuppression: value)),
              ),
              const CardGap(),
              ToggleRow(
                icon: LucideIcons.volume2,
                title: context.tr('au_aec'),
                subtitle: context.tr('au_aec_sub'),
                value: state.echoCancellation,
                onChanged: (value) =>
                    onChanged(state.copyWith(echoCancellation: value)),
              ),
              const CardGap(),
              ToggleRow(
                icon: LucideIcons.gauge,
                title: context.tr('au_agc'),
                subtitle: context.tr('au_agc_sub'),
                value: state.autoGainControl,
                onChanged: (value) =>
                    onChanged(state.copyWith(autoGainControl: value)),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        Segmented<MicrophoneMode>(
          value: state.microphoneMode,
          entries: [
            SegmentEntry(
              value: MicrophoneMode.alwaysOn,
              label: context.tr('au_always_on'),
              icon: LucideIcons.mic,
            ),
            SegmentEntry(
              value: MicrophoneMode.pushToTalk,
              label: context.tr('au_ptt'),
              icon: LucideIcons.gamepad2,
            ),
          ],
          onChanged: (value) =>
              onChanged(state.copyWith(microphoneMode: value)),
        ),
        const SizedBox(height: 14),
        const PreferenceNotice(),
      ],
    );
  }
}
