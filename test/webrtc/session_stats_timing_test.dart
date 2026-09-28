// Rincian latensi client dari getStats: jitter buffer & decode per frame.
//
// Paritas dengan `web/test/latency_probe.test.js` dan `average()` di
// `web/src/rtc.ts`: kedua client harus menurunkan angka yang sama dari
// penghitung kumulatif WebRTC yang sama. Yang diuji murni aritmetika —
// tidak butuh peer connection.

import 'package:flutter_test/flutter_test.dart';
import 'package:xydesk/webrtc/rtc_service.dart';

void main() {
  group('averageDeltaMs', () {
    test('delta total / delta count, detik → ms', () {
      // 60 frame menunggu total 0.72 s → 12 ms/frame.
      expect(averageDeltaMs(1.72, 160, 1.0, 100), closeTo(12.0, 1e-9));
    });

    test('null bila nilai hilang', () {
      expect(averageDeltaMs(null, 10, 0.0, 0), isNull);
      expect(averageDeltaMs(1.0, null, 0.0, 0), isNull);
      expect(averageDeltaMs(1.0, 10, null, 0), isNull);
      expect(averageDeltaMs(1.0, 10, 0.0, null), isNull);
    });

    test('null bila penghitung tidak maju (frame beku)', () {
      expect(averageDeltaMs(1.0, 100, 1.0, 100), isNull);
    });

    test('null bila total mundur (laporan direset oleh track baru)', () {
      expect(averageDeltaMs(0.1, 5, 1.0, 100), isNull);
    });
  });

  group('SessionStats label timing', () {
    test('tanpa data → "-" bukan 0 ms', () {
      const st = SessionStats();
      expect(st.jitterBufferLabel, '-');
      expect(st.decodeLabel, '-');
    });

    test('dibulatkan ke ms utuh', () {
      const st = SessionStats(jitterBufferMs: 11.6, decodeMs: 4.2);
      expect(st.jitterBufferLabel, '12 ms');
      expect(st.decodeLabel, '4 ms');
    });

    test('copyWith mempertahankan nilai timing', () {
      const st = SessionStats(jitterBufferMs: 10, decodeMs: 5);
      final next = st.copyWith(fps: 60);
      expect(next.jitterBufferMs, 10);
      expect(next.decodeMs, 5);
      expect(next.fps, 60);
    });
  });
}
