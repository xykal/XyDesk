import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';
import 'media_capabilities.dart';
import 'panel_session.dart';
import 'panel_widgets.dart';
import 'session_panels.dart';
import 'session_settings.dart';

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
            child: const Text('Mengerti'),
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
        const SectionTitle(
          title: 'Suara PC ke perangkat',
          subtitle: 'Preferensi output untuk audio sistem Windows.',
        ),
        PanelCard(
          child: Column(
            children: [
              ToggleRow(
                icon: LucideIcons.volume2,
                title: 'Minta audio PC',
                subtitle: state.pcAudioRequested
                    ? 'Aktif — audio PC (WASAPI loopback) diputar di '
                          'perangkat ini'
                    : 'Tidak diminta',
                value: state.pcAudioRequested,
                onChanged: (value) =>
                    onChanged(state.copyWith(pcAudioRequested: value)),
              ),
              const CardGap(),
              DeviceRow(
                icon: LucideIcons.monitor,
                title: 'Sumber host',
                value: 'Output default Windows',
                onTap: () => _showDeviceStatus(
                  context,
                  title: 'Pilih sumber audio PC',
                  current: 'Output default Windows',
                  requirement:
                      'WASAPI loopback dan daftar render endpoint host',
                ),
              ),
              const CardGap(),
              DeviceRow(
                icon: LucideIcons.volume2,
                title: 'Output perangkat',
                value: 'Otomatis',
                onTap: () => _showDeviceStatus(
                  context,
                  title: 'Pilih output perangkat',
                  current: 'Otomatis',
                  requirement: 'Track audio remote yang aktif',
                ),
              ),
              const CardGap(),
              SliderRow(
                label: 'Volume remote',
                valueLabel: '${(state.remoteVolume * 100).round()}%',
                value: state.remoteVolume,
                onChanged: (value) =>
                    onChanged(state.copyWith(remoteVolume: value)),
              ),
              const CardGap(),
              ToggleRow(
                icon: LucideIcons.activity,
                title: 'Audio stereo',
                subtitle: 'Mono menghemat bandwidth',
                value: state.stereoAudio,
                onChanged: (value) =>
                    onChanged(state.copyWith(stereoAudio: value)),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        Text(
          'MODE LATENSI',
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
          entries: const [
            SegmentEntry(value: AudioLatencyMode.lowLatency, label: 'Gaming'),
            SegmentEntry(value: AudioLatencyMode.balanced, label: 'Seimbang'),
            SegmentEntry(value: AudioLatencyMode.quality, label: 'Kualitas'),
          ],
          onChanged: (value) =>
              onChanged(state.copyWith(audioLatencyMode: value)),
        ),
        const SizedBox(height: 22),
        const SectionTitle(
          title: 'Mikrofon HP ke Windows',
          subtitle: 'Target akhir: endpoint XyDesk Virtual Microphone.',
        ),
        PanelCard(
          child: Column(
            children: [
              ToggleRow(
                icon: LucideIcons.mic,
                title: 'Minta passthrough mikrofon',
                subtitle: state.microphoneRequested
                    ? 'Aktif — mic dikirim dan diputar di speaker PC host'
                    : 'Mikrofon tidak diminta',
                value: state.microphoneRequested,
                onChanged: (value) =>
                    onChanged(state.copyWith(microphoneRequested: value)),
              ),
              const CardGap(),
              DeviceRow(
                icon: LucideIcons.smartphone,
                title: 'Input',
                value: 'Mikrofon default HP',
                onTap: () => _showDeviceStatus(
                  context,
                  title: 'Pilih input mikrofon',
                  current: 'Mikrofon default HP',
                  requirement: 'Izin mikrofon dan enumerasi input perangkat',
                ),
              ),
              const CardGap(),
              DeviceRow(
                icon: LucideIcons.monitor,
                title: 'Output Windows',
                value: 'XyDesk Virtual Mic',
                onTap: () => _showDeviceStatus(
                  context,
                  title: 'Target Windows',
                  current: 'XyDesk Virtual Microphone',
                  requirement: 'Komponen virtual microphone terpasang di host',
                ),
              ),
              const CardGap(),
              const InactiveLevelMeter(),
              const CardGap(),
              SliderRow(
                label: 'Gain input',
                valueLabel: '${((state.microphoneGain - 0.5) * 24).round()} dB',
                value: state.microphoneGain,
                onChanged: (value) =>
                    onChanged(state.copyWith(microphoneGain: value)),
              ),
              const CardGap(),
              SliderRow(
                label: 'Level kirim',
                valueLabel: '${(state.microphoneSendLevel * 100).round()}%',
                value: state.microphoneSendLevel,
                onChanged: (value) =>
                    onChanged(state.copyWith(microphoneSendLevel: value)),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        const SectionTitle(
          title: 'Pemrosesan suara',
          subtitle: 'Diterapkan pada capture sebelum Opus ketika engine siap.',
        ),
        PanelCard(
          child: Column(
            children: [
              ToggleRow(
                icon: LucideIcons.activity,
                title: 'Peredam bising',
                subtitle: 'Noise suppression',
                value: state.noiseSuppression,
                onChanged: (value) =>
                    onChanged(state.copyWith(noiseSuppression: value)),
              ),
              const CardGap(),
              ToggleRow(
                icon: LucideIcons.volume2,
                title: 'Peredam gema',
                subtitle: 'AEC untuk suara speaker perangkat',
                value: state.echoCancellation,
                onChanged: (value) =>
                    onChanged(state.copyWith(echoCancellation: value)),
              ),
              const CardGap(),
              ToggleRow(
                icon: LucideIcons.gauge,
                title: 'Gain otomatis',
                subtitle: 'AGC menstabilkan volume suara',
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
          entries: const [
            SegmentEntry(
              value: MicrophoneMode.alwaysOn,
              label: 'Selalu aktif',
              icon: LucideIcons.mic,
            ),
            SegmentEntry(
              value: MicrophoneMode.pushToTalk,
              label: 'Push to talk',
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
