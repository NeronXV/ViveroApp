# Administración de pedidos — migración 010

La API oficial incorpora consulta, detalle y cambios de estado auditados. Los
consumidores siguen en Supabase. Este bloque no crea ventas, cobros, reservas ni
movimientos de inventario; tampoco habilita acceso desde navegador.

## Endpoints y permisos

Todos requieren Bearer de una sesión propia de API y capacidades vigentes del
servidor. Una sesión Supabase no funciona como token de API.

| Ruta | Contrato |
|---|---|
| GET `/api/v1/admin/web-orders` | Bandeja con `schema_version`, `items`, `next_before_id` |
| GET `/api/v1/admin/web-orders/:id` | `schema_version`, `order`, `items`, `history` |
| PATCH `/api/v1/admin/web-orders/:id` | Cambia estado y agrega historial en la misma transacción |

Lectura: `VIEW_ALL_SALES` permite consulta global, incluso sin sucursal asignada.
`VIEW_BRANCH_SALES` limita a la sucursal propia activa. Sin esas capacidades se
rechaza el acceso; no se decide por nombres de roles en el código.

Escritura: requiere una de esas capacidades **y** pertenecer a la sucursal activa
del pedido. Tener consulta global no permite cambiar pedidos de otras sucursales.
Esta regla conserva el aislamiento por sucursal definido para la API nueva; es
más restrictiva que la función Supabase original con acceso global. No modificar
la asignación de sucursal de un usuario como atajo desde el cliente.

La bandeja admite `limit` 1–100 (50 por defecto), `before_id` entero positivo,
`branch_id` opcional y `status` PENDING/CONFIRMED/READY/CANCELLED. Orden por ID
descendente; el cursor pertenece al conjunto filtrado. Reiniciarlo al cambiar
filtros. Claves repetidas/desconocidas se rechazan. Un usuario local no puede
consultar otra sucursal mediante el filtro (403). Detalle o cambio fuera de su
alcance devuelve 404, igual que un pedido inexistente.

La bandeja contiene datos de contacto y totales; solo se devuelve tras autorizar.
El detalle agrega las partidas guardadas y el historial ordenado por revisión.
Ninguna respuesta administrativa expone idempotency_hash, request_hash o la clave
de recuperación. Las respuestas continúan con Cache-Control: no-store.

## Estados, concurrencia e historial

Transiciones permitidas:

- PENDING → CONFIRMED o CANCELLED.
- CONFIRMED → READY o CANCELLED.
- READY → CANCELLED.
- CANCELLED es terminal.

COMPLETED devuelve 409 `WEB_ORDER_PAYMENT_REQUIRED`: todavía no existe un pago
MariaDB que habilite completar la entrega. No equivale a confirmar ni a preparar.
La restricción SQL también impide almacenar COMPLETED en esta fase.

Ejemplo de cuerpo PATCH:

```json
{"status":"CONFIRMED","expected_revision":0,"observation":"Pedido revisado"}
```

Los tres campos son obligatorios; observation puede ser null, se recorta y admite
hasta 500 caracteres sin controles. No se aceptan campos de importes, contacto,
sucursal ni actor. El actor proviene de la sesión verificada por el servidor.

Cada pedido empieza con revision=0 y un evento PENDING sin actor administrativo.
La migración registra ese evento para pedidos 009 preexistentes usando su fecha
original; no inventa quién los creó. Nuevos pedidos insertan el evento junto con
cabecera y partidas. La revisión y updated_at cambian solo al cambiar el estado.

La API bloquea la fila, comprueba expected_revision, actualiza y escribe el
evento atómicamente. Cada evento tiene PK entera, FK al pedido/usuario y único
(order_id, revision). Guarda estado anterior/nuevo, actor, observación y fecha
UTC con microsegundos. Si falla el historial, se revierte el cambio de estado.

Repetir la última transición con la revisión anterior, mismo actor y observación
normalizada devuelve `idempotent_replay:true` sin otro evento. Si ya hubo otra
transición o cambia el contenido/actor, devuelve 409 `WEB_ORDER_VERSION_CONFLICT`.
Releer el detalle tras un resultado incierto; no incrementar la revisión a ciegas.
Una transición no permitida devuelve 409 `WEB_ORDER_STATUS_INVALID`.

Respuesta PATCH: id, status, revision, idempotent_replay. El detalle permite
comprobar la fecha y el actor. La recuperación pública sigue devolviendo un
recibo mínimo y refleja el estado actual, nunca contacto o historial.

La cuenta HTTP solo puede actualizar status, revision y updated_at en web_orders;
no importes, contactos ni hashes. El historial permite SELECT/INSERT, nunca
UPDATE/DELETE. No se añadieron permisos nuevos ni índices avanzados.
Los pedidos cancelados dejan de contar para el límite público por contacto,
siguiendo la regla de Supabase; repetir su creación no los reactiva.

## Probar localmente

Aplicar antes de recrear API: health exige 010. Desde ViveroApp:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up --build -d --wait api
npm --prefix backend run check
npm --prefix backend test
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Crear un pedido sintético según [recepción de pedidos](backend-web-orders.md),
obtener una sesión local autorizada y usar su token sin imprimirlo:

```powershell
$headers = @{ Authorization = "Bearer $token" }
$base = 'http://127.0.0.1:3001/api/v1/admin/web-orders'
$page = Invoke-RestMethod -Uri "$base`?status=PENDING&limit=10" -Headers $headers
$detail = Invoke-RestMethod -Uri "$base/$orderId" -Headers $headers
$body = @{ status = 'CONFIRMED'; expected_revision = $detail.order.revision; observation = $null } | ConvertTo-Json
Invoke-RestMethod -Method Patch -Uri "$base/$orderId" -Headers $headers -ContentType 'application/json' -Body $body
```

El ejemplo supone API_PORT=3001 y variables token/orderId obtenidas localmente.
La suite no necesita cuentas reales ni bootstrap manual. No es una validación
de la UI Web ni del recorrido de caja.

Siguiente bloque: inventario por sucursal, movimientos y conciliación; resolver
reservas y entrega idempotente de pedidos a caja antes de conectar consumidores.

## Evidencia de esta entrega

- `npm run check`: correcto. `npm test`: 27/27.
- Integración HTTP/MariaDB: 10/10 sobre volumen existente y las mismas 10/10 en
  instalación nueva. Se amplió el caso de pedidos con administración: permisos,
  paginación, aislamiento entre sucursales, lectura global sin bypass de escritura,
  sucursal inactiva, concurrencia, revisiones, historial, cancelación terminal y
  bloqueo de COMPLETED. Fallo inyectado de historial revertido mediante rollback.
- La primera ejecución de 010 falló por tratar un CHECK de columna como una
  restricción nombrada. Se inspeccionó el esquema, sin cambios parciales, y se
  corrigió con MODIFY COLUMN. La versión final se aplicó correctamente.
- Verificador estático Supabase: 134 comprobaciones. Sin pgTAP porque no cambió
  SQL PostgreSQL; SQL nuevo ejecutado en MariaDB. No se probaron datos reales,
  VPS, MySQL 8 ni carga sostenida. El relleno inicial de historial está definido
  en SQL, pero no se ensayó con un lote de pedidos 009 preexistentes no vacío.
- Sin builds Android/Web ni cambios de UI: sus fuentes permanecieron intactas.

Archivos de este bloque:

- `backend/src/web-order-admin.js`, `backend/src/web-orders.js`, `backend/src/app.js`.
- `backend/test/web-orders.test.js`, `backend/test/integration.test.js`, `backend/package.json`.
- `database/mysql/migrations/010_web_order_admin.sql`, `infra/docker/compose.yaml`.
- `docs/backend-web-order-admin.md`, `docs/backend-web-orders.md`,
  `docs/backend-api-mariadb.md`, `docs/supabase-migration-map.md`, `docs/web-catalog-cutover.md`.

Cambios anteriores de IDE, AGENTS, README y módulos/documentación previos
preservados. Rama main, HEAD a4621f6d42deb735714c047b05789ee83760939a sin cambios.
Sin staging, commit, push, despliegue ni operaciones remotas.
