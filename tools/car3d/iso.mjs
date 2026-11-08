import { chromium } from 'playwright-core';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 用法: node iso.mjs out.png "0,1"   —— 隐藏 car.children 或 bodyGroup.children 里的索引
const dir = path.dirname(fileURLToPath(import.meta.url));
const out = process.argv[2] || 'iso.png';
const spec = (process.argv[3] || '').trim();
const clip = (process.argv[4] || '').trim();

const browser = await chromium.launch({
  executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  headless: true,
  args: ['--allow-file-access-from-files', '--enable-unsafe-swiftshader',
         '--use-angle=swiftshader', '--disable-gpu-sandbox', '--no-sandbox']
});
const page = await browser.newPage({
  viewport: { width: 900, height: 620 }, deviceScaleFactor: clip ? 3 : 1
});
page.on('pageerror', e => console.log('[err]', e.message));
await page.goto('file:///' + path.join(dir, 'index.html').replace(/\\/g, '/'));
await page.waitForFunction('window.__ready === true', { timeout: 30000 });

await page.evaluate(s => {
  const bg = window.__car.children[0];
  for (const part of s.split(';')) {
    const [sel, idxs] = part.split(':');
    const parent = sel === 'bg' ? bg : window.__car;
    for (const i of (idxs || '').split(',').filter(Boolean)) {
      const ch = parent.children[Number(i)];
      if (ch) ch.visible = false;
    }
  }
  window.__car.rotation.y = 0;
}, spec);

await page.waitForTimeout(900);
const opts = { path: path.join(dir, out), omitBackground: true };
if (clip) {
  const [x, y, w, h] = clip.split(',').map(Number);
  opts.clip = { x, y, width: w, height: h };
}
await page.screenshot(opts);
await browser.close();
console.log('ok', out);
