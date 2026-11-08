package com.zjy.bydmini;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class Widget2x2Provider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            m.updateAppWidget(id, WidgetViews.build(c, false));
        }
    }

    @Override
    public void onEnabled(Context c) {
        WidgetViews.updateAll(c);
    }
}
