# Preparación de Supabase

## Configuración

1. Crear o seleccionar un proyecto Supabase.
2. Abre **SQL Editor** y ejecuta en orden `supabase/migrations/202608080001_auth_roles.sql` y `202608080002_catalog.sql`, o usa `supabase db push` si trabajas con Supabase CLI.
3. Activar y verificar RLS antes de cargar datos reales.
4. Agrega a `local.properties` sin comillas:

```properties
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_ANON_KEY=tu_clave_publicable_o_anon
```
5. Exponer solo esos valores mediante `BuildConfig`.
6. Crea un usuario desde **Authentication > Users**. El trigger creará su perfil.
7. Asigna su primer rol desde SQL Editor usando el UUID del usuario. Para el primer administrador esta operación debe realizarse desde el panel seguro, porque todavía no existe un administrador autenticado:

```sql
insert into public.user_roles (user_id, role_id)
select 'UUID_DEL_USUARIO', id from public.roles where name = 'ADMIN';
```

La aplicación usa automáticamente el modo demo mientras las dos propiedades estén vacías.

Nunca se copiará la `service_role key` a la app. Realtime y Storage respetarán políticas por rol y por bucket.
