import { chromium } from 'playwright-core';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
const browser = await chromium.launch({
  executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  headless: true,
  args: ['--allow-file-access-from-files', '--enable-unsafe-swiftshader',
         '--use-angle=swiftshader', '--disable-gpu-sandbox', '--no-sandbox']
});
const page = await browser.newPage({ viewport: { width: 900, height: 620 }, deviceScaleFactor: 1 });
page.on('pageerror', e => console.log('[err]', e.message));
await page.goto('file:///' + path.join(dir, 'index.html').replace(/\\/g, '/'));
await page.waitForFunction('window.__ready === true', { timeout: 30000 });

const list = await page.evaluate(() => {
  const bg = window.__car.children[0];
  return bg.children.map((ch, i) => {
    const g = ch.geometry;
    g.computeBoundingBox();
    const b = g.boundingBox;
    const f = n => n.toFixed(3);
    return i + ' ' + g.type + ' v=' + g.attributes.position.count +
      ' x[' + f(b.min.x) + ',' + f(b.max.x) + ']' +
      ' y[' + f(b.min.y) + ',' + f(b.max.y) + ']' +
      ' z[' + f(b.min.z) + ',' + f(b.max.z) + ']';
  }).join('\n');
});
console.log(list);
await browser.close();
