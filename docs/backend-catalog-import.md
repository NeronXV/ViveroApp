# Importación local de categorías y productos

La migración `006_catalog_imports` agrega correspondencias persistentes de
origen Supabase → MariaDB. El comando de mantenimiento `import-catalog` consume
un JSON local y, por defecto, solo simula. No descarga datos ni se conecta a
Supabase. No hay endpoint de importación ni cambios de clientes.

El formato versión 1 de este documento se conserva. La
[versión 2 para promociones](backend-promotion-import.md) usa el mismo comando y
requiere la migración 007, que también pasa a ser requerida por health HTTP.
La [versión 3 para imágenes](backend-image-import.md) agrega manifiesto y archivos
locales al mismo comando, y exige la migración 008.

## Alcance y formato

El [fixture sintético](../backend/test/fixtures/catalog-import-demo.json) es el
contrato completo del archivo, `schema_version: 1`. Se exige exactamente ese
conjunto de campos: envoltura `source_key`, `categories`, `products` y todos los
campos mostrados por registro. `source_key` identifica un origen estable y su
entorno, usando 1–64 caracteres a-z, 0–9, guion o guion bajo. No colocar URLs,
credenciales ni nombres de personas; no cambiar la etiqueta entre lotes del
mismo origen.

Los IDs de origen y category_id son UUID en texto, normalizados a minúsculas.
No se convierten a números: cada destino tiene un nuevo ID INT AUTO_INCREMENT.
Las tablas `catalog_category_sources` y `catalog_product_sources` también tienen
PK entera y FK RESTRICT al destino; la pareja origen/UUID y cada destino son
únicos. La relación de producto a categoría se resuelve con el mapa confirmado.
La cuenta HTTP `catalog_api` no recibe permisos sobre estas tablas.

Todos los campos de producto deben estar presentes, incluidos null explícitos
para barcode, scientific_name y wholesale_price_cents. Dinero como número entero
seguro, nunca string ni decimal. Descripción null de categoría se representa
como texto vacío en destino. Se rechazan espacios exteriores que implicarían
normalización, campos desconocidos, textos fuera de límite y valores inválidos;
no se trunca contenido. Se conservan estados activos e inactivos.

Cada lote debe incluir todas las categorías referidas por sus productos, aunque
ya estén importadas. Máximo 500 categorías, 1000 productos y 5 MiB de UTF-8.
Para lotes posteriores repetir las categorías necesarias sin modificarlas.
El JSON no es un dump completo de Supabase: preparar una proyección explícita
de los campos del fixture sobre una exportación autorizada y conservar el
original. No se importan timestamps históricos: MariaDB asigna los de creación.

Imágenes, promociones, existencias, mínimo de stock, usuarios y operaciones no
forman parte del formato; si aparecen como campos extra, el archivo se rechaza.
El mínimo global de producto en Supabase requiere resolver su relación con el
mínimo por sucursal de MariaDB antes de migrar inventario. No descartar estos
datos del respaldo original ni interpretar este lote como una migración total.

## Simulación, conflictos y repetición

La simulación usa una transacción READ ONLY con snapshot consistente. No inserta
y revierte como simulación: no reserva IDs ni avanza AUTO_INCREMENT. Las
colisiones de nombres, códigos y barcodes se comparan en MariaDB con la misma
collation de sus índices únicos (incluye diferencias de mayúsculas y acentos).

El reporte JSON contiene hash SHA-256 del archivo y una acción por registro:
`create`, `reuse` o `conflict`, con entidad, índice de entrada (base cero) e ID
destino cuando ya existe. No imprime el contenido del catálogo ni SQL/secretos.
Una simulación no asigna ni promete IDs para registros nuevos.

- `DUPLICATE_SOURCE_ID`: el archivo repite el mismo UUID para una entidad.
- `DUPLICATE_BUSINESS_KEY_IN_BATCH`: dos registros colisionan según MariaDB;
  se marca el primer índice del grupo.
- `UNMAPPED_BUSINESS_KEY_COLLISION`: ya existe nombre/código/barcode sin esa
  correspondencia. No se adopta automáticamente un registro por similitud.
- `SOURCE_CHANGED`: los campos de un UUID ya importado cambiaron.
- `TARGET_CHANGED`: hubo una edición posterior en MariaDB.
- `CATEGORY_NOT_IN_BATCH`: falta una categoría referida.

Con un conflicto se bloquea el lote completo. Este primer importador es de
inserción y reanudación, no sincronización ni herramienta para sobrescribir.
Conserva cambios posteriores; la conciliación de conflictos es un trabajo
explícito posterior, sin borrar mapas, registros demo o registros operativos
como atajo. La aplicación vuelve a validar el estado actual de la base.

Al aplicar, un bloqueo de mantenimiento serializa los importadores y se
bloquean filas relevantes. Categorías, productos y mapas se confirman en una
única transacción. Un fallo de inserción revierte el lote, aunque una aplicación
fallida sí puede dejar huecos normales de AUTO_INCREMENT. Repetir el mismo lote
ya confirmado devuelve `reuse` con los mismos IDs y no actualiza sus filas.

Si se pierde la respuesta del COMMIT se informa
`COMMIT_UNCERTAIN_RECHECK_SAME_FILE`: revisar con simulación y repetir exactamente
el archivo, sin borrar datos. El hash solicitado por `--apply` protege el archivo
revisado contra cambios, no sustituye el control de conflictos de la base.

## Comandos para Tony

Desde ViveroApp, con `.env` local configurado según `.env.example`:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools build import-catalog
```

Sin configurar archivo, se usa exclusivamente el fixture sintético incluido.
Para preparar otro archivo local, guardarlo en `tmp/` (ignorado por Git) y
configurar su ruta absoluta antes de simular:

```powershell
New-Item -ItemType Directory -Force tmp | Out-Null
Copy-Item backend/test/fixtures/catalog-import-demo.json tmp/catalog-import.json
$env:CATALOG_IMPORT_FILE = (Resolve-Path tmp/catalog-import.json).Path
$preview = docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm -T import-catalog --dry-run | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or -not $preview.can_apply) { throw 'Revisar el reporte antes de aplicar' }
$preview | ConvertTo-Json -Depth 6
```

Tras revisar el reporte, aplicar el mismo archivo:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm -T import-catalog --apply --expected-sha256 $preview.input_sha256
```

Aplicar sin hash o con un hash distinto falla. Salidas: 0 operación válida,
2 reporte con conflictos, 1 error de archivo, validación o ejecución. El montaje
del archivo es solo lectura y falla si no existe; no crea directorios sustitutos.
No versionar exportaciones reales ni reportes que revelen datos del origen.

El comando exige entorno development, host Docker `db`, base `vivero` y usuario
administrativo local. No está habilitado para VPS/producción. Para volúmenes
existentes ejecutar migrate; los nuevos inicializan 006. Health de HTTP sigue
exigiendo 005 porque el importador no cambia endpoints; la CLI verifica 006.

Pruebas sobre un entorno local **sintético**, no sobre el destino de una
importación real:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

La siguiente entrega recomendada es importar promociones mediante los mapas
de producto, preservando selección, fechas y orden de desempate. Después,
imágenes y validación de conciliación antes del cambio coordinado de clientes.

Referencias de MariaDB: [transacciones](https://mariadb.com/docs/server/reference/sql-statements/transactions/start-transaction)
y [JSON_TABLE](https://mariadb.com/docs/server/reference/sql-functions/special-functions/json-functions/json_table).

## Evidencia de esta entrega

- `npm run check` y `npm test`: correctos, 19/19 pruebas locales.
- Volumen previo `vivero-validation-20260929`: migración 006 aplicada tras omitir
  002–005; suite HTTP/MariaDB 7/7. Se verificaron conteos y AUTO_INCREMENT antes
  y después de simular, IDs repetibles, FK, permisos mínimos, cambios de origen
  y destino, colisiones por acentos y rollback con fallo de inserción inyectado.
- Volumen nuevo `vivero-import-fresh-20260929`: inicialización y suite final 7/7;
  migrador posterior 002–006 `already applied`. Son siete casos ejecutados en
  dos escenarios, no catorce casos distintos.
- CLI real: dry-run del fixture incluido; hash incorrecto rechazado con salida
  1; aplicación correcta y repetición con los mismos IDs. Solo quedó importado
  el fixture sintético en el volumen de validación, ninguna exportación real.
- Verificación estática Supabase: 134 comprobaciones correctas. No hubo cambios
  PostgreSQL ni pgTAP; SQL nuevo ejecutado en MariaDB. Sin build Android/Web
  porque no cambiaron los consumidores. Sin pruebas de carga, importaciones
  concurrentes, pérdida de respuesta de COMMIT, datos reales, MySQL 8 ni VPS.
- Revisión de los archivos de entrega y `git diff --check`: correctos.
  Contenedores de ambos entornos de prueba detenidos, volúmenes conservados.

Archivos de esta entrega:

- `backend/scripts/catalog-import.js`, `backend/scripts/import-catalog.js`.
- `backend/test/catalog-import.test.js`, `backend/test/integration.test.js`,
  `backend/test/fixtures/catalog-import-demo.json`, `backend/package.json`.
- `database/mysql/migrations/006_catalog_imports.sql`, `infra/docker/compose.yaml`.
- Este documento, `docs/backend-api-mariadb.md`, `docs/supabase-migration-map.md`.

Estado Git: main y HEAD sin cambios. Se conservaron cambios previos de IDE,
AGENTS, README, presential-release, auditorías, automation y backend de entregas
anteriores. Backend y migraciones siguen sin seguimiento. Sin staging, commit,
push, despliegue ni operaciones remotas de Supabase.
