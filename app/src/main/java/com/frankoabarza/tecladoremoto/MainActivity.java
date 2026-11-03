package com.frankoabarza.tecladoremoto;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Pantalla de ayuda: elegir cómo escribir en la tablet, ver la dirección para el iPhone y probar. */
public class MainActivity extends Activity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(label("Teclado remoto", 28, true));
        root.addView(label("Escribe en esta tablet desde el iPhone, por Wi-Fi. Hay dos formas de "
                + "conectarlo a la tablet; con una basta.", 16, false));

        root.addView(label("Forma A: sin cambiar de teclado", 20, true));
        root.addView(label("Sigues usando el teclado Samsung. La app escribe en el campo de texto "
                + "que esté activo. Se enciende en Accesibilidad: Aplicaciones instaladas → "
                + "Teclado remoto → activar.", 15, false));
        root.addView(button("Abrir ajustes de accesibilidad", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(label("Si el interruptor sale gris o dice «Configuración restringida», toca el "
                + "botón de abajo, luego los tres puntos de arriba a la derecha → «Permitir "
                + "configuración restringida», y vuelve a intentar.", 14, false));
        root.addView(button("Abrir información de la app", v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())))));

        root.addView(label("Forma B: como teclado", 20, true));
        root.addView(label("Se activa el teclado «Teclado remoto» y se elige como teclado actual. "
                + "Para volver al de siempre, usa el botón «Cambiar de teclado» de la franja de abajo.",
                15, false));
        root.addView(button("Abrir ajustes de teclados", v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        root.addView(button("Elegir teclado", v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        }));

        root.addView(label("Estado y dirección para el iPhone", 20, true));
        status = label("", 16, false);
        status.setTextIsSelectable(true);
        root.addView(status);
        root.addView(label("El servidor queda encendido mientras veas el aviso «Teclado remoto "
                + "encendido» arriba en la tablet. La tablet y el iPhone deben estar en la misma red. "
                + "Si el Wi-Fi no deja que se vean entre sí, conecta la tablet al punto de acceso "
                + "del iPhone.", 14, false));
        root.addView(button("Actualizar", v -> refresh()));
        root.addView(button("Probar la página en esta tablet", v -> {
            Uri uri = Uri.parse("http://127.0.0.1:" + Config.port(this) + "/?k=" + Config.token(this));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }));
        root.addView(button("Cambiar el código", v -> {
            Config.renewToken(this);
            refresh();
        }));

        root.addView(label("Probar", 20, true));
        EditText test = new EditText(this);
        test.setHint("Toca aquí y escribe desde el iPhone");
        root.addView(test);

        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startServer();
        refresh();
        main.postDelayed(this::refresh, 1200); // el servidor tarda un instante en abrir el puerto
    }

    private void startServer() {
        try {
            startForegroundService(new Intent(this, ServerService.class));
        } catch (RuntimeException e) {
            ServerHolder.ensureStarted(this); // sin aviso fijo, pero al menos encendido
        }
    }

    private boolean keyboardChosen() {
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return ime != null && ime.startsWith(getPackageName() + "/");
    }

    private boolean accessibilityOn() {
        String list = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return list != null && list.contains(getPackageName() + "/");
    }

    private void refresh() {
        final int port = Config.port(this);
        final List<String> ips = Config.addresses();
        final StringBuilder urls = new StringBuilder();
        if (ips.isEmpty()) {
            urls.append("No encuentro la red. Conecta la tablet a un Wi-Fi.");
        } else {
            for (String ip : ips) {
                urls.append(Config.url(this, ip)).append("\n");
            }
        }
        final String modes = "Forma A (accesibilidad): " + (accessibilityOn() ? "activada" : "apagada")
                + "\nForma B (teclado elegido): " + (keyboardChosen() ? "sí" : "no");
        final String list = urls.toString().trim();
        status.setText("Comprobando el servidor…\n" + modes + "\n\n" + list);

        new Thread(() -> {
            final boolean up = port > 0 && Config.serverUp(port);
            runOnUiThread(() -> status.setText(
                    (up ? "Servidor encendido (puerto " + port + ")."
                        : "El servidor no está encendido. Toca «Actualizar»; si sigue igual, "
                            + "revisa que los avisos de la app estén permitidos.")
                            + "\n" + modes + "\n\n" + list));
        }).start();
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) {
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        }
        t.setPadding(0, dp(bold ? 18 : 6), 0, dp(4));
        return t;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
