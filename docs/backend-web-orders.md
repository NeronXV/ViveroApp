# Pedidos: cotización, recepción y recuperación

Etapa 3 local: [alias cortos y compatibilidad](backend-short-folios.md). El contrato
anterior permanece por defecto; `X-Vivero-Folio-Format: short-v1` solicita alias
persistidos, conservando la consulta privada del comprobante de etapa 2.

## Actualización local: comprobante público, 9 de octubre de 2026

API/MariaDB es el backend vigente de estos pedidos. El texto posterior conserva
el contexto histórico de la migración 009; no describe el estado actual de Web,
cobro/entrega ni los permisos añadidos por migraciones posteriores.

Se añade `POST /api/v1/web-orders/ticket`, con JSON `{}` y cabecera
`Idempotency-Key` de 64 caracteres hexadecimales minúsculos. Es una consulta:
no crea pedidos, ventas, pagos ni movimientos. No admite parámetros en la URL
ni un ID/folio en el cuerpo. Conserva el control de Origin y `Cache-Control:
no-store`. Una clave desconocida devuelve 404; una clave/cuerpo inválido, 400.
La clave secreta autoriza únicamente el pedido cuyo hash coincide.

Respuesta estricta:

```text
{ schema_version: 1,
  order: <recibo mínimo existente>,
  branch: { id, code, name },
  subtotal_cents, discount_cents,
  items: [{ product_id, product_name, quantity,
            list_price_cents, unit_price_cents, line_total_cents }] }
```

Las partidas y totales proceden de `web_order_items`/`web_orders`, con los nombres
y precios aceptados al confirmar; no se recotizan ni se leen del catálogo actual.
La sucursal corresponde al `branch_id` guardado en el pedido; su nombre/código
se consultan en `branches`. No hay historial de nombres de sucursal: si se
renombra antes de recuperar el comprobante, aparecerá su nombre vigente.
No se devuelven datos de contacto, notas, hashes ni la clave. Las respuestas de
crear/repetir/recuperar permanecen intactas para clientes existentes.

La Web consulta este detalle antes de liberar el intento y guarda una copia
validada del último comprobante sin clave secreta ni datos de contacto. Fallar
al consultar/guardar conserva el intento original para recuperar con la misma
clave. La copia sirve para reimprimir el pedido tal como se registró; no acredita
pago ni representa el estado actual de Caja. Solo se añade una lectura SQL y
una ruta; no requiere migraciones ni cambios Android. API debe actualizarse
antes que Web en un eventual despliegue autorizado.

Validado en ensayo Docker aislado: precios/nombres históricos tras editar el
catálogo, consultas repetidas sin mutaciones y rechazo de consulta por ID/folio
o clave ajena. Evidencia Web y PDF: `ViveroWeb/docs/public-order-ticket-stage2.md`.
Sin despliegue de esta actualización.

## Contexto histórico de la migración 009

La migración `009_web_orders` incorpora recepción de pedidos en la API oficial.
La [migración 010](backend-web-order-admin.md) amplía este bloque con consulta
por capacidad/sucursal, revisiones e historial atómico. Los límites PENDING-only
y SELECT/INSERT descritos abajo corresponden a 009; 010 permite confirmar,
preparar y cancelar, con UPDATE restringido a estado/revisión/fecha. Health exige
ahora 010. Cobro y entrega final siguen pendientes.
Es un bloque local preparatorio: Web/Android siguen usando Supabase. No hay corte
de consumidores, envío a caja, cobro, reserva ni descuento de inventario.

## Contrato HTTP versión 1

| Método y ruta | Resultado |
|---|---|
| GET `/api/v1/web-orders/options` | `{schema_version:1, branches:[{id,code,name}]}`; solo sucursales activas |
| POST `/api/v1/web-orders/quote` | Cotización de sucursal e items con precios y promociones vigentes |
| POST `/api/v1/web-orders` | 201 al crear; 200 al repetir exactamente la solicitud normalizada |
| POST `/api/v1/web-orders/recover` | Recibo mínimo por clave secreta; cuerpo `{}` |

No admiten query parameters. Las escrituras reciben JSON y mantienen el límite
de cuerpo de la API (16 KiB). Se conserva el rechazo de Origin: estas rutas aún
no habilitan consumo desde navegador. No requieren sesión porque la recepción
es pública, como el contrato Supabase de pedidos; no exponen administración.

Cotización:

```json
{"branch_id":1,"items":[{"product_id":1,"quantity":2}]}
```

Son obligatorios IDs numéricos enteros positivos, de 1 a 4294967295; 1–25 productos
distintos y cantidades enteras 1–100. Se rechazan UUID, IDs string, duplicados,
campos desconocidos y precios unitarios aportados por el cliente. La cotización
devuelve `schema_version`, `branch_id`, `subtotal_cents`, `discount_cents`,
`total_cents` e `items`. Cada partida contiene product_id, product_name,
internal_code, quantity, list_price_cents, unit_price_cents, promotion_id,
promotion_name y line_total_cents. No garantiza existencias ni congela precios.

Crear exige los mismos campos más `customer_name`, `customer_phone`,
`customer_email`, `notes` y `expected_total_cents`. Todos deben estar presentes;
teléfono/email/notas pueden ser null, pero debe existir al menos un contacto.
Nombre: 2–160 caracteres; teléfono: 8–24 con dígitos y `+() .-`; correo: hasta
254 y formato validado; notas: hasta 500. Se recortan espacios exteriores y se
normaliza el correo a minúsculas; no se admiten controles ni Unicode mal formado.

La cabecera `Idempotency-Key` es obligatoria al crear y recuperar: 32 bytes
aleatorios representados por 64 caracteres hexadecimales minúsculos. Se genera
una vez por intento lógico y se conserva antes de enviar. No es el ID del pedido.
No se debe poner en URL, logs, capturas, repositorio ni mensajes. El servidor
almacena únicamente su SHA-256 y una huella de la solicitud normalizada.

La respuesta de crear/repetir/recuperar contiene únicamente `schema_version`,
`id` entero, `order_number` (`VW-<id>`), `status: PENDING`, `total_cents`,
`created_at` UTC con seis decimales e `idempotent_replay`. No contiene datos de
contacto, notas ni partidas. No existe consulta pública por ID secuencial.
Poseer la clave permite recuperar ese recibo; perderla impide este mecanismo de
recuperación. No se ofrece búsqueda pública por teléfono o email.

## Reglas y persistencia

Se comparte el cálculo SQL de promociones con el catálogo: no se copió el cálculo
al cliente ni se creó otro motor de precios. Una sola consulta evalúa todos los
productos/promociones con el reloj SQL. Se bloquean sucursal, productos y
categorías durante la operación y se usa READ COMMITTED. Los totales se suman
con BigInt y se rechaza un subtotal mayor a 9007199254740991 o total cero.

La confirmación vuelve a cotizar. Si el total difiere de `expected_total_cents`,
responde 409 `WEB_ORDER_PRICE_CHANGED` sin crear nada; volver a cotizar y pedir
al comprador que revise el total antes de enviar. Este campo comprueba el total,
no garantiza que cada partida o promoción sea idéntica a una cotización previa.

Pedidos y partidas se insertan en una transacción. Los nombres, códigos, precios
y promoción quedan como instantánea; cambios posteriores del catálogo no alteran
un recibo ya confirmado. Se conserva el reintento incluso si la sucursal después
queda inactiva. Cambiar contacto, partidas, notas, sucursal o total esperado con
la misma clave produce 409 `WEB_ORDER_IDEMPOTENCY_CONFLICT`.

Un bloqueo nombrado MariaDB serializa la creación entre procesos de API, incluida
la comprobación de idempotencia y el límite de tres pedidos en quince minutos
que coincidan por teléfono **o** correo. Los reintentos no consumen el límite.
Es una implementación deliberadamente simple para esta fase; limita rendimiento
y no sustituye controles de abuso de una publicación futura. Si no obtiene el
bloqueo en cinco segundos, devuelve 503 `WEB_ORDER_BUSY`.

Las dos tablas nuevas (`web_orders`, `web_order_items`) tienen PK enteras
autoincrementales, FKs RESTRICT y únicos necesarios. Las partidas guardan importes
en centavos y calculan descuento/total de línea en SQL. La cuenta HTTP solo tiene
SELECT/INSERT sobre ellas; no UPDATE/DELETE. No se añadieron índices avanzados.
El esquema solo admite `PENDING`: estados posteriores e historial de cambios
requieren el próximo bloque, no escrituras manuales a tablas.

## Fallos y recuperación

- Validación: 400; sucursal/productos no disponibles o total no admitido: 409.
- Límite de contacto: 429 `WEB_ORDER_RATE_LIMITED`.
- Clave desconocida al recuperar: 404 `WEB_ORDER_NOT_FOUND`; puede existir una
  solicitud todavía en curso. Reintentar con la misma clave y contenido.
- Fallo antes de COMMIT revierte pedido y partidas. El HTTP responde un error
  seguro, sin SQL, datos personales ni credenciales.
- Si COMMIT pierde su confirmación: 503 `WEB_ORDER_RESULT_UNCERTAIN`. Recuperar o
  repetir con la misma clave; nunca crear una nueva para resolver incertidumbre.
- El límite por contacto y la idempotencia se almacenan en MariaDB; no dependen
  de memoria de un proceso. La clave única es la última defensa contra duplicados.

No modifica ventas, pagos o inventario. No incluye pago en línea, notificaciones,
administración, cancelación, entrega ni importación de pedidos antiguos. Tampoco
autoriza desplegarlo como servicio público.

## Levantar y probar localmente

Desde ViveroApp, usando `.env` propio y datos sintéticos:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up --build -d --wait api
npm --prefix backend run check
npm --prefix backend test
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Aplicar 009 antes de recrear API: health ahora la exige. Volúmenes nuevos la
reciben mediante init. No borrar volúmenes para actualizar.

Ejemplo PowerShell sobre el seed local (ajustar puerto si API_PORT es distinto):

```powershell
$api = 'http://127.0.0.1:3001/api/v1/web-orders'
$quoteInput = @{ branch_id = 1; items = @(@{ product_id = 1; quantity = 2 }) }
$quote = Invoke-RestMethod -Method Post -Uri "$api/quote" -ContentType 'application/json' -Body ($quoteInput | ConvertTo-Json -Depth 4)
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$key = -join ($bytes | ForEach-Object { $_.ToString('x2') })
$headers = @{ 'Idempotency-Key' = $key }
$payload = $quoteInput + @{ customer_name = 'Cliente de prueba'; customer_phone = $null; customer_email = 'demo@example.invalid'; notes = $null; expected_total_cents = $quote.total_cents }
$json = $payload | ConvertTo-Json -Depth 4
$receipt = Invoke-RestMethod -Method Post -Uri $api -Headers $headers -ContentType 'application/json' -Body $json
$replay = Invoke-RestMethod -Method Post -Uri $api -Headers $headers -ContentType 'application/json' -Body $json
$recovered = Invoke-RestMethod -Method Post -Uri "$api/recover" -Headers $headers -ContentType 'application/json' -Body '{}'
```

Conservar la clave de forma privada mientras se prueban reintentos. El ejemplo
la mantiene solo en memoria; no representa todavía la persistencia del futuro
cliente Web. Repetir el ejemplo completo genera una nueva clave y otro pedido.

Siguiente bloque: administración de pedidos por capacidad y sucursal, con cambios
de estado auditados. Después implementar el envío idempotente a caja y su relación
con inventario antes de activar el recorrido Web.

## Evidencia del bloque (2026-09-30)

- `npm run check`: correcto; `npm test`: 26/26 pruebas unitarias.
- Integración HTTP/MariaDB: 10/10 sobre base existente actualizada y las mismas
  10/10 sobre base nueva. La prueba de límite con claves distintas se amplió a
  solicitudes concurrentes después de la primera ejecución y pasó en la nueva.
- Reintentos simultáneos con la misma clave: una creación y dos repeticiones,
  mismo ID. Con claves distintas se respetó el límite por contacto. Cambio de
  payload bloqueado, precios de cliente rechazados, total fuera de rango rechazado.
- Fallo inyectado al insertar partidas: sin pedido persistido. Pérdida simulada
  de acuse tras un COMMIT real: recuperación y repetición sin duplicar. No es una
  prueba de corte físico de red ni de caída del servidor MariaDB.
- Se verificaron promociones, recibo estable tras cambios de precio/sucursal,
  ocultación de datos personales y ausencia de ventas creadas por este flujo.
- 009 aplicada al volumen existente; 002–009 reconocidas como ya aplicadas tras
  inicialización desde cero. Verificación estática Supabase: 134 comprobaciones.
- Sin pgTAP porque no cambió SQL PostgreSQL; migración nueva probada en MariaDB.
  Sin builds Android/Web porque no cambiaron consumidores. No se validaron VPS,
  MySQL 8, carga sostenida, navegador ni el recorrido pedido → caja.
- Contenedores de ambos proyectos de prueba detenidos; volúmenes conservados.

Archivos de esta entrega:

- `backend/src/web-orders.js`, `backend/src/app.js`, `backend/src/catalog-pricing.js`.
- `backend/test/web-orders.test.js`, `backend/test/integration.test.js`.
- `backend/package.json`, `database/mysql/migrations/009_web_orders.sql`.
- `infra/docker/compose.yaml`.
- `docs/backend-web-orders.md`, `docs/backend-api-mariadb.md`,
  `docs/supabase-migration-map.md`, `docs/web-catalog-cutover.md`.

Cambios anteriores de IDE, AGENTS, README, documentación y módulos previos
preservados. Rama main, HEAD a4621f6d42deb735714c047b05789ee83760939a sin cambios.
Sin staging, commit, push, despliegue ni operaciones remotas. No se modificaron
ViveroWeb ni ViveroAppCliente en este bloque.
