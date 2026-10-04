# Importación local de imágenes

La migración `008_image_imports` extiende el importador oficial `import-catalog`
con manifiestos versión 3. No cambia consumidores: Android y Web siguen usando
Supabase. No descarga archivos, accede a Storage remoto ni migra datos reales.

## Preparar el manifiesto

Ver `backend/test/fixtures/image-import-demo.json` y su PNG sintético en
`backend/test/fixtures/images/`. El objeto contiene exactamente `schema_version: 3`,
`source_key` e `images`. Cada imagen exige:

| Campo | Contrato |
|---|---|
| `id`, `product_id` | UUID del origen, solo para correspondencias; nunca PK de MariaDB |
| `file` | Nombre plano local PNG/JPEG/WebP, sin rutas, URL ni enlaces simbólicos |
| `sha256` | SHA-256 hexadecimal minúsculo de los bytes originales |
| `content_type` | `image/png`, `image/jpeg` o `image/webp`; debe coincidir con los bytes |
| `alt_text` | Texto sin controles, hasta 500 caracteres, sin espacios exteriores |
| `sort_order` | Entero 0–65535, distinto para cada imagen del producto |
| `is_primary` | Booleano; exactamente una principal por producto incluido |

Primero importar los productos mediante el formato versión 1 con el mismo
`source_key`. El lote de imágenes contiene el conjunto activo completo de cada
producto incluido: máximo 12 por producto y 24 en total. Dividir por productos,
nunca partir las imágenes de un mismo producto entre lotes.

Supabase permite texto alternativo null y posiciones empatadas. La preparación
del manifiesto debe convertir null a texto vacío y asignar posiciones distintas
respetando el orden original `is_primary DESC, sort_order, id`. Si no existe una
principal, elegirla explícitamente antes de importar. El importador no resuelve
estas decisiones silenciosamente. No incluir `storage_path`, fechas ni otros
campos del origen: el archivo se identifica mediante nombre y hash revisados.

Los archivos se montan en solo lectura. Se comprueban todos antes de escribir:
5 MiB por archivo, 16 millones de píxeles, sin animaciones y recodificación WebP
con las mismas reglas de la API. Las rutas se limitan a nombres planos y el
contenedor Linux rechaza enlaces simbólicos. No editar archivos durante la tarea.

## Simulación, conciliación y aplicación

La simulación es una transacción SQL de solo lectura y no escribe archivos ni
consume IDs. La aplicación usa el bloqueo compartido de los importadores y
bloquea los productos para coordinarse con las escrituras de imágenes por API.
Todas las filas y correspondencias se confirman en una transacción.

`catalog_image_sources` tiene PK entera autoincremental, FK real y únicos mínimos
para origen/imagen. Guarda huellas del manifiesto normalizado y del WebP final.
La cuenta HTTP no tiene acceso a esa tabla. No se alteran permisos operativos.

Repetir el mismo manifiesto reutiliza IDs y archivos. La conciliación comprueba
las imágenes incluidas y el conjunto activo de sus productos; no es un inventario
global del volumen ni de productos ausentes del lote. Bloquea todo el lote ante:

- Producto sin correspondencia: `PRODUCT_MAPPING_REQUIRED`.
- Archivo de origen ausente, ilegible o modificado: `SOURCE_FILE_UNREADABLE` o
  `SOURCE_FILE_HASH_MISMATCH`; bytes no válidos: `INVALID_IMAGE`.
- Datos del manifiesto cambiados: `SOURCE_CHANGED`; edición/baja local:
  `TARGET_CHANGED`.
- Archivo almacenado ausente o corrupto: `TARGET_FILE_UNREADABLE` o
  `TARGET_FILE_CHANGED`.
- Imagen activa fuera del manifiesto, incluida una carga local por API:
  `UNLISTED_ACTIVE_IMAGE`. No se sustituye ni se elimina automáticamente.
- Orden ambiguo: `AMBIGUOUS_IMAGE_ORDER`.

Los archivos se escriben con creación exclusiva antes de publicar sus referencias.
Un fallo SQL o una caída puede dejar archivos huérfanos. Se conservan, incluso si
COMMIT tiene resultado incierto: no borrar archivos para intentar revertirlo.
Volver a simular el mismo manifiesto permite comprobar qué quedó confirmado.
No hay reparación automática, búsqueda global de huérfanos ni purga en este bloque.
Respaldar SQL y archivos conjuntamente sigue siendo necesario.

## Comandos desde la raíz

Usar un `.env` local propio, preparado según `.env.example`. Para una base existente:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools build import-catalog
docker compose --env-file .env -f infra/docker/compose.yaml up --build -d --wait api
```

Primero aplicar el catálogo sintético versión 1 siguiendo
[la guía del catálogo](backend-catalog-import.md). Después:

```powershell
$env:CATALOG_IMPORT_FILE = (Resolve-Path backend/test/fixtures/image-import-demo.json).Path
$env:CATALOG_IMPORT_IMAGES_DIR = (Resolve-Path backend/test/fixtures/images).Path
$preview = docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm -T import-catalog --dry-run | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or -not $preview.can_apply) { throw 'Revisar conflictos antes de aplicar' }
$preview | ConvertTo-Json -Depth 6
```

Tras revisar el reporte:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm -T import-catalog --apply --expected-sha256 $preview.input_sha256
```

El hash de confirmación corresponde a los bytes JSON e incluye indirectamente los
hashes de todos los archivos. El apply vuelve a comprobar cada archivo. Salidas:
0 correcto, 2 conflictos, 1 error. El formato 1/2 y sus comandos siguen vigentes.
Al terminar, quitar las variables del proceso si se vuelve al fixture por defecto:

```powershell
Remove-Item Env:CATALOG_IMPORT_FILE, Env:CATALOG_IMPORT_IMAGES_DIR -ErrorAction SilentlyContinue
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Para un lote preparado localmente, guardar manifiesto y archivos en `tmp/` ignorado
y resolver sus rutas absolutas. La herramienta conserva las restricciones de
mantenimiento local; no habilita uso sobre producción ni VPS. `/health` continúa
comprobando el contrato HTTP 007; este importador exige además la migración 008.

Siguiente bloque recomendado: preparar el corte del catálogo Web con una revisión
de contratos, configuración de origen y plan de reversión, antes de activar el
consumo del backend. Login, ventas y caja deben permanecer en su flujo vigente.

## Evidencia del bloque (2026-09-30)

- `npm run check`: correcto. `npm test`: 23/23 en Windows y en el contenedor
  Linux; Linux también ejecutó el rechazo de enlaces simbólicos. El primer intento
  en el sandbox Windows falló por `spawn EPERM`; fuera del sandbox pasó.
- Integración: 9/9 sobre el volumen existente actualizado con 008 y las mismas
  9/9 desde una base nueva. La comprobación adicional de posiciones distintas se
  incorporó después de la primera ejecución y quedó cubierta en la base nueva
  y en las pruebas unitarias Linux.
- Migrador: 008 aplicada al volumen existente; 002–008 reconocidas como ya
  aplicadas después de inicializar el nuevo.
- CLI real: simulación, creación y repetición sin duplicar la imagen sintética.
  Lectura HTTP posterior: 200, `image/webp`, 68 bytes; la lista conservó texto
  alternativo, posición y principal. Fixture retenido en el volumen de pruebas.
- Verificación estática Supabase: 134 comprobaciones correctas. No cambió SQL
  PostgreSQL; no se ejecutó pgTAP. El SQL nuevo se ejecutó en MariaDB.
- Ambos proyectos de prueba detenidos correctamente; volúmenes conservados.
- Sin compilación Android/Web porque no cambió su código. Sin pruebas de VPS,
  MySQL 8, disco lleno, caída durante COMMIT, carga concurrente del importador ni
  restauración de respaldos. No se importaron datos reales.

Archivos creados o modificados en este bloque:

- `backend/scripts/image-import.js`, `backend/scripts/import-catalog.js`.
- `backend/test/image-import.test.js`, `backend/test/integration.test.js`.
- `backend/test/fixtures/image-import-demo.json`, `backend/test/fixtures/images/demo.png`.
- `backend/package.json`, `database/mysql/migrations/008_image_imports.sql`.
- `infra/docker/compose.yaml`.
- `docs/backend-image-import.md`, `docs/backend-catalog-import.md`,
  `docs/backend-api-mariadb.md`, `docs/supabase-migration-map.md`.

Se conservaron los cambios preexistentes de IDE, AGENTS, README, documentación
operativa y módulos anteriores. Rama `main`, sin staging, commit, push ni
despliegue. El backend y su documentación continúan sin seguimiento en Git como
al inicio de este bloque.
