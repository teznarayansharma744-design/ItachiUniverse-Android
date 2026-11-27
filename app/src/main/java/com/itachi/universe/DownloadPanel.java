package com.itachi.universe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Download progress: a glowing bar with a moving shine, percent, size done / total, speed and time left. */
public class DownloadPanel extends LinearLayout {
    private final Bar bar;
    private final TextView percent, sizes, speed;
    private float shown = 0f, target = 0f;
    private ValueAnimator slide, shine;
    private float shinePos = 0f;
    private final int id;

    public DownloadPanel(Context c, int id) {
        super(c);
        this.id = id;
        setOrientation(VERTICAL);
        bar = new Bar(c);
        addView(bar, new LayoutParams(-1, UI.dp(c, 10)));

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UI.dp(c, 8), 0, 0);
        percent = UI.tv(c, "0%", 14, true, UI.TEAL);
        row.addView(percent, new LayoutParams(-2, -2));
        sizes = UI.tv(c, "", 12, false, UI.dim(c));
        sizes.setPadding(UI.dp(c, 10), 0, 0, 0);
        row.addView(sizes, new LayoutParams(0, -2, 1f));
        speed = UI.tv(c, "", 12, true, UI.text(c));
        speed.setGravity(Gravity.END);
        row.addView(speed, new LayoutParams(-2, -2));
        addView(row, new LayoutParams(-1, -2));
        setVisibility(GONE);
    }

    private static String size(long b) {
        double mb = b / 1048576.0;
        return mb >= 1024 ? String.format(Locale.US, "%.2f GB", mb / 1024) : String.format(Locale.US, "%.1f MB", mb);
    }

    private static String rate(long bps) {
        double kb = bps / 1024.0;
        return kb >= 1024 ? String.format(Locale.US, "%.1f MB/s", kb / 1024) : String.format(Locale.US, "%.0f KB/s", kb);
    }

    private static String eta(long secs) {
        if (secs < 0) return "";
        if (secs >= 3600) return (secs / 3600) + "h " + ((secs % 3600) / 60) + "m left";
        if (secs >= 60) return (secs / 60) + "m " + (secs % 60) + "s left";
        return secs + "s left";
    }

    /** Reads the live numbers from Downloads; hides itself when nothing is downloading. */
    public void refresh() {
        int p = Downloads.progress(id);
        if (p < 0) { setVisibility(GONE); stopShine(); return; }
        setVisibility(VISIBLE);
        long[] st = Downloads.stats(id);   // done, total, bytes per second
        percent.setText(p + "%");
        if (st != null && st[1] > 0) {
            sizes.setText(size(st[0]) + " of " + size(st[1]));
            long left = st[2] > 0 ? (st[1] - st[0]) / st[2] : -1;
            speed.setText(st[2] > 0 ? rate(st[2]) + (left >= 0 ? "  ·  " + eta(left) : "") : "Starting…");
        } else if (st != null) {
            sizes.setText(size(st[0]) + " downloaded");
            speed.setText(st[2] > 0 ? rate(st[2]) : "Starting…");
        } else {
            sizes.setText(p >= 99 ? "Saving notes…" : "Preparing…");
            speed.setText("");
        }
        moveTo(p / 100f);
        startShine();
    }

    private void moveTo(float to) {
        target = to;
        if (slide != null) slide.cancel();
        slide = ValueAnimator.ofFloat(shown, target);
        slide.setDuration(450);
        slide.setInterpolator(Motion.SMOOTH);
        slide.addUpdateListener(a -> { shown = (float) a.getAnimatedValue(); bar.invalidate(); });
        slide.start();
    }

    private void startShine() {
        if (shine != null && shine.isRunning()) return;
        shine = ValueAnimator.ofFloat(0f, 1f);
        shine.setDuration(1400);
        shine.setRepeatCount(ValueAnimator.INFINITE);
        shine.setInterpolator(new LinearInterpolator());
        shine.addUpdateListener(a -> { shinePos = (float) a.getAnimatedValue(); bar.invalidate(); });
        shine.start();
    }

    private void stopShine() { if (shine != null) shine.cancel(); }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopShine();
        if (slide != null) slide.cancel();
    }

    private final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
                glow = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(Context c) {
            super(c);
            track.setColor(UI.dark(c) ? 0x26FFFFFF : 0x22000000);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), rad = h / 2f;
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, rad, rad, track);
            float fw = Math.max(h, w * shown);
            if (shown <= 0f) return;
            fill.setShader(new LinearGradient(0, 0, fw, 0, UI.INDIGO, UI.TEAL, Shader.TileMode.CLAMP));
            glow.setColor(UI.TEAL);
            glow.setAlpha(60);
            r.set(0, -h * 0.25f, fw, h * 1.25f);
            cv.drawRoundRect(r, rad * 1.5f, rad * 1.5f, glow);   // soft glow behind the fill
            r.set(0, 0, fw, h);
            cv.drawRoundRect(r, rad, rad, fill);
            // a bright band sweeps across the filled part
            float bx = (fw + h * 4) * shinePos - h * 2;
            sheen.setShader(new LinearGradient(bx - h * 2, 0, bx + h * 2, 0,
                    new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            int save = cv.save();
            cv.clipRect(0, 0, fw, h);
            cv.drawRoundRect(r, rad, rad, sheen);
            cv.restoreToCount(save);
        }
    }
}
