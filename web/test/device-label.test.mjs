import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { transformWithOxc } from 'vite';

const source = readFileSync(new URL('../src/device_label.ts', import.meta.url), 'utf8')
  .replace(/^export /gm, '');
let count = 0;
async function fixture(data, run) {
  const { code } = await transformWithOxc(
    source + '\nexports.controllerDisplayLabel = controllerDisplayLabel;',
    `device_label.fixture.${++count}.ts`,
  );
  const exports = {};
  vm.runInNewContext(code, { exports, navigator: { userAgentData: data } });
  await new Promise((resolve) => setImmediate(resolve));
  await run(exports.controllerDisplayLabel);
}

test('optional device model only enriches display name', async () => {
  await fixture({ getHighEntropyValues: async (hints) => { assert.deepEqual(Array.from(hints), ['model']); return { model: '23021RAAEG' }; } }, label => assert.equal(label('Operator'), '23021RAAEG · Operator'));
});

test('unsupported, withheld, generic and rejected metadata keep existing fallback', async () => {
  for (const value of [undefined, { getHighEntropyValues: async () => ({ model: '' }) }, { getHighEntropyValues: async () => ({ model: 'K' }) }, { getHighEntropyValues: async () => { throw Error('blocked'); } }]) await fixture(value, label => assert.equal(label('Operator'), 'Operator'));
});

test('pending model lookup never blocks pairing label and label stays bounded', async () => {
  await fixture({ getHighEntropyValues: () => new Promise(() => {}) }, label => assert.equal(label(undefined), undefined));
  await fixture({ getHighEntropyValues: async () => ({ model: 'Redmi Note 12' }) }, label => assert.equal(label('x'.repeat(80)).length, 48));
});
