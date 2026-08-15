# Base de datos

Las migraciones actuales son:

- `202608080001_auth_roles.sql`: perfiles, roles, asignaciones y sucursales.
- `202608080002_catalog.sql`: categorías, productos e imágenes de productos.
- `202608080003_sales_cart.sql`: ventas, partidas, historial de estados y envío idempotente a caja.

Todas incluyen restricciones, índices, marcas de tiempo y RLS.

## Modelo previsto

Entidades principales: perfiles y roles; sucursales; categorías, productos e imágenes; ubicaciones, movimientos y saldos de inventario; clientes y membresías; ventas, partidas, pagos e historial de estados; sesiones de caja; promociones; ledger de puntos; auditoría.

Reglas obligatorias para el diseño:

- UUID para identificadores internos y folios comerciales separados.
- Claves foráneas, restricciones positivas para precios/cantidades e índices por código, folio y fechas.
- `created_at` y `updated_at` con zona horaria.
- Row Level Security activada y políticas por rol.
- Confirmación de pago mediante función PostgreSQL o Edge Function transaccional e idempotente.
- Existencias y puntos derivados de movimientos auditables, no de cambios aislados.

Las tablas restantes se añadirán por módulo para que cada migración pueda revisarse y probarse de forma aislada.
