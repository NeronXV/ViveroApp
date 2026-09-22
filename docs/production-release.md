# Entrega Android 1.0.1 (versionCode 2)

Esta entrega corrige la navegación a Inventario, abre la recepción del producto
recién creado y evita reabrir esa recepción al refrescar las existencias. La
validación local no sustituye el piloto de caja ni certifica el entorno remoto.

## Configuración y firma

Debug conserva `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` y `AUTH_REDIRECT_URL`.
Release usa exclusivamente estas propiedades de `local.properties`, ignorado por Git:

```properties
RELEASE_SUPABASE_URL=https://proyecto-de-entrega.supabase.co
RELEASE_SUPABASE_PUBLISHABLE_KEY=CLAVE_PUBLICABLE_DEL_PROYECTO
RELEASE_AUTH_REDIRECT_URL=https://sitio-de-entrega.example/recuperar
```

Son ejemplos: confirmar el proyecto y el redirect exactos antes de configurar.
La clave debe ser publicable o un JWT `anon`, nunca `service_role` o `sb_secret_*`.
La validación del build comprueba formato, no autentica contra Supabase ni certifica
que las migraciones o el redirect estén configurados en el servidor.

Configurar la firma existente en `key.properties`, también ignorado por Git:

```properties
storeFile=C:/ruta/segura/firma-existente.jks
storePassword=CONTRASENA_LOCAL
keyAlias=ALIAS_EXISTENTE
keyPassword=CONTRASENA_LOCAL
```

Usar barras `/` en la ruta de Windows y el escape estándar de Java Properties
cuando las contraseñas contengan caracteres especiales. No enviar contraseñas al
chat, registrarlas en logs ni versionar este archivo. Reutilizar la misma clave
del APK instalado: cambiarla impide actualizar normalmente la aplicación.
Custodiar una copia del almacén de firma y sus credenciales fuera del repositorio.

## Validaciones y artefacto

Desde la raíz, con el JDK configurado:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintRelease
powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase\tests\verify_migrations.ps1
.\gradlew.bat verifyReleaseConfiguration
.\gradlew.bat assembleRelease
```

`packageRelease` y `packageReleaseBundle` dependen de la validación de configuración
y firma. No se genera un paquete release si falta esa configuración. El APK nuevo
queda en `app/build/outputs/apk/release/`; no reemplaza el APK preexistente de
`app/release/`. Para Google Play, generar `bundleRelease` una vez definido ese canal.

Verificar el APK con `apksigner verify --print-certs`, comparar el SHA-256 del
certificado con la versión instalada y registrar SHA-256 del archivo, versión,
commit base, cambios locales incluidos y entorno de destino. Probar actualización
desde el APK anterior sin desinstalar ni borrar datos, además de instalación limpia.

## Datos locales y recuperación

La app deshabilita el respaldo automático y excluye sus datos de las transferencias
entre dispositivos: sesiones, borradores, ventas pendientes e intentos de pago no
deben clonarse a otra instalación. Las actualizaciones normales conservan los datos
locales; una desinstalación, borrado de datos o pérdida de la tablet puede perder los
borradores y operaciones todavía no sincronizados.

Antes de reemplazar o retirar una tablet, sincronizar sus comandas y conciliar los
cobros inciertos con los tickets del servidor. No repetir una entrega de dinero ni
borrar datos para resolver un cobro incierto. Las operaciones confirmadas se
consultan en Supabase; el respaldo del backend se administra por separado.

Referencias: [respaldo de Android](https://developer.android.com/identity/data/autobackup)
y [firma de aplicaciones](https://developer.android.com/studio/publish/app-signing).

## Condiciones para autorizar la salida

- [x] Aplicar las 35 migraciones desde cero en un Supabase local de pruebas
  confirmado y ejecutar las 18 suites pgTAP (499 aserciones correctas).
- [x] Probar competencia entre dos cajeros y reintentos idempotentes:
  una venta, un pago, un descuento de inventario y ningún efecto parcial.
- [ ] Ejecutar pruebas instrumentadas: migraciones Room, caja e inventario.
- [ ] Completar el recorrido de `pilot-runbook.md` con datos sintéticos y registrar
  evidencias. Incluir permisos por rol/sucursal, cámara, pérdida de red, reinicio,
  devolución y corte. Validar el APK release final en las tablets de operación.
- [ ] Confirmar el proyecto remoto, las migraciones aplicadas, las políticas RLS,
  Auth/SMTP y la recepción real de invitaciones y recuperación de contraseña.
- [ ] Confirmar el alcance operativo: cortes y devoluciones se administran en Web;
  los reportes de ventas existentes muestran importes antes de devoluciones.
- [ ] Cargar y verificar usuarios, roles, sucursales, precios y conteo inicial.
  Activar inventario por sucursal solo después de verificar ese conteo. Revisar los
  pagos históricos sin corte antes del primer cierre.
- [ ] Registrar respaldo del backend y demostrar restauración en un entorno de
  prueba. Identificar responsable de incidencias y revisión de fallos de RPC/Auth.
- [ ] Confirmar canal de distribución y firma del artefacto. Conservar el artefacto
  anterior y un procedimiento de recuperación compatible con los datos actuales.
- [ ] Obtener autorización sobre el destino concreto antes de aplicar migraciones,
  desplegar funciones, publicar o distribuir la entrega.

Ante duplicación, descuadre de caja/inventario o acceso entre sucursales, detener
las operaciones afectadas y conciliar con el servidor. Una recuperación no debe
desinstalar la app ni revertir migraciones borrando datos. Corregir hacia adelante
y probar la nueva versión con el mismo certificado.

El boletín requiere además los secretos y la verificación de entrega descritos en
`presential-release.md` si se incluye en la primera salida. Las mejoras visuales,
actualizaciones generales de dependencias y nuevas funcionalidades quedan fuera
de este cierre.

## Evidencia local del 21 de septiembre de 2026

Base: `main`, commit `ee084b3a48317058ab72e1c1745cdb1d46279dbc`, más los cambios
locales de esta preparación. No representa un commit de release.

- `assembleDebug testDebugUnitTest lintRelease`: correcto con JDK 21; 226 pruebas
  unitarias sin fallos, lint con cero errores y 30 advertencias.
- `assembleDebugAndroidTest`: correcto; compila las pruebas pero no las ejecuta.
- `verify_migrations.ps1`: 134 comprobaciones estáticas correctas.
- `verifyReleaseConfiguration`: rechazo esperado por falta de
  `RELEASE_SUPABASE_URL`. Aún faltan destino explícito y configuración de firma.
- `assembleRelease bundleRelease --dry-run`: ambos paquetes incluyen la validación
  obligatoria de release en el grafo. No se generó un nuevo APK/AAB release.
- `apksigner verify --print-certs app/release/app-release.apk`: firma válida del
  APK preexistente; aún no se dispone de su configuración de firma para actualizarlo.
- Docker y la base aislada se ejecutaron: 35 migraciones desde cero, 499 pruebas pgTAP y cinco escenarios de concurrencia correctos. Ver `database-validation.md`. Siguen pendientes pruebas de dispositivo, piloto y servicios HTTP/correo. No se consultó ni modificó Supabase remoto.
