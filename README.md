# canales-tv

Reproductor de **TV abierta** para Android TV. La idea es una app que haga lo que dice que hace y
nada más: mostrar canales de señal abierta y gratuita, sin cuentas, sin publicidad propia y sin
telemetría.

- Paquete: `cl.itatagroup.canales` · Android 6+ (minSdk 23) · licencia GPL-3.0 (ver `LICENSE`).
- Rama de trabajo `dev`, estable `main`.
- Estado: **0.1.0 (H0)**. Es un reproductor a pantalla completa con tres canales de prueba (↑ ↓ para
  cambiar). Siguen la búsqueda de canales, la grilla, los canales grandes, las listas propias y la
  búsqueda de actualización, con el mismo sistema firmado de `launcher-tv`.

## Qué hace y qué no

- **Solo se conecta para traer el video del canal que estás viendo**, desde el servidor de ese
  canal. User-Agent `Canales/<versión>`, sin cookies ni datos del equipo.
- **Sin telemetría, sin cuentas, sin publicidad propia y sin P2P**: la app no sube nada ni comparte
  tu conexión con nadie. Lo que un canal emite dentro de su propia señal (sus comerciales) se ve tal
  cual.
- **Sin canales de pago.** Solo entran señales abiertas y gratuitas. Las listas públicas mezclan
  canales de pago subidos sin licencia, y la app los filtra (desde el H1).
- Permisos (se comprueban en `app/src/main/AndroidManifest.xml`):
  - `INTERNET`: para recibir el video.
  - `ACCESS_NETWORK_STATE`: Media3 consulta si hay red y de qué tipo, para elegir la calidad inicial.
    No sale del equipo.

  Los manifiestos de las librerías **no** se mezclan con el de la app: el APK pide exactamente esos
  dos permisos.

## Librerías

Usa **Media3/ExoPlayer** de Google (Apache-2.0) para reproducir HLS. Es la única excepción a la regla
de «sin dependencias» de estas apps, porque el reproductor nativo de Android no maneja bien los
canales con anuncios insertados ni fMP4. La lista exacta, con versión y SHA-256, está en
`tools/dependencias.json` y en `TERCEROS.md`. El build:

- las baja solo de Google Maven y Maven Central, y se niega a compilar si el SHA-256 no coincide;
- las recorta con R8 **sin ofuscar**: los nombres de clases y métodos del APK son los del código, para
  que cualquiera pueda comparar el APK con este repositorio;
- usa de cada librería solo su código y sus reglas de R8, nunca su manifiesto.

## Compilar e instalar

```bash
node tools/build.mjs
```

```bash
powershell -File scripts/instalar.ps1 -Ip <IP de tu TV>
```

Requisitos y clave de firma: `docs/build.md`.
