package com.frankoabarza.tecladoremoto;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * El único servidor de la app. Vive aparte del teclado y de la accesibilidad, para que siga
 * encendido aunque se cambie de teclado un momento. Quien sepa escribir en la tablet
 * (teclado o accesibilidad) se registra aquí mientras esté activo.
 */
final class ServerHolder {

    private static RemoteServer server;
    private static volatile Injector keyboard;
    private static volatile Injector accessibility;
    private static String cachedPage;

    private ServerHolder() {
    }

    static void setKeyboard(Injector k) {
        keyboard = k;
    }

    static void clearKeyboard(Injector k) {
        if (keyboard == k) {
            keyboard = null;
        }
    }

    static void setAccessibility(Injector a) {
        accessibility = a;
    }

    static void clearAccessibility(Injector a) {
        if (accessibility == a) {
            accessibility = null;
        }
    }

    /** El teclado si tiene un campo activo; si no, accesibilidad; si no, lo que haya. */
    private static Injector pick() {
        Injector k = keyboard;
        if (k != null && k.isFieldActive()) {
            return k;
        }
        Injector a = accessibility;
        return a != null ? a : k;
    }

    /** Enciende el servidor si no lo está. Devuelve el puerto, o 0 si no se pudo abrir. */
    static synchronized int ensureStarted(Context context) {
        final Context app = context.getApplicationContext();
        if (server != null && server.isRunning()) {
            return Config.port(app);
        }
        server = new RemoteServer(new RemoteServer.Delegate() {
            @Override
            public String token() {
                return Config.token(app);
            }

            @Override
            public String page() {
                return loadPage(app);
            }

            @Override
            public boolean keyboardActive() {
                return keyboard != null || accessibility != null;
            }

            @Override
            public boolean fieldActive() {
                Injector i = pick();
                return i != null && i.isFieldActive();
            }

            @Override
            public String handle(Map<String, String> params) {
                Injector i = pick();
                return i == null ? "no_keyboard" : i.handleCommand(params);
            }
        });
        try {
            int port = server.start(Config.DEFAULT_PORT);
            Config.setPort(app, port);
            return port;
        } catch (IOException e) {
            server = null;
            Config.setPort(app, 0);
            return 0;
        }
    }

    static synchronized void stop(Context context) {
        if (server != null) {
            server.stop();
            server = null;
        }
        Config.setPort(context.getApplicationContext(), 0);
    }

    private static synchronized String loadPage(Context app) {
        if (cachedPage == null) {
            try (InputStream in = app.getAssets().open("index.html")) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                }
                cachedPage = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "<h1>No se pudo cargar la página</h1>";
            }
        }
        return cachedPage;
    }
}
