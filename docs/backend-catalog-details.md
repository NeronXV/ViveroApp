# Catálogo operativo: categorías, cuidados y mayoreo

Entrega incremental sobre la API oficial. No cambia los consumidores Web o
Android, ni las RPC de Supabase. Imágenes y promociones siguen pendientes en la
API; este documento no declara una migración completa del catálogo.

## Contrato

- `PATCH /api/v1/categories/:id`: nombre (2–100 caracteres), descripción
  (0–2000), `is_active` booleano. Omisiones conservan el valor existente;
  objetos vacíos, null y campos desconocidos se rechazan.
- `DELETE /api/v1/categories/:id`: baja lógica repetible. No elimina ni cambia
  los productos asociados. La consulta pública los oculta mientras su categoría
  esté inactiva. Reactivar la categoría no reactiva productos dados de baja.
- Ambas operaciones requieren una sesión y `MANAGE_PRODUCTS`; inexistente: 404.
- Productos admiten `watering_advice` (0–2000 caracteres), `light_type` y
  `recommended_climate` (0–160). Son textos, no enumeraciones inventadas.
- `wholesale_price_cents` es un entero seguro no negativo o null (sin precio de
  mayoreo). Omitirlo en PATCH conserva el valor. Cambiarlo o borrarlo requiere
  `MANAGE_PRICES`, igual que cambiar el precio de lista; el servidor compara
  ambos precios bajo bloqueo antes de actualizar. INVENTORY puede reenviar un
  precio sin cambios. Crear productos sigue requiriendo ambas capacidades.
- La lectura pública incluye cuidados, pero no mayoreo. Este último se devuelve
  solo con `status=all`, que exige `MANAGE_PRODUCTS`. Es información comercial;
  guardarlo no activa reglas automáticas de precio en ventas o pedidos.

La migración `003_catalog_details.sql` agrega cuatro columnas a `products`, sin
tablas, índices ni cambios de PK. Conserva los registros existentes: mayoreo
inicia en null y cuidados en texto vacío. No duplica el mínimo de inventario por
sucursal con un segundo mínimo de producto: esa diferencia con Supabase deberá
resolverse explícitamente al migrar inventario.

## Levantar o actualizar

Desde la raíz, con `.env` local configurado según `.env.example`:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up --build -d --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Ejecutar tests únicamente en una base local sintética. Una instalación vacía
aplica 003 mediante el init de MariaDB; los volúmenes existentes requieren el
migrador. Health exige 003 para evitar declarar lista una API con esquema viejo.
No borrar volúmenes para actualizar. DDL no es transaccional: ante una aplicación
parcial, inspeccionar el error y el esquema antes de reintentar.

## Preparación del traslado de IDs (diseño pendiente de implementación)

Conservar PK enteras. El importador futuro deberá registrar correspondencias
persistentes por entidad: origen Supabase + UUID de origen → FK entera destino.
No derivar IDs de nombres, códigos, posición de filas ni casts. Si se usan tablas
de correspondencia, deben tener PK autoincremental, unicidad del origen y FK real
al destino; evitar una tabla polimórfica sin integridad referencial.

Importar primero categorías, después productos y finalmente imágenes/relaciones
de promociones. Rechazar referencias sin correspondencia y duplicados ambiguos,
emitir un reporte de conciliación y permitir reanudar sin duplicar registros.
Validar límites de longitud antes de escribir: Supabase tiene textos sin los
límites nuevos; nunca truncar silenciosamente. No importar contraseñas/sesiones.

Antes de cambiar Web: resolver carritos persistidos con UUID, pedidos en curso,
ventas pendientes y sincronización offline Android; definir una ventana de corte
y conciliación. Un producto entero de MariaDB no se enviará al checkout de
Supabase. No se agrega doble escritura ni un selector permanente de backend.

## Siguiente entrega

Imágenes de producto con almacenamiento persistente Docker, validación de
archivos y autorización; después promociones y precios efectivos equivalentes
a `resolve_catalog_product_price`. Pedidos y ventas seguirán usando Supabase
hasta completar su corte coordinado y sus pruebas de atomicidad/idempotencia.

## Validación de esta entrega (2026-09-29)

- `npm run check`: correcto. `npm test`: 10/10. El primer intento de tests
  recibió `spawn EPERM` del sandbox; la repetición autorizada pasó.
- Volumen sintético existente `vivero-validation-20260929`: el migrador omitió
  002 y aplicó 003; Compose tests pasó 4/4 sobre HTTP y MariaDB reales.
- Volumen nuevo `vivero-catalog-fresh-20260929`: inicialización completa y tests
  4/4, incluyendo los casos adicionales de INVENTORY sin permiso de cambiar o
  borrar mayoreo, y SALES sin permiso de editar/desactivar categorías.
- Migrador sobre la instalación nueva: 002 y 003 `already applied`, sin repetir
  DDL. Los contenedores de validación quedaron detenidos, volúmenes conservados.
- Verificación estática Supabase: 134 comprobaciones correctas. No se ejecutó
  pgTAP porque no se modificó PostgreSQL; esta migración se probó en MariaDB.
- Sin compilación Android ni Web: no se modificó código de esos consumidores.
  Sin pruebas contra MySQL 8, VPS ni datos reales.

Archivos de esta entrega: `backend/src/app.js`, `backend/src/catalog.js`,
`backend/test/auth.test.js`, `backend/test/unit.test.js`,
`backend/test/integration.test.js`,
`database/mysql/migrations/003_catalog_details.sql`, `infra/docker/compose.yaml`,
`docs/backend-api-mariadb.md`, `docs/supabase-migration-map.md` y este documento.
Se conservaron los cambios previos, incluidos IDE, AGENTS, README,
`docs/presential-release.md`, auditorías y archivos sin seguimiento. El backend
de las entregas anteriores también estaba sin seguimiento al iniciar esta.
No hubo staging, commit, push, despliegue ni operación remota.
