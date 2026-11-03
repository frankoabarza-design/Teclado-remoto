# Teclado remoto

Permite escribir en una tablet Android desde un iPhone, por Wi-Fi. La tablet abre un servidor en la
red local. El iPhone abre una página en Safari, y lo que se escribe ahí aparece en el campo de
texto que esté activo en la tablet.

Hecho para una Samsung Galaxy Tab S6 Lite y un iPhone. No usa Bluetooth porque iOS no permite
que un iPhone se haga pasar por teclado Bluetooth.

## Dos formas de escribir en la tablet

La app trae las dos; con una basta.

- **Forma A, por Accesibilidad.** Se sigue usando el teclado de siempre. Un servicio de
  accesibilidad busca el campo de texto con el foco y le cambia el contenido. Se activa en
  Ajustes → Accesibilidad → Aplicaciones instaladas → Teclado remoto. Solo toca ese campo: no
  lee ni guarda nada más de la pantalla. No puede deshacer, y en campos de varias líneas las
  flechas de arriba y abajo se mueven por saltos de línea, no por líneas partidas por el ancho.
- **Forma B, como teclado.** Se activa «Teclado remoto» en los ajustes de teclados y se elige como
  teclado actual. Es la más fiel, pero algunas tablets no dejan elegirlo.

Si la forma A pide permisos y el interruptor sale gris o dice «Configuración restringida», es
porque el APK se instaló desde fuera de una tienda. Se arregla en Ajustes → Aplicaciones →
Teclado remoto → los tres puntos de arriba a la derecha → «Permitir configuración restringida».

## Instalar en la tablet

1. En la tablet, abre la pestaña **Releases** de este repositorio y descarga `teclado-remoto.apk`.
2. Ábrelo. Android pedirá permiso para instalar apps desde ese navegador: acéptalo.
   Si Play Protect avisa que la app no es conocida, elige «Instalar de todos modos».
3. Abre **Teclado remoto**, activa una de las dos formas y mira la dirección que aparece.

## Usar

1. Tablet y iPhone en la misma red. Si el Wi-Fi no deja que los equipos se vean entre sí,
   conecta la tablet al punto de acceso del iPhone (Ajustes → Compartir internet).
2. En el iPhone, abre la dirección que muestra la app (tiene la forma
   `http://192.168.x.x:8765/?k=123456`). Se puede agregar a la pantalla de inicio.
3. En la tablet, toca el campo de texto donde quieras escribir.
4. En el iPhone, toca el recuadro y escribe con el teclado normal.

La página también trae Enter, borrar, flechas, Tab, Esc, Inicio, Fin, Supr, y los botones
Seleccionar todo, Copiar, Cortar, Pegar y Deshacer. Copiar, Cortar y Pegar usan el portapapeles
de la tablet, no el del iPhone.

El servidor sigue encendido mientras veas el aviso «Teclado remoto encendido» en la tablet. Se
apaga con el botón **Apagar** del aviso y vuelve a encenderse al abrir la app.

Si algo no conecta, la página del iPhone dice qué falta (activar el teclado o la accesibilidad,
tocar un campo de texto, código incorrecto). En la app de la tablet, el botón **Probar la página en
esta tablet** abre la misma página en el navegador de la tablet, sin escribir ninguna dirección:
sirve para saber si el servidor funciona aparte de la red. La app también indica si cada forma
está activada.

## Límites

- No mueve el cursor del mouse ni sirve como touchpad. Solo escribe en campos de texto.
- Los atajos con Ctrl (salvo Deshacer en la forma B) no están disponibles.
- La conexión no está cifrada (HTTP). Un código de 6 dígitos protege el acceso y se bloquea
  un minuto tras 10 intentos fallidos, pero no uses esto en un Wi-Fi público. En una red
  ajena, el punto de acceso del iPhone es más seguro.
- «Cambiar el código» en la app de la tablet genera uno nuevo y deja de servir el anterior.

## Cómo se compila

El APK lo arma GitHub Actions (`.github/workflows/build.yml`) con cada cambio en `main`, y lo
deja en **Releases** con el nombre `teclado-remoto.apk`. También se puede lanzar a mano desde
la pestaña **Actions** → **Compilar APK** → **Run workflow**.

El APK está firmado con la llave `app/debug.keystore`, que es pública a propósito. Sirve para
que cada versión nueva se instale encima de la anterior; no es apta para Play Store.

Código:

- `RemoteServer.java`: servidor HTTP mínimo, sin dependencias de Android.
- `ServerHolder.java` y `ServerService.java`: mantienen un solo servidor encendido, con aviso fijo.
- `RemoteKeyboardService.java`: forma B; escribe en el campo activo con `InputConnection`.
- `RemoteAccessibilityService.java`: forma A; edita el campo con foco.
- `MainActivity.java`: pantalla de ayuda y estado.
- `app/src/main/assets/index.html`: la página que se abre en el iPhone.
