// Sumber teks: lib/l10n/arb/app_<kode>.arb (template: app_en.arb).
// `flutter gen-l10n` menghasilkan lib/l10n/gen/ saat `flutter pub get`;
// `context.tr('kunci')` tetap API-nya lewat peta tr_keys.g.dart.
// Bahasa baru: salin app_en.arb -> app_<kode>.arb, terjemahkan, tambah
// AppLang di bawah, jalankan `python3 tool/l10n/export_arb.py`.

import 'package:flutter/widgets.dart';

import 'gen/app_localizations.dart';
import 'tr_keys.g.dart';

export 'gen/app_localizations.dart' show AppLocalizations;

/// Daftar bahasa yang tersedia.
class AppLang {
  const AppLang(this.code, this.name, this.nativeName, {this.rtl = false});

  final String code;
  final String name;
  final String nativeName;
  final bool rtl;

  static const id = AppLang('id', 'Indonesian', 'Indonesia');
  static const en = AppLang('en', 'English', 'English');
  static const zh = AppLang('zh', 'Chinese', '中文');
  static const es = AppLang('es', 'Spanish', 'Español');
  static const pt = AppLang('pt', 'Portuguese', 'Português');
  static const ar = AppLang('ar', 'Arabic', 'العربية', rtl: true);
  static const ja = AppLang('ja', 'Japanese', '日本語');
  static const ko = AppLang('ko', 'Korean', '한국어');
  static const fr = AppLang('fr', 'French', 'Français');
  static const de = AppLang('de', 'German', 'Deutsch');
  static const ru = AppLang('ru', 'Russian', 'Русский');
  static const hi = AppLang('hi', 'Hindi', 'हिन्दी');

  static const all = <AppLang>[id, en, zh, es, pt, ar, ja, ko, fr, de, ru, hi];

  static AppLang byCode(String c) =>
      all.firstWhere((l) => l.code == c, orElse: () => en);
}

/// Akses teks terlokalisasi. Bahasa tanpa ARB jatuh ke Inggris.
class L {
  L(this.lang) : _loc = _lookup(lang.code);

  final AppLang lang;
  final AppLocalizations _loc;

  static final Map<String, AppLocalizations> _cache = {};

  static AppLocalizations _lookup(String code) {
    final supported =
        AppLocalizations.supportedLocales.any((l) => l.languageCode == code);
    final key = supported ? code : 'en';
    return _cache[key] ??= lookupAppLocalizations(Locale(key));
  }

  String t(String key) => trKeys[key]?.call(_loc) ?? key;

  static L of(BuildContext context) =>
      L(Localizations.of<L>(context, L)?.lang ?? AppLang.en);
}

/// Delegate agar `L` bisa diambil lewat `Localizations`.
class LDelegate extends LocalizationsDelegate<L> {
  const LDelegate(this.lang);

  final AppLang lang;

  @override
  bool isSupported(Locale locale) =>
      AppLang.all.any((l) => l.code == locale.languageCode);

  @override
  Future<L> load(Locale locale) async => L(lang);

  @override
  bool shouldReload(LDelegate old) => old.lang.code != lang.code;
}

extension LX on BuildContext {
  /// Ringkas: `context.tr('nav_home')`
  String tr(String key) => L.of(this).t(key);
}
