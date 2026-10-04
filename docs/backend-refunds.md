# Devoluciones totales en Backend API / MariaDB

Bloque 015, validado localmente el 2026-09-30. El contrato de referencia es
`supabase/migrations/202609080002_cashier_closings_refunds.sql`; no se cambia Supabase.

## Contrato

`GET /api/v1/cashier/sales/:id/refunds` consulta folio, importe pagado,
`already_refunded` y `restock_available: false`.

`POST /api/v1/cashier/sales/:id/refunds` registra una devolución total en persona.
Requiere Bearer y `Idempotency-Key` de 64 caracteres hexadecimales minúsculos:

```json
{"reason":"Devolución de demo","method":"CASH","restock":false,"money_returned":true}
```

Se exigen OPERATE_CASHIER y MANAGE_DISCOUNTS, usuario activo y sucursal propia
activa. No hay excepción para administradores globales. El motivo contiene de 5
a 300 caracteres; métodos CASH, CARD o TRANSFER. `money_returned: true` confirma
que el operador ya devolvió el dinero; la API no ejecuta cargos ni transferencias.
No acepta importes del cliente: devuelve exactamente `amount_due_cents` del pago,
excluyendo efectivo recibido de más y cambio. Primera respuesta 201, reintento
idéntico 200; cambia de operación o datos con la misma clave: 409. La misma venta
no puede devolverse dos veces, aunque se use otra clave. Un reintento del POST
recupera el recibo si se perdió la respuesta.

`sale_refunds` tiene ID entero autoincremental, FKs reales, claves únicas por venta,
pago e idempotencia y centavos enteros. El rol SQL de la API solo puede leer e
insertar; no modificar ni borrar el registro. Un trigger comprueba que importe,
pago, venta PAID y sucursal corresponden. Ventas y pagos originales se conservan.

Las operaciones bloquean primero el pedido vinculado y después la venta, dentro
de transacciones READ COMMITTED. La entrega también usa READ COMMITTED para
comprobar devoluciones confirmadas tras esperar un bloqueo. API y trigger impiden
pasar un pedido a COMPLETED cuando su venta tiene devolución. Una devolución de
una entrega ya completada conserva el historial de esa entrega.

## Inventario y consumidores

El inventario aún no admite movimientos SALE y las ventas no descuentan stock.
`restock` conserva la intención del operador, pero no añade existencias ni genera
movimientos. Activar stock por venta exigirá ampliar este contrato para devolver
solo cantidades realmente descontadas; nunca sumar las partidas sin evidencia.
No hay devoluciones parciales, cortes ni integración con bancos en este bloque.
Web y Android siguen consumiendo sus contratos de Supabase; no se hace cutover.

## Comandos desde la raíz

Con `.env` local según la plantilla, sin credenciales operativas:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
npm --prefix backend test
npm --prefix backend run check
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
git diff --check
```

## Evidencia de esta sesión

- Migración 015 aplicada sobre el volumen sintético existente, sin reset.
- 42 pruebas unitarias aprobadas (Node con `--test-isolation=none` local).
- 15 pruebas de integración Docker aprobadas: permisos/sucursal, cantidad del pago
  sin cambio, concurrencia 201/200, conflicto de claves, devolución única,
  reversión de inserción fallida, bloqueo de entrega API/SQL y auditoría inmutable.
- Verificación de sintaxis y 134 comprobaciones estáticas Supabase aprobadas;
  estas últimas protegen los contratos conservados y no verifican el SQL MariaDB.
- Los intentos iniciales detectaron errores en fixtures y conteo de tablas,
  corregidos. Las repeticiones agotaron el límite de login del contenedor de
  pruebas; se expiró solo el contador de su IP en el entorno sintético local.
- Fixtures y trigger temporal se eliminaron al terminar. No se probaron instalación
  de volumen vacío, datos reales, Android ni pgTAP: no cambian Android/PostgreSQL.
  No hubo commit, push ni despliegue.

Siguiente módulo: cortes de caja, enlazando los IDs exactos de pagos y devoluciones
con cada corte para evitar perder operaciones simultáneas.
## Actualización 017 (2026-09-30)

Las limitaciones de stock descritas arriba corresponden al bloque 015. Desde 017,
`restock: true` repone solo salidas SALE registradas; si la venta no descontó,
no suma nada. `restock_available` consulta esa evidencia y la ausencia de devolución.
Contrato y pruebas actuales en [backend-sale-inventory.md](backend-sale-inventory.md).
Los consumidores Web/Android permanecen en Supabase.
