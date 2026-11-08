package com.zjy.bydmini;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int SLOTS = 4;
    private static final int[] CELL_IDS = {R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4};
    private static final int[] ICON_IDS = {
            R.id.cellIcon1, R.id.cellIcon2, R.id.cellIcon3, R.id.cellIcon4};
    private static final int[] LABEL_IDS = {
            R.id.cellLabel1, R.id.cellLabel2, R.id.cellLabel3, R.id.cellLabel4};

    private CarView carView;
    private TextView carName;
    private TextView statusText;
    private View statusDot;
    private TextView batteryText;

    private final View[] cells = new View[SLOTS];
    private final ImageView[] icons = new ImageView[SLOTS];
    private final TextView[] labels = new TextView[SLOTS];
    private final BydActions.Def[] defs = new BydActions.Def[SLOTS];

    private boolean locked = true;
    private boolean shieldAwaitingHide;
    private Bitmap shieldBmp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable fallbackHide = this::hideShieldNow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        carView = findViewById(R.id.carView);
        carName = findViewById(R.id.carName);
        statusText = findViewById(R.id.statusText);
        statusDot = findViewById(R.id.statusDot);
        batteryText = findViewById(R.id.batteryText);

        for (int i = 0; i < SLOTS; i++) {
            cells[i] = findViewById(CELL_IDS[i]);
            icons[i] = findViewById(ICON_IDS[i]);
            labels[i] = findViewById(LABEL_IDS[i]);
            final int slot = i;
            cells[i].setOnClickListener(v -> {
                if (defs[slot] != null) runAction(defs[slot], slot);
            });
            bindPress(cells[i], 0.94f);
        }
        findViewById(R.id.btnSettings).setOnClickListener(v -> openSettings());
        bindPress(findViewById(R.id.btnSettings), 0.9f);

        carName.setOnLongClickListener(v -> {
            onDebug();
            return true;
        });

        SharedPreferences sp = getSharedPreferences(SettingsActivity.PREF, 0);
        locked = sp.getBoolean("locked", true);

        applyHeader();
        applyActions();
        carView.setLocked(locked);
        playEnterAnim();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
        applyHeader();
        applyActions();
        WidgetViews.updateAll(this);
        // 已经回到自己界面，再揭开遮罩，避免官方 App 画面闪现
        if (shieldAwaitingHide) {
            shieldAwaitingHide = false;
            handler.postDelayed(this::hideShieldNow, 320);
        }
    }

    private void applyHeader() {
        SharedPreferences sp = getSharedPreferences(SettingsActivity.PREF, 0);
        carName.setText(sp.getString("name", "海豹 06"));
        batteryText.setText(sp.getString("pct", "82") + "%   ·   续航 "
                + sp.getString("range", "412") + " km");

        Bitmap bmp = SettingsActivity.loadCarBitmap(this);
        carView.setCarBitmap(bmp);
    }

    /** 按用户配置填四个格子；自定义动作改了在 onResume 会重跑一次 */
    private void applyActions() {
        String[] ids = BydActions.slots(this, BydActions.KEY_APP);
        for (int i = 0; i < SLOTS; i++) {
            BydActions.Def d = BydActions.byId(this,
                    BydActions.slotId(ids, BydActions.DEFAULT_APP, i));
            defs[i] = d;
            labels[i].setText(d.label);
            icons[i].setImageResource(d.icon);
            cells[i].setEnabled(true);
            cells[i].setAlpha(1f);
        }
    }

    private void playEnterAnim() {
        View header = findViewById(R.id.headerRow);
        View panel = findViewById(R.id.bottomPanel);

        header.setAlpha(0f);
        header.setTranslationY(-dp(16));
        header.animate().alpha(1f).translationY(0f)
                .setDuration(520).setInterpolator(new DecelerateInterpolator()).start();

        carView.setAlpha(0f);
        carView.setScaleX(0.92f);
        carView.setScaleY(0.92f);
        carView.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(620).setStartDelay(70)
                .setInterpolator(new OvershootInterpolator(0.55f)).start();

        panel.setAlpha(0f);
        panel.setTranslationY(dp(46));
        panel.animate().alpha(1f).translationY(0f)
                .setDuration(560).setStartDelay(150)
                .setInterpolator(new OvershootInterpolator(0.7f)).start();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void bindPress(View v, float to) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(to).scaleY(to)
                            .setDuration(120)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f)
                            .setDuration(320)
                            .setInterpolator(new OvershootInterpolator(2.4f))
                            .start();
                    break;
            }
            return false;
        });
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_fast);
    }

    private void refreshState() {
        boolean acc = BydAccessibilityService.isEnabled(this);
        boolean live = BydAccessibilityService.instance != null;
        boolean app = BydLauncher.isInstalled(this);

        if (!app) {
            statusText.setText("未安装比亚迪汽车");
            statusDot.setBackgroundResource(R.drawable.dot_dim);
            setStatusClick(true);
        } else if (!acc) {
            statusText.setText("无障碍未开启");
            statusDot.setBackgroundResource(R.drawable.dot_dim);
            setStatusClick(true);
        } else if (!live) {
            statusText.setText("无障碍掉线 · 点此重开");
            statusDot.setBackgroundResource(R.drawable.dot_dim);
            setStatusClick(true);
        } else {
            statusText.setText(BydAction.isBusy() ? "执行中" : "就绪");
            statusDot.setBackgroundResource(R.drawable.dot_on);
            setStatusClick(false);
        }
    }

    /** 只有不能干活的时候点状态才跳去无障碍设置，就绪时点了只是刷新 */
    private void setStatusClick(boolean goSettings) {
        View.OnClickListener l = goSettings
                ? v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                : v -> refreshState();
        statusText.setOnClickListener(l);
        statusDot.setOnClickListener(l);
    }

    private boolean isAccessibilityEnabled() {
        return BydAccessibilityService.isEnabled(this);
    }

    private void runAction(BydActions.Def def, int slot) {
        if (!ensureReady()) return;
        if (BydAction.isBusy()) {
            toast("上一个操作还没完成");
            return;
        }
        setBusy(true, slot);
        BydAccessibilityService svc = BydAccessibilityService.instance;
        if (svc != null) svc.showShield(snapshot(), "正在执行 · " + def.label);
        BydAction.run(this, def.texts, (ok, reason, dump) -> {
            BydAccessibilityService s = BydAccessibilityService.instance;
            if (!ok) {
                toastLong(reason);
                if (s != null) s.updateShield("失败 · " + shortReason(reason), false);
            } else {
                if (def.lockState >= 0) {
                    locked = def.lockState == 1;
                    carView.setLocked(locked);
                    getSharedPreferences(SettingsActivity.PREF, 0).edit()
                            .putBoolean("locked", locked).apply();
                }
                if (s != null) s.updateShield("已完成 · " + def.label, true);
            }
            returnToSelf();
            armShieldHide();
            setBusy(false, slot);
        });
    }

    private String shortReason(String reason) {
        if (reason == null || reason.isEmpty()) return "未知原因";
        int cut = reason.indexOf('：');
        String head = cut > 0 ? reason.substring(0, cut) : reason;
        if (head.length() > 12) head = head.substring(0, 12) + "…";
        return head;
    }

    /** 等回到前台再揭遮罩；1800ms 兜底，防止回调丢了遮罩一直盖着 */
    private void armShieldHide() {
        shieldAwaitingHide = true;
        handler.removeCallbacks(fallbackHide);
        handler.postDelayed(fallbackHide, 1800);
    }

    private void hideShieldNow() {
        shieldAwaitingHide = false;
        handler.removeCallbacks(fallbackHide);
        BydAccessibilityService s = BydAccessibilityService.instance;
        if (s != null) s.hideShield();
    }

    /** 把当前界面画成位图，交给无障碍服务当遮罩用；尺寸不变就复用同一张 */
    private Bitmap snapshot() {
        try {
            View root = getWindow().getDecorView();
            int w = root.getWidth();
            int h = root.getHeight();
            if (w <= 0 || h <= 0) return null;
            if (shieldBmp == null || shieldBmp.isRecycled()
                    || shieldBmp.getWidth() != w || shieldBmp.getHeight() != h) {
                shieldBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            Canvas cv = new Canvas(shieldBmp);
            cv.drawColor(getColor(R.color.bg));
            root.draw(cv);
            return shieldBmp;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(fallbackHide);
        super.onDestroy();
    }

    private boolean ensureReady() {
        if (!BydLauncher.isInstalled(this)) {
            toast("没找到比亚迪汽车 App");
            return false;
        }
        if (!isAccessibilityEnabled()) {
            toast("先开启「Car」无障碍服务");
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return false;
        }
        if (BydAccessibilityService.instance == null) {
            toast("无障碍服务掉线了：把「Car」关掉再打开");
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return false;
        }
        return true;
    }

    private void returnToSelf() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void setBusy(boolean busy, int slot) {
        for (int i = 0; i < SLOTS; i++) {
            if (defs[i] == null) continue;
            cells[i].setEnabled(!busy);
            cells[i].setAlpha(busy ? 0.6f : 1f);
        }
        if (slot >= 0 && defs[slot] != null) {
            labels[slot].setText(busy ? "· · ·" : defs[slot].label);
        }
        if (busy) refreshState();
    }

    /** 长按车名：跳出官方 App，把当前界面结构复制到剪贴板，方便排查按钮名字 */
    private void onDebug() {
        UiDump.captureToClipboard(this);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void toastLong(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
