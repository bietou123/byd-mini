package com.byd.aeri.caranywhere;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 模拟「比亚迪汽车」官方 App 的车控首屏，仅用于 Car（byd-mini）的无障碍联调。
 * 包名故意与官方一致；按钮文案与 BydActions 内置动作保持一致。
 * 每次点击都会写 logcat（tag=MockByd），并更新顶部状态文字，便于自动化断言。
 */
public class MainActivity extends Activity {

    private static final String TAG = "MockByd";

    private TextView status;
    private int lockState = 1; // 1=已锁车 0=未锁车

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(20));
        root.setBackgroundColor(0xFFF4F6FA);

        TextView title = new TextView(this);
        title.setText("比亚迪汽车（模拟界面）");
        title.setTextSize(20);
        title.setTextColor(0xFF1A1A1A);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(0xFF5A6472);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        root.addView(status, slp);

        addButton(root, "解锁", () -> {
            lockState = 0;
            click("解锁");
        });
        addButton(root, "上锁", () -> {
            lockState = 1;
            click("上锁");
        });
        addButton(root, "开启空调", () -> click("开启空调"));
        addButton(root, "寻车", () -> click("寻车"));
        addButton(root, "开启后备箱", () -> click("开启后备箱"));

        Button more = addButton(root, "更多设置", () -> {
            startActivity(new Intent(this, MoreActivity.class));
            Log.i(TAG, "open more");
        });
        more.setText("更多设置（二级页）");

        ScrollView scroller = new ScrollView(this);
        scroller.addView(root);
        setContentView(scroller);
        refresh();
    }

    private void click(String label) {
        Log.i(TAG, "click " + label);
        refresh(label);
    }

    private void refresh() {
        refresh(null);
    }

    private void refresh(String lastAction) {
        String lock = lockState == 1 ? "已锁车" : "未锁车";
        StringBuilder sb = new StringBuilder();
        sb.append(lock).append(" · 电量 82% · 续航 412km");
        if (lastAction != null) {
            sb.append("\n已执行：").append(lastAction);
        }
        status.setText(sb.toString());
    }

    private Button addButton(LinearLayout parent, String label, Runnable onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xFF3482FF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(12);
        parent.addView(b, lp);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
