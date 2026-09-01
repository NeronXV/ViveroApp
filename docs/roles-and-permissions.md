# Roles, capacidades y alcance

Supabase es la autoridad de seguridad. Android y el futuro dashboard web pueden ocultar acciones por conveniencia, pero RLS y las funciones de PostgreSQL son quienes autorizan cada operación.

## Roles

| Rol | Responsabilidad |
|---|---|
| `SALES` | Catálogo, escáner, creación de comandas y consulta de ventas propias |
| `CASHIER` | Bandeja pendiente y operaciones de caja de su sucursal |
| `INVENTORY` | Catálogo, productos, recepción, ajustes y alertas de inventario; no precios |
| `MANAGER` | Operación de ventas, caja, catálogo, precios, descuentos, inventario y reportes de su sucursal |
| `ADMIN` | Administración operativa delegada; no puede promover a `OWNER` ni modificar a un `OWNER` |
| `OWNER` | Máxima autoridad de negocio, sin acceso directo a secretos ni operaciones técnicas de la base |

Cada usuario tiene un perfil y exactamente un rol activo.

## Capacidades

Las capacidades viven en `permissions` y su asignación en `role_permissions`, por lo que Android y web comparten los mismos nombres y reglas.

| Capacidad | SALES | CASHIER | INVENTORY | MANAGER | ADMIN | OWNER |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `VIEW_CATALOG` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `SCAN_PRODUCTS` | ✓ |  | ✓ | ✓ | ✓ | ✓ |
| `CREATE_SALES` | ✓ |  |  | ✓ | ✓ | ✓ |
| `VIEW_OWN_SALES` | ✓ |  |  | ✓ | ✓ | ✓ |
| `OPERATE_CASHIER` |  | ✓ |  | ✓ | ✓ | ✓ |
| `VIEW_BRANCH_SALES` |  |  |  | ✓ | ✓ | ✓ |
| `VIEW_ALL_SALES` |  |  |  |  | ✓ | ✓ |
| `MANAGE_PRODUCTS` |  |  | ✓ | ✓ | ✓ | ✓ |
| `MANAGE_PRICES` |  |  |  | ✓ | ✓ | ✓ |
| `MANAGE_DISCOUNTS` |  |  |  | ✓ | ✓ | ✓ |
| `MANAGE_INVENTORY` |  |  | ✓ | ✓ | ✓ | ✓ |
| `VIEW_INVENTORY_ALERTS` |  |  | ✓ | ✓ | ✓ | ✓ |
| `VIEW_REPORTS` |  |  |  | ✓ | ✓ | ✓ |
| `MANAGE_BRANCHES` |  |  |  |  | ✓ | ✓ |
| `MANAGE_USERS` |  |  |  |  | ✓ | ✓ |
| `ASSIGN_ROLES` |  |  |  |  | ✓* | ✓ |
| `VIEW_AUDIT` |  |  |  |  | ✓ | ✓ |
| `MANAGE_SETTINGS` |  |  |  |  | ✓ | ✓ |

`ADMIN` posee la capacidad técnica de invocar la asignación, pero `assign_user_role` rechaza promover a `OWNER` y modificar a cualquier `OWNER`. También se impide reasignar al último `OWNER`.

## Perfil personal

Una persona autenticada solo puede actualizar directamente:

- `full_name`
- `avatar_path`

Son campos protegidos y no tienen privilegio de actualización directa:

- `branch_id`
- `is_active`
- `id`
- fechas de creación y actualización
- la fila asociada en `user_roles`

Los cambios protegidos deberán pasar por funciones administrativas específicas y auditables. No se entregan permisos directos de escritura sobre `user_roles` a `authenticated`.

Los privilegios generales creados por defaults de Supabase se eliminan explícitamente. `authenticated` conserva lectura sobre las tablas de aplicación, escritura RLS sobre las tres tablas de catálogo y `UPDATE` exclusivamente sobre `profiles.full_name` y `profiles.avatar_path`. `anon` y `PUBLIC` no reciben privilegios directos de tabla; el catálogo público se expone exclusivamente mediante `get_public_catalog`.

La ejecución de funciones también usa una lista blanca. `authenticated` recibe los RPC operativos y `has_permission`; `bootstrap_first_owner` queda reservado a `service_role`; las funciones de trigger no son invocables directamente por clientes. `anon` ejecuta únicamente `get_public_catalog` y `PUBLIC` no ejecuta ninguna función de `public`.

La administración de sucursales usa exclusivamente estos RPC:

- `create_branch(text, text)`
- `update_branch(uuid, text, text)`
- `set_branch_active(uuid, boolean)`
- `assign_user_branch(uuid, uuid)`

Los tres primeros requieren `MANAGE_BRANCHES`; el cuarto requiere `MANAGE_USERS`. `ADMIN` no puede cambiar la sucursal de un `OWNER`. Reactivar una sucursal conserva su UUID, código, nombre e historial; desactivarla se rechaza si mantiene personal activo o ventas pendientes.

Los listados administrativos usan contratos de presentación separados:

- `get_admin_branches(integer, text, uuid, boolean)`, autorizado por `MANAGE_BRANCHES` o `MANAGE_USERS`;
- `get_admin_staff(integer, text, uuid, text, uuid, boolean)`, autorizado por `MANAGE_USERS`.

Ambos retornan JSON con `schemaVersion` 1, paginación estable y datos mínimos de presentación. No exponen correo, credenciales ni metadatos de `auth.users`.

## Alcance por sucursal

RLS limita la bandeja de `CASHIER` a `SENT_TO_CASHIER` y `PAYMENT_PENDING` de su sucursal. `MANAGER` consulta ventas de su sucursal mediante una política independiente. `ADMIN` y `OWNER` pueden consultar todas las sucursales. El historial de caja específico se incorporará con su propia consulta o función.

## Cobertura de capacidades

| Capacidad | Política RLS | RPC o trigger | Estado |
|---|---|---|---|
| `VIEW_CATALOG` | `categories_read`, `products_read`, `product_images_read` | `get_public_catalog` | Activa en Android y Web |
| `SCAN_PRODUCTS` | Reutiliza lectura de catálogo | — | Activa en Android |
| `CREATE_SALES` | — | `submit_sale_to_cashier` | Activa |
| `VIEW_OWN_SALES` | `sales_creator_read_own` | — | Activa |
| `OPERATE_CASHIER` | `sales_cashier_read_pending` | RPC de claims, confirmación y presentación de Caja | Activa en Android y Web; validación integral pendiente |
| `VIEW_BRANCH_SALES` | `sales_management_read_branch` | — | Activa |
| `VIEW_ALL_SALES` | `sales_management_read_all` | — | Activa |
| `MANAGE_PRODUCTS` | `categories_manage`, `products_manage`, `product_images_manage` | `upsert_category`, `upsert_product`, `set_product_image_primary` | Contrato local activo; ejecución integral pendiente |
| `MANAGE_PRICES` | `products_manage` | `enforce_product_price_permission`, `upsert_product` | Contrato local activo; ejecución integral pendiente |
| `MANAGE_DISCOUNTS` | lectura de promociones y relaciones de productos | `apply_sale_discount`, `upsert_catalog_promotion` | Campañas de catálogo y descuentos de venta separados por alcance |
| `MANAGE_INVENTORY` | políticas de ubicaciones, movimientos, saldos y conteos sin escritura directa | `get_my_inventory_dashboard`, `record_inventory_reception`, `reconcile_inventory_count`, `get_my_inventory_history` | Piloto Android implementado para gerente/admin/owner; ejecución integral pendiente |
| `VIEW_INVENTORY_ALERTS` | `inventory_balances_read` | `get_low_inventory_alerts`, `get_my_inventory_dashboard`, `get_my_inventory_history` | Contrato local endurecido por sucursal; ejecución integral pendiente |
| `VIEW_REPORTS` | políticas operativas por sucursal | `get_report_daily_sales`, `get_report_top_products` | Integración Web parcial; ejecución integral pendiente |
| `MANAGE_BRANCHES` | `branches_read_authenticated` para lectura | mutaciones de sucursal y `get_admin_branches` | Integración Web parcial; ejecución integral pendiente |
| `MANAGE_USERS` | políticas de perfiles, roles y clientes | `set_user_active`, `assign_user_branch`, `get_admin_branches`, `get_admin_staff` | Directorio y mutaciones integrados en Android y Web; validación remota por RPC |
| `ASSIGN_ROLES` | — | `assign_user_role`, `bootstrap_first_owner` controlado | Activa |
| `VIEW_AUDIT` | — | — | Fase futura |
| `MANAGE_SETTINGS` | — | — | Fase futura |
