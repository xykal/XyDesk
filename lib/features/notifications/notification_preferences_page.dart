import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';
import '../../widgets/seamless.dart';
import 'notification_service.dart';
import '../../core/l10n_bridge.dart';

class NotificationPreferencesPage extends StatefulWidget {
  const NotificationPreferencesPage({super.key});

  @override
  State<NotificationPreferencesPage> createState() =>
      _NotificationPreferencesPageState();
}

class _NotificationPreferencesPageState
    extends State<NotificationPreferencesPage>
    with WidgetsBindingObserver {
  final _service = NotificationService.instance;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _service.addListener(_rebuild);
    unawaited(_service.refresh());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _service.removeListener(_rebuild);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(_service.refresh());
  }

  void _rebuild() {
    if (mounted) setState(() {});
  }

  Future<void> _toggle() async {
    if (_service.active) {
      await _service.pauseUpdates();
      return;
    }

    final enabled = await _service.enableUpdates();
    if (!mounted || enabled) return;
    final message =
        _service.lastError ??
        context.tr('notif_not_enabled');
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final status = _status;
    return Scaffold(
      backgroundColor: c.bg,
      appBar: AppBar(
        title: Text(context.tr('notif_update_title')),
        leading: IconButton(
          icon: Icon(LucideIcons.arrowLeft, size: 20, color: c.textMid),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(
          Gap.screen,
          Gap.xl,
          Gap.screen,
          Gap.h40,
        ),
        children: [
          Center(
            child: Container(
              width: 86,
              height: 86,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [c.accent.withValues(alpha: 0.28), c.raised],
                ),
                borderRadius: BorderRadius.circular(R.xl),
              ),
              child: Icon(
                _service.active ? LucideIcons.bell : LucideIcons.bellOff,
                size: 34,
                color: c.accent,
              ),
            ),
          ),
          const SizedBox(height: Gap.xl),
          Text(
            context.tr('notif_know_when'),
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 21,
              fontWeight: FontWeight.w700,
              color: c.textHi,
            ),
          ),
          const SizedBox(height: Gap.sm),
          Text(
            context.tr('notif_rare'),
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12.5, height: 1.6, color: c.textMid),
          ),
          SectionLabel(context.tr('notif_status')),
          SurfaceCard(
            child: Row(
              children: [
                Container(
                  width: 34,
                  height: 34,
                  decoration: BoxDecoration(
                    color: status.color.withValues(alpha: 0.13),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(status.icon, size: 17, color: status.color),
                ),
                const SizedBox(width: Gap.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        status.title,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: c.textHi,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        status.description,
                        style: TextStyle(
                          fontSize: 11,
                          height: 1.4,
                          color: c.textLow,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          SectionLabel(context.tr('notif_how')),
          _InfoRow(
            icon: LucideIcons.smartphone,
            title: context.tr('notif_you_decide'),
            body:
                context.tr('notif_you_decide_sub'),
          ),
          _InfoRow(
            icon: LucideIcons.packageOpen,
            title: context.tr('notif_internal'),
            body:
                context.tr('notif_internal_sub'),
          ),
          _InfoRow(
            icon: LucideIcons.shieldCheck,
            title: context.tr('notif_official'),
            body:
                context.tr('notif_official_sub'),
          ),
          const SizedBox(height: Gap.xl),
          FilledButton.icon(
            onPressed: !_service.supported || _service.busy ? null : _toggle,
            icon: _service.busy
                ? const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(
                    _service.active ? LucideIcons.bellOff : LucideIcons.bell,
                    size: 17,
                  ),
            label: Text(
              _service.busy
                  ? context.tr('notif_preparing')
                  : _service.active
                  ? context.tr('notif_pause')
                  : !_service.permissionGranted &&
                        !_service.canRequestPermission &&
                        _service.initialized
                  ? context.tr('notif_open_settings')
                  : context.tr('notif_enable'),
            ),
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(50),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(R.md),
              ),
            ),
          ),
          if (_service.active) ...[
            const SizedBox(height: Gap.sm),
            Text(
              context.tr('notif_pause_note'),
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 10.5, height: 1.5, color: c.textLow),
            ),
          ],
        ],
      ),
    );
  }

  _NotificationStatus get _status {
    final c = context.c;
    if (!_service.supported) {
      return _NotificationStatus(
        title: context.tr('notif_unsupported'),
        description: context.tr('notif_unsupported_sub'),
        icon: LucideIcons.info,
        color: c.textLow,
      );
    }
    if (_service.lastError != null && !_service.initialized) {
      return _NotificationStatus(
        title: context.tr('notif_not_connected'),
        description: _service.lastError!,
        icon: LucideIcons.info,
        color: AppColors.warning,
      );
    }
    if (_service.active) {
      return _NotificationStatus(
        title: context.tr('notif_active'),
        description: context.tr('notif_active_sub'),
        icon: LucideIcons.check,
        color: AppColors.success,
      );
    }
    if (_service.permissionGranted && !_service.optedIn) {
      return _NotificationStatus(
        title: context.tr('notif_paused'),
        description: context.tr('notif_paused_sub'),
        icon: LucideIcons.bellOff,
        color: c.textLow,
      );
    }
    return _NotificationStatus(
      title: context.tr('notif_off'),
      description: context.tr('notif_off_sub'),
      icon: LucideIcons.bellOff,
      color: c.textLow,
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.icon, required this.title, required this.body});

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Padding(
      padding: const EdgeInsets.only(bottom: Gap.lg),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 31,
            height: 31,
            decoration: BoxDecoration(
              color: c.raised,
              borderRadius: BorderRadius.circular(9),
            ),
            child: Icon(icon, size: 15, color: c.accent),
          ),
          const SizedBox(width: Gap.md),
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
                const SizedBox(height: 3),
                Text(
                  body,
                  style: TextStyle(
                    fontSize: 11.5,
                    height: 1.5,
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

class _NotificationStatus {
  const _NotificationStatus({
    required this.title,
    required this.description,
    required this.icon,
    required this.color,
  });

  final String title;
  final String description;
  final IconData icon;
  final Color color;
}
