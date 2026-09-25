// Panduan sesi web: relay TURN dan "tersambung tapi belum ada gambar".
//
// Keduanya keputusan yang harus bisa dipertanggungjawabkan ke pengguna: apa
// yang dikatakan, dan langkah apa yang ditawarkan. Dulu relay yang tidak ada
// berakhir sebagai daftar kosong tanpa pesan, dan sesi yang tersambung tanpa
// frame hanya terlihat sebagai layar gelap.

import test from 'node:test';
import assert from 'node:assert/strict';

import { captureHealthText, frameGuidance, relayReasonText, relayStatusText } from '../src/session_guidance.ts';

test('setiap sebab relay yang mungkin punya kalimat, kode asing tidak ditelan', () => {
  for (const reason of [
    'no-credentials',
    'token-invalid',
    'ticket-invalid',
    'ticket-revoked',
    'turn-forbidden',
    'turn-not-configured',
    'turn-auth-unavailable',
    'providers-failed',
    'no-servers',
    'bad-response',
    'network',
  ]) {
    const text = relayReasonText(reason);
    assert.notEqual(text, 'sebab tidak diketahui', `${reason} harus punya kalimat`);
    assert.notEqual(text, reason, `${reason} tidak boleh tampil sebagai kode mentah`);
  }
  assert.equal(relayReasonText('sebab-baru'), 'sebab-baru');
  assert.equal(relayReasonText(undefined), 'sebab tidak diketahui');
});

test('status relay siap menyebut jumlah server, belum diperiksa tidak berbohong', () => {
  assert.match(relayStatusText('ready', 3), /^3 server siap/);
  assert.match(relayStatusText('unavailable'), /Tidak tersedia/);
  assert.equal(relayStatusText(undefined), 'Belum diperiksa');
  assert.equal(relayStatusText('pending'), 'Belum diperiksa');
});

test('tanpa peringatan frame: tidak ada banner', () => {
  assert.equal(frameGuidance(null), null);
  assert.equal(frameGuidance(undefined), null);
  assert.equal(frameGuidance({ noFrameWarning: false, relayState: 'ready' }), null);
});

test('belum ada gambar tanpa masalah relay: menyebut langkah berikutnya', () => {
  const g = frameGuidance({ noFrameWarning: true, relayState: 'ready' });
  assert.ok(g);
  assert.equal(g.title, 'Tersambung tapi belum ada gambar');
  assert.equal(g.action, 'Ganti layar');
  assert.equal(g.relayRelated, false);
  assert.match(g.detail, /PC host/);
});

test('relay tidak tersedia disebut sebagai sebab, lengkap dengan alasannya', () => {
  const g = frameGuidance({
    noFrameWarning: true,
    relayState: 'unavailable',
    relayReason: 'token-invalid',
  });
  assert.ok(g);
  assert.equal(g.relayRelated, true);
  assert.match(g.detail, /token perangkat ditolak server/);
  assert.match(g.detail, /sambung ulang/);
});

test('relay belum diperiksa tidak diklaim sebagai penyebab', () => {
  const g = frameGuidance({ noFrameWarning: true, relayState: 'pending' });
  assert.ok(g);
  assert.equal(g.relayRelated, false);
  assert.doesNotMatch(g.detail, /Relay TURN juga tidak tersedia/);
});

test('teks panduan tidak memakai istilah internal proyek', () => {
  const g = frameGuidance({ noFrameWarning: true, relayState: 'unavailable', relayReason: 'network' });
  assert.ok(g);
  for (const forbidden of ['rtc.ts', 'worker.js', 'selectDisplay', 'noFrameWarning', 'undefined', 'null']) {
    assert.doesNotMatch(g.title + ' ' + g.detail, new RegExp(forbidden.replace('.', '\\.')));
  }
});

test('kesehatan capture: sesi berbeda dan layar terkunci disebut jujur', () => {
  assert.match(captureHealthText(false, true), /sesi yang berbeda/);
  assert.match(captureHealthText(true, false), /terkunci/);
  assert.equal(captureHealthText(false, false), null);
  assert.equal(captureHealthText(undefined, undefined), null);
});
