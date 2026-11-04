# Verificador ZXP

No se empaqueta con la estación. `mvn package` de CredencialesSM no lo construye.

Genera la carpeta para enviar:

```
cd zxp-prueba
mvn -o package
```

El resultado queda en `zxp-prueba/target/envio/`: el jar, `lib/` con solo el SDK que usa la ZXP, los lanzadores y `natives/ZebraNativeUsbAdapter_64.dll`. Esa carpeta es la que se copia a la máquina de la impresora. Ahí se ejecuta `zxp-prueba.bat`.

Abre un PNG de `print-preview` (la hoja completa o una cara). El registro de cada intento se ve en la ventana y en `%USERPROFILE%\.credencialessm\zxp-prueba.log`.
