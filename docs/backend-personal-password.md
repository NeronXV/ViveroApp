# Cambio personal de contraseña

## Uso

En la app habitual 1.0.6-vps (código 7), iniciar sesión, pulsar el icono de
perfil del inicio y seleccionar **Cambiar contraseña**. Escribir contraseña
actual, una nueva de 6 a 128 caracteres y su confirmación. Se admiten frases
y espacios; no se recortan. Al guardar, todas las sesiones se revocan y la app
vuelve al login con una confirmación. No enviar contraseñas por el chat.

Si la contraseña actual es incorrecta, la sesión se conserva y se puede
corregir. Si se pierde comunicación o la respuesta es incompatible, no se
reenvía automáticamente: se cierra el acceso local y se indica intentar login
con la nueva contraseña, y con la anterior si no funciona. No presentar ese
resultado como un cambio confirmado.

## Contrato y seguridad

`POST /api/v1/auth/change-password` requiere Bearer válido y un JSON exacto:

```json
{"current_password":"synthetic current phrase","new_password":"synthetic replacement phrase"}
```

No admite ID ni correo de destino. El usuario se determina exclusivamente por
la sesión y se vuelve a autenticar dentro de la transacción. No requiere rol
administrativo ni sucursal activa. Comprueba la contraseña actual, preserva
espacios y usa scrypt con sal individual. Comparte el presupuesto persistente
de intentos del login y limita a dos cambios con KDF simultáneos por instancia.

Respuesta confirmada: 200 con `schema_version: 1`, `password_changed: true`,
`sessions_revoked: true`. Errores relevantes: 401 sesión inválida; 403
`CURRENT_PASSWORD_INCORRECT`; 400 entrada inválida o `PASSWORD_UNCHANGED`;
429 límite de intentos. La app no muestra cuerpos arbitrarios de errores.

Usa el bloqueo administrativo existente antes de modificar el usuario. La
actualización del hash y la auditoría USER_PASSWORD se confirman juntas;
los triggers existentes revocan sesiones y enlaces de recuperación. Un fallo
de auditoría revierte todos esos efectos. No se añadieron privilegios ni SQL.

Los campos de contraseña del diálogo usan estado efímero, sin SavedState ni
Room. Se enmascaran y se limpian al enviar o cancelar. No se registran ni se
escriben en archivos; los valores sintéticos de pruebas no son credenciales reales.
La recuperación por correo sigue siendo un flujo separado y continúa pendiente.

Cambiar la contraseña personal puede invalidar la antigua credencial privada
de bootstrap usada por algunas verificaciones de mantenimiento. No copiar la
nueva contraseña personal a documentación, scripts, Git ni configuraciones
compartidas; esas verificaciones requerirán una sesión administrativa autorizada.

## Evidencia previa de la entrega 1.0.5 del 2026-10-03

- `npm --prefix backend test`: 87 pruebas aprobadas, cero fallos.
- `assembleDebug testDebugUnitTest`: 355 casos, 354 aprobados, una prueba HTTP
  opcional omitida sin fixture; cero fallos/errores.
- Prueba HTTP/SQL en `vivero-acceptance-20261003`, con cuentas sintéticas creadas
  para el ensayo y retiradas al terminar: contraseña incorrecta, manipulación de
  usuario destino, rollback de auditoría, revocación de dos sesiones, login con
  la nueva contraseña conservando espacios y otra cuenta sin cambios.
- Respaldo operativo completo verificado antes de recrear solo API. Web y
  MariaDB conservan sus contenedores; todos los montajes persistentes se conservan.
- HTTPS público saludable; solicitud de cambio sin sesión rechazada con 401.
  Archivos de la imagen desplegada coinciden por SHA-256 con el código enviado.
- Firma Android igual a la anterior, instalación con `adb install -r` y versión
  1.0.5-vps confirmadas. Base privada respaldada y archivos de base idénticos
  antes/después de actualizar; Room 6 y 15 ventas históricas. No se repitieron
  pruebas conectadas que puedan retirar la aplicación del dispositivo.
- No se cambió la contraseña de ninguna cuenta real desde herramientas.
  Falta que Pedro complete el recorrido humano y confirme el nuevo login.
- `git diff --check` aprobado; cambios preexistentes conservados, sin commit ni push.

Reversión API: imagen `vivero-vps-api:before-password-change-20261003` y evidencia
privada en `/opt/vivero/maintenance/20261003-password-change-attempt2`.
Los respaldos y APK locales están en `tmp/`, privado e ignorado por Git.
Supabase, R2 y correo no se modificaron.

## Archivos de esta tarea

- `backend/src/auth/change-password.js`, `backend/src/app.js` y `backend/package.json`.
- `backend/test/password-change.test.js`.
- Android `feature/auth/domain/repository/BackendAuthGateway.kt`.
- Android `feature/auth/data/remote/BackendAuthRemoteDataSource.kt`.
- Android `feature/auth/data/repository/BackendAuthRepository.kt`.
- Android `feature/auth/presentation/BackendAuthViewModel.kt`.
- Android `feature/home/presentation/BackendHomeScreen.kt` y `navigation/ViveroApp.kt`.
- `app/build.gradle.kts`: únicamente versión/código de esta continuación.
- Pruebas Android `BackendAuthRemoteTest.kt`, `BackendAuthRepositoryTest.kt` y
  adaptación del fake en `BackendWorkflowTest.kt` al nuevo método de contrato.
- Este documento y enlace desde `docs/android-premium-vps.md`.

Las rutas Android anteriores son relativas a
`app/src/main/java/com/intutec/viveroapp/` salvo las de pruebas y Gradle.

## Ajuste solicitado: mínimo 6, versión 1.0.6

El cambio personal de contraseña ahora acepta de 6 a 128 caracteres Unicode
(incluye exactamente 6); 5 se rechaza. UI, repositorio, transporte Android y API
usan el mismo mínimo. La creación administrativa y recuperación por enlaces
conservan su mínimo previo de 15: el mínimo de 6 se pasa explícitamente solo
para el cambio personal y su hash. No se modificaron claves reales.

Validaciones de esta continuación: 87 pruebas backend aprobadas; compilación
Android y 355 pruebas aprobadas, una HTTP opcional omitida (356 casos), cero
fallos. Prueba HTTP/SQL aislada con nueva contraseña sintética de exactamente
6 caracteres, rechazo de 5, revocación, rollback de auditoría y nuevo login.
Respaldo completo anterior verificado antes de recrear exclusivamente API;
MariaDB, Web y todos los montajes permanecen iguales. HTTPS saludable y cambio
sin sesión rechazado. Actualización Android 1.0.6-vps (código 7) con firma igual,
respaldo privado íntegro y cada archivo de base idéntico antes/después, previo
al arranque. Sin pruebas conectadas, cambios SQL, commit ni push.

Evidencia VPS: `/opt/vivero/maintenance/20261003-password-minimum`.
Imagen de reversión: `vivero-vps-api:before-password-minimum-20261003`.
Cambios de esta continuación: `backend/src/auth/password.js`,
`backend/src/auth/change-password.js`, `backend/test/password-change.test.js`,
Android `BackendAuthRemoteDataSource.kt`, `BackendAuthRepository.kt`,
`BackendHomeScreen.kt`, `BackendAuthRemoteTest.kt`, `app/build.gradle.kts`
y este documento. Cambios preexistentes preservados. Aún falta confirmar el
recorrido humano de Pedro; la contraseña actual debe ser correcta y la nueva
debe coincidir con su confirmación y ser distinta de la anterior.
