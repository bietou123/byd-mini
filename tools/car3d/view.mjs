import { chromium } from 'playwright-core';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 用法: node view.mjs out.png [azDeg] [elDeg] [dist] [ty] [fov] [scale]
const dir = path.dirname(fileURLToPath(import.meta.url));
const out = process.argv[2] || 'view.png';
const az = Number(process.argv[3] || 90);      // 从车头算起，90 = 正侧
const el = Number(process.argv[4] || 0);
const dist = Number(process.argv[5] || 1.9);
const ty = Number(process.argv[6] || 0.14);
const fov = Number(process.argv[7] || 20);
const scale = Number(process.argv[8] || 2);

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
await page.waitForFunction('window.__ready === true', { timeout: 30000 });

await page.evaluate(({ az, el, dist, ty, fov }) => {
  const a = az * Math.PI / 180, e = el * Math.PI / 180;
  const cam = window.__cam;
  cam.fov = fov;
  cam.updateProjectionMatrix();
  cam.position.set(-Math.cos(a) * Math.cos(e) * dist,
                  Math.sin(e) * dist + ty,
                  Math.sin(a) * Math.cos(e) * dist);
  cam.lookAt(0, ty, 0);
  window.__car.rotation.y = 0;
  window.__lockCam = true;
}, { az, el, dist, ty, fov });

await page.waitForTimeout(900);
await page.screenshot({ path: path.join(dir, out), omitBackground: true });
await browser.close();
console.log('ok', out);
