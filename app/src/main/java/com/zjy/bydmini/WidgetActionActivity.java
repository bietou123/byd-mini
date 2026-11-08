package com.zjy.bydmini;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class WidgetActionActivity extends Activity {

    static final String EXTRA = "act";

    private boolean hideScheduled;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        String act = getIntent().getStringExtra(EXTRA);
        if (act == null || act.isEmpty()) {
            finish();
            return;
        }
        BydActions.Def def = BydActions.byId(this, act);
        if (!BydLauncher.isInstalled(this)) {
            toast("没找到比亚迪汽车 App");
            finish();
            return;
        }
        if (!BydAccessibilityService.isEnabled(this)) {
            toast("先开启「Car」无障碍服务");
            finish();
            return;
        }

        BydAccessibilityService svc = BydAccessibilityService.instance;
        if (svc == null) {
            toast("无障碍服务掉线了：把「Car」关掉再打开");
            finish();
            return;
        }
        // 小组件是透明窗口，没有可截图的内容，直接铺底色盖住跳转过程
        svc.showShield(null, "正在执行 · " + def.label);

        BydAction.run(this, def.texts, (ok, reason, dump) -> {
            BydAccessibilityService s = BydAccessibilityService.instance;
            if (!ok) toast(reason);
            if (s != null) {
                s.updateShield(ok ? "已完成 · " + def.label : "失败 · " + head(reason), ok);
            }
            backHome();
            hideScheduled = true;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                BydAccessibilityService h = BydAccessibilityService.instance;
                if (h != null) h.hideShield();
            }, 650);
            finish();
        });
    }

    private static String head(String reason) {
        if (reason == null || reason.isEmpty()) return "未完成";
        int cut = reason.indexOf('：');
        String s = cut > 0 ? reason.substring(0, cut) : reason;
        return s.length() > 12 ? s.substring(0, 12) + "…" : s;
    }

    /** 做完直接回桌面，不让用户停在官方 App 里 */
    private void backHome() {
        BydAccessibilityService svc = BydAccessibilityService.instance;
        if (svc != null && svc.home()) return;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        // 正常流程会把隐藏交给延迟任务，只有异常退出才在这里立刻收掉遮罩
        if (!hideScheduled) {
            BydAccessibilityService s = BydAccessibilityService.instance;
            if (s != null) s.hideShield();
        }
        super.onDestroy();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
