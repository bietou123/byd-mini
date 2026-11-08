package com.zjy.bydmini;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/** 抓一份官方 App 的界面结构丢进剪贴板，按钮文字对不上时用来排查 */
final class UiDump {

    private static final long DELAY_MS = 2000;

    private UiDump() {
    }

    static void captureToClipboard(Activity a) {
        if (!BydLauncher.isInstalled(a)) {
            shortToast(a, "没找到比亚迪汽车 App");
            return;
        }
        if (BydAccessibilityService.instance == null) {
            shortToast(a, "无障碍服务未连接，先去开启");
            return;
        }
        shortToast(a, "切到车控页，2 秒后抓取界面");
        BydLauncher.launch(a);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            BydAccessibilityService svc = BydAccessibilityService.instance;
            if (svc == null) return;
            String path = svc.dumpTree();
            if (path.startsWith("ERR") || path.startsWith("NO_WINDOW")) {
                shortToast(a, path);
                return;
            }
            String content = readText(new File(path));
            if (content == null || content.isEmpty()) {
                shortToast(a, "导出失败：文件为空");
                return;
            }
            ClipboardManager cm = a.getSystemService(ClipboardManager.class);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Car UI", content));
            }
            Toast.makeText(a, "界面结构已复制（" + content.length() + " 字符），可直接发给 AI",
                    Toast.LENGTH_LONG).show();
        }, DELAY_MS);
    }

    private static String readText(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } catch (Exception e) {
            return null;
        }
        return sb.toString();
    }

    private static void shortToast(Activity a, String s) {
        Toast.makeText(a, s, Toast.LENGTH_SHORT).show();
    }
}
