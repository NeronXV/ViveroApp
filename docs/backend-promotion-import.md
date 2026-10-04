# Importación local de promociones

El mismo comando `import-catalog` reconoce `schema_version: 2` para promociones.
La versión 1 de categorías/productos permanece compatible. No hay otro backend
ni conexión automática a Supabase. Los clientes operativos conservan Supabase.

## Preparar el archivo

Usar el [fixture completo](../backend/test/fixtures/promotion-import-demo.json).
La envoltura exige exactamente schema_version, source_key y promotions. Cada
registro exige todos los campos del ejemplo, incluidos null explícitos.

- `id` es el UUID original; `source_key` coincide con el de los productos
  importados. Los IDs de destino y de la tabla de correspondencias siguen siendo
  INT AUTO_INCREMENT con FK RESTRICT.
- `value` es texto decimal: `"12.50"` para 12.5% o `"500.00"` para 500 centavos
  fijos. Replica numeric(14,2) del origen. Rechaza floats, exponentes, porcentajes
  fuera de 0.01–100 y centavos fijos fraccionarios. Conversión entera exacta.
- `created_at` es obligatorio y conserva su valor histórico. Fechas UTC con 3 o
  6 decimales y sufijo Z; convertir offsets antes de preparar el JSON sin perder
  microsegundos. Inicio/fin null significan sin límite. Se rechazan fechas
  imposibles y ventanas vacías/invertidas.
- `product_ids` contiene UUID de vínculos activos de Supabase. No se importan
  vínculos históricos inactivos. SELECTED_PRODUCTS exige una lista no vacía sin
  duplicados y correspondencias existentes del mismo origen. ALL_PRODUCTS exige
  lista vacía y afecta a todos los productos del destino, incluidos los nativos.
- Se admiten promociones activas e inactivas. Scope SALE queda rechazado: los
  descuentos de ventas/caja permanecen en Supabase.
- Máximo 100 promociones, 1000 productos por promoción y 5 MiB por archivo. No
  se truncan textos, importes ni fechas. Descripción null pasa a texto vacío.
  Campos extra, como updated_at, se rechazan: preparar una proyección explícita
  y conservar la exportación original autorizada.

Identidad por UUID y source_key, nunca por nombre. Dos UUID distintos pueden
tener el mismo nombre; no se fusionan promociones por similitud. Este paso no
reconcilia precios base: ejecutar antes la simulación de categorías/productos
frente a la misma exportación para detectar cambios locales.

## Simulación, repetición y conflictos

Se reutilizan el bloqueo de mantenimiento y la transacción del importador.
Simular es READ ONLY y no consume IDs. Aplicar inserta promoción, vínculos y
correspondencia en una única transacción. Un conflicto bloquea todo el lote;
repetir el mismo contenido devuelve reuse sin modificar filas.

Además de SOURCE_CHANGED y TARGET_CHANGED, el reporte distingue:

- PRODUCT_MAPPING_REQUIRED: falta algún producto en los mapas del mismo origen.
- TARGET_SELECTION_CHANGED: la selección activa en MariaDB fue modificada.
- SALE_PROMOTION_OUT_OF_SCOPE: el archivo incluye una promoción de venta.

No se sobrescriben ediciones posteriores. Se conservan el manejo de COMMIT
incierto y el hash descritos en la [guía del importador](backend-catalog-import.md).

## Fechas y desempates

`007_promotion_imports` amplía starts_at/ends_at de DATETIME(3) a DATETIME(6),
conserva los valores existentes y agrega `catalog_promotion_sources`. Solo hay
índices de PK/FK/unicidad necesarios.

El precio sigue eligiendo mayor descuento y luego created_at más antiguo.
Si empatan las fechas, entre importadas se compara el UUID original, igual que
Supabase, aunque los lotes lleguen invertidos. Entre nativas sigue ganando el ID
entero menor. Si una nativa y una importada tienen exactamente igual descuento
y microsegundo de creación, la importada precede a la nativa. Entre orígenes
distintos con UUID idénticos, el último desempate es el ID entero: combinar
orígenes no equivale a copiar una única base Supabase.

La cuenta HTTP recibe SELECT solo de promotion_id/source_id para ordenar, sin
acceso a source_key/source_hash ni escritura del mapa. No se añaden UUID al JSON
público. La API acepta fechas con 3 o 6 decimales y devuelve seis. Evita pasar
fechas por JavaScript Date, que recortaría microsegundos. Leer y guardar mediante
PUT conserva precisión; el cálculo usa UTC_TIMESTAMP(6). Health ahora exige 007.

## Comandos

Desde ViveroApp, con `.env` propio:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools build import-catalog
docker compose --env-file .env -f infra/docker/compose.yaml up --build -d --wait api
```

Importar primero el fixture de catálogo versión 1 siguiendo su guía. Después:

```powershell
$env:CATALOG_IMPORT_FILE = (Resolve-Path backend/test/fixtures/promotion-import-demo.json).Path
$preview = docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm -T import-catalog --dry-run | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or -not $preview.can_apply) { throw 'Revisar conflictos antes de aplicar' }
$preview | ConvertTo-Json -Depth 6
```

Tras revisar el reporte del mismo archivo:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm -T import-catalog --apply --expected-sha256 $preview.input_sha256
```

Exportaciones reales se guardan en `tmp/` ignorado y requieren preparación y
autorización del origen. La herramienta solo permite mantenimiento local; no
habilita VPS ni producción. Probar el backend sobre un entorno sintético:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Siguiente bloque: importación local de imágenes con correspondencias, manifiesto
de archivos y conciliación antes del cambio coordinado de clientes.

## Evidencia de este bloque

Pruebas ejecutadas antes de la interrupción de esta entrega; al retomarla solo se
revisaron Git, documentación y el intento de detener los contenedores:

- `npm run check` y `npm test` en `backend/`: correctos, 21 pruebas unitarias.
- Integración Docker: 8/8 en el volumen existente y las mismas 8/8 en una
  instalación nueva. Incluyen precisión de microsegundos, desempate por UUID de
  origen, límites temporales, lectura/edición sin pérdida de precisión, conflictos,
  permisos mínimos y rollback ante un fallo de inserción.
- Migrador: 007 aplicada al volumen existente; 002–007 reconocidas como ya
  aplicadas después de inicializar el volumen nuevo.
- CLI con el fixture sintético: simulación, creación y repetición con reutilización
  del mismo ID. No se importaron datos reales.
- `supabase/tests/verify_migrations.ps1`: 134 comprobaciones correctas.
- Sin compilación Android/Web ni pgTAP: no cambiaron consumidores ni SQL de
  PostgreSQL. El SQL nuevo se probó directamente en MariaDB.

No se validaron MySQL 8, VPS, carga concurrente ni pérdida de conexión durante
COMMIT. La autorización operativa de clientes continúa en Supabase.

El proyecto de validación existente se detuvo conservando sus volúmenes. No se
pudo confirmar la parada del proyecto nuevo `vivero-promoimport-fresh-20260929`:
al retomar, Docker no encontró el pipe `dockerDesktopLinuxEngine`. Cuando Docker
esté disponible, este comando local permite detenerlo sin borrar sus datos:

```powershell
docker compose --env-file tmp/backend-validation-20260929.env -p vivero-promoimport-fresh-20260929 -f infra/docker/compose.yaml stop
```

Ese archivo de entorno es ignorado y específico de la validación local; para una
instalación nueva se siguen los comandos con `.env` de esta guía.

Seguimiento del diagnóstico de Docker (2026-09-30): fuera del entorno restringido,
Docker Desktop y WSL respondieron correctamente. Se ejecutó `up -d --wait db api`
sobre el proyecto nuevo y ambos servicios alcanzaron estado `healthy`. Después,
`stop` terminó correctamente para ambos, conservando los volúmenes. Queda resuelto
el cierre pendiente descrito arriba. Dentro del entorno restringido se reprodujo
`Acceso denegado`; las ejecuciones autorizadas no presentaron ese fallo. No fue
necesario reinstalar Docker ni cambiar su configuración.

## Archivos de esta entrega

- `database/mysql/migrations/007_promotion_imports.sql`
- `backend/src/promotion-dates.js`
- `backend/src/promotions.js`
- `backend/src/catalog-pricing.js`
- `backend/src/app.js`
- `backend/scripts/catalog-import.js`
- `backend/scripts/promotion-import.js`
- `backend/scripts/import-catalog.js`
- `backend/test/fixtures/promotion-import-demo.json`
- `backend/test/promotion-import.test.js`
- `backend/test/promotions.test.js`
- `backend/test/integration.test.js`
- `backend/package.json`
- `infra/docker/compose.yaml`
- `docs/backend-promotion-import.md`
- `docs/backend-api-mariadb.md`
- `docs/backend-catalog-promotions.md`
- `docs/backend-catalog-import.md`
- `docs/supabase-migration-map.md`

Se conservaron los cambios preexistentes, incluidos los de IDE, `AGENTS.md`,
`README.md`, `docs/presential-release.md` y los módulos de migración anteriores.
No se hicieron staging, commit, push ni despliegue.
