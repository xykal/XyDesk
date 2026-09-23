// Relay TURN di client Flutter: label dan sebabnya.
//
// Paritas dengan `web/test/session_guidance.test.js` + `web/test/turn_ice_api.test.js`:
// tiga platform (host, web, client) harus memakai kalimat yang sama untuk
// sebab yang sama, karena ketiganya berbicara ke endpoint `/turn-ice` yang
// sama. Yang diuji di sini bagian yang murni: tidak butuh jaringan.

import 'package:flutter_test/flutter_test.dart';
import 'package:xydesk/webrtc/rtc_service.dart';

void main() {
  group('relayReasonLabel', () {
    const reasons = [
      'no-credentials',
      'token-invalid',
      'ticket-invalid',
      'ticket-revoked',
      'turn-forbidden',
      'turn-not-configured',
      'turn-auth-unavailable',
      'providers-failed',
      'no-servers',
      'no-signaling',
      'bad-response',
      'network',
    ];

    test('setiap sebab punya kalimat, bukan kode mentah', () {
      for (final reason in reasons) {
        final label = relayReasonLabel(reason);
        expect(label, isNot('sebab tidak diketahui'), reason: reason);
        expect(label, isNot(reason), reason: reason);
      }
    });

    test('sebab asing tidak ditelan: kodenya tetap tampil', () {
      expect(
        relayReasonLabel('sebab-baru-dari-server'),
        'sebab-baru-dari-server',
      );
    });

    test('sebab kosong jatuh ke kalimat netral', () {
      expect(relayReasonLabel(null), 'sebab tidak diketahui');
      expect(relayReasonLabel(''), 'sebab tidak diketahui');
    });
  });

  group('TurnRelay', () {
    test('belum diperiksa tidak diklaim sebagai tidak tersedia', () {
      const relay = TurnRelay();
      expect(relay.ok, isFalse);
      expect(relay.label, 'Belum diperiksa');
    });

    test('relay siap menyebut jumlah server', () {
      const relay = TurnRelay(
        servers: [
          {'urls': 'turn:relay.example:3478'},
          {'urls': 'turn:relay.example:3478?transport=tcp'},
        ],
      );
      expect(relay.ok, isTrue);
      expect(relay.serverCount, 2);
      expect(relay.label, contains('2 server siap'));
    });

    test('relay tidak tersedia membawa sebab yang bisa dibaca', () {
      const relay = TurnRelay(
        reason: 'token-invalid',
        hint: 'ambil token baru',
      );
      expect(relay.ok, isFalse);
      expect(relay.label, contains('Tidak tersedia'));
      expect(relay.label, contains('token perangkat ditolak server'));
      expect(relay.hint, 'ambil token baru');
    });
  });

  group('SessionStats relay', () {
    test('belum ada laporan relay: netral, bukan klaim', () {
      const stats = SessionStats();
      expect(stats.relayLabel, 'Belum diperiksa');
      expect(stats.relayUnavailable, isFalse);
    });

    test('relay tidak tersedia menandai banner "belum ada gambar"', () {
      const stats = SessionStats(
        relayOk: false,
        relayReason: 'turn-not-configured',
      );
      expect(stats.relayUnavailable, isTrue);
      expect(stats.relayLabel, contains('server belum dikonfigurasi TURN'));
    });

    test('relay siap tidak menandai apa pun', () {
      const stats = SessionStats(relayOk: true, relayServers: 2);
      expect(stats.relayUnavailable, isFalse);
      expect(stats.relayLabel, contains('2 server siap'));
    });
  });
}
