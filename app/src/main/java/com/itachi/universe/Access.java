package com.itachi.universe;

import android.app.Activity;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

/** Demo: everything visible but locked, except free items. Premium: everything unlocked. */
public final class Access {
    private Access() {}

    public static boolean premium = false;

    public static boolean isDemo() { return !premium; }

    public static void showPremiumRequired(Activity a) {
        new AlertDialog.Builder(a)
                .setTitle("Locked in Demo Mode")
                .setMessage("This lecture is for premium users. Enter your key to unlock everything.")
                .setPositiveButton("Enter Premium Key", (d, w) -> a.startActivity(new Intent(a, KeyActivity.class)))
                .setNegativeButton("Not now", null)
                .show();
    }
}
