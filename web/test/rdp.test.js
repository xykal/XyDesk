// Tes panel Koneksi RDP — validasi isian + berkas .rdp fallback.
// Pola: transform TypeScript memakai compiler yang sama dengan build web
// (transformWithOxc), lalu jalankan fungsi murni di vm — tanpa React.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { transformWithOxc } from 'vite';

const source = readFileSync(new URL('../src/rdp_panel.tsx', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '')
  .replace(/^export /gm, '');
const { code } = await transformWithOxc(
  source + '\nexports.validateRdpForm = validateRdpForm;exports.buildRdpFile = buildRdpFile;',
  'rdp_panel.tsx',
);
const exports = {};
// Stub runtime JSX: fungsi murni yang dites tidak pernah merender elemen.
const requireStub = () => ({ jsx: () => null, jsxs: () => null, Fragment: Symbol('frag') });
vm.runInNewContext(code, { exports, require: requireStub, React: {}, useState: () => {}, document: {} });
const { validateRdpForm, buildRdpFile } = exports;

test('form RDP menolak host yang tidak sah', () => {
  const base = { host: '192.168.1.10', port: '3389', user: 'xyadmin', pass: 'rahasia' };
  assert.equal(validateRdpForm(base), null);
  assert.ok(validateRdpForm({ ...base, host: 'host;shutdown' }));
  assert.ok(validateRdpForm({ ...base, host: 'a..b' }));
  assert.ok(validateRdpForm({ ...base, host: '-evil' }));
  assert.ok(validateRdpForm({ ...base, host: '' }));
  assert.ok(validateRdpForm({ ...base, host: 'host&whoami' }));
});

test('form RDP memvalidasi port, user, dan panjang sandi', () => {
  const base = { host: 'pc.local', port: '3389', user: 'KANTOR\\xyadmin', pass: 'x' };
  assert.equal(validateRdpForm(base), null);
  assert.ok(validateRdpForm({ ...base, port: '0' }));
  assert.ok(validateRdpForm({ ...base, port: '70000' }));
  assert.ok(validateRdpForm({ ...base, port: 'abc' }));
  assert.ok(validateRdpForm({ ...base, user: 'user|whoami' }));
  assert.ok(validateRdpForm({ ...base, user: '' }));
  assert.ok(validateRdpForm({ ...base, pass: 'x'.repeat(600) }));
  assert.equal(validateRdpForm({ ...base, user: 'user@domain.id' }), null);
});

test('berkas .rdp fallback tidak pernah memuat kata sandi', () => {
  const body = buildRdpFile({ host: 'pc.local', port: 3390, user: 'xyadmin', width: 1600, height: 900 });
  assert.ok(body.includes('full address:s:pc.local:3390'));
  assert.ok(body.includes('desktopwidth:i:1600'));
  assert.ok(!/password/i.test(body));
  assert.ok(body.includes('prompt for credentials:i:1'));
});
