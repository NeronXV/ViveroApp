# Pedidos web enviados a caja — API/MariaDB

La migración `014_web_order_checkout` vincula `sales.web_order_id` mediante FK
y unicidad a `web_orders`. Un pedido genera como máximo una venta presencial.
No se añade una tabla intermedia ni un motor alternativo. Los consumidores
actuales continúan en Supabase hasta el cambio coordinado.

## Envío y recuperación

`POST /api/v1/admin/web-orders/:id/send-to-cashier`, con sesión Bearer y cuerpo
exacto `{}`, requiere `OPERATE_CASHIER`, `VIEW_BRANCH_SALES` o `VIEW_ALL_SALES`
y una sucursal activa asignada. Incluso administración global solo puede enviar
pedidos de su sucursal actual. Un ID ajeno responde 404.

El servidor bloquea el pedido dentro de la transacción de acceso, comprueba
CONFIRMED/READY y valida productos/categorías activos y sumas de partidas.
Conserva nombre, código, cantidades, precios de lista/efectivos y promoción
aceptados por el pedido. No recalcula precios al cambiar el catálogo.

Se crea venta SENT_TO_CASHIER, partidas e historial DRAFT/SENT_TO_CASHIER en la
misma transacción. El folio lo genera el servidor. La FK única y el bloqueo de
pedido protegen reintentos concurrentes entre personas/API distintas. El ID
del pedido es la identidad de esta operación; no requiere otra clave de pago.

Responde 201 al crear y 200 al reintentar, con `schema_version`, `order_id`,
`sale_id`, `folio`, `status`, `total_cents` e `idempotent_replay`. Si se pierde
la respuesta, repetir el mismo POST recupera la venta, también después de cobrar
o completar el pedido. Bandeja/detalle administrativos incluyen `cashier_sale_id`.

## Cobro y entrega

La venta conserva subtotal de lista, descuento y total del pedido. Caja valida
sumas de lista/efectivas con centavos exactos y cobra el total mediante las rutas
de [reservas y pagos](backend-cashier.md). MariaDB devuelve sumas como DECIMAL;
se comprueba que la fracción de centavos sea cero antes de convertir a BigInt.
Las ventas normales conservan su validación de subtotal efectivo del bloque 012.

Después de enviar a caja se bloquea CANCELLED con
`WEB_ORDER_ALREADY_IN_CASHIER`. La ruta PATCH de administración admite
READY→COMPLETED solo con una venta vinculada PAID y su pago persistido; conserva
`expected_revision`, autor e historial, y recuperación de reintentos. El cobro
no completa automáticamente el pedido: entrega física y pago son pasos distintos.
Un trigger MariaDB impide cancelación vinculada o COMPLETED sin pago incluso si
se intenta cambiar el estado directamente mediante SQL.

Sin pago responde `WEB_ORDER_PAYMENT_REQUIRED`. No se implementan devoluciones,
cortes, cancelación de venta enviada, stock automático ni cambio de consumidores
en este bloque. No se importan datos ni claves UUID de Supabase.

## Preparación local

Desde la raíz, con `.env` local preparado:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Health exige 014. Conservar el volumen al actualizar. Compose incluye la migración
para nuevas instalaciones y la prueba `web-order-checkout-integration.test.js`
en el perfil test. Esta sesión utilizó únicamente el entorno sintético existente
`vivero-validation-20260929` y su archivo ignorado de credenciales en `tmp/`.
Pruebas Android y Supabase no aplican: sus fuentes y contratos no cambiaron.
Siguiente módulo recomendado: devoluciones y después cortes de caja.

## Evidencia ejecutada — 2026-09-30

Migración 014 aplicada al volumen sintético existente. Pasaron 40 pruebas
locales y las 14 pruebas de integración Docker. El escenario nuevo cubrió sesión
real y permisos/sucursal, rechazo de pedido pendiente, dos envíos simultáneos,
una única venta vinculada, snapshots intactos tras cambio de precio, cobro de un
pedido con descuento, bloqueo de cancelación/completado tanto en API como en SQL,
entrega tras PAID y replay después de completar. Un trigger temporal forzó un
fallo tardío de historial y se comprobó rollback; al retirarlo el reintento creó
la venta correctamente. Se rechazó también un producto desactivado.

La primera ejecución detectó el formato DECIMAL de la suma de precios de lista;
se corrigió la conversión exacta y la suite completa volvió a pasar. Fixtures
y trigger de prueba se eliminaron por sus propios IDs. Esta evidencia no acredita
una instalación desde volumen vacío, importación real, corte de consumidores ni
despliegue a VPS.
