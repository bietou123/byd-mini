package com.zjy.bydmini;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 拉起官方 App → 等界面出现 → 点目标文本。
 * 主界面和小组件共用同一套超时/重试，避免两边逻辑分叉。
 * 同一时刻只允许一个动作在跑，快速连点会直接被拒绝。
 */
public final class BydAction {

    private static final long POLL_MS = 90;
    /** 找不到按钮时的重试时刻：先给冷启动留足时间，再退一级/重启 */
    private static final long[] RETRY_AT_MS = {6000, 9500};
    private static final long SNAPSHOT_EVERY_MS = 400;
    private static final long TIMEOUT_MS = 13000;

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    public interface Callback {
        void onResult(boolean ok, String reason, String dumpPath);
    }

    private BydAction() {
    }

    public static boolean isBusy() {
        return BUSY.get();
    }

    public static void run(Activity host, String[] texts, Callback cb) {
        if (!BUSY.compareAndSet(false, true)) {
            cb.onResult(false, "上一个操作还没完成", null);
            return;
        }
        if (BydAccessibilityService.instance == null) {
            BUSY.set(false);
            cb.onResult(false, "无障碍服务被系统回收了：设置→无障碍→把「Car」关掉再打开", null);
            return;
        }
        Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            long start;
            long lastSnapshot = -SNAPSHOT_EVERY_MS;
            int backs = 0;
            boolean sawByd = false;
            boolean relaunched = false;
            boolean done = false;

            void finish(boolean ok, String reason, String dump) {
                if (done) return;
                done = true;
                BUSY.set(false);
                cb.onResult(ok, reason, dump);
            }

            @Override
            public void run() {
                if (done) return;
                if (start == 0L) {
                    if (!BydLauncher.launch(host)) {
                        finish(false, "没找到比亚迪汽车 App", null);
                        return;
                    }
                    android.util.Log.i("BydMini", "action begin " + java.util.Arrays.toString(texts));
                    start = SystemClock.uptimeMillis();
                    h.postDelayed(this, POLL_MS);
                    return;
                }

                BydAccessibilityService svc = BydAccessibilityService.instance;
                if (svc != null && svc.clickByText(texts)) {
                    android.util.Log.i("BydMini", "clicked " + java.util.Arrays.toString(texts)
                            + " at " + (SystemClock.uptimeMillis() - start) + "ms");
                    finish(true, null, null);
                    return;
                }
                long el = SystemClock.uptimeMillis() - start;
                if (svc != null && svc.hasBydWindow()) {
                    sawByd = true;
                    if (el - lastSnapshot >= SNAPSHOT_EVERY_MS) {
                        lastSnapshot = el;
                        svc.snapshotTree();
                    }
                }

                if (el >= TIMEOUT_MS || (svc == null && el >= 3000)) {
                    android.util.Log.i("BydMini", "timeout el=" + el + " svc=" + (svc != null)
                            + " sawByd=" + sawByd + " backs=" + backs);
                    String reason;
                    String dump = null;
                    if (svc == null) {
                        reason = "无障碍服务掉线了：设置→无障碍→关掉「Car」再打开";
                    } else if (!sawByd) {
                        reason = "跳转被系统拦住：给「Car」开「后台弹出界面」和「显示在其他应用上层」";
                    } else {
                        dump = svc.dumpTree();
                        reason = "没找到操作按钮，界面结构已保存，可在设置里复制";
                    }
                    finish(false, reason, dump);
                    return;
                }

                // 已经进官方 App 却找不到按钮，多半停在二级页 —— 退一级再找（首次延后，避免打断冷启动）
                // 已经进官方 App 却找不到按钮，多半停在二级页 —— 退一级再找。
                // 第一次延到 6s，避免冷启动还没画完就按后退把 App 直接退掉。
                if (sawByd && backs < RETRY_AT_MS.length && el > RETRY_AT_MS[backs]) {
                    boolean has = svc.hasBydWindow();
                    android.util.Log.i("BydMini", "retry#" + backs + " el=" + el
                            + " hasByd=" + has + " relaunched=" + relaunched);
                    if (has) {
                        svc.back();
                    } else if (!relaunched) {
                        BydLauncher.launch(host);
                        relaunched = true;
                    }
                    backs++;
                }
                h.postDelayed(this, POLL_MS);
            }
        });
    }
}
