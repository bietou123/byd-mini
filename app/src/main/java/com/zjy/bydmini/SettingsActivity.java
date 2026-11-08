package com.zjy.bydmini;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class SettingsActivity extends Activity {

    static final String PREF = "bydmini";
    static final String CAR_FILE = "car.png";

    private static final int REQ_PICK = 1001;
    private static final int MAX_DIM = 1400;

    private EditText nameIn;
    private EditText pctIn;
    private EditText rangeIn;
    private ImageView preview;
    private TextView previewHint;
    private CheckBox cutSwitch;
    private SeekBar tolBar;
    private TextView tolLabel;

    private Bitmap source;
    private Bitmap result;
    private int generation = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        nameIn = findViewById(R.id.nameIn);
        pctIn = findViewById(R.id.pctIn);
        rangeIn = findViewById(R.id.rangeIn);
        preview = findViewById(R.id.preview);
        previewHint = findViewById(R.id.previewHint);
        cutSwitch = findViewById(R.id.cutSwitch);
        tolBar = findViewById(R.id.tolBar);
        tolLabel = findViewById(R.id.tolLabel);

        SharedPreferences sp = getSharedPreferences(PREF, 0);
        nameIn.setText(sp.getString("name", "海豹 06"));
        pctIn.setText(sp.getString("pct", "82"));
        rangeIn.setText(sp.getString("range", "412"));

        Bitmap saved = loadCarBitmap(this);
        if (saved != null) showPreview(saved);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPick).setOnClickListener(v -> pick());
        findViewById(R.id.btnReset).setOnClickListener(v -> {
            new File(getFilesDir(), CAR_FILE).delete();
            result = null;
            source = null;
            preview.setImageDrawable(null);
            previewHint.setVisibility(View.VISIBLE);
            previewHint.setText("已恢复默认车模");
        });
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnPin2).setOnClickListener(v -> pin(Widget2x2Provider.class));
        findViewById(R.id.btnPin4).setOnClickListener(v -> pin(Widget4x2Provider.class));
        findViewById(R.id.btnDiag).setOnClickListener(v -> UiDump.captureToClipboard(this));

        tolLabel.setText("抠图强度 " + (tolBar.getProgress() + 2));
        tolBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tolLabel.setText("抠图强度 " + (p + 2));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                recut();
            }
        });

        cutSwitch.setOnCheckedChangeListener((v, checked) -> recut());

        findViewById(R.id.btnNewAction).setOnClickListener(v -> newCustomAction());
        findViewById(R.id.btnManageAction).setOnClickListener(v -> manageCustoms());
        rebuildSlotRows();
        fillLooks();
    }

    private void fillLooks() {
        LinearLayout box = findViewById(R.id.lookBox);
        box.removeAllViews();
        box.addView(slotRow("桌面名字",
                LauncherStyle.NAMES[LauncherStyle.nameIndex(this)],
                v -> chooseLook(true)));
        box.addView(divider());
        box.addView(slotRow("桌面图标",
                LauncherStyle.ICONS[LauncherStyle.iconIndex(this)],
                v -> chooseLook(false)));
    }

    private void chooseLook(boolean name) {
        String[] items = name ? LauncherStyle.NAMES : LauncherStyle.ICONS;
        int cur = name ? LauncherStyle.nameIndex(this) : LauncherStyle.iconIndex(this);
        String[] shown = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            shown[i] = i == cur ? "✓  " + items[i] : items[i];
        }
        new AlertDialog.Builder(this)
                .setTitle(name ? "桌面名字" : "桌面图标")
                .setItems(shown, (dlg, which) -> {
                    if (name) LauncherStyle.save(this, which, LauncherStyle.iconIndex(this));
                    else LauncherStyle.save(this, LauncherStyle.nameIndex(this), which);
                    fillLooks();
                    toast("已切换，桌面刷新可能要几秒");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void rebuildSlotRows() {
        fillSlots(findViewById(R.id.appSlotBox), BydActions.KEY_APP, 4,
                BydActions.DEFAULT_APP, "按钮 ");
        fillSlots(findViewById(R.id.widgetSlotBox), BydActions.KEY_WIDGET, 5,
                BydActions.DEFAULT_WIDGET, "小部件 ");
    }

    private void fillSlots(LinearLayout box, String key, int count, String[] dflt,
                           String prefix) {
        box.removeAllViews();
        String[] ids = BydActions.slots(this, key);
        for (int i = 0; i < count; i++) {
            if (i > 0) box.addView(divider());
            final int slot = i;
            box.addView(slotRow(prefix + (i + 1),
                    BydActions.byId(this, BydActions.slotId(ids, dflt, i)).label,
                    v -> chooseAction(key, count, dflt, slot)));
        }
    }

    private View divider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = dp(14);
        lp.rightMargin = dp(14);
        v.setLayoutParams(lp);
        v.setBackgroundColor(getColor(R.color.stroke));
        return v;
    }

    private View slotRow(String left, String right, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setBackgroundResource(R.drawable.row_bg);
        row.setOnClickListener(click);

        TextView l = new TextView(this);
        l.setText(left);
        l.setTextColor(getColor(R.color.text_dim));
        l.setTextSize(12f);
        row.addView(l, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView r = new TextView(this);
        r.setText(right);
        r.setTextColor(getColor(R.color.text));
        r.setTextSize(14f);
        row.addView(r);
        return row;
    }

    private void chooseAction(String key, int count, String[] dflt, int slot) {
        List<BydActions.Def> all = BydActions.all(this);
        String curId = BydActions.slotId(BydActions.slots(this, key), dflt, slot);
        String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            BydActions.Def d = all.get(i);
            names[i] = d.id.equals(curId) ? "✓  " + d.label : d.label;
        }
        new AlertDialog.Builder(this)
                .setTitle("第 " + (slot + 1) + " 个")
                .setItems(names, (dlg, which) -> {
                    String[] ids = BydActions.slots(this, key);
                    String[] out = new String[count];
                    for (int i = 0; i < count; i++) out[i] = BydActions.slotId(ids, dflt, i);
                    out[slot] = all.get(which).id;
                    BydActions.setSlots(this, key, out);
                    WidgetViews.updateAll(this);
                    rebuildSlotRows();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void newCustomAction() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(6), dp(20), 0);

        EditText label = new EditText(this);
        label.setHint("名字，比如 开窗");
        label.setSingleLine(true);
        box.addView(label);

        EditText texts = new EditText(this);
        texts.setHint("官方 App 里那个按钮的文字");
        texts.setSingleLine(true);
        box.addView(texts);

        new AlertDialog.Builder(this)
                .setTitle("新建自定义动作")
                .setView(box)
                .setPositiveButton("添加", (dlg, w) -> {
                    String name = label.getText().toString().trim();
                    String btn = texts.getText().toString().trim().replace('，', ',');
                    if (name.isEmpty() || btn.isEmpty()) {
                        toast("名字和按钮文字都要填");
                        return;
                    }
                    BydActions.addCustom(this, name, btn);
                    rebuildSlotRows();
                    toast("已添加，去上面的列表选它");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 管理用户自建动作：列出全部，点一项可删除 */
    private void manageCustoms() {
        List<BydActions.Def> list = BydActions.customs(this);
        if (list.isEmpty()) {
            toast("还没有自定义动作，先点「新建自定义动作」");
            return;
        }
        String[] rows = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            BydActions.Def d = list.get(i);
            StringBuilder t = new StringBuilder();
            for (String s : d.texts) {
                if (t.length() > 0) t.append('、');
                t.append(s);
            }
            rows[i] = d.label + "   ← " + t;
        }
        new AlertDialog.Builder(this)
                .setTitle("自定义动作（点一项可删除）")
                .setItems(rows, (dlg, which) -> {
                    BydActions.Def d = list.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle("删除「" + d.label + "」？")
                            .setMessage("用到它的槽位会回落到默认动作")
                            .setPositiveButton("删除", (x, y) -> {
                                BydActions.removeCustom(this, d.id);
                                rebuildSlotRows();
                                toast("已删除");
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    static Bitmap loadCarBitmap(Context c) {
        File f = new File(c.getFilesDir(), CAR_FILE);
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private void showPreview(Bitmap b) {
        preview.setImageBitmap(b);
        previewHint.setVisibility(View.GONE);
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "选择主图"), REQ_PICK);
        } catch (Exception e) {
            toast("没有可用的图库");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            Bitmap b = decode(uri);
            if (b == null) {
                toast("读不出这张图");
                return;
            }
            int deg = exifDegrees(uri);
            if (deg != 0) {
                Matrix m = new Matrix();
                m.postRotate(deg);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            source = b;
            recut();
        } catch (Exception e) {
            toast("读图失败：" + e.getMessage());
        }
    }

    private void recut() {
        if (source == null) {
            result = null;
            return;
        }
        final int tol = tolBar.getProgress() + 2;
        final boolean doCut = cutSwitch.isChecked();
        final Bitmap src = source;
        final int gen = ++generation;

        previewHint.setVisibility(View.VISIBLE);
        previewHint.setText("处理中…");

        new Thread(() -> {
            Bitmap out = doCut ? BackgroundRemover.cut(src, tol) : src;
            runOnUiThread(() -> {
                if (gen != generation || isFinishing()) return;
                result = out;
                showPreview(out);
            });
        }).start();
    }

    private void save() {
        SharedPreferences.Editor e = getSharedPreferences(PREF, 0).edit();
        e.putString("name", text(nameIn, "海豹 06"));
        e.putString("pct", text(pctIn, "82"));
        e.putString("range", text(rangeIn, "412"));
        e.apply();

        File f = new File(getFilesDir(), CAR_FILE);
        if (result != null && cutSwitch.isChecked()) {
            try (OutputStream out = new FileOutputStream(f)) {
                result.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception ex) {
                toast("保存失败：" + ex.getMessage());
                return;
            }
        } else if (source != null) {
            try (OutputStream out = new FileOutputStream(f)) {
                source.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception ex) {
                toast("保存失败：" + ex.getMessage());
                return;
            }
        }

        toast("已保存");
        WidgetViews.updateAll(this);
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_out_right);
    }

    private void pin(Class<?> provider) {
        try {
            AppWidgetManager awm = getSystemService(AppWidgetManager.class);
            if (awm != null && awm.isRequestPinAppWidgetSupported()) {
                ComponentName cn = new ComponentName(this, provider);
                awm.requestPinAppWidget(cn, null, null);
            } else {
                toast("长按桌面空白处 → 小部件 → Car");
            }
        } catch (Exception e) {
            toast("长按桌面空白处 → 小部件 → Car");
        }
    }

    private static String text(EditText v, String dflt) {
        String s = v.getText().toString().trim();
        return s.isEmpty() ? dflt : s;
    }

    private Bitmap decode(Uri uri) throws Exception {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in, null, probe);
        if (in != null) in.close();
        if (probe.outWidth <= 0 || probe.outHeight <= 0) return null;

        int scale = 1;
        while (Math.max(probe.outWidth, probe.outHeight) / scale > MAX_DIM) scale *= 2;

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = scale;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        in = getContentResolver().openInputStream(uri);
        Bitmap b = BitmapFactory.decodeStream(in, null, o);
        if (in != null) in.close();
        return b;
    }

    private int exifDegrees(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            ExifInterface ex = new ExifInterface(in);
            int o = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
