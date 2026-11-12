package com.itachi.universe;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;

/** Small helpers that build the "glass" look in code (same colors as the Flutter theme). */
public final class UI {
    private UI() {}

    public static final int TEAL = 0xFF19C3B1;
    public static final int INDIGO = 0xFF5B5BFF;
    public static final int PINK = 0xFFFF6AA2;

    // ---------- theme ----------
    public static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
    public static int bg(Context c) { return dark(c) ? 0xFF0B1020 : 0xFFEEF1FB; }
    public static int text(Context c) { return dark(c) ? Color.WHITE : 0xFF121528; }
    public static int dim(Context c) { return dark(c) ? 0xB8FFFFFF : 0xFF4A4F6A; }
    public static int fill(Context c) { return dark(c) ? 0x14FFFFFF : 0x8CFFFFFF; }
    public static int border(Context c) { return dark(c) ? 0x2EFFFFFF : 0xE6FFFFFF; }

    public static void toggleTheme(Context c) {
        boolean nowDark = dark(c);
        SharedPreferences p = c.getSharedPreferences("ui", Context.MODE_PRIVATE);
        p.edit().putBoolean("dark", !nowDark).apply();
        AppCompatDelegate.setDefaultNightMode(
                !nowDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    // ---------- sizes ----------
    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams lpMargin(Context c, int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static View space(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(lp(1, dp(c, heightDp)));
        return v;
    }

    // ---------- views ----------
    public static TextView tv(Context c, String s, float sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static GradientDrawable box(int fill, float radiusPx, int stroke, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (stroke != 0) g.setStroke(strokePx, stroke);
        return g;
    }

    /** Glass card (vertical). gradient = null for the plain translucent fill. */
    public static LinearLayout card(Context c, float radiusDp, int padDp, int[] gradient, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, padDp), dp(c, padDp), dp(c, padDp), dp(c, padDp));
        GradientDrawable g;
        if (gradient != null) {
            g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, gradient);
        } else {
            g = new GradientDrawable();
            g.setColor(fill(c));
        }
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, 1)), border(c));
        int ripple = dark(c) ? 0x33FFFFFF : 0x22000000;
        l.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), g, null));
        if (click != null) {
            l.setClickable(true);
            l.setOnClickListener(click);
        }
        return l;
    }

    /** Rounded square with an emoji/glyph inside. */
    public static TextView iconTile(Context c, String glyph, int tint, int sizeDp) {
        TextView t = new TextView(c);
        t.setText(glyph);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.42f);
        t.setTextColor(Color.WHITE);
        t.setBackground(box((tint & 0x00FFFFFF) | 0x50000000, dp(c, sizeDp / 3f), 0, 0));
        t.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    public static TextView pillButton(Context c, String label, View.OnClickListener click) {
        TextView t = tv(c, label, 13, true, text(c));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 12), dp(c, 11), dp(c, 12), dp(c, 11));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                box(dark(c) ? 0x18FFFFFF : 0x18000000, dp(c, 14), border(c), 1), null));
        t.setOnClickListener(click);
        return t;
    }

    /** Big gradient button (indigo to teal). */
    public static TextView gradientButton(Context c, String label, View.OnClickListener click) {
        TextView t = tv(c, label, 17, true, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{INDIGO, TEAL});
        g.setCornerRadius(dp(c, 18));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
        t.setOnClickListener(click);
        t.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 56)));
        return t;
    }

    public static TextView textButton(Context c, String label, View.OnClickListener click) {
        TextView t = tv(c, label, 14, true, 0xFF7C7CFF);
        t.setPadding(dp(c, 8), dp(c, 10), dp(c, 8), dp(c, 10));
        t.setOnClickListener(click);
        return t;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(text(c));
        e.setHintTextColor(dim(c));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        e.setBackground(box(fill(c), dp(c, 16), border(c), 1));
        return e;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Back button + small caption + title at the top of inner screens. */
    public static LinearLayout backHeader(Activity a, String caption, String title) {
        return backHeader(a, caption, title, a::finish);
    }

    public static LinearLayout backHeader(Activity a, String caption, String title, Runnable onBack) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout back = card(a, 14, 0, null, v -> onBack.run());
        TextView arrow = tv(a, "\u2039", 26, true, text(a));
        arrow.setGravity(Gravity.CENTER);
        back.addView(arrow, lp(dp(a, 44), dp(a, 44)));
        row.addView(back, lp(dp(a, 44), dp(a, 44)));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView cap = tv(a, caption, 12, false, dim(a));
        cap.setSingleLine(true);
        cap.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView tt = tv(a, title, 18, true, text(a));
        tt.setSingleLine(true);
        tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(cap);
        col.addView(tt);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = dp(a, 12);
        row.addView(col, cp);
        return row;
    }

    // ---------- background ----------
    /** Soft colored glow behind everything, drawn with plain radial gradients. */
    public static class GlassBackground extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public GlassBackground(Context c) {
            super(c);
            setWillNotDraw(false);
        }

        private void glow(Canvas cv, float cx, float cy, float r, int color, int alphaPct) {
            int a = Math.round(255 * alphaPct / 100f);
            int c0 = (color & 0x00FFFFFF) | (a << 24);
            int c1 = color & 0x00FFFFFF;
            paint.setShader(new RadialGradient(cx, cy, r, c0, c1, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Context c = getContext();
            boolean d = dark(c);
            cv.drawColor(bg(c));
            float w = getWidth(), h = getHeight(), u = getResources().getDisplayMetrics().density;
            glow(cv, 90 * u, 110 * u, 230 * u, d ? 0xFF5B5BFF : 0xFF8E8EFF, 50);
            glow(cv, w - 40 * u, 440 * u, 220 * u, d ? 0xFF19C3B1 : 0xFF6FE0D2, 38);
            glow(cv, 100 * u, h - 70 * u, 220 * u, d ? 0xFFFF6AA2 : 0xFFFFA3C6, 34);
        }
    }
}
