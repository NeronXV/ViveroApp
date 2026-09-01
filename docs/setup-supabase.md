# Preparación de Supabase

## Configuración

1. Crea o selecciona un proyecto Supabase de staging.
2. Vincula el repositorio con Supabase CLI y revisa primero `supabase db push --dry-run`.
3. Aplica las migraciones con `supabase db push` únicamente después de revisar el resultado.
4. Agrega a `local.properties`, sin comillas, solo la URL pública y la clave publicable/`anon`:

```properties
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_PUBLISHABLE_KEY=tu_clave_publicable
```

5. Crea el primer usuario desde **Authentication > Users** y confirma que el trigger haya creado su fila activa en `public.profiles`, inicialmente con `branch_id = null`.
6. Desde SQL Editor, invoca una sola vez el procedimiento controlado usando el UUID de ese usuario:

```sql
select public.bootstrap_first_owner('UUID_DEL_USUARIO'::uuid);
```

`bootstrap_first_owner` solo está concedida a `service_role`, rechaza una segunda ejecución y no contiene correos, contraseñas ni UUID personales en la migración. El SQL Editor puede invocarla con el contexto administrativo del proyecto.

7. Inicia sesión en un cliente autenticado como ese `OWNER` y crea la primera sucursal mediante RPC:

```sql
select public.create_branch('CENTRO', 'Sucursal Centro');
```

8. Conserva el UUID devuelto y asigna al primer `OWNER` a esa sucursal. Un `OWNER` global puede existir temporalmente con `branch_id = null`, pero necesita una sucursal activa para enviar ventas:

```sql
select public.assign_user_branch(
    auth.uid(),
    'UUID_DE_LA_SUCURSAL'::uuid
);
```

9. Crea el usuario `SALES` desde Authentication. Después de que el trigger cree su perfil, el `OWNER` asigna primero el rol y después la misma sucursal activa:

```sql
select public.assign_user_role('UUID_DEL_USUARIO_SALES'::uuid, 'SALES');
select public.assign_user_branch(
    'UUID_DEL_USUARIO_SALES'::uuid,
    'UUID_DE_LA_SUCURSAL'::uuid
);
```

10. Crea por RPC o interfaz autorizada al menos una categoría y un producto activo con UUID real. El usuario `SALES` podrá entonces enviar la primera comanda mediante `submit_sale_to_cashier`. Para comprobar la bandeja de Caja se requiere además un usuario `CASHIER` asignado a la misma sucursal.

No ejecutes el bootstrap desde Android ni distribuyas una clave administrativa. Los RPC de sucursales exigen perfil activo y capacidades; `ADMIN` no puede cambiar la sucursal de un `OWNER`.

La aplicación requiere ambas propiedades locales para habilitar el inicio de sesión. Si están vacías, la pantalla indica que falta configuración y no permite autenticarse. Nunca copies la clave `service_role`, la contraseña de Postgres ni tokens personales en Android, `local.properties` o archivos versionados.
