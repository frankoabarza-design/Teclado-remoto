package com.frankoabarza.tecladoremoto;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * El único servidor de la app. Vive aparte del teclado, para que siga encendido aunque
 * se cambie a otro teclado un momento. El teclado solo se registra aquí mientras es el elegido.
 */
final class ServerHolder {

    private static RemoteServer server;
    private static volatile RemoteKeyboardService keyboard;
    private static String cachedPage;

    private ServerHolder() {
    }

    static void setKeyboard(RemoteKeyboardService k) {
        keyboard = k;
    }

    static void clearKeyboard(RemoteKeyboardService k) {
        if (keyboard == k) {
            keyboard = null;
        }
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
                return keyboard != null;
            }

            @Override
            public boolean fieldActive() {
                RemoteKeyboardService k = keyboard;
                return k != null && k.isFieldActive();
            }

            @Override
            public String handle(Map<String, String> params) {
                RemoteKeyboardService k = keyboard;
                return k == null ? "no_keyboard" : k.handleCommand(params);
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
