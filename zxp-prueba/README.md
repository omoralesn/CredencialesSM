# Verificador ZXP

No se empaqueta con la estación. `mvn package` de CredencialesSM no lo construye.

## Linux (desarrollo)

```bash
cd zxp-prueba
mvn -DskipTests clean package
```

Resultado en `zxp-prueba/target/envio/`: el jar, `lib/` con el SDK que usa la ZXP, los lanzadores y `natives/ZebraNativeUsbAdapter_64.dll`.

## Windows (estación / impresora)

```bash
cd zxp-prueba
mvn -Pwindows -DskipTests clean verify
```

Resultado: `zxp-prueba/target/zxp-prueba-windows.zip`  
Contiene `zxp-prueba-windows/` con el jar, `lib/` (incluye `libusb4java` win32), `natives/ZebraNativeUsbAdapter_64.dll` y `zxp-prueba.bat`.

En la máquina de la impresora: descomprimir y ejecutar `zxp-prueba.bat` (Java 21 en el `PATH`).

Abre un PNG de `print-preview` (la hoja completa o una cara). El registro de cada intento se ve en la ventana y en `%USERPROFILE%\.credencialessm\zxp-prueba.log`.
