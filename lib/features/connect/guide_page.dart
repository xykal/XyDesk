import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../core/tokens.dart';
import '../../widgets/brand.dart';
import '../../widgets/seamless.dart';
import '../../core/l10n_bridge.dart';

enum GuideAudience { client, host }

/// Panduan langkah demi langkah untuk sisi client dan host.
class GuidePage extends StatefulWidget {
  const GuidePage({super.key, this.initialAudience = GuideAudience.client});

  final GuideAudience initialAudience;

  @override
  State<GuidePage> createState() => _GuidePageState();
}

class _GuidePageState extends State<GuidePage> {
  late GuideAudience _audience = widget.initialAudience;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final client = _audience == GuideAudience.client;
    final steps = client ? _clientSteps : _hostSteps;

    return Scaffold(
      backgroundColor: c.bg,
      appBar: AppBar(
        title: Text(context.tr('guide_title')),
        leading: IconButton(
          icon: Icon(LucideIcons.arrowLeft, color: c.textMid),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.screen, 8, Gap.screen, 36),
        children: [
          const Center(child: Illus(Img.guideOverview, size: 220)),
          const SizedBox(height: Gap.sm),
          Text(
            context.tr('guide_headline'),
            textAlign: TextAlign.center,
            style: TextStyle(
              color: c.textHi,
              fontSize: 20,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: Gap.sm),
          Text(
            context.tr('guide_sub'),
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12.5, color: c.textMid, height: 1.45),
          ),
          const SizedBox(height: Gap.lg),
          _AudienceToggle(
            selected: _audience,
            onChanged: (value) => setState(() => _audience = value),
          ),
          const SizedBox(height: Gap.lg),
          for (var i = 0; i < steps.length; i++) ...[
            _GuideStepCard(
              number: i + 1,
              title: steps[i].title,
              description: steps[i].description,
              icon: steps[i].icon,
              illustration: steps[i].illustration,
            ),
            if (i != steps.length - 1) const SizedBox(height: Gap.md),
          ],
          const SizedBox(height: Gap.lg),
          SurfaceCard(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(LucideIcons.info, size: 17, color: c.accent),
                const SizedBox(width: Gap.md),
                Expanded(
                  child: Text(
                    client
                        ? context.tr('guide_client_is')
                        : context.tr('guide_host_is'),
                    style: TextStyle(
                      color: c.textMid,
                      fontSize: 11.5,
                      height: 1.5,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  List<_GuideData> get _clientSteps => [
    _GuideData(
      title: context.tr('guide_c1'),
      description: context.tr('guide_c1_sub'),
      icon: LucideIcons.smartphone,
      illustration: Img.guideClient,
    ),
    _GuideData(
      title: context.tr('guide_c2'),
      description: context.tr('guide_c2_sub'),
      icon: LucideIcons.keyRound,
    ),
    _GuideData(
      title: context.tr('guide_c3'),
      description: context.tr('guide_c3_sub'),
      icon: LucideIcons.scanLine,
    ),
    _GuideData(
      title: context.tr('guide_c4'),
      description: context.tr('guide_c4_sub'),
      icon: LucideIcons.play,
    ),
  ];

  List<_GuideData> get _hostSteps => [
    _GuideData(
      title: context.tr('guide_h1'),
      description: context.tr('guide_h1_sub'),
      icon: LucideIcons.monitor,
      illustration: Img.guideHost,
    ),
    _GuideData(
      title: context.tr('guide_h2'),
      description: context.tr('guide_h2_sub'),
      icon: LucideIcons.power,
    ),
    _GuideData(
      title: context.tr('guide_h3'),
      description: context.tr('guide_h3_sub'),
      icon: LucideIcons.qrCode,
    ),
    _GuideData(
      title: context.tr('guide_h4'),
      description: context.tr('guide_h4_sub'),
      icon: LucideIcons.shieldCheck,
    ),
  ];
}

class _AudienceToggle extends StatelessWidget {
  const _AudienceToggle({required this.selected, required this.onChanged});

  final GuideAudience selected;
  final ValueChanged<GuideAudience> onChanged;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: c.input,
        borderRadius: BorderRadius.circular(R.lg),
      ),
      child: Row(
        children: [
          _item(
            context,
            GuideAudience.client,
            context.tr('guide_im_phone'),
            LucideIcons.smartphone,
          ),
          _item(
            context,
            GuideAudience.host,
            context.tr('guide_im_pc'),
            LucideIcons.monitor,
          ),
        ],
      ),
    );
  }

  Widget _item(
    BuildContext context,
    GuideAudience value,
    String label,
    IconData icon,
  ) {
    final c = context.c;
    final active = value == selected;
    return Expanded(
      child: GestureDetector(
        onTap: () => onChanged(value),
        child: AnimatedContainer(
          duration: D.tab,
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: active ? c.raised : Colors.transparent,
            borderRadius: BorderRadius.circular(R.md),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 15, color: active ? c.accent : c.textLow),
              const SizedBox(width: 6),
              Text(
                label,
                style: TextStyle(
                  color: active ? c.textHi : c.textLow,
                  fontSize: 11,
                  fontWeight: active ? FontWeight.w600 : FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _GuideData {
  const _GuideData({
    required this.title,
    required this.description,
    required this.icon,
    this.illustration,
  });

  final String title;
  final String description;
  final IconData icon;
  final String? illustration;
}

class _GuideStepCard extends StatelessWidget {
  const _GuideStepCard({
    required this.number,
    required this.title,
    required this.description,
    required this.icon,
    this.illustration,
  });

  final int number;
  final String title;
  final String description;
  final IconData icon;
  final String? illustration;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return SurfaceCard(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (illustration != null) ...[
            Center(child: Illus(illustration!, size: 160)),
            const SizedBox(height: Gap.sm),
          ],
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 30,
                height: 30,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: c.accentSoft,
                  borderRadius: BorderRadius.circular(9),
                ),
                child: Text(
                  '$number',
                  style: TextStyle(
                    color: c.accent,
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const SizedBox(width: Gap.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(icon, size: 16, color: c.textMid),
                        const SizedBox(width: 7),
                        Expanded(
                          child: Text(
                            title,
                            style: TextStyle(
                              color: c.textHi,
                              fontSize: 13.5,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 5),
                    Text(
                      description,
                      style: TextStyle(
                        color: c.textMid,
                        fontSize: 11.5,
                        height: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
