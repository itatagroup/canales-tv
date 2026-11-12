<#
  Arma lo que se sube a un release de GitHub de Canales (contrato común de actualización, docs/actualizaciones.md).
  Uso:  powershell -File scripts/publicar.ps1 -Version 0.4.0 [-Notas "texto corto en español"]

  1. Compila (node tools/build.mjs); la versión pedida tiene que ser la de version.json.
  2. Arma dist/publicar/v<versión>/ con canales-<versión>.apk, version.json (UTF-8 sin BOM), version.json.sig
     (firmado con keystore/actualizaciones.pem por tools/firmar-manifiesto.mjs), SHA256SUMS.txt y notas.md.
  3. Imprime (no ejecuta) el «gh release create ... --draft». Revisa el borrador en GitHub antes de publicarlo:
     recién al publicarlo pasa a ser el «latest» que consultan los equipos.
#>
param(
  [Parameter(Mandatory=$true)][string]$Version,
  [string]$Notas
)
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$repo = "itatagroup/canales-tv"

if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "La versión debe ser como 0.4.0" }
$info = Get-Content -Raw -Encoding UTF8 (Join-Path $raiz "version.json") | ConvertFrom-Json
if ($info.versionName -ne $Version) {
  throw "version.json dice $($info.versionName), no $Version. Súbelo (y el versionCode) antes de publicar."
}
$clave = Join-Path $raiz "keystore\actualizaciones.pem"
if (-not (Test-Path $clave)) { throw "Falta la clave de actualizaciones en keystore\actualizaciones.pem (ver docs/actualizaciones.md)." }
if (-not $Notas) { $Notas = "Canales $Version." }

Push-Location $raiz
try {
  # El release se ancla al commit compilado: quien compile desde el tag obtiene lo mismo que se publica (docs/verificar.md).
  if (git status --porcelain) { throw "Hay cambios sin commitear: el release tiene que salir de un commit limpio." }
  $commit = (git rev-parse HEAD).Trim()
  git fetch --quiet origin
  if (-not (git branch -r --contains $commit)) { throw "El commit $commit no está en GitHub: empújalo antes de publicar." }

  & node tools/build.mjs
  if ($LASTEXITCODE -ne 0) { throw "Falló la compilación." }

  $destino = Join-Path $raiz "dist\publicar\v$Version"
  if (Test-Path $destino) { Remove-Item -Recurse -Force $destino }
  New-Item -ItemType Directory -Force $destino | Out-Null

  $nombreApk = "canales-$Version.apk"
  $apk = Join-Path $destino $nombreApk
  Copy-Item (Join-Path $raiz "out\canales.apk") $apk
  $sha = (Get-FileHash -Algorithm SHA256 $apk).Hash.ToLowerInvariant()
  $tamano = (Get-Item $apk).Length

  $manifiesto = [ordered]@{
    formato = 1
    repo    = $repo
    apps    = [ordered]@{
      "canales-android" = [ordered]@{
        version     = $Version
        versionCode = [int64]$info.versionCode
        url         = "https://github.com/$repo/releases/download/v$Version/$nombreApk"
        tamano      = [int64]$tamano
        sha256      = $sha
        notas       = $Notas
      }
    }
  }
  $sinBom = New-Object System.Text.UTF8Encoding $false
  $rutaManifiesto = Join-Path $destino "version.json"
  [System.IO.File]::WriteAllText($rutaManifiesto, ($manifiesto | ConvertTo-Json -Depth 5), $sinBom)

  & node tools/firmar-manifiesto.mjs $rutaManifiesto $clave
  if ($LASTEXITCODE -ne 0) { throw "Falló la firma del manifiesto." }

  $sumas = foreach ($f in @($nombreApk, "version.json", "version.json.sig")) {
    "$((Get-FileHash -Algorithm SHA256 (Join-Path $destino $f)).Hash.ToLowerInvariant())  $f"
  }
  [System.IO.File]::WriteAllText((Join-Path $destino "SHA256SUMS.txt"), (($sumas -join "`n") + "`n"), $sinBom)

  $textoNotas = @"
# Canales $Version

$Notas

## Instalar o actualizar

- Desde la propia app: «Buscar actualización», al final de la lista de categorías. Android pide confirmar.
- Por ADB: ``powershell -File scripts/instalar.ps1 -Ip <IP de tu TV> -Apk $nombreApk``.

## Verificar

Compilado desde el commit ``$commit`` (el de este tag). SHA-256 de ``$nombreApk``: ``$sha`` ($tamano bytes). Todas las huellas en ``SHA256SUMS.txt``;
cómo comprobar que la APK es la oficial en ``docs/verificar.md``.
"@
  [System.IO.File]::WriteAllText((Join-Path $destino "notas.md"), $textoNotas, $sinBom)
}
finally {
  Pop-Location
}

Write-Host ""
Write-Host "Listo: $destino"
Get-ChildItem $destino | ForEach-Object { Write-Host ("  {0,-22} {1,10} bytes" -f $_.Name, $_.Length) }
Write-Host ""
Write-Host "Para crear el borrador del release (revísalo en GitHub y publícalo a mano):"
$archivos = (@($nombreApk, "version.json", "version.json.sig", "SHA256SUMS.txt") | ForEach-Object { "`"$(Join-Path $destino $_)`"" }) -join " "
Write-Host "gh release create v$Version --repo $repo --target $commit --draft --title `"Canales $Version`" --notes-file `"$(Join-Path $destino 'notas.md')`" $archivos"
