import 'package:flutter/material.dart';

import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';

/// Papan ketik sistem (IME) untuk sesi: satu field teks yang memakai
/// keyboard bawaan Android, lalu mengirim isi ke host sebagai 0x06 TEXT.
/// Tombol Enter & Backspace diteruskan sebagai keycode Windows.
class SystemKeyboard extends StatefulWidget {
  const SystemKeyboard({
    super.key,
    required this.onText,
    required this.onKey,
    required this.onDismiss,
  });

  final ValueChanged<String> onText;
  final void Function(int vk, bool down) onKey;
  final VoidCallback onDismiss;

  @override
  State<SystemKeyboard> createState() => _SystemKeyboardState();
}

class _SystemKeyboardState extends State<SystemKeyboard> {
  final _ctrl = TextEditingController();
  final _focus = FocusNode();
  // Jejak teks terakhir yang sudah dikirim, supaya onChanged hanya mengirim
  // selisih (delta) sehingga host tidak mengetik ulang seluruh isi tiap
  // ketikan (yang akan menjadi "a" → "ab" → "aab").
  String _sent = '';

  @override
  void initState() {
    super.initState();
    // Fokus otomatis supaya keyboard sistem langsung muncul.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _focus.requestFocus();
    });
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _changed(String value) {
    if (value.length > _sent.length && value.startsWith(_sent)) {
      // Karakter baru ditambahkan di akhir — kirim sisa teksnya saja.
      widget.onText(value.substring(_sent.length));
    } else if (value.length < _sent.length) {
      // Ada penghapusan — tekan Backspace pada host sebanyak selisih.
      // (Anggapan sederhana: penghapusan dari akhir; umum pada IME.)
      for (var i = 0; i < _sent.length - value.length; i++) {
        widget.onKey(0x08, true);
        widget.onKey(0x08, false);
      }
    }
    _sent = value;
  }

  void _submit() {
    // Sisa teks yang belum terkirim (mis. diketik lalu langsung Enter).
    if (_ctrl.text.isNotEmpty && _ctrl.text != _sent) {
      widget.onText(_ctrl.text);
    }
    // Enter diteruskan ke host.
    widget.onKey(0x0D, true);
    widget.onKey(0x0D, false);
    _ctrl.clear();
    _sent = '';
    _focus.requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Material(
      color: c.bg,
      elevation: 16,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _ctrl,
                      focusNode: _focus,
                      autofocus: true,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _submit(),
                      onChanged: _changed,
                      style: const TextStyle(fontSize: 16),
                      decoration: InputDecoration(
                        hintText: 'Ketik ke PC…',
                        hintStyle: TextStyle(fontSize: 14, color: c.textLow),
                        isDense: true,
                        border: InputBorder.none,
                      ),
                    ),
                  ),
                  const SizedBox(width: 6),
                  IconButton(
                    tooltip: 'Hapus satu karakter',
                    icon: const Icon(LucideIcons.delete, size: 18),
                    color: c.textMid,
                    onPressed: () {
                      widget.onKey(0x08, true); // Backspace
                      widget.onKey(0x08, false);
                    },
                  ),
                  IconButton(
                    tooltip: 'Kirim',
                    icon: const Icon(LucideIcons.arrowUp, size: 18),
                    color: c.accent,
                    onPressed: _submit,
                  ),
                  IconButton(
                    tooltip: 'Tutup keyboard',
                    icon: const Icon(LucideIcons.x, size: 18),
                    color: c.textLow,
                    onPressed: widget.onDismiss,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
