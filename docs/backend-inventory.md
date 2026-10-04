# Inventario por sucursal en Backend API/MariaDB

La migración `011_inventory_operations` agrega una bitácora aditiva al saldo
actual. Cada sucursal conserva sus cantidades `DECIMAL(14,3)`; una entrada
inmutable de apertura representa el saldo preexistente y cada recepción o
conciliación posterior suma su movimiento al saldo dentro de la misma operación
SQL. `inventory_counts` conserva también los conteos sin diferencia.

El acceso se limita a la sucursal activa de la sesión. `MANAGE_INVENTORY` puede
consultar y escribir; `VIEW_INVENTORY_ALERTS` puede consultar. Las nuevas
cantidades se reciben como cadenas decimales exactas para no pasar por flotantes
JavaScript. En paridad con los RPC actuales, recepciones y conteos físicos son
cantidades enteras no negativas, aunque los saldos permitan fracciones.

## Endpoints

- `GET /api/v1/inventory/dashboard?limit=100`: productos
  activos de la sucursal, saldo, mínimo y alerta de bajo inventario. El cursor
  se basa en el ID entero del producto.
- `GET /api/v1/inventory/history?limit=50&before_id=500&product_id=3`: movimientos
  más recientes de la sucursal; `before_id` pagina por ID de bitácora.
- `POST /api/v1/inventory/receptions`: cuerpo exacto
  `{ "product_id": 3, "quantity": "5", "notes": "Proveedor demo" }`.
- `POST /api/v1/inventory/counts`: cuerpo exacto
  `{ "product_id": 3, "counted_quantity": "8", "reason": "Conteo físico" }`.

Las dos escrituras requieren `Authorization: Bearer ...` y un encabezado
`Idempotency-Key` único (16–128 caracteres ASCII permitidos). Repetir una
solicitud idéntica de la misma persona, sucursal y producto devuelve el resultado
original; reutilizar la clave con contenido distinto genera conflicto. La
conciliación calcula y registra la diferencia bajo bloqueo de saldo; diferencia
cero conserva el conteo, sin movimiento artificial.

## Límites de esta entrega

El trigger de MariaDB actualiza el saldo al insertar en la bitácora. La migración
registra los saldos existentes como `OPENING` antes de instalarlo, sin sumar dos
veces. Aún no se migran salidas de ventas, devoluciones, traspasos, compras,
reaperturas ni configuración de mínimos desde una pantalla API. El trigger
`SALE` gradual de Supabase permanece apagado y los flujos de venta/caja existentes
siguen intactos en Supabase. No se conectó Android ni ViveroWeb a estos endpoints.

Compose monta el archivo para instalaciones nuevas; en bases locales ya creadas
se aplica una vez con el servicio de mantenimiento:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build api
```

La documentación no afirma que la migración se haya aplicado al volumen actual
en esta entrega. MariaDB local debe estar arriba y saludable antes del migrador.

## Revisión posterior del bloque

Se corrigieron la lectura de mínimos (pertenecen a `inventory`, por sucursal),
los permisos para crear saldos en cero, el formato de ajustes negativos y la
lectura idempotente bajo bloqueo. La bitácora referencia el par sucursal/producto
del saldo mediante FK y el trigger actualiza ese saldo, evitando insertar una
cantidad negativa como fila nueva. La cuenta API solo puede inicializar el saldo;
la modificación de cantidades existentes corresponde al trigger.

Las pruebas de lógica se ejecutan con `node --test test/inventory.test.js` desde
`backend/`; en entornos que bloquean procesos hijos puede usarse
`node --test --test-isolation=none test/inventory.test.js`. Estas pruebas usan
datos sintéticos y dobles de SQL; no acreditan ejecución real de la migración,
restricciones, privilegios ni concurrencia en MariaDB. El bloque siguiente debe
confirmar esos puntos en Docker antes de habilitar ventas o cambiar consumidores.

Validación de esta revisión: ocho pruebas específicas de inventario y 35 pruebas
totales del backend aprobadas en Node 24.16.0, con aislamiento de procesos
desactivado porque el sandbox bloqueó `spawn` con EPERM. La comprobación de
sintaxis pasó. Docker Desktop recibió un intento de inicio, pero el motor Linux
siguió inaccesible (`dockerDesktopLinuxEngine` no encontrado); no se aplicó SQL.
No existe `.env` en la raíz; las credenciales temporales de validaciones previas
no se mostraron ni modificaron. No atribuir los resultados históricos de Docker
a la migración 011.

## Validación real posterior — 2026-09-30

Docker Engine 29.7.2 se recuperó conservando por renombrado los directorios de
sockets temporales, con el motor detenido, y recreándolos vacíos antes de iniciar.
No se borraron volúmenes ni se cambiaron ACL, configuración o credenciales.
Se reutilizó `vivero-validation-20260929` con sus datos sintéticos y archivo
ignorado `tmp/backend-validation-20260929.env`.

El migrador aplicó 008–011 al volumen existente; 002–007 ya estaban aplicadas.
MariaDB y API quedaron saludables. El health desde Windows en
`http://127.0.0.1:33001/health` devolvió `status: ok`.

La prueba real detectó que `ON DUPLICATE KEY UPDATE` exigía UPDATE sobre otras
columnas del saldo. La inicialización ahora usa `INSERT IGNORE` de un saldo cero
y luego comprueba y bloquea la fila; no se ampliaron los permisos SQL. Se actualizó
la prueba de esquema a las 26 tablas de las migraciones disponibles.

Pasaron las 11 pruebas de integración del perfil Docker, incluida inventario.
La prueba de inventario ampliada pasó también con sesión real y rutas HTTP:
dashboard, historial, replay de conteo y rechazo al usuario sin sucursal.
La comprobación SQL incluyó creación del saldo con la cuenta runtime, recepción,
ajuste negativo, conteo sin diferencia, replay paralelo, conflicto de clave,
coherencia entre suma de movimientos y saldo, y rechazo de UPDATE directo de
cantidad. Ocho pruebas unitarias de inventario y `npm run check` pasaron localmente.

Para repetir en este entorno sintético, desde la raíz:

```powershell
docker compose --env-file tmp/backend-validation-20260929.env -p vivero-validation-20260929 -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Los fixtures nuevos se eliminan por sus propios IDs al finalizar; los volúmenes
y contenedores de validación permanecen disponibles. Esta sesión acredita una
actualización incremental, no una instalación nueva desde volumen vacío ni
compatibilidad MySQL 8. Los consumidores Web/Android siguen en Supabase.
Siguiente bloque: ventas y partidas con precios del servidor e idempotencia.
## Actualización 017 (2026-09-30)

Disponible activación por sucursal y stock transaccional al pagar/devolver.
Corregido el caso de interbloqueo INSERT IGNORE seguido de FOR UPDATE mediante
bloqueo previo de sucursal. Las recepciones y conteos conservan sus contratos.
Consultar [backend-sale-inventory.md](backend-sale-inventory.md) para el alcance
actual y pruebas nuevas. No se activaron sucursales operativas ni consumidores.
