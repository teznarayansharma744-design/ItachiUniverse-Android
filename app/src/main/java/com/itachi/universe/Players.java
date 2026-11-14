package com.itachi.universe;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.appcompat.app.AlertDialog;

/** The video players a student can choose: the built-in one, or MX Player, VLC and PlayIt. */
public final class Players {
    private Players() {}

    static final class Choice {
        final String label;
        final String[] packages;  // empty = the built-in player
        Choice(String label, String... packages) { this.label = label; this.packages = packages; }
        boolean builtIn() { return packages.length == 0; }
    }

    static final Choice[] ALL = {
            new Choice("Default player"),
            new Choice("PLAYit", "com.playit.videoplayer"),
            new Choice("MX Player", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro"),
            new Choice("VLC", "org.videolan.vlc"),
    };

    private static SharedPreferences prefs(Activity a) { return a.getSharedPreferences("ui", Activity.MODE_PRIVATE); }

    /** Asks which player to use, then opens the lecture in it. */
    static void choose(BaseActivity a, Models.Lecture lec, String subtitle) {
        int last = prefs(a).getInt("player", 0);
        String[] names = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) names[i] = ALL[i].label;
        new AlertDialog.Builder(a)
                .setTitle("Play with")
                .setSingleChoiceItems(names, last, (d, which) -> {
                    d.dismiss();
                    prefs(a).edit().putInt("player", which).apply();
                    open(a, ALL[which], lec, subtitle);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void playInApp(Activity a, Models.Lecture lec, String subtitle) {
        Intent i = new Intent(a, PlayerActivity.class);
        i.putExtra("id", lec.id);
        i.putExtra("title", lec.title);
        i.putExtra("subtitle", subtitle);
        a.startActivity(i);
    }

    private static void open(BaseActivity a, Choice c, Models.Lecture lec, String subtitle) {
        if (c.builtIn()) { playInApp(a, lec, subtitle); return; }
        a.toast("Opening " + c.label + "...");
        a.runJob(() -> Api.externalLink(lec.id), new Api.Callback<String>() {
            @Override public void onSuccess(String url) { launch(a, c, url, lec.title); }
            @Override public void onError(Api.ApiException e) {
                new AlertDialog.Builder(a)
                        .setTitle(c.label)
                        .setMessage(e.getMessage())
                        .setPositiveButton("Play in app", (d, w) -> playInApp(a, lec, subtitle))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private static void launch(Activity a, Choice c, String url, String title) {
        for (String pkg : c.packages) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(url), "video/*");
            i.setPackage(pkg);
            i.putExtra("title", title);
            try {
                a.startActivity(i);
                return;
            } catch (ActivityNotFoundException ignored) {
                // try the next package name, then offer the Play Store
            }
        }
        String pkg = c.packages[0];
        new AlertDialog.Builder(a)
                .setTitle(c.label + " is not installed")
                .setMessage("Install " + c.label + " from the Play Store to use it here.")
                .setPositiveButton("Install", (d, w) -> {
                    try {
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
                    } catch (ActivityNotFoundException e) {
                        a.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
