# Backend oficial — guía local

Esta guía conserva el arranque y alcance de las primeras fases. La API ya es
el destino del grafo Android operativo y existe evidencia posterior de VPS e
importación. Ver [estado actual](../README.md#estado-actual) y
[cierre de entrega](release-readiness.md); las limitaciones originales de
consumidores locales/demo no describen el despliegue posterior.

Actualizado con el módulo de [identidad y permisos](backend-identity.md).
El token compartido de fase 1 se retiró; las mutaciones exigen una sesión de usuario.
Consultar la [validación real local](backend-local-validation.md); los resultados
previos al arranque se conservan en el informe de fase 2.

Decisión de Pedro: Web, Android operativo y futura app cliente consumirán una
misma API, conectada a MariaDB y ejecutable en Docker. Esta es la ruta oficial,
en el repositorio ViveroApp y la rama existente; no hay una rama experimental ni
un segundo backend en ViveroWeb. No se incluye n8n en esta arquitectura.

## Qué está disponible

- Node 24, HTTP nativo y mysql2 con consultas preparadas y lockfile.
- MariaDB 11.4, volumen persistente, inicialización SQL y datos sintéticos.
- Health check con consulta real a la base, catálogo CRUD y categorías.
- API publicada solo en `127.0.0.1:3001`; MariaDB no publica puertos al host.
- Usuario SQL `catalog_api` con escrituras de catálogo/sesiones y lectura de
  identidad/capacidades. Puede insertar ventas/partidas e historial; no puede
  modificar importes de ventas ni usuarios/roles. Caja puede actualizar estado
  e insertar pagos; los permisos de reservas están limitados por columna.
  Nunca utiliza root en ejecución normal.
- Pruebas unitarias y pruebas de integración HTTP/SQL en el perfil Docker `test`.

**Todavía no se ha migrado ningún consumidor Web/Android.** El catálogo local es
el primer módulo implementado en la nueva API, con datos demo independientes;
no es una réplica ni un reemplazo activo del catálogo operativo. No hay doble
escritura, sincronización automática ni fallback entre motores.

## Inicio para Tony

Clonar ViveroApp desde su remoto habitual y entrar en su raíz. Se necesita Git y
Docker Desktop con motor Linux activo (o Docker Engine + Compose v2 en Linux).
Node 24/npm solo son necesarios para pruebas/desarrollo fuera de Docker.

1. Copiar `.env.example` a `.env` **solo si no existe**. Si ya existe, añadir las
   variables faltantes conservando las anteriores. Nunca copiar secretos al chat.
2. Sustituir los dos marcadores de base de datos con valores aleatorios diferentes; generar cada
   uno con `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`
   o con un generador de contraseñas. No reutilizar credenciales de Supabase.
3. Desde la raíz:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml config --quiet
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up --build -d --wait
curl.exe --fail http://127.0.0.1:3001/health
curl.exe --fail http://127.0.0.1:3001/api/v1/categories
curl.exe --fail http://127.0.0.1:3001/api/v1/products
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --rm tests
```

En Linux usar `curl` en lugar de `curl.exe`. Si 3001 está ocupado, cambiar
`API_PORT` en `.env` y en las URL anteriores. El perfil test usa la red interna.
El proyecto Compose se llama `vivero-local`; no administra contenedores Supabase.

Para ver el estado, detener o retomar conservando datos:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml ps
docker compose --env-file .env -f infra/docker/compose.yaml stop
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait
```

No usar `down -v` para actualizar un esquema: elimina el volumen. Los scripts
`schema.sql`, `seed.sql`, `grants.sql` y la migración de identidad corren durante
la primera inicialización de un volumen vacío. `migrate` aplica los pendientes
de `database/mysql/migrations/` a un volumen existente; omite versiones registradas
en `schema_migrations`. La fase 2 agrega `002_identity` sin modificar el baseline.
La ampliación [catálogo operativo](backend-catalog-details.md) agrega
`003_catalog_details`: cuidados, mayoreo y edición/baja de categorías. Health
requiere esta última migración; actualizar el volumen antes de iniciar la API.
La entrega posterior [imágenes de producto](backend-product-images.md) agrega
`004_product_images` y el volumen `catalog_images`; esta pasa a ser la migración
requerida por health. Incluye endpoints de carga, orden, principal y baja lógica.
La entrega [promociones y precios efectivos](backend-catalog-promotions.md)
agrega `005_catalog_promotions`, ahora requerida por health. El precio de lista
se conserva y se expone adicionalmente el efectivo calculado por el servidor.
El [importador local de catálogo](backend-catalog-import.md) agrega
`006_catalog_imports` y el servicio de herramientas `import-catalog`. Simula por
defecto; no cambia consumidores ni importa datos remotos automáticamente.
El [formato de promociones del importador](backend-promotion-import.md) agrega
`007_promotion_imports`, microsegundos y desempate por UUID de origen. Health
requiere 007 porque el cálculo del catálogo utiliza su correspondencia.
Cambiar las contraseñas del `.env` tampoco
rota las cuentas de un volumen existente. Las siguientes modificaciones del
esquema requieren migraciones SQL incrementales revisadas, respaldo y validación;
no reeditar este baseline como método de actualización de datos existentes.

Pruebas de lógica sin MariaDB:

```powershell
cd backend
npm ci
npm run check
npm test
```

Después de editar la API, reconstruir con `up --build -d --wait`. La API de
Docker no depende de una instalación de npm en el host. Para incorporar la Web,
trabajar en sus servicios de frontera existentes, no en un frontend alternativo.

## Contrato HTTP v1

Todas las respuestas son JSON. Los IDs son números enteros positivos; no UUID.
Los precios son números enteros en centavos, entre 0 y 9007199254740991, para
mantener exactitud en JavaScript; almacenamiento SQL BIGINT y Android Long.
La moneda de esta fase es MXN. No usar floats para importes.

| Método y ruta | Resultado / acceso |
|---|---|
| `GET /health` | 200 con base disponible; 503 si falla |
| `GET /api/v1/products` | Productos activos de categorías activas; público |
| `GET /api/v1/categories` | Categorías activas; público |
| `POST /api/v1/products` | Crear, 201 `{id}`; MANAGE_PRODUCTS + MANAGE_PRICES |
| `PATCH /api/v1/products/:id` | Actualizar, 200 `{id}`; MANAGE_PRODUCTS y MANAGE_PRICES si cambia precio |
| `DELETE /api/v1/products/:id` | Desactivar, nunca borrar; 200 `{id}`, repetible; MANAGE_PRODUCTS |
| `POST /api/v1/categories` | Crear, 201 `{id}`; MANAGE_PRODUCTS |
| `PATCH /api/v1/categories/:id` | Editar nombre, descripción o estado; MANAGE_PRODUCTS |
| `DELETE /api/v1/categories/:id` | Baja lógica sin borrar productos; MANAGE_PRODUCTS |

Listas: `?limit=50&after_id=123`, límite 1–100, orden estable por ID. Respuesta
`{items: [...], next_after_id: number | null}`. Seguir el cursor hasta null.
`?status=all` incluye inactivos y exige sesión con MANAGE_PRODUCTS. No hay búsqueda avanzada aún.

Producto nuevo mínimo:

```json
{"internal_code":"LOCAL-001","common_name":"Planta demo","category_id":1,"price_cents":12500,"unit":"maceta"}
```

Opcionales: `barcode`, `scientific_name` (admiten null), `description`, `unit`
(`pieza`, `maceta`, `charola`, `bolsa`, `kg`), `is_active` (booleano).
PATCH usa esos mismos campos; rechaza objetos vacíos y campos desconocidos.
Categoría: `{"name":"Exterior","description":"Plantas de exterior"}`.
Existen `/api/v1/auth/login`, `/api/v1/auth/me` y `/api/v1/auth/logout`;
ver [contrato y bootstrap](backend-identity.md). No hay endpoints para administrar
usuarios ni para inventario, ventas o cobros en esta fase.

Escritura desde PowerShell, sin guardar el token en scripts ni en el historial:

```powershell
$tokenSeguro = Read-Host 'access_token obtenido al iniciar sesión en la API' -AsSecureString
$credencial = [System.Net.NetworkCredential]::new('', $tokenSeguro)
$cabeceras = @{ Authorization = "Bearer $($credencial.Password)" }
Invoke-RestMethod -Uri http://127.0.0.1:3001/api/v1/products -Method Post `
  -Headers $cabeceras -ContentType 'application/json' `
  -Body '{"internal_code":"LOCAL-001","common_name":"Planta demo","category_id":1,"price_cents":12500}'
Remove-Variable tokenSeguro,credencial,cabeceras
```

Errores estables: 400 `INVALID_INPUT`/`INVALID_JSON`, 401 `UNAUTHORIZED`,
403 `BROWSER_ACCESS_NOT_ENABLED`, 404 `NOT_FOUND`, 409 `DUPLICATE` o
`REFERENCE_CONFLICT`, 413 `BODY_TOO_LARGE`, 415 `JSON_REQUIRED`,
403 `FORBIDDEN`, 429 `LOGIN_RATE_LIMITED`,
503 `DATABASE_UNAVAILABLE`/`SERVICE_UNAVAILABLE`.
Los mensajes SQL y las credenciales no se devuelven ni registran.

## Autorización propia de API

Las sesiones y capacidades de MariaDB reemplazan por completo el token local.
No hay fallback, JWT de Supabase ni bypass mediante `LOCAL_CATALOG_WRITE_TOKEN`.
El catálogo mantiene las reglas MANAGE_PRODUCTS/MANAGE_PRICES del contrato
anterior. Las solicitudes con Origin todavía se rechazan: la integración web,
el almacenamiento de sesión y el corte operativo se harán coordinadamente.
No exponer este Compose de desarrollo a Internet; falta validar la integración
de los consumidores antes de su corte operativo. MariaDB y la API
ya se verificaron con datos sintéticos locales. Ver [identidad](backend-identity.md).

## Esquema y decisiones

| Tabla | Relación y propósito |
|---|---|
| branches | Sucursal identificada por código único |
| roles | Los seis roles actuales |
| users | Perfil y futura identidad; FKs a sucursal y rol |
| categories | Clasificación única por nombre |
| products | FK a categoría; código y barcode únicos; baja lógica |
| inventory | Relación producto/sucursal; par único, cantidades DECIMAL(14,3) |
| sales | FK a sucursal y creador; totales coherentes, folio único |
| sale_items | FKs a venta/producto, snapshot de nombre/precio, cantidad decimal |
| cashier_payments | FKs a venta/cajero; un pago por venta, clave idempotente única |

Todas las PK son INT UNSIGNED AUTO_INCREMENT. Solo hay índices de PK, FK y
unicidad necesarios. InnoDB, utf8mb4 y fechas UTC. No se borran dependencias en
cascada. `users` incorpora el perfil para evitar una relación 1:1 innecesaria.
`user_roles` no se necesita: en el esquema vigente `user_id` ya es su PK y
permite un solo rol. `users.role_id` conserva esa cardinalidad. La migración
`002_identity` agrega `permissions`, `role_permissions` (relación muchos a muchos
necesaria), `auth_sessions`, `auth_login_limits` y `schema_migrations`, todas con
PK entera autoincremental. Las capacidades se leen en el servidor; no deben
deducirse solo del rol en los clientes. No se importan contraseñas ni sesiones de Supabase.

Seed: una sucursal, seis roles, un usuario inactivo sin contraseña utilizable,
dos categorías, dos productos y sus existencias. Ventas/pagos quedan vacíos.
Las nueve tablas son una base inicial, no equivalencia funcional del esquema
PostgreSQL. Aún faltan historial de inventario, claims, recuperación idempotente,
precios/promociones, descuentos, imágenes, ventas offline, pedidos, devoluciones
y cortes. Los CHECK no garantizan sumas entre partidas ni transiciones de venta:
esa lógica exigirá transacciones y bloqueo en la API antes de habilitar esos
módulos. La cuenta de ejecución actual no puede acceder a esas tablas.

## Siguiente módulo y VPS

1. La validación local de identidad/MariaDB, bootstrap y actualización de
   volúmenes está completada para los escenarios documentados. Mantener el login
   existente hasta completar el corte de consumidores.
2. Completar catálogo operativo (imágenes, precios y promociones) y preparar
   importación explícita con correspondencias de IDs conservadas fuera de las
   PK. No convertir UUID a entero mediante cast, ni equiparar códigos o nombres.
3. Hacer el corte coordinado de catálogo y consumidores de pedidos/ventas. No
   conectar productos locales al checkout Supabase. Retirar el servicio antiguo
   del módulo al completar su corte; no dejar selectores permanentes de motor.
4. Migrar inventario y luego ventas/caja con atomicidad, idempotencia, aislamiento
   por sucursal, auditoría y pruebas concurrentes antes de retirar sus RPC.

Para VPS falta: autenticación operativa, TLS/reverse proxy, orígenes autorizados,
secretos del servidor, imágenes fijadas por digest, respaldos/restauración
probados, migraciones incrementales y observabilidad. Conservar la base sin
puerto público y desplegar solo con autorización del destino. Esta fase no
contrata ni publica nada y no certifica compatibilidad ejecutada con MySQL 8;
el motor elegido y probado en esta sesión es MariaDB 11.4.13.

La migración 008 agrega [importación local de imágenes](backend-image-import.md)
al comando de catálogo existente. Requiere un manifiesto revisado y archivos
locales; conserva IDs al repetir y bloquea conflictos. Los consumidores y sus
flujos de autenticación, ventas y caja permanecen sin cambios.

La [preparación del cambio de catálogo Web](web-catalog-cutover.md) añade filtros
`search` y `category_id` al GET de productos existente, conservando la paginación
por ID y los permisos. Incluye contratos pendientes y reversión; no habilita
acceso desde navegador ni cambia el consumidor Web.

La migración 009 agrega [cotización y recepción idempotente de pedidos](backend-web-orders.md).
Aplicarla antes de actualizar API: health ahora exige 009. Los pedidos nuevos
permanecen PENDING, sin operaciones de inventario ni caja y sin cambio de clientes.

La migración 010 incorpora [administración auditada de pedidos](backend-web-order-admin.md).
Health exige ahora 014. Consulta por capacidades, cambios dentro de la sucursal
activa del usuario y control de revisión; completar entrega sigue bloqueado.

La migración 011 incorpora [inventario por sucursal](backend-inventory.md):
bitácora de apertura, recepciones idempotentes, consultas y conciliación de
conteos físicos. No activa movimientos de inventario de ventas; caja y venta
siguen en Supabase.

La migración 012 incorpora [ventas y partidas](backend-sales.md): envío atómico
a `SENT_TO_CASHIER`, precios y promociones del servidor, snapshots y recuperación
idempotente. El cobro se incorpora en 013; cambio de consumidores pendiente.

La migración 013 incorpora [caja y pagos](backend-cashier.md): reservas auditables,
cobro atómico por sucursal y recuperación de recibo. Health exige 013; Web y
Android mantienen sus flujos Supabase hasta el cambio coordinado de consumidores.

La migración 014 agrega [pedido→caja→pago→entrega](backend-web-order-checkout.md),
con venta única, snapshots preservados y guardas SQL de cancelación/completado.
Health exige 014. Devoluciones y cortes quedan para los próximos bloques.

Referencias técnicas: [mysql2](https://sidorares.github.io/node-mysql2/docs/documentation),
[inicialización y variables de MariaDB](https://mariadb.com/docs/server/server-management/automated-mariadb-deployment-and-administration/docker-and-mariadb/mariadb-server-docker-official-image-environment-variables),
[healthcheck oficial](https://mariadb.com/docs/server/server-management/automated-mariadb-deployment-and-administration/docker-and-mariadb/using-healthcheck-sh).
# Bloque 015: devoluciones (2026-09-30)

La API exige ahora la migración `015_sale_refunds` en `/health`.
Consulta y registro de devoluciones totales:
`GET/POST /api/v1/cashier/sales/:id/refunds`.
Contrato, validaciones y límites: [backend-refunds.md](backend-refunds.md).
Se conservan los consumidores Supabase y no se activa descuento/reposición de stock.

## Bloque 016: cortes de caja (2026-09-30)

Health exige ahora `016_cashier_closings`. La API incorpora previa, cierre,
consulta y recuperación de cortes por cajero/sucursal, asignando los IDs exactos
de pagos y devoluciones. Ver [backend-closings.md](backend-closings.md) para
contrato, comandos y evidencia, incluido el fallo intermitente observado en la
prueba preexistente de inventario. Web/Android siguen usando Supabase.

## Bloque 017: inventario por venta y devolución (2026-09-30)

Health exige `017_sale_inventory`. Se incorpora activación explícita por sucursal,
descuento al confirmar pago y reposición basada en movimientos reales de venta.
La migración conserva por defecto todas las sucursales sin activar y no modifica
ventas históricas. Se corrigió el bloqueo de recepciones concurrentes observado
en 016; la suite final completa pasó 17/17. Contrato y comandos en
[backend-sale-inventory.md](backend-sale-inventory.md). Web/Android siguen en Supabase.

## Bloque 018: clientes (2026-09-30)

Health exige `018_customers`. La API incorpora búsqueda, alta, consulta, edición
administrativa y desactivación de clientes globales con ID entero y auditoría.
Contrato, comandos y evidencia en [backend-customers.md](backend-customers.md).
La suite final completa pasó 18/18, aislando los archivos que instalan triggers de
fallo y conservando su concurrencia funcional. Web/Android siguen en Supabase.

## Bloque 019: personal y sucursales (2026-09-30)

Health exige `019_staff_branches`. Administración con jerarquía OWNER/ADMIN,
protección del último OWNER activo, auditoría atómica y revocación de sesiones
ante cambios reales de acceso. Sucursales con operaciones pendientes no se cierran.
Contrato y comandos en [backend-administration.md](backend-administration.md).
Integración final 19/19; consumidores continúan en Supabase.

## Bloque 020: compras y proveedores (2026-09-30)

Health exige `020_supplier_purchases`. Proveedores y presentaciones, borradores
con costos exactos, revisión/aprendizaje por proveedor y confirmación atómica con
recepciones en la bitácora existente. No altera precios de venta ni caja.
Contrato y comandos en [backend-purchases.md](backend-purchases.md).
53/53 unidades y 20/20 integraciones aprobadas. Consumidores siguen en Supabase.

## Bloque 021: reportes (2026-10-01)

API de ventas diarias por cobro y productos más vendidos por snapshots de ventas
PAID. Preserva VIEW_REPORTS/VIEW_ALL_SALES, fechas UTC y centavos exactos.
No requiere nueva migración ni altera caja. Contrato y comandos en
[backend-reports.md](backend-reports.md). Consumidores siguen en Supabase.

Validación final del bloque 021: Docker recuperado conservando sockets residuales
sin tocar volúmenes; 55/55 unidades y 21/21 integraciones aprobadas en Docker.
API saludable. Pendiente instalación integral desde cero y conexión de consumidores.

## Bloque 022: instalación integral nueva (2026-10-01)

Instalación desde volúmenes vacíos verificada: corregido cargador de triggers
compuestos del cliente MariaDB, sin cambiar migraciones SQL. 39 tablas,
bootstrap/login OWNER, persistencia tras stop/up, 55/55 unidades y 21/21
integraciones aprobadas. Guía para Tony en
[backend-fresh-install.md](backend-fresh-install.md). Pendiente conexión de
consumidores, importación real y VPS; no hubo publicación ni despliegue.

## Bloque 023: canal Web/API (2026-10-01)

Proxy Vite de mismo origen y WEB_ORIGIN explícito comprobados con MariaDB:
catálogo, sesión demo y reportes, conservando autenticación/capacidades sin CORS.
57/57 unidades backend, 21/21 integraciones y 361/361 pruebas Web aprobadas.
Ver [backend-browser-connection.md](backend-browser-connection.md).
No se cambiaron aún los servicios UI: consumidores siguen Supabase hasta un
corte coordinado de IDs, carrito/pedidos, administración e identidad.

## Actualización: cuentas de personal (2026-10-01)

La API permite alta administrativa de personal y restablecimiento de contraseñas con auditoría y revocación transaccional. Migración SQL 021. Consultar [backend-staff-accounts.md](backend-staff-accounts.md) para contratos, permisos y evidencia. Web y Android conservan sus sesiones Supabase hasta la integración coordinada de sus consumidores.

## Cotización y consumidor de mostrador (2026-10-01)

Nuevo POST /api/v1/sales/quote autenticado con CREATE_SALES y sucursal tomada de la sesión; solo cotiza, sin escribir ventas. Quote/submit/recover comparan las cabeceras opcionales X-Expected-Actor-Id y X-Expected-Branch-Id antes de operar. ViveroWeb tiene consumidor de cotización, envío, conservación del intento y recuperación probado contra Docker local, todavía no invocado por UI. Sin cambios SQL. Contratos, comandos, evidencia y pendientes en ViveroWeb/docs/backend-web-counter-sales.md.

## Escaneo API preparado (2026-10-01)

Nuevo POST /api/v1/products/scan autenticado con VIEW_CATALOG, código exacto interno/barcode, precio efectivo del servidor y rechazo de ambigüedad. Sin cambios de tablas/índices. Consumidor Web integrado al servicio de mostrador preparado, todavía no invocado en UI; Web y Android visibles siguen en Supabase. Contratos y evidencia en ViveroWeb/docs/backend-web-product-scan.md.
