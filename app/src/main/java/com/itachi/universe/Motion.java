package com.itachi.universe;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/** The motion language of the app: soft springs with a tiny overshoot, like HyperOS and ColorOS. */
public final class Motion {
    private Motion() {}

    /** Critically-ish damped spring: settles fast, overshoots about 4%. */
    public static final TimeInterpolator SPRING = t -> (float) (1 - Math.exp(-7.5 * t) * Math.cos(9.5 * t));
    /** Smooth deceleration used for things that slide in. */
    public static final TimeInterpolator SMOOTH = t -> 1f - (float) Math.pow(1 - t, 4);

    /** Presses shrink a little and spring back, on any tappable view. Does not eat the click. */
    @SuppressLint("ClickableViewAccessibility")
    public static void press(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(110).setInterpolator(SMOOTH).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(360).setInterpolator(SPRING).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** The children of [col] rise in one after another the first time it is drawn. */
    public static void riseOnFirstDraw(ViewGroup col) {
        col.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                col.getViewTreeObserver().removeOnPreDrawListener(this);
                float lift = UI.dp(col.getContext(), 26);
                int n = Math.min(col.getChildCount(), 12);
                for (int i = 0; i < n; i++) {
                    View c = col.getChildAt(i);
                    c.setAlpha(0f);
                    c.setTranslationY(lift);
                    c.animate().alpha(1f).translationY(0f).setStartDelay(i * 45L).setDuration(520)
                            .setInterpolator(SPRING).start();
                }
                return true;
            }
        });
    }
}
