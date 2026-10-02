# Genera CredencialesSM.exe con el icono del sello.
# Ejecutar en Windows, con Amazon Corretto 21 (trae jpackage) y Maven:
#
#   powershell -ExecutionPolicy Bypass -File packaging\credencialessm-windows.ps1
#
# El programa queda en target\credencialessm-windows\CredencialesSM\CredencialesSM.exe
# Si WiX 3 está instalado (candle.exe), también arma el instalador .exe.
# Desde Linux no sale un .exe: ahí se lanza con  mvn javafx:run

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
$Icono = Join-Path $Root "packaging\credencialessm.ico"
if (-not (Test-Path $Icono)) {
    throw "No está el icono $Icono"
}

mvn -q -DskipTests package
$Jar = Join-Path $Root "target\credencialessm-1.0.0-SNAPSHOT.jar"
if (-not (Test-Path $Jar)) {
    throw "No se generó $Jar"
}

$Entrada = Join-Path $Root "target\jpackage-input"
if (Test-Path $Entrada) {
    Remove-Item -Recurse -Force $Entrada
}
New-Item -ItemType Directory -Path $Entrada | Out-Null

mvn -q dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=$Entrada"
Copy-Item $Jar $Entrada
Copy-Item (Join-Path $Root "lib\sidid.jar") $Entrada

Get-ChildItem $Entrada -Filter "javafx-*.jar" | ForEach-Object {
    if ($_.Name -match "-win\.jar$") {
        return
    }
    $conNativo = Join-Path $Entrada ($_.Name -replace "\.jar$", "-win.jar")
    if (Test-Path $conNativo) {
        Remove-Item $_.FullName
    }
}

$Destino = Join-Path $Root "target\credencialessm-windows"
if (Test-Path $Destino) {
    Remove-Item -Recurse -Force $Destino
}

$Comunes = @(
    "--name", "CredencialesSM",
    "--app-version", "1.0.0",
    "--vendor", "SMSEM",
    "--description", "Estación de credenciales SMSEM",
    "--icon", $Icono,
    "--input", $Entrada,
    "--main-jar", "credencialessm-1.0.0-SNAPSHOT.jar",
    "--main-class", "mx.org.smsem.credencialessm.CredencialesMain",
    "--java-options", "-Dfile.encoding=UTF-8"
)

& $Jpackage @Comunes --type app-image --dest $Destino
$Exe = Join-Path $Destino "CredencialesSM\CredencialesSM.exe"
if (-not (Test-Path $Exe)) {
    throw "No se generó $Exe"
}
Write-Host "Ejecutable: $Exe"

$Wix = Get-Command candle.exe -ErrorAction SilentlyContinue
if ($Wix) {
    $Instalador = Join-Path $Root "target\credencialessm-instalador"
    if (Test-Path $Instalador) {
        Remove-Item -Recurse -Force $Instalador
    }
    & $Jpackage @Comunes --type exe --dest $Instalador --win-shortcut --win-menu --win-dir-chooser
    Write-Host "Instalador: $Instalador"
} else {
    Write-Host "Sin WiX 3 no se armó el instalador. El programa para abrir es $Exe"
}
