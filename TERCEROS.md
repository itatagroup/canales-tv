# Código de terceros

El APK incluye estas librerías, recortadas con R8 (sin ofuscar). Todas tienen licencia **Apache-2.0**,
compatible con la GPL-3.0 de este repositorio. La versión exacta y el SHA-256 de cada archivo están
en `tools/dependencias.json`, y el build se niega a usar un archivo que no coincida.

| Librería | Versión | Para qué | Origen |
|---|---|---|---|
| androidx.media3: common, container, datasource, database, decoder, extractor, exoplayer, exoplayer-hls | 1.11.1 | Reproducir HLS (el formato de los canales) | Google Maven |
| com.google.guava: guava, failureaccess | 33.3.1-android, 1.0.2 | La usa Media3 | Maven Central |
| androidx.exifinterface | 1.3.6 | La declara Media3 | Google Maven |
| androidx.concurrent: concurrent-futures | 1.2.0 | La declara Media3 HLS | Google Maven |

Texto de la licencia Apache-2.0: https://www.apache.org/licenses/LICENSE-2.0

Media3 es de The Android Open Source Project; Guava, de The Guava Authors.
