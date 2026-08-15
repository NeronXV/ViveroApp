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

El modo demo de Android usa `SALES`. Cada usuario real tiene un perfil y exactamente un rol activo.

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

## Alcance por sucursal

RLS limita la bandeja de `CASHIER` a `SENT_TO_CASHIER` y `PAYMENT_PENDING` de su sucursal. `MANAGER` consulta ventas de su sucursal mediante una política independiente. `ADMIN` y `OWNER` pueden consultar todas las sucursales. El historial de caja específico se incorporará con su propia consulta o función.
