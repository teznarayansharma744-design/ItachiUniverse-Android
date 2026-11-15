package com.itachi.universe;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.IntConsumer;

/**
 * Pop-ups with a frosted-glass look: the screen behind is blurred (Android 12+) and dimmed, the card
 * is translucent with a bright rim, and it springs in and out. Same builder calls as AlertDialog.
 */
public class GlassDialog extends Dialog {
    private final Content spec;
    private View card;
    private boolean closing;

    private static final class Content {
        CharSequence title, message;
        View view;
        CharSequence posText, negText, neuText;
        DialogInterface.OnClickListener pos, neg, neu;
        boolean cancelable = true;
        String[] items;
        IntConsumer onItem;
    }

    public static class Builder {
        private final Context ctx;
        private final Content c = new Content();
        public Builder(Context ctx) { this.ctx = ctx; }
        public Builder setTitle(CharSequence t) { c.title = t; return this; }
        public Builder setMessage(CharSequence m) { c.message = m; return this; }
        public Builder setView(View v) { c.view = v; return this; }
        public Builder setCancelable(boolean b) { c.cancelable = b; return this; }
        public Builder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) { c.posText = t; c.pos = l; return this; }
        public Builder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) { c.negText = t; c.neg = l; return this; }
        public Builder setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) { c.neuText = t; c.neu = l; return this; }
        public GlassDialog create() { return new GlassDialog(ctx, c); }
        public GlassDialog show() { GlassDialog d = create(); d.show(); return d; }
    }

    /** A small glass menu (replaces PopupMenu). */
    public static void menu(Context ctx, String title, String[] items, IntConsumer onPick) {
        Content c = new Content();
        c.title = title;
        c.items = items;
        c.onItem = onPick;
        new GlassDialog(ctx, c).show();
    }

    private GlassDialog(Context ctx, Content spec) {
        super(ctx, R.style.GlassDialogTheme);
        this.spec = spec;
        setCancelable(spec.cancelable);
        setCanceledOnTouchOutside(spec.cancelable);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Context c = getContext();
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setDimAmount(UI.dark(c) ? 0.42f : 0.28f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (Build.VERSION.SDK_INT >= 31) {  // the real frosted glass: blur everything behind the pop-up
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.setBlurBehindRadius(UI.dp(c, 22));
                w.setAttributes(lp);
            }
        }

        FrameLayout root = new FrameLayout(c);
        root.setOnClickListener(v -> { if (spec.cancelable) cancel(); });
        int margin = UI.dp(c, 28);
        root.setPadding(margin, margin, margin, margin);

        card = buildCard(c);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                Math.min(UI.dp(c, 360), c.getResources().getDisplayMetrics().widthPixels - 2 * margin),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        card.setClickable(true);  // taps on the card must not close the dialog
        root.addView(card, cp);
        setContentView(root);

        card.setAlpha(0f);
        card.setScaleX(0.86f);
        card.setScaleY(0.86f);
        card.setTranslationY(UI.dp(c, 26));
        card.post(() -> card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(460).setInterpolator(Motion.SPRING).start());
    }

    private View buildCard(Context c) {
        boolean dark = UI.dark(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = UI.dp(c, 22);
        col.setPadding(pad, pad, pad, UI.dp(c, 16));

        float r = UI.dp(c, 28);
        GradientDrawable base = new GradientDrawable();
        base.setColor(dark ? 0xCC141A33 : 0xCCF3F5FF);   // dense enough to read on phones without blur
        base.setCornerRadius(r);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0x3CFFFFFF, 0x08FFFFFF} : new int[]{0x99FFFFFF, 0x22FFFFFF});
        sheen.setCornerRadius(r);
        sheen.setStroke(Math.max(1, UI.dp(c, 1)), dark ? 0x4DFFFFFF : 0xCCFFFFFF);  // bright glass rim
        col.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[]{base, sheen}));
        col.setElevation(UI.dp(c, 18));

        int text = UI.text(c), dim = UI.dim(c);
        if (spec.title != null && spec.title.length() > 0) {
            TextView t = UI.tv(c, spec.title.toString(), 19, true, text);
            col.addView(t, UI.matchWrap());
            col.addView(UI.space(c, 8));
        }
        if (spec.message != null && spec.message.length() > 0) {
            TextView m = UI.tv(c, spec.message.toString(), 15, false, dim);
            m.setLineSpacing(0, 1.12f);
            ScrollView sv = new ScrollView(c);
            sv.addView(m);
            col.addView(sv, new LinearLayout.LayoutParams(-1, -2));
            col.addView(UI.space(c, 8));
        }
        if (spec.view != null) {
            if (spec.view.getParent() instanceof ViewGroup) ((ViewGroup) spec.view.getParent()).removeView(spec.view);
            ScrollView sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(spec.view);
            int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.55f);
            col.addView(sv, new LinearLayout.LayoutParams(-1, -2));
            sv.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                if (sv.getHeight() > maxH) { ViewGroup.LayoutParams lp = sv.getLayoutParams(); lp.height = maxH; sv.setLayoutParams(lp); }
            });
            // negative margins of the old AlertDialog view padding are kept by the view itself
        }
        if (spec.items != null) {
            for (int i = 0; i < spec.items.length; i++) {
                final int idx = i;
                boolean danger = spec.items[i].toLowerCase().startsWith("delete") || spec.items[i].toLowerCase().startsWith("revoke");
                TextView row = UI.tv(c, spec.items[i], 16, true, danger ? 0xFFFF6B7A : text);
                row.setPadding(UI.dp(c, 14), UI.dp(c, 14), UI.dp(c, 14), UI.dp(c, 14));
                row.setBackground(UI.box(dark ? 0x14FFFFFF : 0x12000000, UI.dp(c, 16), 0, 0));
                row.setOnClickListener(v -> { dismiss(); if (spec.onItem != null) spec.onItem.accept(idx); });
                Motion.press(row);
                LinearLayout.LayoutParams lp = UI.matchWrap();
                lp.topMargin = UI.dp(c, 8);
                col.addView(row, lp);
            }
        }

        // buttons: a row for one or two, a stack for three
        int n = (spec.posText != null ? 1 : 0) + (spec.negText != null ? 1 : 0) + (spec.neuText != null ? 1 : 0);
        if (n > 0) {
            LinearLayout bar = new LinearLayout(c);
            bar.setOrientation(n == 3 ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            if (spec.neuText != null) addButton(c, bar, spec.neuText, spec.neu, DialogInterface.BUTTON_NEUTRAL, false, n);
            if (spec.negText != null) addButton(c, bar, spec.negText, spec.neg, DialogInterface.BUTTON_NEGATIVE, false, n);
            if (spec.posText != null) addButton(c, bar, spec.posText, spec.pos, DialogInterface.BUTTON_POSITIVE, true, n);
            LinearLayout.LayoutParams bp = UI.matchWrap();
            bp.topMargin = UI.dp(c, 14);
            col.addView(bar, bp);
        } else if (spec.items == null) {
            col.addView(UI.space(c, 6));
        }
        return col;
    }

    private void addButton(Context c, LinearLayout bar, CharSequence label, DialogInterface.OnClickListener l, int which, boolean primary, int n) {
        boolean dark = UI.dark(c);
        TextView b = UI.tv(c, label.toString(), 15, true, primary ? Color.WHITE : UI.text(c));
        b.setGravity(Gravity.CENTER);
        b.setPadding(UI.dp(c, 12), UI.dp(c, 13), UI.dp(c, 12), UI.dp(c, 13));
        GradientDrawable bg;
        if (primary) {
            bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{UI.INDIGO, UI.TEAL});
        } else {
            bg = new GradientDrawable();
            bg.setColor(dark ? 0x1FFFFFFF : 0x14000000);
        }
        bg.setCornerRadius(UI.dp(c, 16));
        b.setBackground(bg);
        b.setOnClickListener(v -> {
            dismiss();
            if (l != null) l.onClick(this, which);
        });
        Motion.press(b);
        LinearLayout.LayoutParams lp = n == 3 ? UI.matchWrap() : new LinearLayout.LayoutParams(0, -2, 1f);
        if (bar.getChildCount() > 0) {
            if (n == 3) lp.topMargin = UI.dp(c, 8); else lp.leftMargin = UI.dp(c, 10);
        }
        bar.addView(b, lp);
    }

    @Override
    public void dismiss() {
        if (closing) return;
        Context c = getContext();
        if (card == null || !card.isAttachedToWindow() || !isShowing()
                || (c instanceof Activity && (((Activity) c).isFinishing() || ((Activity) c).isDestroyed()))) {
            try { super.dismiss(); } catch (Exception ignored) {}
            return;
        }
        closing = true;
        card.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(190)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f))
                .withEndAction(() -> { try { super.dismiss(); } catch (Exception ignored) {} }).start();
    }
}
