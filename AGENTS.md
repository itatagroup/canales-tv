# AGENTS.md — canales-tv

Guía para agentes de IA y colaboradores.

## Idioma

Todo en **español de Chile**: respuestas, commits, documentación y textos de la pantalla. El código
sigue la convención Java/Android habitual.

## Qué es

Reproductor de TV abierta para Android TV (`cl.itatagroup.canales`). Detalle en `README.md`. Licencia
GPL-3.0.

## Ramas

`dev` es la rama de trabajo y `main` la estable. Repo público `itatagroup/canales-tv` (12/11/2026),
con las mismas reglas que launcher-tv: `main` y `dev` sin borrar ni forzar y con PR aprobado por el
mantenedor (él puede empujar directo), etiquetas `v*` solo del mantenedor, Actions apagado, escaneo
de secretos y reporte privado de vulnerabilidades activos. Los cambios entran por pull request contra `dev`.

## Comandos

- Compilar: `node tools/build.mjs` → `out/canales.apk` (necesita la clave en `keystore/`, ver `docs/build.md`).
- Instalar: `powershell -File scripts/instalar.ps1 -Ip <IP de tu TV>`.
- Pruebas de las reglas (búsqueda, filtro, guía) en el PC: `node tools/probar-catalogo.mjs`.
- Antes de dar algo por terminado: esas pruebas, compilar, instalar en una TV o en el emulador de
  Android TV y seguir la «Verificación manual» de `docs/build.md`.

## Reglas

1. **Hace lo que dice y nada más.** Sin telemetría, sin analítica, sin cuentas, sin publicidad propia,
   sin P2P y sin SDK de terceros que se conecten por su cuenta. Las conexiones son las de la tabla
   del `README.md` y nada más; una nueva se agrega ahí y en «Qué hace esta app».
2. **Solo señal abierta y gratuita** en lo que la app busca y propone. Un canal de pago no entra
   aunque aparezca en una lista pública. Ante la duda, no entra. **Lo que se deja fuera se muestra**
   con su motivo, y las fuentes están a la vista. Las listas que agregue el usuario no pasan por
   ningún filtro: son responsabilidad suya y se muestran aparte.
3. **Única dependencia permitida: Media3** y lo que arrastra (`tools/dependencias.json`), por decisión
   del 12/11/2026. Cualquier otra se discute antes. Cambiar una versión = actualizar URL, SHA-256 y
   `TERCEROS.md`, y medir con `adb shell dumpsys meminfo cl.itatagroup.canales`.
4. **R8 sin ofuscar** (`-dontobfuscate` en `app/reglas-r8.pro`). No quitarlo.
5. **Los manifiestos de las librerías no se mezclan**: un permiso nuevo se agrega a mano en el
   manifiesto, con un comentario que diga para qué, y se anota en el `README.md`.
6. **Las claves no se versionan** (`keystore/` en `.gitignore`).
7. Sube `versionCode` en `version.json` en cada cambio que se vaya a publicar.
8. Aspecto en recursos XML (`res/`), no en código.
9. `cl.itatagroup.actualizacion` es copia de `pantalla-tv/android-comun` (commit `d2f88ac`): un arreglo
   en una copia va también a las otras (launcher-tv y pantalla-tv).

## Qué falta

- Primer release (la búsqueda de actualización no tiene a qué apuntar hasta entonces; ver
  `docs/actualizaciones.md`).
- Pruebas automáticas de la interfaz (hoy solo las reglas en el PC y la verificación manual).
- Guía para «Mis listas» (`url-tvg` de cada lista) y logos de los canales (exigiría conexiones nuevas).
