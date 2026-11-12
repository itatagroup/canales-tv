# Política de seguridad

## Cómo reportar una vulnerabilidad

**No abras un issue público.** Usa «Report a vulnerability» en la pestaña *Security* de este
repositorio de GitHub (reporte privado). Cuenta qué encontraste, cómo reproducirlo y qué versión
(`version.json`) es.

## Qué está en alcance

- El código de la app (`app/`) y el proceso de compilación (`tools/build.mjs`, `tools/dependencias.json`).
- Que la app se conecte a algo distinto del canal que estás viendo, o que envíe datos del equipo o de
  uso.
- Que el APK pida permisos que el manifiesto no declara, o que no corresponda al código fuente.
- Que el build acepte una librería con un SHA-256 distinto del declarado.

## Qué no está en alcance

- Vulnerabilidades de Android, del firmware de cada fabricante o de Media3 (repórtalas a Google;
  aquí se actualiza la versión cuando salga el arreglo).
- El contenido de cada canal, incluidos sus comerciales.

Cualquier hallazgo que contradiga «sin telemetría, sin P2P y solo se conecta al canal que ves» es de
máxima prioridad.
