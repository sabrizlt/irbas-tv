package it.irbas.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** «Telecomando sul telefono»: il QR da inquadrare e il codice di 6 cifre. */
public class PairActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.addView(new BackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        root.addView(card(this, true), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);
    }

    /** Il riquadro col QR (lo usa anche la guida al primo avvio). */
    static LinearLayout card(Activity activity, boolean withTitle) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Glass.panel(activity, 30, 1));
        int pad = Glass.dp(activity, 28);
        card.setPadding(pad, pad, pad + Glass.dp(activity, 8), pad);

        String url = Pairing.url(activity);
        FrameLayout qrBox = new FrameLayout(activity);
        GradientDrawable white = new GradientDrawable();
        white.setColor(0xFFFFFFFF);
        white.setCornerRadius(Glass.dp(activity, 18));
        qrBox.setBackground(white);
        int inset = Glass.dp(activity, 10);
        qrBox.setPadding(inset, inset, inset, inset);
        ImageView qr = new ImageView(activity);
        Bitmap bitmap = url == null ? null : Pairing.qr(url, 600);
        if (bitmap != null) qr.setImageBitmap(bitmap);
        qrBox.addView(qr, new FrameLayout.LayoutParams(-1, -1));
        card.addView(qrBox, new LinearLayout.LayoutParams(Glass.dp(activity, 190), Glass.dp(activity, 190)));

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        if (withTitle) text.addView(label(activity, "TELECOMANDO SUL TELEFONO", 11, Glass.ACCENT, true));
        TextView title = label(activity, "Inquadra il QR con la fotocamera", 24, Glass.TEXT, true);
        title.setPadding(0, Glass.dp(activity, 8), 0, Glass.dp(activity, 10));
        text.addView(title);
        text.addView(label(activity, url == null
                ? "La TV non è collegata a una rete: collegala alla Wi-Fi di casa."
                : "Il telefono deve essere sulla stessa Wi-Fi della TV. Si apre il telecomando, già collegato.", 14, Glass.SOFT, false));
        TextView manual = label(activity, url == null ? "" : "Oppure apri nel browser:  " + url.substring(0, url.indexOf("/#")), 13, Glass.MUTED, false);
        manual.setPadding(0, Glass.dp(activity, 14), 0, 0);
        text.addView(manual);
        TextView pin = label(activity, "Codice:  " + spaced(Pairing.pin(activity)), 22, Glass.TEXT, true);
        pin.setLetterSpacing(0.08f);
        pin.setPadding(0, Glass.dp(activity, 8), 0, 0);
        text.addView(pin);
        TextView tip = label(activity, "Poi sul telefono: Condividi › Aggiungi alla schermata Home, e diventa un'app.", 12, Glass.MUTED, false);
        tip.setPadding(0, Glass.dp(activity, 14), 0, 0);
        text.addView(tip);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Glass.dp(activity, 420), -2);
        params.leftMargin = Glass.dp(activity, 28);
        card.addView(text, params);
        return card;
    }

    private static String spaced(String pin) {
        return pin.length() == 6 ? pin.substring(0, 3) + " " + pin.substring(3) : pin;
    }

    static TextView label(Activity activity, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setLineSpacing(Glass.dp(activity, 2), 1f);
        return view;
    }
}
