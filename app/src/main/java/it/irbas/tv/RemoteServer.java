package it.irbas.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Il telecomando del telefono parla con questo piccolo server (porta 8790, sulla Wi-Fi o con Tailscale).
 * Ogni richiesta deve avere lo stesso codice del server di Irbas (X-Irbas-Code).
 */
final class RemoteServer implements Runnable {
    static final int PORT = 8790;
    private static RemoteServer running;
    private static String pendingAsk;

    private final Context context;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Map<String, byte[]> banners = new HashMap<>();

    private static final Map<String, Integer> KEYS = new HashMap<>();
    static {
        KEYS.put("up", 19);
        KEYS.put("down", 20);
        KEYS.put("left", 21);
        KEYS.put("right", 22);
        KEYS.put("ok", 23);
        KEYS.put("back", 4);
        KEYS.put("home", 3);
        KEYS.put("menu", 82);
        KEYS.put("playpause", 85);
        KEYS.put("next", 87);
        KEYS.put("previous", 88);
        KEYS.put("rewind", 89);
        KEYS.put("forward", 90);
        KEYS.put("volup", 24);
        KEYS.put("voldown", 25);
        KEYS.put("mute", 164);
        KEYS.put("wake", 224);
        KEYS.put("sleep", 223);
        KEYS.put("channelup", 166);
        KEYS.put("channeldown", 167);
        KEYS.put("tvinput", 178);
    }

    private RemoteServer(Context context) {
        this.context = context.getApplicationContext();
    }

    static synchronized void start(Context context) {
        if (running != null) return;
        running = new RemoteServer(context);
        Thread thread = new Thread(running, "irbas-remote");
        thread.setDaemon(true);
        thread.start();
    }

    /** Una domanda arrivata dal telefono, che «Chiedi a Irbas» mostra e legge sulla TV. */
    static synchronized String takeAsk() {
        String text = pendingAsk;
        pendingAsk = null;
        return text;
    }

    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                Socket client = server.accept();
                pool.execute(() -> handle(client));
            }
        } catch (IOException e) {
            synchronized (RemoteServer.class) {
                running = null;
            }
        }
    }

    // MARK: HTTP essenziale

    private void handle(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(20_000);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            String requestLine = line(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String target = parts[1];
            Map<String, String> headers = new HashMap<>();
            String header;
            while ((header = line(in)) != null && !header.isEmpty()) {
                int colon = header.indexOf(':');
                if (colon > 0) headers.put(header.substring(0, colon).trim().toLowerCase(Locale.ROOT), header.substring(colon + 1).trim());
            }
            int length = 0;
            try {
                length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (NumberFormatException ignored) {
                length = 0;
            }
            byte[] body = new byte[Math.min(Math.max(0, length), 64 * 1024)];
            int read = 0;
            while (read < body.length) {
                int n = in.read(body, read, body.length - read);
                if (n < 0) break;
                read += n;
            }

            String path = target;
            Map<String, String> query = new HashMap<>();
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                for (String pair : target.substring(q + 1).split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) query.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
                }
            }

            // la pagina del telecomando, il manifest e l'icona sono pubblici: i comandi no
            if (method.equals("GET") && (path.equals("/") || path.equals("/remote"))) {
                bytes(socket.getOutputStream(), 200, "text/html; charset=utf-8", asset("remote.html"));
                return;
            }
            if (method.equals("GET") && path.equals("/manifest.json")) {
                bytes(socket.getOutputStream(), 200, "application/manifest+json", manifest());
                return;
            }
            if (method.equals("GET") && path.equals("/icon.png")) {
                bytes(socket.getOutputStream(), 200, "image/png", icon());
                return;
            }

            // il codice del server di Irbas (app del telefono) oppure il codice di 6 cifre mostrato sulla TV
            String code = IrbasLink.prefs(context).getString("code", "");
            String pin = headers.get("x-tv-pin");
            if (pin == null) pin = query.get("pin");
            boolean allowed = (!code.isEmpty() && code.equals(headers.get("x-irbas-code"))) || Pairing.pin(context).equals(pin);
            if (!allowed) {
                json(socket.getOutputStream(), 401, new JSONObject().put("error", "Codice sbagliato: guarda il codice sulla TV (Irbas TV › Telefono)"));
                return;
            }
            JSONObject input = body.length == 0 ? new JSONObject() : new JSONObject(new String(body, StandardCharsets.UTF_8));
            route(socket.getOutputStream(), method, path, query, input);
        } catch (Exception ignored) {
            // la connessione si chiude e il telefono riprova
        }
    }

    private void route(OutputStream out, String method, String path, Map<String, String> query, JSONObject input) throws Exception {
        switch (method + " " + path) {
            case "GET /tv/state":
                json(out, 200, state());
                return;
            case "GET /tv/banner": {
                byte[] png = banner(query.getOrDefault("pkg", ""));
                if (png == null) json(out, 404, new JSONObject().put("error", "nessuna immagine"));
                else bytes(out, 200, "image/png", png);
                return;
            }
            case "POST /tv/key": {
                String name = input.optString("key", "");
                Integer key = KEYS.get(name);
                if (key == null) {
                    json(out, 400, new JSONObject().put("error", "tasto sconosciuto"));
                    return;
                }
                if (press(name, key)) json(out, 200, new JSONObject().put("ok", true));
                else json(out, 503, new JSONObject().put("error", "Per questo tasto attiva «Irbas TV» in Impostazioni › Accessibilità sulla TV"));
                return;
            }
            case "POST /tv/power": {
                PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                boolean awake = power != null && power.isInteractive();
                try {
                    LocalAdb.get(context).shell("input keyevent " + (awake ? 223 : 224));
                    json(out, 200, new JSONObject().put("ok", true).put("awake", !awake));
                } catch (IOException e) {
                    json(out, 503, new JSONObject().put("error", "Accendere e spegnere dal telefono richiede il debug di rete sulla TV (vedi le istruzioni)"));
                }
                return;
            }
            case "POST /tv/open": {
                String pkg = input.optString("pkg", "");
                String component = component(pkg);
                if (component == null) {
                    json(out, 404, new JSONObject().put("error", "app non trovata"));
                    return;
                }
                if (HomeKeyService.TIM.equals(pkg)) IrbasLink.prefs(context).edit().putBoolean("timAllowed", true).apply();
                Intent launch = launchIntent(pkg);
                if (start(launch, "am start -n " + component)) json(out, 200, new JSONObject().put("ok", true));
                else json(out, 503, new JSONObject().put("error", "Non riesco ad aprire l'app: attiva «Irbas TV» in Accessibilità sulla TV"));
                return;
            }
            case "POST /tv/text": {
                String text = input.optString("text", "");
                boolean typed = text.isEmpty();
                HomeKeyService service = HomeKeyService.get();
                if (!typed && service != null) typed = service.type(text);
                if (!typed) {
                    try {
                        LocalAdb.get(context).shell("input text " + shellText(text));
                        typed = true;
                    } catch (IOException ignored) {
                        typed = false;
                    }
                }
                if (typed) json(out, 200, new JSONObject().put("ok", true));
                else json(out, 503, new JSONObject().put("error", "Seleziona prima un campo di testo sulla TV"));
                return;
            }
            case "POST /tv/ask": {
                String text = input.optString("text", "").trim();
                if (text.isEmpty()) {
                    json(out, 400, new JSONObject().put("error", "domanda vuota"));
                    return;
                }
                synchronized (RemoteServer.class) {
                    pendingAsk = text;
                }
                Intent assist = new Intent(context, AssistActivity.class);
                if (start(assist, "am start -n it.irbas.tv/.AssistActivity")) json(out, 200, new JSONObject().put("ok", true));
                else json(out, 503, new JSONObject().put("error", "Non riesco ad aprire Irbas sulla TV"));
                return;
            }
            default:
                json(out, 404, new JSONObject().put("error", "Non conosco " + path));
        }
    }

    // MARK: cosa sa il box

    private JSONObject state() throws Exception {
        Map<String, String> labels = new LinkedHashMap<>();
        List<ResolveInfo> apps = Apps.installed(context, labels);
        JSONArray list = new JSONArray();
        for (String pkg : HomeActivity.FAVORITES) {
            if (labels.containsKey(pkg)) list.put(new JSONObject().put("pkg", pkg).put("label", labels.get(pkg)).put("favorite", true));
        }
        for (ResolveInfo app : apps) {
            String pkg = app.activityInfo.packageName;
            if (!HomeActivity.FAVORITES.contains(pkg)) list.put(new JSONObject().put("pkg", pkg).put("label", labels.get(pkg)).put("favorite", false));
        }
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        JSONObject state = new JSONObject()
                .put("name", Build.MODEL == null ? "TV" : Build.MODEL)
                .put("adb", adbWorks())
                .put("accessibility", HomeKeyService.get() != null)
                .put("version", 1)
                .put("awake", power != null && power.isInteractive())
                .put("apps", list);
        try {
            String top = LocalAdb.get(context).shell("dumpsys activity activities | grep -m1 topResumedActivity");
            int start = top.indexOf(" u0 ");
            if (start >= 0) {
                String rest = top.substring(start + 4).trim();
                int slash = rest.indexOf('/');
                if (slash > 0) {
                    String pkg = rest.substring(0, slash);
                    String label = labels.get(pkg);
                    if (pkg.equals(context.getPackageName())) label = "Irbas TV";
                    state.put("now", label == null ? pkg : label).put("nowPackage", pkg);
                }
            }
        } catch (IOException ignored) {
            // senza adb non so cosa c'è sullo schermo
        }
        return state;
    }

    // MARK: tasti e aperture: debug di rete se c'è, altrimenti il servizio di accessibilità

    private long adbFailedAt;

    private boolean adbWorks() {
        if (System.currentTimeMillis() - adbFailedAt < 60_000) return false;
        try {
            LocalAdb.get(context).shell("echo ok");
            return true;
        } catch (IOException e) {
            adbFailedAt = System.currentTimeMillis();
            return false;
        }
    }

    private boolean press(String name, int key) {
        // volume e tasti multimediali: li gestisce Android, senza permessi speciali
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio != null) {
            switch (name) {
                case "volup":
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                    return true;
                case "voldown":
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    return true;
                case "mute":
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                    return true;
                case "playpause":
                case "next":
                case "previous":
                case "rewind":
                case "forward": {
                    long now = SystemClock.uptimeMillis();
                    audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0));
                    audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0));
                    return true;
                }
                default:
                    break;
            }
        }
        if (adbFailedAt == 0 || System.currentTimeMillis() - adbFailedAt > 60_000) {
            try {
                LocalAdb.get(context).shell("input keyevent " + key);
                return true;
            } catch (IOException e) {
                adbFailedAt = System.currentTimeMillis();
            }
        }
        HomeKeyService service = HomeKeyService.get();
        if (service == null) return false;
        switch (name) {
            case "ok": return service.ok();
            case "back": return service.back();
            case "home":
                service.goHome();
                return true;
            default: {
                int direction = HomeKeyService.direction(name);
                return direction != 0 && service.move(direction);
            }
        }
    }

    /** Apre un'attività: col servizio di accessibilità (sempre permesso), poi con adb, poi direttamente. */
    private boolean start(Intent intent, String adbCommand) {
        if (intent == null) return false;
        HomeKeyService service = HomeKeyService.get();
        if (service != null && service.launch(new Intent(intent))) return true;
        try {
            LocalAdb.get(context).shell(adbCommand);
            return true;
        } catch (IOException ignored) {
            // niente adb
        }
        try {
            context.startActivity(new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Intent launchIntent(String pkg) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }

    // MARK: la pagina del telecomando

    private byte[] asset(String name) throws IOException {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static byte[] manifest() throws Exception {
        JSONObject icon = new JSONObject().put("src", "/icon.png").put("sizes", "512x512").put("type", "image/png");
        return new JSONObject()
                .put("name", "Irbas TV")
                .put("short_name", "Irbas TV")
                .put("start_url", "/")
                .put("display", "standalone")
                .put("background_color", "#020306")
                .put("theme_color", "#020306")
                .put("icons", new JSONArray().put(icon))
                .toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] icon;

    private synchronized byte[] icon() {
        if (icon != null) return icon;
        Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF020306);
        android.graphics.Paint arc = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        android.graphics.Paint pill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        IrbasLogoView.paint(canvas, 256, 256, 300, arc, pill, new android.graphics.RectF(), new android.graphics.RectF());
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
        icon = png.toByteArray();
        return icon;
    }

    private String component(String pkg) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null || intent.getComponent() == null) return null;
        return intent.getComponent().flattenToShortString();
    }

    private synchronized byte[] banner(String pkg) {
        if (pkg.isEmpty()) return null;
        byte[] cached = banners.get(pkg);
        if (cached != null) return cached;
        try {
            PackageManager pm = context.getPackageManager();
            Drawable drawable = pm.getApplicationBanner(pkg);
            boolean icon = drawable == null;
            if (icon) drawable = pm.getApplicationIcon(pkg);
            int w = icon ? 192 : 320;
            int h = icon ? 192 : 180;
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, w, h);
            drawable.draw(canvas);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
            byte[] data = png.toByteArray();
            banners.put(pkg, data);
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    /** Testo per «input text»: gli spazi diventano %s, il resto tra apici. */
    private static String shellText(String text) {
        String clean = text.replace("\n", " ").replace("'", "").replace(" ", "%s");
        return "'" + clean + "'";
    }

    // MARK: risposte

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') buffer.write(c);
            if (buffer.size() > 8192) break;
        }
        if (c == -1 && buffer.size() == 0) return null;
        return buffer.toString("UTF-8");
    }

    private static void json(OutputStream out, int status, JSONObject body) throws IOException {
        bytes(out, status, "application/json; charset=utf-8", body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void bytes(OutputStream out, int status, String type, byte[] body) throws IOException {
        String reason = status == 200 ? "OK" : status == 401 ? "Unauthorized" : status == 404 ? "Not Found" : status == 503 ? "Unavailable" : "Error";
        String head = "HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }
}
