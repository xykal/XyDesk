import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';
import 'panel_widgets.dart';
import 'session_panels.dart';

class ControlsPanel extends StatelessWidget {
  const ControlsPanel({
    super.key,
    required this.state,
    required this.onChanged,
  });

  final SessionSettings state;
  final ValueChanged<SessionSettings> onChanged;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final gaming = state.experience == SessionExperience.gaming;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SectionTitle(
          title: gaming ? 'Gaming Controls' : 'Desktop Controls',
          subtitle: gaming
              ? 'Touch HUD, joystick, keypad, keyboard — spacious & complete'
              : 'Pointer, click, keyboard, clipboard — all adjustable',
        ),
        // Pemilihan keyboard
        const SectionTitle(
          title: 'Keyboard Selection',
          subtitle: 'Pick input source — XyDesk full or system IME',
        ),
        PanelCard(
          child: Column(
            children: [
              Segmented<KeyboardSource>(
                value: state.keyboardSource,
                onChanged: (value) =>
                    onChanged(state.copyWith(keyboardSource: value)),
                entries: const [
                  SegmentEntry<KeyboardSource>(
                    value: KeyboardSource.xydesk,
                    label: 'XyDesk Full',
                    icon: LucideIcons.keyboard,
                  ),
                  SegmentEntry<KeyboardSource>(
                    value: KeyboardSource.system,
                    label: 'System IME',
                    icon: LucideIcons.smartphone,
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Padding(
                padding: const EdgeInsets.only(left: 4, bottom: 8),
                child: Text(
                  state.keyboardSource == KeyboardSource.system
                      ? 'System keyboard (IME) — for forms, search, fast typing. Supports physical keyboard via Bluetooth/USB.'
                      : 'XyDesk virtual keyboard (F1-F12, modifiers, split/full/compact) — for games & precise control. Supports physical keyboard mapping.',
                  style: TextStyle(
                    fontSize: 11.5,
                    color: c.textLow,
                    height: 1.5,
                  ),
                ),
              ),
              const CardGapLarge(),
              DeviceRow(
                icon: LucideIcons.keyboard,
                title: 'Keyboard layout',
                value: 'Split / Full / Compact',
                onTap: () {
                  // Layout handled in VirtualKeyboard — show info
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text(
                        'Keyboard layout: Split (two thumbs), Full, Compact — change in virtual keyboard header',
                      ),
                    ),
                  );
                },
              ),
              const CardGapLarge(),
              DeviceRow(
                icon: LucideIcons.type,
                title: 'Physical keyboard',
                value: 'Auto-detected',
                onTap: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text(
                        'Physical keyboard: QWERTY auto-detected, Bluetooth/USB supported',
                      ),
                    ),
                  );
                },
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),

        // Joystick, gamepad, keypad
        const SectionTitle(
          title: 'Joystick & Gamepad & Keypad',
          subtitle: 'Complete mapping — not yet perfect, now more spacious',
        ),
        PanelCard(
          child: Column(
            children: [
              if (gaming) ...[
                ToggleRow(
                  icon: LucideIcons.gamepad2,
                  title: 'Show touch controls',
                  subtitle: 'D-pad, joystick, action buttons — spacious',
                  value: state.showGamingControls,
                  onChanged: (value) =>
                      onChanged(state.copyWith(showGamingControls: value)),
                ),
                const CardGapLarge(),
              ],
              ToggleRow(
                icon: LucideIcons.joystick,
                title: 'Joystick enabled',
                subtitle: 'Left stick for movement, right for camera',
                value: true,
                onChanged: (_) {},
              ),
              const CardGapLarge(),
              ToggleRow(
                icon: LucideIcons.gamepad,
                title: 'Gamepad support',
                subtitle: 'Bluetooth/USB gamepad → WASD + mouse',
                value: true,
                onChanged: (_) {},
              ),
              const CardGapLarge(),
              ToggleRow(
                icon: LucideIcons.keyboard,
                title: 'Keypad / Numpad',
                subtitle: 'Numpad 0-9, arrows, for games & desktop',
                value: true,
                onChanged: (_) {},
              ),
              const CardGapLarge(),
              DeviceRow(
                icon: LucideIcons.settings2,
                title: 'Control mapping',
                value: 'Gaming & Desktop profiles',
                onTap: () {
                  // Navigate to control mapping page
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => const ControlMappingPageWrapper(),
                    ),
                  );
                },
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),

        const SectionTitle(
          title: 'Pointer & Haptics',
          subtitle: 'Sensitivity, tap-to-click, scroll, haptics',
        ),
        PanelCard(
          child: Column(
            children: [
              ToggleRow(
                icon: LucideIcons.vibrate,
                title: 'Haptic feedback',
                value: state.haptics,
                onChanged: (value) => onChanged(state.copyWith(haptics: value)),
              ),
              const CardGapLarge(),
              SliderRow(
                label: gaming ? 'Aim sensitivity' : 'Pointer speed',
                valueLabel:
                    '${(0.5 + state.pointerSensitivity * 2.5).toStringAsFixed(1)}×',
                value: state.pointerSensitivity,
                onChanged: (value) =>
                    onChanged(state.copyWith(pointerSensitivity: value)),
              ),
              if (!gaming) ...[
                const CardGapLarge(),
                ToggleRow(
                  icon: LucideIcons.mouse,
                  title: 'Tap to click',
                  value: state.tapToClick,
                  onChanged: (value) =>
                      onChanged(state.copyWith(tapToClick: value)),
                ),
                const CardGapLarge(),
                ToggleRow(
                  icon: LucideIcons.activity,
                  title: 'Reverse scroll',
                  value: state.reverseScroll,
                  onChanged: (value) =>
                      onChanged(state.copyWith(reverseScroll: value)),
                ),
                const CardGapLarge(),
                ToggleRow(
                  icon: LucideIcons.crosshair,
                  title: 'Relative pointer',
                  subtitle: 'For 3D & FPS games',
                  value: state.pointerLock,
                  onChanged: (value) =>
                      onChanged(state.copyWith(pointerLock: value)),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 20),
        const StatusCard(
          icon: LucideIcons.info,
          title: 'Controls ready',
          body:
              'All controls (keyboard, joystick, gamepad, keypad, mouse) can be remapped in Control Mapping page.',
        ),
      ],
    );
  }
}
