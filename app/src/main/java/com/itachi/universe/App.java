package com.itachi.universe;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Api.init(this);
        SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        boolean dark = p.getBoolean("dark", true);
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
