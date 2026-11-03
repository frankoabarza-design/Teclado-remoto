package com.frankoabarza.tecladoremoto;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Servidor HTTP mínimo para recibir teclas desde otro dispositivo de la misma red.
 * No usa clases de Android, así que se puede probar en cualquier JVM.
 *
 * Rutas:
 *   GET  /            página con el teclado (sin código)
 *   GET  /api/ping    estado (pide el código en ?k=)
 *   POST /api/send    envía texto, una tecla o una acción (pide el código en k=)
 */
public class RemoteServer {

    public interface Delegate {
        /** Código que debe presentar el cliente. */
        String token();

        /** HTML de la página de control. */
        String page();

        /** true si hay un campo de texto listo para recibir. */
        boolean fieldActive();

        /** Ejecuta la orden. Devuelve "ok", "no_field" u otro texto de error. */
        String handle(Map<String, String> params);
    }

    private static final int MAX_BODY = 64 * 1024;
    private static final int MAX_FAILURES = 10;
    private static final long LOCK_MILLIS = 60_000L;

    private final Delegate delegate;
    private ServerSocket server;
    private ExecutorService pool;
    private volatile boolean running;

    private int failures;
    private long lockedUntil;

    public RemoteServer(Delegate delegate) {
        this.delegate = delegate;
    }

    /** Abre el primer puerto libre desde firstPort. Devuelve el puerto usado. */
    public synchronized int start(int firstPort) throws IOException {
        IOException lastError = null;
        for (int port = firstPort; port < firstPort + 10; port++) {
            ServerSocket candidate = new ServerSocket();
            try {
                candidate.setReuseAddress(true);
                candidate.bind(new InetSocketAddress(port));
                server = candidate;
                break;
            } catch (IOException e) {
                lastError = e;
                try {
                    candidate.close();
                } catch (IOException ignored) {
                    // nada que hacer
                }
            }
        }
        if (server == null) {
            throw lastError != null ? lastError : new IOException("No hay puertos libres");
        }
        pool = Executors.newFixedThreadPool(4);
        running = true;
        Thread acceptor = new Thread(this::acceptLoop, "teclado-remoto-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        return server.getLocalPort();
    }

    public synchronized void stop() {
        running = false;
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // nada que hacer
            }
            server = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    private void acceptLoop() {
        final ServerSocket s = server;
        while (running && s != null && !s.isClosed()) {
            try {
                final Socket client = s.accept();
                ExecutorService p = pool;
                if (p == null) {
                    client.close();
                    return;
                }
                p.execute(() -> serve(client));
            } catch (IOException e) {
                if (!running || s.isClosed()) {
                    return;
                }
            } catch (RejectedExecutionException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(5000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                respond(out, 400, "text/plain; charset=utf-8", "Solicitud no válida");
                return;
            }
            String method = parts[0];
            String target = parts[1];

            int length = 0;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                if (line.regionMatches(true, 0, "content-length:", 0, 15)) {
                    try {
                        length = Integer.parseInt(line.substring(15).trim());
                    } catch (NumberFormatException ignored) {
                        length = -1;
                    }
                }
            }
            if (length < 0 || length > MAX_BODY) {
                respond(out, 413, "text/plain; charset=utf-8", "Mensaje demasiado grande");
                return;
            }
            byte[] body = new byte[length];
            int read = 0;
            while (read < length) {
                int n = in.read(body, read, length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }

            String path = target;
            String query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            }
            Map<String, String> params = parseForm(query);
            if ("POST".equals(method)) {
                params.putAll(parseForm(new String(body, 0, read, StandardCharsets.UTF_8)));
            }
            route(out, method, path, params);
        } catch (IOException ignored) {
            // cliente desconectado o con tiempo agotado
        }
    }

    private void route(OutputStream out, String method, String path, Map<String, String> params)
            throws IOException {
        if ("GET".equals(method) && (path.equals("/") || path.equals("/index.html"))) {
            respond(out, 200, "text/html; charset=utf-8", delegate.page());
            return;
        }
        if ("GET".equals(method) && path.equals("/api/ping")) {
            int auth = authorize(params.get("k"));
            if (auth != 200) {
                respondAuthError(out, auth);
                return;
            }
            respond(out, 200, "application/json", "{\"ok\":true,\"field\":" + delegate.fieldActive() + "}");
            return;
        }
        if ("POST".equals(method) && path.equals("/api/send")) {
            int auth = authorize(params.get("k"));
            if (auth != 200) {
                respondAuthError(out, auth);
                return;
            }
            String result = delegate.handle(params);
            if ("ok".equals(result)) {
                respond(out, 200, "application/json", "{\"ok\":true}");
            } else if ("no_field".equals(result)) {
                respond(out, 409, "application/json", "{\"ok\":false,\"error\":\"no_field\"}");
            } else {
                respond(out, 400, "application/json", "{\"ok\":false,\"error\":\"" + result + "\"}");
            }
            return;
        }
        respond(out, 404, "text/plain; charset=utf-8", "No encontrado");
    }

    /** 200 si el código es correcto, 401 si no, 429 si está bloqueado por demasiados intentos. */
    private synchronized int authorize(String given) {
        long now = System.currentTimeMillis();
        if (now < lockedUntil) {
            return 429;
        }
        String expected = delegate.token();
        boolean ok = given != null && expected != null
                && MessageDigest.isEqual(
                        given.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8));
        if (ok) {
            failures = 0;
            return 200;
        }
        failures++;
        if (failures >= MAX_FAILURES) {
            failures = 0;
            lockedUntil = now + LOCK_MILLIS;
        }
        return 401;
    }

    private void respondAuthError(OutputStream out, int status) throws IOException {
        respond(out, status, "application/json", "{\"ok\":false,\"error\":\"auth\"}");
    }

    private static void respond(OutputStream out, int status, String type, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + " " + reason(status) + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200:
                return "OK";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 404:
                return "Not Found";
            case 409:
                return "Conflict";
            case 413:
                return "Payload Too Large";
            case 429:
                return "Too Many Requests";
            default:
                return "Error";
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                buf.write(c);
            }
            if (buf.size() > 8192) {
                throw new IOException("Línea demasiado larga");
            }
        }
        if (c == -1 && buf.size() == 0) {
            return null;
        }
        return buf.toString("UTF-8");
    }

    static Map<String, String> parseForm(String data) {
        Map<String, String> map = new HashMap<>();
        if (data == null || data.isEmpty()) {
            return map;
        }
        for (String pair : data.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawKey = eq >= 0 ? pair.substring(0, eq) : pair;
            String rawValue = eq >= 0 ? pair.substring(eq + 1) : "";
            try {
                map.put(URLDecoder.decode(rawKey, "UTF-8"), URLDecoder.decode(rawValue, "UTF-8"));
            } catch (IOException | IllegalArgumentException ignored) {
                // se ignora el par mal codificado
            }
        }
        return map;
    }
}
