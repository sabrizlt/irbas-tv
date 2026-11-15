package it.irbas.tv;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** La guida al primo avvio: un passo alla volta, testo grande, un solo pulsante principale. */
public class SetupActivity extends Activity {
    private int step;
    private FrameLayout stage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.addView(new BackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();   // tornando dalle impostazioni, lo stato del passo si aggiorna
    }

    @Override
    public void onBackPressed() {
        if (step > 0) {
            step--;
            show();
        } else {
            super.onBackPressed();
        }
    }

    private void show() {
        stage.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView counter = PairActivity.label(this, "PASSO " + (step + 1) + " DI 4", 11, Glass.ACCENT, true);
        counter.setLetterSpacing(0.18f);
        page.addView(counter);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        View first;
        switch (step) {
            case 0: {
                page.addView(title("Benvenuto in Irbas TV"));
                page.addView(body("Una schermata semplice, senza pubblicità, con le tue app e un assistente a cui parlare. In tre passi è pronta."));
                first = button("Iniziamo", true, v -> next());
                buttons.addView(first);
                break;
            }
            case 1: {
                boolean ok = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                page.addView(title("Il microfono del telecomando"));
                page.addView(body(ok ? "✓ Fatto: Irbas può ascoltarti quando premi il microfono."
                        : "Serve per parlare con Irbas: «apri Netflix», «metti YouTube». Premi Consenti e poi, sulla finestra che compare, ancora Consenti."));
                if (ok) {
                    first = button("Avanti", true, v -> next());
                    buttons.addView(first);
                } else {
                    first = button("Consenti", true, v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1));
                    buttons.addView(first);
                    buttons.addView(button("Salta", false, v -> next()), spaced());
                }
                break;
            }
            case 2: {
                boolean ok = accessibilityOn();
                page.addView(title("Il tasto Home"));
                page.addView(body(ok ? "✓ Fatto: il tasto Home del telecomando porta sempre a Irbas TV, e il telecomando sul telefono può muoversi nelle app."
                        : "Nella pagina che si apre scegli «Irbas TV: tasto Home» (a volte è dentro «Servizi» o «App scaricate») e attivalo. Poi torna indietro qui."));
                if (ok) {
                    first = button("Avanti", true, v -> next());
                    buttons.addView(first);
                } else {
                    first = button("Apri Accessibilità", true, v -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    buttons.addView(first);
                    buttons.addView(button("Salta", false, v -> next()), spaced());
                }
                break;
            }
            default: {
                page.addView(title("Il telecomando sul telefono"));
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-2, -2);
                cardParams.topMargin = Glass.dp(this, 18);
                cardParams.bottomMargin = Glass.dp(this, 6);
                page.addView(PairActivity.card(this, false), cardParams);
                first = button("Fatto, andiamo", true, v -> finishSetup());
                buttons.addView(first);
                break;
            }
        }
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-2, -2);
        buttonParams.topMargin = Glass.dp(this, 26);
        page.addView(buttons, buttonParams);
        stage.addView(page, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        first.requestFocus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        show();
    }

    private void next() {
        step = Math.min(3, step + 1);
        show();
    }

    private void finishSetup() {
        IrbasLink.prefs(this).edit().putBoolean("setupDone", true).apply();
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void open(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                Toast.makeText(this, "Cerca «Accessibilità» nelle impostazioni", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {
                Toast.makeText(this, "Apri Impostazioni › Accessibilità dal menu della TV", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean accessibilityOn() {
        String list = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(list)) return false;
        String me = new ComponentName(this, HomeKeyService.class).flattenToString();
        String meShort = new ComponentName(this, HomeKeyService.class).flattenToShortString();
        for (String item : list.split(":")) if (item.equalsIgnoreCase(me) || item.equalsIgnoreCase(meShort)) return true;
        return false;
    }

    private TextView title(String text) {
        TextView view = PairActivity.label(this, text, 34, Glass.TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, Glass.dp(this, 12), 0, Glass.dp(this, 14));
        return view;
    }

    private TextView body(String text) {
        TextView view = PairActivity.label(this, text, 17, Glass.SOFT, false);
        view.setGravity(Gravity.CENTER);
        view.setMaxWidth(Glass.dp(this, 620));
        return view;
    }

    private TextView button(String text, boolean primary, View.OnClickListener action) {
        TextView view = PairActivity.label(this, text, 16, primary ? 0xFF07101E : Glass.TEXT, true);
        view.setGravity(Gravity.CENTER);
        int h = Glass.dp(this, 24);
        view.setPadding(h, 0, h, 0);
        view.setMinHeight(Glass.dp(this, 46));
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(primary ? Glass.primary(this) : Glass.focusable(this, 999));
        view.setOnClickListener(action);
        view.setOnFocusChangeListener((v, focused) -> v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(140).start());
        return view;
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = Glass.dp(this, 14);
        return params;
    }
}
