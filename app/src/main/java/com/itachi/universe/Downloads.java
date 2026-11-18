package com.itachi.universe;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Offline videos. They are saved inside the app's own private folder (never the gallery or the
 * downloads folder), under a plain numeric name with no video extension, so no other app, file
 * manager or gallery can see them. Uninstalling the app removes them.
 */
public final class Downloads {
    private Downloads() {}

    public static final class Item {
        public int id;
        public String title = "", subtitle = "";
        public long size;
    }

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).followRedirects(true).build();
    private static final Map<Integer, Integer> progress = new ConcurrentHashMap<>();   // id -> 0..100
    private static final Map<Integer, AtomicBoolean> cancels = new ConcurrentHashMap<>();
    private static final Map<Integer, String> ytProcesses = new ConcurrentHashMap<>();
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Runnable r) { listeners.add(r); }
    public static void removeListener(Runnable r) { listeners.remove(r); }
    private static void ping() { main.post(() -> { for (Runnable r : listeners) r.run(); }); }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "vault");
        d.mkdirs();
        return d;
    }
    public static File file(Context c, int id) { return new File(dir(c), "v" + id + ".bin"); }
    private static File part(Context c, int id) { return new File(dir(c), "v" + id + ".part"); }

    public static boolean has(Context c, int id) { return file(c, id).length() > 0; }
    /** -1 = not downloading, otherwise 0..100. */
    public static int progress(int id) { Integer p = progress.get(id); return p == null ? -1 : p; }
    public static int activeCount() { return progress.size(); }
    public static int averageProgress() {
        if (progress.isEmpty()) return 0;
        int t = 0;
        for (int p : progress.values()) t += p;
        return t / progress.size();
    }

    // ---------- offline PDFs ----------
    public static File pdfFile(Context c, int id) { return new File(dir(c), "p" + id + ".bin"); }
    public static boolean hasPdf(Context c, int id) { return pdfFile(c, id).length() > 0; }

    public static void savePdf(Context c, int id, String title, String subtitle, byte[] data) throws IOException {
        File tmp = new File(dir(c), "p" + id + ".part");
        try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(data); }
        if (!tmp.renameTo(pdfFile(c, id))) throw new IOException("Could not save the PDF.");
        Item it = new Item();
        it.id = id; it.title = title; it.subtitle = subtitle == null ? "" : subtitle;
        rememberIn(c, "pdfs", it);
        ping();
    }

    public static void deletePdf(Context c, int id) {
        pdfFile(c, id).delete();
        JsonArray arr = new JsonArray();
        for (Item o : listPdfs(c)) if (o.id != id) arr.add(toJson(o));
        prefs(c).edit().putString("pdfs", arr.toString()).apply();
        ping();
    }

    public static List<Item> listPdfs(Context c) {
        List<Item> out = new ArrayList<>();
        for (Item it : readItems(c, "pdfs")) {
            if (hasPdf(c, it.id)) { it.size = pdfFile(c, it.id).length(); out.add(it); }
        }
        return out;
    }

    // ---------- list ----------
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("downloads", Context.MODE_PRIVATE); }

    public static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        for (Item it : readItems(c, "items")) {
            if (has(c, it.id)) { it.size = file(c, it.id).length(); out.add(it); }
        }
        return out;
    }

    private static List<Item> readItems(Context c, String key) {
        List<Item> out = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(prefs(c).getString(key, "[]")).getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                Item it = new Item();
                it.id = o.get("id").getAsInt();
                it.title = o.has("title") ? o.get("title").getAsString() : "";
                it.subtitle = o.has("subtitle") ? o.get("subtitle").getAsString() : "";
                out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void remember(Context c, Item it) { rememberIn(c, "items", it); }

    private static void rememberIn(Context c, String key, Item it) {
        JsonArray arr = new JsonArray();
        for (Item o : readItems(c, key)) if (o.id != it.id) arr.add(toJson(o));
        arr.add(toJson(it));
        prefs(c).edit().putString(key, arr.toString()).apply();
    }

    private static JsonObject toJson(Item i) {
        JsonObject o = new JsonObject();
        o.addProperty("id", i.id);
        o.addProperty("title", i.title);
        o.addProperty("subtitle", i.subtitle);
        return o;
    }

    public static void delete(Context c, int id) {
        file(c, id).delete();
        part(c, id).delete();
        JsonArray arr = new JsonArray();
        for (Item o : list(c)) if (o.id != id) arr.add(toJson(o));
        prefs(c).edit().putString("items", arr.toString()).apply();
        ping();
    }

    public static long totalBytes(Context c) {
        long t = 0;
        for (Item i : list(c)) t += i.size;
        return t;
    }

    public static void cancel(int id) {
        AtomicBoolean f = cancels.get(id);
        if (f != null) f.set(true);
        String processId = ytProcesses.get(id);
        if (processId != null) {
            try { YoutubeDL.getInstance().destroyProcessById(processId); }
            catch (Exception ignored) { }
        }
    }

    /** yt-dlp writes into the same app-private vault; the temporary extension is removed on commit. */
    private static void downloadYoutube(Context c, int id, String url, AtomicBoolean cancel) throws Exception {
        File d = dir(c);
        String prefix = "yt" + id;
        File[] stale = d.listFiles((parent, name) -> name.startsWith(prefix + "."));
        if (stale != null) for (File f : stale) f.delete();

        String processId = "itachi-youtube-" + id;
        ytProcesses.put(id, processId);
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--no-playlist");
        request.addOption("--no-part");
        request.addOption("--no-mtime");
        request.addOption("--no-warnings");
        // Prefer a single-file MP4 containing both audio and video; fall back to the best muxed format.
        request.addOption("-f", "best[ext=mp4]/best");
        request.addOption("-o", new File(d, prefix + ".%(ext)s").getAbsolutePath());
        try {
            YoutubeDL.getInstance().execute(request, processId, (percent, etaSeconds, line) -> {
                if (cancel.get()) {
                    try { YoutubeDL.getInstance().destroyProcessById(processId); }
                    catch (Exception ignored) { }
                    return kotlin.Unit.INSTANCE;
                }
                int p = Math.max(0, Math.min(99, Math.round(percent)));
                progress.put(id, p);
                ping();
                return kotlin.Unit.INSTANCE;
            });
        } finally {
            ytProcesses.remove(id);
        }
        if (cancel.get()) throw new IOException("cancelled");

        File[] outputs = d.listFiles((parent, name) -> name.startsWith(prefix + ".") && !name.endsWith(".part"));
        if (outputs == null || outputs.length == 0 || outputs[0].length() == 0)
            throw new IOException("yt-dlp did not produce a video file. The video may be unavailable or restricted.");
        File output = outputs[0];
        if (!output.renameTo(file(c, id))) throw new IOException("Could not move the downloaded video into private storage.");
    }

    // ---------- download ----------
    /** Starts saving a lecture's video. onFail runs on the main thread with a readable message. */
    public static void start(Context ctx, int id, String title, String subtitle, java.util.function.Consumer<String> onFail) {
        final Context c = ctx.getApplicationContext();
        if (progress.containsKey(id) || has(c, id)) return;
        final AtomicBoolean cancel = new AtomicBoolean(false);
        cancels.put(id, cancel);
        progress.put(id, 0);
        ping();
        ContextCompat.startForegroundService(c, new Intent(c, DownloadService.class));

        new Thread(() -> {
            String error = null;
            try {
                Models.PlaySource src = Api.playSource(id, "video");
                String lower = src.url.toLowerCase();
                if (MediaLinks.youtubeId(src.url) != null) {
                    downloadYoutube(c, id, src.url, cancel);
                    Item it = new Item();
                    it.id = id; it.title = title; it.subtitle = subtitle;
                    it.size = file(c, id).length();
                    remember(c, it);
                } else {
                if (lower.contains(".m3u8")) throw new IOException("This video is a live-style stream and cannot be saved.");
                File part = part(c, id);
                long have = part.exists() ? part.length() : 0;
                Request.Builder rb = new Request.Builder().url(src.url);
                for (Map.Entry<String, String> h : src.headers.entrySet()) rb.header(h.getKey(), h.getValue());
                if (have > 0) rb.header("Range", "bytes=" + have + "-");
                try (Response resp = http.newCall(rb.build()).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) throw new IOException("The server did not give the video (" + resp.code() + ").");
                    String type = String.valueOf(resp.header("Content-Type", "")).toLowerCase();
                    if (type.startsWith("text/")) throw new IOException("This link is a web page, not a video file.");
                    boolean resumed = resp.code() == 206 && have > 0;
                    if (!resumed) have = 0;
                    long total = resp.body().contentLength() + have;
                    try (InputStream in = resp.body().byteStream(); FileOutputStream out = new FileOutputStream(part, resumed)) {
                        byte[] buf = new byte[65536];
                        long done = have;
                        int n, last = -1;
                        while ((n = in.read(buf)) > 0) {
                            if (cancel.get()) throw new IOException("cancelled");
                            out.write(buf, 0, n);
                            done += n;
                            if (total > 0) {
                                int p = (int) Math.min(99, done * 100 / total);
                                if (p != last) { last = p; progress.put(id, p); ping(); }
                            }
                        }
                    }
                }
                File fin = file(c, id);
                if (!part.renameTo(fin)) throw new IOException("Could not save the file.");
                Item it = new Item();
                it.id = id; it.title = title; it.subtitle = subtitle;
                remember(c, it);
                }
            } catch (Exception e) {
                boolean wasCancelled = cancel.get() || "cancelled".equals(e.getMessage());
                error = wasCancelled ? null : (e.getMessage() == null ? "Download failed." : e.getMessage());
                if (wasCancelled) {
                    part(c, id).delete();
                    File[] ytFiles = dir(c).listFiles((parent, name) -> name.startsWith("yt" + id + "."));
                    if (ytFiles != null) for (File f : ytFiles) f.delete();
                }
            } finally {
                progress.remove(id);
                cancels.remove(id);
                ping();
            }
            if (error != null && onFail != null) { final String m = error; main.post(() -> onFail.accept(m)); }
        }).start();
    }
}
