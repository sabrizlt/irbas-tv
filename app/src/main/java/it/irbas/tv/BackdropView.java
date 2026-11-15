package it.irbas.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Lo sfondo: nero profondo con due luci di Irbas (azzurra e viola) che si muovono pianissimo. */
public class BackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private float t;

    public BackdropView(Context context) {
        super(context);
        animator.setDuration(45_000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        canvas.drawColor(Glass.BG);
        if (w <= 0 || h <= 0) return;
        double a = t * Math.PI * 2;
        blob(canvas, w * (0.14f + 0.05f * (float) Math.sin(a)), h * (0.02f + 0.06f * (float) Math.cos(a)), w * 0.60f, 0x3D27D3F0);
        blob(canvas, w * (0.92f + 0.04f * (float) Math.cos(a)), h * (0.98f + 0.05f * (float) Math.sin(a)), w * 0.62f, 0x387A4DFF);
        blob(canvas, w * (0.62f + 0.08f * (float) Math.sin(a + 1.3)), h * (0.40f + 0.08f * (float) Math.cos(a + 2.1)), w * 0.34f, 0x1A2E6BFF);
        // un velo scuro sui bordi, per tenere lo sguardo al centro
        paint.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.78f, 0x00000000, 0xB3000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
    }

    private void blob(Canvas canvas, float x, float y, float radius, int color) {
        if (radius <= 0) return;
        paint.setShader(new RadialGradient(x, y, radius, color, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, paint);
    }
}
