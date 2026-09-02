# Contrato Web para administración de roles

Este contrato permite construir una pantalla Web que asigne uno de los seis roles aprobados a un perfil existente. No crea cuentas en `auth.users` ni permite crear tipos de rol arbitrarios.

## Flujo recomendado

1. Cargar personal con `get_admin_staff`.
2. Cargar las opciones permitidas para el actor con `get_admin_role_options`.
3. Mostrar el rol actual de cada perfil y usar únicamente las opciones devueltas por el backend.
4. Confirmar el cambio con `set_admin_staff_role`.
5. Sustituir el rol de la fila con la respuesta de la mutación o recargar `get_admin_staff`.

La Web no debe inferir que `ADMIN` puede asignar `OWNER`. El backend omite esa opción para `ADMIN` y vuelve a comprobar la jerarquía durante la mutación.

## Listado de personal

```ts
const { data, error } = await supabase.rpc("get_admin_staff", {
  p_limit: 50,
  p_after_full_name: null,
  p_after_id: null,
  p_search: null,
  p_branch_id: null,
  p_include_inactive: true,
});
```

La respuesta usa `schemaVersion: 1`. Cada elemento contiene `id`, `fullName`, `isActive`, `branch`, `role` y `updatedAt`. El contrato omite correo y datos privados de autenticación deliberadamente.

## Opciones asignables

```ts
const { data, error } = await supabase.rpc("get_admin_role_options");
```

Respuesta de un `ADMIN`:

```json
{
  "schemaVersion": 1,
  "actorRole": "ADMIN",
  "items": [
    {
      "name": "SALES",
      "displayName": "Ventas",
      "capabilities": ["CREATE_SALES", "SCAN_PRODUCTS", "VIEW_CATALOG", "VIEW_OWN_SALES"]
    }
  ],
  "serverTime": "2026-09-01T00:00:00Z"
}
```

`ADMIN` recibe cinco opciones y no recibe `OWNER`. `OWNER` recibe las seis. El orden es `SALES`, `CASHIER`, `INVENTORY`, `MANAGER`, `ADMIN`, `OWNER`.

## Asignación

```ts
const { data, error } = await supabase.rpc("set_admin_staff_role", {
  p_user_id: staffId,
  p_role_name: selectedRole,
});
```

Respuesta:

```json
{
  "schemaVersion": 1,
  "userId": "00000000-0000-0000-0000-000000000000",
  "role": {
    "name": "MANAGER",
    "displayName": "Gerente"
  },
  "updatedAt": "2026-09-01T00:00:00Z",
  "serverTime": "2026-09-01T00:00:00Z"
}
```

Solo se aceptan los nombres `SALES`, `CASHIER`, `INVENTORY`, `MANAGER`, `ADMIN` y `OWNER`. El backend normaliza espacios exteriores y mayúsculas, pero la Web debe enviar el valor `name` recibido en las opciones.

## Errores estables

| Mensaje | Tratamiento sugerido |
|---|---|
| `ROLE_ASSIGNMENT_UNAUTHORIZED` | Ocultar la acción, invalidar la caché de acceso y mostrar que la sesión no tiene permiso. |
| `ROLE_ASSIGNMENT_INVALID` | No enviar valores fuera de las opciones recibidas; mostrar selección inválida. |
| `ROLE_TARGET_UNAVAILABLE` | Recargar el directorio; el perfil no existe o está inactivo. |
| `ROLE_OWNER_RESTRICTED` | Informar que un administrador no puede promover ni modificar propietarios. |
| `ROLE_LAST_OWNER_REQUIRED` | Informar que debe conservarse al menos un propietario. |

Los errores se entregan como errores RPC con SQLSTATE `P0001`. La Web debe comparar el mensaje estable y no depender del texto traducido que presente al usuario.

## Seguridad y actualización de sesiones

- Ambos RPC nuevos solo conceden ejecución a `authenticated`.
- Las funciones validan perfil activo y capacidad `ASSIGN_ROLES`.
- `set_admin_staff_role` reutiliza `assign_user_role`; no duplica ni relaja su jerarquía.
- No existe escritura directa de cliente sobre `user_roles`.
- Un usuario cuyo rol cambió debe renovar su contexto de acceso. Hasta que la Web o Android implementen refresco automático, debe volver a iniciar sesión para ver el panel correspondiente al nuevo rol.
