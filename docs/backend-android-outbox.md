# Persistencia de intentos de ventas API en Android

Estado posterior, 2026-10-02: el recorrido Android principal ya usa la API.
Consultar [backend-android-workflow.md](backend-android-workflow.md) para el alcance
actual, configuración y pruebas nuevas. La evidencia siguiente conserva el
alcance original de este bloque; no acredita paridad nativa completa.

Bloque del 2026-10-01. Se incorpora almacenamiento oficial de intentos API en
la misma base `vivero.db`, versión Room 4, con migración explícita 3→4. La UI y
el sincronizador de ventas Supabase continúan operativos con su contrato anterior.
No se convirtió, movió ni reenviará automáticamente ninguna venta UUID previa.

## Archivos de esta entrega

Rutas Kotlin relativas a `app/src/main/java/com/intutec/viveroapp/`:

- `core/database/ViveroDatabase.kt`: versión 4, entidades/DAO, migración aditiva y recuperación de intentos API interrumpidos.
- `core/di/DatabaseModule.kt`: registro de migración, DAO y recuperación al abrir.
- `core/di/BackendNetworkModule.kt`: enlace Hilt de BackendSaleOutboxStore.
- `feature/cart/data/local/BackendSaleAttemptDao.kt`: entidades, inserción transaccional y reclamación condicional.
- `feature/cart/sync/BackendSaleOutboxStore.kt`: persistencia/restauración del intento original.
- `feature/cart/sync/BackendPendingSaleSynchronizer.kt`: envío y recuperación explícitos, sin activación en UI o tarea automática.
- `app/src/test/java/com/intutec/viveroapp/feature/cart/BackendPendingSaleSynchronizerTest.kt`: ocho pruebas nuevas.
- `app/src/test/scripts/verify_backend_outbox_sql.py`: comprobación reproducible SQLite local contra SQL generado por Room.
- `app/src/androidTest/java/com/intutec/viveroapp/feature/cart/ViveroDatabaseV2Test.kt`: cadena de migraciones existente extendida a versión 4.
- Este documento y `docs/supabase-migration-map.md`.

## Contrato de conservación

`backend_sale_attempts` usa PK INTEGER AUTOINCREMENT y una clave única de 64 hex.
Conserva usuario/sucursal enteros, total esperado, estado y comprobante confirmado
(ID remoto, folio y estado servidor). `backend_sale_attempt_items` conserva los
productos/cantidades con PK compuesta y FK real al intento; DELETE RESTRICT.
Esta relación uno a muchos requiere detalle separado. No se crean usuarios o
productos locales ficticios para referenciar los IDs remotos. El intento no guarda
tokens o contraseñas. No hay índices avanzados ni borrado automático de evidencia.

Las tablas anteriores de ventas, carrito y cobros Supabase permanecen intactas.
Los dos contratos de identidad son incompatibles durante la transición: no se
sobrecargan UUID existentes con números ni se introduce otro backend. El destino
de las nuevas tablas es exclusivamente la API oficial; PendingSaleSynchronizer
existente solo lee las tablas anteriores.

El alta de encabezado/items es transaccional. Una clave repetida falla y no
reemplaza un intento previo. El emisor futuro debe conservar el intento creado
y el ID local devuelto por enqueue; nunca generar otra clave por un error de
respuesta. No existe conversión automática de pendientes de Supabase.

Solo PENDING o UNCERTAIN se pueden reclamar como SYNCING; la actualización
condicional impide dos envíos simultáneos. Debe coincidir la cuenta/sucursal
original, sesión vigente, contexto READY y CREATE_SALES. Se revalida la sesión
después de reclamar y antes de reenviar tras una recuperación sin resultado.
Una solicitud ya enviada puede terminar aunque el cierre local ocurra después;
su comprobante se guarda para evitar duplicarla. El servidor verifica identidad
y permisos en cada operación.

La primera petición usa submit. Ante error, cancelación o reinicio se conserva
UNCERTAIN. En el siguiente intento se consulta recover con la misma clave: un
resultado confirmado se persiste sin reenviar; únicamente 404 SALE_NOT_FOUND
permite submit con el payload original. Otros errores no autorizan otra escritura.
No se cambian productos, total o clave ni se borra el intento ante errores
permanentes. Por ello un cambio de precio o rechazo requiere resolución visible
antes de activar el emisor; no hay bucle automático de reintentos. Tampoco se
transfiere un intento a otra cuenta/sucursal.

## Verificación de este bloque

```powershell
$env:JAVA_HOME='C:/Users/GAMER/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2'
.\gradlew.bat testDebugUnitTest --tests 'com.intutec.viveroapp.feature.cart.BackendPendingSaleSynchronizerTest'
python app/src/test/scripts/verify_backend_outbox_sql.py
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest
git diff --check
```

El test específico inicial pasó con siete casos; se añadió después el octavo
caso de cierre durante la reclamación y se ejecutó la suite completa. APK debug
y APK instrumentado compilaron; 263 pruebas unitarias pasaron, cero fallos,
errores u omitidas. SQLite local verificó preservación de ventas/items sintéticos
de esquema v3, coincidencia de columnas/FK/índices con Room generado, claves
únicas, FK, reclamación exclusiva y recuperación sin alterar payload.

No se ejecutaron pruebas instrumentadas: requieren dispositivo/emulador, que no
se inició. Compilar su APK no confirma ejecución ni migración en Android. SQLite
de Python complementa esa prueba, sin sustituirla. No hubo HTTP real, Docker,
cambios MariaDB/Supabase SQL, Web o dependencias. La migración SQL es local Room;
no aplica pgTAP remoto. JDK existente y caché Gradle usados con ejecución autorizada
fuera del sandbox; no se instaló Java ni se cambió su configuración global.
También se ejecutó `powershell.exe -NoProfile -ExecutionPolicy Bypass -File
supabase/tests/verify_migrations.ps1`: 134 comprobaciones estáticas correctas;
las migraciones Supabase no se modificaron.

Se conservaron los cambios anteriores; sin commit, push ni despliegue.
Siguiente trabajo: probar migración Room en dispositivo autorizado y conectar
sesión/catálogo/carrito API al emisor con restauración y resolución de pendientes,
coordinando Caja y pedidos Web en el mismo destino. Este bloque no constituye
corte de las pantallas ni finalización de toda la migración.
