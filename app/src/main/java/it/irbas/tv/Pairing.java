package it.irbas.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** L'abbinamento col telefono: un codice di 6 cifre mostrato sulla TV e un QR che apre il telecomando. */
final class Pairing {
    private Pairing() {}

    static String pin(Context context) {
        SharedPreferences prefs = IrbasLink.prefs(context);
        String pin = prefs.getString("pin", "");
        if (pin.length() != 6) {
            pin = String.format("%06d", new SecureRandom().nextInt(1_000_000));
            prefs.edit().putString("pin", pin).apply();
        }
        return pin;
    }

    /** Un codice nuovo: i telefoni abbinati prima vanno abbinati di nuovo. */
    static void reset(Context context) {
        IrbasLink.prefs(context).edit().remove("pin").apply();
        pin(context);
    }

    /** L'indirizzo della TV sulla rete di casa (Wi-Fi o cavo). */
    static String address() {
        String fallback = null;
        try {
            for (NetworkInterface net : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!net.isUp() || net.isLoopback()) continue;
                for (InetAddress address : Collections.list(net.getInetAddresses())) {
                    if (!(address instanceof Inet4Address)) continue;
                    String ip = address.getHostAddress();
                    if (address.isSiteLocalAddress()) return ip;   // 192.168.x.x, 10.x.x.x
                    if (fallback == null) fallback = ip;
                }
            }
        } catch (Exception ignored) {
            // nessuna rete
        }
        return fallback;
    }

    static String url(Context context) {
        String ip = address();
        if (ip == null) return null;
        return "http://" + ip + ":" + RemoteServer.PORT + "/#pin=" + pin(context);
    }

    /** Il QR code, nero su bianco, `size` pixel per lato. */
    static Bitmap qr(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) pixels[y * w + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            return null;
        }
    }
}
