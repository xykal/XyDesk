import 'package:flutter/material.dart';

import '../../core/haptics.dart';
import '../../widgets/hud_glyphs.dart';

class GamingControls extends StatelessWidget {
  const GamingControls({super.key, required this.compact, required this.onKey});

  final bool compact;

  /// Kirim tombol keyboard (vk, down) ke host. Pemetaan default game PC:
  /// D-pad = WASD; A=Space (lompat), B=Shift (lari), X=E (aksi), Y=Q.
  final void Function(int vk, bool down) onKey;

  @override
  Widget build(BuildContext context) {
    final bottom = compact ? 76.0 : 88.0;
    return Positioned.fill(
      child: Stack(
        children: [
          Positioned(
            left: 24,
            bottom: bottom,
            child: _DpadControl(size: compact ? 82 : 98, onKey: onKey),
          ),
          Positioned(
            right: 30,
            bottom: bottom + 4,
            child: SizedBox(
              width: compact ? 126 : 150,
              height: compact ? 92 : 108,
              child: Stack(
                children: [
                  Positioned(
                    right: 0,
                    top: 22,
                    child: _ActionButton(
                      label: 'B',
                      compact: compact,
                      vk: 0xA0, // Shift kiri
                      onKey: onKey,
                    ),
                  ),
                  Positioned(
                    right: compact ? 52 : 62,
                    top: 0,
                    child: _ActionButton(
                      label: 'Y',
                      compact: compact,
                      vk: 0x51, // Q
                      onKey: onKey,
                    ),
                  ),
                  Positioned(
                    right: compact ? 52 : 62,
                    bottom: 0,
                    child: _ActionButton(
                      label: 'A',
                      compact: compact,
                      vk: 0x20, // Space
                      onKey: onKey,
                    ),
                  ),
                  Positioned(
                    left: 0,
                    top: 22,
                    child: _ActionButton(
                      label: 'X',
                      compact: compact,
                      vk: 0x45, // E
                      onKey: onKey,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// D-pad virtual: sentuhan pada kuadran diterjemahkan ke WASD hold/release.
class _DpadControl extends StatefulWidget {
  const _DpadControl({required this.size, required this.onKey});

  final double size;
  final void Function(int vk, bool down) onKey;

  @override
  State<_DpadControl> createState() => _DpadControlState();
}

class _DpadControlState extends State<_DpadControl> {
  static const _w = 0x57, _a = 0x41, _s = 0x53, _d = 0x44;
  final Set<int> _held = {};

  void _update(Offset local) {
    final c = widget.size / 2;
    final dx = local.dx - c;
    final dy = local.dy - c;
    final dead = widget.size * 0.12;
    final next = <int>{};
    if (dy < -dead) next.add(_w);
    if (dy > dead) next.add(_s);
    if (dx < -dead) next.add(_a);
    if (dx > dead) next.add(_d);

    final newlyPressed = next.difference(_held);
    if (newlyPressed.isNotEmpty) {
      AppHaptics.tap();
    }

    for (final vk in _held.difference(next)) {
      widget.onKey(vk, false);
    }
    for (final vk in newlyPressed) {
      widget.onKey(vk, true);
    }
    _held
      ..clear()
      ..addAll(next);
    setState(() {});
  }

  void _releaseAll() {
    for (final vk in _held) {
      widget.onKey(vk, false);
    }
    _held.clear();
    setState(() {});
  }

  @override
  void dispose() {
    _releaseAll();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onPanDown: (d) => _update(d.localPosition),
      onPanUpdate: (d) => _update(d.localPosition),
      onPanEnd: (_) => _releaseAll(),
      onPanCancel: _releaseAll,
      child: _TouchControl(
        size: widget.size,
        glyph: HudGlyph.dpad,
        label: 'Gerak',
        active: _held.isNotEmpty,
      ),
    );
  }
}

class _TouchControl extends StatelessWidget {
  const _TouchControl({
    required this.size,
    required this.glyph,
    required this.label,
    this.active = false,
  });

  final double size;
  final HudGlyph glyph;
  final String label;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        gradient: active
            ? const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0x667C3AED), Color(0x445B21B6)],
              )
            : const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0x44181926), Color(0x220E1018)],
              ),
        shape: BoxShape.circle,
        border: Border.all(
          color: active
              ? const Color(0xFFA78BFA).withValues(alpha: 0.6)
              : Colors.white.withValues(alpha: 0.28),
          width: active ? 1.4 : 1.0,
        ),
        boxShadow: active
            ? [
                BoxShadow(
                  color: const Color(0xFF7C3AED).withValues(alpha: 0.35),
                  blurRadius: 10,
                ),
              ]
            : [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.2),
                  blurRadius: 6,
                ),
              ],
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          HudIcon(
            glyph,
            size: size * 0.54,
            color: active ? Colors.white : Colors.white70,
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w600,
              color: active ? const Color(0xFFC4B5FD) : Colors.white54,
            ),
          ),
        ],
      ),
    );
  }
}

class _ActionButton extends StatefulWidget {
  const _ActionButton({
    required this.label,
    required this.compact,
    required this.vk,
    required this.onKey,
  });

  final String label;
  final bool compact;
  final int vk;
  final void Function(int vk, bool down) onKey;

  @override
  State<_ActionButton> createState() => _ActionButtonState();
}

class _ActionButtonState extends State<_ActionButton> {
  bool _down = false;

  void _set(bool down) {
    if (_down == down) return;
    _down = down;
    widget.onKey(widget.vk, down);
    if (down) AppHaptics.tap();
    setState(() {});
  }

  @override
  void dispose() {
    if (_down) widget.onKey(widget.vk, false);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final size = widget.compact ? 44.0 : 52.0;
    return Listener(
      onPointerDown: (_) => _set(true),
      onPointerUp: (_) => _set(false),
      onPointerCancel: (_) => _set(false),
      child: AnimatedScale(
        scale: _down ? 0.92 : 1.0,
        duration: const Duration(milliseconds: 60),
        curve: Curves.easeOutCubic,
        child: Container(
          width: size,
          height: size,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            gradient: _down
                ? const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      Color(0xFF8B5CF6),
                      Color(0xFF7C3AED),
                      Color(0xFF5B21B6),
                    ],
                  )
                : const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [Color(0x52181926), Color(0x330E1018)],
                  ),
            shape: BoxShape.circle,
            border: Border.all(
              color: _down
                  ? const Color(0xFFC4B5FD)
                  : Colors.white.withValues(alpha: 0.28),
              width: _down ? 1.4 : 1.0,
            ),
            boxShadow: _down
                ? [
                    BoxShadow(
                      color: const Color(0xFF7C3AED).withValues(alpha: 0.5),
                      blurRadius: 12,
                      spreadRadius: 1,
                    ),
                  ]
                : [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.25),
                      blurRadius: 6,
                    ),
                  ],
          ),
          child: Text(
            widget.label,
            style: TextStyle(
              fontSize: widget.compact ? 13 : 14.5,
              fontWeight: FontWeight.w800,
              color: _down
                  ? Colors.white
                  : Colors.white.withValues(alpha: 0.85),
            ),
          ),
        ),
      ),
    );
  }
}
