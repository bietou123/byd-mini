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
const page = await browser.newPage({ viewport: { width: 900, height: 620 }, deviceScaleFactor: 2 });
page.on('pageerror', e => console.log('[err]', e.message));
await page.goto('file:///' + path.join(dir, 'index.html').replace(/\\/g, '/'));
await page.waitForFunction('window.__ready === true', { timeout: 30000 });
await page.evaluate('window.__car.rotation.y = 0');

const list = await page.evaluate(`(() => {
  const out = [];
  window.__car.children.forEach((ch, i) => {
    const geo = ch.geometry;
    let info = ch.type;
    if (geo) {
      geo.computeBoundingBox();
      const b = geo.boundingBox;
      info = geo.type + ' x[' + b.min.x.toFixed(3) + ',' + b.max.x.toFixed(3) + ']' +
        ' y[' + b.min.y.toFixed(3) + ',' + b.max.y.toFixed(3) + ']' +
        ' z[' + b.min.z.toFixed(3) + ',' + b.max.z.toFixed(3) + ']';
    } else {
      info = 'GROUP(' + ch.children.length + ')';
    }
    out.push(i + ' ' + info);
  });
  return out.join('\\n');
})()`);
console.log(list);
await browser.close();
