# Mapa de transición de Supabase a Backend API/MariaDB

Para el estado actual consultar [README](../README.md#estado-actual) y
[cierre de entrega](release-readiness.md). Este mapa conserva el estado del
2 de octubre anterior al despliegue/importación documentados el día 3 y a
la integración local de cámara del día 4. Sus pendientes históricos no
sustituyen la evidencia posterior.

## Estado vigente (2026-10-02)

Mostrador, administración de catálogo/imágenes/promociones y compras/proveedores
Web ya llaman a la API oficial; se suman a los consumidores migrados antes.
Recuperación de contraseña e invitaciones Web también usan API (migración 024).
Newsletter Web también usa API (migración 025), incluido consentimiento, baja y
campañas. El bundle generado ya no incluye el SDK Supabase. Android activa el
recorrido login/catálogo/carrito/caja/inventario API; sus otras pantallas nativas aún requieren
portar contratos y permanecen fuera del grafo activo. AppCliente sigue
demo. No hay exportación/importación real. El perfil VPS se ensayó solamente en
localhost; no certifica el corte operativo. Ver evidencia y pendientes exactos en
[backend-complete-cutover.md](backend-complete-cutover.md).

Contratos de newsletter: [backend-newsletter.md](backend-newsletter.md).
Android ya activa consumidores operativos API sin sesión/configuración Supabase.
Conserva historial UUID sin convertirlo ni reenviarlo; el SDK y las fuentes antiguas
permanecen como código histórico. Evidencia nueva: APK debug y de pruebas compilados,
330 pruebas unitarias, SQLite, 25 SQL/HTTP Docker y recorrido HTTP Kotlin/MariaDB
correctos. Ya incorpora historial propio, comprobantes y detalle de comandas.
Migración 026 bloquea claves de pago retiradas sin cancelar pagos ni ventas.
[Detalle del bloque](backend-android-history.md) y
[límites del recorrido](backend-android-workflow.md).
Inventario nativo ahora usa API/MariaDB: saldos, recepción, conteo e historial.
Room 6 conserva intentos antes de HTTP y recupera con cuerpo/clave originales.
Validación nueva: 343 unitarias, un HTTP opcional omitido y ejecutado aparte;
SQLite 3→4→5→6, 68 backend/check y SQL/HTTP de inventario correctos.
[Alcance y evidencia actual](backend-android-inventory.md). Los 25 SQL/HTTP
indicados arriba corresponden al bloque anterior; no se repitió toda esa suite.
Contratos y pruebas de correo de acceso: [backend-account-links.md](backend-account-links.md).

## Estado histórico del corte en curso (2026-10-01)

Pedro autorizó integrar los consumidores restantes de los tres proyectos y
confirmó conservar los datos actuales. La decisión y preparación de la copia
privada están en [backend-complete-cutover.md](backend-complete-cutover.md).
No hay exportación/importación real ni retirada completa de Supabase.

ViveroWeb utiliza API para sesión/login, catálogo/pedidos públicos, atención
administrativa de pedidos, cobro de Caja, cortes/devoluciones, clientes,
activación de inventario y el adaptador de sucursales/personal/roles/informes/
movimientos. No hay fallback a Supabase en esos consumidores.

Siguen pendientes mostrador, edición de catálogo/imágenes/promociones, compras,
newsletter, invitaciones y recuperación de contraseña. El nuevo login no
concede sesión Supabase a esos módulos. Android conserva sus consumidores
operativos Supabase y Cliente sus repositorios demo. No hay corte operativo
completo, exportación/importación real ni despliegue.

Las secciones siguientes conservan evidencia histórica; sus afirmaciones de
consumidores preparados o login Supabase quedan sustituidas por este estado.
Verificaciones nuevas: Web 451 pruebas, build/lint correctos; backend 64
pruebas/check correctos; Docker 23 pruebas SQL/HTTP correctas. Sin pruebas
visuales de extremo a extremo Web ni compilación/dispositivo Android nuevos.

Inspección local: 2026-09-29. Se revisaron estructura, dependencias, fuentes de
producción, servicios remotos, migraciones, funciones Edge, contratos y docs de
los tres proyectos. No se consultaron servicios remotos ni credenciales. Este
mapa describe el código local; no acredita el estado desplegado.

Punto de partida: ViveroApp `main` / `a4621f6d42deb735714c047b05789ee83760939a`;
ViveroWeb `main` / `d4db2b666e4d726d2887d133e64fa34794535ef6`.
ViveroAppCliente no tiene repositorio Git en la carpeta inspeccionada.

## Dónde trabajar

| Área | Ubicación y autoridad |
|---|---|
| API oficial nueva | ViveroApp `backend/src/`: HTTP en app.js, validación/SQL de catálogo en catalog.js, conexión/configuración en server.js |
| Base nueva | ViveroApp `database/mysql/`: schema.sql, seed.sql y grants.sql |
| Entorno local nuevo | ViveroApp `infra/docker/compose.yaml`, `.env.example` |
| Android operativo | ViveroApp `app/`; Kotlin/Compose, Hilt, Room, repositorios por funcionalidad |
| Web | ViveroWeb `src/`; React/Vite, servicios por funcionalidad y parsers estrictos |
| App cliente | ViveroAppCliente `app/`; Compose/Hilt y repositorios con datos locales demo |
| Backend transitorio | ViveroApp `supabase/migrations/` (35 migraciones) y `supabase/functions/` |

`supabase/migrations/` sigue siendo autoritativo exclusivamente para los módulos
que permanecen en PostgreSQL. MariaDB es el destino oficial nuevo y su definición
vive en `database/mysql/`; no se duplica SQL en los otros clientes.

## Android: dependencias vigentes

Las rutas siguientes parten de `app/src/main/java/com/intutec/viveroapp/`.

| Módulo | Archivos / contratos Supabase | Estado después de fase 1 |
|---|---|---|
| Conexión/DI | `core/network/SupabaseProvider.kt`, `core/di/RepositoryModule.kt`; dependencias en catálogo Gradle y configuración en app/build.gradle.kts | Sin cambios |
| Auth y acceso | `feature/auth/data/remote/SupabaseAuthRemoteDataSource.kt`: sesión/contraseña/recuperación; lectura profiles, user_roles con roles, role_permissions, branches | Supabase Auth + RLS |
| Catálogo y escaneo | `feature/catalog/data/remote/SupabaseCatalogRemoteDataSource.kt`: categories, products con product_images, get_catalog_pricing, get_my_branch_catalog_inventory, get_product_by_scan_code | Supabase |
| Administración catálogo | `feature/catalog/data/remote/SupabaseCatalogAdminRemoteDataSource.kt`: upsert_category, upsert_product | Supabase |
| Imágenes | `feature/catalog/data/remote/SupabaseCatalogImageRemoteDataSource.kt`: bucket catalog-images, product_images, set_product_image_primary | Supabase Storage + SQL |
| Venta y outbox | `feature/cart/sync/SupabaseSaleSyncRemoteDataSource.kt`: submit_sale_to_cashier; Room conserva venta/reintento local | Room + Supabase |
| Mis ventas | `feature/mysales/data/remote/SupabaseMySalesRemoteDataSource.kt`: get_my_recent_sales | Supabase |
| Caja | `feature/cashier/data/remote/SupabaseCashierRemoteDataSource.kt`: sales/sale_items; `SupabaseCashierPaymentRemoteDataSource.kt`: claim_sale_for_payment, release_sale_payment_claim, confirm_sale_payment | Supabase; no se altera pago |
| Inventario | `feature/inventory/data/repository/SupabaseInventoryRepository.kt`: get_my_inventory_dashboard, get_my_inventory_history, record_inventory_reception, reconcile_inventory_count | Supabase |
| Clientes | `feature/customer/data/remote/SupabaseCustomerRemoteDataSource.kt`: search_customers | Supabase |
| Personal/sucursales | `feature/staff/data/repository/SupabaseStaffRepository.kt`: get_admin_staff, get_admin_branches, assign_user_role, assign_user_branch | Supabase |
| Reportes | `feature/reports/data/remote/SupabaseReportsRemoteDataSource.kt`: get_report_daily_sales, get_report_top_products | Supabase |

Compose y ViewModels dependen de estos repositorios. Los IDs UUID existentes,
QR, outbox y Room no se cambian en esta fase. Los modelos de dominio permanecen
independientes de los DTO del nuevo backend.

## Web: dependencias vigentes

Rutas relativas a ViveroWeb `src/`.

| Módulo | Punto de entrada / contratos | Estado |
|---|---|---|
| Cliente/configuración | `lib/supabase/client.ts`, `env.ts` | Supabase; no se cambian variables existentes |
| Sesión/recuperación | `features/auth/AuthProvider.tsx`, `PasswordRecoveryPage.tsx` | Supabase Auth |
| Capacidades/rutas | `features/access/access-service.ts`: get_my_access_context; parsers y AccessGuards | Supabase |
| Catálogo público | `features/public-catalog/catalog-service.ts`: get_public_catalog V3; `catalog-image.ts`: URL de Storage | Supabase |
| Catálogo admin | `features/admin/admin-catalog-service.ts`: products/categories, upsert_product, upsert_category, set_product_image_primary, catalog-images y product_images | Supabase |
| Promociones | `features/admin/admin-promotions-service.ts`: promotions, upsert_catalog_promotion | Supabase |
| Clientes | `features/admin/admin-customers-service.ts`: search_customers, upsert_customer | Supabase |
| Sucursales/personal | `features/admin/admin-service.ts`: get_admin_branches, create_branch, update_branch, set_branch_active, get_admin_staff, assign_user_branch, set_admin_staff_role, set_user_active | Supabase |
| Inventario/reportes | `features/admin/admin-service.ts`: get_my_inventory_dashboard/history, record_inventory_reception, reconcile_inventory_count, get_report_daily_sales/top_products; activación en InventoryActivation.tsx | Supabase |
| Caja | `features/cashier/cashier-service.ts`: get_cashier_sales/detail, claim_sale_for_payment, release_sale_payment_claim, confirm_sale_payment, get_cashier_payment_result | Supabase |
| Mostrador | `features/cashier/counter-sale-service.ts`: products, get_product_by_scan_code, submit_sale_to_cashier | Supabase |
| Cortes/devoluciones | `features/cashier/cashier-operations-service.ts`: get_my_cashier_closing_preview, close_my_cashier, refund_sale_in_person, get_refundable_sale | Supabase |
| Pedidos | `features/public-orders/web-order-service.ts`: get_public_web_order_options, submit_web_order, get_admin_web_orders, set_admin_web_order_status, send_web_order_to_cashier | Supabase |
| Compras/proveedores | `features/admin/purchases/purchases-service.ts`: upsert_supplier, create_supplier_purchase_draft, get_my_supplier_purchases, get_supplier_purchase, set_supplier_presentation, resolve_supplier_purchase_item, confirm_supplier_purchase | Supabase |
| Invitaciones | `features/admin/StaffInvitation.tsx`: Edge invite-staff | Supabase Auth administrativo, solo servidor |
| Boletín | `features/newsletter/`: Edge newsletter, confirm_newsletter_subscription, unsubscribe_newsletter, get_newsletter_campaigns y preparación de campaña | Supabase + correo externo |

`DemoStore`/datos mock siguen siendo demostrativos, no una tercera persistencia
del nuevo catálogo. No se modifican ni se presentan como datos de MariaDB.

## Contratos del servidor transitorio

Las 35 migraciones contienen Auth/perfiles, roles/capacidades, RLS y privilegios;
catálogo e imágenes; sucursales; ventas/partidas/historial; reservas y pagos;
inventario/recepciones/conciliaciones; clientes; promociones/precios autoritativos;
reportes; pedidos web; proveedores/compras; cortes/devoluciones; boletín.
Las funciones Edge `supabase/functions/invite-staff/index.ts` y
`supabase/functions/newsletter/index.ts` usan privilegios solo de servidor.
No se copian sus credenciales al nuevo backend.

La migración exige trasladar reglas de RLS/RPC a autorización y transacciones
de API, no traducir solo CREATE TABLE. Las pruebas pgTAP existentes siguen
describiendo el comportamiento que debe conservarse cuando corresponda.

## ViveroAppCliente

No se encontraron llamadas ni dependencias Supabase en las fuentes/dependencias
inspeccionadas. `core/di/RepositoryModule.kt` enlaza repositorios locales de
plantas, carrito, club, doctor y seguimiento; sus implementaciones contienen
datos demo/StateFlow. Será consumidor futuro de la misma API. No se crea otro
backend ni se migra un login que esta app todavía no implementa.

## Por qué todavía no se conecta ViveroWeb

**Bloqueante para el corte de catálogo:** `PublicCartProvider` conserva IDs del
catálogo y `web-order-service.ts` los envía a `submit_web_order`. Los contratos
operativos y parsers esperan UUID de PostgreSQL. Caja/mostrador consultan el mismo
catálogo y PostgreSQL recalcula precios/promociones antes de cobrar. Un producto
entero de MariaDB no existe allí; editar solo su precio nuevo tampoco modifica
el precio que se cobraría. Las correspondencias de importación 006–008 no son
un puente operativo de pedidos. La API ya recibe pedidos PENDING, pero su
administración ya está implementada en 010 y la entrega a caja sigue pendiente.

**Bloqueante para administración web:** las sesiones propias de API implementadas
en fase 2 ya se validaron contra MariaDB local, pero falta conectarlas al consumidor.
El token compartido local fue retirado; una sesión Supabase no se acepta como
sesión nueva. No introducir credenciales de bootstrap en Vite.

**Importante:** importar catálogo, imágenes y precios; definir correspondencias
de IDs y tratamiento de borradores/outbox; completar autorización de API antes
del cambio de consumidor. **Deuda posterior:** búsqueda/índices especializados,
observabilidad avanzada y optimización. No justifican ampliar esta fase.

Por ello no se agrega un selector de motor, catálogo duplicado en la UI ni una
ruta experimental. Identidad/capacidades de API están implementadas y probadas
con MariaDB local; sigue pendiente el corte coordinado del catálogo. Ver
[guía de trabajo y límites](backend-api-mariadb.md).

La [preparación del cambio Web](web-catalog-cutover.md) documenta ahora la matriz
de contratos, origen y reversión. El endpoint existente de productos incorpora
búsqueda y filtro por categoría antes de paginar. El catálogo Web no se activó:
pedidos, carrito persistido y autorización requieren un cambio coordinado.

## Estado de migración verificable

| Componente | Backend/MariaDB nuevo | Supabase todavía |
|---|---|---|
| Health y CRUD catálogo local por HTTP | Implementado y probado con perfil test y desde Windows | Catálogo operativo original intacto |
| Usuarios/roles/sucursales | Login, sesión, logout y permisos probados en MariaDB local; bootstrap explícito validado | Autenticación y autorización de consumidores existentes |
| Inventario | Migración 011 y API local: saldo por sucursal, bitácora, recepción idempotente y conciliación | Android/Web aún usan RPC y Supabase; salidas por venta, devoluciones, traspasos y compras siguen pendientes |
| Ventas/partidas/pagos | API 012 para ventas; 013 para bandeja de caja, reservas, cobro atómico y recuperación | Clientes aún operan ventas, caja y pagos mediante Supabase; cortes/devoluciones y activación de stock pendientes |
| Web/Android | Ningún consumidor cambiado aún | Todos los módulos operativos descritos |
| Futura app cliente | Pendiente de conectar | No consume Supabase actualmente |

No hubo importación de datos, commit, push, despliegue ni cambios remotos. Las
validaciones ejecutadas y sus límites se registran en
[fase 1](backend-phase1-validation.md), [fase 2](backend-phase2-validation.md)
y [validación local completada](backend-local-validation.md).

## Ampliación de catálogo en API

La API permite editar/desactivar/reactivar categorías y guardar cuidados y
mayoreo de productos mediante `003_catalog_details`. Véase
[contrato y preparación del traslado de IDs](backend-catalog-details.md).
Esto no cambia la autoridad operativa de los clientes: Web y Android mantienen
catálogo, imágenes, promociones, pedidos, ventas y caja en Supabase hasta su
corte coordinado. El mapeo de IDs es un diseño documentado, no un importador ya
implementado.

La API también incorpora [imágenes con almacenamiento persistente](backend-product-images.md)
en `004_product_images`. Los clientes todavía usan Supabase Storage. No hubo
importación de imágenes ni activación del catálogo nuevo en Web/Android.

`005_catalog_promotions` incorpora [administración y resolución de promociones](backend-catalog-promotions.md)
en MariaDB/API. Supabase sigue siendo la autoridad de precios en los clientes
operativos y en sus ventas/pedidos; la API aún no ejecuta esos flujos. No hay
doble escritura ni importación automática entre motores.

`006_catalog_imports` implementa [correspondencias e importación local de categorías/productos](backend-catalog-import.md).
Solo se ha ejecutado con fixtures sintéticos. Las correspondencias ya no son
únicamente diseño; falta importar datos reales autorizados, promociones e
imágenes y completar la conciliación antes de cambiar consumidores.

La [importación local de promociones](backend-promotion-import.md), migración
007 y formato versión 2 del mismo importador, ya conserva correspondencias de
productos, fechas con microsegundos y el desempate por UUID de origen. Solo se
validó con datos sintéticos.

La [importación local de imágenes](backend-image-import.md), migración 008 y
formato versión 3, valida archivos mediante SHA-256 y concilia correspondencias,
metadatos y bytes almacenados antes de reutilizar IDs. Probada solo con fixtures
sintéticos; sigue pendiente importar datos reales autorizados y cambiar los
consumidores. Web/Android aún leen imágenes de Supabase Storage. No hay purga ni
conciliación global del volumen.

La [recepción de pedidos en API](backend-web-orders.md), migración 009, incorpora
sucursales públicas, cotización, creación idempotente y recuperación de recibos.
Solo se probó con datos sintéticos locales. Web sigue enviando sus pedidos a
Supabase; administración, entrega a caja, cobro e inventario no cambiaron.

La [administración de pedidos API](backend-web-order-admin.md), migración 010,
añade bandeja, detalle e historial, con permisos y sucursal verificados en cada
solicitud. CONFIRMED/READY/CANCELLED están implementados en la API; no se activó
su consumidor Web. Caja, cobro e inventario operativo siguen en Supabase.

La [API de inventario](backend-inventory.md), migración 011, añade saldo y
movimientos por sucursal, bitácora inicial, recepción y conciliación atómicas con
claves idempotentes. Aún no cambia consumidores ni la autoridad Supabase de las
operaciones existentes; en particular no se activa el trigger gradual de ventas.

La [API de ventas](backend-sales.md), migración 012, agrega envío a caja,
partidas e historial atómicos, snapshots de promociones, recuperación idempotente
y consulta propia por sucursal. No activa clientes, cobro ni salidas de stock.

La [API de caja](backend-cashier.md), migración 013, implementa reservas exclusivas
de cinco minutos, liberación, cobro CASH/CARD/TRANSFER y recuperación idempotente
por cajero/sucursal. Validada en MariaDB sintético local; no cambia consumidores
ni activa salidas automáticas de inventario, cortes o devoluciones.

La [entrega de pedidos a caja](backend-web-order-checkout.md), migración 014,
vincula cada pedido a una sola venta conservando sus snapshots, bloquea su
cancelación tras enviarlo y habilita COMPLETED solo después del pago. Las rutas
operativas Web/Android todavía usan Supabase; no se activó stock automático.
# Bloque 015: devoluciones (2026-09-30)

Backend/MariaDB implementa consulta y registro de devoluciones totales en persona,
con permisos de caja y descuentos, importe del pago, idempotencia y bloqueo de
entrega tras devolución. Ver [backend-refunds.md](backend-refunds.md).
Los consumidores Web/Android siguen en Supabase. No se activa stock por venta:
la intención de reposición se registra sin aumentar existencias. Cortes de caja
es el siguiente módulo pendiente.

## Bloque 016: cortes de caja (2026-09-30)

Backend/MariaDB implementa cortes propios por cajero y sucursal: previa,
confirmación idempotente, recuperación y consulta de recibo con IDs de operaciones.
Ver [backend-closings.md](backend-closings.md). Los consumidores siguen en Supabase.
El siguiente módulo operativo recomendado es inventario por venta/reposición;
incluye revisar el interbloqueo intermitente encontrado en su prueba local.

## Bloque 017: inventario por venta y devolución (2026-09-30)

Backend/MariaDB ofrece activación por sucursal, salidas por pago y reposición de
devoluciones basada en sus movimientos reales. Sin retroactividad ni activación
operativa automática. Corregido el caso de interbloqueo de recepciones detectado
en 016, con 17/17 pruebas finales de integración aprobadas. Ver
[backend-sale-inventory.md](backend-sale-inventory.md).
Consumidores y datos reales continúan en Supabase. Siguiente módulo: clientes.

## Bloque 018: clientes (2026-09-30)

Backend/MariaDB implementa clientes globales: búsqueda acotada, alta por ventas o
administración, edición/desactivación por MANAGE_USERS y auditoría con FKs.
Consultar [backend-customers.md](backend-customers.md). Sin importación de contactos
ni asociación automática a ventas/pedidos. Consumidores continúan en Supabase.
Siguiente módulo: administración de personal y sucursales.

## Bloque 019: personal y sucursales (2026-09-30)

Backend/MariaDB administra roles, estado y sucursal de personal existente y
altas/edición/estado de sucursales con auditoría y revocación de sesiones.
Preserva jerarquía OWNER/ADMIN y protege al último OWNER activo bajo concurrencia.
Ver [backend-administration.md](backend-administration.md). No incluye nuevas
cuentas ni importación de identidades. Web/Android continúan en Supabase.
Siguiente módulo: compras y proveedores.

## Bloque 020: compras y proveedores (2026-09-30)

Backend/MariaDB implementa proveedores/presentaciones, borradores con costos,
revisión de líneas, equivalencias y confirmación con recepciones idempotentes.
Ver [backend-purchases.md](backend-purchases.md). Sin importación de documentos
reales, parser de archivos ni conexión de consumidores; estos siguen en Supabase.
Siguiente módulo: reportes de ventas y productos.

## Bloque 021: reportes (2026-10-01)

Backend ofrece daily-sales y top-products respetando el alcance por capacidades
y la diferencia histórica entre fecha de cobro y ventas PAID. Ver
[backend-reports.md](backend-reports.md). No sustituye aún los RPC de consumidores
ni genera reportes contables netos; Web/Android continúan en Supabase.

Integración de reportes ya verificada tras recuperar Docker: 21/21 suites de
integración y 55/55 unidades aprobadas. Próximo paso: instalación integral desde
un volumen local nuevo y aislado, antes de conectar consumidores.

## Bloque 022: instalación nueva (2026-10-01)

Validado el backend completo en volumen vacío, bootstrap OWNER y persistencia.
Corregido el cargador SQL inicial de Docker; 21/21 integraciones y 55/55 unidades
aprobadas. Ver [backend-fresh-install.md](backend-fresh-install.md).
Consumidores y datos reales siguen en Supabase. Próximo paso: coordinar conexión
por módulos con identidad/sesión, correspondencia de IDs y rollback.

## Bloque 023: canal Web/API (2026-10-01)

Comunicación de navegador habilitada mediante proxy Vite y origen backend
explícito, validada con sesión/catálogo demo y MariaDB. Sin corte de servicios
UI ni canje de identidad Supabase. Ver
[backend-browser-connection.md](backend-browser-connection.md).
Auth, catálogo, carrito, pedidos y caja de Web/Android siguen en Supabase.

## Actualización: cuentas de personal (2026-10-01)

La API permite alta administrativa de personal y restablecimiento de contraseñas con auditoría y revocación transaccional. Migración SQL 021. Consultar [backend-staff-accounts.md](backend-staff-accounts.md) para contratos, permisos y evidencia. Web y Android conservan sus sesiones Supabase hasta la integración coordinada de sus consumidores.

## Consumidores Web preparados (2026-10-01)

ViveroWeb ya tiene servicios TypeScript de sesión, lectura de catálogo, pedidos públicos, atención administrativa y núcleo de Caja probados contra la API local. Son código preparado aún no invocado por UI: sesión y módulos visibles siguen en Supabase. En esta entrega se verificó un pedido sintético hasta cobro CASH, recuperación del mismo pago y entrega completada. Consultar ViveroWeb/docs/backend-web-cashier-service.md para evidencia y consumidores complementarios pendientes. No se modificó SQL ni se declara un corte operativo.

## Cotización y consumidor de mostrador (2026-10-01)

Nuevo POST /api/v1/sales/quote autenticado con CREATE_SALES y sucursal tomada de la sesión; solo cotiza, sin escribir ventas. Quote/submit/recover comparan las cabeceras opcionales X-Expected-Actor-Id y X-Expected-Branch-Id antes de operar. ViveroWeb tiene consumidor de cotización, envío, conservación del intento y recuperación probado contra Docker local, todavía no invocado por UI. Sin cambios SQL. Contratos, comandos, evidencia y pendientes en ViveroWeb/docs/backend-web-counter-sales.md.

## Escaneo API preparado (2026-10-01)

Nuevo POST /api/v1/products/scan autenticado con VIEW_CATALOG, código exacto interno/barcode, precio efectivo del servidor y rechazo de ambigüedad. Sin cambios de tablas/índices. Consumidor Web integrado al servicio de mostrador preparado, todavía no invocado en UI; Web y Android visibles siguen en Supabase. Contratos y evidencia en ViveroWeb/docs/backend-web-product-scan.md.

### Consumidor Web de devoluciones y cortes (2026-10-01)

Se preparó `ViveroWeb/src/features/cashier/backend-cashier-operations-service.ts` para los endpoints existentes de devolución y corte, con comprobación de sesión/identidad, recibos y reintentos idempotentes. Guía: `ViveroWeb/docs/backend-web-cashier-operations.md`. Validación nueva: lint, build y 416 pruebas Web (9 nuevas), correctos. No se realizó nueva prueba Docker/UI. La pantalla de Caja continúa en Supabase; faltan persistencia/exclusión de intentos y conexión conjunta con autenticación Backend API antes de activarlo. No cambia el estado de migración de consumidores visibles.

### Persistencia de devoluciones y cortes Web (2026-10-01)

El consumidor anterior ahora exige almacenamiento explícito al enviar: guarda/relee el intento antes de la petición, conserva pendientes ante errores, restaura por usuario/sucursal y serializa envío/eliminación mediante Web Locks. Un pendiente impide que otra devolución o corte lo reemplace. Validación nueva: lint, compilación y 423 pruebas Web correctos, incluyendo 7 nuevas de persistencia/concurrencia. Sin nueva prueba Docker/UI; la pantalla sigue en Supabase. Siguiente paso: comprobar el recorrido contra API local y conectar restauración/presentación junto con la sesión Backend API.

### Verificación Web de devoluciones/cortes contra Docker (2026-10-01)

Nueva prueba reproducible: `ViveroWeb/scripts/verify-backend-cashier-operations.mjs`, con auxiliar local `backend/test/web-cashier-operations-fixture.js`. Pasó consumidor TypeScript → proxy Vite → API → MariaDB en `vivero-fresh-20261001c`: respuesta perdida de devolución, restauración/reenvío con un único registro, bloqueo de pendiente, corte/detalle/recuperación/reenvío, totales exactos y asociaciones verificadas por SQL. Las filas sintéticas se limpiaron. Sin cambios de lógica de producción; lint Web y sintaxis de scripts correctos. Almacenamiento y bloqueo fueron adaptadores inyectados: no constituye prueba de UI/localStorage/Web Locks nativos. Caja visible continúa en Supabase. La sesión fue fixture, no login probado. Siguiente bloque: integración coherente de sesión Backend API y pantalla de Caja.

### Historial y búsqueda por folio para preservar Caja (2026-10-01)

Antes de activar la sesión Backend API se detectaron faltantes de consulta de comprobantes y búsqueda de devoluciones por folio. Se implementaron GET `/api/v1/cashier/receipts`, GET `/api/v1/cashier/receipts/:paymentId` y GET `/api/v1/cashier/refunds/lookup?folio=...`, con scope por cajero/sucursal y permisos de devolución. Consumidores Web correspondientes implementados; pantalla y AuthProvider aún Supabase. Guía: `docs/backend-cashier-receipts.md`. Nuevas validaciones correctas: 60 unitarias backend, syntax check, integraciones específicas de Caja/devoluciones, 427 pruebas Web, lint/build y runner Web → Vite → API → MariaDB con comprobante previo/posterior a devolución. Fixtures limpiados; sin SQL, Android o despliegue. Este bloque resolvió una dependencia; no se contabiliza como sesión/UI migrada.

### Ciclo de sesión API en AuthProvider Web (2026-10-01)

Se integró `backend-session.ts` + `useBackendSession.ts` dentro del único AuthProvider, con contrato propio de IDs enteros, token en memoria, login/contexto atómico, cierre/vencimiento, permisos invalidados al refrescar y protección ante respuestas tardías. Cierre global también invalida la sesión API. Validación nueva: 436 pruebas Web (9 nuevas), lint/build correctos; sin nueva prueba UI/Docker. Guía: `ViveroWeb/docs/backend-web-session.md`. No se activó formulario, rutas ni bandeja: Android y pedidos Web aún envían ventas a Supabase y el corte aislado de Caja rompería ese recorrido. Siguiente trabajo es coordinar emisores/identidades/datos y Caja en los frentes existentes; no se creó otra ruta ni transferencia paralela. Sin backend/SQL/Android modificados en este bloque.

### Consumidor Android de envío/cotización/recuperación de ventas (2026-10-01)

Se agregó transporte Ktor oficial y enlaces Hilt, contrato de dominio de IDs enteros/centavos Long e intento inmutable de 64 hex, y consumidor cart/data/remote para quote/submit/recover con comprobación de identidad y respuesta. BACKEND_API_URL es configuración pública local; HTTP local se limita a debug/hosts de loopback y emulador. No se activó en ViewModel/sincronizador ni se modificó Room: ventas, catálogo y sesión siguen Supabase. Guía `docs/backend-android-sales.md`. Validación nueva: compilación Kotlin, 7 pruebas específicas y assembleDebug/testDebugUnitTest completos correctos. Sin prueba HTTP real o dispositivo, sin cambios de dependencias/SQL. JDK 21 existente y caché Gradle requirieron ejecución autorizada fuera del sandbox; no se instaló ni configuró Java globalmente. Siguiente dependencia: sesión y catálogo Android antes de migrar el outbox y activar emisores/Caja coordinadamente.

### Ciclo de sesión API Android preparado (2026-10-01)

Se agregó BackendAuthGateway y su repositorio/fuente remota con login/contexto atómico, refresco, cierre y vencimiento; Hilt y SessionStore existentes alojan el contexto de IDs enteros sin sustituir AuthRepository Supabase. Token en memoria, permisos invalidados al refrescar y respuestas tardías descartadas tras cierre. Guía [backend-android-session.md](backend-android-session.md), incluyendo requisito de activar monitorExpiry en el propietario de UI y diferencia entre cierre local y revocación remota. Validación nueva: 15 pruebas específicas y assembleDebug/testDebugUnitTest correctos (248 pruebas totales), diff check correcto. Sin HTTP real, dispositivo, Docker, SQL o cambios Web. Login/catalogo/ventas visibles continúan Supabase; no se contabiliza como corte de consumidores. Siguiente dependencia: catálogo Android y outbox antes de activar emisores y Caja coordinadamente.

### Lectura y escaneo del catálogo API Android preparados (2026-10-01)

Se agregó BackendCatalogGateway con fuente remota y enlace Hilt para categorías/productos activos paginados, filtros y escaneo oficial; transporte GET con parámetros codificados por Ktor. IDs Long, precios efectivos del servidor, imagen principal y promoción; sin stock inventado ni conversión de UUID. Guía [backend-android-catalog.md](backend-android-catalog.md). Validación nueva: siete pruebas específicas, APK debug y 255 pruebas totales correctos, diff check correcto. Sin HTTP real/UI/dispositivo, Docker, SQL o cambios Web. No se reemplazó CatalogRepository ni se activaron pantallas: catálogo/login/ventas visibles siguen Supabase. Siguiente dependencia: outbox Room preservando pendientes e identidades, antes de activar emisores/Caja/pedidos Web coordinadamente.

### Persistencia y recuperación de intentos API Android (2026-10-01)

Room pasa de versión 3 a 4 mediante migración aditiva: encabezado API con PK autoincremental y clave única, items con FK, almacenamiento transaccional, reclamación exclusiva y recuperación de SYNCING a UNCERTAIN. El sincronizador API conserva payload/identidad y recupera antes de reenviar; tablas, pendientes y sincronizador Supabase permanecen intactos. Hilt preparado, sin emisor/UI activo. Guía [backend-android-outbox.md](backend-android-outbox.md). Validación nueva: ocho pruebas de sincronización, 263 unitarias totales correctas, APK debug e instrumentado compilados, verificación SQLite local (preservación, esquema Room, FK/unicidad/reclamación/recuperación), 134 comprobaciones estáticas Supabase y diff check correctos. Sin ejecutar pruebas instrumentadas o HTTP real, Docker, cambios SQL remotos o Web. El siguiente paso es validar Room en dispositivo autorizado y conectar el emisor Android con restauración/resolución de pendientes, coordinando Caja y pedidos Web. No se declara la migración completa ni el corte de pantallas.

### Emisor API Android con persistencia previa (2026-10-01)

BackendSaleEmitter conecta cotización oficial, alta/lectura Room antes de envío y sincronización/recuperación con clave original; Hilt singleton. Pendientes incluidos SYNCING bloquean ventas nuevas y se restauran solo para cuenta/sucursal originales. Carrito visible y botón continúan Supabase; no se mezclaron IDs ni se activó UI. Guía [backend-android-emitter.md](backend-android-emitter.md). Validación nueva: ocho pruebas específicas, APK debug y 271 unitarias totales correctas, verificación SQLite local y diff check correctos. Sin HTTP/UI/dispositivo, Docker o cambios Web/DDL. Siguiente dependencia: presentación y resolución de pendientes/rechazos y comprobantes, integración de pantallas en el destino compartido y validación completa. La migración no se declara terminada.

### Panel condicionado de intentos API en el carrito Android (2026-10-01)

Panel en CartScreen existente, sin ruta nueva: lista hasta 100 intentos locales y comprobantes confirmados de la cuenta/sucursal API, consulta resultado sin submit y ofrece reintento explícito con confirmación. Con sesión Supabase permanece oculto; Provider Hilt evita construir transporte API en ese caso. ViewModel invalida datos ante cierre/cambio de contexto y protege respuestas tardías. Rechazos conservados; no hay descarte por recover 404 porque no cierra una solicitud tardía en servidor. Guía [backend-android-pending-sales.md](backend-android-pending-sales.md). Nuevas verificaciones: 280 unitarias correctas (nueve nuevas), APK debug/instrumentado compilados, SQLite local y diff check correctos. Sin ejecutar UI/dispositivo/HTTP real ni cambios backend/SQL remoto/Web. Login/catalogo/carrito operativo/Caja siguen Supabase. Siguiente dependencia: cierre transaccional de claves rechazadas en servidor y consumidor de resolución, antes de activar emisores y demás pantallas coordinadamente.
