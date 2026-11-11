// Cek overflow horizontal: documentElement.scrollWidth harus <= viewport.
import { chromium } from 'playwright';

const widths = [360, 768, 1440];
const routes = ['/', '/download', '/connect'];
const browser = await chromium.launch();
let bad = 0;
for (const w of widths) {
  const ctx = await browser.newContext({ viewport: { width: w, height: 900 } });
  const page = await ctx.newPage();
  for (const r of routes) {
    await page.goto('http://localhost:4173' + r, { waitUntil: 'networkidle' });
    const m = await page.evaluate(() => ({
      sw: document.documentElement.scrollWidth,
      iw: window.innerWidth,
    }));
    const ok = m.sw <= m.iw + 1;
    if (!ok) bad++;
    console.log(`${ok ? 'OK ' : 'OVERFLOW'} ${w}px ${r} scrollWidth=${m.sw} inner=${m.iw}`);
  }
  await ctx.close();
}
await browser.close();
process.exit(bad ? 1 : 0);
