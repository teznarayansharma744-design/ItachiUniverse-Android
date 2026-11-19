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
    private View anchor;
    private boolean closing;
    private final java.util.ArrayList<View> rows = new java.util.ArrayList<>();
    private android.animation.ValueAnimator veil;

    private static final class Content {
        CharSequence title, message;
        View view;
        CharSequence posText, negText, neuText;
        DialogInterface.OnClickListener pos, neg, neu;
        boolean cancelable = true;
        String[] items;
        IntConsumer onItem;
        int checked = -1;
        View anchor;
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
        menu(ctx, title, items, -1, onPick);
    }

    /** Same, with a check mark on the item that is currently selected. */
    public static void menu(Context ctx, String title, String[] items, int checked, IntConsumer onPick) {
        Content c = new Content();
        c.checked = checked;
        c.title = title;
        c.items = items;
        c.onItem = onPick;
        new GlassDialog(ctx, c).show();
    }

    /** Pop-up menu that grows out of the tapped button (like the three-dot menus in HyperOS / Telegram). */
    public static void menu(Context ctx, View anchor, String title, String[] items, IntConsumer onPick) {
        Content c = new Content();
        c.title = title; c.items = items; c.onItem = onPick; c.anchor = anchor;
        new GlassDialog(ctx, c).show();
    }

    /** Dim + blur behind the pop-up fade in and out with the card instead of jumping. */
    private void veil(boolean in, Runnable end) {
        Window w = getWindow();
        if (w == null) return;
        final float dimMax = UI.dark(getContext()) ? 0.42f : 0.28f;
        final int blurMax = UI.dp(getContext(), 22);
        if (veil != null) veil.cancel();
        veil = android.animation.ValueAnimator.ofFloat(in ? 0f : 1f, in ? 1f : 0f);
        veil.setDuration(in ? 420 : 220);
        veil.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            try {
                w.setDimAmount(dimMax * f);
                if (Build.VERSION.SDK_INT >= 31) {
                    WindowManager.LayoutParams lp = w.getAttributes();
                    lp.setBlurBehindRadius(Math.max(1, Math.round(blurMax * f)));
                    w.setAttributes(lp);
                }
            } catch (Exception ignored) {}
        });
        if (end != null) veil.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { end.run(); }
        });
        veil.start();
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
            w.setDimAmount(0f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (Build.VERSION.SDK_INT >= 31) {  // the real frosted glass: blur everything behind the pop-up
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.setBlurBehindRadius(1);
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
        anchor = spec.anchor;
        if (anchor != null) {   // sit next to the button, on the side with more room
            int[] loc = new int[2];
            anchor.getLocationOnScreen(loc);
            int sw = c.getResources().getDisplayMetrics().widthPixels;
            int sh = c.getResources().getDisplayMetrics().heightPixels;
            root.setPadding(0, 0, 0, 0);
            cp.width = Math.min(UI.dp(c, 260), sw - UI.dp(c, 24));
            boolean right = loc[0] + anchor.getWidth() / 2 > sw / 2;
            cp.gravity = Gravity.TOP | (right ? Gravity.END : Gravity.START);
            int side = UI.dp(c, 12);
            if (right) cp.rightMargin = Math.max(side, sw - (loc[0] + anchor.getWidth()) );
            else cp.leftMargin = Math.max(side, loc[0]);
            cp.topMargin = Math.min(Math.max(UI.dp(c, 8), loc[1] + anchor.getHeight() - UI.dp(c, 6)), sh - UI.dp(c, 220));
        }
        root.addView(card, cp);
        setContentView(root);

        card.setAlpha(0f);
        veil(true, null);
        if (anchor != null) {
            card.setScaleX(0.25f);
            card.setScaleY(0.25f);
            card.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int l, int t, int r, int bt, int ol, int ot, int or, int ob) {
                    v.removeOnLayoutChangeListener(this);
                    int[] loc = new int[2];
                    anchor.getLocationOnScreen(loc);
                    int[] rl = new int[2];
                    ((View) v.getParent()).getLocationOnScreen(rl);
                    v.setPivotX(Math.max(0, Math.min(v.getWidth(), loc[0] + anchor.getWidth() / 2f - rl[0] - v.getLeft())));
                    v.setPivotY(Math.max(0, Math.min(v.getHeight(), loc[1] + anchor.getHeight() / 2f - rl[1] - v.getTop())));
                    v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(Motion.SPRING).start();
                    stagger(180);
                }
            });
        } else {
            card.setScaleX(0.86f);
            card.setScaleY(0.86f);
            card.setTranslationY(UI.dp(c, 26));
            card.post(() -> { card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(460).setInterpolator(Motion.SPRING).start(); stagger(120); });
        }
    }

    /** Menu rows slide in one after another. */
    private void stagger(int startDelay) {
        float lift = UI.dp(getContext(), 10);
        for (int i = 0; i < rows.size() && i < 10; i++) {
            View r = rows.get(i);
            r.setAlpha(0f);
            r.setTranslationY(lift);
            r.animate().alpha(1f).translationY(0f).setStartDelay(startDelay + i * 38L).setDuration(380)
                    .setInterpolator(Motion.SMOOTH).start();
        }
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
            final int maxMsgH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.34f);
            sv.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                ViewGroup.LayoutParams lp = sv.getLayoutParams();
                if (sv.getHeight() > maxMsgH && lp.height != maxMsgH) { lp.height = maxMsgH; sv.setLayoutParams(lp); }
            });
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
            // the list scrolls, so every item stays reachable in landscape where the screen is short
            LinearLayout itemsCol = new LinearLayout(c);
            itemsCol.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < spec.items.length; i++) {
                final int idx = i;
                boolean danger = spec.items[i].toLowerCase().startsWith("delete") || spec.items[i].toLowerCase().startsWith("revoke");
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(UI.tv(c, spec.items[i], 16, true, danger ? 0xFFFF6B7A : text), new LinearLayout.LayoutParams(0, -2, 1f));
                if (idx == spec.checked) row.addView(UI.iconView(c, R.drawable.ic_check, 18, UI.TEAL));
                row.setPadding(UI.dp(c, 16), UI.dp(c, 13), UI.dp(c, 14), UI.dp(c, 13));
                row.setBackground(idx == spec.checked ? new GlassDrawable(c, UI.dp(c, 16), 0, 2f) : UI.box(dark ? 0x14FFFFFF : 0x12000000, UI.dp(c, 16), 0, 0));
                row.setOnClickListener(v -> { dismiss(); if (spec.onItem != null) spec.onItem.accept(idx); });
                Motion.press(row);
                LinearLayout.LayoutParams lp = UI.matchWrap();
                lp.topMargin = UI.dp(c, i == 0 ? 4 : 8);
                itemsCol.addView(row, lp);
                rows.add(row);
            }
            ScrollView isv = new ScrollView(c);
            isv.setVerticalScrollBarEnabled(false);
            isv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            isv.addView(itemsCol);
            final int maxItemsH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.62f);
            isv.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                ViewGroup.LayoutParams lp = isv.getLayoutParams();
                if (isv.getHeight() > maxItemsH && lp.height != maxItemsH) { lp.height = maxItemsH; isv.setLayoutParams(lp); }
            });
            col.addView(isv, new LinearLayout.LayoutParams(-1, -2));
            // scroll to the selected item so it is visible when the list opens
            if (spec.checked > 0) isv.post(() -> {
                View sel = itemsCol.getChildAt(spec.checked);
                if (sel != null) isv.scrollTo(0, Math.max(0, sel.getTop() - UI.dp(c, 40)));
            });
        }

        // buttons: a row for one or two, a stack for three
        int n = (spec.posText != null ? 1 : 0) + (spec.negText != null ? 1 : 0) + (spec.neuText != null ? 1 : 0);
        if (n > 0) {
            LinearLayout bar = new LinearLayout(c);
            final boolean stack = n == 3 && c.getResources().getDisplayMetrics().heightPixels > c.getResources().getDisplayMetrics().widthPixels;
            bar.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            if (stack) {  // stacked: the main action on top
                addButton(c, bar, spec.posText, spec.pos, DialogInterface.BUTTON_POSITIVE, true, true);
                addButton(c, bar, spec.negText, spec.neg, DialogInterface.BUTTON_NEGATIVE, false, true);
                addButton(c, bar, spec.neuText, spec.neu, DialogInterface.BUTTON_NEUTRAL, false, true);
            } else {
                if (spec.neuText != null) addButton(c, bar, spec.neuText, spec.neu, DialogInterface.BUTTON_NEUTRAL, false, false);
                if (spec.negText != null) addButton(c, bar, spec.negText, spec.neg, DialogInterface.BUTTON_NEGATIVE, false, false);
                if (spec.posText != null) addButton(c, bar, spec.posText, spec.pos, DialogInterface.BUTTON_POSITIVE, true, false);
            }
            LinearLayout.LayoutParams bp = UI.matchWrap();
            bp.topMargin = UI.dp(c, 14);
            col.addView(bar, bp);
        } else if (spec.items == null) {
            col.addView(UI.space(c, 6));
        }
        return col;
    }

    private void addButton(Context c, LinearLayout bar, CharSequence label, DialogInterface.OnClickListener l, int which, boolean primary, boolean stack) {
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
        LinearLayout.LayoutParams lp = stack ? UI.matchWrap() : new LinearLayout.LayoutParams(0, -2, 1f);
        if (bar.getChildCount() > 0) {
            if (stack) lp.topMargin = UI.dp(c, 8); else lp.leftMargin = UI.dp(c, 10);
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
        veil(false, null);
        float to = anchor != null ? 0.3f : 0.92f;
        card.animate().alpha(0f).scaleX(to).scaleY(to).setDuration(anchor != null ? 220 : 190)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f))
                .withEndAction(() -> { try { super.dismiss(); } catch (Exception ignored) {} }).start();
    }
}
