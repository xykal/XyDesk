import 'package:flutter/material.dart';

import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';
import 'media_capabilities.dart';

/// Rail kontrol sesi.
///
/// Menempel di tepi kanan dan hanya selebar ikon. Versi sebelumnya memakai
/// bar melayang di tengah atas dan dock di tengah bawah; dua-duanya menutupi
/// bagian layar PC yang paling sering dilihat. Rail ini bisa dilipat jadi tab
/// kecil, dan saat dilipat tidak ada apa pun yang menghalangi gambar.
class SessionRail extends StatelessWidget {
  const SessionRail({
    super.key,
    required this.expanded,
    required this.compact,
    required this.showClipboard,
    required this.audioRequested,
    required this.microphoneRequested,
    required this.onToggleExpanded,
    required this.onAudio,
    required this.onMicrophone,
    required this.onKeyboard,
    required this.onClipboard,
    required this.onClipboardPull,
    required this.onSettings,
    required this.onDisconnect,
  });

  final bool expanded;
  final bool compact;
  final bool showClipboard;
  final bool audioRequested;
  final bool microphoneRequested;
  final VoidCallback onToggleExpanded;
  final VoidCallback onAudio;
  final VoidCallback onMicrophone;
  final VoidCallback onKeyboard;
  final VoidCallback onClipboard;
  final VoidCallback onClipboardPull;
  final VoidCallback onSettings;
  final VoidCallback onDisconnect;

  @override
  Widget build(BuildContext context) {
    const caps = SessionMediaCapabilities.currentBuild;
    if (!expanded) {
      return GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onToggleExpanded,
        child: Opacity(
          opacity: 0.34,
          child: Container(
            width: 20,
            height: 58,
            margin: const EdgeInsets.only(right: 4),
            decoration: BoxDecoration(
              color: const Color(0xC818191D),
              borderRadius: BorderRadius.circular(R.pill),
              border: Border.all(color: Colors.white.withValues(alpha: 0.14)),
            ),
            child: const Icon(
              LucideIcons.chevronLeft,
              size: 14,
              color: Colors.white70,
            ),
          ),
        ),
      );
    }

    final size = compact ? 40.0 : 44.0;
    return Container(
      margin: const EdgeInsets.only(right: 6),
      padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 3),
      decoration: BoxDecoration(
        color: const Color(0xEB18191D),
        borderRadius: BorderRadius.circular(R.lg),
        border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.32),
            blurRadius: 18,
            offset: const Offset(-4, 4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _RailButton(
            icon: LucideIcons.chevronRight,
            tooltip: 'Sembunyikan kontrol',
            size: size,
            onTap: onToggleExpanded,
          ),
          const _RailDivider(),
          _RailButton(
            icon: LucideIcons.volume2,
            tooltip: 'Suara PC',
            size: size,
            active: audioRequested,
            pending: audioRequested && !caps.pcSystemAudio.isActive,
            onTap: onAudio,
          ),
          _RailButton(
            icon: LucideIcons.mic,
            tooltip: 'Mik ke PC',
            size: size,
            active: microphoneRequested,
            pending: microphoneRequested && !caps.phoneMicrophone.isActive,
            onTap: onMicrophone,
          ),
          _RailButton(
            icon: LucideIcons.keyboard,
            tooltip: 'Keyboard',
            size: size,
            onTap: onKeyboard,
          ),
          if (showClipboard)
            _RailButton(
              icon: LucideIcons.clipboard,
              tooltip: 'Kirim ke papan klip PC',
              size: size,
              onTap: onClipboard,
            ),
          if (showClipboard)
            _RailButton(
              icon: LucideIcons.clipboardPaste,
              tooltip: 'Ambil dari papan klip PC',
              size: size,
              onTap: onClipboardPull,
            ),
          _RailButton(
            icon: LucideIcons.settings,
            tooltip: 'Pengaturan sesi',
            size: size,
            onTap: onSettings,
          ),
          const _RailDivider(),
          _RailButton(
            icon: LucideIcons.power,
            tooltip: 'Putuskan',
            size: size,
            danger: true,
            onTap: onDisconnect,
          ),
        ],
      ),
    );
  }
}

class _RailDivider extends StatelessWidget {
  const _RailDivider();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 22,
      height: 1,
      margin: const EdgeInsets.symmetric(vertical: 4),
      color: Colors.white.withValues(alpha: 0.12),
    );
  }
}

class _RailButton extends StatelessWidget {
  const _RailButton({
    required this.icon,
    required this.tooltip,
    required this.size,
    required this.onTap,
    this.active = false,
    this.pending = false,
    this.danger = false,
  });

  final IconData icon;
  final String tooltip;
  final double size;
  final VoidCallback onTap;
  final bool active;
  final bool pending;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final color = danger
        ? AppColors.danger
        : pending
        ? AppColors.warning
        : active
        ? AppColors.accentDark
        : Colors.white70;
    return Tooltip(
      message: pending ? '$tooltip - belum aktif' : tooltip,
      preferBelow: false,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(R.sm),
        child: SizedBox(
          width: size,
          height: size,
          child: Stack(
            alignment: Alignment.center,
            children: [
              Icon(icon, size: 19, color: color),
              if (active && !danger)
                Positioned(
                  bottom: 6,
                  child: Container(
                    width: 14,
                    height: 2,
                    decoration: BoxDecoration(
                      color: color,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
              if (pending)
                const Positioned(
                  top: 8,
                  right: 8,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: AppColors.warning,
                      shape: BoxShape.circle,
                    ),
                    child: SizedBox(width: 5, height: 5),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
