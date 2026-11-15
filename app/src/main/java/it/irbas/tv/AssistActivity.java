package it.irbas.tv;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * «Chiedi a Irbas»: si apre col microfono del telecomando, anche sopra Netflix o YouTube.
 * Ascolta, apre da sé le app («apri Netflix»), il resto lo chiede a Irbas sul server e legge la risposta.
 */
public class AssistActivity extends Activity implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final int MIC = 7;
    private static final String[] OPEN_WORDS = {"apri ", "metti ", "vai su ", "vai a ", "avvia ", "lancia ", "fammi vedere "};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private OrbView orb;
    private VoiceWave wave;
    private TextView status;
    private TextView heard;
    private TextView answer;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;
    private boolean listening;
    private final Runnable close = this::finish;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF2020306, 0xB3020306, 0x66020306}));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setBackground(Glass.panel(this, 34, 1));
        panel.setPadding(dp(20), dp(24), dp(36), dp(24));

        orb = new OrbView(this);
        panel.addView(orb, new LinearLayout.LayoutParams(dp(150), dp(150)));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        status = text("TI ASCOLTO…", 12, Glass.ACCENT, true);
        status.setLetterSpacing(0.16f);
        top.addView(status);
        wave = new VoiceWave(this);
        LinearLayout.LayoutParams waveParams = new LinearLayout.LayoutParams(dp(46), dp(22));
        waveParams.leftMargin = dp(14);
        top.addView(wave, waveParams);
        heard = text("", 24, Glass.TEXT, true);
        heard.setPadding(0, dp(8), 0, 0);
        heard.setMaxLines(3);
        answer = text("", 16, Glass.SOFT, false);
        answer.setPadding(0, dp(10), 0, 0);
        answer.setMaxLines(7);
        answer.setLineSpacing(dp(3), 1f);
        TextView hint = text("OK per parlare di nuovo  ·  Indietro per chiudere", 11, Glass.MUTED, false);
        hint.setPadding(0, dp(14), 0, 0);
        column.addView(top);
        column.addView(heard);
        column.addView(answer);
        column.addView(hint);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, -2, 1f);
        columnParams.leftMargin = dp(22);
        panel.addView(column, columnParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(820), -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        panelParams.bottomMargin = dp(44);
        root.addView(panel, panelParams);
        setContentView(root);

        tts = new TextToSpeech(this, this);
        String fromPhone = RemoteServer.takeAsk();
        if (fromPhone != null) handle(fromPhone);
        else listen();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String fromPhone = RemoteServer.takeAsk();
        if (fromPhone != null) handle(fromPhone);
        else listen();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
            case KeyEvent.KEYCODE_ASSIST:
                listen();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) listen();
        else status.setText("Senza il permesso del microfono non posso ascoltarti.");
    }

    // MARK: ascolto

    private void listen() {
        handler.removeCallbacks(close);
        if (tts != null) tts.stop();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("Il riconoscimento vocale non è disponibile su questo box.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC);
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
        } else if (listening) {
            recognizer.cancel();
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        heard.setText("");
        answer.setText("");
        status.setText("TI ASCOLTO…");
        wave.setVisibility(android.view.View.VISIBLE);
        orb.setListening(true);
        listening = true;
        recognizer.startListening(intent);
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) {
        if (wave != null) wave.setLevel(rmsdB);
    }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onEndOfSpeech() {
        status.setText("HO CAPITO…");
        wave.setVisibility(android.view.View.INVISIBLE);
    }

    @Override
    public void onPartialResults(Bundle partial) {
        String text = first(partial);
        if (!text.isEmpty()) heard.setText(text);
    }

    @Override
    public void onResults(Bundle results) {
        listening = false;
        wave.setVisibility(android.view.View.INVISIBLE);
        orb.setListening(false);
        String text = first(results);
        if (text.isEmpty()) {
            status.setText("Non ho sentito niente. Premi OK e riprova.");
            return;
        }
        handle(text);
    }

    @Override
    public void onError(int error) {
        listening = false;
        wave.setVisibility(android.view.View.INVISIBLE);
        orb.setListening(false);
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            status.setText("Non ho sentito niente. Premi OK e riprova.");
        } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            status.setText("Manca il permesso del microfono: rilancia collega.sh dal Mac.");
        } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
            status.setText("Il riconoscimento vocale non raggiunge internet.");
        } else {
            status.setText("Il microfono non ha funzionato (errore " + error + "). Premi OK e riprova.");
        }
    }

    private static String first(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return list == null || list.isEmpty() || list.get(0) == null ? "" : list.get(0).trim();
    }

    // MARK: cosa fare

    private void handle(String text) {
        heard.setText("«" + text + "»");
        if (openApp(text)) return;
        if (!IrbasLink.configured(this)) {
            status.setText("SERVE IRBAS PER RISPONDERE");
            answer.setText("Le app le apro da solo: prova «apri YouTube». Per le domande serve un server Irbas collegato.");
            return;
        }
        status.setText("CI PENSO…");
        JSONObject body = new JSONObject();
        try {
            body.put("text", text);
        } catch (Exception ignored) {
            // non succede con una stringa
        }
        ask("/tv/ask", body);
    }

    /** Chiede a Irbas; se il server non conosce ancora le domande dalla TV, usa la chat normale. */
    private void ask(String path, JSONObject body) {
        IrbasLink.call(this, "POST", path, body, 180_000, (json, error) -> {
            if (isFinishing()) return;
            if (error != null && path.equals("/tv/ask") && error.startsWith("Non conosco")) {
                ask("/ask", body);
                return;
            }
            if (error != null) {
                status.setText("NON RIESCO A PARLARE CON IRBAS");
                answer.setText(error);
                return;
            }
            String reply = json.optString("answer", "").trim();
            status.setText("IRBAS");
            answer.setText(reply);
            speak(reply);
            handler.postDelayed(close, 45_000);
        });
    }

    /** «Apri Netflix», «metti YouTube»: lo faccio subito, senza chiedere al server. */
    private boolean openApp(String text) {
        String said = plain(text);
        String wanted = null;
        for (String word : OPEN_WORDS) {
            if (said.startsWith(word)) {
                wanted = said.substring(word.length()).trim();
                break;
            }
        }
        if (wanted == null || wanted.isEmpty()) return false;
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(query, 0);
        ResolveInfo best = null;
        for (ResolveInfo app : apps) {
            CharSequence label = app.loadLabel(pm);
            if (label == null) continue;
            String name = plain(label.toString());
            if (name.isEmpty()) continue;
            if (name.equals(wanted)) {
                best = app;
                break;
            }
            if (best == null && (name.contains(wanted) || wanted.contains(name))) best = app;
        }
        if (best == null) return false;
        String name = best.loadLabel(pm).toString();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) return false;
        status.setText("APRO " + name.toUpperCase(Locale.ITALIAN));
        speak("Apro " + name);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Intent go = launch;
        handler.postDelayed(() -> {
            try {
                startActivity(go);
            } catch (Exception ignored) {
                // l'app non si è aperta: resta la schermata
            }
            finish();
        }, 700);
        return true;
    }

    private static String plain(String text) {
        String folded = Normalizer.normalize(text.toLowerCase(Locale.ITALIAN), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return folded.replaceAll("[^a-z0-9+ ]", " ").replaceAll("\\s+", " ").trim();
    }

    // MARK: voce

    @Override
    public void onInit(int result) {
        if (result != TextToSpeech.SUCCESS) return;
        tts.setLanguage(Locale.ITALIAN);
        ttsReady = true;
        if (pendingSpeech != null) {
            speak(pendingSpeech);
            pendingSpeech = null;
        }
    }

    private void speak(String text) {
        String clean = text.replaceAll("[*_#`>]", "").replaceAll("https?://\\S+", "il link").trim();
        if (clean.isEmpty()) return;
        if (!ttsReady) {
            pendingSpeech = clean;
            return;
        }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "irbas");
    }

    // MARK: pezzi comuni

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private int dp(float value) {
        return Glass.dp(this, value);
    }
}
