package com.byd.aeri.caranywhere;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 二级页：故意不含任何车控按钮，用来验证 Car 的「找不到按钮就退一级再试」。 */
public class MoreActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("MockByd", "open MoreActivity");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 96, 48, 48);
        root.setBackgroundColor(0xFFFFFFFF);

        TextView t = new TextView(this);
        t.setText("设置 · 二级页（这里没有任何车控按钮）");
        t.setTextSize(16);
        root.addView(t);

        Button back = new Button(this);
        back.setText("返回首屏");
        back.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 140);
        lp.topMargin = 60;
        root.addView(back, lp);
        back.setOnClickListener(v -> finish());

        setContentView(root);
    }
}
