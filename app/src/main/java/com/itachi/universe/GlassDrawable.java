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
    private final Paint top = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius, stroke, lift;
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
        this.lift = UI.dp(c, 1);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
        top.setStyle(Paint.Style.STROKE);
        top.setStrokeWidth(stroke);
    }

    private static int a(int color, float alphaMul) {
        int al = Math.min(255, Math.round(((color >>> 24) & 0xFF) * alphaMul));
        return (color & 0x00FFFFFF) | (al << 24);
    }

    @Override protected void onBoundsChange(android.graphics.Rect b) {
        float h = stroke / 2f;
        rect.set(b.left + h, b.top + h, b.right - h, b.bottom - h);
        if (rect.width() <= 0 || rect.height() <= 0) return;
        int s0 = dark ? 0x30FFFFFF : 0xB0FFFFFF, s1 = dark ? 0x0CFFFFFF : 0x50FFFFFF;
        fill.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                a(s0, strength), a(s1, strength), Shader.TileMode.CLAMP));
        if (tint != 0) {
            tintPaint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    a(tint, dark ? 1f : 0.8f), tint & 0x00FFFFFF, Shader.TileMode.CLAMP));
        }
        int r0 = dark ? 0x70FFFFFF : 0xFFFFFFFF, r1 = dark ? 0x14FFFFFF : 0x60FFFFFF;
        rim.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                a(r0, Math.min(1.4f, strength)), r1, Shader.TileMode.CLAMP));
        float x0 = rect.left + radius, x1 = rect.right - radius;
        if (x1 > x0) {
            int hl = dark ? 0x66FFFFFF : 0xFFFFFFFF;
            top.setShader(new LinearGradient(x0, 0, x1, 0, new int[]{hl & 0x00FFFFFF, hl, hl & 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
        }
    }

    @Override public void draw(Canvas cv) {
        if (rect.width() <= 0) return;
        cv.drawRoundRect(rect, radius, radius, fill);
        if (tint != 0) cv.drawRoundRect(rect, radius, radius, tintPaint);
        cv.drawRoundRect(rect, radius, radius, rim);
        if (rect.width() > radius * 2) cv.drawLine(rect.left + radius, rect.top + lift + stroke, rect.right - radius, rect.top + lift + stroke, top);
    }

    @Override public void getOutline(Outline o) { o.setRoundRect(getBounds(), radius); }
    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
