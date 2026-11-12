# Compilar e instalar

## Requisitos

- SDK de Android con `build-tools` y una plataforma con `android.jar` (el de Android Studio sirve).
- JDK 17 o más nuevo (el `jbr` de Android Studio sirve).
- Node 20 o más nuevo.
- Internet la primera vez: el build baja las librerías de `tools/dependencias.json` a `libs/`
  (ignorada por git) y comprueba su SHA-256 en cada compilación.

## Clave de firma

El build firma con `keystore/canales.jks` (alias `k`) y la clave guardada en `keystore/password.txt`.
La carpeta está en `.gitignore`. Para generar una propia:

```bash
keytool -genkeypair -keystore keystore/canales.jks -alias k -keyalg RSA -keysize 3072 -validity 36500 -dname "CN=Canales"
```

Una app firmada con otra clave no se puede instalar encima: hay que desinstalar primero.

## Compilar

```bash
node tools/build.mjs
```

Genera `out/canales.apk`. Pasos: bajar y verificar librerías → `aapt2` (recursos) → `javac` → R8 sin
ofuscar (código de la app + librerías, con las reglas de `app/reglas-r8.pro` y las de cada librería)
→ `zipalign` → `apksigner`.

## Instalar

Con la depuración por red activa en la TV:

```bash
powershell -File scripts/instalar.ps1 -Ip <IP de tu TV>
```

## Verificación manual

En una TV o en el emulador de Android TV (`emulator -avd <tv>`):

1. Primera vez: aparece la bienvenida; «Buscar canales» muestra el avance y termina en menos de un
   minuto con «N canales encontrados» y «Ver cuáles» (lo que quedó fuera y por qué).
2. Abre a pantalla completa en el último canal visto; ↑ ↓ cambian de canal, los números van directo,
   Atrás vuelve al canal anterior, OK o ← abren la lista.
3. → muestra los detalles: «Ahora / Después» si hay guía, el servidor de la señal y la lista de origen.
4. Mantener OK sobre un canal lo marca como favorito y aparece en «★ Favoritos».
5. «Agregar listas»: desde otro equipo de la red (o con `adb forward tcp:8642 tcp:8642` en el
   emulador) se agrega una lista con el código de la pantalla; sus canales aparecen en «Mis listas».
6. «Buscar actualización» responde «al día», ofrece la versión nueva, o dice «No se pudo comprobar».
7. `adb logcat -s Canales` muestra la búsqueda, el tiempo de cada canal y los errores.
8. `adb shell dumpsys package cl.itatagroup.canales | grep permission` muestra solo `INTERNET`,
   `ACCESS_NETWORK_STATE` y `REQUEST_INSTALL_PACKAGES`.
