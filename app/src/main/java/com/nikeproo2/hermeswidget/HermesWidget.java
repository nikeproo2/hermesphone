package com.nikeproo2.hermeswidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HermesWidget extends AppWidgetProvider {

    static final long TICK_MS = 60_000L;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            render(context, manager, id);
        }
        scheduleTick(context);
    }

    @Override
    public void onEnabled(Context context) {
        scheduleTick(context);
    }

    @Override
    public void onDisabled(Context context) {
        cancelTick(context);
    }

    static void render(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_hermes);

        Date now = new Date();
        views.setTextViewText(R.id.tv_time,
                new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        views.setTextViewText(R.id.tv_date,
                new SimpleDateFormat("EEEE, d MMMM", new Locale("ru")).format(now));

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battery = context.registerReceiver(null, filter);
        int level = 0;
        int status = BatteryManager.BATTERY_STATUS_UNKNOWN;
        if (battery != null) {
            int raw = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, status);
            if (raw >= 0 && scale > 0) {
                level = Math.round(raw * 100f / scale);
            }
        }
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        views.setTextViewText(R.id.tv_battery, level + "%" + (charging ? "  ⚡" : ""));

        manager.updateAppWidget(id, views);
    }

    static PendingIntent tickIntent(Context context) {
        Intent intent = new Intent(context, TickReceiver.class);
        intent.setAction(TickReceiver.ACTION_TICK);
        return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleTick(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + TICK_MS,
                TICK_MS,
                tickIntent(context));
    }

    static void cancelTick(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(tickIntent(context));
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName me = new ComponentName(context, HermesWidget.class);
        int[] ids = manager.getAppWidgetIds(me);
        for (int id : ids) {
            render(context, manager, id);
        }
    }
}
