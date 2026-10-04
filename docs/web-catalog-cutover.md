# Preparación del cambio de catálogo Web

Revisión local del 2026-09-30. Estado: **preparación implementada parcialmente;
cambio de consumidor no activado**. No se consultó ni modificó ningún entorno
remoto. ViveroApp: `main`, HEAD `a4621f6d42deb735714c047b05789ee83760939a`.
ViveroWeb: `main`, HEAD `d4db2b666e4d726d2887d133e64fa34794535ef6`.

## Resultado de este bloque

El endpoint existente `GET /api/v1/products` ahora acepta `search` y `category_id`.
Se aplican antes de paginar y calcular las promociones de la página; no se filtra
una página ya descargada. No hay otro catálogo, ruta experimental ni selector
de motor. El servicio Web y el carrito conservan su fuente actual.

- `search`: texto recortado, máximo 80 unidades UTF-16, sin controles ni Unicode
  mal formado. Vacío equivale a sin búsqueda. Coincidencia de subcadena en nombre
  común o científico, según la colación de MariaDB. `%` y `_` son texto literal.
- `category_id`: ID entero positivo canónico, máximo 4294967295. Categoría
  inexistente produce una lista vacía, no un error ni una ampliación del filtro.
- `limit`, `after_id` y `status` conservan su contrato. Orden por ID ascendente;
  `next_after_id` pertenece al conjunto filtrado. Reiniciar cursor al cambiar filtros.
- Claves desconocidas o repetidas se rechazan. Categorías no admite esos filtros.
- Visibilidad pública exige producto y categoría activos. `status=all` sigue
  requiriendo sesión propia API y `MANAGE_PRODUCTS`; no se expone precio mayorista
  al visitante público. No se añadieron tablas ni índices.

## Contratos que debe resolver el cambio de consumidor

Las rutas Web siguientes son relativas a `ViveroWeb/src/`.

| Área | Web actual | API actual / trabajo previo a activar |
|---|---|---|
| Carga | `features/public-catalog/catalog-service.ts`: RPC `get_public_catalog` V3 | Sustituir una sola implementación por HTTP; conservar aborto, timeout y errores seguros |
| IDs | `catalog-parser.ts`, categorías y promociones exigen UUID | API usa enteros; actualizar tipos, parsers y consumidores juntos. No convertir entero en UUID ni presentarlo como UUID antiguo |
| Respuesta | `catalog-types.ts`: items, categories y page en un objeto | API devuelve items y next_after_id; categorías se cargan por su endpoint, recorriendo todas sus páginas. Una categoría faltante debe ser error de consistencia, no nombre inventado |
| Paginación | Cursor nombre minúsculo + UUID, orden alfabético | Cursor entero y orden por ID. Adaptar hooks y decidir explícitamente si se acepta ese orden antes del corte |
| Filtros | `catalog-query.ts`: búsqueda y categoría | Disponibles en products desde este bloque; no compatibles con los UUID actuales |
| Precio | Objeto price con amountCents/originalAmountCents/discountPercent | effective_price_cents y price_cents; descuento de API puede tener decimales, parser Web actual exige entero. Adaptar presentación sin recalcular importes autoritativos |
| Promoción | ID UUID y nombre | active_promotion con ID entero, nombre y porcentaje; no enviar este precio como autoridad a pedidos |
| Imagen | `catalog-image.ts`: bucket/path y URL Supabase validada | URL relativa `/api/v1/images/:id`; resolver únicamente contra origen API aprobado, sin tokens en URL ni relajar la validación de Storage existente |
| Cuidados | `useCareCatalog.ts` recorre todas las páginas del mismo servicio | Actualizar cursor y detección de repetición junto con usePublicCatalog; no dejarlo con Supabase tras cambiar ese servicio |
| Carrito | `features/public-orders/PublicCartProvider.tsx`: localStorage `viveroweb_public_cart_v1`, sin origen en IDs | Definir versión nueva y tratamiento visible del carrito anterior; nunca mezclar enteros y UUID ni borrar pedidos confirmados |
| Pedido | `web-order-service.ts` envía product_id a submit_web_order | API 009 recibe pedidos con cotización/idempotencia y 010 agrega administración auditada; falta entrega a caja. Supabase valida UUID y recalcula su propio precio. Sigue bloqueado el corte |
| Administración | `features/admin/admin-catalog-service.ts`, auth y acceso Supabase | Requiere sesiones/capacidades API en un bloque coordinado; el JWT Supabase no es una sesión API |

El campo común `id: string` de algunos tipos Web no demuestra compatibilidad:
los parsers, RPC y almacenamiento persistido fijan su significado operativo.
Las correspondencias 006–008 sirven para importar y conciliar; no son un puente
de pedidos ni garantizan sincronía después de editar cualquiera de los motores.

## Hallazgos y secuencia

**Bloqueantes:** pedidos con IDs/precios de otro motor; sesión administrativa
incompatible; contrato Web estricto distinto; navegador rechazado por la API.
No habilitar un catálogo comprable con MariaDB mientras el pedido siga recibiendo
productos y precios de Supabase. No añadir traducción de IDs en el navegador,
doble escritura ni fallback silencioso de motor si la API falla.

**Importantes:** conciliar exportaciones reales autorizadas, fijar tratamiento del
carrito persistido, probar categorías paginadas y decidir el orden de listado.
**Deuda técnica:** índices de búsqueda especializados y optimización por carga;
no son necesarios para este incremento y no se añadieron.

Secuencia propuesta para los próximos bloques:

1. Recepción implementada en [009_web_orders](backend-web-orders.md): sucursales,
   cotización autoritativa, creación idempotente y recuperación del resultado.
   Administración auditada implementada en [010](backend-web-order-admin.md).
   Revisar inventario/reservas y completar entrega a caja.
2. Completar el recorrido pedido → caja en el motor que será autoridad de ese
   recorrido. Una tabla sales existente no demuestra que cobrar esté migrado.
   Mantener el recorrido operativo Supabase hasta que el nuevo esté completo.
3. Conciliar catálogo/precios/imágenes de un origen autorizado, congelar ediciones
   durante el corte y respaldar SQL+archivos juntos. No es necesario hacerlo
   sobre datos reales para desarrollar los bloques anteriores.
4. Cambiar catálogo, carrito y pedidos Web juntos, en sus servicios actuales;
   adaptar parsers y hooks, sin rutas nuevas ni selección permanente de motor.
   Migrar la administración con su identidad antes de permitir escrituras nuevas.
5. Verificar el recorrido y retirar el uso Supabase solo en los módulos migrados.

## Origen: decisión preparada, configuración aún no aplicada

Objetivo recomendado: Web y API bajo el mismo origen público, con reverse proxy
para `/api/v1` y la ruta de health si se decide exponerla. En desarrollo, proxy Vite
local hacia el puerto loopback de Docker. No introducir una URL administrativa,
credenciales bootstrap ni contraseñas en variables `VITE_*`.

La API actual rechaza cualquier cabecera Origin, incluso una de mismo origen.
Configurar únicamente un proxy no resuelve eso. El bloque de conexión deberá
validar una lista explícita de orígenes exactos de Web y probar solicitudes con
Origin, imágenes y preflight cuando corresponda; no usar `*` ni eliminar Origin
en el proxy para eludir la validación. No confiar en Host/X-Forwarded-* sin definir
qué proxy es confiable. CORS tampoco sustituye la autorización de escrituras.

## Reversión del corte futuro

Antes de activar, conservar el artefacto Web anterior, su configuración de origen,
un respaldo conjunto de MariaDB/archivos y un inventario de operaciones pendientes.
Registrar el último pedido confirmado por cada autoridad durante la ventana.

Si falla la lectura antes de admitir escrituras nuevas, restaurar el artefacto y
configuración anteriores y verificar el recorrido Supabase. Mantener los volúmenes
MariaDB; no ejecutar down -v ni reconstruir datos con seed.

Si ya existen pedidos/ventas nuevos en MariaDB, detener nuevas operaciones del
módulo y conciliar los resultados antes de volver atrás: un rollback del frontend
no transfiere esos pedidos a Supabase. Recuperar reintentos por su clave idempotente;
no reenviar a otro motor ni copiar cobros automáticamente. Conservar carritos de
ambas versiones separados hasta decidir cómo recuperar productos disponibles.

## Criterios de aceptación antes de activar

- Navegación real: búsqueda, filtro, carga siguiente, vacío, error y reintento,
  cancelación de respuestas obsoletas y catálogo completo de cuidados.
- Imágenes del origen permitido; producto/categoría inactivos no visibles.
- Precio mostrado y precio confirmado consistentes con una sola autoridad;
  promociones evaluadas en servidor y cambios de precio comunicados al comprador.
- Carrito anterior tratado explícitamente; ningún UUID llega como ID MariaDB ni
  ningún ID entero se envía al RPC Supabase.
- Crear/repetir/recuperar un pedido no duplica operaciones; entrega a caja y cobro
  probados de extremo a extremo antes de habilitar ese recorrido.
- Sesión y capacidades correctas; fallo de API no concede permisos ni cambia motor.
- Ensayo de reversión sin perder pedidos confirmados ni borrar volúmenes.

## Probar el incremento de este bloque

Desde ViveroApp con entorno local sintético preparado:

```powershell
npm --prefix backend run check
npm --prefix backend test
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
Invoke-RestMethod 'http://127.0.0.1:3001/api/v1/products?search=planta&category_id=1&limit=24'
```

El puerto del ejemplo presupone API_PORT=3001; ajustar al puerto local configurado.
Esto prueba HTTP desde terminal, no acredita que el navegador esté habilitado.

## Evidencia de esta entrega

- `npm run check` y `npm test` en backend: correctos, 24/24 pruebas unitarias.
- Perfil Docker test: 9/9 pruebas de integración en la base sintética local
  existente con migraciones 002–008. El caso de catálogo comprueba búsqueda por
  ambos nombres, categoría, paginación filtrada, caracteres literales, valores
  inválidos, claves repetidas, visibilidad y permisos administrativos.
- No cambió el esquema: no se repitieron inicialización desde cero, verificación
  de migraciones ni pgTAP. No se ejecutaron builds Android/Web ni lint Web porque
  sus fuentes y configuración no cambiaron. Web solo recibió una referencia
  documental a este plan. No hay evidencia de corte, navegador ni datos reales.

Archivos de este bloque en ViveroApp: `backend/src/app.js`,
`backend/src/catalog.js`, `backend/src/catalog-pricing.js`,
`backend/test/unit.test.js`, `backend/test/integration.test.js`,
`docs/web-catalog-cutover.md`, `docs/backend-api-mariadb.md` y
`docs/supabase-migration-map.md`. En ViveroWeb:
`docs/backend-catalog-cutover.md`.

Se conservaron los cambios preexistentes de ambos repositorios, incluidos IDE,
AGENTS, README, auditorías y módulos previos. No hubo staging, commit, push,
despliegue ni operación remota. No se creó rama, worktree ni backend adicional.
