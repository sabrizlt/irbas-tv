package it.irbas.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Il logo di Irbas dentro un alone che respira: più veloce quando ascolta, più lento quando aspetta. */
public class OrbView extends View {
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final RectF bar = new RectF();
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private float phase;
    private boolean listening;

    public OrbView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(2400);
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    public void setListening(boolean on) {
        if (listening == on) return;
        listening = on;
        animator.setDuration(on ? 700 : 2400);
        if (animator.isRunning()) {
            animator.cancel();
            animator.start();
        }
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
        float cx = w / 2f;
        float cy = h / 2f;
        float size = Math.min(w, h);
        if (size <= 0) return;
        float eased = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI));
        float radius = size * (0.40f + 0.08f * eased);
        int alpha = (int) ((listening ? 120 : 70) + 60 * eased);
        int inner = (alpha << 24) | 0x4CEDFB;
        halo.setShader(new RadialGradient(cx, cy, radius, new int[]{inner, 0x224C6BFB, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, halo);
        IrbasLogoView.paint(canvas, cx, cy, size * 0.42f, arc, pill, oval, bar);
    }
}
