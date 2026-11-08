package com.zjy.bydmini;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 动作注册表：内置常用功能 + 用户自定义（自定义项只需填官方 App 里那个按钮的文字）。
 * 软件内和小部件共用同一份定义，槽位各自保存。
 *
 * 自定义动作存成 id|名字|按钮文字1,按钮文字2，id 只增不改（c1、c2…），
 * 删除后槽位里的旧 id 会被清掉回落默认值，不会串到别的动作上。
 */
final class BydActions {

    static final String KEY_APP = "slots.app";
    static final String KEY_WIDGET = "slots.widget";
    static final String KEY_CUSTOM = "custom.actions";
    private static final String KEY_SEQ = "custom.seq";

    static final class Def {
        final String id;
        final String label;
        final int icon;
        final int lockState; // -1 不改车锁状态，0 解锁，1 上锁
        final String[] texts;

        Def(String id, String label, int icon, int lockState, String... texts) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.lockState = lockState;
            this.texts = texts;
        }
    }

    static final Def[] BUILTIN = {
            new Def("unlock", "解锁", R.drawable.ic_unlock, 0, "解锁", "开锁", "解锁车辆", "车门解锁"),
            new Def("lock", "上锁", R.drawable.ic_lock, 1, "上锁", "锁车", "闭锁", "车门上锁"),
            new Def("trunk", "开后备箱", R.drawable.ic_trunk, -1, "后备箱", "开启后备箱", "尾门"),
            new Def("off", "一键熄火", R.drawable.ic_power, -1, "熄火", "一键熄火", "关闭发动机"),
            new Def("ac", "空调", R.drawable.ic_fan, -1, "空调", "开启空调", "空调开"),
            new Def("find", "寻车", R.drawable.ic_find, -1, "寻车", "鸣笛", "闪灯", "找车"),
    };

    static final String[] DEFAULT_APP = {"unlock", "lock", "off", "trunk"};
    static final String[] DEFAULT_WIDGET = {"unlock", "lock", "off", "trunk", "ac"};

    private BydActions() {
    }

    /** 内置 + 自定义，顺序即选择列表顺序 */
    static List<Def> all(Context c) {
        List<Def> list = new ArrayList<>();
        for (Def d : BUILTIN) list.add(d);
        list.addAll(customs(c));
        return list;
    }

    static List<Def> customs(Context c) {
        List<Def> out = new ArrayList<>();
        String raw = prefs(c).getString(KEY_CUSTOM, "");
        if (raw == null || raw.isEmpty()) return out;

        List<String[]> rows = new ArrayList<>();
        boolean legacy = false;
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\|", 3);
            if (p.length == 3 && p[0].matches("c\\d+")) {
                rows.add(new String[]{p[0], p[1], p[2]});
            } else if (p.length >= 2) {
                // 旧版本只有 名字|按钮文字，没有 id
                legacy = true;
                StringBuilder texts = new StringBuilder(p[1]);
                for (int i = 2; i < p.length; i++) texts.append('|').append(p[i]);
                rows.add(new String[]{"", p[0], texts.toString()});
            }
        }
        if (legacy) rows = migrateLegacy(c, rows);

        for (String[] r : rows) {
            if (r[2].trim().isEmpty()) continue;
            out.add(new Def(r[0], r[1], R.drawable.ic_any, -1, r[2].split(",")));
        }
        return out;
    }

    /** 把旧的两段式自定义动作补上稳定 id，并同步改写槽位里的引用 */
    private static List<String[]> migrateLegacy(Context c, List<String[]> rows) {
        SharedPreferences sp = prefs(c);
        int seq = sp.getInt(KEY_SEQ, 0);
        for (String[] r : rows) {
            if (!r[0].isEmpty()) {
                try {
                    seq = Math.max(seq, Integer.parseInt(r[0].substring(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        String[] map = new String[rows.size()];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            String id = r[0];
            if (id.isEmpty()) id = "c" + (++seq);
            r[0] = id;
            map[i] = id;
            if (sb.length() > 0) sb.append('\n');
            sb.append(id).append('|').append(r[1]).append('|').append(r[2]);
        }

        // 旧槽位存的是 c0、c1（按出现顺序），换成新 id
        for (String key : new String[]{KEY_APP, KEY_WIDGET}) {
            String cur = sp.getString(key, null);
            if (cur == null) continue;
            String[] parts = cur.split(",", -1);
            boolean changed = false;
            for (int i = 0; i < parts.length; i++) {
                String v = parts[i].trim();
                if (!v.matches("c\\d+")) continue;
                int idx = Integer.parseInt(v.substring(1));
                if (idx >= 0 && idx < map.length && map[idx] != null) {
                    parts[i] = map[idx];
                    changed = true;
                }
            }
            if (changed) sp.edit().putString(key, String.join(",", parts)).apply();
        }
        sp.edit().putInt(KEY_SEQ, seq).putString(KEY_CUSTOM, sb.toString()).apply();
        return rows;
    }

    static Def byId(Context c, String id) {
        for (Def d : all(c)) {
            if (d.id.equals(id)) return d;
        }
        return BUILTIN[0];
    }

    static String[] slots(Context c, String key) {
        String raw = prefs(c).getString(key, String.join(",", defaultSlots(key)));
        String[] parts = raw.split(",", -1);
        String[] out = new String[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = parts[i].trim();
        return out;
    }

    /** 取第 i 个槽位的动作 id，没配过就用默认值兜底 */
    static String slotId(String[] ids, String[] dflt, int i) {
        if (i < ids.length && ids[i] != null && !ids[i].isEmpty()) return ids[i];
        return dflt[Math.min(i, dflt.length - 1)];
    }

    static void setSlots(Context c, String key, String[] ids) {
        prefs(c).edit().putString(key, String.join(",", ids)).apply();
    }

    static String addCustom(Context c, String label, String texts) {
        SharedPreferences sp = prefs(c);
        int seq = sp.getInt(KEY_SEQ, 0) + 1;
        String id = "c" + seq;
        String raw = sp.getString(KEY_CUSTOM, "");
        StringBuilder sb = new StringBuilder(raw == null ? "" : raw.trim());
        if (sb.length() > 0) sb.append('\n');
        sb.append(id).append('|').append(cleanLabel(label)).append('|').append(cleanTexts(texts));
        sp.edit().putInt(KEY_SEQ, seq).putString(KEY_CUSTOM, sb.toString()).apply();
        return id;
    }

    static void updateCustom(Context c, String id, String label, String texts) {
        List<Def> list = customs(c);
        StringBuilder sb = new StringBuilder();
        for (Def d : list) {
            if (sb.length() > 0) sb.append('\n');
            if (d.id.equals(id)) {
                sb.append(d.id).append('|').append(cleanLabel(label)).append('|')
                        .append(cleanTexts(texts));
            } else {
                sb.append(d.id).append('|').append(d.label).append('|')
                        .append(String.join(",", d.texts));
            }
        }
        prefs(c).edit().putString(KEY_CUSTOM, sb.toString()).apply();
    }

    /** 删掉自定义动作，同时把槽位里指向它的引用清空（会自动回落默认动作） */
    static void removeCustom(Context c, String id) {
        List<Def> list = customs(c);
        StringBuilder sb = new StringBuilder();
        for (Def d : list) {
            if (d.id.equals(id)) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(d.id).append('|').append(d.label).append('|')
                    .append(String.join(",", d.texts));
        }
        prefs(c).edit().putString(KEY_CUSTOM, sb.toString()).apply();

        SharedPreferences sp = prefs(c);
        for (String key : new String[]{KEY_APP, KEY_WIDGET}) {
            String cur = sp.getString(key, null);
            if (cur == null) continue;
            String[] parts = cur.split(",", -1);
            boolean changed = false;
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].trim().equals(id)) {
                    parts[i] = "";
                    changed = true;
                }
            }
            if (changed) setSlots(c, key, parts);
        }
    }

    static boolean isBuiltin(String id) {
        for (Def d : BUILTIN) {
            if (d.id.equals(id)) return true;
        }
        return false;
    }

    static String cleanLabel(String s) {
        return s == null ? "" : s.replace('|', '／').replace('\n', ' ').trim();
    }

    static String cleanTexts(String s) {
        return s == null ? "" : s.replace('，', ',').replace('\n', ' ').trim();
    }

    private static String[] defaultSlots(String key) {
        return KEY_WIDGET.equals(key) ? DEFAULT_WIDGET : DEFAULT_APP;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(SettingsActivity.PREF, 0);
    }
}
