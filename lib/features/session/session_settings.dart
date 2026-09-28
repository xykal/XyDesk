import 'package:flutter/material.dart';

enum SessionExperience { gaming, desktop }

enum SessionPanelSection { stream, audio, controls, session }

enum AudioLatencyMode { lowLatency, balanced, quality }

enum MicrophoneMode { alwaysOn, pushToTalk }

/// Sumber papan ketik saat sesi.
enum KeyboardSource {
  /// Papan ketik XyDesk (tata letak penuh, F1–F12, modifier sticky) —
  /// mengirim keycode Windows ke host. Paling cocok untuk game dan kontrol
  /// penuh.
  xydesk,

  /// Papan ketik sistem (IME Android) lewat field teks — mengetik sebagai
  /// teks bebas, tidak tergantung tata letak keyboard host. Cocok untuk
  /// formulir, kolom pencarian, dan mengetik cepat.
  system,
}

@immutable
class SessionSettings {
  const SessionSettings({
    this.experience = SessionExperience.gaming,
    this.pcAudioRequested = true,
    this.microphoneRequested = false,
    this.remoteVolume = 0.78,
    this.microphoneGain = 0.58,
    this.microphoneSendLevel = 0.86,
    this.noiseSuppression = true,
    this.echoCancellation = true,
    this.autoGainControl = false,
    this.audioLatencyMode = AudioLatencyMode.balanced,
    this.microphoneMode = MicrophoneMode.alwaysOn,
    this.stereoAudio = true,
    this.showGamingControls = true,
    this.haptics = true,
    this.pointerSensitivity = 0.46,
    this.tapToClick = true,
    this.reverseScroll = false,
    this.pointerLock = false,
    this.keyboardSource = KeyboardSource.xydesk,
  });

  final SessionExperience experience;
  final bool pcAudioRequested;
  final bool microphoneRequested;
  final double remoteVolume;
  final double microphoneGain;
  final double microphoneSendLevel;
  final bool noiseSuppression;
  final bool echoCancellation;
  final bool autoGainControl;
  final AudioLatencyMode audioLatencyMode;
  final MicrophoneMode microphoneMode;
  final bool stereoAudio;
  final bool showGamingControls;
  final bool haptics;
  final double pointerSensitivity;
  final bool tapToClick;
  final bool reverseScroll;
  final bool pointerLock;
  final KeyboardSource keyboardSource;

  SessionSettings copyWith({
    SessionExperience? experience,
    bool? pcAudioRequested,
    bool? microphoneRequested,
    double? remoteVolume,
    double? microphoneGain,
    double? microphoneSendLevel,
    bool? noiseSuppression,
    bool? echoCancellation,
    bool? autoGainControl,
    AudioLatencyMode? audioLatencyMode,
    MicrophoneMode? microphoneMode,
    bool? stereoAudio,
    bool? showGamingControls,
    bool? haptics,
    double? pointerSensitivity,
    bool? tapToClick,
    bool? reverseScroll,
    bool? pointerLock,
    KeyboardSource? keyboardSource,
  }) {
    return SessionSettings(
      experience: experience ?? this.experience,
      pcAudioRequested: pcAudioRequested ?? this.pcAudioRequested,
      microphoneRequested: microphoneRequested ?? this.microphoneRequested,
      remoteVolume: remoteVolume ?? this.remoteVolume,
      microphoneGain: microphoneGain ?? this.microphoneGain,
      microphoneSendLevel: microphoneSendLevel ?? this.microphoneSendLevel,
      noiseSuppression: noiseSuppression ?? this.noiseSuppression,
      echoCancellation: echoCancellation ?? this.echoCancellation,
      autoGainControl: autoGainControl ?? this.autoGainControl,
      audioLatencyMode: audioLatencyMode ?? this.audioLatencyMode,
      microphoneMode: microphoneMode ?? this.microphoneMode,
      stereoAudio: stereoAudio ?? this.stereoAudio,
      showGamingControls: showGamingControls ?? this.showGamingControls,
      haptics: haptics ?? this.haptics,
      pointerSensitivity: pointerSensitivity ?? this.pointerSensitivity,
      tapToClick: tapToClick ?? this.tapToClick,
      reverseScroll: reverseScroll ?? this.reverseScroll,
      pointerLock: pointerLock ?? this.pointerLock,
      keyboardSource: keyboardSource ?? this.keyboardSource,
    );
  }
}
