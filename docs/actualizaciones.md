# Actualizaciones de Canales

Canales busca una versión nueva de sí misma **solo cuando lo pides**: «Buscar actualización», al final
de la columna de categorías. Nunca consulta sola: ni al abrir, ni en segundo plano, ni con un
temporizador. Es la única razón por la que pide `REQUEST_INSTALL_PACKAGES`.

Es el mismo contrato que `launcher-tv` y `pantalla-tv`, con la misma clave pública: el código de
`app/src/main/java/cl/itatagroup/actualizacion/` es copia de `pantalla-tv/android-comun` (commit
`d2f88ac`). Un arreglo en una copia va también a las otras.

## Qué consulta y a dónde

Un GET sin cookies ni identificadores, con User-Agent `Canales/<versión>`, a:

- `https://github.com/itatagroup/canales-tv/releases/latest/download/version.json`
- `https://github.com/itatagroup/canales-tv/releases/latest/download/version.json.sig`

## Formato de `version.json`

```json
{
  "formato": 1,
  "repo": "itatagroup/canales-tv",
  "apps": {
    "canales-android": {
      "version": "0.3.0",
      "versionCode": 3,
      "url": "https://github.com/itatagroup/canales-tv/releases/download/v0.3.0/canales-0.3.0.apk",
      "tamano": 672692,
      "sha256": "hex en minúsculas",
      "notas": "texto corto en español"
    }
  }
}
```

`version.json.sig` es la firma ECDSA P-256 + SHA-256 (DER, base64 en una línea) de los bytes exactos de
`version.json`.

## Qué comprueba la app, en orden

1. Solo HTTPS, también en cada redirección; manifiesto y firma de 64 KB como máximo; 10 s de espera.
2. La firma, con la clave pública que viene dentro de la app (`Actualizador.CLAVE_PUBLICA`).
3. `formato == 1`, `repo == "itatagroup/canales-tv"` y la entrada `canales-android`.
4. `versionCode` mayor que el instalado (si no: «Canales está al día»).
5. `url` dentro de `https://github.com/itatagroup/canales-tv/releases/download/`.
6. Tamaño exacto y SHA-256 del APK descargado; si no coinciden, se borra.
7. Instalación con `PackageInstaller`: **Android siempre pide confirmar**. La primera vez hay que permitir
   «Instalar apps desconocidas» para Canales; la app lo explica y abre ese ajuste.

Si algo falla no ofrece nada y dice «No se pudo comprobar»; el motivo queda en `adb logcat -s Actualizador`.

## Cómo publicar una versión

1. Sube `versionCode` y `versionName` en `version.json`, commitea y empuja.
2. Pon la clave privada de actualizaciones en `keystore/actualizaciones.pem` (la misma de launcher-tv y
   pantalla-tv; nunca va a git ni se imprime). `tools/firmar-manifiesto.mjs` se niega a firmar si su
   pública no es la de la app.
3. `node tools/build.mjs` y `powershell -File scripts/publicar.ps1 -Version <x.y.z>`: arma
   `dist/publicar/v<x.y.z>/` e imprime el `gh release create ... --draft` para correrlo a mano.
4. Revisa el borrador en GitHub y publícalo.
