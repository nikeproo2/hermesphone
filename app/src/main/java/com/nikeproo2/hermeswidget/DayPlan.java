package com.nikeproo2.hermeswidget;

import java.util.Calendar;
import java.util.List;

/**
 * Состояние дня: прошедший урок, текущий и следующий.
 * Ровно то, что нужно показать в трёх строках виджета.
 */
final class DayPlan {

    final ScheduleStore.Lesson prev;      // последний завершившийся
    final ScheduleStore.Lesson current;   // идёт прямо сейчас
    final ScheduleStore.Lesson next;      // ближайший впереди
    final int progressPercent;            // 0..100, только для current
    final int minutesToNext;              // до начала next, -1 если next нет
    final int nextDayOffset;              // 0 = next сегодня, 1 = завтра, ...
    final boolean emptyDay;               // на сегодня уроков вообще нет

    private DayPlan(ScheduleStore.Lesson prev, ScheduleStore.Lesson current,
                    ScheduleStore.Lesson next, int progressPercent,
                    int minutesToNext, int nextDayOffset, boolean emptyDay) {
        this.prev = prev;
        this.current = current;
        this.next = next;
        this.progressPercent = progressPercent;
        this.minutesToNext = minutesToNext;
        this.nextDayOffset = nextDayOffset;
        this.emptyDay = emptyDay;
    }

    static DayPlan compute(Calendar now, List<ScheduleStore.Lesson> today,
                           List<ScheduleStore.Lesson>[] week) {
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        ScheduleStore.Lesson prev = null;
        ScheduleStore.Lesson current = null;
        ScheduleStore.Lesson next = null;

        for (ScheduleStore.Lesson l : today) {
            if (l.endMin <= nowMin) {
                if (prev == null || l.endMin > prev.endMin) prev = l;
            } else if (l.startMin <= nowMin) {
                current = l;                                  // start <= now < end
            } else if (next == null || l.startMin < next.startMin) {
                next = l;
            }
        }

        int progress = 0;
        if (current != null) {
            int span = Math.max(1, current.endMin - current.startMin);
            progress = Math.min(100, (nowMin - current.startMin) * 100 / span);
        }

        int toNext = next != null ? next.startMin - nowMin : -1;
        int dayOffset = 0;

        // Сегодня уроков больше нет — ищем первый урок следующего учебного дня
        if (next == null) {
            int isoToday = now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                    ? 7 : now.get(Calendar.DAY_OF_WEEK) - 1;
            for (int d = 1; d <= 7; d++) {
                int iso = ((isoToday - 1 + d) % 7) + 1;
                List<ScheduleStore.Lesson> day = week[iso];
                if (day != null && !day.isEmpty()) {
                    next = day.get(0);
                    dayOffset = d;
                    break;
                }
            }
        }

        return new DayPlan(prev, current, next, progress, toNext, dayOffset,
                today == null || today.isEmpty());
    }
}
