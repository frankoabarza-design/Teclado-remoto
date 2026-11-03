package com.frankoabarza.tecladoremoto;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/** Código de acceso, puerto y direcciones de red compartidos entre la pantalla y el teclado. */
public final class Config {

    public static final int DEFAULT_PORT = 8765;
    private static final String PREFS = "teclado_remoto";

    private Config() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Código de 6 dígitos. Se crea la primera vez y se conserva hasta que se renueve. */
    public static synchronized String token(Context c) {
        SharedPreferences p = prefs(c);
        String t = p.getString("token", null);
        if (t == null) {
            t = newToken();
            p.edit().putString("token", t).apply();
        }
        return t;
    }

    public static synchronized String renewToken(Context c) {
        String t = newToken();
        prefs(c).edit().putString("token", t).apply();
        return t;
    }

    private static String newToken() {
        return String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1_000_000));
    }

    public static int port(Context c) {
        return prefs(c).getInt("port", DEFAULT_PORT);
    }

    public static void setPort(Context c, int port) {
        prefs(c).edit().putInt("port", port).apply();
    }

    /** Direcciones IPv4 privadas de esta tablet (Wi-Fi o punto de acceso). */
    public static List<String> addresses() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            while (all != null && all.hasMoreElements()) {
                NetworkInterface ni = all.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        out.add(a.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
            // se devuelve la lista que haya
        }
        return out;
    }

    public static String url(Context c, String ip) {
        return "http://" + ip + ":" + port(c) + "/?k=" + token(c);
    }

    /** Comprueba si el servidor responde en este mismo equipo. Llamar fuera del hilo principal. */
    public static boolean serverUp(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 400);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
