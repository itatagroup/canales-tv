# canales-tv

Reproductor de **TV abierta** para Android TV. La idea es una app que haga lo que dice que hace y
nada más: mostrar canales de señal abierta y gratuita, sin cuentas, sin publicidad propia y sin
telemetría. Pensada para el control remoto: video a pantalla completa, ↑ ↓ cambian de canal, los
números van directo, OK abre la lista y Atrás dos veces
sale de la app (la primera solo avisa).

- Paquete: `cl.itatagroup.canales` · Android 6+ (minSdk 23) · licencia GPL-3.0 (ver `LICENSE`).
- Rama de trabajo `dev`, estable `main`.

## Qué hace

- **Buscar canales** (solo cuando lo pides): baja las listas públicas de abajo, deja fuera los
  canales de pago y los enlaces que no vienen del propio canal, prueba cada uno y guarda los que
  responden, numerados y por categoría. Al terminar dice **cuántos dejó fuera y cuáles**, con el motivo.
- **TVN, Canal 13 y Chilevisión**: sus señales principales no tienen enlace abierto. Aparecen en la
  lista y abren su app oficial, o su ficha en la tienda. Las señales que TVN publica en tvn.cl
  (24 Play, NTV, TV Chile, TVN 3) se ven dentro de la app.
- **Mis listas**: listas M3U o Xtream que agregas desde el teléfono (ver abajo). **No pasan por ningún
  filtro**: lo que contienen es responsabilidad de quien las agrega, como en cualquier reproductor.
- **Guía**: «Ahora / Después» en los canales que aparecen en la guía pública de Chile.
- **Favoritos** (mantener OK), **detalles** con → (de qué servidor viene la señal y de qué lista salió)
  y **Qué hace esta app**, con las fuentes exactas para verificarlas.

## A qué se conecta, y cuándo

| Cuándo | A dónde |
|---|---|
| Ves un canal | El servidor de ese canal (lo muestra → en el control) |
| «Buscar canales» | `https://www.tvn.cl/en-vivo`, `https://iptv-org.github.io/iptv/countries/cl.m3u`, `.../languages/spa.m3u`, `.../languages/eng.m3u`, `https://epgshare01.online/epgshare01/epg_ripper_CL1.xml.gz`, y cada canal para probarlo |
| Agregas una lista | El enlace de esa lista |
| «Buscar actualización» | El último release de `itatagroup/canales-tv` en GitHub (`docs/actualizaciones.md`) |
| «Agregar listas» abierta | Escucha en la red local, puerto 8642 (ver abajo) |

User-Agent `Canales/<versión>`, sin cookies ni datos del equipo. **Sin telemetría, sin cuentas, sin
publicidad propia y sin P2P**: la app no sube nada ni comparte tu conexión. Guarda en el equipo los
canales, sus números, tus favoritos, tus listas, la guía y el último canal visto; nada más.

## Mis listas desde el teléfono

«Agregar listas» muestra una dirección (`http://<IP de la tele>:8642`) y un código de 4 dígitos que
cambia cada vez. Mientras esa pantalla está abierta, el teléfono, en el mismo Wi-Fi, puede agregar y
quitar listas. Cada cambio exige el código, y tras 5 códigos errados la página se cierra. La página
muestra solo el nombre y el servidor de cada lista, nunca el enlace (que en Xtream lleva usuario y
clave). Al cerrar esa pantalla, o al salir de la app, deja de escuchar.

## Permisos

Se comprueban en `app/src/main/AndroidManifest.xml`. Los manifiestos de las librerías **no** se
mezclan con el de la app: el APK pide exactamente estos tres.

- `INTERNET`: video, búsqueda, listas, actualización y la página de Mis listas.
- `ACCESS_NETWORK_STATE`: Media3 consulta si hay red y de qué tipo, para elegir la calidad inicial.
- `REQUEST_INSTALL_PACKAGES`: solo para «Buscar actualización»; Android siempre pide confirmar.

## Librerías

Usa **Media3/ExoPlayer** de Google (Apache-2.0) para reproducir HLS. Es la única excepción a la regla
de «sin dependencias» de estas apps, porque el reproductor nativo de Android no maneja bien los
canales con anuncios insertados ni fMP4. La lista exacta, con versión y SHA-256, está en
`tools/dependencias.json` y en `TERCEROS.md`. El build:

- las baja solo de Google Maven y Maven Central, y se niega a compilar si el SHA-256 no coincide;
- las recorta con R8 **sin ofuscar**: los nombres de clases y métodos del APK son los del código, para
  que cualquiera pueda comparar el APK con este repositorio;
- usa de cada librería solo su código y sus reglas de R8, nunca su manifiesto.

## Compilar, probar e instalar

```bash
node tools/build.mjs
```

```bash
node tools/probar-catalogo.mjs
```

```bash
powershell -File scripts/instalar.ps1 -Ip <IP de tu TV>
```

`probar-catalogo.mjs` corre en el PC las pruebas de las reglas de la búsqueda y de la guía; acepta
listas y guías bajadas para contar qué entra. Requisitos y clave de firma: `docs/build.md`.
