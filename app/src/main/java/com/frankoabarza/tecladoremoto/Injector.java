package com.frankoabarza.tecladoremoto;

import java.util.Map;

/** Algo capaz de escribir en el campo de texto activo de la tablet: el teclado o el servicio de accesibilidad. */
interface Injector {

    /** true si hay un campo de texto listo para recibir. */
    boolean isFieldActive();

    /** Ejecuta la orden recibida. Devuelve "ok", "no_field" u otro texto de error. */
    String handleCommand(Map<String, String> params);
}
