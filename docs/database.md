# Base de datos

Las migraciones actuales son:

- `202608080001_auth_roles.sql`: perfiles, roles, asignaciones y sucursales.
- `202608080002_catalog.sql`: categorías, productos e imágenes de productos.
- `202608080003_sales_cart.sql`: ventas, partidas, historial de estados y envío idempotente a caja.
- `202608140001_branch_management.sql`: RPC controlados para crear, actualizar, activar y desactivar sucursales, y asignar personal a una sucursal activa.
- `202608150001_harden_table_privileges.sql`: elimina privilegios generales heredados por clientes y reconstruye el mínimo requerido para RLS, catálogo y campos personales.
- `202608150002_harden_function_privileges.sql`: revoca la ejecución predeterminada de funciones y concede únicamente los RPC y auxiliares requeridos.
- `202608220001_cashier_payments.sql`: claims auditables y confirmación de pagos atómica e idempotente.
- `202608220002_get_my_access_context.sql`: contexto autenticado de perfil, rol, sucursal y capacidades.
- `202608240001_get_public_catalog.sql`: proyección pública paginada del catálogo.
- `202608270001_public_catalog_images.sql`: contrato seguro del bucket e imágenes públicas del catálogo.
- `202608280001_cashier_web_contract.sql`: bandeja, detalle y recuperación de pagos para Caja Web.
- `202608290001_admin_web_contract.sql`: proyecciones administrativas V1 de sucursales y personal.

El modelo resultante conserva restricciones, índices, marcas de tiempo y RLS. Las migraciones de presentación de Caja y Administración añaden únicamente funciones de lectura y privilegios mínimos de ejecución; no cambian tablas ni políticas.

Las mutaciones de `branches` y `profiles.branch_id` no se conceden directamente a `authenticated`. Se realizan mediante `create_branch`, `update_branch`, `set_branch_active` y `assign_user_branch`. Reactivar conserva el identificador y los datos de la sucursal; repetir el mismo estado o la misma asignación no genera cambios adicionales. Una sucursal no puede eliminarse físicamente mediante estos RPC.

Supabase configura privilegios predeterminados amplios para tablas nuevas de `public`. Cada migración que cree tablas deberá revocar esos privilegios de `anon`, `authenticated` y `PUBLIC`, y conceder después solo las operaciones que sus políticas RLS necesiten. Los privilegios y propietarios de `service_role`, `postgres` y demás roles internos no se modifican.

Toda función nueva debe crearse y endurecerse en la misma transacción: revocar explícitamente `EXECUTE` de `PUBLIC`, `anon` y cualquier rol cliente no requerido, y concederlo únicamente a la lista mínima necesaria. Las funciones de trigger no se exponen directamente a clientes; una función usada por RLS solo se concede a los roles que evalúan esa política.

El cliente de Administración deberá consumir `get_admin_branches` y `get_admin_staff` para obtener JSON V1 paginado sin depender del esquema físico ni exponer correo o metadatos de `auth.users`. Las mutaciones continúan usando exclusivamente los RPC administrativos existentes.

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
