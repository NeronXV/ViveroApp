# Ventas y partidas en Backend API/MariaDB

La migración `012_sales_submission` amplía las tablas iniciales `sales` y
`sale_items` y agrega historial de estados. Los IDs siguen siendo enteros
autoincrementales. La API calcula precios y promociones con el mismo resolvedor
SQL utilizado por catálogo y pedidos; no recibe precios unitarios del cliente.
Conserva snapshots de nombre, código, precio de lista, precio cobrado y promoción.

## Contrato

`POST /api/v1/sales` requiere sesión Bearer, `CREATE_SALES`, sucursal activa y
encabezado `Idempotency-Key` con 32 bytes aleatorios expresados en 64 caracteres
hexadecimales minúsculos. Cuerpo exacto:

```json
{
  "items": [{ "product_id": 1, "quantity": 2 }],
  "expected_total_cents": 1800
}
```

Se admiten 1–25 productos distintos, con cantidades enteras entre 1 y 100000.
La sucursal y el creador vienen de la sesión; folio e ID los genera el servidor.
Si el total calculado cambia, responde 409 `SALE_PRICE_CHANGED` sin guardar venta.
La escritura guarda venta, partidas y los eventos DRAFT/SENT_TO_CASHIER en una
sola transacción. Como el RPC anterior, subtotal y total equivalen a la suma de
precios efectivos y descuento de venta es cero; los descuentos de producto se
conservan en sus snapshots. Los importes son centavos enteros, acotados a enteros
seguros de JavaScript y calculados mediante BigInt para acumular cantidades.

La clave se guarda como SHA-256 delimitada por usuario. El bloqueo del usuario
en `auth.withAccess` serializa envíos de la misma persona, incluso con sesiones
distintas. READ COMMITTED permite leer precios actuales tras bloquear productos.
Un reintento idéntico devuelve 200 y el mismo recibo, sin recalcular precios ni
duplicar partidas. La clave con distinto contenido/sucursal genera 409. Una
operación nueva devuelve 201. No confiar en memoria del proceso para idempotencia.

`POST /api/v1/sales/recover` con cuerpo `{}` y la misma clave recupera el recibo
del creador en su sucursal activa. Permite resolver una respuesta perdida tras
el commit sin crear otra venta. Conservar la clave en el futuro outbox hasta
obtener confirmación; no crear otra clave al recibir un error de red.

`GET /api/v1/sales?limit=50&before_id=123` y `GET /api/v1/sales/123` exigen
`CREATE_SALES` y `VIEW_OWN_SALES`. Solo consultan ventas del usuario y su sucursal;
el detalle incluye partidas e historial. No hay acceso general de caja todavía.

## Alcance y conservación

Los clientes Android/Web continúan usando Supabase y sus UUID. Esta API no acepta
esos UUID como productos enteros ni convierte borradores/outbox existentes.
No se conecta catálogo nuevo a caja antigua. Asignación de cliente, mayoreo,
descuento manual de venta, cobro, devolución, reservas de caja y entrega de pedido
web requieren sus respectivos contratos posteriores. No hay movimiento de stock
al enviar una venta a caja; se conserva la fase gradual del inventario.

La migración conserva filas existentes y completa precio de lista desde precio
unitario; código/promoción de filas antiguas pueden ser nulos y no se inventa
historial retroactivo. Runtime puede leer e insertar ventas/partidas/historial,
pero no actualizar ventas, cobrar ni acceder a pagos.

Para aplicar y validar localmente con `.env` preparado, desde la raíz:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Health exige la migración 012. No borrar el volumen para actualizar. Las pruebas
de integración usan fixtures sintéticos y los eliminan por sus propios IDs.
Las pruebas Android/Supabase no aplican: sus fuentes y contratos no cambiaron.
Siguiente bloque: caja y pagos con reservas de cobro e idempotencia.

## Validación ejecutada — 2026-09-30

En `vivero-validation-20260929`, usando el archivo ignorado de credenciales
sintéticas existente, se aplicó la migración 012 al volumen persistente y se
reconstruyó la API. Las 12 pruebas de integración del perfil Docker aprobaron.
La prueba de ventas verificó promociones del servidor, snapshots, dos envíos
simultáneos con el mismo recibo, conflicto de contenido, recuperación, aislamiento
entre usuarios, rechazo sin sucursal, saldo intacto y ausencia de permisos de
actualización de ventas/pagos. Un trigger temporal restringido al producto de
prueba forzó un fallo de partida: se confirmó rollback sin venta adicional.
El trigger y los fixtures se retiraron al finalizar.

Las 37 pruebas locales pasaron con `--test-isolation=none`. Esta evidencia no
certifica una instalación desde volumen vacío, importación real, corte de clientes,
ventas offline migradas ni despliegue. Supabase no fue modificado.
