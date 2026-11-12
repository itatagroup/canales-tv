<#
  Instala la app en una TV por ADB y la abre.
  Uso:  powershell -File scripts/instalar.ps1 -Ip <IP de tu TV> [-Apk out/canales.apk]
  Requisitos: depuración por red activa en la TV y la conexión autorizada en su pantalla.
#>
param(
  [Parameter(Mandatory=$true)][string]$Ip,
  [string]$Apk = (Join-Path $PSScriptRoot "..\out\canales.apk")
)
$ErrorActionPreference = "Stop"
$paquete = "cl.itatagroup.canales"

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $adb)) { throw "No encuentro adb. Instala platform-tools o ponlo en el PATH." }
if (-not (Test-Path $Apk)) { throw "No existe $Apk. Corre antes: node tools/build.mjs" }

$destino = "${Ip}:5555"
& $adb connect $destino | Out-Null
$estado = (& $adb devices | Select-String $Ip) -join ""
if ($estado -match "unauthorized") { throw "La TV no autorizó la conexión: acepta el diálogo de depuración en su pantalla." }
if ($estado -notmatch "device") { throw "No hay conexión con $destino." }

& $adb -s $destino install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "Falló la instalación (si dice firma distinta, desinstala primero $paquete)." }
& $adb -s $destino shell am start -n "$paquete/.Reproductor" | Out-Null
Write-Host "Instalada y abierta en $destino."
