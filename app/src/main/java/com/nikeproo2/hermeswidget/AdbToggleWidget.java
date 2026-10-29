package com.nikeproo2.hermeswidget;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.widget.RemoteViews;

public class AdbToggleWidget extends AppWidgetProvider {

    static final String KEY = "adb_wifi_enabled";
    static final String ACTION_TOGGLE = "com.nikeproo2.hermeswidget.ADB_TOGGLE";
    static final int REQ = 4011;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            toggle(context);
        }
        super.onReceive(context, intent);
    }

    static boolean granted(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isOn(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), KEY, 0) == 1;
    }

    static void toggle(Context c) {
        if (!granted(c)) return;
        try {
            Settings.Global.putInt(c.getContentResolver(), KEY, isOn(c) ? 0 : 1);
        } catch (SecurityException e) {
            android.util.Log.e("AdbToggleWidget", "toggle failed", e);
        }
        renderAll(c);
    }

    static void renderAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, AdbToggleWidget.class));
        for (int id : ids) render(c, m, id);
    }

    private static void render(Context context, AppWidgetManager manager, int id) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_adb);
        try {
            boolean ok = granted(context);
            boolean on = isOn(context);

            v.setTextViewText(R.id.adb_label, "ОТЛАДКА ПО WI-FI");

            if (!ok) {
                v.setTextViewText(R.id.adb_state, "НЕТ ДОСТУПА");
                v.setTextColor(R.id.adb_state, 0xFFFF8A80);
                v.setTextViewText(R.id.adb_hint, "выдай права через adb");
            } else if (on) {
                v.setTextViewText(R.id.adb_state, "ВКЛ");
                v.setTextColor(R.id.adb_state, 0xFF8AB4F8);
                v.setTextViewText(R.id.adb_hint, "нажми, чтобы выключить");
            } else {
                v.setTextViewText(R.id.adb_state, "ВЫКЛ");
                v.setTextColor(R.id.adb_state, 0xB3FFFFFF);
                v.setTextViewText(R.id.adb_hint, "нажми, чтобы включить");
            }

            Intent i = new Intent(context, AdbToggleWidget.class);
            i.setAction(ACTION_TOGGLE);
            PendingIntent pi = PendingIntent.getBroadcast(context, REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.adb_root, pi);
        } catch (Exception e) {
            android.util.Log.e("AdbToggleWidget", "render failed", e);
            v.setTextViewText(R.id.adb_label, "ОТЛАДКА");
            v.setTextViewText(R.id.adb_state, "ОШИБКА");
            v.setTextViewText(R.id.adb_hint, "");
        }
        manager.updateAppWidget(id, v);
    }
}
