# Bloque 1 — seguridad y preparación del inventario

Implementación local en `main`. Sin producción, VPS, importación real, commit,
push, instalación Android ni rediseño. Las referencias aprobadas se conservan en
[dirección visual](design/admin-premium-direction.md).

## Autorización y consistencia

- Registrar conteos: sesión activa, sucursal activa y `MANAGE_INVENTORY`.
- Recepción directa, confirmación nueva de compras, aprobación/rechazo de
  diferencias y activación del control de existencias: además, rol efectivo
  `OWNER`. No se conceden ni modifican capacidades o asignaciones de usuarios.
- La gerente y el rol INVENTORY conservan catálogo/conteos autorizados; no pueden
  recibir ni aplicar diferencias positivas o negativas. ADMIN conserva sus
  demás funciones, pero no sustituye la autorización de propietario.
- Devoluciones mantienen sus permisos y contrato actuales. Borradores de compra
  siguen sin alterar inventario; su confirmación requiere propietario.
- Observación: cantidad, autor y hora del servidor, motivo, sucursal, producto,
  saldo y último movimiento de referencia. No crea movimientos ni modifica saldo.
- Una referencia tomada antes del conteo debe acompañar el envío. Se comprueba al
  guardar y al aprobar, incluyendo cambios que regresen al mismo saldo (ABA).
  Si hay movimientos, respuesta `INVENTORY_COUNT_STALE`: rechazar/recontar, nunca
  aplicar el valor viejo sobre el saldo actual. No hay rebases automáticos.
- Aprobación transaccional: bloqueos sucursal → producto → existencia; movimiento
  y conteo auditado, responsable de decisión, motivo y hora del servidor. Una sola
  decisión por observación. Rechazo conserva el historial sin movimiento.
- Sin diferencia: cierre auditado y confirmación inicial de sucursal/producto,
  sin movimiento. Reintentos conservan resultado; clave/actor/payload distintos
  generan conflicto. Un fallo revierte todas las escrituras de la aprobación.

## Contratos para Web móvil

Todos usan sesión actual y sucursal del servidor, no una sucursal enviada en el
cuerpo. Encabezados de identidad esperada se verifican en las mutaciones.

| Método y ruta `/api/v1/` | Uso |
|---|---|
| GET `inventory/permissions` | Acciones efectivas `can_record_count`, `can_receive`, `can_approve_counts`. |
| GET `inventory/count-baseline?product_id=…` | Saldo y último movimiento antes de contar. Sin creación de saldo. |
| POST `inventory/count-observations` | Guarda observación; `Idempotency-Key` obligatorio. |
| POST `inventory/count-observations/result` | Recupera con el mismo cuerpo y clave, sin escribir. |
| GET `inventory/count-observations?status=PENDING&limit=100&after_id=…` | Bandeja paginada de sucursal; también ALL/APPLIED/CLOSED/REJECTED. |
| POST `inventory/count-observations/{id}` | `{decision: APPROVE o REJECT, reason}` y clave; propietario. |
| GET `products/{id}/preparation` | Estado y requisitos por sucursal. |
| POST `products/{id}/preparation/activate` | Cuerpo vacío; `MANAGE_PRODUCTS` + `MANAGE_PRICES`, con requisitos cumplidos. |

Cuerpo de observación: `product_id`, `counted_quantity` decimal textual entero
(cero permitido), `reason`, `baseline_quantity` decimal textual y
`baseline_movement_id`. La referencia se conserva junto al intento pendiente;
no se reemplaza por una referencia reciente al reintentar un conteo antiguo.

Web tiene un diario por usuario/sucursal, escrito antes del POST. Tras pérdida de
respuesta consulta resultado; sólo reenvía con la misma clave si recibe 404.
No acepta otra operación mientras exista un resultado incierto. Decisiones
reintentan su misma clave. El servidor conserva observaciones aunque se cierre
el navegador; no guardar tokens en este diario.

Se adaptaron los controles existentes: registrar conteo, revisión con motivo,
acciones de recepción según servidor, y confirmación de compra restringida.
La ficha móvil premium y la nueva navegación quedan para bloques 2 y 3.

## Preparación comercial y compatibilidad

Migración pendiente de despliegue: `033_inventory_preparation.sql` después de
032. Añade metadatos a productos y dos tablas con llaves enteras, restricciones,
FK y privilegios acotados. Health de la nueva API exige 033. No cambia saldos,
ventas, conteos históricos, folios, pagos, imágenes ni permisos de roles.

- Productos históricos de precio positivo: `LEGACY`, conservan compatibilidad
  anterior. `legacy_compatibility=true` identifica esta excepción; los requisitos
  devueltos como cumplidos por compatibilidad no acreditan un conteo físico nuevo.
- Ceros históricos: conservan registro/historia, pero pasan a `PENDING`; cero no
  acredita precio comercial confirmado. No se desactivan productos masivamente.
- Nuevos/importados: `PENDING`, precio explícitamente confirmado y mayor que cero,
  categoría habilitada, conteo inicial aprobado por propietario para la sucursal
  (también cero), y activación autorizada. Foto y botánica opcionales.
- Estado consultable: `IMPORTED_PENDING`, `PENDING`, `READY` o `DISABLED`, con
  razones independientes de precio, conteo, categoría y activación. La importación
  inactiva se distingue de una desactivación voluntaria registrada por la API.
- Crear/editar un precio positivo con `MANAGE_PRICES` registra confirmación y
  responsable. Precio cero invalida confirmación y activación; desactivar también
  invalida activaciones nuevas. Reactivar el checkbox no completa la preparación.
- Activación puede habilitar un importado pendiente una vez cumplidos requisitos;
  no reactiva por sí sola un producto desactivado voluntariamente.
- Catálogo público/escaneo muestran nuevos productos habilitados en al menos una
  sucursal activa. Cotización, envío de pedido a Caja y cobro verifican la sucursal
  concreta. La comprobación también existe con control de existencias deshabilitado.
  La regla actual de unidades suficientes sigue aplicándose al cobrar donde el
  inventario está activado; este bloque no activa sucursales automáticamente.
- Pendientes de Caja históricos conservan precios capturados y recuperación.
  No se reescriben comprobantes ni contratos de pago/devolución.
- Clientes anteriores: venta, cobro y recuperación mantienen sus contratos.
  Nuevos POST a `inventory/counts` reciben `INVENTORY_OBSERVATION_REQUIRED`.
  Replays/resultados históricos permanecen disponibles. Android no se modificó:
  su conciliación directa antigua requiere usar Web con el nuevo flujo. Intentos
  locales antiguos no se reprocesan ni se borran automáticamente.

## Preparación de las cuatro listas

[Formato de entrada sintético](plant-lists-input.example.json). No se recibieron
las listas reales y no se importó ninguna. Categorías admitidas: Follaje, Arbustos
y setos, Floral, Arbolado. `entry_id` y `source_key` deben permanecer estables;
reordenar filas no debe cambiarlos. Conservar `original_name`; `approved_name`
opcional aplica una normalización revisada sin perder el original en el informe.

Preparación sin base de datos:

```text
node backend/scripts/prepare-plant-lists.js INPUT.json SNAPSHOT.json OUTPUT_DIRECTORY
```

Snapshot autorizado de sólo lectura: `{products:[{id,common_name,scientific_name,
internal_code,barcode,description}],categories:[{id,name}]}`. Conservar tamaños y
presentaciones en la revisión; no inferir equivalencia por nombre. El generador
propone código estable `VDP-…` y referencias de origen compatibles con el
importador actual; compara nombres, científicos, códigos y barras, incluyendo
cruces que causarían ambigüedad de escaneo. No asigna códigos en base de datos.

Salida: `catalog.json` del importador canónico y `review.json`. No sobrescribe
salidas existentes. Precio cero es un marcador obligatorio de esquema y queda
sin confirmar; no inventa cantidades, precios reales ni fotografías. Unidad
predeterminada existente `pieza`, para revisar junto con presentación/tamaño.

Coincidencias con productos/categorías existentes y normalizaciones bloquean
`can_apply`. Hay que resolverlas y comprobar códigos con el dry-run del importador
contra el destino antes de aplicar. No se crean categorías parecidas para eludir
el conflicto ni se enlazan productos por semejanza automáticamente. La resolución
de equivalencias reales requiere las listas y un snapshot autorizado actualizado.
La repetición exacta reutiliza referencias; si una ficha se completó después, el
importador detecta `TARGET_CHANGED` y se detiene sin sobrescribir ni duplicar.

## Validación y cierre

Pruebas focalizadas: 33 backend unitarias/HTTP, 11 integración MariaDB/API y 29 Web
de contratos/diario, todas aprobadas.
Integración: roles y sucursales, recepción y compra, observación sin saldo,
aprobación repetida/dos propietarios, rechazo, cero inicial, cambios ABA,
concurrencia con cobro, rollback tardío, envío retrasado, producto incompleto,
compatibilidad histórica, devolución/reintentos, importación repetida y upgrade
032→033 que conserva catálogo válido, saldos, ventas, conteos y permisos.
La instalación fresca incluye los grants reales; el upgrade en base hermana
sintética valida DDL/datos omitiendo sólo GRANT/REVOKE para no alterar grants del
esquema principal del ensayo. Sin credenciales productivas.

Compilación y lint Web y comprobaciones de sintaxis aprobadas. `git diff --check`
aprobado en ambos repositorios; sólo avisos de conversión LF/CRLF.
No se ejecutan suites Android ni dispositivos al no cambiar Android.
No se acredita aceptación física o visual del futuro bloque 2.

Comandos ejecutados (desde `backend/` y ViveroWeb, respectivamente):

```text
npm run check
node --test test/unit.test.js test/inventory.test.js test/inventory-preparation.test.js test/catalog-import.test.js test/sales.test.js test/web-orders.test.js test/web-order-checkout.test.js test/refunds.test.js
npm run build
npm run lint
npm test -- src/features/admin/inventory-preparation-service.test.ts src/features/admin/backend-admin-request.test.ts src/features/admin/admin-boundary-service.test.ts src/features/admin/purchases/purchases-api.test.ts
```

La integración se ejecutó con `test/inventory-preparation-integration.test.js`
en Compose local `vivero-admin-block1-verified-20261010`, usando exclusivamente
credenciales y datos sintéticos. API y MariaDB de este ensayo quedaron detenidas;
sus volúmenes se conservaron. Los demás proyectos no se modificaron.

Despliegue posterior requiere respaldo recuperable y autorización independiente:
033 → API → Web, coordinados. No publicar sólo Web o sólo API con esquema 032.
Confirmar las cuentas OWNER y sus sucursales antes de operar; no se verificaron
cuentas reales ni se concedieron permisos en producción.

Trabajo previo conservado: cambios IDE/informe piloto en ViveroApp y cambios POS,
ticket e informes en ViveroWeb. No se añadieron al staging ni se modificaron.
Los ensayos y credenciales sintéticas quedan bajo `tmp/admin-block1/` ignorado;
los servicios de esta tarea se detienen al cerrar, sin eliminar volúmenes.

## Archivos de este bloque

- `backend/src/`: app.js, catalog.js, catalog-pricing.js, inventory.js,
  purchases.js, stock.js, web-orders.js, web-order-checkout.js; nuevos
  inventory-policy.js, inventory-observations.js y product-preparation.js.
- `backend/scripts/`: nuevos plant-list-preparation.js y prepare-plant-lists.js;
  verify-local-install.js actualizado al esquema 033.
- `backend/test/`: inventory.test.js adaptado al rechazo seguro de conciliación
  directa; nuevos inventory-preparation.test.js e
  inventory-preparation-integration.test.js. `backend/package.json`: comandos
  de validación, sin cambios de dependencias ni lockfile.
- `database/mysql/migrations/033_inventory_preparation.sql`.
- ViveroWeb `src/features/admin/`: AdminInventory.tsx, InventoryActivation.tsx,
  purchases/PurchaseDraftWizard.tsx; nuevos InventoryCountReview.tsx,
  useInventoryPermissions.ts, inventory-preparation-service.ts y su prueba.
- Este informe, formato JSON sintético, dirección visual y dos referencias.

Se omitió deliberadamente la suite integral antigua. Sus fixtures que crean
productos nuevos como vendibles o esperan una conciliación directa automática
deben adoptar la preparación y observación nuevas; no se presentan como pruebas
aprobadas. La suite focalizada prueba explícitamente los históricos válidos y los
nuevos contratos, con MariaDB y privilegios reales.
