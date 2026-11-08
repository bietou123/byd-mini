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
const page = await browser.newPage({ viewport: { width: 900, height: 620 } });
page.on('pageerror', e => console.log('[err]', e.message));
await page.goto('file:///' + path.join(dir, 'index.html').replace(/\\/g, '/'));
await page.waitForFunction('window.__ready === true', { timeout: 30000 });

const out = await page.evaluate(() => {
  const bg = window.__car.children[0];
  const lines = [];
  [1, 2, 3, 4, 5].forEach(i => {
    const ch = bg.children[i];
    if (!ch || !ch.geometry) return;
    const g = ch.geometry;
    const pos = g.attributes.position;
    const parts = ['#' + i + ' ' + g.type + ' groups=' + JSON.stringify(g.groups.map(q => [q.start, q.count, q.materialIndex]))];
    for (const gr of g.groups) {
      const idxArr = g.index.array;
      let ymin = 1e9, ymax = -1e9, xmin = 1e9, xmax = -1e9, zmin = 1e9, zmax = -1e9;
      for (let k = gr.start; k < gr.start + gr.count; k++) {
        const v = idxArr[k];
        const x = pos.getX(v), y = pos.getY(v), z = pos.getZ(v);
        if (y < ymin) ymin = y; if (y > ymax) ymax = y;
        if (x < xmin) xmin = x; if (x > xmax) xmax = x;
        if (z < zmin) zmin = z; if (z > zmax) zmax = z;
      }
      const f = n => n.toFixed(3);
      parts.push('   m' + gr.materialIndex + ' tris=' + (gr.count / 3) +
        ' x[' + f(xmin) + ',' + f(xmax) + '] y[' + f(ymin) + ',' + f(ymax) +
        '] z[' + f(zmin) + ',' + f(zmax) + ']');
    }
    lines.push(parts.join('\n'));
  });
  return lines.join('\n');
});
console.log(out);
await browser.close();
