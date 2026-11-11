import { chromium } from 'playwright';
const base = 'http://localhost:4173';
const sizes = [
  ['hp', 360, 800],
  ['tab', 768, 1024],
  ['laptop', 1440, 900],
];
const routes = [['landing', '/'], ['download', '/download'], ['connect', '/connect']];
const browser = await chromium.launch();
for (const [sn, w, h] of sizes) {
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  for (const [rn, path] of routes) {
    await page.goto(base + path, { waitUntil: 'networkidle' }).catch(() => {});
    await page.waitForTimeout(600);
    await page.screenshot({ path: `/home/user/shot-${sn}-${rn}.png`, fullPage: true });
    console.log('shot', sn, rn);
  }
  await page.close();
}
await browser.close();
