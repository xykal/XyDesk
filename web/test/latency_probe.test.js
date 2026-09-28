import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { transformWithOxc } from 'vite';

// Kompilasi TS dengan compiler yang sama dengan build; DOM tidak dibutuhkan
// karena statistik probe murni angka.
const input = readFileSync(new URL('../src/latency_probe.ts', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '')
  .replace(/^export /gm, '');
const { code } = await transformWithOxc(
  input + '\nexports.LatencyProbe = LatencyProbe; exports.percentiles = percentiles; exports.estimateGlassToGlass = estimateGlassToGlass; exports.buildLatencyReport = buildLatencyReport;',
  'latency_probe.ts',
);
const exports = {};
vm.runInNewContext(code, { exports, HTMLVideoElement: undefined });
const { LatencyProbe, percentiles, estimateGlassToGlass, buildLatencyReport } = exports;

test('percentiles: p50/p95/max dari daftar tak berurut', () => {
  const p = percentiles([30, 10, 20, 40, 50, 60, 70, 80, 90, 100]);
  assert.equal(p.p50, 50);
  assert.equal(p.p95, 100);
  assert.equal(p.max, 100);
  assert.equal(percentiles([]), undefined);
  assert.equal(percentiles([NaN]), undefined);
});

test('probe: receive→display dihitung per frame, jendela geser dipegang', () => {
  const probe = new LatencyProbe(3);
  // 5 frame @16.67ms; receive selalu 12 ms sebelum display, kecuali frame ke-4 (40 ms).
  const frames = [0, 16.67, 33.33, 50, 66.67].map((t, i) => ({
    expectedDisplayTime: 1000 + t,
    receiveTime: 1000 + t - (i === 3 ? 40 : 12),
  }));
  frames.forEach(f => probe.push(f));
  const s = probe.summary();
  assert.equal(s.samples, 3, 'jendela dibatasi 3 frame terakhir');
  assert.equal(s.unsupported, 0);
  assert.equal(s.receiveToDisplay.p50, 12);
  assert.equal(s.receiveToDisplay.max, 40);
  assert.ok(Math.abs(s.frameInterval.p50 - 16.67) < 0.01);
  assert.equal(s.captureToDisplay, undefined, 'tanpa captureTime tidak ada angka capture');
});

test('probe: browser tanpa receiveTime dihitung sebagai unsupported, bukan 0 ms', () => {
  const probe = new LatencyProbe();
  probe.push({ expectedDisplayTime: 10 });
  probe.push({ expectedDisplayTime: 26 });
  const s = probe.summary();
  assert.equal(s.unsupported, 2);
  assert.equal(s.receiveToDisplay, undefined);
});

test('probe: captureTime dari host dipakai bila ada; nilai negatif dibuang', () => {
  const probe = new LatencyProbe();
  probe.push({ expectedDisplayTime: 100, receiveTime: 90, captureTime: 60 });
  probe.push({ expectedDisplayTime: 116, receiveTime: 106, captureTime: 120 }); // jam host melompat: buang
  const s = probe.summary();
  assert.equal(s.captureToDisplay.p50, 40);
  assert.equal(s.captureToDisplay.max, 40);
});

test('estimate: tanpa captureTime → lower-bound = RTT/2 + receive→display (+ encode bila ada)', () => {
  const probe = new LatencyProbe();
  for (let i = 0; i < 10; i++) probe.push({ expectedDisplayTime: i * 16, receiveTime: i * 16 - 14 });
  const noEncode = estimateGlassToGlass({ rttMs: 8, summary: probe.summary() });
  assert.equal(noEncode.confidence, 'lower-bound');
  assert.equal(noEncode.totalMs, 4 + 14);
  const withEncode = estimateGlassToGlass({ rttMs: 8, summary: probe.summary(), hostEncodeMs: 9 });
  assert.equal(withEncode.totalMs, 4 + 14 + 9);
  assert.equal(estimateGlassToGlass({ rttMs: 8, summary: new LatencyProbe().summary() }), undefined);
});

test('estimate: dengan captureTime → measured, memakai capture→display langsung', () => {
  const probe = new LatencyProbe();
  probe.push({ expectedDisplayTime: 100, receiveTime: 90, captureTime: 65 });
  const e = estimateGlassToGlass({ rttMs: 100, summary: probe.summary() });
  assert.equal(e.confidence, 'measured');
  assert.equal(e.totalMs, 35);
});

test('report: skema stabil, hanya field koneksi yang relevan ikut', () => {
  const probe = new LatencyProbe();
  probe.push({ expectedDisplayTime: 100, receiveTime: 88 });
  const summary = probe.summary();
  const report = buildLatencyReport({
    summary,
    estimate: estimateGlassToGlass({ rttMs: 10, summary }),
    stats: { rttMs: 10, codec: 'H264', fps: 60, cursorState: 'rahasia-internal', playerState: 'playing' },
    userAgent: 'test',
    version: '6.8.5',
    now: new Date('2026-09-28T00:00:00Z'),
  });
  assert.equal(report.schema, 'xydesk-latency-report/1');
  assert.equal(report.generatedAt, '2026-09-28T00:00:00.000Z');
  assert.deepEqual({ ...report.connection }, { rttMs: 10, codec: 'H264', fps: 60 });
  assert.equal(report.estimate.totalMs, 5 + 12);
  assert.equal(report.latency.receiveToDisplay.p50, 12);
});
