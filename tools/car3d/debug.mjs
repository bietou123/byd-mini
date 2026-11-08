import { chromium } from 'playwright-core';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 用法: node debug.mjs out.png "<js>" [az] [el] [dist] [ty] [fov] [scale]
// <js> 在页面上下文执行，可用 window.__car / __cam / __scene
const dir = path.dirname(fileURLToPath(import.meta.url));
const out = process.argv[2] || 'dbg.png';
const js = process.argv[3] || '';
const az = Number(process.argv[4] || 90);
const el = Number(process.argv[5] || 0);
const dist = Number(process.argv[6] || 2.95);
const ty = Number(process.argv[7] || 0.16);
const fov = Number(process.argv[8] || 14);
const scale = Number(process.argv[9] || 2);
const tx = Number(process.argv[10] || 0);

const browser = await chromium.launch({
  executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  headless: true,
  args: ['--allow-file-access-from-files', '--enable-unsafe-swiftshader',
         '--use-angle=swiftshader', '--disable-gpu-sandbox', '--no-sandbox']
});
const page = await browser.newPage({ viewport: { width: 900, height: 620 }, deviceScaleFactor: scale });
page.on('pageerror', e => console.log('[err]', e.message));
await page.goto('file:///' + path.join(dir, 'index.html').replace(/\\/g, '/'));
await page.waitForFunction('window.__ready === true', { timeout: 30000 });

const info = await page.evaluate(new Function('return (function(){' + js + '})()'));
if (info !== undefined) console.log('[js]', JSON.stringify(info));

await page.evaluate(({ az, el, dist, ty, fov, tx }) => {
  const a = az * Math.PI / 180, e = el * Math.PI / 180;
  const cam = window.__cam;
  cam.fov = fov; cam.updateProjectionMatrix();
  cam.position.set(tx - Math.cos(a) * Math.cos(e) * dist, Math.sin(e) * dist + ty,
    Math.sin(a) * Math.cos(e) * dist);
  cam.lookAt(tx, ty, 0);
  window.__car.rotation.y = 0;
  window.__lockCam = true;
}, { az, el, dist, ty, fov, tx });

await page.waitForTimeout(900);
await page.screenshot({ path: path.join(dir, out), omitBackground: true });
await browser.close();
console.log('ok', out);
