package it.irbas.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * Un piccolo client ADB verso il box stesso (127.0.0.1:5555): serve per premere i tasti del telecomando
 * («input keyevent») e aprire le app quando i comandi arrivano dal telefono.
 * La prima volta la TV chiede «Consentire il debug?»: si risponde una volta sola, con «Consenti sempre».
 */
final class LocalAdb {
    private static final int CNXN = 0x4e584e43;
    private static final int AUTH = 0x48545541;
    private static final int OPEN = 0x4e45504f;
    private static final int OKAY = 0x59414b4f;
    private static final int CLSE = 0x45534c43;
    private static final int WRTE = 0x45545257;
    private static final int TOKEN = 1;
    private static final int SIGNATURE = 2;
    private static final int RSAPUBLICKEY = 3;
    private static final int WORDS = 64;   // chiave da 2048 bit

    private static LocalAdb shared;
    private final SharedPreferences prefs;
    private KeyPair keys;
    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private int nextId = 1;

    private LocalAdb(Context context) {
        prefs = IrbasLink.prefs(context);
    }

    static synchronized LocalAdb get(Context context) {
        if (shared == null) shared = new LocalAdb(context.getApplicationContext());
        return shared;
    }

    /** Esegue un comando nella shell del box e ne restituisce l'output. */
    synchronized String shell(String command) throws IOException {
        try {
            connect();
            int local = nextId++;
            send(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            int remote = 0;
            while (true) {
                Message m = read();
                if (m.command == OKAY) {
                    remote = m.arg0;
                } else if (m.command == WRTE) {
                    output.append(new String(m.data, StandardCharsets.UTF_8));
                    send(OKAY, local, m.arg0, null);
                } else if (m.command == CLSE) {
                    if (remote != 0) send(CLSE, local, remote, null);
                    break;
                }
            }
            return output.toString();
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    private void connect() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) return;
        socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", 5555), 1500);
        socket.setSoTimeout(90_000);   // la prima volta si aspetta il «Consenti» sulla TV
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        send(CNXN, 0x01000000, 4096, "host::irbas-tv\0".getBytes(StandardCharsets.UTF_8));
        boolean signed = false;
        boolean offered = false;
        Message m = read();
        while (m.command == AUTH) {
            if (m.arg0 == TOKEN) {
                if (!signed) {
                    send(AUTH, SIGNATURE, 0, sign(m.data));
                    signed = true;
                } else if (!offered) {
                    send(AUTH, RSAPUBLICKEY, 0, publicKey());
                    offered = true;
                } else {
                    throw new IOException("Il debug non è stato autorizzato sulla TV");
                }
            }
            m = read();
        }
        if (m.command != CNXN) throw new IOException("Risposta inattesa dal box");
        socket.setSoTimeout(15_000);
    }

    private void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // già chiuso
        }
        socket = null;
    }

    // MARK: messaggi

    private static final class Message {
        int command;
        int arg0;
        int arg1;
        byte[] data;
    }

    private void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        int length = data == null ? 0 : data.length;
        int sum = 0;
        if (data != null) for (byte b : data) sum += b & 0xFF;
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command).putInt(arg0).putInt(arg1).putInt(length).putInt(sum).putInt(~command);
        out.write(header.array());
        if (data != null) out.write(data);
        out.flush();
    }

    private Message read() throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer header = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = header.getInt();
        m.arg0 = header.getInt();
        m.arg1 = header.getInt();
        int length = header.getInt();
        m.data = new byte[Math.max(0, length)];
        if (length > 0) in.readFully(m.data);
        return m;
    }

    // MARK: chiavi (le stesse di adb: RSA 2048)

    private KeyPair keys() throws IOException {
        if (keys != null) return keys;
        try {
            String priv = prefs.getString("adbPrivate", "");
            String pub = prefs.getString("adbPublic", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (!priv.isEmpty() && !pub.isEmpty()) {
                keys = new KeyPair(
                        factory.generatePublic(new X509EncodedKeySpec(Base64.decode(pub, Base64.NO_WRAP))),
                        factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(priv, Base64.NO_WRAP))));
            } else {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(WORDS * 32);
                keys = generator.generateKeyPair();
                prefs.edit()
                        .putString("adbPrivate", Base64.encodeToString(keys.getPrivate().getEncoded(), Base64.NO_WRAP))
                        .putString("adbPublic", Base64.encodeToString(keys.getPublic().getEncoded(), Base64.NO_WRAP))
                        .apply();
            }
            return keys;
        } catch (Exception e) {
            throw new IOException("Chiavi ADB non disponibili: " + e.getMessage());
        }
    }

    /** Firma PKCS#1 v1.5 con SHA-1 del token (il token è già il «digest»), come fa adb. */
    private byte[] sign(byte[] token) throws IOException {
        try {
            PrivateKey key = keys().getPrivate();
            byte[] prefix = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
            int size = WORDS * 4;
            byte[] block = new byte[size];
            block[0] = 0x00;
            block[1] = 0x01;
            int padEnd = size - prefix.length - token.length - 1;
            for (int i = 2; i < padEnd; i++) block[i] = (byte) 0xFF;
            block[padEnd] = 0x00;
            System.arraycopy(prefix, 0, block, padEnd + 1, prefix.length);
            System.arraycopy(token, 0, block, padEnd + 1 + prefix.length, token.length);
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(block);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Firma non riuscita: " + e.getMessage());
        }
    }

    /** La chiave pubblica nel formato di adb (android_pubkey), in base64, con il nome. */
    private byte[] publicKey() throws IOException {
        RSAPublicKey key = (RSAPublicKey) keys().getPublic();
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        BigInteger n = key.getModulus();
        BigInteger r = BigInteger.ZERO.setBit(WORDS * 32);
        BigInteger rr = r.modPow(BigInteger.valueOf(2), n);
        BigInteger n0inv = n.remainder(r32).modInverse(r32);
        ByteBuffer buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(WORDS);
        buffer.putInt(n0inv.negate().intValue());
        BigInteger modulus = n;
        int[] words = new int[WORDS];
        int[] rrWords = new int[WORDS];
        for (int i = 0; i < WORDS; i++) {
            BigInteger[] split = rr.divideAndRemainder(r32);
            rr = split[0];
            rrWords[i] = split[1].intValue();
            split = modulus.divideAndRemainder(r32);
            modulus = split[0];
            words[i] = split[1].intValue();
        }
        for (int w : words) buffer.putInt(w);
        for (int w : rrWords) buffer.putInt(w);
        buffer.putInt(key.getPublicExponent().intValue());
        String text = Base64.encodeToString(buffer.array(), Base64.NO_WRAP) + " irbas@tv\0";
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
