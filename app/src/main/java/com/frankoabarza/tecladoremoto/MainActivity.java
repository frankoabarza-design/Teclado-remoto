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

/** Pantalla de ayuda: activar el teclado, ver la dirección para el iPhone y probar. */
public class MainActivity extends Activity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView address;

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
        root.addView(label("Escribe en esta tablet desde el iPhone, por Wi-Fi.", 16, false));

        root.addView(label("1. Activar el teclado", 18, true));
        root.addView(label("Enciende el interruptor de «Teclado remoto» en la lista que se abre.", 15, false));
        root.addView(button("Abrir ajustes de teclados", v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));

        root.addView(label("2. Elegirlo como teclado actual", 18, true));
        root.addView(label("Sin este paso la tablet no recibe nada. Para volver a tu teclado de siempre, "
                + "usa el botón «Cambiar de teclado» que aparece en la parte de abajo.", 15, false));
        root.addView(button("Elegir teclado", v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        }));

        root.addView(label("3. Abrir en el iPhone", 18, true));
        address = label("", 16, false);
        address.setTextIsSelectable(true);
        root.addView(address);
        root.addView(label("El servidor queda encendido mientras veas el aviso «Teclado remoto encendido» "
                + "arriba en la tablet. La tablet y el iPhone deben estar en la misma red. Si el Wi-Fi "
                + "no deja que se vean entre sí, conecta la tablet al punto de acceso del iPhone.", 14, false));
        root.addView(button("Actualizar", v -> refresh()));
        root.addView(button("Probar la página en esta tablet", v -> {
            Uri uri = Uri.parse("http://127.0.0.1:" + Config.port(this) + "/?k=" + Config.token(this));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }));
        root.addView(button("Cambiar el código", v -> {
            Config.renewToken(this);
            refresh();
        }));

        root.addView(label("4. Probar", 18, true));
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
        final String list = urls.toString().trim();
        address.setText("Comprobando el servidor…\n\n" + list);

        new Thread(() -> {
            final boolean up = port > 0 && Config.serverUp(port);
            runOnUiThread(() -> address.setText(
                    (up ? "Servidor encendido (puerto " + port + ").\n\n"
                        : "El servidor no está encendido. Toca «Actualizar»; si sigue igual, "
                            + "revisa que los avisos de la app estén permitidos.\n\n")
                            + list));
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
