package com.zjy.bydmini;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

final class WidgetViews {

    private static final int[] SLOTS_2X2 = {R.id.wSlot1, R.id.wSlot2, R.id.wSlot3, R.id.wSlot4};
    private static final int[] SLOTS_4X2 = {
            R.id.wSlot1, R.id.wSlot2, R.id.wSlot3, R.id.wSlot4, R.id.wSlot5};

    private WidgetViews() {
    }

    static RemoteViews build(Context c, boolean wide) {
        RemoteViews rv = new RemoteViews(c.getPackageName(),
                wide ? R.layout.widget_4x2 : R.layout.widget_2x2);
        rv.setTextViewText(R.id.widgetName, c.getSharedPreferences(SettingsActivity.PREF, 0)
                .getString("name", "海豹 06"));
        boolean live = BydAccessibilityService.isEnabled(c)
                && BydAccessibilityService.instance != null;
        rv.setInt(R.id.widgetDot, "setBackgroundResource",
                live ? R.drawable.dot_on : R.drawable.dot_dim);

        String[] ids = BydActions.slots(c, BydActions.KEY_WIDGET);
        if (wide) {
            for (int i = 0; i < SLOTS_4X2.length; i++) {
                BydActions.Def d = BydActions.byId(c,
                        BydActions.slotId(ids, BydActions.DEFAULT_WIDGET, i));
                rv.setTextViewText(SLOTS_4X2[i], d.label);
                rv.setOnClickPendingIntent(SLOTS_4X2[i], pi(c, d.id, 11 + i));
            }
        } else {
            for (int i = 0; i < SLOTS_2X2.length; i++) {
                BydActions.Def d = BydActions.byId(c,
                        BydActions.slotId(ids, BydActions.DEFAULT_WIDGET, i));
                rv.setImageViewResource(SLOTS_2X2[i], d.icon);
                rv.setOnClickPendingIntent(SLOTS_2X2[i], pi(c, d.id, 11 + i));
            }
        }
        return rv;
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        ComponentName small = new ComponentName(c, Widget2x2Provider.class);
        for (int id : m.getAppWidgetIds(small)) {
            m.updateAppWidget(id, build(c, false));
        }
        ComponentName wide = new ComponentName(c, Widget4x2Provider.class);
        for (int id : m.getAppWidgetIds(wide)) {
            m.updateAppWidget(id, build(c, true));
        }
    }

    private static PendingIntent pi(Context c, String actionId, int code) {
        Intent i = new Intent(c, WidgetActionActivity.class);
        i.setAction("com.zjy.bydmini.WIDGET_ACTION");
        i.putExtra(WidgetActionActivity.EXTRA, actionId);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        return PendingIntent.getActivity(c, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
