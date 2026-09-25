# Genera la imagen de CredencialesSM para Windows.
# Ejecutar en Windows con Amazon Corretto 21 (incluye jpackage) y Maven.
# Desde Linux no se produce el .exe.
#
#   powershell -ExecutionPolicy Bypass -File packaging\credencialessm-windows.ps1
#
# El runtime de JavaFX queda en target\credencialessm-runtime (bin\CredencialesSM).
# jpackage envuelve esa imagen en target\credencialessm-windows.
# Para un instalador, cambie --type app-image por --type exe.

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if (-not $env:JAVA_HOME) {
    throw "Defina JAVA_HOME con Amazon Corretto 21 para Windows."
}
$Jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $Jpackage)) {
    throw "No está jpackage en $Jpackage"
}

mvn javafx:jlink
$Runtime = Join-Path $Root "target\credencialessm-runtime"
if (-not (Test-Path $Runtime)) {
    throw "No se generó $Runtime"
}

$Destino = Join-Path $Root "target\credencialessm-windows"
if (Test-Path $Destino) {
    Remove-Item -Recurse -Force $Destino
}

& $Jpackage `
    --type app-image `
    --name CredencialesSM `
    --app-version 1.0.0 `
    --vendor SMSEM `
    --runtime-image $Runtime `
    --dest $Destino

Write-Host "Listo: $Destino\CredencialesSM"
Write-Host "También puede lanzar directo: $Runtime\bin\CredencialesSM"
