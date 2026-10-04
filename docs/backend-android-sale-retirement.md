# Cierre seguro de intentos API en Android

Estado posterior, 2026-10-02: el recorrido Android principal ya usa la API.
Consultar [backend-android-workflow.md](backend-android-workflow.md) para el alcance
actual, configuración y pruebas nuevas. La evidencia siguiente conserva el
alcance original de este bloque; no acredita paridad nativa completa.

Entrega verificada el 2026-10-02. Android implementa el contrato existente de la
migración MariaDB 022: `POST /api/v1/sales/retire`. No crea otro backend, otra ruta
de navegación ni una copia de reglas financieras. El panel de ventas guardadas
ofrece «Cerrar intento» con confirmación explícita.

## Contrato y conservación

El transporte envía `{}` con la clave original, token y encabezados de cuenta y
sucursal esperadas. El servidor serializa cierre y envío con el bloqueo de la
cuenta. Si ya existe la venta, responde `COMMITTED` y Android verifica y guarda
su comprobante como `SYNCED`. Si no existe, responde `RETIRED` y bloquea cualquier
envío posterior con esa clave; solo entonces Android guarda el estado terminal
`RETIRED`. Una consulta 404 nunca autoriza un cierre local.

El cierre reclama el intento con el mismo guardado `SYNCING` usado para enviar;
una segunda acción no puede enviarlo ni cerrarlo mientras está reclamado.
Pérdida de respuesta, error, cancelación o respuesta incompatible conservan el
intento como `UNCERTAIN`; no permiten una nueva cotización. Se puede consultar o
repetir el cierre con la misma clave. Al reiniciar, los `SYNCING` interrumpidos
siguen recuperándose como `UNCERTAIN`. No hay reintentos HTTP automáticos.

La sesión debe estar vigente, con permisos consultados, `CREATE_SALES` y sucursal
activa. La cuenta/sucursal deben coincidir con las originales del intento. El
ViewModel cancela acciones al cambiar el contexto y descarta respuestas antiguas.
Si una operación ya llegó al servidor, su resultado se conserva en el historial
de su propietario, sin restaurar la pantalla después del cierre de sesión.

Los encabezados, productos, cantidades, total y clave permanecen guardados. El
historial muestra «Cerrado sin venta», sin inventar comprobante, y no ofrece
reenvío. El estado terminal deja de bloquear una nueva cotización, cuya clave
será nueva. No se borran ni convierten ventas UUID históricas.

Room permanece en versión 4: se utiliza el campo TEXT `state` ya existente y los
predicados de reclamación/liberación del DAO; no cambia el esquema ni se necesita
una migración DDL. No cambian SQL MariaDB, Supabase, backend ni Web.

## Archivos modificados

Bajo `app/src/main/java/com/intutec/viveroapp/feature/cart/`:

- `domain/repository/BackendSaleGateway.kt` y `BackendSaleEmitter.kt`.
- `data/remote/BackendSaleRemoteDataSource.kt` y `data/repository/BackendSaleEmitterRepository.kt`.
- `sync/BackendSaleOutboxStore.kt` y `BackendPendingSaleSynchronizer.kt`.
- `presentation/BackendPendingSalesViewModel.kt` y `BackendPendingSalesPanel.kt`.

Pruebas en `app/src/test/java/com/intutec/viveroapp/feature/cart/`:
`BackendPendingSaleSynchronizerTest.kt`, `BackendSaleEmitterTest.kt`,
`BackendPendingSalesViewModelTest.kt`, `BackendSaleJournalTest.kt` y
`data/remote/BackendSaleRemoteDataSourceTest.kt`. También se amplió
`app/src/test/scripts/verify_backend_outbox_sql.py`.

Documentación: este archivo, `backend-android-pending-sales.md`,
`backend-complete-cutover.md`, `supabase-migration-map.md` y `README.md`.

## Validación ejecutada

Desde la raíz de ViveroApp:

```powershell
$env:JAVA_HOME='C:/Users/GAMER/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2'
.\gradlew.bat testDebugUnitTest --tests 'com.intutec.viveroapp.feature.cart.Backend*' --tests 'com.intutec.viveroapp.feature.cart.data.remote.BackendSaleRemoteDataSourceTest'
.\gradlew.bat assembleDebug testDebugUnitTest
python app/src/test/scripts/verify_backend_outbox_sql.py
git diff --check
```

Resultado: APK debug compilado y **296 pruebas unitarias correctas**, 16 nuevas.
Se verifican comprobante existente, clave conservada, respuesta perdida,
cancelación, cuenta/sucursal ajenas, doble acción, error incompatible, historial
terminal y desbloqueo de la cotización. SQLite ejecuta los predicados reales del
DAO, confirma que un intento cerrado no se reclama ni se reactiva al reiniciar y
preserva las filas UUID anteriores.

También se ejecutó en el entorno Docker local de pruebas ya existente:

```powershell
docker compose --project-name vivero-cutover-20261001 --env-file tmp/cutover-validation-20261001.env -f infra/docker/compose.yaml -f tmp/cutover-networks.yaml --profile test run --rm --no-deps tests node --test test/sales-integration.test.js
```

Una prueba HTTP/SQL correcta, con datos sintéticos y limpieza de sus propias
filas: comprobante, cierre repetido, envío tardío bloqueado y carrera cierre/envío.
Los archivos privados de `tmp/` pertenecen al ensayo local, están ignorados y no
son requisitos de un clon. Para otro entorno local, usar su `.env` y el Compose
oficial, sin copiar estas credenciales. No se tocaron bases remotas.

## Límites y siguiente integración

No se ejecutó UI en dispositivo ni pruebas instrumentadas; no se inició
emulador. Tampoco se repitieron suites Web o todos los módulos backend: sus
fuentes no cambiaron. La prueba de contrato HTTP/SQL no equivale a ejecutar el
cliente Kotlin contra HTTP en un dispositivo.

Este bloque **no activa login, catálogo ni carrito operativo API**. El panel
sigue condicionado a una sesión API y no construye el consumidor con la sesión
Supabase actual. El requisito de retiro que faltaba en Android queda resuelto;
la siguiente integración debe conectar sesión, catálogo y carrito a las
identidades enteras y preservar los pendientes UUID. Los módulos restantes no
pueden recibir identidades enteras convertidas a UUID ni usar fallback automático.
No hay importación real, VPS, commit, push ni despliegue.
