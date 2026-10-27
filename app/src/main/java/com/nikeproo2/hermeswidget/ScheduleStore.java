package com.nikeproo2.hermeswidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Хранилище расписания: встроенный JSON -> кэш в SharedPreferences -> обновление с сервера.
 */
final class ScheduleStore {

    private static final String TAG = "ScheduleStore";
    private static final String PREFS = "schedule";
    private static final String KEY_JSON = "json";
    private static final String KEY_TS = "ts";
    private static final long STALE_MS = 6L * 60 * 60 * 1000;   // перечитывать с сервера раз в 6 часов

    static final String REMOTE_URL = "http://34.31.252.67:8080/schedule.json";

    private ScheduleStore() {}

    static final class Lesson {
        final int num;
        final String subject;
        final int startMin;
        final int endMin;
        final boolean homework;
        final String room;

        Lesson(int num, String subject, int startMin, int endMin, boolean homework, String room) {
            this.num = num;
            this.subject = subject;
            this.startMin = startMin;
            this.endMin = endMin;
            this.homework = homework;
            this.room = room;
        }
    }

    /** Все уроки по ISO-дню недели (1 = понедельник). */
    static List<Lesson> forDay(Context ctx, int isoDay) {
        String raw = readRaw(ctx);
        List<Lesson> out = new ArrayList<>();
        if (raw == null) return out;
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject days = root.optJSONObject("days");
            if (days == null) return out;
            JSONArray arr = days.optJSONArray(String.valueOf(isoDay));
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Lesson(
                        o.optInt("n", i + 1),
                        o.optString("subject", ""),
                        parseTime(o.optString("start", "")),
                        parseTime(o.optString("end", "")),
                        o.optBoolean("hw", false),
                        o.optString("room", null)));
            }
        } catch (Exception e) {
            Log.w(TAG, "bad schedule json", e);
        }
        out.sort((a, b) -> Integer.compare(a.startMin, b.startMin));
        return out;
    }

    /** Читает JSON: сначала кэш (свежий или нет), иначе встроенная копия. */
    private static String readRaw(Context ctx) {
        String cached = prefs(ctx).getString(KEY_JSON, null);
        if (cached != null) return cached;
        try (InputStream in = ctx.getAssets().open("schedule.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } catch (Exception e) {
            Log.w(TAG, "no bundled schedule", e);
            return null;
        }
    }

    /** Тянет расписание с сервера, если кэш устарел. Вызывать из фонового потока. */
    static void refreshIfStale(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long age = System.currentTimeMillis() - p.getLong(KEY_TS, 0L);
        if (age < STALE_MS) return;

        HttpURLConnection conn = null;
        try {
            URL url = new URL(REMOTE_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Cache-Control", "no-cache");
            int code = conn.getResponseCode();
            if (code != 200) return;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            String body = sb.toString().trim();
            if (!body.startsWith("{")) return;              // защита от страниц-заглушек
            new JSONObject(body);                            // проверка валидности
            p.edit().putString(KEY_JSON, body)
                    .putLong(KEY_TS, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            Log.i(TAG, "refresh failed, keeping cache: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int parseTime(String hhmm) {
        try {
            int c = hhmm.indexOf(':');
            if (c < 0) return -1;
            int h = Integer.parseInt(hhmm.substring(0, c).trim());
            int m = Integer.parseInt(hhmm.substring(c + 1).trim());
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    static String hhmm(int minutes) {
        if (minutes < 0) return "--:--";
        return String.format(java.util.Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
