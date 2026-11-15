package it.irbas.tv;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/** Quanti minuti al giorno usi ogni app (solo il tempo, non cosa guardi). Serve il permesso dato dal Mac. */
final class Usage {
    private Usage() {}

    @SuppressWarnings("deprecation")
    static boolean allowed(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Gli ultimi 7 giorni e l'elenco delle app, nel formato che il server si aspetta. */
    static JSONObject payload(Context context, Map<String, String> labels) throws Exception {
        JSONObject body = new JSONObject();
        JSONArray installed = new JSONArray();
        for (Map.Entry<String, String> app : labels.entrySet()) {
            installed.put(new JSONObject().put("pkg", app.getKey()).put("label", app.getValue()));
        }
        body.put("installed", installed);
        JSONArray days = new JSONArray();
        if (allowed(context)) {
            UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            Calendar day = Calendar.getInstance();
            day.set(Calendar.HOUR_OF_DAY, 0);
            day.set(Calendar.MINUTE, 0);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            for (int i = 0; i < 7; i++) {
                long start = day.getTimeInMillis();
                long end = i == 0 ? System.currentTimeMillis() : start + 86_400_000L;
                Map<String, UsageStats> stats = manager.queryAndAggregateUsageStats(start, end);
                JSONArray apps = new JSONArray();
                for (Map.Entry<String, UsageStats> entry : stats.entrySet()) {
                    String label = labels.get(entry.getKey());
                    if (label == null) continue;
                    long minutes = entry.getValue().getTotalTimeInForeground() / 60_000L;
                    if (minutes < 1) continue;
                    apps.put(new JSONObject().put("pkg", entry.getKey()).put("label", label).put("minutes", minutes));
                }
                days.put(new JSONObject().put("date", format.format(day.getTime())).put("apps", apps));
                day.add(Calendar.DAY_OF_MONTH, -1);
            }
        }
        body.put("days", days);
        // dove il telefono trova il ricevitore del telecomando (Wi-Fi di casa e Tailscale)
        JSONArray addresses = new JSONArray();
        try {
            for (java.net.NetworkInterface net : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!net.isUp() || net.isLoopback()) continue;
                for (java.net.InetAddress address : java.util.Collections.list(net.getInetAddresses())) {
                    if (address instanceof java.net.Inet4Address) addresses.put(address.getHostAddress());
                }
            }
        } catch (Exception ignored) {
            // senza indirizzi il telefono usa quello scritto a mano
        }
        body.put("box", new JSONObject().put("port", RemoteServer.PORT).put("addresses", addresses));
        return body;
    }
}
