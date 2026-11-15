package it.irbas.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Il collegamento con il server di Irbas (indirizzo e codice arrivano dal Mac con collega.sh). */
final class IrbasLink {
    interface Done {
        void result(JSONObject json, String error);
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private IrbasLink() {}

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("irbas", Context.MODE_PRIVATE);
    }

    static boolean configured(Context context) {
        SharedPreferences p = prefs(context);
        return !p.getString("server", "").isEmpty() && !p.getString("code", "").isEmpty();
    }

    static void call(Context context, String method, String path, JSONObject body, int timeoutMs, Done done) {
        SharedPreferences p = prefs(context);
        String server = p.getString("server", "").replaceAll("/+$", "");
        String code = p.getString("code", "");
        POOL.execute(() -> {
            JSONObject result = null;
            String error = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(server + path).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(timeoutMs);
                connection.setRequestMethod(method);
                connection.setRequestProperty("X-Irbas-Code", code);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (body != null) {
                    connection.setDoOutput(true);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                int status = connection.getResponseCode();
                String text = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
                JSONObject json = text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
                if (status >= 400) error = json.optString("error", "Errore " + status);
                else result = json;
            } catch (Exception e) {
                error = "Irbas non risponde: controlla che il box sia collegato a Tailscale e il server sia acceso.";
            }
            final JSONObject finalResult = result;
            final String finalError = error;
            MAIN.post(() -> done.result(finalResult, finalError));
        });
    }

    private static String read(InputStream in) throws java.io.IOException {
        if (in == null) return "";
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }
}
