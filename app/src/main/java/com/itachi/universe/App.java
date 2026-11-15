package com.itachi.universe;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;

import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Api.init(this);
        try {
            YoutubeDL.getInstance().init(this);
        } catch (YoutubeDLException e) {
            Log.e("ItachiUniverse", "yt-dlp initialization failed", e);
        }
        SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        boolean dark = p.getBoolean("dark", true);
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
