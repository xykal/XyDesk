import 'package:flutter_test/flutter_test.dart';
import 'package:xydesk/webrtc/auto_preset.dart';
import 'package:xydesk/webrtc/input_codec.dart';

// Skenario sama dengan web/test/auto_preset.test.js — kedua implementasi
// harus mengambil keputusan identik.
const phone1080 = AutoInput(
  clientLongEdgePx: 2400,
  hostLevel: 40,
  fpsLimit: 60,
);
const phone720 = AutoInput(clientLongEdgePx: 1599, hostLevel: 40, fpsLimit: 60);

AutoInput calm(
  AutoInput base,
  String encoder, {
  double? loss,
  double? glass,
  double? fps,
  double? rtt,
  double? decode,
}) => AutoInput(
  clientLongEdgePx: base.clientLongEdgePx,
  hostLevel: base.hostLevel,
  fpsLimit: base.fpsLimit,
  encoder: encoder,
  rttMs: rtt ?? 20,
  recentLossPct: loss ?? 0,
  jitterBufferMs: 30,
  decodeMs: decode ?? 8,
  deliveredFps: fps ?? 30,
  glassMs: glass ?? 60,
);

void main() {
  test('plafon: layar 720p tidak pernah diminta 1080p', () {
    expect(ceilingTier(phone720.copyWith(encoder: 'nvenc')).$1, 2);
    expect(ceilingTier(phone720.copyWith(encoder: 'openh264')).$1, 0);
  });

  test('plafon: software tidak pernah 60 FPS, hardware boleh 1080p60', () {
    expect(ceilingTier(phone1080.copyWith(encoder: 'openh264')).$1, 1);
    expect(ceilingTier(phone1080.copyWith(encoder: 'nvenc')).$1, 3);
  });

  test('awal: hardware 1080p30, software 720p30', () {
    expect(
      AutoPreset().initial(phone1080.copyWith(encoder: 'nvenc'), 0).tier,
      1,
    );
    expect(
      AutoPreset().initial(phone1080.copyWith(encoder: 'openh264'), 0).tier,
      0,
    );
  });

  test('naik bertahap tiap 12 detik sampai plafon', () {
    final a = AutoPreset()..initial(phone1080.copyWith(encoder: 'nvenc'), 0);
    final changes = <(int, int)>[];
    for (var t = 1000; t <= 60000; t += 1000) {
      final d = a.update(
        calm(phone1080, 'nvenc', fps: a.current.fps.toDouble()),
        t,
      );
      if (d != null) changes.add((t, d.tier));
    }
    expect(changes, [(12000, 2), (24000, 3)]);
  });

  test('turun segera saat loss; tidak lebih sering dari 5 detik', () {
    final a = AutoPreset()..initial(phone1080.copyWith(encoder: 'nvenc'), 0);
    a.update(calm(phone1080, 'nvenc'), 12000);
    a.update(calm(phone1080, 'nvenc', fps: 60), 24000);
    expect(a.current.tier, 3);
    final d = a.update(calm(phone1080, 'nvenc', loss: 5), 30000)!;
    expect(d.tier, 2);
    expect(d.reason, contains('kehilangan paket'));
    expect(a.update(calm(phone1080, 'nvenc', loss: 5), 31000), isNull);
    expect(a.update(calm(phone1080, 'nvenc', loss: 5), 36000)!.tier, 1);
  });

  test('dua kali gagal naik = berhenti (tanpa osilasi)', () {
    final a = AutoPreset()..initial(phone1080.copyWith(encoder: 'nvenc'), 0);
    var changes = 0;
    for (var t = 0; t < 400000; t += 1000) {
      final heavy = a.current.tier >= 2;
      final d = a.update(calm(phone1080, 'nvenc', loss: heavy ? 4 : 0), t);
      if (d != null) {
        changes++;
      }
    }
    expect(a.current.tier, 1);
    expect(changes, lessThanOrEqualTo(4));
  });

  test('software: 1080p turun bila latensi pemrosesan tinggi', () {
    final a = AutoPreset()..initial(phone1080.copyWith(encoder: 'openh264'), 0);
    expect(a.update(calm(phone1080, 'openh264'), 12000)!.tier, 1);
    final d = a.update(calm(phone1080, 'openh264', glass: 140), 20000)!;
    expect(d.tier, 0);
    expect(d.reason, contains('latensi'));
  });

  test('decode client lambat = turun walau jaringan bersih', () {
    final a = AutoPreset()..initial(phone1080.copyWith(encoder: 'nvenc'), 0);
    expect(a.current.tier, 1);
    final d = a.update(calm(phone1080, 'nvenc', decode: 28), 20000)!;
    expect(d.tier, 0);
    expect(d.reason, contains('decode'));
  });

  test('protokol: 0x0C dan 0x0F persis 2 byte, 0x0A/0x0B 8 byte', () {
    expect(InputCodec.videoMode(1), [0x0c, 1]);
    expect(InputCodec.videoFps(60), [0x0f, 60]);
    expect(InputCodec.videoFps(45), [0x0f, 30]);
    expect(InputCodec.videoQuality(3).length, 8);
    expect(InputCodec.videoQuality(9)[1], 3);
    final b = InputCodec.videoBitrateMbps(25);
    expect(b.length, 8);
    expect(b[0], 0x0b);
    expect(b[1] | (b[2] << 8), 25);
  });
}
