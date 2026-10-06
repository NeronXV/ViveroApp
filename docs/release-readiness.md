# Pendientes para la entrega operativa de Vivero

Revisión local del 4 de octubre de 2026, America/Mazatlan. Fuente del estado:
[README](../README.md#estado-actual). Esta guía convierte los pendientes en
criterios de aceptación; las filas sin evidencia nueva permanecen pendientes.

## Decisiones y avances de este bloque

- Pedro confirmó que las dos ventas UUID antiguas fallidas **eran pruebas**.
  Se conservan como historial de ensayo; no deben enviarse como ventas MariaDB
  ni generar inventario/pagos. La clasificación no prueba que se hayan cerrado
  otros intentos del dispositivo o del navegador.
- Distribución acordada: **APK directo**. Se instaló 1.0.7-vps,
  código 8, sobre 1.0.6-vps en el Samsung A22 conectado. La firma definitiva
  sigue pendiente, con [procedimiento de comparación](production-release.md).
- Catálogo incluye cámara QR/EAN/Code 128 usando CameraX/ML Kit existentes.
  El permiso CAMERA se solicita al abrirla. Denegación, fallo y cancelación
  permiten volver a la entrada manual. No guarda fotos/video. Un código cierra
  la cámara y consulta la API; agregar al carrito sigue siendo una acción explícita.
  `SCAN_PRODUCTS` habilita la cámara; `CREATE_SALES` y sucursal activa habilitan
  agregar al carrito. Roles nominales no sustituyen capacidades del servidor.
- El README conserva la documentación anterior bajo una sección histórica y
  dirige a los contratos API actuales. No se retiró Supabase.

## Criterios de aceptación

Ejecutar operaciones nuevas solo en un entorno de ensayo confirmado con datos
sintéticos. Conservar las claves originales para recuperar respuestas inciertas.
En producción, revisar y conciliar las operaciones existentes antes de tomar
cualquier decisión de cobro, retiro o cambio de inventario.

| Área | Comprobación para cerrar | Evidencia requerida | Estado |
|---|---|---|---|
| Acceso | Login, rol/capacidades y sucursal en Web/tablet; una cuenta sin capacidad no puede operar | Versión, cuenta sintética/rol, resultado y 403 del servidor | Pendiente |
| Cámara | QR/EAN/Code 128, ceros iniciales, sin permiso, permiso denegado, volver de ajustes, cancelación y fallo; un producto sin agregado automático | Modelo Android, formato, resultado de consulta y carrito antes/después | Código compilable; cámara física pendiente |
| Carrito | Dos unidades persisten al cerrar/reabrir; separación de cuenta/sucursal; nueva sesión tras reinicio de proceso | Captura sin datos sensibles y resultado Room | Pendiente en dispositivo |
| Venta/pago | Producto de 500 centavos × 2; cotización 1000, efectivo 1200, cambio 200; una venta/pago | Folio sintético, comprobante y conciliación SQL | Ensayo previo documentado; aceptación nueva pendiente |
| Inventario | Saldo 10→8 al pagar, una salida de dos; reabrir/reintentar no descuenta otra vez; insuficiencia rechaza entero | Saldo/movimientos y conteos SQL | Pendiente de aceptación humana |
| Red/reinicio | Perder respuesta después de enviar/pagar, reabrir y recuperar con la misma clave; nunca repetir con otra | Una venta/pago/movimiento y estado de intento conciliado | Pendiente |
| Cambio de contraseña | Contraseña actual incorrecta conserva sesión; cambio confirmado revoca sesiones; login nuevo | Resultado humano, sin contraseñas en evidencia | Ensayo HTTP anterior; aceptación humana pendiente |
| Actualización APK | Certificado idéntico, código mayor, update conserva bases/carrito/historial | Verificador APK, respaldo privado y revisión en tablet | Debug verificado e instalado en Samsung A22, datos conservados; firma release pendiente |
| Cuentas | Cinco cuentas importadas acceden con rol/sucursal correctos según revisión previa | Acceso individual y contexto de API, sin credenciales en reporte | Pendiente de verificación/configuración |
| Correo | Remitente/dominio verificados; recuperación e invitación llegan; enlace expira y solo se usa una vez | Recepción real y resultados, sin tokens | Pendiente de cuenta/configuración y ensayo |
| CENTRO | Confirmar conteo físico y acceso responsable; activar mediante endpoint oficial | Conteo y resultado autorizado por sucursal | Pendiente; MATRIZ ya activa según evidencia anterior |
| Copia externa | Subir un respaldo COMPLETE verificado y restaurarlo desde la copia cifrada | Snapshot/hash, restauración aislada y conciliación | Pendiente R2/credenciales/custodia |

Guía de entorno sintético: [aceptación Web/Android](vps-acceptance-web-android.md).
Conservar su historia y verificar que el entorno sigue disponible antes de usarlo.
No reutilizar cuentas operativas para ventas de prueba ni ejecutar tests conectados
contra el paquete habitual: el runner anterior desinstaló la app al terminar.
Preferir paquete separado y respaldar todos los datos privados de la tablet.

## Conciliación de intentos existentes

1. Las dos ventas UUID clasificadas por Pedro se registran como **pruebas** en
   esta guía; no se altera la base del dispositivo ni se aplica un mapeo a MariaDB.
2. Inventariar por origen: app habitual, app de ensayo y navegador anterior/nuevo.
   La última revisión documental tiene dos ventas recientes enviadas a caja sin
   pago. Confirmar su estado actual antes de actuar; no confundirlas con las dos
   pruebas UUID. Las claves/token originales siguen privados y fuera del reporte.
3. Consultar el resultado con el mecanismo oficial de recuperación y contrastar
   con venta, pago, comprobante y movimiento. Un timeout o 404 aislado no autoriza
   descartar la clave ni generar otra venta/pago.
4. Resolver rechazos con el mecanismo de retiro/fence del contrato correspondiente.
   Compras conserva un pendiente de retiro seguro de rechazos permanentes; no
   borrar su localStorage para desbloquearlo. Web está fuera de este bloque local.
5. Registrar el resultado sin credenciales. Retirar alias del dominio antiguo y
   considerar la retirada de Supabase solo cuando no queden consumidores/intentos
   dependientes y esté aceptada la operación.

## Configuración externa que falta

**Correo:** usar el sender existente del servidor, `RESEND_API_KEY` y
`ACCOUNT_MAIL_FROM`, con el origen canónico. Las cuentas importadas con rol/sucursal
ya asignados no admiten el flujo de invitación para una cuenta nueva: habilitar
su contraseña con el procedimiento administrativo autorizado o completar el
flujo adecuado. No resetear OWNER para pruebas. Ver
[contratos de cuentas](backend-account-links.md) y [personal](backend-staff-accounts.md).

**R2:** completar el destino y credencial limitada del bucket en archivos privados,
custodiar la contraseña Restic fuera del VPS y usar el script existente
`infra/host/offsite-backup.py`. Antes del primer envío, verificar el respaldo
COMPLETE. Después, `check --read-data` y una restauración aislada desde la copia
externa: check por sí solo no demuestra recuperación de MariaDB. Pedro confirmó
que Resend y R2 todavía no están configurados. No se programan tareas ni retención.
Procedimiento y permisos: [operación VPS](vps-multiproject-operations.md).

**Firma:** falta `key.properties`; el release antiguo y debug actual tienen
certificados distintos. El nuevo verificador rechaza esa combinación. Hace falta
identificar el certificado instalado y disponer de su clave, o acordar una
transición específica que preserve datos. No presentar la APK debug como release.

**Dispositivo conectado:** Samsung A22 SM-A225M, Android 13, físico (no emulador).
La continuación con dispositivo permitió respaldar y actualizar la app habitual
sin desinstalarla. Se verificó la firma contra el APK extraído del propio teléfono,
no solo contra una compilación local. Los respaldos privados permanecen bajo
`tmp/device-readiness-20261004-bd0a7fec31d4475196705fce12e4cc58`, ignorados por Git
y con permisos restringidos. No compartirlos: contienen datos y configuración.

Después de `adb install -r`, los siete archivos persistentes respaldados fueron
idénticos byte por byte: Room 6 íntegro, 15 ventas históricas y cero filas en
intentos API de venta/pago/inventario y carrito API. Esto no concilia operaciones
remotas, otros dispositivos ni navegadores. Se abrió la app para aceptación de
acceso; el login humano y la lectura óptica siguen sin confirmar.

Las pruebas instrumentadas usan `com.intutec.viveroapp.readiness` y su paquete de
tests, independientes de la app habitual. Su API se fijó a `127.0.0.1:33003`.
No ejecutar `connectedAndroidTest` sobre el paquete operativo ni borrar sus datos.

## Validación local

La revisión previa de este chat ejecutó: 87 pruebas backend, syntax check,
8 pruebas simuladas de backup, SQLite Room 3→6 y 134 verificaciones estáticas
Supabase, correctas; Android 355 aprobadas/una HTTP omitida, debug y lintRelease
correctos (32 advertencias). Son evidencia del diagnóstico anterior al cambio
de cámara y versión, no validación de la nueva interfaz.

En este bloque, las cinco pruebas nuevas de catálogo pasaron junto con
`assembleDebug`: consulta sin agregado automático, falta de capacidad, limpieza
del mensaje anterior, respuesta tardía y logout durante la búsqueda.
La validación final posterior al cierre de callbacks de cámara al cancelar pasó:

- `assembleDebug testDebugUnitTest assembleDebugAndroidTest lintRelease`:
  BUILD SUCCESSFUL. 361 casos, 360 aprobados, una prueba HTTP opcional omitida
  sin fixture; cero fallos/errores. Lint: cero errores y 32 advertencias.
- `python app/src/test/scripts/verify_backend_outbox_sql.py`: correcto,
  Room 3→4→5→6, preservación UUID, FK, checkout atómico y recuperación.
- `scripts/verify-android-apk.ps1`: APK de ensayo 1.0.7/código 8 validado
  con `-AllowDebug` contra una referencia debug 1.0.6 compilada localmente.
  Rechazos correctos de certificado distinto, debug sin autorización de ensayo
  y versión inesperada. La referencia no acredita el APK instalado en tablet.
- BuildConfig generado conserva API/Web en el origen canónico HTTPS; esta
  comprobación no ejecuta operaciones remotas.
- `git diff --check` de los archivos propios: correcto. El diff completo conserva
  los dos avisos de líneas vacías finales preexistentes en backend-inventory.md
  y backend-refunds.md; no se corrigieron cambios ajenos.

APK debug de ensayo (paquete habitual, instalado en el Samsung conectado):
`tmp/readiness-c9e3018843ff41939a8da97fcfec1723/Vivero-Dulcinea-1.0.7-pruebas.apk`.
SHA-256: `a78fd3bde129dbf23851a0e53defb01c011834933815d9c788cbbcbd3f994557`.
Está ignorado por Git y apunta al VPS operativo; no enviar ventas de prueba a
esa base. No constituye una entrega release ni prueba física de cámara.

La continuación ejecutó pruebas conectadas; véase la evidencia adicional abajo.
No se ejecutaron MariaDB/pgTAP, correo, copia R2 ni UI Web en esta continuación.
Docker Desktop no pudo iniciar su motor local por errores de acceso a sockets.
Se conservaron los sockets anteriores apartando únicamente los directorios
temporales `Docker/run` y `docker-secrets-engine` a sus hermanos con sufijo
`-stale-20261004-readiness`; no se borraron volúmenes, bases ni configuración.
El arranque continuó fallando en `sailor-ingest.sock`; la venta integral local
queda bloqueada hasta recuperar el motor. No se sustituyó por producción.
Los contratos SQL y backend no cambiaron. La exportación n8n y su copia permanecen como reporte histórico;
su actualización/sincronización corresponde al cierre solicitado de sesión.

## Archivos propios

### Evidencia adicional con dispositivo, 4 de octubre

- `gradlew.bat -I tmp/device-tests/readiness.init.gradle assembleDebug assembleDebugAndroidTest`:
  correcto, paquete separado y origen exclusivamente local.
- `adb shell am instrument -w -r -e class ... com.intutec.viveroapp.readiness.test/androidx.test.runner.AndroidJUnitRunner`:
  **44 casos aprobados**, cero fallos, en Samsung A22 Android 13. Incluye
  CameraCodeDialogTest, ViveroDatabaseV2Test, HomeScreenTest,
  CashierPaymentScreenTest, InventoryScreenTest y CustomerSearchDialogTest.
  Log local ignorado: `tmp/device-tests/instrumentation-final.log`.
- La primera ejecución pasó 43/44: una expectativa del test de cobro heredado
  buscaba «Reserva visual» aunque la UI dice «Reserva de cobro». Se corrigió
  únicamente esa expectativa, se recompiló el APK de tests y se repitieron los
  44 casos con éxito. No se modificó el comportamiento del cobro.
- Los dos casos nuevos comprueban entrada manual y cancelación sin permiso de
  cámara, sin emitir códigos. No prueban el sensor ni decodificación óptica.
  Las pruebas de las pantallas heredadas tampoco certifican el flujo API completo.
- La instalación habitual pasó el verificador de versión/firma contra su APK
  real anterior, `adb install -r` y comparación de los respaldos antes/después.
- No se repitieron pruebas unitarias/lint por esta corrección exclusiva del test;
  su resultado del bloque anterior se conserva separado más arriba.

### Relación de archivos

- `app/build.gradle.kts`.
- `app/src/main/java/com/intutec/viveroapp/feature/catalog/presentation/BackendCatalogScreen.kt`.
- `app/src/main/java/com/intutec/viveroapp/feature/catalog/presentation/BackendCatalogViewModel.kt`.
- `app/src/main/java/com/intutec/viveroapp/feature/scanner/presentation/ScannerScreen.kt`.
- `app/src/main/java/com/intutec/viveroapp/feature/scanner/presentation/CameraCodeDialog.kt`.
- `app/src/test/java/com/intutec/viveroapp/feature/catalog/BackendCatalogViewModelTest.kt`.
- `app/src/androidTest/java/com/intutec/viveroapp/feature/scanner/CameraCodeDialogTest.kt`.
- `app/src/androidTest/java/com/intutec/viveroapp/feature/cashier/CashierPaymentScreenTest.kt`.
- `scripts/verify-android-apk.ps1`.
- `README.md`: estado superior y etiqueta histórica, conservando el trabajo previo.
- `docs/production-release.md`.
- `docs/backend-android-workflow.md`.
- `docs/backend-api-mariadb.md`.
- `docs/supabase-migration-map.md`.
- `docs/release-readiness.md`.

Los cambios se limitan a Android, el verificador APK y documentación. Sin cambios
SQL ni comprobaciones nuevas de integración remota, correo, R2 o UI Web.
Los cambios previos de IDE, AGENTS.md, README y documentación se conservan.
Rama `main`, HEAD `3369bbb95179ec62daa4d369efd61b5cef3d5097`; sin commit,
push ni despliegue de servidor. Hubo instalación Android local explícitamente
autorizada, conservando la app habitual y aislando las pruebas.
