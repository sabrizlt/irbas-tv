package it.irbas.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Il logo di Irbas: due archi azzurri e la barra luminosa al centro, con il bagliore. */
public class IrbasLogoView extends View {
    static final int CYAN = 0xFF4CEDFB;
    static final int GLOW = 0xB34CEDFB;
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final RectF bar = new RectF();

    public IrbasLogoView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // serve per il bagliore
        arc.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = Math.min(getWidth(), getHeight()) * 0.8f; // margine per il bagliore
        paint(canvas, getWidth() / 2f, getHeight() / 2f, h, arc, pill, oval, bar);
    }

    /** Disegna il logo alto `h` con il centro in (cx, cy). Le proporzioni sono quelle del logo originale. */
    static void paint(Canvas canvas, float cx, float cy, float h, Paint arc, Paint pill, RectF oval, RectF bar) {
        float radius = 0.456f * h * 0.94f;
        arc.setStyle(Paint.Style.STROKE);
        arc.setColor(CYAN);
        arc.setStrokeWidth(Math.max(1f, 0.049f * h));
        arc.setShadowLayer(h * 0.12f, 0, 0, GLOW);
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(oval, -61f, 122f, false, arc);
        canvas.drawArc(oval, 119f, 122f, false, arc);

        float barWidth = 0.153f * h;
        float barHeight = 0.98f * h;
        bar.set(cx - barWidth / 2f, cy - barHeight / 2f, cx + barWidth / 2f, cy + barHeight / 2f);
        pill.setShader(new LinearGradient(0, bar.top, 0, bar.bottom, 0xFFFFFFFF, 0xFF70F1FD, Shader.TileMode.CLAMP));
        pill.setShadowLayer(h * 0.12f, 0, 0, GLOW);
        canvas.drawRoundRect(bar, barWidth / 2f, barWidth / 2f, pill);
    }
}
