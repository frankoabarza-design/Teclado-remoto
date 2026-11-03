package com.frankoabarza.tecladoremoto;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Teclado de Android que no tiene teclas: escribe en el campo de texto activo lo que le
 * llega desde la página del iPhone. El servidor que recibe esas órdenes está en ServerHolder.
 */
public class RemoteKeyboardService extends InputMethodService {

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean fieldActive;
    private TextView info;

    @Override
    public void onCreate() {
        super.onCreate();
        ServerHolder.setKeyboard(this);
        ServerHolder.ensureStarted(this); // por si el servicio con aviso fijo no está activo
    }

    @Override
    public void onDestroy() {
        ServerHolder.clearKeyboard(this);
        super.onDestroy();
    }

    // ---- Vista del teclado en la tablet ----

    @Override
    public boolean onEvaluateInputViewShown() {
        return true; // también con teclado físico conectado
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF202124);
        int pad = dp(16);
        root.setPadding(pad, dp(10), pad, dp(10));

        TextView title = new TextView(this);
        title.setText("Teclado remoto activo");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        root.addView(title);

        info = new TextView(this);
        info.setTextColor(0xFFBDC1C6);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        info.setPadding(0, dp(4), 0, dp(8));
        root.addView(info);

        Button change = new Button(this);
        change.setText("Cambiar de teclado");
        change.setAllCaps(false);
        change.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });
        root.addView(change);

        refreshInfo();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        refreshInfo(); // la red pudo cambiar desde la última vez
    }

    private void refreshInfo() {
        if (info == null) {
            return;
        }
        List<String> ips = Config.addresses();
        if (Config.port(this) == 0) {
            info.setText("No se pudo abrir el servidor. Cierra y vuelve a elegir este teclado.");
        } else if (ips.isEmpty()) {
            info.setText("No encuentro la red. Conecta la tablet al Wi-Fi o al punto de acceso del iPhone.");
        } else {
            StringBuilder sb = new StringBuilder("En el iPhone, abre en Safari:");
            for (String ip : ips) {
                sb.append("\n").append(Config.url(this, ip));
            }
            info.setText(sb.toString());
        }
    }

    // ---- Estado del campo de texto ----

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        fieldActive = attribute != null && attribute.inputType != InputType.TYPE_NULL;
    }

    @Override
    public void onFinishInput() {
        fieldActive = false;
        super.onFinishInput();
    }

    // ---- Llamadas desde ServerHolder (hilos del servidor) ----

    boolean isFieldActive() {
        return fieldActive;
    }

    String handleCommand(final Map<String, String> params) {
        FutureTask<String> task = new FutureTask<>(() -> apply(params));
        main.post(task);
        try {
            return task.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "error";
        }
    }

    // ---- Escritura en el campo (hilo principal) ----

    private String apply(Map<String, String> p) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || !fieldActive) {
            return "no_field";
        }
        String key = p.get("key");
        if (key != null) {
            return pressKey(key) ? "ok" : "tecla_desconocida";
        }
        String action = p.get("action");
        if (action != null) {
            return doAction(ic, action) ? "ok" : "accion_desconocida";
        }

        int back = 0;
        try {
            back = Integer.parseInt(p.containsKey("back") ? p.get("back") : "0");
        } catch (NumberFormatException ignored) {
            // se trata como 0
        }
        back = Math.max(0, Math.min(back, 500));
        String text = p.get("text");

        ic.beginBatchEdit();
        for (int i = 0; i < back; i++) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
        if (text != null && !text.isEmpty()) {
            ic.commitText(text, 1);
        }
        ic.endBatchEdit();
        return "ok";
    }

    private boolean pressKey(String key) {
        switch (key) {
            case "ENTER":
                pressEnter();
                return true;
            case "BACKSPACE":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                return true;
            case "DELETE":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_FORWARD_DEL);
                return true;
            case "TAB":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB);
                return true;
            case "ESC":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ESCAPE);
                return true;
            case "LEFT":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
                return true;
            case "RIGHT":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
                return true;
            case "UP":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP);
                return true;
            case "DOWN":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN);
                return true;
            case "HOME":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_HOME);
                return true;
            case "END":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_END);
                return true;
            default:
                return false;
        }
    }

    /** Enter: usa la acción del campo (Buscar, Ir, Enviar…) si la tiene; si no, un salto de línea. */
    private void pressEnter() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noAction = (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            boolean multiLine = (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
            if (!noAction && !multiLine
                    && action != EditorInfo.IME_ACTION_NONE
                    && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && ic.performEditorAction(action)) {
                    return;
                }
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    }

    private boolean doAction(InputConnection ic, String action) {
        switch (action) {
            case "selectAll":
                ic.performContextMenuAction(android.R.id.selectAll);
                return true;
            case "copy":
                ic.performContextMenuAction(android.R.id.copy);
                return true;
            case "cut":
                ic.performContextMenuAction(android.R.id.cut);
                return true;
            case "paste":
                ic.performContextMenuAction(android.R.id.paste);
                return true;
            case "undo": {
                long now = SystemClock.uptimeMillis();
                int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
                ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, meta));
                ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, meta));
                return true;
            }
            default:
                return false;
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
