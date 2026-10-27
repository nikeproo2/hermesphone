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
        List<ScheduleStore.Lesson>[] week = new List[8];
        for (int d = 1; d <= 7; d++) {
            week[d] = ScheduleStore.forDay(context, d);
        }
        DayPlan plan = DayPlan.compute(now, week[isoToday], week);

        bindTop(context, v, plan);
        bindBottom(context, v, plan);

        manager.updateAppWidget(id, v);
    }

    /** Верхняя строка: идёт урок — показываем его; иначе ближайший. */
    private static void bindTop(Context context, RemoteViews v, DayPlan plan) {
        if (plan.current != null) {
            ScheduleStore.Lesson l = plan.current;
            v.setTextViewText(R.id.cur_caption, "СЕЙЧАС");
            v.setTextViewText(R.id.cur_subject, l.subject);
            v.setTextViewText(R.id.cur_time,
                    ScheduleStore.hhmm(l.startMin) + " – " + ScheduleStore.hhmm(l.endMin)
                            + " · осталось " + humanMinutes(minutesLeft(nowMinutes(), l.endMin)));
            v.setViewVisibility(R.id.cur_hw, l.homework ? View.VISIBLE : View.GONE);
            v.setViewVisibility(R.id.cur_progress, View.VISIBLE);
            v.setProgressBar(R.id.cur_progress, 100, plan.progressPercent, false);
            return;
        }

        v.setViewVisibility(R.id.cur_progress, View.GONE);

        if (plan.next != null && plan.nextDayOffset == 0) {
            ScheduleStore.Lesson l = plan.next;
            v.setTextViewText(R.id.cur_caption, "СЕЙЧАС");
            v.setTextViewText(R.id.cur_subject, l.subject);
            v.setTextViewText(R.id.cur_time,
                    "нет урока · через " + humanMinutes(plan.minutesToNext)
                            + " · " + ScheduleStore.hhmm(l.startMin));
            v.setViewVisibility(R.id.cur_hw, l.homework ? View.VISIBLE : View.GONE);
        } else if (plan.next != null) {
            v.setTextViewText(R.id.cur_caption, "СЕЙЧАС");
            v.setTextViewText(R.id.cur_subject, plan.next.subject);
            v.setTextViewText(R.id.cur_time,
                    dayWord(plan.nextDayOffset) + " · " + ScheduleStore.hhmm(plan.next.startMin));
            v.setViewVisibility(R.id.cur_hw, plan.next.homework ? View.VISIBLE : View.GONE);
        } else {
            v.setTextViewText(R.id.cur_caption, "СЕЙЧАС");
            v.setTextViewText(R.id.cur_subject, plan.emptyDay ? "Уроков нет" : "Свободно");
            v.setTextViewText(R.id.cur_time, "");
            v.setViewVisibility(R.id.cur_hw, View.GONE);
        }
    }

    /** Нижняя строка: урок после текущего (или после ближайшего, если урока нет). */
    private static void bindBottom(Context context, RemoteViews v, DayPlan plan) {
        ScheduleStore.Lesson l;
        if (plan.current != null) {
            l = plan.next;
        } else if (plan.next != null && plan.nextDayOffset == 0) {
            l = lessonAfter(context, plan.next);
        } else {
            l = null;
        }

        v.setTextViewText(R.id.next_caption, "ДАЛЬШЕ");

        if (l == null) {
            v.setTextViewText(R.id.next_subject,
                    plan.current != null ? "На сегодня всё" : "—");
            v.setTextViewText(R.id.next_time, "");
            v.setViewVisibility(R.id.next_hw, View.GONE);
            return;
        }

        v.setTextViewText(R.id.next_subject, l.subject);

        int nowMin = nowMinutes();
        int from = plan.current != null ? plan.current.endMin
                : (plan.next != null ? plan.next.startMin : nowMin);
        int gap = Math.max(0, l.startMin - from);

        String sub = ScheduleStore.hhmm(l.startMin) + " – " + ScheduleStore.hhmm(l.endMin);
        if (gap > 0) sub += " · через " + gap + " мин";
        v.setTextViewText(R.id.next_time, sub);
        v.setViewVisibility(R.id.next_hw, l.homework ? View.VISIBLE : View.GONE);
    }

    private static ScheduleStore.Lesson lessonAfter(Context context, ScheduleStore.Lesson first) {
        Calendar now = Calendar.getInstance();
        int iso = now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ? 7 : now.get(Calendar.DAY_OF_WEEK) - 1;
        boolean passed = false;
        for (ScheduleStore.Lesson l : ScheduleStore.forDay(context, iso)) {
            if (passed) return l;
            if (l.startMin == first.startMin && l.num == first.num) passed = true;
        }
        return null;
    }

    private static int nowMinutes() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private static int minutesLeft(int now, int endMin) {
        return Math.max(0, endMin - now);
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
