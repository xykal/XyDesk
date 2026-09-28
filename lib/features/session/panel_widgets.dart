import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';

class PreferenceNotice extends StatelessWidget {
  const PreferenceNotice({super.key});

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Text(
      'Tidak ada paywall selama beta. Nantinya, aktivasi dapat mengikuti '
      'capability negotiation setelah host, track Opus, dan virtual microphone '
      'tersedia.',
      style: TextStyle(fontSize: 11, height: 1.5, color: c.textLow),
    );
  }
}

class InactiveLevelMeter extends StatelessWidget {
  const InactiveLevelMeter({super.key});

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Level mikrofon',
                  style: TextStyle(fontSize: 12.5, color: c.textMid),
                ),
              ),
              Text(
                'Tidak aktif',
                style: TextStyle(fontSize: 11, color: c.textLow),
              ),
            ],
          ),
          const SizedBox(height: 9),
          Row(
            children: [
              for (var index = 0; index < 14; index++) ...[
                Expanded(
                  child: Container(
                    height: 5,
                    decoration: BoxDecoration(
                      color: c.textLow.withValues(alpha: 0.18),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                if (index != 13) const SizedBox(width: 3),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class SectionTitle extends StatelessWidget {
  const SectionTitle({super.key, required this.title, this.subtitle});

  final String title;
  final String? subtitle;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12, top: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: TextStyle(
              fontSize: 14.5,
              fontWeight: FontWeight.w700,
              color: c.textHi,
              letterSpacing: -0.2,
            ),
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 4),
            Text(
              subtitle!,
              style: TextStyle(fontSize: 11.5, height: 1.5, color: c.textLow),
            ),
          ],
        ],
      ),
    );
  }
}

class PanelCard extends StatelessWidget {
  const PanelCard({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      decoration: BoxDecoration(
        color: c.input.withValues(alpha: 0.72),
        borderRadius: BorderRadius.circular(R.lg),
        border: Border.all(color: c.textLow.withValues(alpha: 0.16)),
      ),
      child: child,
    );
  }
}

class CardGap extends StatelessWidget {
  const CardGap({super.key});

  @override
  Widget build(BuildContext context) {
    return const SizedBox(height: 1);
  }
}

class CardGapLarge extends StatelessWidget {
  const CardGapLarge({super.key});

  @override
  Widget build(BuildContext context) {
    // No Divider per design rule seamless — use Container 1px
    return SizedBox(
      height: 8,
      child: Center(
        child: Container(
          height: 1,
          color: context.c.textLow.withValues(alpha: 0.08),
        ),
      ),
    );
  }
}

class InfoRow extends StatelessWidget {
  const InfoRow({
    super.key,
    required this.icon,
    required this.title,
    required this.value,
  });

  final IconData icon;
  final String title;
  final String value;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 56),
      child: Row(
        children: [
          Icon(icon, size: 18, color: c.textLow),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              title,
              style: TextStyle(fontSize: 13, color: c.textMid),
            ),
          ),
          const SizedBox(width: 12),
          Flexible(
            child: Text(
              value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.end,
              style: TextStyle(
                fontSize: 12.5,
                fontWeight: FontWeight.w600,
                color: c.textHi,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class DeviceRow extends StatelessWidget {
  const DeviceRow({
    super.key,
    required this.icon,
    required this.title,
    required this.value,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String value;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return InkWell(
      onTap: onTap,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 52),
        child: Row(
          children: [
            Icon(icon, size: 17, color: c.textLow),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                title,
                style: TextStyle(fontSize: 12.5, color: c.textMid),
              ),
            ),
            const SizedBox(width: 8),
            Flexible(
              child: Text(
                value,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.end,
                style: TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w500,
                  color: c.textHi,
                ),
              ),
            ),
            const SizedBox(width: 4),
            Icon(LucideIcons.chevronRight, size: 15, color: c.textLow),
          ],
        ),
      ),
    );
  }
}

class ToggleRow extends StatelessWidget {
  const ToggleRow({
    super.key,
    required this.icon,
    required this.title,
    required this.value,
    required this.onChanged,
    this.subtitle,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 54),
      child: Row(
        children: [
          Icon(icon, size: 17, color: value ? c.accent : c.textLow),
          const SizedBox(width: 10),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 9),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(fontSize: 12.5, color: c.textHi),
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitle!,
                      style: TextStyle(
                        fontSize: 10.5,
                        height: 1.35,
                        color: c.textLow,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          Transform.scale(
            scale: 0.78,
            child: Switch(value: value, onChanged: onChanged),
          ),
        ],
      ),
    );
  }
}

class SliderRow extends StatelessWidget {
  const SliderRow({
    super.key,
    required this.label,
    required this.valueLabel,
    required this.value,
    required this.onChanged,
  });

  final String label;
  final String valueLabel;
  final double value;
  final ValueChanged<double> onChanged;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 9),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  label,
                  style: TextStyle(fontSize: 12.5, color: c.textMid),
                ),
              ),
              Text(
                valueLabel,
                style: TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w600,
                  color: c.textHi,
                ),
              ),
            ],
          ),
          SizedBox(
            height: 30,
            child: Slider(value: value.clamp(0.0, 1.0), onChanged: onChanged),
          ),
        ],
      ),
    );
  }
}

class StatusCard extends StatelessWidget {
  const StatusCard({
    super.key,
    required this.icon,
    required this.title,
    required this.body,
  });

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: c.raised,
        borderRadius: BorderRadius.circular(R.md),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: c.textLow),
          const SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w600,
                    color: c.textHi,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  body,
                  style: TextStyle(
                    fontSize: 11,
                    height: 1.45,
                    color: c.textLow,
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

class SegmentEntry<T> {
  const SegmentEntry({required this.value, required this.label, this.icon});

  final T value;
  final String label;
  final IconData? icon;
}

class Segmented<T> extends StatelessWidget {
  const Segmented({
    super.key,
    required this.value,
    required this.entries,
    required this.onChanged,
    this.height = 42,
  });

  final T value;
  final List<SegmentEntry<T>> entries;
  final ValueChanged<T> onChanged;
  final double height;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Container(
      height: height,
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: c.input,
        borderRadius: BorderRadius.circular(R.md),
        border: Border.all(color: c.textLow.withValues(alpha: 0.16)),
      ),
      child: Row(
        children: [
          for (final entry in entries)
            Expanded(
              child: InkWell(
                onTap: () => onChanged(entry.value),
                borderRadius: BorderRadius.circular(R.sm),
                child: AnimatedContainer(
                  duration: D.fast,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: value == entry.value ? c.raised : Colors.transparent,
                    borderRadius: BorderRadius.circular(R.sm),
                    boxShadow: value == entry.value
                        ? [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.18),
                              blurRadius: 5,
                            ),
                          ]
                        : null,
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      if (entry.icon != null) ...[
                        Icon(
                          entry.icon,
                          size: 15,
                          color: value == entry.value ? c.accent : c.textLow,
                        ),
                        const SizedBox(width: 6),
                      ],
                      Flexible(
                        child: Text(
                          entry.label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 11.5,
                            fontWeight: value == entry.value
                                ? FontWeight.w600
                                : FontWeight.w500,
                            color: value == entry.value ? c.textHi : c.textMid,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
