# Catálogo API Android preparado

Estado posterior, 2026-10-02: el recorrido Android principal ya usa la API.
Consultar [backend-android-workflow.md](backend-android-workflow.md) para el alcance
actual, configuración y pruebas nuevas. La evidencia siguiente conserva el
alcance original de este bloque; no acredita paridad nativa completa.

Bloque del 2026-10-01 en la arquitectura oficial Backend API + MariaDB.
El catálogo visible, login, carrito y sincronizador continúan en Supabase.
No se sustituyó CatalogRepository ni se activó este consumidor en ViewModels.

## Archivos de esta entrega

Rutas Kotlin relativas a `app/src/main/java/com/intutec/viveroapp/`:

- `feature/catalog/domain/repository/BackendCatalogGateway.kt`: modelos y contrato de lectura con IDs Long y centavos enteros; páginas, categorías, productos, imagen principal y promoción.
- `feature/catalog/data/remote/BackendCatalogRemoteDataSource.kt`: implementación de categorías/productos paginados y escaneo usando los endpoints existentes.
- `core/network/BackendApiTransport.kt`: GET con mapa de parámetros codificados por Ktor, sin interpolar búsquedas en rutas.
- `core/di/BackendNetworkModule.kt`: enlace Hilt al contrato nuevo, conservando los enlaces existentes.
- `app/src/test/java/com/intutec/viveroapp/feature/catalog/BackendCatalogRemoteTest.kt`: siete pruebas sintéticas.
- Este documento y `docs/supabase-migration-map.md`.

## Contratos y límites

GET `/api/v1/categories` y GET `/api/v1/products` consultan exclusivamente activos.
Paginación por limit 1–100 y after_id positivo; productos permiten search (hasta
80 unidades UTF-16, como el servidor) y category_id. El cursor devuelto debe
coincidir con el último ID de una página completa; IDs ordenados, únicos y
posteriores al cursor solicitado. No se carga todo el catálogo automáticamente.

POST `/api/v1/products/scan` requiere VIEW_CATALOG en servidor, acepta código
interno o barcode y devuelve producto o null. Se preserva el error 409 por código
ambiguo; no se elige un producto arbitrariamente. Códigos/búsquedas inválidos se
rechazan antes de enviar. Cancelaciones se propagan; no hay reintentos automáticos.

Los métodos reciben token API explícito en memoria. Aunque las listas activas
son públicas en servidor, este contrato Android envía el token de su sesión API;
no usa tokens Supabase ni las credenciales de MariaDB. La sesión preparada está
documentada en [backend-android-session.md](backend-android-session.md).

El parser verifica IDs enteros positivos uint32, centavos exactos seguros,
productos activos, precio efectivo menor o igual al base y coherencia de la
presencia de promoción. No recalcula descuentos ni cotizaciones. Las imágenes
solo admiten la ruta oficial `/api/v1/images/:id`, sin URLs externas. Esa ruta es
relativa: al activar UI se resolverá con el origen API público configurado.

No se inventa stock, mínimo, fechas o precio mayorista: las listas activas de API
no ofrecen esos campos. Tampoco se convierte el ID entero en UUID/String del
Product Supabase actual. No incluye administración, caché Room ni conexión de
pantallas en este bloque. Respuestas incompatibles fallan con mensaje seguro,
sin mostrar cuerpo remoto. Se conserva el límite de respuesta del transporte
(65536 caracteres); los consumidores deben pedir páginas menores si el contenido
excede ese límite.

## Verificación

Desde la raíz con el JDK 21 ya instalado, configurado solo para el proceso:

```powershell
$env:JAVA_HOME='C:/Users/GAMER/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2'
.\gradlew.bat testDebugUnitTest --tests 'com.intutec.viveroapp.feature.catalog.BackendCatalogRemoteTest'
.\gradlew.bat assembleDebug testDebugUnitTest
git diff --check
```

Las siete pruebas específicas pasaron: precios/promociones e imagen, parámetros,
categorías vacías, escaneo, contratos inválidos, cursores/filtro incompatibles,
entrada inválida sin petición, errores seguros y cancelación sin reintento.
La compilación APK debug y las 255 pruebas totales pasaron, sin fallos, errores
u omitidas; git diff --check correcto.
Pruebas con transporte inyectado, sin HTTP real ni verificación de UI/dispositivo.
No se inició Docker o emulador. Sin SQL, dependencias nuevas, cambios Web o secretos.
La comprobación SQL no aplica. Los cambios preexistentes se conservaron; sin
commit, push o despliegue.

Siguiente dependencia: conservar los pendientes Room y sus identidades al adaptar
el outbox para API; después activar sesión/catálogo/emisores junto con Caja y
pedidos Web en el mismo destino. No declarar un corte de módulos por tener este
consumidor preparado.
