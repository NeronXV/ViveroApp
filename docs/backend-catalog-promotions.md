# Promociones y precios efectivos del catálogo

La migración `005_catalog_promotions` añade promociones en la API oficial y
MariaDB. El contrato de referencia es `upsert_catalog_promotion` y
`resolve_catalog_product_price` de Supabase (migración 202608290015). No se
migran aquí descuentos sobre ventas, pedidos, cobros ni consumidores Android/Web.

## Administración

Todas las rutas administrativas requieren sesión y `MANAGE_DISCOUNTS` (igual
que la RPC vigente; no basta MANAGE_PRODUCTS ni MANAGE_PRICES).

| Ruta | Resultado |
|---|---|
| `GET /api/v1/promotions?limit=50&after_id=1` | Todas las promociones, activas/inactivas, por ID; cursor igual al catálogo |
| `POST /api/v1/promotions` | Crea, 201 `{id}` |
| `PUT /api/v1/promotions/:id` | Reemplaza configuración y selección de productos atómicamente, 200 `{id}` |
| `DELETE /api/v1/promotions/:id` | Baja lógica repetible, 200 `{id}`; inexistente 404 |

Ejemplo de cuerpo POST/PUT:

```json
{
  "name": "Plantas de temporada",
  "description": "Descuento demo",
  "scope": "SELECTED_PRODUCTS",
  "promo_type": "PERCENTAGE",
  "percentage_bps": 1250,
  "fixed_amount_cents": null,
  "min_purchase_cents": 0,
  "max_discount_cents": 5000,
  "starts_at": "2026-10-01T00:00:00.000Z",
  "ends_at": "2026-11-01T00:00:00.000Z",
  "is_active": true,
  "product_ids": [1, 2]
}
```

Porcentajes enteros en puntos básicos: 1250 = 12.50%, rango 1–10000. Para monto
fijo usar `FIXED_AMOUNT`, `percentage_bps: null` y `fixed_amount_cents` positivo.
Los importes son centavos enteros seguros (máximo 9007199254740991). No se usa
un número flotante ambiguo `value` que pueda significar porcentaje o centavos.
Al importar Supabase, convertir su porcentaje decimal exacto a puntos básicos;
rechazar valores que no puedan representarse sin redondearlos silenciosamente.

Nombre de 2–100 caracteres, descripción hasta 500. Scope solo ALL_PRODUCTS o
SELECTED_PRODUCTS: el primero exige lista vacía; el segundo, de 1–1000 IDs
enteros distintos y existentes. La relación muchos a muchos necesita
`promotion_products`: ambas tablas tienen PK INT autoincremental, FKs RESTRICT
y solo índices para PK/FK/unicidad. No hay DELETE ni DDL en los grants de API.
Los vínculos retirados se desactivan y se reutilizan al reincorporarlos.

PUT es reemplazo completo, no PATCH. Nombre, scope, tipo, valor correspondiente
y product_ids son necesarios. Campos opcionales omitidos vuelven a sus valores
predeterminados: descripción vacía, mínimo 0, máximo/fechas null, activo true.
Reactivar requiere PUT. Fechas en UTC con 3 o 6 decimales; la API devuelve seis
desde la migración 007. Null elimina el límite. Se rechazan fechas imposibles y fin <= inicio.
Referencias inexistentes devuelven 409 y conservan íntegra la configuración
previa. Las escrituras de una promoción se serializan bloqueando su fila; no
hay control optimista de versión: la última escritura confirmada prevalece.

## Resolución del precio

`GET /api/v1/products` y su variante administrativa conservan `price_cents`
como precio de lista y añaden:

```json
{
  "price_cents": 105,
  "effective_price_cents": 94,
  "active_promotion": { "id": 1, "name": "Demo 10%", "discount_percent": 10.48 }
}
```

Se aplica la misma regla del contrato existente:

1. Producto activo y promoción activa, inicio <= ahora, fin > ahora.
2. Mínimo comparado con el precio unitario de lista, no con cantidad ni total del
   carrito; scope global o vínculo de producto activo.
3. Porcentaje redondeado al centavo más próximo (medio centavo hacia arriba), o
   monto fijo. El descuento se limita por precio y máximo configurado.
4. Descartar descuentos cero. Elegir el mayor descuento, luego created_at más
   antiguo y finalmente ID menor para nativas. Para importadas se conserva el
   UUID original: ver [desempates de importación](backend-promotion-import.md).
   Las promociones no se acumulan.

El porcentaje mostrado se calcula sobre el descuento real tras redondeo y
topes; puede diferir del porcentaje configurado. Si no hay promoción aplicable,
precio efectivo = lista y active_promotion = null. Producto inactivo en lectura
administrativa tampoco recibe promoción. Mayoreo no interviene en este cálculo.

La página se calcula en una sola sentencia SQL con el reloj UTC de MariaDB y
una vista consistente de productos, promociones y vínculos. DECIMAL evita
overflow al multiplicar precios grandes y evita redondear dos veces el
porcentaje mostrado. No hay descuentos calculados por el cliente ni parámetros
de precio/fecha controlados por el visitante.

Esta consulta es para catálogo, no una reserva ni cotización garantizada para
checkout. Al migrar ventas/pedidos deberán recalcular bajo transacción y guardar
el precio/promoción aplicado en cada partida. No conectar todavía estos IDs
enteros al checkout Supabase.

## Actualización local

Desde ViveroApp, con `.env` local propio:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up --build -d --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Usar solo datos sintéticos para la suite. Health exige 005; volúmenes existentes
necesitan migrate y volúmenes nuevos aplican el init correspondiente. No se
modifica el baseline ni se borran volúmenes. Ante un error de DDL, inspeccionar
el estado antes de reintentar: las migraciones no son transaccionales.

No se agregan promociones al seed para no alterar precios demo por sorpresa.
No se ha medido rendimiento con miles de promociones/productos; se conservan
los índices mínimos solicitados y paginación de hasta 100 productos.

Siguiente bloque recomendado: importador local con modo de simulación y
correspondencias persistentes UUID → ID entero para categorías/productos, antes
de importar imágenes/promociones y planificar el cambio coordinado de clientes.
El importador debe consumir exportaciones autorizadas, no conectarse ni escribir
en Supabase remoto sin permiso explícito.

## Evidencia de esta entrega

- `npm run check` y `npm test`: correctos, 16/16 pruebas unitarias.
- Volumen existente `vivero-validation-20260929`: 005 aplicada tras omitir
  002–004; suite final HTTP/MariaDB 6/6. Se corrigió el fixture que enviaba
  after_id=0 para la primera promoción; el cursor inicial debe omitirse.
- Volumen vacío `vivero-promotions-fresh-20260929`: inicialización y suite 6/6.
  Migrador posterior: 002–005 `already applied`. Son seis casos en dos escenarios,
  no doce pruebas distintas. Contenedores de ambas pruebas detenidos; volúmenes
  conservados.
- Casos de precio: medio centavo, no acumulación, selección por fecha/ID, mínimo,
  topes, cero, importes máximos seguros, porcentaje mostrado sin doble redondeo,
  inicio inclusivo y fin exclusivo. Se usó reloj fijo solo en una conexión de
  prueba, sin alterar el reloj global o el de la API.
- Casos de administración: permisos MANAGER/INVENTORY, FK real, rechazo de valores
  SQL inválidos, reemplazo de selección, scope global, rollback ante referencias
  inexistentes, baja repetible y paginación.
- Verificación estática Supabase: 134 comprobaciones correctas. Sin pgTAP porque
  no cambió PostgreSQL; SQL nuevo ejecutado en MariaDB. Sin build Android/Web
  porque no se modificaron consumidores. No hay pruebas de carga, escrituras
  concurrentes de promociones, checkout MariaDB, VPS ni MySQL 8.
- `git diff --check` y revisión de whitespace de archivos nuevos: correctos.

Archivos de esta entrega:

- `backend/src/promotions.js`, `backend/src/catalog-pricing.js`,
  `backend/src/catalog.js`, `backend/src/app.js`, `backend/package.json`.
- `backend/test/promotions.test.js`, `backend/test/integration.test.js`.
- `database/mysql/migrations/005_catalog_promotions.sql`, `infra/docker/compose.yaml`.
- Este documento, `docs/backend-api-mariadb.md`, `docs/supabase-migration-map.md`.

Estado Git: main y HEAD sin cambios. Se conservaron los cambios preexistentes de
IDE, AGENTS, README, presential-release, auditorías, automation y backend de
entregas anteriores. El backend sigue sin seguimiento. Sin staging, commit,
push, despliegue, importación ni operaciones remotas de Supabase.
