# Inventario Android con Backend API y MariaDB

Estado verificado localmente: 2026-10-02. El grafo oficial Android incorpora
inventario nativo: saldos y mínimos por sucursal, recepción, conteo físico e
historial paginado por producto. Usa la misma API y MariaDB de Web. No se modificó
el esquema MariaDB ni se creó un backend alternativo.

## Contratos y permisos

- Lecturas: sesión vigente, sucursal activa y `MANAGE_INVENTORY` o
  `VIEW_INVENTORY_ALERTS`. Escrituras y recuperación: `MANAGE_INVENTORY`.
- Los saldos DECIMAL(14,3) se validan y representan como milésimas `Long`, sin
  `Double`. Recepciones y conteos aceptan unidades enteras según el contrato
  existente. Recepción positiva; conteo permite cero y requiere motivo.
- El conteo registra la diferencia mediante el servidor y sus triggers. No se
  edita el saldo directamente. Un comprobante recuperado de conteo describe la
  cantidad registrada entonces; el dashboard se vuelve a consultar para mostrar
  el saldo actual. No activa automáticamente el control de inventario de ventas.
- API existente: GET `/api/v1/inventory/dashboard` y `/history`, POST
  `/receptions` y `/counts`. Se añaden POST `/receptions/result` y `/counts/result`:
  consultas autenticadas sin crear movimientos, con cuerpo y clave originales.
  Recuperan operaciones confirmadas aun si después se desactiva el producto.
- Las cabeceras opcionales `X-Expected-Actor-Id` y `X-Expected-Branch-Id` se
  comprueban antes de escribir o recuperar. El servidor verifica también
  propiedad, producto, cantidad y notas/motivo del intento.

## Persistencia y recuperación

Room pasa de 5 a 6 con migración aditiva. `backend_inventory_attempts` conserva
actor, sucursal, producto, operación, cantidad, notas y clave antes de HTTP.
Usa PK entera autoincremental y clave única; no referencia mediante FK IDs
remotos que no tienen tabla local. Las FK reales del inventario permanecen en
MariaDB. Las tablas UUID y los borradores/intentos API anteriores se preservan.

Un intento pendiente bloquea otro movimiento para esa cuenta/sucursal. Al abrir
Room se recupera `SYNCING` como `UNCERTAIN`. Consultar resultado nunca reenvía.
El reintento explícito consulta primero y solo vuelve a enviar el mismo cuerpo y
clave ante `404 INVENTORY_OPERATION_NOT_FOUND`. Otros errores conservan el
intento. Cambio de cuenta, sucursal o permisos cancela consultas y descarta sus
respuestas tardías; los diálogos se cierran al cambiar la identidad.

**Límite operativo:** no hay retiro administrativo de claves de inventario en
este bloque. Un rechazo permanente sin resultado confirmado sigue pendiente;
no se elimina ni se sustituye su clave. Su resolución segura exige un bloqueo
servidor del intento original antes de habilitar otra operación. Activación de
stock y edición de mínimos continúan disponibles mediante Web.

## Validaciones nuevas

- `gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest`:
  APK debug y de pruebas compilados; 344 casos, 343 pasan y un HTTP opcional
  omitido sin fixture. Sin errores ni fallos.
- HTTP Kotlin opcional ejecutado aparte contra Docker local: login, catálogo,
  recepción 10→12, recuperación sin repetir, conteo 12→10, historial/dashboard,
  venta y cobro 10→8. Conciliación SQL de venta/pago/stock PASS; fixture privado
  y filas sintéticas retirados, limpieza PASS.
- Backend Docker: 68 pruebas unitarias y `npm run check` correctos. Suite SQL/HTTP
  de inventario correcta: recuperación con producto inactivo, conflicto de
  cuerpo, identidad incorrecta, ausencia sin creación y saldo sin modificación;
  mantiene pruebas de repetición, conteo y recepciones concurrentes.
- `python app/src/test/scripts/verify_backend_outbox_sql.py`: esquemas Room
  3→4→5→6, preservación UUID y pagos anteriores, FK, checkout atómico y
  recuperación/reclamación/finalización de intentos de inventario correctos.

La prueba HTTP usa exclusivamente el proyecto sintético
`vivero-cutover-20261001`, API `http://127.0.0.1:33003`; las credenciales aleatorias
se redirigen a `tmp/android-http-fixture.json` ignorado, nunca se versionan.
El auxiliar `backend/test/android-workflow-fixture.js` elimina conteos antes de
movimientos para respetar las FK. El destino debe confirmarse antes de repetir.

No se ejecutaron pruebas instrumentadas ni cámara en dispositivo, release,
Supabase o VPS. El APK instrumentado compilado no acredita ejecución de Room en
un dispositivo. No cambió SQL PostgreSQL ni MariaDB; no corresponde pgTAP a este
cambio. No se repitieron pruebas Web/Cliente: sus fuentes no cambiaron. La
exportación/importación de datos reales sigue pendiente.

## Archivos de este bloque

Rutas Kotlin relativas a `app/src/main/java/com/intutec/viveroapp/`:

- Nuevos: `feature/inventory/domain/repository/BackendInventoryGateway.kt`,
  `data/remote/BackendInventoryRemoteDataSource.kt`,
  `data/local/BackendInventoryAttemptDao.kt`,
  `data/repository/BackendRoomInventoryOperations.kt`,
  `presentation/BackendInventoryViewModel.kt` y `BackendInventoryScreen.kt`.
- Integración: `core/database/ViveroDatabase.kt`,
  `core/di/{DatabaseModule,BackendNetworkModule}.kt`, `navigation/ViveroApp.kt`.
- Pruebas: `app/src/test/java/com/intutec/viveroapp/feature/inventory/`
  (`BackendInventoryTest.kt`, `BackendInventoryViewModelTest.kt`), HTTP Kotlin
  del carrito, `verify_backend_outbox_sql.py` y `ViveroDatabaseV2Test.kt`.
- Backend: `backend/src/{app,inventory}.js`,
  `backend/test/{inventory-integration.test,android-workflow-fixture}.js`.
- Documentación: esta guía, README, mapa de migración, estado de corte y notas
  de vigencia en las guías Android de historial y recorrido.

Se preservaron cambios preexistentes y los consumidores históricos de Supabase.
No hubo commit, push ni despliegue.

Siguiente bloque recomendado: cámara/escaneo y asociación de cliente en ventas
Android. Después: consumidores nativos restantes, aceptación en dispositivo,
importación conciliada de datos reales, correo y preparación del corte/VPS.
No se declara terminada la migración ni se autoriza apagar Supabase.
