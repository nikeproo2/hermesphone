package com.nikeproo2.hermeswidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class NextLessonWidget extends AppWidgetProvider {

    static final long TICK_MS = 60_000L;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id);
        scheduleTick(context);
        refreshInBackground(context);
    }

    @Override
    public void onEnabled(Context context) {
        scheduleTick(context);
        refreshInBackground(context);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickIntent(context));
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, NextLessonWidget.class));
        for (int id : ids) render(context, manager, id);
    }

    private static void render(Context context, AppWidgetManager manager, int id) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_next);

        Calendar now = Calendar.getInstance();
        int isoToday = now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ? 7 : now.get(Calendar.DAY_OF_WEEK) - 1;

        @SuppressWarnings("unchecked")
        List<ScheduleStore.Lesson>[] week = NextLesson.emptyWeek();
        for (int d = 1; d <= 7; d++) {
            NextLesson.putWeek(week, d, ScheduleStore.forDay(context, d));
        }
        List<ScheduleStore.Lesson> today = week[isoToday];
        NextLesson nl = NextLesson.compute(now, today.toArray(new ScheduleStore.Lesson[0]), week);

        switch (nl.kind) {
            case NOW:
                v.setTextViewText(R.id.tv_label,
                        "Сейчас идёт · до " + ScheduleStore.hhmm(nl.lesson.endMin));
                v.setViewVisibility(R.id.progress, View.VISIBLE);
                v.setProgressBar(R.id.progress, 100, nl.progressPercent, false);
                break;
            case SOON:
                v.setTextViewText(R.id.tv_label, "Через " + humanMinutes(nl.minutes));
                v.setViewVisibility(R.id.progress, View.GONE);
                break;
            case LATER_TODAY:
                v.setTextViewText(R.id.tv_label, "Следующий урок");
                v.setViewVisibility(R.id.progress, View.GONE);
                break;
            case NEXT_DAY:
                v.setTextViewText(R.id.tv_label, dayWord(nl.dayOffset));
                v.setViewVisibility(R.id.progress, View.GONE);
                break;
            case DONE:
                v.setTextViewText(R.id.tv_label, "На сегодня всё");
                v.setViewVisibility(R.id.progress, View.GONE);
                break;
            default:
                v.setTextViewText(R.id.tv_label, "Расписания нет");
                v.setViewVisibility(R.id.progress, View.GONE);
                break;
        }

        if (nl.lesson != null) {
            v.setTextViewText(R.id.tv_subject, nl.lesson.subject);
            String time = ScheduleStore.hhmm(nl.lesson.startMin) + " – "
                    + ScheduleStore.hhmm(nl.lesson.endMin);
            if (nl.lesson.room != null && !nl.lesson.room.isEmpty()) {
                time += " · каб. " + nl.lesson.room;
            }
            v.setTextViewText(R.id.tv_time, time);
            v.setViewVisibility(R.id.tv_hw,
                    nl.lesson.homework ? View.VISIBLE : View.GONE);
        } else {
            v.setTextViewText(R.id.tv_subject,
                    nl.kind == NextLesson.Kind.DONE ? "Отдыхай" : "—");
            v.setTextViewText(R.id.tv_time, "");
            v.setViewVisibility(R.id.tv_hw, View.GONE);
        }

        manager.updateAppWidget(id, v);
    }

    private static String humanMinutes(int m) {
        if (m <= 0) return "минуту";
        int h = m / 60, min = m % 60;
        if (h == 0) return min + " " + plural(min, "минуту", "минуты", "минут");
        if (min == 0) return h + " " + plural(h, "час", "часа", "часов");
        return h + " ч " + min + " мин";
    }

    private static String plural(int n, String one, String few, String many) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return one;
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
        return many;
    }

    private static String dayWord(int offset) {
        if (offset == 1) return "Завтра";
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, offset);
        String[] names = {"", "В понедельник", "Во вторник", "В среду", "В четверг",
                "В пятницу", "В субботу", "В воскресенье"};
        int iso = c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ? 7 : c.get(Calendar.DAY_OF_WEEK) - 1;
        return names[iso];
    }

    static PendingIntent tickIntent(Context context) {
        Intent intent = new Intent(context, TickReceiver.class);
        intent.setAction(TickReceiver.ACTION_TICK);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleTick(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + TICK_MS, TICK_MS, tickIntent(context));
    }

    private static void refreshInBackground(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            ScheduleStore.refreshIfStale(app);
            refreshAll(app);
        }, "schedule-refresh").start();
    }
}
