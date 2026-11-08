package com.zjy.bydmini;

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class BydAccessibilityService extends AccessibilityService {

    private static final String TAG = "BydMini";
    public static volatile BydAccessibilityService instance;

    private FrameLayout shieldRoot;
    private TextView shieldText;
    private GradientDrawable shieldDotBg;
    private ValueAnimator shieldPulse;
    private WindowManager shieldWm;

    @Override
    public void onServiceConnected() {
        instance = this;
        connectedAt = System.currentTimeMillis();
        Log.i(TAG, "accessibility connected");
    }

    /**
     * 国产 ROM 的省电策略会隔一阵子解绑无障碍服务，导致「用一会儿就失效」。
     * 返回 true 且不调 super，系统会保留绑定关系，下次不必重新去设置里打开。
     */
    @Override
    public boolean onUnbind(Intent intent) {
        Log.w(TAG, "accessibility unbound, keeping binding");
        instance = null;
        return true;
    }

    /** 服务被系统重新拉起后仍可用；连接时间用于界面提示 */
    private static volatile long connectedAt;
    static long connectedAt() { return connectedAt; }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        hideShieldNow();
        instance = null;
        super.onDestroy();
    }

    /**
     * 把当前画面盖住，让跳官方 App 的过程完全看不见。
     * label 是顶部胶囊的文案，比如「正在执行 · 解锁」。
     */
    public void showShield(Bitmap snap, String label) {
        hideShieldNow();
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(getColor(R.color.bg));

            if (snap != null && !snap.isRecycled()) {
                ImageView img = new ImageView(this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                img.setImageBitmap(snap);
                root.addView(img, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            }

            TextView text = new TextView(this);
            text.setText(label == null ? "正在执行" : label);
            text.setTextColor(Color.WHITE);
            text.setTextSize(12.5f);
            text.setLetterSpacing(0.03f);

            View dot = new View(this);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(7), dp(7));
            dotLp.rightMargin = dp(8);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(0xFF3482FF);
            dot.setBackground(dotBg);

            LinearLayout pill = new LinearLayout(this);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(dp(15), dp(9), dp(18), dp(9));
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(0xE6101014);
            pillBg.setCornerRadius(dp(30));
            pill.setBackground(pillBg);
            pill.addView(dot, dotLp);
            pill.addView(text);

            FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            pillLp.topMargin = dp(46);
            root.addView(pill, pillLp);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            wm.addView(root, lp);

            shieldRoot = root;
            shieldText = text;
            shieldDotBg = dotBg;
            shieldWm = wm;
            startPulse(dot);
        } catch (Exception e) {
            Log.w(TAG, "shield failed", e);
        }
    }

    /** 执行结果回写到遮罩上：成功圆点变绿，失败变红并停下呼吸 */
    public void updateShield(String label, boolean ok) {
        if (shieldText != null && label != null) shieldText.setText(label);
        if (shieldDotBg != null) {
            shieldDotBg.setColor(ok ? 0xFF34C759 : 0xFFFF453A);
        }
        if (shieldPulse != null) {
            shieldPulse.cancel();
            shieldPulse = null;
        }
        if (shieldRoot != null) shieldRoot.animate().alpha(1f).setDuration(80).start();
    }

    public void hideShield() {
        if (shieldRoot == null) return;
        FrameLayout v = shieldRoot;
        WindowManager wm = shieldWm;
        shieldRoot = null;
        shieldWm = null;
        shieldText = null;
        shieldDotBg = null;
        if (shieldPulse != null) {
            shieldPulse.cancel();
            shieldPulse = null;
        }
        if (wm == null) return;
        v.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            try {
                wm.removeViewImmediate(v);
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void hideShieldNow() {
        if (shieldPulse != null) {
            shieldPulse.cancel();
            shieldPulse = null;
        }
        FrameLayout v = shieldRoot;
        WindowManager wm = shieldWm;
        shieldRoot = null;
        shieldWm = null;
        shieldText = null;
        shieldDotBg = null;
        if (v == null || wm == null) return;
        try {
            wm.removeViewImmediate(v);
        } catch (Exception ignored) {
        }
    }

    private void startPulse(View dot) {
        shieldPulse = ValueAnimator.ofFloat(0.35f, 1f);
        shieldPulse.setDuration(520);
        shieldPulse.setRepeatMode(ValueAnimator.REVERSE);
        shieldPulse.setRepeatCount(ValueAnimator.INFINITE);
        shieldPulse.setInterpolator(new LinearInterpolator());
        shieldPulse.addUpdateListener(a -> dot.setAlpha((float) a.getAnimatedValue()));
        shieldPulse.start();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /**
     * 点官方 App 里的按钮。
     * 先找文本/描述完全相等的节点，再退回包含匹配；
     * 「已解锁 / 未上锁 / 解锁中」这类状态文字不算按钮，避免点到状态卡片。
     */
    public boolean clickByText(String... candidates) {
        AccessibilityNodeInfo root = bydRoot();
        if (root == null) return false;
        List<AccessibilityNodeInfo> exact = new ArrayList<>();
        List<AccessibilityNodeInfo> loose = new ArrayList<>();
        collect(root, candidates, exact, loose);
        for (AccessibilityNodeInfo n : exact) {
            if (performClick(n)) return true;
        }
        for (AccessibilityNodeInfo n : loose) {
            if (performClick(n)) return true;
        }
        return false;
    }

    private void collect(AccessibilityNodeInfo n, String[] cands,
                         List<AccessibilityNodeInfo> exact,
                         List<AccessibilityNodeInfo> loose) {
        if (n == null || exact.size() + loose.size() > 40) return;
        String text = str(n.getText());
        String desc = str(n.getContentDescription());
        for (String c : cands) {
            if (c == null) continue;
            String cand = c.trim();
            if (cand.isEmpty()) continue;
            if (cand.equals(text) || cand.equals(desc)) {
                if (n.isVisibleToUser() && isActionable(n)) exact.add(n);
                break;
            }
            if (isStateText(text, cand) || isStateText(desc, cand)) continue;
            if (text.contains(cand) || desc.contains(cand)) {
                if (n.isVisibleToUser() && isActionable(n)) loose.add(n);
                break;
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            collect(n.getChild(i), cands, exact, loose);
        }
    }

    private static String str(CharSequence s) {
        return s == null ? "" : s.toString().trim();
    }

    /** 已锁车 / 未解锁 / 解锁中 这类是状态展示，不是操作按钮 */
    private static boolean isStateText(String s, String cand) {
        if (s == null || s.isEmpty() || !s.contains(cand)) return false;
        return s.startsWith("已") || s.startsWith("未") || s.endsWith("中");
    }

    private boolean isActionable(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        int depth = 0;
        while (cur != null && depth < 8) {
            if (cur.isClickable() && cur.isEnabled()) return true;
            cur = cur.getParent();
            depth++;
        }
        return false;
    }

    public String foregroundPackage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && isByd(root)) return BydLauncher.PKG;
        return hasBydWindow() ? BydLauncher.PKG : null;
    }

    public boolean hasBydWindow() {
        boolean has = false;
        List<AccessibilityWindowInfo> wins = getWindows();
        if (wins != null) {
            for (AccessibilityWindowInfo w : wins) {
                if (isByd(w.getRoot())) {
                    has = true;
                    break;
                }
            }
        }
        if (!has) has = isByd(getRootInActiveWindow());
        if (lastHasByd == null || lastHasByd != has) {
            lastHasByd = has;
            Log.i(TAG, "hasBydWindow " + has + " " + diag());
        }
        return has;
    }

    private Boolean lastHasByd;
    private String lastTree;
    private long lastTreeAt;

    /**
     * 平时轮询时顺手留一份界面快照。
     * 超时那一刻官方 App 可能刚好被 back 掉或正在重启、窗口读不到，
     * 这时用几百毫秒前的快照兜底，保证「界面结构已保存」不是空话。
     */
    public void snapshotTree() {
        AccessibilityNodeInfo root = bydRoot();
        if (root == null) return;
        StringBuilder sb = new StringBuilder();
        walk(root, 0, sb);
        lastTree = sb.toString();
        lastTreeAt = System.currentTimeMillis();
    }

    /** 诊断用：把当前可见窗口和活动窗口的包名摊平 */
    private String diag() {
        StringBuilder sb = new StringBuilder();
        try {
            List<AccessibilityWindowInfo> wins = getWindows();
            sb.append("wins=").append(wins == null ? "null" : wins.size());
            if (wins != null) {
                for (AccessibilityWindowInfo w : wins) {
                    AccessibilityNodeInfo r;
                    try {
                        r = w.getRoot();
                    } catch (Throwable t) {
                        sb.append(" |t").append(w.getType()).append(":EX");
                        continue;
                    }
                    sb.append(" |t").append(w.getType()).append(':')
                            .append(r == null ? "null" : String.valueOf(r.getPackageName()));
                }
            }
            AccessibilityNodeInfo a = getRootInActiveWindow();
            sb.append(" |active=").append(a == null ? "null" : String.valueOf(a.getPackageName()));
        } catch (Throwable t) {
            sb.append(" EX ").append(t);
        }
        return sb.toString();
    }

    /** 只在官方 App 的窗口里找控件，避免点到遮罩或自己界面上的同名文字 */
    private AccessibilityNodeInfo bydRoot() {
        List<AccessibilityWindowInfo> wins = getWindows();
        if (wins != null) {
            for (AccessibilityWindowInfo w : wins) {
                AccessibilityNodeInfo r = w.getRoot();
                if (isByd(r)) return r;
            }
        }
        AccessibilityNodeInfo r = getRootInActiveWindow();
        return isByd(r) ? r : null;
    }

    private static boolean isByd(AccessibilityNodeInfo n) {
        if (n == null) return false;
        CharSequence p = n.getPackageName();
        return p != null && BydLauncher.PKG.contentEquals(p);
    }

    public boolean back() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public boolean home() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public static boolean isEnabled(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String full = c.getPackageName() + "/" + BydAccessibilityService.class.getName();
        String shortName = c.getPackageName() + "/."
                + BydAccessibilityService.class.getSimpleName();
        return enabled.contains(full) || enabled.contains(shortName);
    }

    private boolean performClick(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        int depth = 0;
        while (cur != null && depth < 10) {
            if (cur.isClickable() && cur.isEnabled()) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            cur = cur.getParent();
            depth++;
        }
        return false;
    }

    /** 导出官方 App 当前界面结构，存在应用私有目录，设置页可以一键复制 */
    public String dumpTree() {
        AccessibilityNodeInfo root = bydRoot();
        StringBuilder sb = new StringBuilder();
        if (root != null) {
            walk(root, 0, sb);
        } else if (lastTree != null) {
            sb.append("# 超时瞬间读不到官方窗口，以下是 ")
                    .append(System.currentTimeMillis() - lastTreeAt).append("ms 前的界面快照\n");
            sb.append(lastTree);
            Log.i(TAG, "dumpTree 用快照兜底 ageMs="
                    + (System.currentTimeMillis() - lastTreeAt));
        } else {
            Log.w(TAG, "dumpTree NO_WINDOW " + diag());
            return "NO_WINDOW";
        }
        try {
            File f = new File(getFilesDir(), "ui_dump.txt");
            Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            w.write(sb.toString());
            w.close();
            Log.i(TAG, "dumpTree ok -> " + f.getAbsolutePath() + " lines=" + sb.length());
            return f.getAbsolutePath();
        } catch (Exception e) {
            Log.w(TAG, "dumpTree ERR " + e);
            return "ERR " + e;
        }
    }

    private void walk(AccessibilityNodeInfo n, int depth, StringBuilder sb) {
        if (n == null) return;
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append('[').append(n.getClassName()).append(']');
        String id = n.getViewIdResourceName();
        if (id != null) sb.append(" id=").append(id);
        CharSequence t = n.getText();
        if (t != null && t.length() > 0) sb.append(" text=").append(t);
        CharSequence d = n.getContentDescription();
        if (d != null && d.length() > 0) sb.append(" desc=").append(d);
        if (n.isClickable()) sb.append(" CLICKABLE");
        if (!n.isEnabled()) sb.append(" DISABLED");
        sb.append('\n');
        for (int i = 0; i < n.getChildCount(); i++) {
            walk(n.getChild(i), depth + 1, sb);
        }
    }
}
