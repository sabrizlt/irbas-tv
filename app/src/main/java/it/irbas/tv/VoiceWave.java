package it.irbas.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** Le barre della voce: si alzano mentre parli. */
public class VoiceWave extends View {
    private static final float[] SHAPE = {0.45f, 0.75f, 1f, 0.75f, 0.45f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] heights = new float[SHAPE.length];
    private float level;
    private long tick;

    public VoiceWave(Context context) {
        super(context);
    }

    /** Il volume da onRmsChanged (circa -2…10 dB). */
    public void setLevel(float rmsDb) {
        float target = Math.max(0f, Math.min(1f, (rmsDb + 2f) / 10f));
        level = level * 0.6f + target * 0.4f;
        tick++;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float bar = w / (SHAPE.length * 2f - 1f);
        paint.setShader(new LinearGradient(0, 0, 0, h, 0xFFFFFFFF, Glass.CYAN, Shader.TileMode.CLAMP));
        for (int i = 0; i < SHAPE.length; i++) {
            float wobble = 0.85f + 0.15f * (float) Math.sin(tick * 0.9 + i * 1.7);
            float target = h * (0.18f + 0.82f * level * SHAPE[i] * wobble);
            heights[i] = heights[i] * 0.5f + target * 0.5f;
            float x = i * bar * 2f;
            float top = (h - heights[i]) / 2f;
            canvas.drawRoundRect(x, top, x + bar, top + heights[i], bar / 2f, bar / 2f, paint);
        }
    }
}
