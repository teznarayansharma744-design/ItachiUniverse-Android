package com.itachi.universe;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Moments a student marked in a lecture ("formula explained here"), kept on this phone. */
public final class Bookmarks {
    private Bookmarks() {}

    public static final class Mark {
        public float t;
        public String label = "";
    }

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("bookmarks", Context.MODE_PRIVATE); }

    public static List<Mark> list(Context c, int lectureId) {
        List<Mark> out = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(p(c).getString("l" + lectureId, "[]")).getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                Mark m = new Mark();
                m.t = o.get("t").getAsFloat();
                m.label = o.has("label") ? o.get("label").getAsString() : "";
                out.add(m);
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> Float.compare(a.t, b.t));
        return out;
    }

    private static void write(Context c, int lectureId, List<Mark> marks) {
        JsonArray arr = new JsonArray();
        for (Mark m : marks) {
            JsonObject o = new JsonObject();
            o.addProperty("t", m.t);
            o.addProperty("label", m.label);
            arr.add(o);
        }
        p(c).edit().putString("l" + lectureId, arr.toString()).apply();
    }

    public static void add(Context c, int lectureId, float t, String label) {
        List<Mark> all = list(c, lectureId);
        Mark m = new Mark();
        m.t = t;
        m.label = label == null ? "" : label;
        all.add(m);
        write(c, lectureId, all);
    }

    public static void remove(Context c, int lectureId, int index) {
        List<Mark> all = list(c, lectureId);
        if (index >= 0 && index < all.size()) all.remove(index);
        write(c, lectureId, all);
    }

    public static int count(Context c, int lectureId) { return list(c, lectureId).size(); }
}
