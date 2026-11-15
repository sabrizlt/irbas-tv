package it.irbas.tv;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

/** I colori e i materiali di Irbas TV: nero profondo, luce azzurra e viola, vetro liquido. */
final class Glass {
    static final int BG = 0xFF020306;
    static final int TEXT = 0xFFF5F8FD;
    static final int MUTED = 0xA6E6ECF7;
    static final int SOFT = 0xD9E6ECF7;
    static final int CYAN = 0xFF4CEDFB;
    static final int ACCENT = 0xFF6FE3FF;
    static final int VIOLET = 0xFFC3A6FF;
    static final int GREEN = 0xFF63E6A0;
    static final int AMBER = 0xFFFFCC66;

    private Glass() {}

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    /** Un pannello di vetro. `strength` 0 = leggero, 1 = più pieno (per il riquadro principale). */
    static Drawable panel(Context context, float radiusDp, float strength) {
        return new GlassDrawable(context, radiusDp, strength, false);
    }

    /** Vetro che si accende quando il telecomando ci arriva sopra. */
    static Drawable focusable(Context context, float radiusDp) {
        return new GlassDrawable(context, radiusDp, 0.3f, true);
    }

    /** Il pulsante principale: pieno e luminoso. */
    static Drawable primary(Context context) {
        GradientDrawable on = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFFFFF, 0xFFCFF8FF});
        on.setCornerRadius(dp(context, 999));
        GradientDrawable off = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xE6EAF6FF, 0xD9B8EEFF});
        off.setCornerRadius(dp(context, 999));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, on);
        states.addState(new int[]{}, off);
        return states;
    }

    static GradientDrawable ring(Context context, float radiusDp) {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(context, radiusDp));
        ring.setStroke(dp(context, 3), 0xF2FFFFFF);
        return ring;
    }
}
