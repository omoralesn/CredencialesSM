# CredencialesSM

Estación de credenciales de empleados SMSEM (JavaFX 21): captura de foto/firma e impresión en Zebra ZXP Series 7.

Para ejecutar desde Eclipse: [docs/ejecutar-eclipse.md](docs/ejecutar-eclipse.md).

## Paquete Windows (estación)

Genera el zip liviano (JAR shade + DLL USB Zebra + `.bat`, sin JRE embebido ni nativos de Linux):

```bash
mvn -Pwindows-dist -DskipTests clean verify
```

Resultado: `target/CredencialesSM-windows.zip`

En la PC Windows: descomprimir, Java 21 en el `PATH`, y ejecutar `CredencialesSM.bat`.  
`impresora.modo=zxp7` en `constantes.properties` (dentro del JAR o en el recurso de la estación) para imprimir en la Zebra; `preview` solo guarda PNG.

## Verificador ZXP (`zxp-prueba`)

Herramienta aparte para probar impresión USB. No se construye con el `package` de la estación.

```bash
cd zxp-prueba
mvn -Pwindows -DskipTests clean verify
```

Resultado: `zxp-prueba/target/zxp-prueba-windows.zip`  
(carpeta con `zxp-prueba.jar`, `lib/`, `natives/ZebraNativeUsbAdapter_64.dll` y `zxp-prueba.bat`)

Detalle: [zxp-prueba/README.md](zxp-prueba/README.md).
