import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
import {transformWithOxc} from 'vite';
const site=readFileSync(new URL('../src/site_routes.ts',import.meta.url),'utf8').replace(/^export /gm,'');
const edge=readFileSync(new URL('../../web_deploy/worker/domain_routes.js',import.meta.url),'utf8').replace(/^export /gm,'');
const {code}=await transformWithOxc(site+'\n'+edge+'\nexports.api={routeHref,pageRedirect};','routes.ts');
const exports={};vm.runInNewContext(code,{exports,URL});const {routeHref,pageRedirect}=exports.api;
test('public pages and remote routes use separate canonical origins',()=>{assert.equal(routeHref('/news','remote.xydesk.my.id'),'https://www.xydesk.my.id/news');assert.equal(routeHref('/session/123456789','www.xydesk.my.id'),'https://remote.xydesk.my.id/session/123456789');assert.equal(routeHref('/devices','localhost'),'/devices');});
test('legacy host redirects remote pages but homepage becomes public',()=>{assert.equal(pageRedirect(new URL('https://app.xydesk.my.id/history')),'https://remote.xydesk.my.id/history');assert.equal(pageRedirect(new URL('https://app.xydesk.my.id/')),'https://www.xydesk.my.id/');});
test('OAuth callbacks remain on their nonce origin and assets are not redirected',()=>{for(const host of ['www','remote']){assert.equal(pageRedirect(new URL(`https://${host}.xydesk.my.id/auth/callback`)),null);assert.equal(pageRedirect(new URL(`https://${host}.xydesk.my.id/assets/main.js`)),null);}assert.equal(pageRedirect(new URL('https://remote.xydesk.my.id/')),null);});
test('remote HTTP becomes HTTPS without changing session address',()=>{assert.equal(pageRedirect(new URL('http://remote.xydesk.my.id/session/123456789#session/'+'a'.repeat(64))),'https://remote.xydesk.my.id/session/123456789#session/'+'a'.repeat(64));});

test('static asset hits must run Worker routing first in production', () => {
  const config = readFileSync(new URL('../../web_deploy/wrangler.toml', import.meta.url), 'utf8');
  const assets = config.split(/^\[assets\]\s*$/m)[1]?.split(/^\[/m)[0];
  assert.ok(assets);
  assert.match(assets, /^run_worker_first\s*=\s*true\s*$/m,
    'asset-first bypasses root redirects and remote robots/no-store');
});

const workerSource = readFileSync(new URL('../../web_deploy/worker/index.js', import.meta.url), 'utf8')
  .replace(/^import .*domain_routes.*;\s*$/m, '')
  .replace('export default {', 'exports.worker = {');
const workerExports = {};
vm.runInNewContext(edge + '\n' + workerSource, {
  exports: workerExports, URL, Request, Response, Headers,
});
const assetEnv = {ASSETS: {fetch: async () => new Response('static fixture', {
  headers: {'content-type': 'text/html', 'cache-control': 'public, max-age=600'},
})}};
test('Worker applies root legacy redirect even when an index asset exists', async () => {
  const response = await workerExports.worker.fetch(new Request('https://app.xydesk.my.id/'), assetEnv);
  assert.equal(response.status, 308);
  assert.equal(response.headers.get('location'), 'https://www.xydesk.my.id/');
});
test('remote root is no-store/noindex and robots blocks crawling', async () => {
  const root = await workerExports.worker.fetch(new Request('https://remote.xydesk.my.id/'), assetEnv);
  assert.equal(root.headers.get('cache-control'), 'no-store');
  assert.equal(root.headers.get('x-robots-tag'), 'noindex, nofollow');
  const robots = await workerExports.worker.fetch(new Request('https://remote.xydesk.my.id/robots.txt'), assetEnv);
  assert.equal(await robots.text(), 'User-agent: *\nDisallow: /\n');
});
