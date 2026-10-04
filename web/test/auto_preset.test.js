import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { transformWithOxc } from 'vite';

const input = readFileSync(new URL('../src/auto_preset.ts', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '')
  .replace(/^export /gm, '');
const { code } = await transformWithOxc(
  input + '\nexports.AutoPreset = AutoPreset; exports.ceilingTier = ceilingTier; exports.overloaded = overloaded;',
  'auto_preset.ts',
);
const exports = {};
vm.runInNewContext(code, { exports });
const { AutoPreset, ceilingTier, overloaded } = exports;

const phone1080 = { clientLongEdgePx: 2400, hostLevel: 40, fpsLimit: 60 };
const phone720 = { clientLongEdgePx: 1600 - 1, hostLevel: 40, fpsLimit: 60 };
const calm = { rttMs: 20, recentLossPct: 0, jitterBufferMs: 30, deliveredFps: 30, glassMs: 60 };

test('plafon: layar 720p tidak pernah diminta 1080p', () => {
  assert.equal(ceilingTier({ ...phone720, encoder: 'nvenc' }).tier, 1);
  assert.equal(ceilingTier({ ...phone720, encoder: 'openh264' }).tier, 0);
});

test('plafon: encoder software tidak pernah 60 FPS, hardware boleh 1080p60', () => {
  assert.equal(ceilingTier({ ...phone1080, encoder: 'openh264' }).tier, 2);
  assert.equal(ceilingTier({ ...phone1080, encoder: 'nvenc' }).tier, 3);
  assert.equal(ceilingTier({ ...phone1080, encoder: 'nvenc', fpsLimit: 30 }).tier, 2);
  assert.equal(ceilingTier({ ...phone1080, encoder: 'nvenc', hostLevel: 31 }).tier, 1);
});

test('awal: semua encoder mulai 720p30, bukan langsung HD', () => {
  assert.equal(new AutoPreset().initial({ ...phone1080, encoder: 'nvenc' }, 0).tier, 0);
  assert.equal(new AutoPreset().initial({ ...phone1080, encoder: 'openh264' }, 0).tier, 0);
  assert.equal(new AutoPreset().initial({ ...phone720, encoder: 'nvenc' }, 0).tier, 0);
});

test('naik bertahap tiap 20 detik stabil sampai plafon, lalu diam', () => {
  const a = new AutoPreset();
  a.initial({ ...phone1080, encoder: 'nvenc' }, 0);
  const changes = [];
  for (let t = 1000; t <= 60000; t += 1000) {
    const d = a.update({ ...phone1080, encoder: 'nvenc', ...calm, deliveredFps: a.current.fps }, t);
    if (d) changes.push([t, d.tier]);
  }
  assert.deepEqual(changes, [[20000, 1], [40000, 2], [60000, 3]]);
});

test('turun segera saat kehilangan paket, tidak lebih sering dari 5 detik', () => {
  const a = new AutoPreset();
  a.initial({ ...phone1080, encoder: 'nvenc' }, 0);
  a.update({ ...phone1080, encoder: 'nvenc', ...calm }, 20000);
  a.update({ ...phone1080, encoder: 'nvenc', ...calm, deliveredFps: 60 }, 40000);
  a.update({ ...phone1080, encoder: 'nvenc', ...calm, deliveredFps: 30 }, 60000);
  assert.equal(a.current.tier, 3);
  const d = a.update({ ...phone1080, encoder: 'nvenc', ...calm, recentLossPct: 5 }, 65000);
  assert.equal(d.tier, 2);
  assert.match(d.reason, /kehilangan paket/);
  assert.equal(a.update({ ...phone1080, encoder: 'nvenc', ...calm, recentLossPct: 5 }, 66000), null);
  assert.equal(a.update({ ...phone1080, encoder: 'nvenc', ...calm, recentLossPct: 5 }, 70000).tier, 1);
});

test('dua kali gagal naik = berhenti mencoba (tanpa osilasi)', () => {
  const a = new AutoPreset();
  const hw = { ...phone1080, encoder: 'nvenc' };
  a.initial(hw, 0);
  let t = 0;
  let changes = 0;
  for (; t < 400000; t += 1000) {
    const heavy = a.current.tier >= 2;
    const d = a.update({ ...hw, ...calm, deliveredFps: a.current.fps, recentLossPct: heavy ? 4 : 0 }, t);
    if (d) changes += 1;
  }
  assert.equal(a.current.tier, 1);
  assert.ok(changes <= 5, `perubahan terlalu sering: ${changes}`);
});

test('encoder software: 1080p diturunkan bila latensi pemrosesan tinggi', () => {
  const sw = { ...phone1080, encoder: 'openh264' };
  const a = new AutoPreset();
  a.initial(sw, 0);
  assert.equal(a.update({ ...sw, ...calm }, 20000).tier, 2);
  const d = a.update({ ...sw, ...calm, rttMs: 20, glassMs: 140 }, 25000);
  assert.equal(d.tier, 0);
  assert.match(d.reason, /latensi/);
});

test('decode client lambat = turun, walau jaringan bersih', () => {
  const cur = { tier: 1, resolution: '1080p', fps: 30, reason: '' };
  assert.match(overloaded({ clientLongEdgePx: 2400, rttMs: 40, recentLossPct: 0, jitterBufferMs: 30, decodeMs: 28, deliveredFps: 30 }, cur, 40), /decode/);
  assert.equal(overloaded({ clientLongEdgePx: 2400, rttMs: 40, recentLossPct: 0, jitterBufferMs: 30, decodeMs: 12, deliveredFps: 30 }, cur, 40), null);
});

test('RTT geografis tinggi tapi stabil bukan alasan turun', () => {
  const cur = { resolution: '1080p', fps: 30, tier: 1, reason: '' };
  assert.equal(overloaded({ clientLongEdgePx: 2400, rttMs: 220, recentLossPct: 0, jitterBufferMs: 40, deliveredFps: 30, glassMs: 180 }, cur, 220), null);
  assert.match(overloaded({ clientLongEdgePx: 2400, rttMs: 360, recentLossPct: 0, jitterBufferMs: 40 }, cur, 220), /RTT/);
});

test('plafon turun (encoder berubah) → turun segera', () => {
  const a = new AutoPreset();
  a.initial({ ...phone1080, encoder: 'nvenc' }, 0);
  a.update({ ...phone1080, encoder: 'nvenc', ...calm, deliveredFps: 30 }, 20000);
  a.update({ ...phone1080, encoder: 'nvenc', ...calm, deliveredFps: 60 }, 40000);
  assert.equal(a.current.tier, 2);
  assert.equal(a.update({ ...phone1080, encoder: 'nvenc', ...calm, hostLevel: 31 }, 41000).tier, 1);
});
