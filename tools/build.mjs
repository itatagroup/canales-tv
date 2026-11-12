// Compila, empaqueta y firma el APK sin Gradle: solo el SDK de Android, un JDK y las librerías de
// tools/dependencias.json (Media3 y lo que arrastra), verificadas por SHA-256.
// Uso: node tools/build.mjs   (genera out/canales.apk)
//
// Basado en tools/build.mjs de launcher-tv (commit b2c46f2). Diferencias: baja y verifica las
// librerías, y usa R8 en vez de D8 para quitar el código de Media3 y Guava que la app no usa.
// R8 corre SIN ofuscar (-dontobfuscate): los nombres de clases y métodos del APK son los del código
// fuente, para que cualquiera pueda comparar el APK con este repositorio.
//
// Variables opcionales:
//   ANDROID_HOME / ANDROID_SDK_ROOT   SDK de Android (por defecto, el de Android Studio en Windows)
//   JAVA_HOME                         JDK (por defecto, el que viene con Android Studio, o el del PATH)
//   CANALES_KEYSTORE                  ruta del .jks (por defecto keystore/canales.jks)
//   CANALES_KEYSTORE_PASS             clave del .jks (por defecto, el contenido de keystore/password.txt)
//   CANALES_KEY_ALIAS                 alias de la clave (por defecto "k")
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, copyFileSync, statSync, writeFileSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const isWin = process.platform === "win32";
const exe = (n) => (isWin ? `${n}.exe` : n);
const out = join(root, "out");
const app = join(root, "app", "src", "main");
const libs = join(root, "libs");

function fail(msg) {
  console.error(`ERROR: ${msg}`);
  process.exit(1);
}

function run(cmd, args, env = process.env) {
  try {
    execFileSync(cmd, args, { stdio: ["ignore", "inherit", "inherit"], env });
  } catch {
    fail(`falló: ${cmd} ${args.join(" ")}`);
  }
}

function highest(dir, filter = () => true) {
  if (!existsSync(dir)) return null;
  const names = readdirSync(dir).filter(filter);
  names.sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  return names.length ? join(dir, names[names.length - 1]) : null;
}

/** Todos los archivos con la extensión dada, en la carpeta y sus subcarpetas. */
function filesWithExt(dir, ext) {
  const found = [];
  for (const n of readdirSync(dir)) {
    const p = join(dir, n);
    if (statSync(p).isDirectory()) found.push(...filesWithExt(p, ext));
    else if (n.endsWith(ext)) found.push(p);
  }
  return found;
}

const sha256 = (file) => createHash("sha256").update(readFileSync(file)).digest("hex");

// --- SDK ---
const sdk =
  process.env.ANDROID_HOME ||
  process.env.ANDROID_SDK_ROOT ||
  (isWin ? join(process.env.LOCALAPPDATA ?? "", "Android", "Sdk") : "");
if (!sdk || !existsSync(sdk)) fail("no encuentro el SDK de Android: define ANDROID_HOME");
const buildTools = highest(join(sdk, "build-tools"), (n) => /^\d/.test(n));
if (!buildTools) fail("el SDK no tiene build-tools");
const platform = highest(join(sdk, "platforms"), (n) => existsSync(join(sdk, "platforms", n, "android.jar")));
if (!platform) fail("el SDK no tiene ninguna plataforma con android.jar");
const androidJar = join(platform, "android.jar");

// --- JDK ---
function jdkBin(tool) {
  const homes = [
    process.env.JAVA_HOME,
    isWin ? "C:\\Program Files\\Android\\Android Studio\\jbr" : null,
  ].filter(Boolean);
  for (const h of homes) {
    const p = join(h, "bin", exe(tool));
    if (existsSync(p)) return p;
  }
  return tool; // el del PATH
}
const javac = jdkBin("javac");
const java = jdkBin("java");
const jar = jdkBin("jar");

// --- versión ---
// Media3 1.11 exige minSdk 23 (Android 6).
const { versionCode, versionName, minSdk = 23, targetSdk = 35 } = JSON.parse(readFileSync(join(root, "version.json"), "utf8"));

// --- firma ---
const keystore = process.env.CANALES_KEYSTORE ?? join(root, "keystore", "canales.jks");
const passFile = join(root, "keystore", "password.txt");
const storePass =
  process.env.CANALES_KEYSTORE_PASS ?? (existsSync(passFile) ? readFileSync(passFile, "utf8").trim() : null);
const keyAlias = process.env.CANALES_KEY_ALIAS ?? "k";
if (!existsSync(keystore)) fail(`no existe el keystore ${keystore} (ver docs/build.md)`);
if (!storePass) fail("falta la clave del keystore (CANALES_KEYSTORE_PASS o keystore/password.txt)");

console.log(`SDK: ${sdk}\nbuild-tools: ${buildTools}\nplataforma: ${platform}\nversión: ${versionName} (${versionCode})`);

// --- librerías: bajar si faltan y verificar SHA-256 siempre ---
mkdirSync(libs, { recursive: true });
const { librerias } = JSON.parse(readFileSync(join(root, "tools", "dependencias.json"), "utf8"));
for (const lib of librerias) {
  if (!lib.url.startsWith("https://dl.google.com/android/maven2/") && !lib.url.startsWith("https://repo1.maven.org/maven2/"))
    fail(`${lib.nombre}: solo se aceptan Google Maven y Maven Central (${lib.url})`);
  const destino = join(libs, basename(new URL(lib.url).pathname));
  if (!existsSync(destino)) {
    console.log(`bajando ${lib.url}`);
    const res = await fetch(lib.url);
    if (!res.ok) fail(`no se pudo bajar ${lib.url} (${res.status})`);
    writeFileSync(destino, Buffer.from(await res.arrayBuffer()));
  }
  const real = sha256(destino);
  if (real !== lib.sha256) {
    rmSync(destino);
    fail(`${basename(destino)}: SHA-256 ${real} no coincide con dependencias.json; se borró`);
  }
  lib.archivo = destino;
}

rmSync(out, { recursive: true, force: true });
mkdirSync(join(out, "classes"), { recursive: true });
mkdirSync(join(out, "gen"), { recursive: true });
mkdirSync(join(out, "libs"), { recursive: true });
mkdirSync(join(out, "dex"), { recursive: true });

// De cada .aar se usan solo classes.jar y sus reglas de R8 (proguard.txt). Sus manifiestos no se
// mezclan con el de la app: los permisos que pide el APK son exactamente los de nuestro manifiesto.
const jars = [];
const reglas = [join(root, "app", "reglas-r8.pro")];
for (const lib of librerias) {
  if (lib.archivo.endsWith(".jar")) { jars.push(lib.archivo); continue; }
  const dir = join(out, "libs", lib.nombre);
  mkdirSync(dir, { recursive: true });
  // `jar xf` extrae en el directorio actual: se corre dentro de la carpeta de la librería.
  try {
    execFileSync(jar, ["xf", lib.archivo, "classes.jar", "proguard.txt"], { cwd: dir, stdio: ["ignore", "ignore", "inherit"] });
  } catch {
    fail(`no se pudo abrir ${lib.archivo}`);
  }
  if (existsSync(join(dir, "classes.jar"))) jars.push(join(dir, "classes.jar"));
  if (existsSync(join(dir, "proguard.txt"))) reglas.push(join(dir, "proguard.txt"));
}

// 1. recursos
run(join(buildTools, exe("aapt2")), ["compile", "--dir", join(app, "res"), "-o", join(out, "res.zip")]);
run(join(buildTools, exe("aapt2")), [
  "link", "-o", join(out, "base.apk"), "-I", androidJar,
  "--manifest", join(app, "AndroidManifest.xml"),
  "--java", join(out, "gen"),
  "--min-sdk-version", String(minSdk), "--target-sdk-version", String(targetSdk),
  "--version-code", String(versionCode), "--version-name", versionName,
  join(out, "res.zip"),
]);

// 2. Java -> classes
const sep = isWin ? ";" : ":";
const javaFiles = [...filesWithExt(join(app, "java"), ".java"), ...filesWithExt(join(out, "gen"), ".java")];
run(javac, [
  "--release", "8", "-Xlint:-options", "-encoding", "UTF-8",
  "-classpath", [androidJar, ...jars].join(sep), "-d", join(out, "classes"), ...javaFiles,
]);

// 3. classes + librerías -> dex con R8 (recorta lo no usado, sin ofuscar)
run(java, [
  "-cp", join(buildTools, "lib", "d8.jar"), "com.android.tools.r8.R8",
  "--release", "--lib", androidJar, "--min-api", String(minSdk), "--output", join(out, "dex"),
  ...reglas.flatMap((r) => ["--pg-conf", r]),
  ...filesWithExt(join(out, "classes"), ".class"), ...jars,
]);
const dexes = readdirSync(join(out, "dex")).filter((n) => /^classes\d*\.dex$/.test(n));
if (!dexes.length) fail("R8 no generó classes.dex");

// 4. dex dentro del APK, alinear y firmar
copyFileSync(join(out, "base.apk"), join(out, "unsigned.apk"));
run(jar, ["uf", join(out, "unsigned.apk"), "-C", join(out, "dex"), ...dexes]);
run(join(buildTools, exe("zipalign")), ["-f", "4", join(out, "unsigned.apk"), join(out, "aligned.apk")]);
run(java, [
  "-jar", join(buildTools, "lib", "apksigner.jar"), "sign",
  "--ks", keystore, "--ks-key-alias", keyAlias,
  // La clave viaja por variable de entorno, no por la línea de comandos (visible en la lista de procesos).
  "--ks-pass", "env:CANALES_SIGN_PASS", "--key-pass", "env:CANALES_SIGN_PASS",
  "--out", join(out, "canales.apk"), join(out, "aligned.apk"),
], { ...process.env, CANALES_SIGN_PASS: storePass });

console.log(`\nListo: ${join(out, "canales.apk")} (${statSync(join(out, "canales.apk")).size} bytes, ${dexes.join(" + ")})`);
