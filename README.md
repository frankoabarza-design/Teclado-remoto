# Teclado remoto

Permite escribir en una tablet Android desde un iPhone, por Wi-Fi. La tablet instala un teclado
sin teclas que abre un servidor en la red local. El iPhone abre una página en Safari, y lo que
se escribe ahí aparece en el campo de texto que esté activo en la tablet.

Hecho para una Samsung Galaxy Tab S6 Lite y un iPhone. No usa Bluetooth porque iOS no permite
que un iPhone se haga pasar por teclado Bluetooth.

## Instalar en la tablet

1. En la tablet, abre la pestaña **Releases** de este repositorio y descarga `teclado-remoto.apk`.
   Hay que haber iniciado sesión en GitHub desde ese navegador.
2. Ábrelo. Android pedirá permiso para instalar apps desde ese navegador: acéptalo.
   Si Play Protect avisa que la app no es conocida, elige «Instalar de todos modos».
3. Abre **Teclado remoto** y sigue los pasos de la pantalla: activar el teclado, elegirlo,
   y mirar la dirección que aparece.

## Usar

1. Tablet y iPhone en la misma red. Si el Wi-Fi no deja que los equipos se vean entre sí,
   conecta la tablet al punto de acceso del iPhone (Ajustes → Compartir internet).
2. En la tablet, toca un campo de texto. Abajo aparece «Teclado remoto activo» con la dirección.
3. En el iPhone, abre esa dirección en Safari (tiene la forma `http://192.168.x.x:8765/?k=123456`).
   Se puede agregar a la pantalla de inicio desde el botón de compartir.
4. Toca el recuadro y escribe con el teclado normal del iPhone.

La página también trae Enter, borrar, flechas, Tab, Esc, Inicio, Fin, Supr, y los botones
Seleccionar todo, Copiar, Cortar, Pegar y Deshacer. Estos cuatro últimos usan el portapapeles
de la tablet, no el del iPhone.

Para volver al teclado normal de la tablet, toca **Cambiar de teclado** en la franja de abajo.
Mientras otro teclado esté elegido, el servidor está apagado.

## Límites

- No mueve el cursor del mouse ni sirve como touchpad. Solo escribe en campos de texto.
- Los atajos con Ctrl (salvo Deshacer) no están disponibles.
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
- `RemoteKeyboardService.java`: el teclado; escribe en el campo activo con `InputConnection`.
- `MainActivity.java`: pantalla de ayuda.
- `app/src/main/assets/index.html`: la página que se abre en el iPhone.
