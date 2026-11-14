package com.itachi.universe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {}

    public static final int[] TINTS = {0xFF5B5BFF, 0xFF19C3B1, 0xFFFF6AA2, 0xFFFFB84D};

    public static class Lecture {
        public int id;
        public String title;
        public boolean hasVideo, hasPdf, free;
    }

    public static class UnitItem {
        public String name;
        public List<Lecture> lectures = new ArrayList<>();

        public int videos() { int n = 0; for (Lecture l : lectures) if (l.hasVideo) n++; return n; }
        public int pdfs() { int n = 0; for (Lecture l : lectures) if (l.hasPdf) n++; return n; }
        public int freeCount() { int n = 0; for (Lecture l : lectures) if (l.free) n++; return n; }
    }

    public static class Subject {
        public String name;
        public int tint;
        public List<UnitItem> units = new ArrayList<>();

        public int lectures() { int n = 0; for (UnitItem u : units) n += u.videos(); return n; }
    }

    public static class Batch {
        public String name;
        public int tint;
        public List<Subject> subjects = new ArrayList<>();

        public int lectures() { int n = 0; for (Subject s : subjects) n += s.lectures(); return n; }
    }

    /** Where to get a video or PDF: a URL plus any headers the request needs. */
    public static class PlaySource {
        public final String url;
        public final Map<String, String> headers;

        public PlaySource(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers;
        }
    }

    public static class AdminKey {
        public String key, deviceId, note, status, expiresAt;
        public int daysLeft;

        public static AdminKey from(JsonObject j) {
            AdminKey k = new AdminKey();
            k.key = str(j, "key");
            k.deviceId = str(j, "device_id");
            k.note = str(j, "note");
            k.status = str(j, "status");
            k.expiresAt = str(j, "expires_at");
            k.daysLeft = j.has("days_left") && !j.get("days_left").isJsonNull() ? j.get("days_left").getAsInt() : 0;
            return k;
        }
    }

    public static class AdminStats {
        public int total, active, expired, revoked;
        public int orders, revenue, ordersToday, revenueWeek;
        public List<String> top = new ArrayList<>();  // "title  -  12 views"

        public static AdminStats from(JsonObject j) {
            AdminStats s = new AdminStats();
            s.orders = num(j, "orders");
            s.revenue = num(j, "revenue");
            s.ordersToday = num(j, "orders_today");
            s.revenueWeek = num(j, "revenue_week");
            if (j.has("top_lectures") && j.get("top_lectures").isJsonArray()) {
                for (JsonElement e : j.getAsJsonArray("top_lectures")) {
                    JsonObject t = e.getAsJsonObject();
                    s.top.add(str(t, "title") + "  -  " + num(t, "views") + " views");
                }
            }
            s.total = num(j, "total");
            s.active = num(j, "active");
            s.expired = num(j, "expired");
            s.revoked = num(j, "revoked");
            return s;
        }
    }

    // ---------- JSON helpers ----------
    public static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return (e == null || e.isJsonNull()) ? "" : e.getAsString();
    }

    public static int num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return (e == null || e.isJsonNull()) ? 0 : e.getAsInt();
    }

    public static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    /** Turns the server's /catalog "batches" array into model objects. Colors are picked by position. */
    public static List<Batch> parseCatalog(JsonArray json) {
        List<Batch> out = new ArrayList<>();
        for (int bi = 0; bi < json.size(); bi++) {
            JsonObject b = json.get(bi).getAsJsonObject();
            Batch batch = new Batch();
            batch.name = str(b, "name");
            batch.tint = TINTS[bi % 4];
            JsonArray subjects = b.getAsJsonArray("subjects");
            for (int si = 0; si < subjects.size(); si++) {
                JsonObject s = subjects.get(si).getAsJsonObject();
                Subject subject = new Subject();
                subject.name = str(s, "name");
                subject.tint = TINTS[si % 4];
                for (JsonElement ue : s.getAsJsonArray("units")) {
                    JsonObject u = ue.getAsJsonObject();
                    UnitItem unit = new UnitItem();
                    unit.name = str(u, "name");
                    for (JsonElement le : u.getAsJsonArray("lectures")) {
                        JsonObject l = le.getAsJsonObject();
                        Lecture lec = new Lecture();
                        lec.id = num(l, "id");
                        lec.title = str(l, "title");
                        lec.hasVideo = bool(l, "has_video");
                        lec.hasPdf = bool(l, "has_pdf");
                        lec.free = bool(l, "free");
                        unit.lectures.add(lec);
                    }
                    subject.units.add(unit);
                }
                batch.subjects.add(subject);
            }
            out.add(batch);
        }
        return out;
    }
}
