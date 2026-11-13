package com.itachi.universe;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * In-app update. Each build is published as a GitHub Release (see the workflow). The app reads
 * the latest release, downloads its APK and opens Android's installer. Android always asks the
 * user to confirm the install, so an update can never be fully silent.
 */
public final class Updater {
    private Updater() {}

    private static final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static boolean checkedThisLaunch = false;

    static final class Info {
        int code;
        String name;
        String url;
        String notes;
    }

    private static boolean alive(Activity a) { return !a.isFinishing() && !a.isDestroyed(); }

    static Info fetchLatest() throws Api.ApiException {
        String repo = BuildConfig.UPDATE_REPO;
        if (repo == null || repo.isEmpty()) throw new Api.ApiException("Updates are not set up in this build.");
        Request req = new Request.Builder()
                .url("https://api.github.com/repos/" + repo + "/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (resp.code() == 404) throw new Api.ApiException("No update has been published yet.");
            if (!resp.isSuccessful() || resp.body() == null) throw new Api.ApiException("Could not check for updates.");
            JsonObject o = JsonParser.parseString(resp.body().string()).getAsJsonObject();
            Info info = new Info();
            info.code = Integer.parseInt(o.get("tag_name").getAsString().replaceAll("[^0-9]", ""));
            info.name = "1.0." + (info.code - 1000);
            info.notes = o.has("body") && !o.get("body").isJsonNull() ? o.get("body").getAsString().trim() : "";
            JsonArray assets = o.getAsJsonArray("assets");
            if (assets != null) {
                for (JsonElement e : assets) {
                    JsonObject asset = e.getAsJsonObject();
                    if (asset.get("name").getAsString().endsWith(".apk")) {
                        info.url = asset.get("browser_download_url").getAsString();
                        break;
                    }
                }
            }
            if (info.url == null) throw new Api.ApiException("The latest release has no APK file.");
            return info;
        } catch (IOException e) {
            throw new Api.ApiException("No internet connection.");
        } catch (RuntimeException e) {
            throw new Api.ApiException("Could not read the update information.");
        }
    }

    /** manual = the user tapped "Check for updates". Automatic checks only speak up if there is news. */
    public static void check(Activity a, boolean manual) {
        if (!manual && checkedThisLaunch) return;
        checkedThisLaunch = true;
        if (manual) Toast.makeText(a, "Checking for updates...", Toast.LENGTH_SHORT).show();
        Api.async(Updater::fetchLatest, new Api.Callback<Info>() {
            @Override public void onSuccess(Info i) {
                if (!alive(a)) return;
                if (i.code > BuildConfig.VERSION_CODE) showUpdate(a, i);
                else if (manual) Toast.makeText(a, "You are on the latest version.", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(Api.ApiException e) {
                if (manual && alive(a)) Toast.makeText(a, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void showUpdate(Activity a, Info i) {
        String msg = "Version " + i.name + " is ready to install.";
        if (!i.notes.isEmpty()) msg += "\n\n" + i.notes;
        new AlertDialog.Builder(a)
                .setTitle("Update available")
                .setMessage(msg)
                .setPositiveButton("Update", (d, w) -> download(a, i))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void download(Activity a, Info info) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("Allow installing updates")
                    .setMessage("Android needs your permission once. Tap Open settings, turn on \"Allow from this source\", "
                            + "then come back and tap Update again.")
                    .setPositiveButton("Open settings", (d, w) -> a.startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UI.dp(a, 24);
        box.setPadding(pad, pad, pad, UI.dp(a, 8));
        TextView label = new TextView(a);
        label.setText("Downloading 0%");
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        box.addView(label);
        box.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        label.setGravity(Gravity.START);

        AtomicBoolean cancel = new AtomicBoolean(false);
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Updating")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Cancel", (d, w) -> cancel.set(true))
                .create();
        dialog.show();

        new Thread(() -> {
            File dir = new File(a.getCacheDir(), "updates");
            dir.mkdirs();
            File apk = new File(dir, "itachi.apk");
            try (Response resp = http.newCall(new Request.Builder().url(info.url).build()).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) throw new IOException("bad response");
                long total = resp.body().contentLength();
                try (InputStream in = resp.body().byteStream(); FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buf = new byte[32768];
                    long done = 0;
                    int n, last = -1;
                    while ((n = in.read(buf)) > 0) {
                        if (cancel.get()) throw new IOException("cancelled");
                        out.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            int pct = (int) (done * 100 / total);
                            if (pct != last) {
                                last = pct;
                                final int p = pct;
                                main.post(() -> { bar.setProgress(p); label.setText("Downloading " + p + "%"); });
                            }
                        }
                    }
                }
                if (!looksLikeApk(apk)) throw new IOException("not an apk");
                main.post(() -> { dialog.dismiss(); if (alive(a)) install(a, apk); });
            } catch (IOException e) {
                boolean cancelled = cancel.get();
                main.post(() -> {
                    dialog.dismiss();
                    if (alive(a) && !cancelled) {
                        Toast.makeText(a, "Download failed. Check your internet and try again.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private static boolean looksLikeApk(File f) {
        if (f.length() < 100_000) return false;
        try (FileInputStream in = new FileInputStream(f)) {
            return in.read() == 'P' && in.read() == 'K';  // an APK is a zip file
        } catch (IOException e) {
            return false;
        }
    }

    private static void install(Activity a, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, "Could not open the installer.", Toast.LENGTH_LONG).show();
        }
    }
}
