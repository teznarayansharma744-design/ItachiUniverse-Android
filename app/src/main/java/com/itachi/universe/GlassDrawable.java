package com.itachi.universe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * One piece of frosted glass: a diagonal sheen from bright to clear, a rim that is bright where the
 * light hits (top left) and faint where it does not, and a thin highlight along the top edge.
 * Everything is built once when the size is known, so drawing (and scrolling) stays cheap.
 */
public class GlassDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius, stroke;
    private final boolean dark;
    private final int tint;
    private final float strength;

    /** strength 1 = normal card, about 1.7 = brighter (selected tab, buttons). tint 0 = none. */
    public GlassDrawable(Context c, float radiusPx, int tint, float strength) {
        this.radius = radiusPx;
        this.dark = UI.dark(c);
        this.tint = tint;
        this.strength = strength;
        this.stroke = Math.max(1f, UI.dp(c, 1));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
    }

    private static int a(int color, float alphaMul) {
        int al = Math.min(255, Math.round(((color >>> 24) & 0xFF) * alphaMul));
        return (color & 0x00FFFFFF) | (al << 24);
    }

    private final RectF inner = new RectF();

    @Override protected void onBoundsChange(android.graphics.Rect b) {
        float h = stroke / 2f;
        rect.set(b.left + h, b.top + h, b.right - h, b.bottom - h);           // the thin rim sits on the edge
        inner.set(b.left + stroke, b.top + stroke, b.right - stroke, b.bottom - stroke); // the glass stops where the rim starts
        if (rect.width() <= 0 || rect.height() <= 0) return;
        int s0 = dark ? 0x2AFFFFFF : 0xA6FFFFFF, s1 = dark ? 0x0DFFFFFF : 0x55FFFFFF;
        fill.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                a(s0, strength), a(s1, strength), Shader.TileMode.CLAMP));
        if (tint != 0) {
            tintPaint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    a(tint, dark ? 0.9f : 0.7f), tint & 0x00FFFFFF, Shader.TileMode.CLAMP));
        }
        // a soft rim: lit at the top left, nearly gone along the sides, a faint echo at the bottom right
        int[] rc = dark ? new int[]{0x52FFFFFF, 0x12FFFFFF, 0x12FFFFFF, 0x2AFFFFFF}
                        : new int[]{0xFFFFFFFF, 0x80FFFFFF, 0x80FFFFFF, 0xC0FFFFFF};
        float k = Math.min(1.25f, strength);
        for (int i = 0; i < rc.length; i++) rc[i] = a(rc[i], k);
        rim.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, rc,
                new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas cv) {
        if (rect.width() <= 0) return;
        float ri = Math.max(0f, radius - stroke);
        cv.drawRoundRect(inner, ri, ri, fill);
        if (tint != 0) cv.drawRoundRect(inner, ri, ri, tintPaint);
        float r = Math.max(0f, radius - stroke / 2f);
        cv.drawRoundRect(rect, r, r, rim);
    }

    @Override public void getOutline(Outline o) { o.setRoundRect(getBounds(), radius); }
    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
