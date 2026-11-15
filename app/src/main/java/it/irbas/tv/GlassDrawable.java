package it.irbas.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Vetro liquido: riempimento traslucido, un riflesso morbido in alto e un bordo di luce
 * che parte chiaro in alto a sinistra e sfuma in basso a destra. Si accende quando ha il fuoco.
 */
final class GlassDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float radius;
    private final float strength;
    private final boolean focusable;
    private final float line;
    private boolean focused;

    GlassDrawable(Context context, float radiusDp, float strength, boolean focusable) {
        this.radius = Glass.dp(context, radiusDp);
        this.strength = strength;
        this.focusable = focusable;
        this.line = Math.max(1f, Glass.dp(context, 1.2f));
        edge.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        shade();
    }

    private void shade() {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float inset = focused ? line : line / 2f;
        box.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        int base = focused ? 0x30 : (int) (0x0F + 0x0B * strength);
        fill.setColor((base << 24) | 0xFFFFFF);
        sheen.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.55f,
                focused ? 0x26FFFFFF : 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        edge.setStrokeWidth(focused ? line * 2.2f : line);
        edge.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{focused ? 0xFFFFFFFF : 0x80FFFFFF, focused ? 0xCCBFF6FF : 0x26FFFFFF, focused ? 0xB3FFFFFF : 0x14FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        if (box.isEmpty()) shade();
        float r = Math.min(radius, Math.min(box.width(), box.height()) / 2f);
        canvas.drawRoundRect(box, r, r, fill);
        canvas.drawRoundRect(box, r, r, sheen);
        canvas.drawRoundRect(box, r, r, edge);
    }

    @Override
    public boolean isStateful() {
        return focusable;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean now = false;
        for (int s : state) if (s == android.R.attr.state_focused) now = true;
        if (now == focused) return false;
        focused = now;
        shade();
        invalidateSelf();
        return true;
    }

    @Override
    public void setAlpha(int alpha) { }

    @Override
    public void setColorFilter(ColorFilter colorFilter) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
