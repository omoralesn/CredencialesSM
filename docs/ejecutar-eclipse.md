# Ejecutar CredencialesSM desde Eclipse

El error «faltan los componentes de JavaFX runtime» aparece si Eclipse lanza `CredencialesApp` directo. Esa clase extiende JavaFX. La clase que hay que ejecutar es `mx.org.smsem.credencialessm.CredencialesMain`.

## Importar

1. File → Import → Maven → Existing Maven Projects.
2. Importar estas dos carpetas:
   - `/home/ojmn/EspaciosTrabajo/Eclipse/sidid`
   - `/home/ojmn/EspaciosTrabajo/Eclipse/CredencialesSM`
3. Clic derecho en **credencialessm** → Maven → Update Project.
4. El JDK del proyecto debe ser Java 21. En esta máquina: `/home/ojmn/programas/java/amazon-corretto-21.0.12.8.1-linux-x64`.

El ingreso de prueba es `operador` / `operador123`. No usa la base: el padrón del Excel queda en memoria y la impresión guarda ids simulados de foto y firma. Para conectar a SMSEM cuando haya base, define `CREDENCIALES_MOCK=false`.

## Ejecutar

1. Abrir `CredencialesMain.java`.
2. Clic derecho → Run As → Java Application.
3. En Run → Run Configurations → CredencialesMain → Environment, agregar:

```
CREDENCIALES_SMSEM_URL=http://localhost:8080/SMSEM
CREDENCIALES_TOKEN=estacion-credenciales-smsem
SIDI_PRINTER=preview
```

`SIDI_PRINTER=preview` guarda un PNG de la credencial. En la estación Windows, con la Zebra, usar `zxp7`.

El ingreso es `operador` / `operador123` mientras `CREDENCIALES_MOCK` no sea `false`. Con la base disponible, pon `CREDENCIALES_MOCK=false` y usa un usuario de `TE_USUARIOS` con perfil `CRDEMP`. SMSEM debe estar levantado y la tabla `TE_PERSONALSMSEM` creada con `SMSEM/sql/TE_PERSONALSMSEM.sql`.

## Si Eclipse sigue sin ver JavaFX

En la misma configuración de ejecución, pestaña Arguments → VM arguments, pegar (ajusta la ruta de `.m2` si no es esta):

```
--module-path /home/ojmn/.m2/repository/org/openjfx/javafx-base/21.0.2/javafx-base-21.0.2-linux.jar:/home/ojmn/.m2/repository/org/openjfx/javafx-graphics/21.0.2/javafx-graphics-21.0.2-linux.jar:/home/ojmn/.m2/repository/org/openjfx/javafx-controls/21.0.2/javafx-controls-21.0.2-linux.jar:/home/ojmn/.m2/repository/org/openjfx/javafx-swing/21.0.2/javafx-swing-21.0.2-linux.jar --add-modules javafx.controls,javafx.swing
```

En Windows el sufijo de esos JAR es `-win`, no `-linux`.
