# Recorrido operativo Android con Backend API

Estado posterior: inventario nativo integrado con API/MariaDB y Room 6.
[Alcance y evidencia nuevos](backend-android-inventory.md). Este documento
conserva los conteos y pendientes históricos de su bloque.

Estado posterior del mismo día: historial, comprobantes y cierre seguro de pagos
ya están integrados. Consultar [backend-android-history.md](backend-android-history.md)
para el nuevo alcance y evidencia. Los conteos siguientes conservan la validación
del bloque anterior.

Estado verificado localmente el 2026-10-02. Esta entrega activa la API oficial en
el grafo principal Android: login → permisos → catálogo → carrito → cotización →
envío a caja → cobro. No crea otro backend ni cambia el esquema MariaDB.
No constituye un corte de producción ni permite apagar Supabase todavía.

## Comportamiento y datos

- `BackendAuthViewModel` usa la sesión opaca y el contexto de permisos de la API.
  Logout, expiración y cambios de cuenta invalidan operaciones y respuestas tardías.
  La sesión se mantiene en memoria: reiniciar el proceso requiere iniciar sesión.
  Recuperación solicita un enlace API que se completa en la Web; el correo real
  sigue pendiente de validar. No se concede una sesión Supabase a los consumidores.
- Catálogo usa precios/promociones/detalles e imágenes API, filtros y paginación.
  La consulta por código es manual. La cámara del escáner anterior aún no está
  integrada en este recorrido. Agregar al carrito requiere `CREATE_SALES` y sucursal.
- Carrito Room separado por cuenta/sucursal, IDs enteros, revisión y centavos.
  El precio mostrado es orientativo; la cotización del servidor determina el total.
  La confirmación exige la misma revisión. Consumir el borrador y guardar el
  intento con su clave original son una sola transacción; cualquier fallo revierte
  ambos. El historial API y cierre seguro de intentos ya preparados se reutilizan.
- Caja obtiene comandas y una reserva del servidor. Guarda cuerpo/clave antes del
  POST, verifica cuenta/sucursal y valida el recibo y la aritmética de centavos. Un pago
  incierto bloquea otros pagos de esa cuenta/sucursal. «Consultar resultado» solo
  observa. «Reintentar el mismo cobro» requiere confirmación, consulta primero y
  solo un `404 PAYMENT_NOT_FOUND` exacto permite reenviar el cuerpo y clave originales.
  No obtiene otra reserva ni genera otra clave. Si expiró la reserva, cambió la
  autorización o el servidor rechaza el pago, el intento permanece para revisión;
  falta un cierre administrativo seguro para esos casos permanentes.
- Los encabezados esperados de cuenta/sucursal se comprueban en el backend dentro
  de la transacción autorizada. Web conserva su contrato sin esos encabezados.
  El servidor sigue controlando precios, inventario, permisos e idempotencia.

Room aumenta de 4 a 5 con migración explícita: `backend_cart_drafts`,
`backend_cart_items` (FK real con cascada) y `backend_payment_attempts`. Usa PK/FK
e índices únicos mínimos. `SYNCING` de pagos pasa a `UNCERTAIN` al reabrir.
Las tablas UUID originales y los intentos API de v4 se conservan. No hay conversión
de UUID a entero ni reenvío automático de intentos Supabase. Antes de instalar esta
versión en un dispositivo operativo, respaldar y conciliar sus intentos antiguos;
el nuevo panel solo muestra intentos API de la cuenta/sucursal actual.

## Configuración local Android

En `local.properties`, ignorado por Git, conservar `sdk.dir` y añadir solo los
orígenes públicos. Para el entorno sintético ensayado en el puerto 33003:

```properties
BACKEND_API_URL=http://10.0.2.2:33003
BACKEND_WEB_URL=
```

`10.0.2.2` corresponde al host desde el emulador Android. En un dispositivo con
USB se puede preparar `adb reverse tcp:33003 tcp:33003` y usar
`http://127.0.0.1:33003`. No se ejecutó ADB ni se inició un emulador en esta sesión.
Para el Compose estándar usar el puerto publicado en su plantilla. El origen no
incluye `/api/v1`, credenciales, query ni fragmento. Debug admite HTTP local;
release requiere `RELEASE_BACKEND_API_URL` HTTPS, opcional
`RELEASE_BACKEND_WEB_URL` HTTPS y la firma existente de `key.properties`.
En esta sesión se añadió únicamente el origen público debug ausente a
`local.properties` ignorado; se conservaron las demás propiedades y no se
mostraron secretos. La configuración Supabase del APK queda
vacía; una API sin configurar muestra un error y no activa fallback.

Desde la raíz de ViveroApp, con JDK 21 configurado:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest
python app/src/test/scripts/verify_backend_outbox_sql.py
```

La instalación local y bootstrap de cuentas MariaDB están en
[backend-api-mariadb.md](backend-api-mariadb.md) y
[backend-staff-accounts.md](backend-staff-accounts.md). Las cuentas antiguas de
Supabase no existen en MariaDB hasta importar/activar su identidad correspondiente.

## Archivos de esta entrega

Todas las rutas Kotlin siguientes parten de
`app/src/main/java/com/intutec/viveroapp/`:

| Área | Archivos añadidos o modificados |
|---|---|
| Entrada/configuración | `navigation/ViveroApp.kt`, `app/build.gradle.kts`, `core/session/BackendAccess.kt`, `feature/auth/presentation/BackendAuthViewModel.kt`, campo backend en `AuthUiState` |
| Transporte/DI/Room | `core/network/BackendApiTransport.kt`, `core/di/BackendNetworkModule.kt`, `core/di/DatabaseModule.kt`, `core/database/ViveroDatabase.kt` |
| Catálogo | `feature/catalog/presentation/BackendCatalogViewModel.kt`, `BackendCatalogScreen.kt` |
| Carrito y envío | `feature/cart/domain/repository/BackendCartRepository.kt`, `BackendSaleEmitter.kt`; `data/local/BackendCartDao.kt`, `BackendSaleAttemptDao.kt`; `data/repository/BackendRoomCartRepository.kt`, `BackendSaleEmitterRepository.kt`; `sync/BackendSaleOutboxStore.kt`; `presentation/BackendCartViewModel.kt`, `BackendCartScreen.kt` |
| Caja | `feature/cashier/domain/repository/BackendCashierGateway.kt`; `data/local/BackendPaymentAttemptDao.kt`; `data/remote/BackendCashierRemoteDataSource.kt`; `data/repository/BackendCashierPayments.kt`; `presentation/BackendCashierViewModel.kt`, `BackendCashierScreen.kt` |
| Backend | `backend/src/app.js`, `backend/test/cashier-integration.test.js`, `backend/test/android-workflow-fixture.js` |
| Pruebas | `BackendWorkflowTest`, `BackendCashierPaymentsTest`, `BackendCashierRemoteTest`, `BackendAndroidHttpIntegrationTest`; fakes de emitter/synchronizer/panel, fixture de `AuthRepositoryImplTest`, `ViveroDatabaseV2Test` y `verify_backend_outbox_sql.py` |
| Documentación | Este documento, README, mapa de migración, estado de corte y notas de alcance en documentos Android anteriores |

## Evidencia nueva y pendientes

- `assembleDebug testDebugUnitTest assembleDebugAndroidTest`: correcto. 320 casos,
  319 pasan y uno opcional HTTP se omite sin fixture. Cero errores/fallos.
- La prueba HTTP opcional se ejecutó separadamente contra Compose aislado en
  `127.0.0.1:33003`, con el transporte Kotlin real: login/contexto, búsqueda/código,
  cotización, envío/recuperación/retiro, cola/reserva/pago/recuperación y logout.
  Conciliación SQL: una venta pagada de 1000 centavos, recibido 1200, cambio 200,
  existencias 10 → 8. El fixture propio se eliminó después y la conciliación pasó.
  La primera ejecución usaba el código como filtro de nombre; se corrigió la prueba
  al contrato existente y la etiqueta de búsqueda de la pantalla.
- SQLite local prueba DDL 3→4→5, preservación UUID, FK, rollback de checkout,
  revisión/cuenta del borrador, cierre y recuperación tras reinicio: correcto.
- Backend Docker: `npm test` (68), `npm run check`, pruebas SQL/HTTP de ventas y
  caja (2): correctos. No se repitieron pruebas Web porque no cambió su código.
- APK de pruebas compilado; pruebas instrumentadas no ejecutadas. Falta validar
  migración Room y recorrido visual en dispositivo, reinicio real y pérdida de red.
  No se construyó release ni se desplegó en VPS.

No hay todavía paridad nativa completa: inventario, reportes, personal,
administración y funciones avanzadas de caja están disponibles en la Web oficial;
sus pantallas antiguas no se montan en el grafo Android activo. Faltan cámara,
asociación de cliente, historial nativo completo de ventas/pagos, revisión de
intentos permanentemente rechazados y portado de los demás módulos nativos.
AppCliente sigue demo. El SDK y fuentes Supabase históricos siguen presentes,
sin configuración operativa del APK; no se declara retirada total del código.

El VPS Hostinger está comprado y su preparación la atiende otra persona. No se
tocó el servidor. Continúan pendientes exportación real, importación/mapeos,
conciliación financiera, correo real y aceptación operativa antes de apagar Supabase.
El siguiente bloque recomendado es historial/detalle nativo de ventas y pagos,
incluida resolución segura de intentos permanentes, antes de portar inventario.
