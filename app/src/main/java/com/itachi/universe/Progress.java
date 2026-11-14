package com.itachi.universe;

import android.content.Context;
import android.content.SharedPreferences;

/** Remembers, on this phone only, where the student stopped in each lecture and which are finished. */
public final class Progress {
    private Progress() {}

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("progress", Context.MODE_PRIVATE); }

    /** Call now and then while a video plays. Positions are in seconds. */
    public static void save(Context c, int id, float pos, float dur) {
        if (id < 0 || dur <= 0 || pos < 0) return;
        SharedPreferences.Editor e = p(c).edit();
        if (pos / dur >= 0.92f) {            // finished: mark watched, next time start from 0
            e.putBoolean("w" + id, true).putFloat("p" + id, 0f).putFloat("d" + id, dur);
        } else if (pos >= 10f) {
            e.putFloat("p" + id, pos).putFloat("d" + id, dur);
        }
        e.apply();
    }

    /** Where to start, in seconds, or 0 to start from the beginning. */
    public static float resume(Context c, int id) { return p(c).getFloat("p" + id, 0f); }

    public static boolean watched(Context c, int id) { return p(c).getBoolean("w" + id, false); }

    /** 0-99 while in progress, otherwise 0. */
    public static int percent(Context c, int id) {
        float pos = p(c).getFloat("p" + id, 0f), dur = p(c).getFloat("d" + id, 0f);
        if (dur <= 0 || pos <= 0) return 0;
        return Math.min(99, Math.round(pos * 100f / dur));
    }

    public static String clock(float seconds) {
        int s = Math.round(seconds), h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }
}
