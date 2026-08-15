# Roles y permisos

| Rol | Alcance inicial |
|---|---|
| WORKER | Catálogo, escáner, carrito y tickets propios |
| CASHIER | Tickets pendientes, cobros y turno de caja |
| INVENTORY | Existencias, escáner y movimientos autorizados |
| MANAGER | Ventas, inventario y promociones |
| ADMIN | Administración completa y usuarios |
| OWNER | Consulta completa y reportes generales |

El modo demo usa un perfil WORKER. Con Supabase, Auth identifica al usuario y después se consultan `profiles` y `user_roles`. El dashboard se construye exclusivamente con los módulos del rol recuperado.

Ocultar una opción no constituye seguridad. La migración `202608080001_auth_roles.sql` activa RLS y separa políticas de lectura, actualización y administración. La app solo recibe URL pública y clave publicable/`anon key`, nunca `service_role` ni credenciales administrativas.
