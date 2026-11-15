package com.itachi.universe;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** A glass pill that springs up from the bottom, then slides away. Replaces the plain system Toast. */
public final class GlassToast {
    private GlassToast() {}

    private static View current;

    public static void show(Activity a, String msg) {
        if (a == null || a.isFinishing() || a.isDestroyed() || msg == null || msg.isEmpty()) return;
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) return;
        if (current != null && current.getParent() != null) ((ViewGroup) current.getParent()).removeView(current);

        boolean dark = UI.dark(a);
        TextView t = UI.tv(a, msg, 14, true, dark ? Color.WHITE : 0xFF121528);
        t.setGravity(Gravity.CENTER);
        t.setMaxWidth(UI.dp(a, 320));
        t.setPadding(UI.dp(a, 20), UI.dp(a, 13), UI.dp(a, 20), UI.dp(a, 13));
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? 0xE61A2142 : 0xECF6F7FF);
        g.setCornerRadius(UI.dp(a, 26));
        g.setStroke(Math.max(1, UI.dp(a, 1)), dark ? 0x55FFFFFF : 0xFFFFFFFF);
        t.setBackground(g);
        t.setElevation(UI.dp(a, 10));

        int bottom = UI.dp(a, 36);
        WindowInsetsCompat wi = ViewCompat.getRootWindowInsets(content);
        if (wi != null) {
            Insets i = wi.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            bottom += i.bottom;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = bottom;
        t.setAlpha(0f);
        t.setScaleX(0.9f);
        t.setScaleY(0.9f);
        t.setTranslationY(UI.dp(a, 40));
        content.addView(t, lp);
        current = t;
        t.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(460).setInterpolator(Motion.SPRING).start();
        long hold = Math.min(4500, 1700 + msg.length() * 45L);
        t.postDelayed(() -> {
            if (t.getParent() == null) return;
            t.animate().alpha(0f).translationY(UI.dp(a, 24)).scaleX(0.95f).scaleY(0.95f).setDuration(260)
                    .withEndAction(() -> { if (t.getParent() != null) ((ViewGroup) t.getParent()).removeView(t); if (current == t) current = null; })
                    .start();
        }, hold);
    }
}
