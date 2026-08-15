# Preparación de Supabase

## Configuración

1. Crea o selecciona un proyecto Supabase de staging.
2. Vincula el repositorio con Supabase CLI y revisa primero `supabase db push --dry-run`.
3. Aplica las migraciones con `supabase db push` únicamente después de revisar el resultado.
4. Agrega a `local.properties`, sin comillas, solo la URL pública y la clave publicable/`anon`:

```properties
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_ANON_KEY=tu_clave_publicable_o_anon
```

5. Crea el primer usuario desde **Authentication > Users** y confirma que el trigger haya creado su fila en `public.profiles`.
6. Desde SQL Editor, invoca una sola vez el procedimiento controlado usando el UUID de ese usuario:

```sql
select public.bootstrap_first_owner('UUID_DEL_USUARIO'::uuid);
```

`bootstrap_first_owner` solo está concedida a `service_role`, rechaza una segunda ejecución y no contiene correos, contraseñas ni UUID personales en la migración. El SQL Editor puede invocarla con el contexto administrativo del proyecto.

La aplicación usa automáticamente el modo demo mientras las dos propiedades locales estén vacías. Nunca copies la clave `service_role`, la contraseña de Postgres ni tokens personales en Android, `local.properties` o archivos versionados.
