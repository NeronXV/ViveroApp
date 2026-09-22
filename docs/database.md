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
- `202608280002_admin_web_contract.sql`: proyecciones administrativas V1 de sucursales y personal.
- `202608290001_admin_user_activation.sql`: activación protegida de usuarios y conservación del último `OWNER` activo.
- `202608290002_inventory_management.sql`: ubicaciones, movimientos y saldos derivados de inventario.
- `202608290003_inventory_projections.sql`: existencias administrativas y alertas de bajo inventario.
- `202608290004_catalog_administration.sql`: mutaciones protegidas de categorías, productos e imágenes.
- `202608290005_sales_inventory_trigger.sql`: salida auditable de inventario al confirmar una venta pagada.
- `202608290006_customer_management.sql`: clientes y búsqueda protegida para su asociación operativa.
- `202608290007_promotions_and_discounts.sql`: promociones y descuentos aplicados a ventas.
- `202608290008_basic_reports.sql`: ventas diarias y productos más vendidos.
- `202608290009_mvp_backend_hardening.sql`: aislamiento por sucursal, privacidad, stock no negativo, promociones autoritativas y reportes por fecha de pago.
- `202608290010_my_branch_catalog_inventory.sql`: proyección autenticada de existencias del catálogo limitada a la sucursal activa del usuario.
- `202608290011_inventory_pilot_contract.sql`: tablero de inventario por sucursal, recepciones idempotentes, conciliación de conteos e historial auditable.
- `202608290012_my_sales_contract.sql`: historial de ventas propias con estados autoritativos y paginación keyset.
- `202608290013_catalog_images_storage.sql`: políticas mínimas de escritura para imágenes del catálogo.
- `202608290014_gradual_inventory_trigger.sql`: activación gradual del descuento automático de inventario al cobrar.
- `202608290015_catalog_promotions.sql`: campañas para todo el catálogo o productos seleccionados, catálogo público V3 y precios autoritativos en Android y ventas.
- `202609010001_admin_role_management.sql`: opciones de rol según el actor y mutación versionada de roles para clientes administrativos.
- `202609010002_web_orders.sql`: pedidos públicos idempotentes con precios autoritativos, datos de contacto protegidos, alcance administrativo por sucursal y transiciones auditadas.
- `202609010003_product_scan_lookup.sql`: consulta de productos por código y existencias de la sucursal.
- `202609010004_supplier_purchases.sql`: revisión de compras a proveedores y recepción idempotente.
- `202609080001_presential_checkout.sql`: enlace del pedido web a Caja y activación de inventario por sucursal.
- `202609080002_cashier_closings_refunds.sql`: cortes, devoluciones totales y protección de entregas.
- `202609080003_newsletter.sql`: confirmación, baja y campañas de boletín.
- `202609210001_presentation_function_privileges.sql`: retira el permiso heredado de `service_role` sobre `get_my_access_context` y `get_my_branch_catalog_inventory`; conserva la ejecución de `authenticated`.

El modelo resultante conserva restricciones, índices, marcas de tiempo y RLS. Las migraciones de presentación de Caja y Administración añaden únicamente funciones de lectura y privilegios mínimos de ejecución. Los módulos posteriores añaden inventario, clientes, promociones y reportes, y la migración de endurecimiento corrige su alcance. Las 35 migraciones se validaron desde cero junto con las 499 aserciones pgTAP; ver `database-validation.md`.

Las mutaciones de `branches` y `profiles.branch_id` no se conceden directamente a `authenticated`. Se realizan mediante `create_branch`, `update_branch`, `set_branch_active` y `assign_user_branch`. Reactivar conserva el identificador y los datos de la sucursal; repetir el mismo estado o la misma asignación no genera cambios adicionales. Una sucursal no puede eliminarse físicamente mediante estos RPC.

Supabase puede configurar privilegios predeterminados amplios para objetos nuevos de `public`. Cada migración deberá revocar los privilegios no requeridos y conceder después solo las operaciones necesarias. Los propietarios y la pertenencia de roles internos se conservan; las funciones de presentación exclusivas de clientes revocan también la ejecución heredada de `service_role`.

Toda función nueva debe crearse y endurecerse en la misma transacción: revocar explícitamente `EXECUTE` de `PUBLIC`, `anon` y cualquier rol cliente no requerido, y concederlo únicamente a la lista mínima necesaria. Las funciones de trigger no se exponen directamente a clientes; una función usada por RLS solo se concede a los roles que evalúan esa política.

El cliente de Administración consume `get_admin_branches`, `get_admin_staff`, `get_admin_role_options`, `set_admin_staff_role` y `get_admin_inventory_balances`. Android consulta `get_my_branch_catalog_inventory` para ventas y usa `get_my_inventory_dashboard`, `record_inventory_reception`, `reconcile_inventory_count` y `get_my_inventory_history` para el piloto operativo, siempre sin poder elegir otra sucursal. Ningún cliente escribe directamente saldos, conteos ni movimientos.

## Modelo actual y previsto

Entidades actuales principales: perfiles y roles; sucursales; categorías, productos e imágenes; ubicaciones, movimientos y saldos de inventario; clientes; ventas, partidas, pagos e historial de estados; pedidos web y su historial; promociones, relaciones `promotion_products` y descuentos. Las promociones conservan alcance `SALE` para descuentos sobre una venta, o usan `ALL_PRODUCTS` y `SELECTED_PRODUCTS` para campañas de catálogo. PostgreSQL selecciona la campaña que produce el menor precio efectivo y guarda la promoción aplicada en cada partida. Membresías, sesiones de caja, ledger de puntos y auditoría detallada permanecen previstas para fases posteriores.

Reglas obligatorias para el diseño:

- UUID para identificadores internos y folios comerciales separados.
- Claves foráneas, restricciones positivas para precios/cantidades e índices por código, folio y fechas.
- `created_at` y `updated_at` con zona horaria.
- Row Level Security activada y políticas por rol.
- Confirmación de pago mediante función PostgreSQL o Edge Function transaccional e idempotente.
- Existencias y puntos derivados de movimientos auditables, no de cambios aislados.

Las tablas restantes se añadirán por módulo para que cada migración pueda revisarse y probarse de forma aislada.
