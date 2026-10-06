import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';

const extras = readFileSync(new URL('../src/connect_extras.tsx', import.meta.url), 'utf8');
const css = readFileSync(new URL('../src/style.css', import.meta.url), 'utf8');

test('ilustrasi panduan ikut berganti mengikuti tab client/host', () => {
  // Kalau ilustrasinya statis, ia kehilangan maknanya: pembaca tidak lagi
  // mendapat penegasan sisi mana yang sedang dibuka.
  assert.match(extras, /side === 'client' \? '\/float-phone\.webp' : '\/float-monitor\.webp'/);
});

test('kedua aset panduan ada di public/', () => {
  for (const aset of ['float-phone.webp', 'float-monitor.webp']) {
    assert.ok(existsSync(new URL(`../public/${aset}`, import.meta.url)), `${aset} hilang`);
  }
});

test('ilustrasi panduan tidak dibacakan dan tidak menangkap klik', () => {
  const tag = extras.match(/<img\s+className="guide-art"[\s\S]*?\/>/)[0];
  assert.match(tag, /alt=""/);
  assert.match(tag, /aria-hidden="true"/);
  assert.match(tag, /loading="lazy"/);
  assert.match(css, /\.guide-art\s*\{[^}]*pointer-events:\s*none/);
});

test('judul dan intro panduan punya ruang supaya tidak tertimpa ilustrasi', () => {
  // Ilustrasinya absolut, jadi teks tidak otomatis menghindarinya.
  assert.match(css, /\.connect-guide h2,\s*\.connect-guide > p\.muted \{[\s\S]*?padding-right:\s*110px/);
  // Ada beberapa blok 640px; ambil yang benar-benar mengatur panduan.
  const mulai = css.indexOf('@media (max-width: 640px)', css.indexOf('.guide-art {'));
  const sempit = css.slice(mulai);
  const blok = sempit.slice(0, sempit.indexOf('}\n\n'));
  assert.match(blok, /\.guide-art\s*\{[^}]*width:\s*64px/);
  assert.match(blok, /padding-right:\s*78px/);
});

test('ilustrasi panduan lebih kecil daripada ruang yang disediakan', () => {
  const lebar = Number(css.match(/\.guide-art\s*\{[^}]*width:\s*(\d+)px/)[1]);
  const ruang = Number(css.match(/\.connect-guide h2,[\s\S]*?padding-right:\s*(\d+)px/)[1]);
  assert.ok(lebar < ruang, `ilustrasi ${lebar}px tidak muat di ruang ${ruang}px`);
});
