import 'package:flutter/material.dart';

import '../../core/brand.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/app_version.dart';
import '../../core/cloudinary_upload.dart';
import '../../core/devlog.dart';
import '../../core/l10n_bridge.dart';
import '../../core/store.dart';
import '../../core/tokens.dart';
import '../../widgets/brand.dart';
import '../../widgets/profile_avatar.dart';
import '../../widgets/seamless.dart';
import '../auth/auth_service.dart';
import '../auth/legal_page.dart';
import '../notifications/app_update_details.dart';
import '../notifications/notification_preferences_page.dart';
import '../notifications/update_page.dart';
import '../session/media_capabilities.dart';
import '../session/control_mapping_page.dart';
import 'permissions_page.dart';
import 'subscription_page.dart';
import '../../core/display_control.dart';
import '../../core/haptics.dart';

/// Ringkasan akun. Pengaturan tidak lagi ditumpuk di halaman profil; setiap
/// kategori membuka layar fokusnya sendiri agar lebih mudah dipindai.
class AccountPage extends ConsumerWidget {
  const AccountPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final c = context.c;
    final user = ref.watch(authProvider);

    return ListView(
      padding: EdgeInsets.only(
        top: MediaQuery.paddingOf(context).top + 64,
        bottom: 112,
      ),
      children: [
        InkWell(
          onTap: user.isGuest ? null : () => _editProfile(context, ref, user),
          borderRadius: BorderRadius.circular(R.lg),
          child: _ProfileHero(
            initial: user.initial,
            name: user.isGuest
                ? context.tr('account_guest')
                : (user.name ?? context.tr('account_user')),
            email: user.email ?? context.tr('account_local_data'),
            badge: user.isGuest
                ? context.tr('account_badge_guest')
                : context.tr('account_badge_active'),
            pictureUrl: user.picture,
          ),
        ),
        SectionLabel(context.tr('settings_preferences')),
        _CategoryRow(
          title: context.tr('settings_appearance_lang'),
          subtitle: context.tr('settings_appearance_sub'),
          icon: LucideIcons.palette,
          onTap: () => _open(context, const AppearanceSettingsPage()),
        ),
        _CategoryRow(
          title: context.tr('settings_behavior_a11y'),
          subtitle: context.tr('settings_behavior_sub'),
          icon: LucideIcons.slidersHorizontal,
          onTap: () => _open(context, const BehaviorSettingsPage()),
        ),
        _CategoryRow(
          title: context.tr('settings_streaming'),
          subtitle: context.tr('settings_streaming_sub'),
          icon: LucideIcons.monitorCog,
          onTap: () => _open(context, const StreamingSettingsPage()),
        ),
        _CategoryRow(
          title: context.tr('settings_notifications'),
          subtitle: context.tr('settings_notifications_sub'),
          icon: LucideIcons.bell,
          onTap: () => _open(context, const NotificationPreferencesPage()),
        ),
        _CategoryRow(
          title: context.tr('settings_system_privacy'),
          subtitle: context.tr('settings_system_sub'),
          icon: LucideIcons.shieldCheck,
          onTap: () => _open(context, const SystemSettingsPage()),
        ),
        SectionLabel(context.tr('settings_account_info')),
        _CategoryRow(
          title: context.tr('settings_subscription'),
          subtitle: context.tr('settings_subscription_sub'),
          icon: LucideIcons.crown,
          value: 'Free',
          onTap: () => _open(context, const SubscriptionPage()),
        ),
        _CategoryRow(
          title: context.tr('settings_control_mapping'),
          subtitle: context.tr('settings_control_mapping_sub'),
          icon: LucideIcons.gamepad2,
          onTap: () => _open(context, const ControlMappingPage()),
        ),
        _CategoryRow(
          title: context.tr('settings_legal_about'),
          subtitle: context.tr('settings_legal_about_sub'),
          icon: LucideIcons.info,
          onTap: () => _open(context, const LegalSettingsPage()),
        ),
        const SizedBox(height: Gap.lg),
        ListRow(
          title: context.tr('sign_out'),
          icon: LucideIcons.logOut,
          danger: true,
          onTap: () async {
            await ref.read(googleAuthServiceProvider).signOut();
            await ref.read(authProvider.notifier).signOut();
          },
        ),
        if (!user.isGuest && user.token != null)
          ListRow(
            title: context.tr('account_delete'),
            subtitle: context.tr('account_delete_sub'),
            icon: LucideIcons.trash2,
            danger: true,
            onTap: () => _deleteAccount(context, ref),
          ),
        const SizedBox(height: Gap.sm),
        Center(
          child: Text(
            AppVersion.labeled,
            style: TextStyle(fontSize: 10.5, color: c.textLow),
          ),
        ),
      ],
    );
  }
}

class AppearanceSettingsPage extends ConsumerWidget {
  const AppearanceSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lang = ref.watch(langProvider);
    return _SettingsScaffold(
      title: context.tr('settings_appearance_lang'),
      description: context.tr('appearance_desc'),
      children: [
        SectionLabel(context.tr('settings_language_section'), top: 0),
        ListRow(
          title: context.tr('settings_language'),
          subtitle: context.tr('settings_language_sub'),
          icon: LucideIcons.languages,
          value: lang.nativeName,
          trailing: _chevron(context),
          onTap: () => _showLanguagePicker(context, ref),
        ),
      ],
    );
  }
}

/// Menjelaskan refresh rate memakai angka nyata dari panel, bukan janji.
String _refreshRateSubtitle() {
  final now = '${DisplayControl.current.round()} Hz';
  if (!DisplayControl.canSwitch) return 'Layar berjalan di $now';
  final list = DisplayControl.supported
      .map((e) => '${e.round()}')
      .join(' dan ');
  return 'Sekarang $now · layar HP ini bisa $list Hz';
}

class BehaviorSettingsPage extends ConsumerWidget {
  const BehaviorSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(settingsProvider);
    final notifier = ref.read(settingsProvider.notifier);
    return _SettingsScaffold(
      title: context.tr('settings_behavior_a11y'),
      description: context.tr('behavior_desc'),
      children: [
        _SwitchRow(
          title: context.tr('behavior_vibration'),
          subtitle: context.tr('behavior_vibration_sub'),
          icon: LucideIcons.vibrate,
          value: s.haptics,
          onChanged: notifier.setHaptics,
        ),
        _SwitchRow(
          title: context.tr('behavior_high_refresh'),
          subtitle: _refreshRateSubtitle(),
          icon: LucideIcons.zap,
          value: s.highRefresh,
          onChanged: notifier.setHighRefresh,
          // Perangkat 60 Hz tidak punya apa pun untuk dipilih. Menampilkan
          // sakelar aktif di situ adalah kebohongan kecil yang gratis
          // dihindari.
          unavailable: DisplayControl.canSwitch
              ? null
              : 'Layar HP ini cuma ${DisplayControl.current.round()} Hz, '
                    'jadi tidak ada yang bisa diubah',
        ),
        _SwitchRow(
          title: context.tr('behavior_keep_on'),
          subtitle: context.tr('behavior_keep_on_sub'),
          icon: LucideIcons.sun,
          value: s.keepScreenOn,
          onChanged: notifier.setKeepScreenOn,
        ),
        _SwitchRow(
          title: context.tr('behavior_reduce_motion'),
          subtitle: context.tr('behavior_reduce_motion_sub'),
          icon: LucideIcons.accessibility,
          value: s.reduceMotion,
          onChanged: notifier.setReduceMotion,
        ),
        SectionLabel(context.tr('settings_developer')),
        _SwitchRow(
          title: context.tr('behavior_devlog'),
          subtitle: context.tr('behavior_devlog_sub'),
          icon: LucideIcons.bug,
          value: s.showDevLog,
          onChanged: notifier.setShowDevLog,
        ),
      ],
    );
  }
}

class StreamingSettingsPage extends ConsumerWidget {
  const StreamingSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(settingsProvider);
    final notifier = ref.read(settingsProvider.notifier);
    return _SettingsScaffold(
      title: context.tr('settings_streaming'),
      description: context.tr('streaming_desc'),
      children: [
        SectionLabel(context.tr('streaming_video'), top: 0),
        ListRow(
          title: context.tr('streaming_codec'),
          subtitle: context.tr('streaming_codec_sub'),
          icon: LucideIcons.cpu,
          value: _shortCodec(s.codec),
          trailing: _chevron(context),
          onTap: () => _pickCodec(context, ref),
        ),
        ListRow(
          title: context.tr('streaming_resolution'),
          subtitle: context.tr('streaming_resolution_sub'),
          icon: LucideIcons.monitor,
          value: s.resolution,
          trailing: _chevron(context),
          onTap: () => _pickResolution(context, ref),
        ),
        ListRow(
          title: context.tr('streaming_bitrate'),
          subtitle: context.tr('streaming_bitrate_sub'),
          icon: LucideIcons.gauge,
          value: '${s.bitrateMbps} Mbps',
          trailing: _chevron(context),
          onTap: () => _pickBitrate(context, ref),
        ),
        SectionLabel(context.tr('streaming_input_media')),
        _SwitchRow(
          title: context.tr('streaming_fps_mode'),
          subtitle: context.tr('streaming_fps_mode_sub'),
          icon: LucideIcons.crosshair,
          value: s.relativeMouseMode,
          onChanged: notifier.setRelativeMouseMode,
        ),
        _SwitchRow(
          title: context.tr('streaming_pc_audio'),
          subtitle:
              'Preferensi tersimpan • '
              '${SessionMediaCapabilities.currentBuild.pcSystemAudio.summary}',
          icon: LucideIcons.volume2,
          value: s.audioEnabled,
          onChanged: notifier.setAudioEnabled,
        ),
        _SwitchRow(
          title: context.tr('streaming_phone_mic'),
          subtitle:
              'Preferensi tersimpan • '
              '${SessionMediaCapabilities.currentBuild.phoneMicrophone.summary}',
          icon: LucideIcons.mic,
          value: s.micPassthrough,
          onChanged: notifier.setMicPassthrough,
        ),
        _SwitchRow(
          title: context.tr('streaming_clipboard'),
          subtitle: context.tr('streaming_clipboard_sub'),
          icon: LucideIcons.clipboard,
          value: s.clipboardSync,
          onChanged: notifier.setClipboardSync,
          // Protokol host belum punya kanal papan klip sama sekali — bukan
          // "belum diuji", memang belum ada kodenya. Sakelar ini sebelumnya
          // menyala secara bawaan dan tidak pernah mengirim apa pun.
          unavailable: context.tr('streaming_clipboard_unavailable'),
        ),
      ],
    );
  }
}

class SystemSettingsPage extends ConsumerWidget {
  const SystemSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return _SettingsScaffold(
      title: context.tr('settings_system_privacy'),
      description: context.tr('system_desc'),
      children: [
        SectionLabel(context.tr('system_device_access'), top: 0),
        ListRow(
          title: context.tr('settings_permissions'),
          subtitle: context.tr('system_permissions_sub'),
          icon: LucideIcons.shield,
          trailing: _chevron(context),
          onTap: () => _open(context, const PermissionsPage()),
        ),
        SectionLabel(context.tr('system_app')),
        ListRow(
          title: context.tr('system_update_center'),
          subtitle: context.tr('system_update_center_sub'),
          icon: LucideIcons.refreshCw,
          trailing: _chevron(context),
          onTap: () => _open(
            context,
            UpdatePage(details: AppUpdateDetails.updateCenter()),
          ),
        ),
        ListRow(
          title: context.tr('settings_devlog'),
          subtitle: context.tr('system_devlog_sub'),
          icon: LucideIcons.fileText,
          trailing: _chevron(context),
          onTap: () => DevLog.openPage(context),
        ),
        SectionLabel(context.tr('system_recovery')),
        ListRow(
          title: context.tr('system_reset'),
          subtitle: context.tr('system_reset_sub'),
          icon: LucideIcons.rotateCcw,
          trailing: _chevron(context),
          onTap: () => _confirmReset(context, ref),
        ),
      ],
    );
  }
}

/// Reset selalu lewat konfirmasi: pengaturan streaming yang sudah dicocokkan
/// dengan jaringan pengguna tidak sepele untuk disusun ulang.
Future<void> _confirmReset(BuildContext context, WidgetRef ref) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(
        context.tr('system_reset_title'),
        style: const TextStyle(fontSize: 16),
      ),
      content: Text(
        context.tr('system_reset_body'),
        style: const TextStyle(fontSize: 13, height: 1.5),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(dialogContext, false),
          child: Text(context.tr('cancel')),
        ),
        TextButton(
          onPressed: () => Navigator.pop(dialogContext, true),
          child: Text(context.tr('system_reset_action')),
        ),
      ],
    ),
  );
  if (ok != true) return;
  await ref.read(settingsProvider.notifier).resetToDefaults();
  if (!context.mounted) return;
  ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(context.tr('system_reset_done'))));
}

class LegalSettingsPage extends StatelessWidget {
  const LegalSettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return _SettingsScaffold(
      title: context.tr('legal_title'),
      description: context.tr('legal_desc'),
      children: [
        SectionLabel(context.tr('legal_section'), top: 0),
        ListRow(
          title: context.tr('legal_terms'),
          icon: LucideIcons.scale,
          trailing: _chevron(context),
          onTap: () => LegalPage.open(context, LegalDoc.terms),
        ),
        ListRow(
          title: context.tr('legal_privacy'),
          icon: LucideIcons.lock,
          trailing: _chevron(context),
          onTap: () => LegalPage.open(context, LegalDoc.privacy),
        ),
        ListRow(
          title: context.tr('legal_licenses'),
          icon: LucideIcons.code,
          trailing: _chevron(context),
          onTap: () => LegalPage.open(context, LegalDoc.licenses),
        ),
        SectionLabel(context.tr('system_app')),
        ListRow(
          title: context.tr('settings_about'),
          subtitle: context.tr('about_sub'),
          icon: LucideIcons.info,
          value: AppVersion.short,
          trailing: _chevron(context),
          onTap: () => _open(context, const AboutPage()),
        ),
      ],
    );
  }
}

class AboutPage extends StatelessWidget {
  const AboutPage({super.key});

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Scaffold(
      backgroundColor: c.bg,
      appBar: AppBar(
        title: Text(context.tr('settings_about')),
        leading: _backButton(context),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(
          Gap.screen,
          Gap.xl,
          Gap.screen,
          Gap.h40,
        ),
        children: [
          const Center(child: BrandLockup(size: 76)),
          const SizedBox(height: Gap.md),
          Center(
            child: Text(
              AppVersion.versiFull,
              style: TextStyle(fontSize: 11.5, color: c.textLow),
            ),
          ),
          const SizedBox(height: Gap.xs),
          Center(
            child: Text(
              brandPoweredBy,
              style: TextStyle(fontSize: 11.5, color: c.textLow),
            ),
          ),
          const SizedBox(height: Gap.xl),
          SectionLabel(context.tr('about_diagnostics')),
          ListRow(
            title: context.tr('settings_devlog'),
            icon: LucideIcons.bug,
            trailing: _chevron(context),
            onTap: () => DevLog.openPage(context),
          ),
        ],
      ),
    );
  }
}

class _SettingsScaffold extends StatelessWidget {
  const _SettingsScaffold({
    required this.title,
    required this.description,
    required this.children,
  });

  final String title;
  final String description;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Scaffold(
      backgroundColor: c.bg,
      appBar: AppBar(title: Text(title), leading: _backButton(context)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(
          Gap.screen,
          Gap.sm,
          Gap.screen,
          Gap.h40,
        ),
        children: [
          Text(
            description,
            style: TextStyle(fontSize: 12, height: 1.5, color: c.textLow),
          ),
          const SizedBox(height: Gap.xl),
          ...children,
        ],
      ),
    );
  }
}

Future<void> _editName(
  BuildContext context,
  WidgetRef ref,
  UserSession user,
) async {
  final controller = TextEditingController(text: user.name ?? '');
  final name = await showDialog<String>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(
        context.tr('profile_rename_title'),
        style: const TextStyle(fontSize: 16),
      ),
      content: TextField(
        controller: controller,
        autofocus: true,
        maxLength: 60,
        decoration: InputDecoration(hintText: context.tr('profile_new_name')),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: Text(context.tr('cancel')),
        ),
        TextButton(
          onPressed: () => Navigator.pop(ctx, controller.text.trim()),
          child: Text(context.tr('save')),
        ),
      ],
    ),
  );
  if (name == null || name.length < 2) return;
  final token = user.token;
  if (token == null) return;
  try {
    final updated = await ref.read(authServiceProvider).updateName(token, name);
    await ref
        .read(authProvider.notifier)
        .refreshAuthenticatedProfile(
          email: updated.email,
          name: updated.name,
          picture: updated.picture,
        );
  } on AuthException catch (error) {
    if (context.mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }
}

/// Menu edit profil: ganti nama (via [_editName]) dan ganti foto (via
/// [_editAvatar]). Dipisah supaya tiap aksi jelas dan tidak berubah jadi
/// satu dialog yang menumpuk.
Future<void> _editProfile(
  BuildContext context,
  WidgetRef ref,
  UserSession user,
) async {
  await showModalBottomSheet<void>(
    context: context,
    backgroundColor: context.c.overlay,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(R.lg)),
    ),
    builder: (ctx) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _EditorHeader(
              title: context.tr('profile_edit'),
              onClose: () => Navigator.pop(ctx),
            ),
            const SizedBox(height: Gap.md),
            ListTile(
              leading: Icon(LucideIcons.user, size: 18, color: ctx.c.textMid),
              title: Text(
                context.tr('profile_rename'),
                style: TextStyle(fontSize: 14, color: ctx.c.textHi),
              ),
              trailing: const Icon(LucideIcons.chevronRight, size: 16),
              onTap: () async {
                Navigator.pop(ctx);
                await _editName(context, ref, user);
              },
            ),
            ListTile(
              leading: Icon(LucideIcons.camera, size: 18, color: ctx.c.textMid),
              title: Text(
                context.tr('profile_change_photo'),
                style: TextStyle(fontSize: 14, color: ctx.c.textHi),
              ),
              trailing: const Icon(LucideIcons.chevronRight, size: 16),
              onTap: () async {
                Navigator.pop(ctx);
                await _editAvatar(context, ref);
              },
            ),
            const SizedBox(height: Gap.sm),
          ],
        ),
      ),
    ),
  );
}

/// Pilih foto profil: dari preset DiceBear atau masukkan URL gambar sendiri.
Future<void> _editAvatar(BuildContext context, WidgetRef ref) async {
  final store = ref.read(storeProvider);
  final current = loadAvatar(store);

  await showModalBottomSheet<void>(
    context: context,
    backgroundColor: context.c.overlay,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(R.lg)),
    ),
    builder: (ctx) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _EditorHeader(
              title: context.tr('profile_change_photo'),
              onClose: () => Navigator.pop(ctx),
            ),
            const SizedBox(height: Gap.md),
            Text(
              context.tr('profile_pick_preset'),
              style: TextStyle(fontSize: 12.5, color: ctx.c.textMid),
            ),
            const SizedBox(height: Gap.sm),
            SizedBox(
              height: 96,
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  for (final seed in profileAvatarSeeds)
                    Padding(
                      padding: const EdgeInsets.only(right: Gap.md),
                      child: GestureDetector(
                        onTap: () async {
                          await saveAvatar(store, 'preset:$seed');
                          if (ctx.mounted) Navigator.pop(ctx);
                        },
                        child: Column(
                          children: [
                            ProfileAvatar(
                              name: seed,
                              initial: seed[0],
                              size: 62,
                            ),
                            const SizedBox(height: 5),
                            Text(
                              seed,
                              style: TextStyle(
                                fontSize: 10,
                                color: ctx.c.textLow,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: Gap.lg),
            ListTile(
              leading: Icon(
                LucideIcons.imageUp,
                size: 18,
                color: ctx.c.textMid,
              ),
              title: Text(
                context.tr('profile_upload_gallery'),
                style: TextStyle(fontSize: 14, color: ctx.c.textHi),
              ),
              trailing: const Icon(LucideIcons.chevronRight, size: 16),
              onTap: () async {
                Navigator.pop(ctx);
                await _uploadAvatar(context, ref);
              },
            ),
            ListTile(
              leading: Icon(LucideIcons.link, size: 18, color: ctx.c.textMid),
              title: Text(
                context.tr('profile_custom_url'),
                style: TextStyle(fontSize: 14, color: ctx.c.textHi),
              ),
              trailing: const Icon(LucideIcons.chevronRight, size: 16),
              onTap: () async {
                final url = await _askUrl(ctx);
                if (url != null && url.isNotEmpty) {
                  await saveAvatar(store, 'url:${Uri.encodeComponent(url)}');
                  if (ctx.mounted) Navigator.pop(ctx);
                }
              },
            ),
            if (current != null)
              TextButton(
                style: TextButton.styleFrom(
                  foregroundColor: ctx.c.textLow,
                  padding: EdgeInsets.zero,
                ),
                onPressed: () async {
                  await saveAvatar(store, '');
                  if (ctx.mounted) Navigator.pop(ctx);
                },
                child: Text(
                  context.tr('profile_reset_initial'),
                  style: const TextStyle(fontSize: 12.5),
                ),
              ),
          ],
        ),
      ),
    ),
  );
}

Future<String?> _askUrl(BuildContext context) async {
  final ctrl = TextEditingController();
  return showDialog<String>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(
        context.tr('profile_image_url'),
        style: const TextStyle(fontSize: 16),
      ),
      content: TextField(
        controller: ctrl,
        autofocus: true,
        keyboardType: TextInputType.url,
        decoration: const InputDecoration(hintText: 'https://…'),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: Text(context.tr('cancel')),
        ),
        TextButton(
          onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
          child: Text(context.tr('save')),
        ),
      ],
    ),
  );
}

/// Pilih foto dari galeri, unggah ke Cloudinary (unsigned preset), lalu
/// simpan URL-nya sebagai avatar. Kalau preset belum dikonfigurasi, tampilkan
/// pesan yang jelas (bukan gagal senyap).
Future<void> _uploadAvatar(BuildContext context, WidgetRef ref) async {
  final store = ref.read(storeProvider);

  // Tangkap messenger SEBELUM `await` apa pun, supaya tidak memakai
  // `BuildContext` di seberang async gap (bukan hal yang bisa aman).
  final messenger = ScaffoldMessenger.of(context);
  void toast(String message) =>
      messenger.showSnackBar(SnackBar(content: Text(message)));

  // Kalau preset belum diisi operator, jangan buka galeri — beri tahu dulu.
  if (cloudinaryUploadPreset.isEmpty) {
    toast('Unggah foto belum aktif (preset Cloudinary belum diisi).');
    return;
  }

  try {
    final picked = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      maxWidth: 512,
      maxHeight: 512,
      imageQuality: 85,
    );
    if (picked == null) return; // dibatalkan pengguna

    final bytes = await picked.readAsBytes();
    if (bytes.isEmpty) {
      toast('Gambar tidak terbaca. Coba pilih yang lain.');
      return;
    }

    toast('Mengunggah foto…');

    final url = await uploadProfileImage(bytes, filename: picked.name);

    await saveAvatar(store, 'url:${Uri.encodeComponent(url)}');
    messenger.clearSnackBars();
    toast('Foto profil terpasang.');
  } on CloudinaryUploadException catch (e) {
    toast(e.message);
  } catch (e) {
    toast('Gagal memilih/unggah foto: $e');
  }
}

/// Judul modal + tombol tutup, dipakai oleh lembar edit profil & avatar.
class _EditorHeader extends StatelessWidget {
  const _EditorHeader({required this.title, required this.onClose});

  final String title;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          title,
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
        ),
        IconButton(
          visualDensity: VisualDensity.compact,
          icon: const Icon(LucideIcons.x, size: 18),
          onPressed: onClose,
        ),
      ],
    );
  }
}

Future<void> _deleteAccount(BuildContext context, WidgetRef ref) async {
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(
        context.tr('account_delete_title'),
        style: const TextStyle(fontSize: 16),
      ),
      content: Text(
        context.tr('account_delete_body'),
        style: const TextStyle(fontSize: 13, height: 1.5),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: Text(context.tr('cancel')),
        ),
        TextButton(
          onPressed: () => Navigator.pop(ctx, true),
          child: Text(
            context.tr('account_delete_forever'),
            style: const TextStyle(color: AppColors.danger),
          ),
        ),
      ],
    ),
  );
  if (confirmed != true) return;
  final token = ref.read(authProvider).token;
  if (token == null) return;
  try {
    await ref.read(authServiceProvider).deleteAccount(token);
    await ref.read(googleAuthServiceProvider).signOut();
    await ref.read(authProvider.notifier).signOut();
  } on AuthException catch (error) {
    if (context.mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }
}

class _ProfileHero extends StatelessWidget {
  const _ProfileHero({
    required this.initial,
    required this.name,
    required this.email,
    required this.badge,
    this.pictureUrl,
  });

  final String initial;
  final String name;
  final String email;
  final String badge;
  final String? pictureUrl;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return SurfaceCard(
      padding: const EdgeInsets.all(Gap.lg),
      child: Row(
        children: [
          ProfileAvatar(
            name: name,
            initial: initial,
            size: 52,
            bordered: false,
            pictureUrl: pictureUrl,
          ),
          const SizedBox(width: Gap.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 15.5,
                    fontWeight: FontWeight.w600,
                    letterSpacing: -0.2,
                    color: c.textHi,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  email,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 11.5, color: c.textLow),
                ),
                const SizedBox(height: 8),
                Text(
                  badge,
                  style: TextStyle(
                    fontSize: 9,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 0.8,
                    color: c.accent,
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

class _CategoryRow extends StatelessWidget {
  const _CategoryRow({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.onTap,
    this.value,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final VoidCallback onTap;
  final String? value;

  @override
  Widget build(BuildContext context) {
    return ListRow(
      title: title,
      subtitle: subtitle,
      icon: icon,
      value: value,
      trailing: _chevron(context),
      onTap: onTap,
    );
  }
}

class _SwitchRow extends StatelessWidget {
  const _SwitchRow({
    required this.title,
    required this.icon,
    required this.value,
    required this.onChanged,
    this.subtitle,
    this.unavailable,
  });

  final String title;
  final String? subtitle;
  final IconData icon;
  final bool value;
  final ValueChanged<bool> onChanged;

  /// Bila diisi, sakelar dinonaktifkan dan alasannya ditampilkan.
  ///
  /// Sakelar yang bisa digeser, mengingat pilihanmu, dan tidak melakukan
  /// apa-apa lebih buruk daripada sakelar yang jujur mengaku belum siap.
  final String? unavailable;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final off = unavailable != null;
    return Opacity(
      opacity: off ? 0.55 : 1,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 9),
        child: Row(
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: c.raised,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(icon, size: 16, color: c.textMid),
            ),
            const SizedBox(width: Gap.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: c.textHi,
                    ),
                  ),
                  if (subtitle != null || off) ...[
                    const SizedBox(height: 3),
                    Text(
                      unavailable ?? subtitle!,
                      style: TextStyle(
                        fontSize: 11,
                        height: 1.4,
                        color: off ? c.warningText : c.textLow,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            Transform.scale(
              scale: 0.82,
              child: Switch(
                value: off ? false : value,
                onChanged: off
                    ? null
                    : (v) {
                        AppHaptics.impact();
                        onChanged(v);
                      },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Choice<T> {
  const _Choice(this.value, this.title, this.subtitle);

  final T value;
  final String title;
  final String subtitle;
}

Future<void> _showChoice<T>(
  BuildContext context, {
  required String title,
  required T current,
  required List<_Choice<T>> choices,
  required ValueChanged<T> onSelected,
}) {
  final c = context.c;
  return showModalBottomSheet<void>(
    context: context,
    backgroundColor: c.overlay,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(R.lg)),
    ),
    builder: (sheetContext) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          Gap.screen,
          Gap.sm,
          Gap.screen,
          Gap.lg,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 38,
              height: 4,
              decoration: BoxDecoration(
                color: c.textLow.withValues(alpha: 0.28),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: Gap.lg),
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                title,
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: c.textHi,
                ),
              ),
            ),
            const SizedBox(height: Gap.sm),
            for (final choice in choices)
              ListRow(
                title: choice.title,
                subtitle: choice.subtitle,
                trailing: choice.value == current
                    ? Icon(LucideIcons.check, size: 17, color: c.accent)
                    : const SizedBox(width: 17),
                onTap: () {
                  onSelected(choice.value);
                  Navigator.pop(sheetContext);
                },
              ),
          ],
        ),
      ),
    ),
  );
}

Future<void> _showLanguagePicker(BuildContext context, WidgetRef ref) {
  final current = ref.read(settingsProvider).langCode;
  return _showChoice<String>(
    context,
    title: context.tr('settings_language'),
    current: current,
    choices: [
      for (final language in AppLang.all)
        _Choice(language.code, language.nativeName, language.name),
    ],
    onSelected: ref.read(settingsProvider.notifier).setLang,
  );
}

Future<void> _pickCodec(BuildContext context, WidgetRef ref) {
  final current = ref.read(settingsProvider).codec;
  return _showChoice<String>(
    context,
    title: context.tr('streaming_codec'),
    current: current,
    choices: [
      _Choice(
        'AV1 (NVENC / AMF GPU)',
        'AV1 (NVENC / AMF GPU)',
        context.tr('codec_av1_sub'),
      ),
      _Choice(
        'HEVC / H.265 (10-bit)',
        'HEVC / H.265 (10-bit)',
        context.tr('codec_hevc_sub'),
      ),
      _Choice(
        'H.264 (AVC Universal)',
        'H.264 (AVC Universal)',
        context.tr('codec_h264_sub'),
      ),
    ],
    onSelected: ref.read(settingsProvider.notifier).setCodec,
  );
}

Future<void> _pickResolution(BuildContext context, WidgetRef ref) {
  final current = ref.read(settingsProvider).resolution;
  return _showChoice<String>(
    context,
    title: context.tr('streaming_resolution'),
    current: current,
    choices: [
      _Choice('720p60 (HD)', '720p60 (HD)', context.tr('res_720_sub')),
      _Choice('1080p60 (FHD)', '1080p60 (FHD)', context.tr('res_1080_sub')),
      _Choice(
        '1440p120 (QHD 2K)',
        '1440p120 (QHD 2K)',
        context.tr('res_1440_sub'),
      ),
      _Choice('4K60 (UHD)', '4K60 (UHD)', context.tr('res_4k_sub')),
    ],
    onSelected: ref.read(settingsProvider.notifier).setResolution,
  );
}

Future<void> _pickBitrate(BuildContext context, WidgetRef ref) {
  final current = ref.read(settingsProvider).bitrateMbps;
  return _showChoice<int>(
    context,
    title: context.tr('streaming_bitrate'),
    current: current,
    choices: [
      _Choice(10, '10 Mbps', context.tr('bitrate_10_sub')),
      _Choice(25, '25 Mbps', context.tr('bitrate_25_sub')),
      _Choice(50, '50 Mbps', context.tr('bitrate_50_sub')),
    ],
    onSelected: ref.read(settingsProvider.notifier).setBitrateMbps,
  );
}

String _shortCodec(String value) {
  if (value.startsWith('AV1')) return 'AV1';
  if (value.startsWith('HEVC')) return 'H.265';
  return 'H.264';
}

Widget _chevron(BuildContext context) =>
    Icon(LucideIcons.chevronRight, size: 16, color: context.c.textLow);

Widget _backButton(BuildContext context) => IconButton(
  icon: Icon(LucideIcons.arrowLeft, size: 20, color: context.c.textMid),
  onPressed: () => Navigator.pop(context),
);

void _open(BuildContext context, Widget page) {
  Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => page));
}
