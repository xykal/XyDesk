import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';

const landing = readFileSync(new URL('../src/landing.tsx', import.meta.url), 'utf8');
const css = readFileSync(new URL('../src/style.css', import.meta.url), 'utf8');

const DECO = [
  ['deco-cloud', 'float-cloud.webp'],
  ['deco-sparkle', 'float-sparkle.webp'],
  ['deco-globe', 'float-globe.webp'],
];

test('tiap dekorasi hero memakai berkas aset yang benar-benar ada', () => {
  for (const [kelas, berkas] of DECO) {
    assert.match(landing, new RegExp(`hero-deco ${kelas}" src="/${berkas.replace('.', '\\.')}"`));
    assert.ok(
      existsSync(new URL(`../public/${berkas}`, import.meta.url)),
      `aset ${berkas} hilang dari web/public — hero akan menampilkan gambar rusak`,
    );
  }
});

test('dekorasi tidak dibacakan pembaca layar dan tidak menangkap klik', () => {
  // Dekorasi murni hiasan: alt kosong, induknya aria-hidden, dan
  // pointer-events dimatikan supaya tidak menghalangi tombol unduh.
  const blokHero = landing.slice(landing.indexOf('className="hero-art"'), landing.indexOf('hero-cartoon'));
  assert.match(blokHero, /aria-hidden="true"/);
  for (const [kelas] of DECO) {
    assert.match(blokHero, new RegExp(`hero-deco ${kelas}"[^>]*alt=""`));
  }
  assert.match(css, /\.hero-deco\s*\{[^}]*pointer-events:\s*none/);
});

test('dekorasi tidak menunda hero: hanya kartun yang eager', () => {
  for (const [kelas] of DECO) {
    assert.match(landing, new RegExp(`hero-deco ${kelas}"[^>]*loading="lazy"`));
  }
  assert.match(landing, /hero-cartoon"[^>]*loading="eager"/);
});

test('ketiga dekorasi berayun dengan durasi berbeda', () => {
  const durasi = [...css.matchAll(/animation:\s*hero-deco-drift\s+([\d.]+)s/g)].map((m) => m[1]);
  assert.equal(durasi.length, DECO.length);
  assert.equal(new Set(durasi).size, DECO.length, 'durasi kembar membuat ketiganya berayun serempak');
});

test('dekorasi disembunyikan saat hero menumpuk satu kolom', () => {
  const sempit = css.slice(css.indexOf('@media (max-width: 900px)'));
  assert.match(sempit.slice(0, sempit.indexOf('}\n\n')), /\.hero-deco\s*\{\s*display:\s*none/);
});

test('gaya orb dan kartu melayang lama tidak tertinggal sebagai kode mati', () => {
  for (const mati of ['hero-orb', 'hero-float-card', 'orb-morph', 'card-float-1']) {
    assert.doesNotMatch(css, new RegExp(mati), `${mati} sudah tidak dipakai markup mana pun`);
  }
});
