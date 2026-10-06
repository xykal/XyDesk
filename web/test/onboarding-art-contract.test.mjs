import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';

const landing = readFileSync(new URL('../src/landing.tsx', import.meta.url), 'utf8');
const connect = readFileSync(new URL('../src/connect_screen.tsx', import.meta.url), 'utf8');
const css = readFileSync(new URL('../src/style.css', import.meta.url), 'utf8');

// Pemetaan mengikuti docs/ILUSTRASI-ASSETS.md: kontrol -> cursor,
// keamanan -> shield, low-latency -> bolt, klien browser/HP -> phone.
const KARTU = [
  ['01', 'float-cursor'],
  ['02', 'float-shield'],
  ['03', 'float-bolt'],
  ['04', 'float-phone'],
];

test('tiap kartu fitur memakai ilustrasi yang sesuai isinya', () => {
  for (const [index, aset] of KARTU) {
    assert.match(landing, new RegExp(`index="${index}" art="${aset}"`));
  }
});

test('semua aset yang dirujuk benar-benar ada di public/', () => {
  for (const [, aset] of [...KARTU, ['otp', 'float-mail']]) {
    assert.ok(
      existsSync(new URL(`../public/${aset}.webp`, import.meta.url)),
      `${aset}.webp hilang — kartu akan menampilkan gambar rusak`,
    );
  }
});

test('ilustrasi kartu tidak dibacakan pembaca layar dan tidak menangkap klik', () => {
  assert.match(landing, /className="feature-art"[^>]*alt=""[^>]*aria-hidden="true"/);
  assert.match(css, /\.feature-art\s*\{[^}]*pointer-events:\s*none/);
});

test('ilustrasi kartu tidak menutupi teks: opasitas rendah dan di belakang', () => {
  const blok = css.slice(css.indexOf('.feature-art {'));
  const aturan = blok.slice(0, blok.indexOf('}'));
  const opacity = Number(aturan.match(/opacity:\s*([\d.]+)/)[1]);
  assert.ok(opacity <= 0.3, `opacity ${opacity} terlalu pekat untuk latar kartu`);
  assert.match(aturan, /position:\s*absolute/);
  assert.match(css, /\.feature-card\s*\{[^}]*overflow:\s*hidden/);
});

test('layar OTP memakai ilustrasi amplop', () => {
  const blokOtp = connect.slice(connect.indexOf('Enam digit dikirim ke') - 400, connect.indexOf('Enam digit dikirim ke'));
  assert.match(blokOtp, /src="\/float-mail\.webp"/);
  assert.match(blokOtp, /alt=""/);
});

test('tidak ada ilustrasi yang ikut memperlambat muatan pertama', () => {
  for (const berkas of [landing, connect]) {
    for (const m of berkas.matchAll(/<img[^>]*float-[^>]*>/g)) {
      assert.match(m[0], /loading="lazy"/, `ilustrasi tanpa lazy: ${m[0].slice(0, 70)}`);
    }
  }
});
