package com.itachi.universe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Talks to the Render backend. Same endpoints as the old Flutter app. */
public final class Api {
    private Api() {}

    public static final String DEFAULT_BASE_URL = BuildConfig.DEFAULT_BASE_URL;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static class ApiException extends Exception {
        public final boolean unauthorized;
        public ApiException(String message, boolean unauthorized) { super(message); this.unauthorized = unauthorized; }
        public ApiException(String message) { this(message, false); }
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(ApiException e);
    }

    public interface Job<T> {
        T run() throws ApiException;
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)   // free servers wake up slowly
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    private static final ExecutorService io = Executors.newFixedThreadPool(4);
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static SharedPreferences prefs;
    private static String baseUrl = DEFAULT_BASE_URL;
    private static String adminToken;
    private static String premiumToken;

    public static void init(Context ctx) {
        try {
            MasterKey key = new MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            prefs = EncryptedSharedPreferences.create(ctx, "secure", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            prefs = ctx.getSharedPreferences("secure_fallback", Context.MODE_PRIVATE);
        }
        baseUrl = prefs.getString("base_url", DEFAULT_BASE_URL);
        adminToken = prefs.getString("admin_token", null);
        premiumToken = prefs.getString("premium_token", null);
    }

    /** Runs [job] on a background thread and delivers the result on the main thread. */
    public static <T> void async(Job<T> job, Callback<T> cb) {
        io.execute(() -> {
            try {
                T r = job.run();
                main.post(() -> cb.onSuccess(r));
            } catch (ApiException e) {
                main.post(() -> cb.onError(e));
            } catch (Exception e) {
                main.post(() -> cb.onError(new ApiException("Something went wrong.")));
            }
        });
    }

    // ---------- server address ----------
    public static String baseUrl() { return baseUrl; }

    public static void setBaseUrl(String url) {
        baseUrl = url.trim().replaceAll("/+$", "");
        prefs.edit().putString("base_url", baseUrl).apply();
    }

    // ---------- student (premium) session ----------
    public static boolean hasPremiumToken() { return premiumToken != null; }

    public static Map<String, String> authHeaders() {
        if (premiumToken == null) return Collections.emptyMap();
        Map<String, String> m = new HashMap<>();
        m.put("Authorization", "Bearer " + premiumToken);
        return m;
    }

    public static void activate(String key, String deviceId) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("key", key);
        body.addProperty("device_id", deviceId);
        JsonObject data = call("POST", "/activate", body).getAsJsonObject();
        premiumToken = data.get("token").getAsString();
        prefs.edit().putString("premium_token", premiumToken).apply();
    }

    public static void clearPremium() {
        premiumToken = null;
        prefs.edit().remove("premium_token").apply();
    }

    /** true = key valid, false = server says it is not, null = could not tell (no internet / server asleep). */
    public static Boolean checkPremium() {
        try {
            call("GET", "/me", null, 8);
            return true;
        } catch (ApiException e) {
            return e.unauthorized ? Boolean.FALSE : null;
        }
    }

    /** Asks the server where a lecture's video or PDF is. kind = "video" or "pdf". */
    public static Models.PlaySource playSource(int lectureId, String kind) throws ApiException {
        JsonObject data = call("GET", "/play/" + lectureId + "/" + kind, null).getAsJsonObject();
        if (data.has("kind") && "link".equals(data.get("kind").getAsString())) {
            return new Models.PlaySource(data.get("url").getAsString(), Collections.emptyMap());
        }
        return new Models.PlaySource(baseUrl + "/media/" + lectureId + "/" + kind, authHeaders());
    }

    public static JsonArray catalogJson() throws ApiException {
        return call("GET", "/catalog", null).getAsJsonObject().getAsJsonArray("batches");
    }

    // ---------- admin session ----------
    public static boolean hasAdminSession() { return adminToken != null; }

    public static void adminLogin(String password) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("password", password);
        JsonObject data = call("POST", "/admin/login", body).getAsJsonObject();
        adminToken = data.get("token").getAsString();
        prefs.edit().putString("admin_token", adminToken).apply();
    }

    public static void adminLogout() {
        adminToken = null;
        prefs.edit().remove("admin_token").apply();
    }

    // ---------- admin: keys ----------
    public static Models.AdminStats adminStats() throws ApiException {
        return Models.AdminStats.from(call("GET", "/admin/stats", null).getAsJsonObject());
    }

    public static java.util.List<Models.AdminKey> adminKeys(String q) throws ApiException {
        String enc;
        try { enc = URLEncoder.encode(q, "UTF-8"); } catch (Exception e) { enc = ""; }
        JsonArray arr = call("GET", "/admin/keys?q=" + enc, null).getAsJsonArray();
        java.util.List<Models.AdminKey> out = new java.util.ArrayList<>();
        for (JsonElement e : arr) out.add(Models.AdminKey.from(e.getAsJsonObject()));
        return out;
    }

    public static Models.AdminKey createKey(String deviceId, int days, String note) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("device_id", deviceId);
        body.addProperty("days", days);
        body.addProperty("note", note);
        return Models.AdminKey.from(call("POST", "/admin/keys", body).getAsJsonObject());
    }

    public static void revokeKey(String key) throws ApiException { call("POST", "/admin/keys/" + key + "/revoke", null); }
    public static void restoreKey(String key) throws ApiException { call("POST", "/admin/keys/" + key + "/restore", null); }

    public static void extendKey(String key, int days) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("days", days);
        call("POST", "/admin/keys/" + key + "/extend", body);
    }

    public static void deleteKey(String key) throws ApiException { call("DELETE", "/admin/keys/" + key, null); }

    /** Moves a key to another device. The old phone is logged out. */
    public static void changeKeyDevice(String key, String deviceId) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("device_id", deviceId);
        call("POST", "/admin/keys/" + key + "/device", body);
    }

    /** The About Developer page. Public. */
    public static JsonObject about() throws ApiException {
        return call("GET", "/about", null, 30).getAsJsonObject();
    }

    /** Admin only: replaces the whole About Developer page and returns what the server kept. */
    public static JsonObject saveAbout(JsonObject doc) throws ApiException {
        return call("PUT", "/admin/about", doc).getAsJsonObject();
    }

    /** A short-lived link for MX Player, VLC or PlayIt. The server refuses YouTube videos. */
    public static String externalLink(int lectureId) throws ApiException {
        return call("GET", "/play/" + lectureId + "/external", null).getAsJsonObject().get("url").getAsString();
    }

    /** Shop bot username and prices: {"bot": "...", "plans": [{name, days, price}]}. */
    public static JsonObject shopInfo() throws ApiException {
        return call("GET", "/shop/info", null, 30).getAsJsonObject();
    }

    // ---------- admin: content ----------
    public static JsonArray adminContent() throws ApiException {
        return call("GET", "/admin/content", null).getAsJsonArray();
    }

    /** kind: batches | subjects | units. parentId is the batch/subject it goes into (null for batches). */
    public static void addItem(String kind, String name, Integer parentId) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("parent_id", parentId);
        call("POST", "/admin/" + kind, body);
    }

    public static void renameItem(String kind, int id, String name) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        call("PATCH", "/admin/" + kind + "/" + id, body);
    }

    /** kind: batches | subjects | units | lectures. Everything inside goes too. */
    public static void deleteItem(String kind, int id) throws ApiException {
        call("DELETE", "/admin/" + kind + "/" + id, null);
    }

    public static void saveLecture(Integer id, Integer unitId, String title, String video, String pdf, boolean free)
            throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("title", title);
        body.addProperty("video_url", video);
        body.addProperty("pdf_url", pdf);
        body.addProperty("free", free);
        body.addProperty("unit_id", unitId);
        if (id == null) call("POST", "/admin/lectures", body);
        else call("PUT", "/admin/lectures/" + id, body);
    }

    // ---------- plumbing ----------
    private static JsonElement call(String method, String path, JsonObject body) throws ApiException {
        return call(method, path, body, 60);
    }

    private static JsonElement call(String method, String path, JsonObject body, int timeoutSeconds)
            throws ApiException {
        boolean isAdmin = path.startsWith("/admin");
        String token = isAdmin ? adminToken : premiumToken;

        Request.Builder rb = new Request.Builder().url(baseUrl + path);
        if (token != null) rb.header("Authorization", "Bearer " + token);
        String payload = body == null ? "{}" : body.toString();
        switch (method) {
            case "GET": rb.get(); break;
            case "DELETE": rb.delete(); break;
            case "PATCH": rb.patch(RequestBody.create(payload, JSON)); break;
            case "PUT": rb.put(RequestBody.create(payload, JSON)); break;
            default: rb.post(RequestBody.create(payload, JSON));
        }

        OkHttpClient c = timeoutSeconds == 60 ? client
                : client.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build();

        int code;
        String text;
        try (Response r = c.newCall(rb.build()).execute()) {
            code = r.code();
            text = r.body() == null ? "" : r.body().string();
        } catch (InterruptedIOException e) {
            throw new ApiException("The server is waking up. Try again in a minute.");
        } catch (IOException e) {
            throw new ApiException("Cannot reach the server. Check your internet and try again.");
        }

        JsonElement decoded = null;
        try { decoded = JsonParser.parseString(text); } catch (Exception ignored) {}

        if (code == 401 && !path.equals("/admin/login")) {
            if (isAdmin) {
                adminLogout();
                throw new ApiException("Session expired. Please log in again.", true);
            }
            clearPremium();
            throw new ApiException("Your premium key is no longer valid.", true);
        }
        if (code >= 400) {
            String message = "Something went wrong (" + code + ").";
            if (decoded != null && decoded.isJsonObject()) {
                JsonElement d = decoded.getAsJsonObject().get("detail");
                if (d != null && d.isJsonPrimitive()) message = d.getAsString();
            }
            throw new ApiException(message);
        }
        return decoded == null ? new JsonObject() : decoded;
    }

    /** Downloads a file (PDF). Used by the PDF screen. */
    public static byte[] download(String url, Map<String, String> headers) throws ApiException {
        Request.Builder rb = new Request.Builder().url(url);
        for (Map.Entry<String, String> e : headers.entrySet()) rb.header(e.getKey(), e.getValue());
        OkHttpClient c = client.newBuilder().callTimeout(90, TimeUnit.SECONDS).build();
        try (Response r = c.newCall(rb.build()).execute()) {
            if (r.code() == 401) {
                clearPremium();
                throw new ApiException("Your premium key is no longer valid.", true);
            }
            if (r.code() != 200 || r.body() == null) {
                throw new ApiException("This link is not a direct PDF file, so it cannot be shown here.");
            }
            byte[] b = r.body().bytes();
            boolean isPdf = b.length > 4 && b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46;
            if (!isPdf) throw new ApiException("This link is not a direct PDF file, so it cannot be shown here.");
            return b;
        } catch (IOException e) {
            throw new ApiException("Could not load this PDF. Check your internet and try again.");
        }
    }
}
