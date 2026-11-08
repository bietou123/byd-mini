package com.zjy.bydmini;

import android.graphics.Bitmap;

/**
 * 从边角做区域生长，把与边缘连通的近似色当作背景清成透明。
 * tolerance 为相邻像素允许的色差步长（越大切得越狠）。
 */
final class BackgroundRemover {

    private BackgroundRemover() {
    }

    static Bitmap cut(Bitmap src, int tolerance) {
        int w = src.getWidth();
        int h = src.getHeight();
        int n = w * h;

        int[] px = new int[n];
        src.getPixels(px, 0, w, 0, 0, w, h);

        boolean[] bg = new boolean[n];
        int[] stack = new int[n];
        int sp = 0;
        int tol2 = tolerance * tolerance * 3;

        for (int x = 0; x < w; x++) {
            sp = seed(px, bg, stack, sp, x, tol2);
            sp = seed(px, bg, stack, sp, (h - 1) * w + x, tol2);
        }
        for (int y = 0; y < h; y++) {
            sp = seed(px, bg, stack, sp, y * w, tol2);
            sp = seed(px, bg, stack, sp, y * w + w - 1, tol2);
        }

        while (sp > 0) {
            int i = stack[--sp];
            int x = i % w;
            int y = i / w;
            int c = px[i];
            if (x > 0) sp = push(px, bg, stack, sp, i - 1, c, tol2);
            if (x < w - 1) sp = push(px, bg, stack, sp, i + 1, c, tol2);
            if (y > 0) sp = push(px, bg, stack, sp, i - w, c, tol2);
            if (y < h - 1) sp = push(px, bg, stack, sp, i + w, c, tol2);
        }

        int[] alpha = new int[n];
        for (int i = 0; i < n; i++) alpha[i] = bg[i] ? 0 : 255;

        int[] soft = new int[n];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                int cnt = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        sum += alpha[yy * w + xx];
                        cnt++;
                    }
                }
                soft[y * w + x] = sum / cnt;
            }
        }

        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = (soft[i] << 24) | (px[i] & 0x00FFFFFF);
        }

        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (soft[y * w + x] > 10) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return src;

        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        int[] crop = new int[cw * ch];
        for (int y = 0; y < ch; y++) {
            System.arraycopy(out, (minY + y) * w + minX, crop, y * cw, cw);
        }
        return Bitmap.createBitmap(crop, cw, ch, Bitmap.Config.ARGB_8888);
    }

    private static int seed(int[] px, boolean[] bg, int[] stack, int sp, int i, int tol2) {
        if (bg[i]) return sp;
        bg[i] = true;
        stack[sp++] = i;
        return sp;
    }

    private static int push(int[] px, boolean[] bg, int[] stack, int sp, int i, int c, int tol2) {
        if (bg[i]) return sp;
        if (dist2(px[i], c) > tol2) return sp;
        bg[i] = true;
        stack[sp++] = i;
        return sp;
    }

    private static int dist2(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }
}
