// Screenshot tata letak halaman publik di beberapa viewport HP/tablet/desktop.
// Tidak ada sesi/transport; hanya bukti visual bahwa layout tidak pecah.
// Jalankan: node layout_shots.mjs http://127.0.0.1:4173 [dir-keluaran]
import { mkdirSync } from 'node:fs';
import { chromium, devices } from 'playwright';

const base = process.argv[2] ?? 'http://127.0.0.1:4173';
const out = process.argv[3] ?? 'shots';
mkdirSync(out, { recursive: true });

const targets = [
  ['iphone-se', devices['iPhone SE']],
  ['iphone-14', devices['iPhone 14']],
  ['pixel-7', devices['Pixel 7']],
  ['ipad', devices['iPad (gen 7)']],
  ['desktop', { viewport: { width: 1440, height: 900 } }],
];
const pages = [
  ['landing', '/'],
  ['connect', '/connect'],
];

const browser = await chromium.launch();
for (const [name, device] of targets) {
  const ctx = await browser.newContext({ ...device, reducedMotion: 'reduce' });
  const page = await ctx.newPage();
  for (const [label, path] of pages) {
    await page.goto(base + path, { waitUntil: 'networkidle' });
    await page.waitForTimeout(300);
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    if (overflow > 1) throw new Error(`${name} ${path}: overflow horizontal ${overflow}px`);
    await page.screenshot({ path: `${out}/${label}-${name}.png`, fullPage: label === 'landing' });
  }
  await ctx.close();
}
await browser.close();
console.log(`ok: ${targets.length * pages.length} screenshot di ${out}/`);
