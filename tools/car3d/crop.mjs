import { chromium } from 'playwright-core';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
const out = process.argv[2] || 'crop.png';
const x = Number(process.argv[3] || 0);
const y = Number(process.argv[4] || 0);
const w = Number(process.argv[5] || 400);
const h = Number(process.argv[6] || 300);
const scale = Number(process.argv[7] || 3);

const browser = await chromium.launch({
  executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  headless: true,
  args: ['--allow-file-access-from-files', '--enable-unsafe-swiftshader',
         '--use-angle=swiftshader', '--disable-gpu-sandbox', '--no-sandbox']
});
const page = await browser.newPage({
  viewport: { width: 900, height: 620 }, deviceScaleFactor: scale
});
page.on('pageerror', e => console.log('[err]', e.message));
await page.goto('file:///' + path.join(dir, 'index.html').replace(/\\/g, '/'));
await page.waitForFunction('window.__ready === true', { timeout: 30000 }).catch(() => {});
await page.waitForTimeout(1200);
await page.screenshot({ path: path.join(dir, out), omitBackground: true,
  clip: { x, y, width: w, height: h } });
await browser.close();
console.log('ok', out);
