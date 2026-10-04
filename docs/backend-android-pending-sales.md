# Consulta y presentación de intentos API Android

Estado posterior, 2026-10-02: el recorrido Android principal ya usa la API.
Consultar [backend-android-workflow.md](backend-android-workflow.md) para el alcance
actual, configuración y pruebas nuevas. La evidencia siguiente conserva el
alcance original de este bloque; no acredita paridad nativa completa.

Actualización del 2026-10-02: ya se implementó el cierre transaccional de intentos
en Android, conectado al contrato MariaDB 022. El panel ofrece confirmación de
cierre, conserva ventas existentes y deja los errores como inciertos. Evidencia
actual: APK debug y 296 pruebas unitarias, verificación SQLite y una prueba
HTTP/SQL Docker correctos. [Contrato y límites](backend-android-sale-retirement.md).
El login operativo sigue Supabase. Las secciones siguientes documentan la entrega
anterior; su pendiente de retiro queda resuelto por esta actualización.

Bloque del 2026-10-01. El carrito existente incluye un panel condicionado a una
sesión API válida con CREATE_SALES, contexto READY y sucursal activa. No se crea
ruta nueva. Con la sesión Supabase actual el panel permanece oculto y su Provider
Hilt no construye el emisor/transporte API. Login, catálogo, carrito de productos
UUID y envío operativo siguen Supabase.

## Archivos de esta entrega

Rutas relativas a `app/src/main/java/com/intutec/viveroapp/`:

- `feature/cart/presentation/CartScreen.kt`: inclusión del panel en la pantalla existente.
- `feature/cart/presentation/BackendPendingSalesPanel.kt`: listado/consulta y confirmación explícita de reintento.
- `feature/cart/presentation/BackendPendingSalesViewModel.kt`: StateFlow encapsulado, carga/vacío/error/operación, invalidación ante cambio de contexto o cierre y vencimiento mientras vive el ViewModel.
- `feature/cart/domain/repository/BackendSaleEmitter.kt`: modelos de entrada de historial/comprobante local y métodos journal/checkResult.
- `feature/cart/data/repository/BackendSaleEmitterRepository.kt`: scope de sesión y consulta sin envío.
- `feature/cart/data/local/BackendSaleAttemptDao.kt`: lectura de últimos 100 encabezados/items por cuenta y sucursal.
- `feature/cart/sync/BackendSaleOutboxStore.kt`: verificación del comprobante local antes de presentarlo.
- `feature/cart/sync/BackendPendingSaleSynchronizer.kt`: modo recoveryOnly; 404 conserva el intento sin submit.
- Pruebas `BackendSaleEmitterTest.kt`, `BackendPendingSalesViewModelTest.kt`, `BackendSaleJournalTest.kt` en `app/src/test/java/com/intutec/viveroapp/feature/cart/`.
- Este documento y `docs/supabase-migration-map.md`.

## Comportamiento y límites

El listado muestra hasta 100 intentos locales recientes de la cuenta/sucursal
actual, cantidad, total esperado, estado, último error seguro y comprobante
confirmado (ID remoto, folio y estado). No expone clave de idempotencia ni token.
El estado del comprobante es el último confirmado; no es una consulta en vivo del
pago/entrega. No se presenta un comprobante con campos incompletos o incompatibles.

Consultar resultado invoca exclusivamente recover: nunca submit. Si existe venta,
se guarda como SYNCED; si no existe o hay error se conserva UNCERTAIN. Reintentar
envío exige confirmación visible y usa resume con payload/clave originales; si
es incierto, recupera primero y solo SALE_NOT_FOUND autoriza reenviar esa clave.
SYNCING no ofrece otra acción mientras el envío esté reclamado. Al reiniciar se
recupera como ya se documentó en [backend-android-outbox.md](backend-android-outbox.md).

Las acciones duplicadas se ignoran durante carga/operación. Cambio/cierre de
sesión cancela consultas y vacía datos; respuestas de generaciones anteriores no
restauran la lista. Errores inesperados tienen mensajes propios sin cuerpo remoto.
La UI solo representa estado/emite eventos: reglas y persistencia permanecen en
repositorio/sincronizador/Room.

### Rechazos conservados, sin descarte inseguro

Un cambio de precio u otro rechazo permanente conserva el intento y bloquea
nuevas ventas según el emisor. Este bloque permite consultar/reintentar; no
implementa borrar, descartar o recotizar reemplazando una venta incierta.
Un recover 404 es una observación temporal: una petición previa aún podría
confirmarse. Para desbloquear con seguridad hace falta cerrar la clave en servidor
de forma transaccional, serializada con submit, y que cualquier envío tardío de
esa clave sea rechazado. El backend actual no ofrece ese contrato; no se infiere
del 404 ni se suplanta mediante reglas de cliente. Esa resolución definitiva es
el siguiente bloque antes de activar el emisor en el botón operativo.

## Validación ejecutada

```powershell
$env:JAVA_HOME='C:/Users/GAMER/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2'
.\gradlew.bat testDebugUnitTest --tests 'com.intutec.viveroapp.feature.cart.BackendSaleEmitterTest' --tests 'com.intutec.viveroapp.feature.cart.BackendPendingSalesViewModelTest'
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest
python app/src/test/scripts/verify_backend_outbox_sql.py
git diff --check
```

Resultado: 280 pruebas unitarias correctas, incluidas nueve nuevas (dos de consulta
del emisor, cinco del ViewModel y dos de lectura/verificación del comprobante).
APK debug e instrumentado compilan. SQLite local sigue verificando esquema,
preservación, FK/unicidad/reclamación y recuperación. Room permanece en versión 4:
no cambió DDL, SQL MariaDB/Supabase, backend, Web o dependencias.

No se ejecutó UI en dispositivo ni pruebas instrumentadas; no se inició emulador
o Docker ni se hizo HTTP real. Compilación del APK instrumentado no equivale a
ejecución. Falta validar visualmente el panel al activar sesión API y comprobar
el recorrido completo. JDK instalado y caché Gradle usados con autorización;
sin instalación ni configuración global de Java.

Se conservaron los cambios preexistentes. Sin commit, push ni despliegue. Esta
integración condicionada no declara migrados login, catálogo, carrito o Caja.
