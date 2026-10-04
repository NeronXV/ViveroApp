# Inventario por venta y devolución — bloque 017

Implementación oficial en Backend API/MariaDB, validada localmente el 2026-09-30.
Referencias vigentes: `202609080001_presential_checkout.sql` y
`202609080002_cashier_closings_refunds.sql` de Supabase. No cambian PostgreSQL ni
los consumidores Web/Android.

## Activación gradual

Las sucursales quedan desactivadas por defecto. Se conservan los saldos actuales
y las ventas históricas. El personal con MANAGE_INVENTORY en su sucursal activa
puede consultar `GET /api/v1/inventory/activation` y activar con:

```http
POST /api/v1/inventory/activation
Authorization: Bearer <sesion-local>
Content-Type: application/json

{"initial_count_confirmed":true}
```

La confirmación declara que ya se realizó el conteo físico inicial; no crea ni
inventa ese conteo. El registro guarda fecha y actor en `branches`, sin una tabla
de configuración paralela. Repetir devuelve el mismo registro con
`idempotent_replay: true`. No hay endpoint de desactivación en este contrato.
No activar una sucursal operativa sin conteo conciliado y cambio de consumidores.
Durante esta sesión se activó exclusivamente una sucursal sintética de pruebas,
eliminada al terminar.

## Pago y devolución

Al confirmar un nuevo pago, una sucursal activada genera un movimiento SALE
negativo por producto, sumando todas sus partidas. Se bloquean los saldos por
producto en orden y el trigger existente aplica su proyección. No se reserva
stock al crear el carrito, enviar a caja o confirmar un pedido web.

Falta de saldo: 409 `INVENTORY_INSUFFICIENT`. Se revierten pago, estado PAID,
movimientos, consumo de reserva e historial. Tras una recepción se puede repetir
el pago con la misma clave mientras su reserva siga vigente; si expiró, se debe
renovar/reclamar como establece caja. Un fallo posterior también revierte todos
los cambios. Un pago ya confirmado se recupera sin descontar otra vez y nunca
genera salidas retroactivas al activar inventario.

Una devolución con `restock: true` genera movimientos REFUND positivos, basados
solo en las salidas SALE registradas para esa venta. No repone a partir de las
partidas si no existe salida. Funciona incluso con productos desactivados o una
venta anterior al cambio de catálogo. `restock: false` devuelve dinero sin sumar
existencias. Reintentos y fallos conservan la transacción única de devolución.
La consulta de devolución muestra `restock_available` solo si hay salidas reales
y aún no se ha registrado la devolución.

El esquema conserva cantidades DECIMAL(14,3); las comparaciones usan milésimas
enteras BigInt, sin redondear. La API de ventas actual sigue aceptando cantidades
enteras, pero el descuento/reposición admite cantidades decimales válidas del
esquema, verificadas con fixtures de 0.500 unidades.

## Integridad y corrección del interbloqueo

Migración `017_sale_inventory.sql`: activación en `branches`, FKs `sale_id` y
`refund_id` en `inventory_movements`, unicidad por venta/producto o
devolución/producto y checks de tipo, signo, actor y referencia. Un trigger
comprueba pago, cantidad de partidas y sucursal para SALE; para REFUND comprueba
la devolución, su intención de reposición y cantidad realmente descontada.
No se crean nuevas tablas. Se concede únicamente UPDATE de los campos de
activación; la API continúa sin poder modificar directamente cantidades.

La ruta previa INSERT IGNORE → SELECT FOR UPDATE podía dejar a dos recepciones
con bloqueos compartidos de clave duplicada, intentando convertirlos a exclusivos
simultáneamente. Ahora se toma primero el bloqueo exclusivo de la sucursal, y
después el de producto/saldo, respetando el orden de las operaciones autenticadas.
Esto serializa el inventario de cada sucursal, una decisión deliberada para esta
fase local. No se introducen reintentos ciegos ni se ocultan errores de transacción.

## Pruebas realizadas

- Migración 017 aplicada al volumen sintético existente sin reset.
- 45 pruebas unitarias aprobadas y sintaxis de fuentes/prueba nueva correcta.
- 17 pruebas de integración Docker aprobadas en una ejecución completa final.
- Cobro sin activar; activación explícita y repetible; pago/devolución histórica
  sin modificar stock; partidas duplicadas agrupadas; cantidades fraccionarias;
  pago y devolución concurrentes idempotentes; falta de stock en segundo producto
  con reversión del primero; fallo tardío de historial; fallo tardío de reposición;
  reintento tras corregir la causa; dos cobros que compiten por el mismo saldo;
  devolución sin reposición; triggers rechazando referencias/cantidades inválidas.
- 12 parejas adicionales de recepciones simultáneas en la prueba de inventario:
  cada pareja creó una sola entrada; sin interbloqueos observados. Esto verifica
  el caso detectado, no constituye una garantía de ausencia de todo deadlock.
- El primer intento de bloqueo con ON DUPLICATE KEY UPDATE fue rechazado por
  MariaDB bajo los privilegios mínimos. Se sustituyó por bloqueo de sucursal,
  conservando los permisos del saldo. Las pruebas específicas y completas pasaron
  después del cambio.
- Se expiró solo el contador de login de la IP del contenedor sintético para la
  suite final; no se cambió la política de autenticación.
- 134 comprobaciones estáticas Supabase aprobadas: protegen los contratos
  existentes, no verifican esta migración MariaDB. `git diff --check` correcto.
- Fixtures y triggers temporales eliminados; API healthy con migración 017.

No se ejecutaron Android/pgTAP: no cambia código Android/PostgreSQL. No se probó
instalación desde volumen vacío, datos reales, corte de consumidores ni VPS.
No hubo commit, push ni despliegue. Se preservó el trabajo previo.

## Comandos desde la raíz

Usar `.env` local según la plantilla, sin secretos operativos:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
npm --prefix backend test
npm --prefix backend run check
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
git diff --check
```

Archivos de este bloque: `backend/src/inventory.js`, `stock.js`, `cashier.js`,
`refunds.js`, `app.js`; `database/mysql/migrations/017_sale_inventory.sql`;
`backend/test/inventory.test.js`, `inventory-integration.test.js`,
`stock-integration.test.js`; `backend/package.json`; `infra/docker/compose.yaml`;
esta guía, `docs/backend-api-mariadb.md`, `docs/supabase-migration-map.md`,
`docs/backend-refunds.md` y `docs/backend-inventory.md`.

Siguiente módulo recomendado: clientes en Backend/MariaDB. La integración de
consumidores continúa pendiente; no se amplía el alcance con funciones nuevas.
