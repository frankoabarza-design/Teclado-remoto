package com.frankoabarza.tecladoremoto;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Segunda forma de escribir en la tablet, sin cambiar de teclado: busca el campo de texto que
 * tiene el foco y le cambia el contenido. Solo toca ese campo; no lee ni guarda nada más.
 */
public class RemoteAccessibilityService extends AccessibilityService implements Injector {

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            ServerHolder.setAccessibility(this);
            ServerHolder.ensureStarted(this);
        } catch (RuntimeException ignored) {
            // el servicio debe quedar activo aunque el servidor falle
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        ServerHolder.clearAccessibility(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        ServerHolder.clearAccessibility(this);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // no se usa: el campo se busca solo cuando llega una orden
    }

    @Override
    public void onInterrupt() {
    }

    // ---- Injector (se llama desde hilos del servidor) ----

    @Override
    public boolean isFieldActive() {
        return onMain(() -> focusedField() != null, Boolean.FALSE);
    }

    @Override
    public String handleCommand(final Map<String, String> params) {
        return onMain(() -> apply(params), "error");
    }

    private <T> T onMain(Callable<T> job, T fallback) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                return job.call();
            } catch (Exception e) {
                return fallback;
            }
        }
        FutureTask<T> task = new FutureTask<>(job);
        main.post(task);
        try {
            return task.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return fallback;
        }
    }

    // ---- Órdenes (hilo principal) ----

    private String apply(Map<String, String> p) {
        String key = p.get("key");
        if ("ESC".equals(key)) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            return "ok";
        }
        AccessibilityNodeInfo field = focusedField();
        if (field == null) {
            return "no_field";
        }
        if (key != null) {
            return pressKey(field, key) ? "ok" : "tecla_desconocida";
        }
        String action = p.get("action");
        if (action != null) {
            return doAction(field, action) ? "ok" : "accion_desconocida";
        }

        int back = 0;
        try {
            back = Integer.parseInt(p.containsKey("back") ? p.get("back") : "0");
        } catch (NumberFormatException ignored) {
            // se trata como 0
        }
        back = Math.max(0, Math.min(back, 500));
        String text = p.get("text");
        edit(field, back, 0, text == null ? "" : text);
        return "ok";
    }

    /** El campo de texto con el foco, o null si no hay ninguno. */
    private AccessibilityNodeInfo focusedField() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return null;
        }
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null) {
            f = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        }
        return f != null && f.isEditable() ? f : null;
    }

    private static String textOf(AccessibilityNodeInfo n) {
        if (n.isShowingHintText()) {
            return ""; // el texto de ayuda no es contenido del campo
        }
        CharSequence c = n.getText();
        return c == null ? "" : c.toString();
    }

    /** {inicio, fin} de la selección; si no se sabe, el cursor va al final. */
    private static int[] selectionOf(AccessibilityNodeInfo n, String t) {
        int s = n.getTextSelectionStart();
        int e = n.getTextSelectionEnd();
        if (s < 0 || e < 0 || s > t.length() || e > t.length()) {
            return new int[]{t.length(), t.length()};
        }
        return new int[]{Math.min(s, e), Math.max(s, e)};
    }

    /** Borra hacia atrás y hacia adelante desde el cursor y luego escribe insert. */
    private void edit(AccessibilityNodeInfo n, int back, int forward, String insert) {
        String t = textOf(n);
        int[] sel = selectionOf(n, t);
        int lo = sel[0];
        int hi = sel[1];
        StringBuilder sb = new StringBuilder(t);

        for (int i = 0; i < back; i++) {
            if (hi > lo) {
                sb.delete(lo, hi);
                hi = lo;
            } else if (lo > 0) {
                int len = Character.charCount(Character.codePointBefore(sb, lo));
                sb.delete(lo - len, lo);
                lo -= len;
                hi = lo;
            }
        }
        for (int i = 0; i < forward; i++) {
            if (hi > lo) {
                sb.delete(lo, hi);
                hi = lo;
            } else if (lo < sb.length()) {
                int len = Character.charCount(Character.codePointAt(sb, lo));
                sb.delete(lo, lo + len);
            }
        }
        if (!insert.isEmpty()) {
            sb.replace(lo, hi, insert);
            lo += insert.length();
        }

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, sb.toString());
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        setSelection(lo, lo);
    }

    private void setSelection(int start, int end) {
        AccessibilityNodeInfo n = focusedField();
        if (n == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start);
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end);
        n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args);
    }

    private boolean pressKey(AccessibilityNodeInfo n, String key) {
        switch (key) {
            case "ENTER":
                if (n.isMultiLine()) {
                    edit(n, 0, 0, "\n");
                } else if (Build.VERSION.SDK_INT >= 30) {
                    n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                }
                return true;
            case "BACKSPACE":
                edit(n, 1, 0, "");
                return true;
            case "DELETE":
                edit(n, 0, 1, "");
                return true;
            case "LEFT":
            case "RIGHT":
            case "UP":
            case "DOWN":
            case "HOME":
            case "END":
                moveCaret(n, key);
                return true;
            case "TAB": {
                AccessibilityNodeInfo next = n.focusSearch(View.FOCUS_FORWARD);
                if (next != null) {
                    next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                }
                return true;
            }
            default:
                return false;
        }
    }

    private void moveCaret(AccessibilityNodeInfo n, String key) {
        String t = textOf(n);
        int[] sel = selectionOf(n, t);
        int lo = sel[0];
        int hi = sel[1];
        int pos;
        switch (key) {
            case "LEFT":
                pos = lo != hi ? lo
                        : (lo > 0 ? lo - Character.charCount(Character.codePointBefore(t, lo)) : 0);
                break;
            case "RIGHT":
                pos = lo != hi ? hi
                        : (hi < t.length() ? hi + Character.charCount(Character.codePointAt(t, hi)) : t.length());
                break;
            case "HOME":
                pos = t.lastIndexOf('\n', lo - 1) + 1;
                break;
            case "END": {
                int e = t.indexOf('\n', hi);
                pos = e < 0 ? t.length() : e;
                break;
            }
            case "UP": {
                int lineStart = t.lastIndexOf('\n', lo - 1) + 1;
                if (lineStart == 0) {
                    pos = 0;
                    break;
                }
                int col = lo - lineStart;
                int prevEnd = lineStart - 1;
                int prevStart = t.lastIndexOf('\n', prevEnd - 1) + 1;
                pos = prevStart + Math.min(col, prevEnd - prevStart);
                break;
            }
            case "DOWN": {
                int lineStart = t.lastIndexOf('\n', lo - 1) + 1;
                int col = lo - lineStart;
                int lineEnd = t.indexOf('\n', hi);
                if (lineEnd < 0) {
                    pos = t.length();
                    break;
                }
                int nextStart = lineEnd + 1;
                int nextEnd = t.indexOf('\n', nextStart);
                if (nextEnd < 0) {
                    nextEnd = t.length();
                }
                pos = nextStart + Math.min(col, nextEnd - nextStart);
                break;
            }
            default:
                return;
        }
        setSelection(pos, pos);
    }

    private boolean doAction(AccessibilityNodeInfo n, String action) {
        switch (action) {
            case "selectAll":
                setSelection(0, textOf(n).length());
                return true;
            case "copy":
                n.performAction(AccessibilityNodeInfo.ACTION_COPY);
                return true;
            case "cut":
                n.performAction(AccessibilityNodeInfo.ACTION_CUT);
                return true;
            case "paste":
                n.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                return true;
            case "undo":
                return true; // este modo no puede deshacer
            default:
                return false;
        }
    }
}
