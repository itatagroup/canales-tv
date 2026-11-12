// Compila Catalogo y Canal con sus pruebas y las corre en el PC (sin TV ni emulador).
// Uso: node tools/probar-catalogo.mjs [cl.m3u [spa.m3u eng.m3u]]   (listas opcionales, para contar)
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readdirSync, rmSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const isWin = process.platform === "win32";
const jdk = process.env.JAVA_HOME ?? (isWin ? "C:\\Program Files\\Android\\Android Studio\\jbr" : "");
const bin = (t) => (jdk && existsSync(join(jdk, "bin")) ? join(jdk, "bin", isWin ? `${t}.exe` : t) : t);
const sdk = process.env.ANDROID_HOME ?? join(process.env.LOCALAPPDATA ?? "", "Android", "Sdk");
const plataformas = readdirSync(join(sdk, "platforms")).filter((n) => existsSync(join(sdk, "platforms", n, "android.jar")));
plataformas.sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
const androidJar = join(sdk, "platforms", plataformas.at(-1), "android.jar");

const out = join(root, "out", "pruebas");
rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
const src = join(root, "app", "src", "main", "java", "cl", "itatagroup", "canales");
const sep = isWin ? ";" : ":";
execFileSync(bin("javac"), [
  "-encoding", "UTF-8", "-cp", androidJar, "-d", out,
  join(src, "Canal.java"), join(src, "Catalogo.java"), join(root, "tools", "pruebas", "ProbarCatalogo.java"),
], { stdio: "inherit" });
execFileSync(bin("java"), ["-cp", [out, androidJar].join(sep), "ProbarCatalogo", ...process.argv.slice(2)], { stdio: "inherit" });
