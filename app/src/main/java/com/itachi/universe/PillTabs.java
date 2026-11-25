package com.itachi.universe;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tab bar where the chosen tab grows into a coloured pill (icon + name) while the tab you left
 * folds back into a plain icon, all in one spring-driven motion.
 */
public class PillTabs extends LinearLayout {
    public interface OnSelect { void on(int index); }

    private static final class Tab {
        LinearLayout box;
        ImageView icon;
        TextView label;
        GradientDrawable pill;
        int labelW;
        float f;
        ValueAnimator anim;
    }

    private final Tab[] tabs;
    private final int[] icons;
    private final OnSelect cb;
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private int selected = -1;

    public PillTabs(Context c, int[] icons, String[] labels, OnSelect cb) {
        super(c);
        this.icons = icons;
        this.cb = cb;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int p = UI.dp(c, 6);
        setPadding(p, p, p, p);
        setBackground(new GlassDrawable(c, UI.dp(c, 26), 0, 1f));
        tabs = new Tab[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Tab t = new Tab();
            t.box = new LinearLayout(c);
            t.box.setGravity(Gravity.CENTER);
            t.box.setOrientation(HORIZONTAL);
            t.box.setPadding(UI.dp(c, 8), UI.dp(c, 10), UI.dp(c, 8), UI.dp(c, 10));
            t.pill = new GradientDrawable();
            t.pill.setColor(UI.TEAL);
            t.pill.setCornerRadius(UI.dp(c, 20));
            t.pill.setAlpha(0);
            t.box.setBackground(t.pill);
            t.icon = UI.iconView(c, icons[i], 22, UI.dim(c));
            t.box.addView(t.icon, UI.lp(UI.dp(c, 22), UI.dp(c, 22)));
            t.label = UI.tv(c, labels[i], 13, true, Color.WHITE);
            t.label.setSingleLine(true);
            t.label.setPadding(UI.dp(c, 6), 0, 0, 0);
            t.label.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            t.labelW = t.label.getMeasuredWidth();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2);
            t.box.addView(t.label, lp);
            t.box.setOnClickListener(v -> { if (idx != selected) { select(idx, true); if (cb != null) cb.on(idx); } });
            addView(t.box, new LinearLayout.LayoutParams(0, -2, 1f));
            tabs[i] = t;
            apply(t, i, 0f);
        }
    }

    public int getSelected() { return selected; }

    /** Moves the pill to tab [i]; the old tab folds up while the new one grows. */
    public void select(int i, boolean animate) {
        if (i < 0 || i >= tabs.length || i == selected) return;
        int old = selected;
        selected = i;
        for (int k = 0; k < tabs.length; k++) {
            float to = k == i ? 1f : 0f;
            if (!animate) { setF(k, to); continue; }
            if (k != i && k != old) continue;
            final int kk = k;
            Tab t = tabs[k];
            if (t.anim != null) t.anim.cancel();
            t.anim = ValueAnimator.ofFloat(t.f, to);
            t.anim.setDuration(k == i ? 520 : 320);
            t.anim.setInterpolator(k == i ? Motion.SPRING : Motion.SMOOTH);
            t.anim.addUpdateListener(a -> setF(kk, (float) a.getAnimatedValue()));
            t.anim.start();
        }
        if (animate) {
            tabs[i].box.setScaleX(0.9f);
            tabs[i].box.animate().scaleX(1f).setDuration(420).setInterpolator(Motion.SPRING).start();
        }
    }

    private void setF(int k, float f) { apply(tabs[k], k, f); }

    private void apply(Tab t, int k, float f) {
        t.f = f;
        float s = Math.max(0f, Math.min(1f, f));
        t.pill.setAlpha(Math.round(255 * s));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.label.getLayoutParams();
        lp.width = Math.round(t.labelW * Math.max(0f, f));
        t.label.setLayoutParams(lp);
        t.label.setAlpha(s * s);
        ((LinearLayout.LayoutParams) t.box.getLayoutParams()).weight = 1f + 0.9f * f;
        int col = (int) argb.evaluate(s, UI.dim(getContext()), Color.WHITE);
        t.icon.setImageDrawable(UI.icon(getContext(), icons[k], 22, col));
        t.box.requestLayout();
    }
}
