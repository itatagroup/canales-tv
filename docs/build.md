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

1. La app aparece en el inicio como «Canales» y abre a pantalla completa.
2. El primer canal muestra imagen y suena; el rótulo indica cuántos milisegundos tardó.
3. ↑ ↓ cambian de canal; OK vuelve a mostrar el rótulo.
4. `adb logcat -s Canales` muestra el tiempo de cada canal y los errores.
5. `adb shell dumpsys package cl.itatagroup.canales | grep permission` muestra solo `INTERNET` y
   `ACCESS_NETWORK_STATE`.
