import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';
import 'panel_widgets.dart';
import 'session_panels.dart';
import '../../core/l10n_bridge.dart';

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
          title: gaming ? context.tr('ct_gaming') : context.tr('ct_desktop'),
          subtitle: gaming
              ? context.tr('ct_gaming_sub')
              : context.tr('ct_desktop_sub'),
        ),
        // Pemilihan keyboard
        SectionTitle(
          title: context.tr('ct_kb_select'),
          subtitle: context.tr('ct_kb_select_sub'),
        ),
        PanelCard(
          child: Column(
            children: [
              Segmented<KeyboardSource>(
                value: state.keyboardSource,
                onChanged: (value) =>
                    onChanged(state.copyWith(keyboardSource: value)),
                entries: [
                  SegmentEntry<KeyboardSource>(
                    value: KeyboardSource.xydesk,
                    label: context.tr('ct_kb_full'),
                    icon: LucideIcons.keyboard,
                  ),
                  SegmentEntry<KeyboardSource>(
                    value: KeyboardSource.system,
                    label: context.tr('ct_kb_ime'),
                    icon: LucideIcons.smartphone,
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Padding(
                padding: const EdgeInsets.only(left: 4, bottom: 8),
                child: Text(
                  state.keyboardSource == KeyboardSource.system
                      ? context.tr('ct_kb_ime_desc')
                      : context.tr('ct_kb_full_desc'),
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
                title: context.tr('ct_kb_layout'),
                value: context.tr('ct_kb_layout_sub'),
                onTap: () {
                  // Layout handled in VirtualKeyboard — show info
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        context.tr('ct_kb_layout_desc'),
                      ),
                    ),
                  );
                },
              ),
              const CardGapLarge(),
              DeviceRow(
                icon: LucideIcons.type,
                title: context.tr('ct_phys_kb'),
                value: context.tr('ct_auto_detected'),
                onTap: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        context.tr('ct_phys_kb_desc'),
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
        SectionTitle(
          title: context.tr('ct_joy_title'),
          subtitle: context.tr('ct_joy_sub'),
        ),
        PanelCard(
          child: Column(
            children: [
              if (gaming) ...[
                ToggleRow(
                  icon: LucideIcons.gamepad2,
                  title: context.tr('ct_show_touch'),
                  subtitle: context.tr('ct_show_touch_sub'),
                  value: state.showGamingControls,
                  onChanged: (value) =>
                      onChanged(state.copyWith(showGamingControls: value)),
                ),
                const CardGapLarge(),
              ],
              ToggleRow(
                icon: LucideIcons.joystick,
                title: context.tr('ct_joystick'),
                subtitle: context.tr('ct_joystick_sub'),
                value: true,
                onChanged: (_) {},
              ),
              const CardGapLarge(),
              ToggleRow(
                icon: LucideIcons.gamepad,
                title: context.tr('ct_gamepad'),
                subtitle: context.tr('ct_gamepad_sub'),
                value: true,
                onChanged: (_) {},
              ),
              const CardGapLarge(),
              ToggleRow(
                icon: LucideIcons.keyboard,
                title: context.tr('ct_keypad'),
                subtitle: context.tr('ct_keypad_sub'),
                value: true,
                onChanged: (_) {},
              ),
              const CardGapLarge(),
              DeviceRow(
                icon: LucideIcons.settings2,
                title: context.tr('ct_mapping'),
                value: context.tr('ct_mapping_sub'),
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

        SectionTitle(
          title: context.tr('ct_pointer'),
          subtitle: context.tr('ct_pointer_sub'),
        ),
        PanelCard(
          child: Column(
            children: [
              ToggleRow(
                icon: LucideIcons.vibrate,
                title: context.tr('ct_haptic'),
                value: state.haptics,
                onChanged: (value) => onChanged(state.copyWith(haptics: value)),
              ),
              const CardGapLarge(),
              SliderRow(
                label: gaming ? context.tr('ct_aim') : context.tr('ct_pointer_speed'),
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
                  title: context.tr('ct_tap_click'),
                  value: state.tapToClick,
                  onChanged: (value) =>
                      onChanged(state.copyWith(tapToClick: value)),
                ),
                const CardGapLarge(),
                ToggleRow(
                  icon: LucideIcons.activity,
                  title: context.tr('ct_reverse_scroll'),
                  value: state.reverseScroll,
                  onChanged: (value) =>
                      onChanged(state.copyWith(reverseScroll: value)),
                ),
                const CardGapLarge(),
                ToggleRow(
                  icon: LucideIcons.crosshair,
                  title: context.tr('ct_relative'),
                  subtitle: context.tr('ct_relative_sub'),
                  value: state.pointerLock,
                  onChanged: (value) =>
                      onChanged(state.copyWith(pointerLock: value)),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 20),
        StatusCard(
          icon: LucideIcons.info,
          title: context.tr('ct_ready'),
          body:
              context.tr('ct_ready_body'),
        ),
      ],
    );
  }
}
