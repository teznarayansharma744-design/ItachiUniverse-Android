package com.itachi.universe;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public abstract class BaseActivity extends AppCompatActivity {
    public static final String ADMIN_TELEGRAM_URL = "https://t.me/Use_r_tg";

    protected UI.GlassBackground bg;
    protected FrameLayout holder;

    /** Shows [content] on the glass background, below the status bar and above the nav bar. */
    protected void setScreen(View content) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        bg = new UI.GlassBackground(this);
        holder = new FrameLayout(this);
        holder.addView(content, new FrameLayout.LayoutParams(-1, -1));
        bg.addView(holder, new FrameLayout.LayoutParams(-1, -1));
        setContentView(bg);
        ViewCompat.setOnApplyWindowInsetsListener(bg, (v, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            holder.setPadding(i.left, i.top, i.right, i.bottom);
            return insets;
        });
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), bg);
        c.setAppearanceLightStatusBars(!UI.dark(this));
        c.setAppearanceLightNavigationBars(!UI.dark(this));
    }

    /** A vertical scroll area with 24dp padding; add your views to the returned column. */
    protected android.widget.LinearLayout column() {
        android.widget.LinearLayout col = new android.widget.LinearLayout(this);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setPadding(UI.dp(this, 24), UI.dp(this, 24), UI.dp(this, 24), UI.dp(this, 24));
        return col;
    }

    protected ScrollView scroll(View child) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(child, new FrameLayout.LayoutParams(-1, -2));
        return sv;
    }

    protected boolean alive() { return !isFinishing() && !isDestroyed(); }

    /** Runs a server call in the background and calls back only while this screen is still open. */
    protected <T> void run(Api.Job<T> job, Api.Callback<T> cb) {
        Api.async(job, new Api.Callback<T>() {
            @Override public void onSuccess(T r) { if (alive()) cb.onSuccess(r); }
            @Override public void onError(Api.ApiException e) { if (alive()) cb.onError(e); }
        });
    }

    protected void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    protected void openAdminTelegram() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ADMIN_TELEGRAM_URL)));
        } catch (ActivityNotFoundException e) {
            toast("Could not open Telegram. Message @Use_r_tg directly.");
        }
    }

    /** Opens the shop bot in Telegram with this phone's Device ID, so it can show the price and
     *  start the payment straight away. Falls back to the admin chat if the shop is not ready. */
    protected void openShopBot() {
        final String device = DeviceId.get(this);
        toast("Opening Telegram...");
        run(Api::shopInfo, new Api.Callback<com.google.gson.JsonObject>() {
            @Override public void onSuccess(com.google.gson.JsonObject info) {
                String bot = info.get("bot").getAsString();
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/" + bot + "?start=" + device)));
                } catch (ActivityNotFoundException e) {
                    toast("Telegram is not installed. Search @" + bot + " in your browser.");
                }
            }
            @Override public void onError(Api.ApiException e) { openAdminTelegram(); }
        });
    }

    protected void copyToClipboard(String text, String done) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("itachi", text));
        toast(done);
    }
}
