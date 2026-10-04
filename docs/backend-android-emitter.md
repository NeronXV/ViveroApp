# Emisor de ventas API Android preparado

Estado posterior, 2026-10-02: el recorrido Android principal ya usa la API.
Consultar [backend-android-workflow.md](backend-android-workflow.md) para el alcance
actual, configuración y pruebas nuevas. La evidencia siguiente conserva el
alcance original de este bloque; no acredita paridad nativa completa.

Bloque del 2026-10-01. Conecta cotización API, persistencia Room y sincronizador
API en el contrato BackendSaleEmitter, dentro de las capas existentes. No se
activa el botón del carrito: productos, clientes y sesión visibles siguen usando
UUID de Supabase. No se convierten esas identidades ni se crea otra ruta/backend.

## Archivos de esta entrega

Rutas relativas a `app/src/main/java/com/intutec/viveroapp/`:

- `feature/cart/domain/repository/BackendSaleEmitter.kt`: contrato, confirmación preparada y resultados independientes de Android/Room/Supabase.
- `feature/cart/data/repository/BackendSaleEmitterRepository.kt`: implementación con sesión verificada, cotización, almacenamiento, envío y restauración.
- `feature/cart/data/local/BackendSaleAttemptDao.kt`: consulta de pendientes incluye SYNCING para bloquear una venta nueva mientras otro envío está en curso.
- `core/di/BackendNetworkModule.kt`: enlace singleton Hilt al emisor oficial.
- `app/src/test/java/com/intutec/viveroapp/feature/cart/BackendSaleEmitterTest.kt`: ocho pruebas nuevas.
- Este documento y `docs/supabase-migration-map.md`.

## Uso previsto desde los casos de uso/presentación

1. `pending()` obtiene hasta 100 IDs locales de la cuenta/sucursal actual. PENDING,
   UNCERTAIN y SYNCING bloquean preparar otra venta. SYNCING no se puede reclamar
   dos veces; al reiniciar Room se recupera a UNCERTAIN según el bloque anterior.
2. `quote(items)` copia/valida IDs enteros y cantidades, consulta el precio del
   servidor y devuelve una confirmación PreparedBackendSale con colección de
   cotización inmutable. La clave original se genera al preparar esta confirmación.
   No guarda nada hasta que el usuario confirme. No contiene token de sesión.
3. `send(prepared)` persiste el intento y relee clave, cuenta/sucursal, items y total
   antes de llamar al sincronizador. Si falla almacenamiento o lectura no envía.
   Volver a enviar la misma confirmación conserva su ID local y clave; si ya se
   confirmó devuelve AlreadyClaimed sin crear otra venta.
4. Tras reinicio, `resume(id)` carga el intento de Room y usa su payload original.
   UNCERTAIN se recupera antes de reenviar, como se explica en
   [backend-android-outbox.md](backend-android-outbox.md).

El singleton serializa cotización/envío/restauración dentro del proceso y rechaza
operaciones simultáneas. La reclamación DAO sigue siendo condicional. La garantía
de bloqueo de ventas nuevas exige que la futura UI use este emisor como única
entrada; no se declara exclusión entre procesos o inserciones directas al DAO.
No se agrega un scheduler ni reintentos automáticos.

Se verifica sesión vigente, contexto READY, CREATE_SALES y sucursal activa; una
respuesta tardía de cotización no se acepta tras cambio/cierre de sesión. Si se
cierra después de persistir, el intento permanece para recuperarlo. Solo la
transacción local de alta y asignación de ID usa NonCancellable; la llamada HTTP
permanece cancelable. La cancelación tras enviar conserva UNCERTAIN.

No se calcula precio, stock o permisos sensibles en cliente. El servidor vuelve
a autorizar y validar total en submit. Este emisor no soporta clientes UUID,
descuentos manuales ni mezclar el carrito anterior con productos enteros.

## Validaciones ejecutadas

```powershell
$env:JAVA_HOME='C:/Users/GAMER/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2'
.\gradlew.bat testDebugUnitTest --tests 'com.intutec.viveroapp.feature.cart.BackendSaleEmitterTest'
.\gradlew.bat assembleDebug testDebugUnitTest
python app/src/test/scripts/verify_backend_outbox_sql.py
git diff --check
```

Ocho pruebas nuevas correctas: cotización/alta antes de petición y doble envío,
fallo de almacenamiento/lectura, respuesta perdida/restauración sin segunda
venta, cierre durante cotización o alta, aislamiento por cuenta, concurrencia,
lista mutable del llamador y cancelación. APK debug y 271 pruebas totales pasaron,
sin fallos, errores u omitidas. Verificación SQLite local correcta; versión Room
continúa en 4, sin DDL nuevo. Las pruebas usan transporte/almacén sintéticos y
no constituyen HTTP real, persistencia Room en dispositivo o UI activa.

No se iniciaron Docker, emulador o dispositivo; no se ejecutaron instrumentadas.
No cambió SQL MariaDB/Supabase, dependencias ni Web. JDK existente usado solo para
el proceso Gradle con ejecución autorizada de su caché; sin configuración global.
Se conservaron los cambios preexistentes. Sin commit, push ni despliegue.

## Pendiente antes de activar

Presentar/restaurar intentos con errores y su comprobante, y resolver rechazos
permanentes (por ejemplo cambio de precio) sin reemplazar silenciosamente una
venta incierta. Ahora se conserva el intento y se bloquea una venta nueva; no se
agregó descarte automático. Después conectar sesión, catálogo y carrito en las
pantallas existentes con el mismo destino que Caja/pedidos Web y verificar el
recorrido HTTP completo y la migración Room en dispositivo autorizado. Este
bloque no declara consumidores visibles migrados ni migración finalizada.
