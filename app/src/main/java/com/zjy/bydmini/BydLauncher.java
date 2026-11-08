package com.zjy.bydmini;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class BydLauncher {

    public static final String PKG = "com.byd.aeri.caranywhere";

    public static boolean isInstalled(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** 无转场动画，配合无障碍遮罩做到视觉上的「没跳走」 */
    public static boolean launch(Context c) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(PKG);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        c.startActivity(i);
        return true;
    }
}
