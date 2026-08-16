# Arquitectura

## Decisión inicial

La aplicación usa una sola Activity y Jetpack Compose. Se organiza por funcionalidad para evitar carpetas globales difíciles de mantener. En Fase 1 se conserva un solo módulo Gradle (`app`) para reducir complejidad; se podrá modularizar cuando el tamaño y los tiempos de compilación lo justifiquen.

Flujo de datos:

```text
Compose UI <- UiState/StateFlow <- ViewModel <- caso de uso <- repositorio
                                                           <- fuente local/remota (fases futuras)
```

- `core`: estado común, modelos compartidos, diseño e inyección.
- `navigation`: rutas tipadas y grafo principal.
- `feature/<funcionalidad>/presentation`: pantallas, ViewModels y eventos.
- `feature/<funcionalidad>/domain`: modelos, contratos y casos de uso.
- `feature/<funcionalidad>/data`: implementaciones, mappers y fuentes de datos.

Los composables reciben estado y emiten eventos; no contienen reglas de negocio. Los modelos de dominio no dependen de DTO de Supabase ni dependerán de entidades Room. Hilt conecta implementaciones con contratos. `AuthRepositoryImpl` usa Supabase cuando existen claves locales y conserva un acceso demo explícito. `SessionStore` comparte solo el perfil de sesión necesario; nunca guarda contraseñas.

## Autenticación y autorización

`AuthViewModel` restaura sesión, inicia acceso, solicita recuperación y cierra sesión. El rol se obtiene de PostgreSQL después de autenticar, no de un selector en el teléfono. `RolePermissions` controla la navegación local y RLS replica la protección en el servidor.

## Catálogo

`CatalogRepository` expone categorías y productos mediante `CatalogSnapshot`. `SessionCatalogRepository` selecciona estrictamente `SupabaseCatalogRepository` para sesiones remotas y `FakeCatalogRepository` para demo, sin fallback entre ambos. Las imágenes remotas conservan UUID, ruta y orden en el dominio; `imageKey` queda reservado a recursos del catálogo demo y el dominio no conoce `R.drawable` ni otras APIs Android.

Los precios se guardan como centavos mediante `Long`. El ViewModel combina consulta, categoría y disponibilidad con el flujo del repositorio, y conserva estados explícitos de carga, contenido, vacío y error reintentable.

## Escáner

`BarcodeAnalyzer` es la única pieza que conoce ML Kit. CameraX entrega fotogramas conservando solo el más reciente; cada `ImageProxy` se cierra al terminar el análisis. Los formatos se restringen a QR, EAN-13, EAN-8 y Code 128 para reducir trabajo innecesario.

`ScannerViewModel` recibe únicamente código y formato, y ejecuta `FindProductByCodeUseCase`. El repositorio busca primero en el catálogo local/demo y, si no hay coincidencia y Supabase está configurado, consulta `products`. Como el inventario por sucursal se implementará en una fase posterior, un producto obtenido remotamente muestra “Disponibilidad por confirmar” y no habilita el carrito.

`DuplicateScanGuard` evita procesar repetidamente el mismo valor durante una ventana corta. La persona puede liberar el control inmediatamente mediante “Otro código”. La pantalla mantiene una entrada manual para pruebas y como alternativa accesible cuando el dispositivo no tiene cámara.

## Carrito y tickets

`Cart`, `CartItem` y `SaleTicket` son modelos de dominio independientes de Room. Las reglas de cantidad, existencia, precio y descuento se validan antes de persistir. Los totales usan `Long` en centavos y operaciones aritméticas exactas para detectar desbordamientos.

`RoomCartRepository` mantiene un único borrador activo y conserva instantáneas del nombre, código, precio y promoción del producto. Un `Mutex` serializa mutaciones y envío, por lo que dos pulsaciones no pueden crear dos tickets. Al enviar, Room persiste venta, partidas y dos eventos de estado de manera transaccional, y elimina el borrador únicamente después de completar la transacción.

El ticket local queda `SENT_TO_CASHIER` con `syncPending=true`. La migración remota incluye `submit_sale_to_cashier`, que utiliza el UUID como clave de idempotencia y recalcula precios desde PostgreSQL. La existencia se validará contra `stock_balances` en la Fase 7 y el descuento promocional completo se conectará en la Fase 9; la aplicación no presenta el envío local como pago confirmado.

## Estado de pantalla

`UiState<T>` representa `Loading`, `Success`, `Empty` y `Error`. Las pantallas con necesidades particulares podrán usar estados específicos conservando esos casos esenciales.

## Dinero y concurrencia

Los importes se representarán como centavos mediante `Long`. Las operaciones críticas de caja e inventario se confirmarán en PostgreSQL de forma atómica e idempotente; el teléfono nunca será la autoridad final.
