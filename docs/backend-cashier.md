# Caja y pagos en Backend API/MariaDB

La migración `013_cashier_payments` agrega reservas auditables de cobro y amplía
`cashier_payments` con sucursal, reserva y datos para detectar reintentos. Los
IDs son enteros autoincrementales; las relaciones tienen FKs. Una clave única
generada mantiene como máximo una reserva abierta por venta, y `sale_id` único
en pagos impide cobrar la venta dos veces.

Todas las rutas requieren sesión Bearer, capacidad `OPERATE_CASHIER` y sucursal
activa. La sucursal se deriva del usuario; no se recibe un ID de sucursal del
cliente y no hay bypass administrativo. Las operaciones bloquean la venta en
una transacción READ COMMITTED antes de consultar o modificar su reserva/pago.

## Rutas y cuerpos

Base: `/api/v1/cashier/sales`.

- `GET /`: pendientes `SENT_TO_CASHIER` de la sucursal, con `limit` (1–100) y
  cursor `before_id` opcionales.
- `GET /:id`: venta y partidas de la sucursal.
- `POST /:id/claim`: `{ "claim_token": null }` crea una reserva de cinco minutos
  o devuelve la propia vigente; un cajero distinto recibe conflicto. Con el
  token propio se renueva cinco minutos desde la hora SQL del servidor. Responde
  con token, propietario, fechas UTC, `server_time` y `renewed`.
- `POST /:id/release`: `{ "claim_token": "<64 caracteres hex>" }` cierra la
  reserva propia con motivo RELEASED o EXPIRED. Repetir después de cerrarla
  devuelve CLAIM_NOT_OWNED, como en el RPC anterior.
- `POST /:id/payments`: cuerpo exacto con `claim_token`, `method`,
  `amount_received_cents` y `reference`.
- `POST /:id/payment-result`: cuerpo `{}` y la clave original de pago recuperan
  el recibo PAID del mismo cajero y sucursal; si falta, responde 404.

`payments` y `payment-result` requieren `Idempotency-Key`: 32 bytes aleatorios
expresados en 64 caracteres hexadecimales minúsculos. La base conserva un digest
delimitado por cajero y un hash del contenido. Un reintento con el mismo método,
importe solicitado, referencia, venta y sucursal devuelve el mismo pago (200),
aunque la reserva ya esté consumida. Contenido diferente devuelve 409. Un pago
nuevo devuelve 201. Ante respuesta perdida se recupera con la clave original;
no se genera otra clave para intentar resolver la incertidumbre.

Ejemplo efectivo:

```json
{
  "claim_token": "<token devuelto por claim>",
  "method": "CASH",
  "amount_received_cents": 1200,
  "reference": null
}
```

Para efectivo el importe debe ser un entero seguro positivo suficiente para
cubrir el total. La API calcula el cambio y no acepta referencia. CARD y TRANSFER
reciben importe `null`, registran el total exacto y cambio cero. Transferencia
exige referencia de hasta 120 caracteres. Tarjeta admite referencia opcional de
hasta 64 caracteres y rechaza formatos de PAN/CVV, como el contrato anterior.
Se registra un cobro realizado por el cajero; no se integra una pasarela bancaria.

## Atomicidad y alcance

Antes de cobrar se verifican estado, total positivo, suma/coherencia de partidas,
reserva propia y vencimiento en hora SQL. Pago, estado PAID, consumo de reserva
(CONFIRMED) e historial SENT_TO_CASHIER→PAID se guardan juntos. Una reserva vencida
no autoriza pago; solicitar una nueva sin token cierra la antigua con EXPIRED.

La migración conserva pagos anteriores y completa su sucursal desde la venta;
no inventa reservas ni claves de reintento para registros históricos. Runtime
recibe INSERT/SELECT de pagos y permisos limitados de actualización de estado
y reserva; no puede modificar importes de ventas, borrar pagos o modificar roles.

Web y Android continúan usando Supabase. No se migran pagos reales ni se cambia
el outbox. La fase gradual mantiene las salidas automáticas de stock apagadas,
también en MariaDB: confirmar pago no agrega movimientos SALE. Devoluciones,
cortes, entrega de pedidos web y activación operativa quedan para próximos bloques.

## Validación — 2026-09-30

Migración 013 aplicada al volumen sintético existente de
`vivero-validation-20260929`. Las 13 pruebas de integración Docker y 39 pruebas
locales aprobaron. Se verificaron sesión real, aislamiento entre sucursales y
capacidades, dos cajeros compitiendo por una reserva, renovación, vencimiento,
liberación, CASH/CARD/TRANSFER, importe insuficiente, dos reintentos simultáneos,
conflicto de contenido, recuperación del recibo y un solo pago/evento PAID.
Triggers temporales forzaron fallos al insertar pago e historial; se comprobó rollback manteniendo
venta pendiente y reserva sin consumir. Fixtures y trigger se retiraron por sus
propios IDs. No se ejecutaron pruebas Android ni pgTAP porque no cambió ese código
ni SQL Supabase. No acredita instalación desde volumen vacío ni despliegue.

Con `.env` local preparado, desde la raíz:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

La sesión usó el archivo ignorado de credenciales sintéticas
`tmp/backend-validation-20260929.env` y `-p vivero-validation-20260929` en esos
comandos. Health exige ahora 013. Conservar los volúmenes al actualizar.
Siguiente bloque: operaciones posteriores y entrega de pedidos a caja,
devoluciones y cortes, en pasos verificables.
