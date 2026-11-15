package it.irbas.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La schermata principale di Irbas TV: nero profondo, vetro liquido e Irbas al centro.
 * Tutto sta in una schermata, senza righe tagliate: le altre app sono in «Tutte le app».
 */
public class HomeActivity extends Activity {
    static final String TIM = "timvision.launcher";
    /** Le app che vanno nella fila in basso, in quest'ordine (se sono installate). */
    static final List<String> FAVORITES = Arrays.asList(
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
            "com.google.android.youtube.tv", "com.dazn", "it.rainet.androidtv", "it.mediaset.infinitytv",
            "com.wbd.stream", "com.apple.atve.androidtv.appletv", TIM);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, String> labels = new LinkedHashMap<>();
    private LinearLayout page;
    private TextView clock;
    private TextView link;
    private View firstFocus;
    private boolean connected;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 20_000);
        }
    };

    private final View.OnFocusChangeListener grow = (v, focused) ->
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(150).start();

    // MARK: ciclo di vita

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        readSetup(getIntent());
        RemoteService.start(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(new BackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        page = column();
        page.setPadding(dp(48), dp(26), dp(48), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (readSetup(intent)) {
            build();
            refresh();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!IrbasLink.prefs(this).getBoolean("setupDone", false)) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        build();
        refresh();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        // È la schermata principale: «indietro» qui non fa niente.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == KeyEvent.KEYCODE_ASSIST) {
            openAssistant();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Indirizzo, codice e nome arrivano dal Mac (collega.sh) come extra dell'avvio. */
    private boolean readSetup(Intent intent) {
        if (intent == null) return false;
        String server = intent.getStringExtra("server");
        String code = intent.getStringExtra("code");
        String name = intent.getStringExtra("name");
        if (server == null && code == null && name == null) return false;
        SharedPreferences.Editor editor = IrbasLink.prefs(this).edit();
        if (server != null) editor.putString("server", server.trim());
        if (code != null) editor.putString("code", code.trim());
        if (name != null) editor.putString("name", name.trim());
        editor.putLong("lastUsage", 0).apply();
        return true;
    }

    // MARK: dal server

    private void refresh() {
        if (!IrbasLink.configured(this)) return;
        IrbasLink.call(this, "GET", "/ping", null, 8_000, (json, error) -> {
            connected = error == null;
            updateLink();
        });
        IrbasLink.call(this, "GET", "/tv/home", null, 15_000, (json, error) -> {
            if (json == null) return;   // server vecchio: i consigli arrivano dopo l'aggiornamento
            JSONObject home = json.optJSONObject("home");
            if (home == null) return;
            String text = home.toString();
            if (!text.equals(IrbasLink.prefs(this).getString("home", ""))) {
                IrbasLink.prefs(this).edit().putString("home", text).apply();
                build();
            }
        });
        long last = IrbasLink.prefs(this).getLong("lastUsage", 0);
        if (System.currentTimeMillis() - last > 30 * 60_000L) {
            try {
                JSONObject body = Usage.payload(this, labels);
                IrbasLink.call(this, "POST", "/tv/usage", body, 15_000, (json, error) -> {
                    if (error == null) IrbasLink.prefs(this).edit().putLong("lastUsage", System.currentTimeMillis()).apply();
                });
            } catch (Exception ignored) {
                // si riprova alla prossima accensione
            }
        }
    }

    private JSONObject cachedHome() {
        try {
            String text = IrbasLink.prefs(this).getString("home", "");
            return text.isEmpty() ? null : new JSONObject(text);
        } catch (Exception e) {
            return null;
        }
    }

    // MARK: la pagina

    private void build() {
        View focused = getCurrentFocus();
        boolean keepFocus = focused != null && focused != firstFocus;
        page.removeAllViews();
        firstFocus = null;
        List<ResolveInfo> apps = Apps.installed(this, labels);

        page.addView(topBar(), new LinearLayout.LayoutParams(-1, dp(32)));

        JSONObject home = cachedHome();
        JSONObject hero = home == null ? null : home.optJSONObject("hero");
        JSONArray news = home == null ? null : home.optJSONArray("news");

        LinearLayout heroRow = row();
        heroRow.addView(aiCard(hero), new LinearLayout.LayoutParams(0, -1, 1.75f));
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(0, -1, 1f);
        sideParams.leftMargin = dp(18);
        heroRow.addView(side(news), sideParams);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(262));
        heroParams.topMargin = dp(22);
        page.addView(heroRow, heroParams);

        TextView dockTitle = text("Le tue app", 15, Glass.TEXT, true);
        dockTitle.setPadding(dp(4), dp(24), 0, 0);
        page.addView(dockTitle);
        page.addView(dock(apps));

        if (!keepFocus && firstFocus != null) firstFocus.requestFocus();
        updateLink();
    }

    private View topBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(new IrbasLogoView(this), new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView name = text("Irbas", 20, Glass.TEXT, true);
        name.setLetterSpacing(0.04f);
        name.setPadding(dp(8), 0, 0, 0);
        bar.addView(name);
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        link = text("", 12, Glass.MUTED, true);
        link.setGravity(Gravity.CENTER);
        link.setPadding(dp(14), 0, dp(14), 0);
        link.setBackground(Glass.panel(this, 999, 0));
        bar.addView(link, new LinearLayout.LayoutParams(-2, dp(28)));

        clock = text("", 14, Glass.SOFT, false);
        clock.setPadding(dp(18), 0, 0, 0);
        bar.addView(clock);

        TextView phone = pill("Telefono");
        phone.setOnClickListener(v -> startActivity(new Intent(this, PairActivity.class)));
        LinearLayout.LayoutParams phoneParams = new LinearLayout.LayoutParams(-2, dp(32));
        phoneParams.leftMargin = dp(16);
        bar.addView(phone, phoneParams);

        TextView settings = pill("Impostazioni");
        settings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                toast("Impostazioni non disponibili");
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(32));
        params.leftMargin = dp(10);
        bar.addView(settings, params);
        updateClock();
        return bar;
    }

    /** Il riquadro di Irbas: l'alone che respira, il saluto o il consiglio della sera, e il pulsante per parlargli. */
    private View aiCard(JSONObject hero) {
        LinearLayout card = row();
        card.setBackground(Glass.panel(this, 30, 1));
        card.setPadding(dp(18), dp(24), dp(28), dp(24));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(new OrbView(this), new LinearLayout.LayoutParams(dp(164), dp(164)));

        LinearLayout text = column();
        String name = IrbasLink.prefs(this).getString("name", "");
        boolean configured = IrbasLink.configured(this);
        String label;
        String title;
        String meta;
        String reason;
        String target;
        if (hero != null) {
            label = hero.optString("label", "STASERA PER TE");
            title = hero.optString("title", "");
            meta = hero.optString("meta", "");
            reason = hero.optString("reason", "");
            target = hero.optString("package", "");
        } else {
            label = "IRBAS";
            title = greeting() + (name.isEmpty() ? "" : ", " + name);
            meta = configured ? "Sto preparando i consigli di stasera." : "Collega Irbas per i consigli e le risposte a voce.";
            reason = "Premi il microfono del telecomando e parlami: ti apro le app, ti dico cosa guardare e mi ricordo cosa ti piace.";
            target = "";
        }
        TextView labelView = text(label.toUpperCase(Locale.ITALIAN), 11, Glass.ACCENT, true);
        labelView.setLetterSpacing(0.18f);
        TextView titleView = text(title, 31, Glass.TEXT, true);
        titleView.setMaxLines(2);
        titleView.setPadding(0, dp(8), 0, dp(6));
        shine(titleView);
        TextView metaView = text(meta, 13, Glass.MUTED, false);
        metaView.setMaxLines(1);
        TextView reasonView = text(reason, 14, Glass.SOFT, false);
        reasonView.setMaxLines(3);
        reasonView.setLineSpacing(dp(3), 1f);
        reasonView.setPadding(0, dp(10), 0, 0);
        text.addView(labelView);
        text.addView(titleView);
        text.addView(metaView);
        text.addView(reasonView);
        text.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout buttons = row();
        TextView talk = primaryPill("Chiedi a Irbas");
        talk.setOnClickListener(v -> openAssistant());
        buttons.addView(talk, new LinearLayout.LayoutParams(-2, dp(40)));
        if (!target.isEmpty()) {
            TextView watch = pill("Guarda ora");
            final String destination = target;
            watch.setOnClickListener(v -> Apps.open(this, destination));
            buttons.addView(watch, spaced(dp(40)));
        }
        if (configured && hero != null) {
            TextView more = pill("Altre idee");
            more.setOnClickListener(v -> IrbasLink.call(this, "POST", "/tv/refresh", new JSONObject(), 15_000, (json, error) ->
                    toast(error == null ? "Cerco altre idee: tra qualche minuto le trovi qui." : error)));
            buttons.addView(more, spaced(dp(40)));
        }
        text.addView(buttons);
        firstFocus = talk;

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -1, 1f);
        textParams.leftMargin = dp(18);
        card.addView(text, textParams);
        return card;
    }

    /** La colonna a destra: le novità della sera, o le scorciatoie se i consigli non ci sono ancora. */
    private View side(JSONArray news) {
        LinearLayout side = column();
        TextView header = text(news != null && news.length() > 0 ? "NOVITÀ PER TE" : "SCORCIATOIE", 10, Glass.MUTED, true);
        header.setLetterSpacing(0.18f);
        header.setPadding(dp(6), dp(2), 0, dp(8));
        side.addView(header);
        List<View> rows = new ArrayList<>();
        if (news != null) {
            for (int i = 0; i < news.length() && rows.size() < 3; i++) {
                JSONObject item = news.optJSONObject(i);
                if (item == null) continue;
                final String target = item.optString("package", "");
                rows.add(sideRow(item.optString("label", ""), item.optString("title", ""), item.optString("meta", ""),
                        item.optBoolean("skip", false), target.isEmpty() ? null : () -> Apps.open(this, target)));
            }
        }
        if (rows.isEmpty()) {
            if (Apps.canOpen(this, TIM)) {
                rows.add(sideRow("TIMVISION", "Canali e programmi TIM", "La schermata di TIM, con i canali in diretta", false, () -> Apps.open(this, TIM)));
            } else {
                rows.add(sideRow("IRBAS", "Chiedi a Irbas", "Premi il microfono del telecomando e parlami", false, this::openAssistant));
            }
            rows.add(sideRow("APP", "Tutte le app", "Giochi, musica e tutto il resto", false, this::openAllApps));
            rows.add(sideRow("TELEFONO", "Telecomando sul telefono", "Inquadra il QR e comandi la TV dal telefono", false,
                    () -> startActivity(new Intent(this, PairActivity.class))));
        }
        for (int i = 0; i < rows.size(); i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 0, 1f);
            if (i > 0) params.topMargin = dp(10);
            side.addView(rows.get(i), params);
        }
        return side;
    }

    private View sideRow(String label, String title, String meta, boolean skip, Runnable action) {
        LinearLayout row = column();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        if (action != null) {
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackground(Glass.focusable(this, 20));
            row.setOnClickListener(v -> action.run());
            row.setOnFocusChangeListener(grow);
        } else {
            row.setBackground(Glass.panel(this, 20, 0));
        }
        TextView l = text(label.toUpperCase(Locale.ITALIAN), 9.5f, skip ? Glass.VIOLET : Glass.ACCENT, true);
        l.setLetterSpacing(0.14f);
        l.setMaxLines(1);
        TextView t = text(title, 15, Glass.TEXT, true);
        t.setMaxLines(1);
        t.setPadding(0, dp(4), 0, dp(3));
        TextView m = text(meta, 11, Glass.MUTED, false);
        m.setMaxLines(1);
        row.addView(l);
        row.addView(t);
        row.addView(m);
        return row;
    }

    /** La fila delle app: banner veri, tutti interi, e in fondo «Tutte le app». */
    private View dock(List<ResolveInfo> apps) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        LinearLayout row = row();
        row.setPadding(dp(8), dp(12), dp(8), dp(12));
        List<ResolveInfo> favorites = new ArrayList<>();
        for (String name : FAVORITES) {
            for (ResolveInfo app : apps) {
                if (app.activityInfo.packageName.equals(name)) {
                    favorites.add(app);
                    break;
                }
            }
        }
        for (ResolveInfo app : favorites) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(168), dp(95));
            if (row.getChildCount() > 0) params.leftMargin = dp(20);
            row.addView(Apps.tile(this, app, labels.get(app.activityInfo.packageName), 18, null), params);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(168), dp(95));
        if (row.getChildCount() > 0) params.leftMargin = dp(20);
        row.addView(allAppsTile(), params);
        scroll.addView(row);
        return scroll;
    }

    private View allAppsTile() {
        LinearLayout tile = column();
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackground(Glass.focusable(this, 18));
        tile.addView(new GridIcon(this), new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView label = text("Tutte le app", 13, Glass.TEXT, true);
        label.setPadding(0, dp(8), 0, 0);
        tile.addView(label);
        tile.setOnClickListener(v -> openAllApps());
        tile.setOnFocusChangeListener((v, focused) ->
                v.animate().scaleX(focused ? 1.1f : 1f).scaleY(focused ? 1.1f : 1f).setDuration(160).start());
        return tile;
    }

    /** Quattro quadratini: l'icona di «Tutte le app». */
    private static final class GridIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GridIcon(Context context) {
            super(context);
            paint.setColor(Glass.TEXT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight());
            float cell = s * 0.42f;
            float gap = s - cell * 2;
            float r = cell * 0.3f;
            for (int i = 0; i < 4; i++) {
                float x = (i % 2) * (cell + gap);
                float y = (i / 2) * (cell + gap);
                canvas.drawRoundRect(x, y, x + cell, y + cell, r, r, paint);
            }
        }
    }

    private void openAssistant() {
        startActivity(new Intent(this, AssistActivity.class));
    }

    private void openAllApps() {
        startActivity(new Intent(this, AppsActivity.class));
    }

    // MARK: pezzi comuni

    /** Il titolo grande con una sfumatura di luce, dal bianco all'azzurro di Irbas. */
    private void shine(TextView view) {
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            float width = Math.max(1, r - l);
            view.getPaint().setShader(new LinearGradient(0, 0, width, 0,
                    new int[]{0xFFFFFFFF, 0xFFE6F8FF, 0xFF8FEAFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            view.invalidate();
        });
    }

    private void updateLink() {
        if (link == null) return;
        if (!IrbasLink.configured(this)) {
            link.setText("●  Irbas non collegato");
            link.setTextColor(Glass.AMBER);
        } else if (connected) {
            link.setText("●  Irbas collegato");
            link.setTextColor(Glass.GREEN);
        } else {
            link.setText("●  Irbas: mi collego…");
            link.setTextColor(Glass.MUTED);
        }
    }

    private void updateClock() {
        if (clock == null) return;
        Date now = new Date();
        String day = new SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(now);
        String time = new SimpleDateFormat("HH:mm", Locale.ITALIAN).format(now);
        clock.setText(capitalized(day) + "  ·  " + time);
    }

    private static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 13) return "Buongiorno";
        if (hour >= 13 && hour < 18) return "Buon pomeriggio";
        return "Buonasera";
    }

    private static String capitalized(String text) {
        if (text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.ITALIAN) + text.substring(1);
    }

    private TextView pill(String title) {
        TextView view = text(title, 14, Glass.TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), 0, dp(20), 0);
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(Glass.focusable(this, 999));
        view.setOnFocusChangeListener(grow);
        return view;
    }

    private TextView primaryPill(String title) {
        TextView view = text(title, 15, 0xFF07101E, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(26), 0, dp(26), 0);
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(Glass.primary(this));
        view.setOnFocusChangeListener(grow);
        return view;
    }

    private LinearLayout.LayoutParams spaced(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, height);
        params.leftMargin = dp(12);
        return params;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        return column;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(float value) {
        return Glass.dp(this, value);
    }
}
