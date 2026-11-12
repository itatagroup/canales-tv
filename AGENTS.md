# AGENTS.md — canales-tv

Guía para agentes de IA y colaboradores.

## Idioma

Todo en **español de Chile**: respuestas, commits, documentación y textos de la pantalla. El código
sigue la convención Java/Android habitual.

## Qué es

Reproductor de TV abierta para Android TV (`cl.itatagroup.canales`). Detalle en `README.md`. Licencia
GPL-3.0.

## Ramas

`dev` es la rama de trabajo y `main` la estable. Los cambios entran por pull request contra `dev`.

## Comandos

- Compilar: `node tools/build.mjs` → `out/canales.apk` (necesita la clave en `keystore/`, ver `docs/build.md`).
- Instalar: `powershell -File scripts/instalar.ps1 -Ip <IP de tu TV>`.
- No hay pruebas automáticas todavía. Antes de dar algo por terminado: compilar, instalar en una TV y
  seguir la «Verificación manual» de `docs/build.md`.

## Reglas

1. **Hace lo que dice y nada más.** Sin telemetría, sin analítica, sin cuentas, sin publicidad propia,
   sin P2P y sin SDK de terceros que se conecten por su cuenta. La única conexión es al canal que se
   está viendo y, cuando exista, la búsqueda **manual** de actualización (mismo contrato firmado que
   `launcher-tv`).
2. **Solo señal abierta y gratuita** en lo que la app busca y propone. Un canal de pago no entra
   aunque aparezca en una lista pública. Ante la duda, no entra. Las listas que agregue el usuario son
   responsabilidad suya y se muestran aparte.
3. **Única dependencia permitida: Media3** y lo que arrastra (`tools/dependencias.json`), por decisión
   del 12/11/2026. Cualquier otra se discute antes. Cambiar una versión = actualizar URL, SHA-256 y
   `TERCEROS.md`, y medir con `adb shell dumpsys meminfo cl.itatagroup.canales`.
4. **R8 sin ofuscar** (`-dontobfuscate` en `app/reglas-r8.pro`). No quitarlo.
5. **Los manifiestos de las librerías no se mezclan**: un permiso nuevo se agrega a mano en el
   manifiesto, con un comentario que diga para qué, y se anota en el `README.md`.
6. **Las claves no se versionan** (`keystore/` en `.gitignore`).
7. Sube `versionCode` en `version.json` en cada cambio que se vaya a publicar.
8. Aspecto en recursos XML (`res/`), no en código.

## Qué falta

Todo lo que viene después del H0: buscar canales, grilla, canales grandes, listas propias y la
búsqueda de actualización. Tampoco hay pruebas automáticas.
