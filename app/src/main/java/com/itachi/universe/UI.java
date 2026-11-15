package com.itachi.universe;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
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
    public static int bg(Context c) { return dark(c) ? 0xFF0A0E22 : 0xFFE9EDFA; }
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
    /** How much bigger or smaller than a normal phone (390dp wide) this screen is. Uses the short
     *  side, so it stays the same when the phone rotates. Small phones shrink a little, tablets grow. */
    public static float scale(Context c) {
        int sw = c.getResources().getConfiguration().smallestScreenWidthDp;
        if (sw <= 0) return 1f;
        return Math.max(0.85f, Math.min(1.3f, sw / 390f));
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v * scale(c), c.getResources().getDisplayMetrics()));
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
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * scale(c));
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

    /** Glass card (vertical). A gradient only lends its first color as a tint; null = clear glass. */
    public static LinearLayout card(Context c, float radiusDp, int padDp, int[] gradient, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, padDp), dp(c, padDp), dp(c, padDp), dp(c, padDp));
        GlassDrawable g = new GlassDrawable(c, dp(c, radiusDp), gradient != null ? gradient[0] : 0, 1f);
        l.setBackground(g);
        if (click != null) {
            l.setForeground(rippleOver(c, dp(c, radiusDp)));
            l.setClickable(true);
            l.setOnClickListener(click);
            Motion.press(l);
        }
        return l;
    }

    /** A soft press ripple clipped to a rounded rectangle, drawn above the glass. */
    public static android.graphics.drawable.Drawable rippleOver(Context c, float radiusPx) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.BLACK);
        mask.setCornerRadius(radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(dark(c) ? 0x33FFFFFF : 0x22000000), null, mask);
    }

    /** Glass background for small controls. */
    public static void glassBg(View v, float radiusDp, float strength) {
        Context c = v.getContext();
        v.setBackground(new GlassDrawable(c, dp(c, radiusDp), 0, strength));
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
        t.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        t.setBackground(new GlassDrawable(c, dp(c, 15), 0, 1.5f));
        t.setForeground(rippleOver(c, dp(c, 15)));
        t.setOnClickListener(click);
        Motion.press(t);
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
        Motion.press(t);
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
        e.setBackground(new GlassDrawable(c, dp(c, 16), 0, 1.2f));
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
    /** The calm midnight (or soft daylight) backdrop: one glow at the top over a vertical gradient. No shapes. */
    public static class GlassBackground extends FrameLayout {
        private final Paint base = new Paint();
        private final Paint glow = new Paint();

        public GlassBackground(Context c) {
            super(c);
            setWillNotDraw(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            boolean d = dark(getContext());
            base.setShader(new LinearGradient(0, 0, 0, h,
                    d ? new int[]{0xFF0E1430, 0xFF0A0E22, 0xFF070A18} : new int[]{0xFFF1F3FF, 0xFFE9EDFA, 0xFFDDE2F4},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            float r = Math.max(w, 1) * 0.95f;
            glow.setShader(new RadialGradient(w / 2f, -0.04f * h, r,
                    d ? 0xFF2B3A8C : 0xFFC4CFFF, d ? 0x002B3A8C : 0x00C4CFFF, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas cv) {
            cv.drawPaint(base);
            cv.drawPaint(glow);
        }
    }
}
