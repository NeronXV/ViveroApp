# Compras y proveedores: bloque 020

Implementación en el backend oficial. Web/Android conservan sus contratos
Supabase hasta un cambio coordinado de consumidores. Health requiere
`020_supplier_purchases`; no se importan proveedores ni documentos reales.

## Contrato REST

Todas las rutas requieren Bearer, MANAGE_INVENTORY y sucursal propia activa,
incluyendo OWNER. Proveedores/presentaciones son globales; compras se consultan
y operan solo en la sucursal del actor. Prefijo `/api/v1`:

| Método y ruta | Entrada |
| --- | --- |
| GET /suppliers | limit, after_id, include_inactive |
| POST /suppliers | code, name, is_active |
| PATCH /suppliers/:id | code, name, is_active |
| GET /suppliers/:id/presentations | Sin cuerpo |
| PUT /suppliers/:id/presentations | code, display_name, nominal_size, size_unit, notes |
| GET /supplier-purchases | limit, after_id, status |
| POST /supplier-purchases | Borrador y header Idempotency-Key |
| GET /supplier-purchases/:id | Documento y líneas, sin hashes de reintento |
| PATCH /supplier-purchases/:id/items/:itemId | resolution_status, product_id, suggested_common_name, suggested_presentation |
| POST /supplier-purchases/:id/confirm | Cuerpo `{}` y header Idempotency-Key |

Listados por ID ascendente: limit 1–100, 50 por defecto; items y next_after_id.
Campos desconocidos/duplicados se rechazan. Este contrato REST no reproduce
literalmente firmas RPC ni nombres camelCase. Alta de proveedor con código
ocupado devuelve 409; edición usa ID explícito, sin sobrescribir por código.
nominal_size es decimal positivo en string, máximo dos decimales; unidad cm/in/l/gal
o ambos campos null. No se infiere tamaño de una descripción de maceta.

Borrador: supplier_id, document_date YYYY-MM-DD real, external_reference nullable,
payment_terms CASH/CREDIT/OTHER, expected_total_cents entero, source_file_name
nullable e items (1–100). Cada línea requiere line_number único, raw_description,
container_code, suggested_common_name nullable, suggested_presentation nullable,
quantity entero 1–1000000 y unit_cost_cents entero no negativo.
MXN fijo; suma exacta calculada con BigInt. Valores monetarios acotados al rango
seguro JSON y almacenados en centavos enteros. El límite HTTP existente de 16 KiB
también se aplica: 100 líneas no garantizan cabida con descripciones máximas.

Los borradores conservan una instantánea normalizada de las líneas y no afectan
inventario. Reordenar líneas no cambia el reintento. Misma clave con otro actor,
sucursal o contenido devuelve 409. Referencia externa única por proveedor cuando
no es null. Recuperación mediante POST repetido o GET por ID/listado propio.
Claves opacas de 16–128 caracteres A-Z/a-z/0-9/._:-, almacenadas como SHA-256;
no son IDs UUID. Un reintento válido funciona con proveedor después desactivado.

Revisión permite MATCHED con producto/categoría activos o IGNORED sin producto.
Las sugerencias no cambian el catálogo ni los precios. MATCHED aprende equivalencia
por proveedor, descripción y contenedor normalizados, con hash de un par JSON
para evitar colisiones por delimitadores. Futuros borradores usan AUTO_MATCHED
solo si producto/categoría siguen activos. IGNORE no elimina equivalencias previas.
Una colisión concurrente al crear una equivalencia revierte la operación con 409;
el cliente puede repetir su revisión.

Confirmar exige DRAFT, ninguna línea UNMATCHED y al menos una línea asociada.
Productos se vuelven a validar. Cada línea asociada crea una RECEPTION en la
bitácora existente y guarda su movement_id con FK y unicidad; las ignoradas no
entran. Movimiento y saldo se actualizan por el trigger existente dentro de la
misma transacción que marca RECEIVED. Nunca se activa inventario automáticamente.
Repetir la confirmación con la misma clave no duplica recepciones; otra clave
devuelve 409. RECEIVED no se puede revisar. Costos de compra no cambian precios
de venta y payment_terms no genera pagos de caja ni contabilidad de proveedores.
No se añade cancelación: Supabase también define CANCELLED sin RPC de cancelación.

## Esquema y seguridad

Cinco tablas con PK enteras autoincrementales y FKs: suppliers,
supplier_presentations, supplier_purchase_documents, supplier_purchase_items y
supplier_product_aliases. Son entidades necesarias del flujo existente, sin
tablas intermedias adicionales. Solo índices PK/FK/unicidad necesarios.
Auditoría: created_by/received_by/resolved_by/updated_by y vínculos a movimientos;
fuente/costos originales no se sobrescriben al revisar. Runtime sin DELETE ni
UPDATE de costos, totales o fuente original. SQL no sustituye la autorización
por capacidad/sucursal de la API; sus credenciales no llegan a los consumidores.

Se conserva el orden de bloqueo de autenticación/sucursal del backend y se
bloquea documento antes de revisión/confirmación. Recepciones usan lockInventoryRow;
no se introduce otra proyección de stock. Presentaciones/equivalencias usan INSERT
o UPDATE explícito para respetar los grants por columna de MariaDB.

## Validación de esta sesión: 2026-09-30

- Migración 020 aplicada al volumen local sintético vivero-validation-20260929,
  sin reset. API saludable y fixtures eliminados.
- Prueba específica: permisos, aislamiento por sucursal, código/referencia única,
  borradores simultáneos, conflicto de contenido, presentaciones, revisión,
  aprendizaje AUTO_MATCHED, documentos inmutables y proveedor desactivado.
- Trigger sintético fuerza fallo en segunda recepción: primera entrada, saldo,
  vínculos y estado se revierten. Confirmaciones simultáneas generan saldo 5.000
  una sola vez y conservan precio de venta original.
- 53/53 pruebas unitarias y 20/20 integraciones completas aprobadas en Docker
  Node 24/MariaDB. Check de sintaxis aprobado; 134 controles estáticos Supabase
  aprobados (no validan MariaDB, cubierta por integración).
- Durante desarrollo se corrigió el fixture de cuenta activa y las escrituras
  ON DUPLICATE KEY que no funcionaban con permisos por columna; pruebas finales
  pasan con las credenciales runtime, sin ampliar grants.
- No Android/pgTAP: no cambia Kotlin/PostgreSQL. Volumen vacío integral,
  importación de documentos reales, parser CSV/PDF/OCR y consumidores pendientes.
  No hubo commit, push ni despliegue.

## Comandos desde la raíz

Con `.env` local basado en la plantilla, sin secretos versionados:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests npm test
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --rm tests
npm --prefix backend run check
git diff --check
```

Archivos del bloque: backend/src/purchases.js, backend/src/app.js,
backend/test/purchases.test.js, backend/test/purchases-integration.test.js,
backend/test/integration.test.js, backend/package.json,
database/mysql/migrations/020_supplier_purchases.sql, infra/docker/compose.yaml,
esta guía, docs/backend-api-mariadb.md y docs/supabase-migration-map.md.

Siguiente módulo recomendado: reportes de ventas y productos.
