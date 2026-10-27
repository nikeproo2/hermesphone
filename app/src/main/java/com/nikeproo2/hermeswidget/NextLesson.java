package com.nikeproo2.hermeswidget;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Логика "что дальше": идёт ли урок сейчас, сколько до следующего, что было последним,
 * и когда начнётся учёба, если сегодня уже всё.
 */
final class NextLesson {

    enum Kind { NOW, SOON, LATER_TODAY, DONE, NEXT_DAY, EMPTY }

    final Kind kind;
    final ScheduleStore.Lesson lesson;
    final int minutes;          // до начала (SOON/LATER_TODAY) или до конца (NOW)
    final int dayOffset;        // 0 = сегодня, 1 = завтра, ... для NEXT_DAY
    final int progressPercent;  // 0..100 для NOW

    private NextLesson(Kind kind, ScheduleStore.Lesson lesson, int minutes, int dayOffset, int progress) {
        this.kind = kind;
        this.lesson = lesson;
        this.minutes = minutes;
        this.dayOffset = dayOffset;
        this.progressPercent = progress;
    }

    static NextLesson compute(Calendar now, ScheduleStore.Lesson[] today, List<ScheduleStore.Lesson>[] week) {
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        // 1. Урок идёт прямо сейчас
        for (ScheduleStore.Lesson l : today) {
            if (l.startMin <= nowMin && nowMin < l.endMin) {
                int span = Math.max(1, l.endMin - l.startMin);
                int done = nowMin - l.startMin;
                return new NextLesson(Kind.NOW, l, l.endMin - nowMin, 0, Math.min(100, done * 100 / span));
            }
        }

        // 2. Ближайший урок сегодня
        ScheduleStore.Lesson next = null;
        for (ScheduleStore.Lesson l : today) {
            if (l.startMin > nowMin) {
                if (next == null || l.startMin < next.startMin) next = l;
            }
        }
        if (next != null) {
            int delta = next.startMin - nowMin;
            return new NextLesson(delta <= 60 ? Kind.SOON : Kind.LATER_TODAY, next, delta, 0, 0);
        }

        // 3. Сегодня всё, ищем следующий учебный день
        int isoToday = now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ? 7 : now.get(Calendar.DAY_OF_WEEK) - 1;
        for (int d = 1; d <= 7; d++) {
            int iso = ((isoToday - 1 + d) % 7) + 1;
            List<ScheduleStore.Lesson> day = week[iso];
            if (day != null && !day.isEmpty()) {
                return new NextLesson(d == 0 ? Kind.NEXT_DAY : Kind.NEXT_DAY, day.get(0), -1, d, 0);
            }
        }

        return new NextLesson(today.length == 0 ? Kind.EMPTY : Kind.DONE, null, -1, 0, 0);
    }

    @SuppressWarnings("unchecked")
    static List<ScheduleStore.Lesson>[] emptyWeek() {
        return new List[8];
    }

    static void putWeek(List<ScheduleStore.Lesson>[] week, int iso, List<ScheduleStore.Lesson> lessons) {
        if (iso >= 1 && iso <= 7) week[iso] = new ArrayList<>(lessons);
    }
}
