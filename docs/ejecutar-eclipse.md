# Ejecutar CredencialesSM desde Eclipse

El error «faltan los componentes de JavaFX runtime» aparece si Eclipse lanza `CredencialesApp` directo. Esa clase extiende JavaFX. La clase que hay que ejecutar es `mx.org.smsem.credencialessm.CredencialesMain`.

## Importar

1. File → Import → Maven → Existing Maven Projects.
2. Importar estas dos carpetas:
   - `/home/ojmn/EspaciosTrabajo/Eclipse/sidid`
   - `/home/ojmn/EspaciosTrabajo/Eclipse/CredencialesSM`
3. Clic derecho en **credencialessm** → Maven → Update Project.
4. El JDK del proyecto debe ser Java 21. En esta máquina: `/home/ojmn/programas/java/amazon-corretto-21.0.12.8.1-linux-x64`.

El script carga el padrón en `TE_PERSONALSMSEM` y el usuario `operador` / `operador123` con perfil `CRDEMP`. Las consultas, altas y el seguimiento salen de esa tabla. La fotografía se toma con la cámara y la firma con el pad Topaz (SignatureGem 1X5-HID, USB `06a8:0043`).

En Linux el nodo `/dev/hidraw*` del pad queda de root. Sin permiso de lectura y escritura la estación lo ve conectado y no lo abre. Una vez, con la contraseña:

```
/home/ojmn/EspaciosTrabajo/Eclipse/sidid/scripts/setup-topaz.sh
```

Después desconecta el pad 10 segundos y vuelve a conectarlo.

## Ejecutar

1. Abrir `CredencialesMain.java`.
2. Clic derecho → Run As → Java Application.

La dirección del servidor, el token, el modo de impresora y el resto de valores de la estación están en `src/main/resources/credencialessm/constantes.properties`. Ahí se cambian una sola vez.

`impresora.modo=preview` guarda un PNG de la credencial. En la estación Windows, con la Zebra, usar `zxp7`.

SMSEM debe estar levantado contra la base de desarrollo y la tabla `TE_PERSONALSMSEM` creada con `CredencialesSM/sql/TE_PERSONALSMSEM.sql`.

La foto y la firma se guardan en MongoDB. En esta máquina el contenedor es `smsem-mongo` (`mongo:4.4`) en el puerto `27018`, porque el `27017` ya lo usa otro proyecto. Cómo levantarlo está en `SMSEM/docs/mongo-docker.md`. En `/opt/documents2/properties/propertiesSMSEM.properties` la conexión debe quedar así:

```
servidor.SMSEM=mongodb://127.0.0.1:27018
base.SMSEM=test
```

## Si Eclipse sigue sin ver JavaFX

En la misma configuración de ejecución, pestaña Arguments → VM arguments, pegar (ajusta la ruta de `.m2` si no es esta):

```
--module-path /home/ojmn/.m2/repository/org/openjfx/javafx-base/21.0.2/javafx-base-21.0.2-linux.jar:/home/ojmn/.m2/repository/org/openjfx/javafx-graphics/21.0.2/javafx-graphics-21.0.2-linux.jar:/home/ojmn/.m2/repository/org/openjfx/javafx-controls/21.0.2/javafx-controls-21.0.2-linux.jar:/home/ojmn/.m2/repository/org/openjfx/javafx-swing/21.0.2/javafx-swing-21.0.2-linux.jar --add-modules javafx.controls,javafx.swing
```

En Windows el sufijo de esos JAR es `-win`, no `-linux`.
