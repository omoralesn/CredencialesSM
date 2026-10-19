# Scripts de producción — estación CredencialesSM

Ejecutar en el esquema SMSEM (p. ej. `SMSEM01`) en este orden, con el usuario dueño de las tablas.

| Orden | Archivo | Qué hace |
|------:|---------|----------|
| 1 | `01_TE_PERSONALSMSEM.sql` | Tabla, secuencia e índice del padrón |
| 2 | `02_TE_PERFILES_CRDEMP.sql` | Perfil `CRDEMP` |
| 3 | `03_TE_MODULOS_EST_CRDEMP.sql` | Módulo de escritorio con vigencia (`FECVIG` / `FECVEN`) |
| 4 | `04_TR_RELMOD_CRDEMP.sql` | Relación perfil ↔ módulo |
| 5 | `05_TE_USUARIOS_operadores.sql` | Operadores de estación (ajustar usuarios/contraseñas) |
| 6 | `06_caducar_modulo.sql` | Caducar o ampliar vigencia |
| 7 | `07_TE_PERSONALSMSEM_reimpresion.sql` | Columnas de reimpresión física |
| 8 | `99_verificar.sql` | Consultas de comprobación |

El padrón masivo (485 personas) sigue en `../TE_PERSONALSMSEM.sql` si hace falta cargarlo.

**Caducar la estación:** actualizar `FECVEN` del módulo `EST_CRDEMP` (ver `06_caducar_modulo.sql`). El login de CredencialesSM exige módulo vigente y fila en `TR_RELMOD` para el perfil del usuario.
