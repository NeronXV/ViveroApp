# Folios cortos — etapa 3

Implementación local del 9 de octubre de 2026. No desplegada. Se conserva la etapa
2 del comprobante público. No incluye cancelaciones ni cambios visuales de Caja
o Android.

## Diseño de migración y contratos

Se inspeccionaron `sales`, `web_orders`, sus partidas, checkout, cobros, consultas
y los parsers estrictos Web/Kotlin antes de elegir el contrato. Sustituir sin
negociación `sales.folio` o `order_number` habría roto Android anterior y Web.
La solución preserva valores históricos e IDs, y cambia solo la representación
solicitada explícitamente por el cliente.

Migración autoritativa: `database/mysql/migrations/031_short_folios.sql`.

- `sale_folio_aliases`: una relación única con cada `sales.id`, ordinal único y
  alias generado **STORED/UNIQUE** por MariaDB (`VD-0001`).
- `sale_folio_counter`: contador global de ventas. El bloqueo de su fila serializa
  la asignación dentro de la misma transacción de la venta/checkout.
- `sale_folio_namespace`: registro privado de nombres originales y alias con
  propietario. Sus lecturas con bloqueo ven reservas actuales incluso cuando el
  llamador mantiene una instantánea REPEATABLE READ antigua. No concede SELECT
  ni escritura al usuario de API.
- `web_order_folio_aliases`: alias **STORED/UNIQUE** generado desde el ID inmutable
  del pedido (`VW-0001`). No requiere otro contador porque MariaDB ya asigna el ID.

Las cuatro tablas nuevas tienen PK enteras autoincrementales; las relaciones con
ventas/pedidos se conservan en claves foráneas únicas cuando corresponden.
Ninguna PK existente se reemplaza.

Ambos formatos tienen un mínimo de cuatro dígitos; LPAD aumenta su longitud en
vez de truncar al pasar 9999. La numeración es global por tipo, sin sucursal/año
en el folio; sucursal y fecha siguen siendo campos independientes. Se permiten
huecos por IDs, reservas históricas y rollback. No es numeración fiscal.

La migración agrega alias a registros existentes, ordenados por ID, sin modificar
`sales.folio`, PK, `web_order_id`, claves de idempotencia, importes ni estados.
Antes del backfill registra todos los nombres originales; omite un candidato
que identifica otra venta histórica. Las inserciones futuras registran original
y alias mediante triggers en la misma transacción. Un original que coincide con
un alias ajeno se rechaza. El folio original se vuelve inmutable; las operaciones
actuales solo actualizan estado/otros campos y no dependen de renombrarlo.

La API recibe únicamente SELECT en las dos tablas de alias. No tiene escritura
en contador/namespace ni EXECUTE de `allocate_sale_folio`; la rutina es de definer
y la invoca el trigger. Las FKs de metadata tienen ON DELETE CASCADE para seguir
el ciclo de su padre (incluida limpieza de fixtures administrativos), sin añadir
un permiso ni endpoint de borrado al cliente. La migración no elimina operaciones.

El arranque de BD reconoce también procedimientos compuestos. El health de la
API nueva exige 031; el verificador de instalación espera 60 tablas/30 migraciones.

## Contrato compatible de presentación

```text
X-Vivero-Folio-Format: short-v1
```

Sin la cabecera, o con una versión desconocida, se mantienen los mismos campos
y folios del contrato anterior: VD hexadecimal, VW sin relleno. Con ella, la
API sustituye únicamente `folio` y `order_number` por alias persistidos, después
de comprobar autorización/identidad/sucursal y terminar la operación original.
No agrega campos ni altera IDs, relaciones, precios o datos de pago.

La transformación conserva también fechas nativas SQL al serializar historial.
Si un alias requerido no está disponible, devuelve 503
`FOLIO_FORMAT_UNAVAILABLE`; nunca inventa un alias. Después de un commit el cliente
conserva su clave y recupera el mismo resultado. No debe emitir otro intento.

Web actual solicita el formato en sus servicios reales y valida ambas respuestas;
Android lo solicita en el transporte Ktor y acepta ambos formatos en respuesta y
diario Room. No cambia el esquema Room ni reescribe pendientes. Por tanto, la API
nueva sigue atendiendo apps anteriores y los clientes nuevos pueden leer una API
anterior que ignore la cabecera. No se garantiza degradar una APK nueva sobre
su base local a una versión que no entiende los folios cortos ya guardados.

Los comprobantes públicos, tickets de Caja, filas/consultas y pantallas Android
que reciben folios del servidor usan los alias sin cambiar lógica comercial.
Las copias locales previas conservan el folio con el que se guardaron; no se
renumeran ni convierten arbitrariamente sin una consulta al servidor. Una copia
pública antigua cuyo secreto ya se eliminó puede seguir mostrando VW antiguo.

## Consultas por ambos folios

- GET `/api/v1/sales?folio=<VD corto u original>`: solo ventas propias/sucursal.
- GET `/api/v1/cashier/sales?folio=<VD corto u original>`: solo fila elegible de
  la sucursal y capacidad de Caja.
- GET `/api/v1/admin/web-orders?folio=<VW corto u original>`: capacidades y ámbito
  administrativo actuales; admite las dos representaciones del mismo ID.
- GET `/api/v1/cashier/refunds/lookup?folio=...`: ambas representaciones; mantiene
  el eco exacto del folio consultado exigido por Web anterior. Esta respuesta se
  excluye deliberadamente de la sustitución; consultas por ID/comprobantes usan
  el alias negociado. No modifica devoluciones ni sus permisos.

Web expone el filtro opcional en servicios existentes de Caja/pedidos. No se
introdujeron pantallas ni una búsqueda pública por folio: el comprobante público
sigue requiriendo su clave secreta. No hay nueva capacidad o rol.

## Pruebas nuevas y resultados

| Verificación | Resultado |
|---|---|
| Backend `npm test` / `npm run check` | 88 aprobadas; sintaxis correcta |
| Compose final, migrador oficial y suite completa | 26 SQL/HTTP aprobadas |
| Prueba específica final ampliada | Aprobada: dos SALES/20 creaciones simultáneas, recuperación, consulta propia/Caja, una relación pedido→venta, cobro/comprobante, búsqueda por alias/original, privilegios y fechas |
| Migración 030→031 sobre BD aislada con originales en conflicto | Aprobada: conserva exactamente filas anteriores de ventas, pedidos, pagos, inventario y movimientos; omite VD-0001 reservado a otra venta; todas las ventas quedan con alias |
| Límite de numeración | VD-10000 y VW-10000 guardados realmente por triggers en fixtures transaccionales; rollback conservó los registros de negocio |
| Snapshot RR antiguo vs escritura concurrente | La asignación ve la reserva nueva y no asigna su folio a otra venta |
| Compatibilidad HTTP | Sin cabecera: contrato/folio original; con cabecera: misma identidad y forma exacta, alias estable; mismo pago/venta al recuperar |
| Web `npm test`, lint, build | 487 aprobadas; una HTTP opcional omitida sin fixture; lint/build correctos |
| Android `assembleDebug testDebugUnitTest` | Correcto: 364 casos, 363 aprobados y una HTTP opcional omitida |
| Kotlin HTTP local | Transporte Ktor real solicita short-v1; el parser lee respuesta corta y respuesta de API antigua |
| Verificador estático Supabase | 134 comprobaciones aprobadas; Supabase sin cambios |
| PDF con VW-0004 / VD-0042 | Breve 1 A4, largo 3 A4, térmica 58/80 mm 1; sin catálogo. Caja/etiquetas: mismas dimensiones, texto y páginas que CSS HEAD |
| Diff | `git diff --check` correcto en ambos repositorios |

La suite completa final pasó con los 26 casos, incluida la prueba ampliada de
folios. Se validaron también el límite web con una fila real, la inmutabilidad
del original y los privilegios adicionales. Durante el ensayo se corrigieron
una cuenta ficticia no activada, un campo requerido del fixture y un aviso de
lint. La revisión detectó fechas que debían conservar su serialización y la
restricción MariaDB de bloquear la tabla que invoca un trigger; el namespace
separado resolvió esta última. MariaDB también rechazó un CHECK sobre la PK
autoincremental del contador; se retiró esa restricción incompatible. La fuente
final se aplicó correctamente con el migrador oficial sobre 030 y pasó otra
prueba independiente de actualización con originales en conflicto. Las versiones
intermedias de ensayo no son evidencia de la implementación final.

Ensayo final válido: `vivero-folios-release-rehearsal`, API loopback 33010, redes
10.243.72.0/24 y 10.243.73.0/24. Actualización válida:
`vivero-folios-upgrade-release`, redes 10.243.74.0/24 y 10.243.75.0/24,
iniciado en 030 antes de aplicar 031. Los ensayos intermedios y la BD de etapa 2
se conservaron; solo se usaron datos locales sintéticos. No hubo conexión o
modificación de producción. Al cerrar las comprobaciones se detuvieron únicamente
los contenedores de estos ensayos; todos sus volúmenes se conservaron.

Regresión PDF reproducible desde Web: generar con `PUBLIC_TICKET_RENDER_DIR`
apuntando a `artifacts/short-folios-stage3` y
`PUBLIC_TICKET_FOLIO_FORMAT=short-v1`, ejecutar
`npm test -- tests/public-order-print-render.test.tsx`, imprimir los ocho HTML
con Chrome headless como en etapa 2, y ejecutar
`node tests/verify-public-order-pdfs.mjs artifacts/short-folios-stage3 VW-0004`.
El formato anterior sigue siendo el valor por defecto de esos fixtures.

Se renderizaron e inspeccionaron visualmente los PDF breve A4 y térmico de 58 mm:
folio, estado, fecha, sucursal y total legibles, sin recortes.

No se instalaron APK ni se ejecutaron pruebas físicas/instrumentadas, pgTAP o
aceptación conjunta. No se cambió firma, versión Android, UI ni base Room.
La candidata anterior guardada en tmp permanece íntegra; el APK de assembleDebug
es un artefacto interno de compilación, no una candidata nueva para distribución.

## Archivos de esta etapa y protección de cambios previos

App/backend/infra:

- `database/mysql/migrations/031_short_folios.sql`.
- `backend/src/short-folios.js`, `app.js`, `sales.js`, `cashier.js`, `refunds.js`, `web-order-admin.js`.
- `backend/test/short-folios.test.js`, `short-folios-integration.test.js`, `short-folio-migration-upgrade.test.js`, `integration.test.js`.
- `backend/package.json` (solo scripts), `backend/scripts/verify-local-install.js`.
- `infra/docker/compose.yaml`, `infra/docker/init-migrations.sh`.
- Android: `core/network/BackendApiTransport.kt`, `feature/cart/domain/repository/BackendSaleGateway.kt`, `feature/cart/data/remote/BackendSaleRemoteDataSource.kt`, `feature/cart/sync/BackendSaleOutboxStore.kt`.
- Pruebas Kotlin: `BackendSaleFolioTest.kt`, `BackendFolioNegotiationTest.kt`, `BackendSaleRemoteDataSourceTest.kt`.
- `docs/backend-short-folios.md` y referencia desde `docs/backend-web-orders.md`.

Web:

- `src/lib/folio-format.ts`, `folio-format.test.ts`, `backend-http.ts`.
- `src/features/public-orders/backend-order-service.ts`, su test y `backend-admin-order-service.ts`.
- `src/features/cashier/backend-cashier-service.ts`, `backend-counter-sale-service.ts`, `backend-cashier-operations-service.ts`.
- `tests/public-order-print-render.test.tsx`, `tests/verify-public-order-pdfs.mjs`.

Se conservó la base inicial sin commit. App sigue en main/
`a58fd614b28920d7b39fee7cef2b67f1c16b0531`; Web en main/
`5f9c4853e42cace586cdaeec13998668b3d204aa`. No se tocaron modificaciones previas
de IDE, README, Gradle/versión, compras, PanelPage, reportes anteriores o scripts
de entrega. En archivos compartidos con etapa 2 solo se agregaron negociación,
validación/referencia y fixtures opcionales; sus consultas privadas, portal/CSS,
guardado previo a liberar intento y recuperación siguen intactos. Los perfiles
de impresión y componente público no se modificaron en esta etapa.

## Pasos para un despliegue futuro, aún no autorizado

1. Revisión del diff combinado de etapas 2/3 y respaldo verificado del destino
   correcto. Los scripts existentes incluyen `--routines --triggers`; mantener
   esas opciones, definer y grants al restaurar. Copia externa sigue a cargo de Toni.
2. Ventana de mantenimiento: detener escrituras de API/importadores. El backfill
   no debe competir con inserciones antes de instalar sus triggers. Ensayar con
   una copia aislada representativa para estimar duración y bloqueos.
3. Aplicar migraciones faltantes en orden, incluida 030 si aún falta y después
   031, con cuenta administrativa. No reset ni borrado. Si falla, inspeccionar
   DDL parcial; no repetir ciegamente ni habilitar API incompleta.
4. Comprobar originales/PK/relaciones sin cambios, una fila de alias por venta y
   pedido, unicidad y ownership del namespace. Verificar migración 031 y permisos.
5. Desplegar API, comprobar health/compatibilidad sin cabecera y con short-v1,
   después Web y una APK versionada con la firma vigente cuando se autorice su
   entrega. Conservar clientes antiguos funcionales.
6. Pedro/Toni: venta/pedido/comprobante/reimpresión/búsqueda de ambos folios y
   recuperación en sus dispositivos. No conciliar/reprocesar ventas históricas
   automáticamente como parte del despliegue.

Riesgos pendientes: tiempo de backfill con volumen real, revisión de definer y
restauración del respaldo actualizado, aceptación física y copias locales viejas
que conservan su folio original. El contador de ventas serializa asignaciones;
no se acredita rendimiento de alta carga ni secuencia sin huecos. Los alias son
compatibles con versiones previas del protocolo, no una autorización para
degradar APK/borrar almacenamiento.

Sin commit, push, despliegue, cambios de producción, activación de CENTRO,
reprocesamiento de pendientes ni avance a etapas posteriores.
