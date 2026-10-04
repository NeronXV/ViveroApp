# Contrato Android de ventas hacia Backend API

Estado posterior, 2026-10-02: el recorrido Android principal ya usa la API.
Consultar [backend-android-workflow.md](backend-android-workflow.md) para el alcance
actual, configuración y pruebas nuevas. La evidencia siguiente conserva el
alcance original de este bloque; no acredita paridad nativa completa.

Implementado el 2026-10-01. El consumidor está preparado y enlazado por Hilt, pero no está activado en CartViewModel ni PendingSaleSynchronizer. La app, su sesión, catálogo y outbox siguen Supabase. No se convirtió ningún UUID ni se alteró ninguna venta local.

## Archivos y contrato

- `core/network/BackendApiTransport.kt`: transporte oficial Ktor/OkHttp, usando dependencias existentes. Bearer opaco, JSON, timeout y rechazo de redirecciones. Sin registros de tokens, credenciales administrativas ni reintentos automáticos de conexión.
- `core/di/BackendNetworkModule.kt`: cliente, origen, transporte y BackendSaleGateway construidos por Hilt. Sin singleton manual. El origen se valida cuando se solicita el nuevo consumidor; no se solicita desde las pantallas actuales.
- `feature/cart/domain/repository/BackendSaleGateway.kt`: IDs Long positivos hasta INT UNSIGNED, centavos Long hasta el máximo seguro compartido con API/Web, 1–25 productos únicos y cantidades enteras 1–100000. Intento con clave aleatoria de 32 bytes/64 hex y copia de partidas que no puede modificarse. `restore` conserva la clave original.
- `feature/cart/data/remote/BackendSaleRemoteDataSource.kt`: cotizar `/api/v1/sales/quote`, enviar `/api/v1/sales` y recuperar `/api/v1/sales/recover`. Envío únicamente productos/cantidades y total esperado; no precio unitario, sucursal ni usuario en el cuerpo. Usuario/sucursal esperados viajan en los encabezados ya admitidos por la API para rechazar cambios de identidad antes de escribir.

Las respuestas se validan estrictamente: claves exactas, tipos numéricos sin strings, cantidades/productos originales, identidad, IDs, folio, fechas UTC, importes y sumas con BigInteger. No se inventan IDs ni se calculan precios autoritativos en Android. Un fallo de red, 5xx o recibo incompatible tras enviar marca resultado incierto. Un conflicto de precio solicita nueva cotización; no se reintenta con otra clave automáticamente. Una cancelación de coroutine se propaga: el futuro outbox debe conservar el intento antes de enviar y también al cancelar.

`BackendSaleAttempt` no es almacenamiento durable. Antes de activar el consumidor hace falta migrar Room y su outbox con versión/migración explícita, persistiendo clave, autoridad, creador/sucursal, productos enteros y total; solo marcar sincronizado tras validar el recibo. No enlazar BackendSaleGateway al SaleSyncRemoteDataSource actual: su contrato y registros son UUID/Supabase. No enviar pendientes antiguos a MariaDB. El servidor todavía no acepta customer_id en este contrato; no perder esa relación sin resolverla antes del corte.

## Configuración pública

Agregar localmente a `local.properties` (ignorado):

```properties
# Ejemplo sintético de emulador Android; ajustar al puerto de Compose local.
BACKEND_API_URL=http://10.0.2.2:3001
# Para VPS futuro, usar el origen HTTPS público, sin /api/v1 ni credenciales.
# BACKEND_API_URL=https://api.example.invalid
```

Se añadió BACKEND_API_URL a BuildConfig; no se inspeccionó ni modificó el archivo local con credenciales. Se admiten HTTPS y, solamente en debug, HTTP a localhost/127.0.0.1/10.0.2.2. `app/src/debug/res/xml/backend_network_security.xml` permite cleartext únicamente para esos tres hosts, con base denegada. El manifiesto principal/release no cambia. En teléfono físico, 10.0.2.2 no corresponde al host; todavía falta configurar y probar acceso al entorno previsto.

No se añadieron versiones, dependencias ni cambios al lockfile. No se inició emulador/dispositivo/Docker, no se hicieron peticiones remotas ni pruebas instrumentadas. Una URL válida no demuestra conectividad ni autenticación: eso se verificará al conectar sesión/catálogo y ejecutar el recorrido contra API local.

## Validación nueva

- `compileDebugKotlin`: correcto.
- `testDebugUnitTest --tests com.intutec.viveroapp.feature.cart.data.remote.BackendSaleRemoteDataSourceTest`: 7 pruebas correctas con transporte falso; valida solicitudes, identidad/clave, recibos erróneos, precio cambiado, cancelación, copia inmutable, origen y cotización.
- `assembleDebug testDebugUnitTest`: correcto: 233 pruebas unitarias, sin fallos, errores ni omitidas (incluyen las 7 nuevas).
- `git diff --check`: correcto.

El primer intento no encontró JAVA_HOME/java. El JBR de Android Studio no tenía jvm.cfg. Se usó el JDK 21 ya instalado en la caché de Gradle; después el sandbox bloqueó el archivo .lck de Gradle. La ejecución autorizada fuera del sandbox pasó. No se instaló Java ni se cambió configuración global. Comando ejecutado en PowerShell:

```powershell
$env:JAVA_HOME='C:/Users/GAMER/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2'
.\gradlew.bat assembleDebug testDebugUnitTest
```

## Próximo paso

Preparar sesión Backend API y lectura de catálogo en Android con IDs enteros; después migrar Room/outbox preservando los pendientes Supabase, y coordinar emisores Web/Android con Caja. Este bloque prepara un emisor, no activa ventas MariaDB en la interfaz. No hubo cambios SQL ni pruebas Supabase porque sus contratos no cambiaron.
