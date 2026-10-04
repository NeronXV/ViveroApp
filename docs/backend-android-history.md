# Historial Android y cierre seguro de pagos

Estado posterior: inventario nativo integrado con API/MariaDB y Room 6.
[Alcance y evidencia nuevos](backend-android-inventory.md). Este documento
conserva los conteos y pendientes históricos de su bloque.

Entrega local del 2026-10-02, posterior al
[recorrido principal Android](backend-android-workflow.md). Android añade «Mis
ventas», «Mis comprobantes» desde Caja y detalle de comandas pendientes, usando
los contratos de la API oficial. No cambia Web ni ejecuta operaciones remotas.

## Lecturas y permisos

- Mis ventas: `GET /api/v1/sales` y `/sales/:id`, con `CREATE_SALES` y
  `VIEW_OWN_SALES`, cuenta propia y sucursal activa. Muestra partidas, importes
  guardados y eventos del servidor.
- Mis comprobantes: `GET /api/v1/cashier/receipts` y `/receipts/:paymentId`, con
  `OPERATE_CASHIER`, cajero propio y sucursal activa. Incluye recibido, cambio,
  referencia y devolución existente, sin recalcular con el catálogo.
- Ver comanda: `GET /api/v1/cashier/sales/:id`, con permiso de Caja en su sucursal.
  Esta lectura no reserva ni cobra la comanda.

Listas paginadas de 20, IDs descendentes y cursor comprobado; líneas ordenadas
por ID. El consumidor comprueba identidad, esquema, sumas exactas, cambio y
cantidades decimales de hasta tres posiciones. Código/precio de lista históricos
nulos siguen nulos. No inventa imágenes, promociones ni datos ausentes.
La pantalla distingue carga, vacío y error y vuelve al listado desde el detalle.
Cambio de cuenta, pérdida de permisos o logout eliminan los documentos visibles;
una respuesta tardía no puede publicarlos en otra sesión.

## Cierre seguro de intentos de cobro

Nueva migración `026_payment_attempt_retirement`: tabla
`payment_attempt_retirements`, PK entera autoincremental, FKs reales a usuario,
sucursal y venta, hash único de clave y fecha. Runtime obtiene solo SELECT/INSERT;
no puede borrar ni alterar una clave bloqueada. Health exige la migración 026.
Es una actualización no destructiva del mismo backend, sin UUID ni índices avanzados.

`POST /api/v1/cashier/sales/:saleId/payment-retire`, cuerpo `{}`, clave original
en `Idempotency-Key`, Bearer y cuenta/sucursal esperadas. Requiere permiso de
Caja y sucursal activa. Usa los mismos bloqueos de usuario y venta que el cobro:

| Resultado | Servidor | Android |
|---|---|---|
| `COMMITTED` | Devuelve el pago existente de esa clave/cajero/venta | Valida y conserva comprobante como `SUCCEEDED` |
| `RETIRED` | Inserta/reutiliza el bloqueo de la clave sin registrar un pago | Guarda `RETIRED` y conserva cuerpo/clave del intento |
| Error o respuesta perdida | No hay confirmación para el cliente | Conserva `UNCERTAIN`; no desbloquea otro cobro |

Si cierre y POST tardío compiten, uno se completa primero: existe un solo pago
confirmado o la clave queda bloqueada. Un POST con clave retirada devuelve
`409 PAYMENT_ATTEMPT_RETIRED`. Una clave de otra venta/sucursal no se cierra como
si fuera la actual. El cierre no cancela venta, libera reservas, devuelve dinero
ni toca inventario. La reserva conserva su expiración normal. Una nueva operación
es posible solo tras el cierre confirmado, con cotización/importe actual del servidor.

La acción «Cerrar intento» exige confirmación explícita. Un 404 de recuperación
no equivale a cierre. Repetir el cierre usa la clave original y recupera el mismo
resultado. Comprobantes de ventas ya entregadas también se conservan.
Room sigue en versión 5: el esquema TEXT ya admite el nuevo estado terminal;
solo cambia una consulta condicional, sin borrar filas ni convertir datos UUID.
Los intentos cerrados permanecen en Room; el historial de comprobantes remoto
muestra pagos efectivos, no claves cerradas sin pago.

## Actualizar y probar

Desde ViveroApp, con la configuración local de Docker preparada, comprobar que
el destino sea local antes de ejecutar:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest
python app/src/test/scripts/verify_backend_outbox_sql.py
```

No borrar volúmenes para actualizar. Esta sesión usó el entorno sintético existente
`vivero-cutover-20261001`, puerto 33003, y añadió únicamente la tabla 026 al volumen.
El esquema local actualizado tiene 45 tablas y 25 marcadores de migración (002–026).

Validaciones nuevas:

- APK debug y APK de pruebas compilados; suite de 331 casos: 330 pasan, cero
  fallos/errores y un HTTP opcional omitido sin fixture.
- HTTP Kotlin ejecutado aparte contra MariaDB: historial propio, detalle de
  comanda, comprobante, consulta/recuperación/cierre de pago, además de login,
  catálogo y venta. Conciliación SQL: una venta de 1000 centavos, cambio 200,
  inventario 10→8. Fixture y claves sintéticas eliminados después; limpieza PASS.
- Backend Docker: 68 pruebas unitarias, `check` y 25 SQL/HTTP correctos. Cobro y
  cierre concurrentes, repetición, claves retiradas, otro cajero/sucursal,
  preservación de pago y rollback verificados. Se añadió y repitió la comprobación
  de que runtime no puede UPDATE/DELETE en la tabla de bloqueos.
- SQLite: migración 3→4→5, preservación UUID, checkout atómico y transición de
  pago `SYNCING→RETIRED` con cuerpo/clave conservados correctos.
- Pruebas de ViewModel verifican permisos y respuesta tardía entre cuentas. La
  lectura de ruta usa navegación tipada en producción; la prueba JVM recibe la
  ruta ya decodificada para no simular `android.os.Bundle`.

No se ejecutaron pruebas instrumentadas en dispositivo, cámara, release, Supabase
ni VPS. No cambió SQL PostgreSQL; pgTAP no valida esta migración MariaDB. No se
repitieron pruebas Web, cuyo código no cambió. No hay importación real nueva.

## Archivos de este bloque

Rutas Kotlin relativas a `app/src/main/java/com/intutec/viveroapp/`:

- Nuevos: `feature/mysales/domain/repository/BackendHistoryGateway.kt`,
  `data/remote/BackendHistoryRemoteDataSource.kt`,
  `presentation/BackendHistoryViewModel.kt` y `BackendHistoryScreen.kt`.
- Modificados: `navigation/{Routes,ViveroApp}.kt`, `core/di/BackendNetworkModule.kt`,
  y gateway, DAO, fuente remota, repositorio, ViewModel y pantalla API de Caja.
- Pruebas Android: nuevo `feature/mysales/BackendHistoryTest.kt`; pruebas de
  pagos/contrato de Caja, HTTP Kotlin y `verify_backend_outbox_sql.py` ampliados.
- Backend: `src/{app,cashier}.js`, `test/{cashier-integration.test,integration.test,
  android-workflow-fixture}.js`, `scripts/verify-local-install.js` y migración 026.
- Documentación: esta guía, README, mapa de transición, estado de corte y notas
  de vigencia en guías del recorrido Android y comprobantes.

Siguiente bloque: inventario nativo (consulta, recepción, conteo e historial) con
la API existente. Continúan pendientes cámara, asociación de cliente, personal,
reportes/administración nativos, AppCliente, datos reales, correo y aceptación
operativa del corte. No apagar Supabase antes de conciliar datos e intentos UUID
anteriores. No hubo commit, push ni despliegue; se preservó el trabajo previo.
