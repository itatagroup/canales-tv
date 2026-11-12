// Firma un version.json con la clave privada de actualizaciones (contrato común, docs/actualizaciones.md).
// Uso: node tools/firmar-manifiesto.mjs <version.json> <clave privada PKCS#8 PEM>
// Escribe <version.json>.sig: la firma ECDSA P-256 + SHA-256 en DER, en base64 y en una sola línea.
//
// Sin dependencias. Nunca imprime la clave: solo la lee, firma, y comprueba que la pública que se deriva de ella
// es la que traen las apps (si no, una firma "válida" no serviría de nada en los equipos).
import { createPrivateKey, createPublicKey, sign, verify } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";

const CLAVE_PUBLICA_APPS =
  "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAENdrCrDU1A0B87/zG/2dD80YPGfx8+gK2CclanFT9+8hPOlWFc9Q4zYXJqMEdQEd6bdsQ2eMgjqwhea0vcj95zA==";

const [manifiesto, rutaClave] = process.argv.slice(2);
if (!manifiesto || !rutaClave) {
  console.error("Uso: node tools/firmar-manifiesto.mjs <version.json> <clave privada .pem>");
  process.exit(1);
}

let privada;
try {
  privada = createPrivateKey(readFileSync(rutaClave));
} catch {
  // El mensaje del error podría citar parte del archivo: no se muestra.
  console.error(`ERROR: no se pudo leer la clave privada de ${rutaClave}`);
  process.exit(1);
}
if (privada.asymmetricKeyType !== "ec" || privada.asymmetricKeyDetails?.namedCurve !== "prime256v1") {
  console.error("ERROR: la clave no es ECDSA P-256");
  process.exit(1);
}
const publica = createPublicKey(privada);
if (publica.export({ type: "spki", format: "der" }).toString("base64") !== CLAVE_PUBLICA_APPS) {
  console.error("ERROR: esta clave no corresponde a la clave pública que traen las apps");
  process.exit(1);
}

const datos = readFileSync(manifiesto);
if (datos[0] === 0xef && datos[1] === 0xbb && datos[2] === 0xbf) {
  console.error("ERROR: el manifiesto tiene BOM; el contrato pide UTF-8 sin BOM");
  process.exit(1);
}
JSON.parse(datos.toString("utf8")); // que al menos sea JSON válido

const firma = sign("sha256", datos, privada); // DER por defecto
if (!verify("sha256", datos, publica, firma)) {
  console.error("ERROR: la firma recién hecha no se pudo verificar");
  process.exit(1);
}
writeFileSync(`${manifiesto}.sig`, firma.toString("base64"));
console.log(`Firmado: ${manifiesto}.sig`);
