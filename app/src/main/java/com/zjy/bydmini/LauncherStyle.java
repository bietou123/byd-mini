package com.zjy.bydmini;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/**
 * 桌面名字 / 图标：靠 manifest 里 16 个 activity-alias 切换，
 * 同一时刻只启用 (名字, 图标) 对应的那一个。
 */
public final class LauncherStyle {

    static final String[] NAMES = {"Car", "极简控车", "我的车", "海豹"};
    static final String[] ICONS = {"蓝色底 · 白车", "曜黑底 · 白车", "白色底 · 蓝车", "深蓝底 · 白车"};

    private LauncherStyle() {
    }

    static int nameIndex(Context c) {
        return clamp(prefs(c).getInt("look.name", 0));
    }

    static int iconIndex(Context c) {
        return clamp(prefs(c).getInt("look.icon", 0));
    }

    static void save(Context c, int name, int icon) {
        prefs(c).edit().putInt("look.name", name).putInt("look.icon", icon).apply();
        apply(c, name, icon);
    }

    /** 切桌面别名；小米桌面刷新可能晚几秒 */
    static void apply(Context c, int name, int icon) {
        PackageManager pm = c.getPackageManager();
        String pkg = c.getPackageName();
        for (int n = 1; n <= 4; n++) {
            for (int i = 1; i <= 4; i++) {
                int state = (n - 1 == name && i - 1 == icon)
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                try {
                    pm.setComponentEnabledSetting(
                            new ComponentName(pkg, pkg + ".Launcher" + n + i),
                            state, PackageManager.DONT_KILL_APP);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(SettingsActivity.PREF, 0);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 3);
    }
}
