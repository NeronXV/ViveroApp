# Identidad y permisos de API — fase 2

La API oficial incorpora identidad propia en MariaDB. Se retira el token
compartido de catálogo; no queda una ruta alternativa para autorizar escrituras.
Web y Android **conservan su login de Supabase** hasta migrar sus consumidores.
No se importa ninguna cuenta, contraseña, UUID ni sesión del entorno anterior.

## Contrato

| Método/ruta | Solicitud | Respuesta |
|---|---|---|
| `POST /api/v1/auth/login` | JSON `{email,password}` | `{token_type:"Bearer",access_token,expires_in:3600}` |
| `GET /api/v1/auth/me` | `Authorization: Bearer <access_token>` | Contexto vigente de usuario, rol, sucursal y capacidades |
| `POST /api/v1/auth/logout` | Mismo encabezado | `{signed_out:true}`; revoca esa sesión, repetible |

Login inválido, cuenta inexistente o inactiva: 401 `INVALID_CREDENTIALS`.
Token ausente, falsificado, vencido o revocado: 401 `UNAUTHORIZED`.
Permiso insuficiente: 403 `FORBIDDEN`. Límite de login: 429
`LOGIN_RATE_LIMITED`; esperar antes de reintentar. Inputs desconocidos, incluidos
`role_id`, `branch_id` y capacidades en el login, se rechazan con 400.
No existe registro público ni endpoint de asignación de roles.

Ejemplo de contexto (datos sintéticos):

```json
{
  "schema_version": 1,
  "user": {"id": 2, "email": "staff@example.invalid", "full_name": "Personal demo"},
  "access_state": "ACTIVE",
  "role": {"id": 3, "name": "INVENTORY", "display_name": "Inventario"},
  "branch": {"id": 1, "code": "DEMO", "name": "Sucursal demostrativa", "is_active": true},
  "capabilities": ["MANAGE_INVENTORY", "MANAGE_PRODUCTS", "SCAN_PRODUCTS", "VIEW_CATALOG", "VIEW_INVENTORY_ALERTS"]
}
```

`NO_ROLE` mantiene la sesión pero entrega `role:null` y capacidades vacías;
ninguna mutación queda autorizada. La sucursal puede ser null o inactiva y el
contexto lo declara. El catálogo es global, igual que el contrato Supabase;
no exige sucursal para administrar. `requireCapabilities` permite además
exigir coincidencia exacta con una sucursal activa en futuros endpoints locales
a una sucursal, sin bypass por OWNER/ADMIN. **Todavía no hay endpoints operativos
por sucursal migrados**; el helper tiene pruebas unitarias, no equivale a migrar caja.

## Reglas de catálogo en servidor

- Crear categoría, desactivar producto y listar inactivos: MANAGE_PRODUCTS.
- Crear producto: MANAGE_PRODUCTS y MANAGE_PRICES.
- Actualizar: MANAGE_PRODUCTS; si cambia el importe, también MANAGE_PRICES.
  Guardar el mismo precio no exige MANAGE_PRICES, como en `upsert_product` previo.
- Cada operación autenticada consulta usuario, rol, sucursal y permisos actuales.
  La autorización y la mutación usan la misma transacción/conexión y lecturas
  con bloqueo; no se confía en claims de rol proporcionados por el cliente.

La relación `role_permissions` es necesaria: cada rol tiene varias capacidades
y cada capacidad pertenece a varios roles. Replica la asignación de los seis
roles y 18 capacidades de `202608080001_auth_roles.sql`. El usuario sigue teniendo
un solo rol; no se agrega `user_roles`. Los permisos para módulos pendientes no
crean sus endpoints ni sustituyen sus reglas transaccionales.

## Contraseñas y sesiones

- scrypt asíncrono, N=131072, r=8, p=1, sal aleatoria de 16 bytes y salida de
  64 bytes. El formato incluye parámetros; no hay contraseñas predeterminadas.
- Para crear credenciales se exigen 15–128 caracteres. No se recorta ni normaliza
  la contraseña. El email se normaliza a minúsculas y admite direcciones ASCII.
- Token opaco aleatorio de 32 bytes. Solo su SHA-256 se almacena en
  `auth_sessions`, con vencimiento absoluto de una hora. No hay refresh token;
  al vencer se necesita otro login. Una contraseña válida puede abrir más de
  una sesión; logout revoca únicamente la presentada.
- Un trigger revoca todas las sesiones si cambia la contraseña o el estado activo
  del usuario. Reactivar la cuenta no revive los tokens anteriores. Cambiar rol,
  permisos o sucursal se refleja en la siguiente operación sin volver a iniciar sesión.
- Un hash ausente o malformado también ejecuta scrypt para reducir diferencias
  de tiempo. Solo se permiten dos verificaciones simultáneas por instancia.
- Intentos de login persistidos: 10 por email y 60 por dirección de conexión en
  ventanas de 15 minutos; cuentan también los éxitos. No se confía en
  X-Forwarded-For. Los contadores conservan hashes de claves, no contraseñas ni
  emails en texto. Varios clientes detrás del mismo proxy comparten límite de IP.
- No se imprimen contraseñas, tokens, payloads ni errores SQL. No hay cookies ni
  CORS habilitado aún. Nunca persistir un token en código, `.env` público o APK.

Los registros vencidos de sesión se limpian al iniciar sesión ese usuario; los
contadores se reinician al volver a usarse. Una política periódica de purga y
configuración del proxy queda pendiente para VPS; no se programan tareas aquí.
Se mantiene el puerto de desarrollo en loopback y la base sin puerto público.

Referencias: [scrypt en Node](https://nodejs.org/docs/latest-v24.x/api/crypto.html#cryptoscryptpassword-salt-keylen-options-callback),
[parámetros de almacenamiento OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html),
[sesiones OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

## Actualizar sin borrar datos

La migración `database/mysql/migrations/002_identity.sql` agrega cinco tablas,
el trigger, las capacidades y los grants de identidad. El baseline, seed y grants
de fase 1 se preservan. Todas las tablas nuevas tienen PK INT AUTO_INCREMENT;
relaciones con FK e índices solo de PK/FK/unicidad. El usuario SQL de ejecución
puede leer identidad y administrar sesiones/contadores, pero no puede modificar
usuarios, roles ni capacidades, y sigue sin acceso a ventas/pagos.

Para volumen nuevo y para actualizar fase 1, desde ViveroApp con `.env` configurado:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up --build -d --wait api
```

La imagen MariaDB aplica la migración al inicializar un volumen nuevo. El comando
`migrate` utiliza un bloqueo asesor y omite versiones ya registradas en
`schema_migrations`; **no reescribe permisos modificados en una versión ya aplicada**.
Solo ejecuta SQL versionado desde un montaje de lectura. El health check exige
que `002_identity` esté registrada. Si falta, la API no se declara saludable.

DDL no es transaccional en MariaDB. Si una migración falla parcialmente, se
detiene sin registrar la versión ni borrar la base: revisar el estado antes de
reintentar. No modificar migraciones aplicadas, no usar `down -v` y no volver a
correr `grants.sql` de fase 1 (revocaría los grants nuevos). Para un entorno con
datos propios hacer respaldo antes de migrar. Los comandos `migrate` y `bootstrap`
solo admiten el destino Compose local `db/vivero` y modo development.

## Crear al primer propietario

El seed sigue sin credenciales activas. Crear un `.env.bootstrap` local ignorado
con estas variables, sin copiar una contraseña real a documentación ni terminal:

```dotenv
BOOTSTRAP_EMAIL=owner@example.invalid
BOOTSTRAP_FULL_NAME=Propietario local
BOOTSTRAP_BRANCH_CODE=DEMO
BOOTSTRAP_PASSWORD=replace-with-a-unique-password-at-least-15-characters
```

Reemplazar la contraseña antes de ejecutar. El marcador es rechazado.

```powershell
docker compose --env-file .env --env-file .env.bootstrap -f infra/docker/compose.yaml --profile tools run --build --rm bootstrap
```

El bootstrap crea exactamente un OWNER asociado a una sucursal activa existente.
Usa transacción y bloqueo asesor para evitar dos propietarios iniciales en
carrera. Si existe cualquier usuario activo o con contraseña, se niega a correr;
no promueve cuentas, no sobrescribe el demo ni restablece contraseñas.
Es una herramienta administrativa explícita, nunca una ruta HTTP ni un paso
automático de arranque. Las credenciales de bootstrap solo se pasan a ese
contenedor temporal; no a la API. Tras aprovisionar, conservar la contraseña en
un gestor y retirar el archivo temporal. No confundir este paso con importar
usuarios de Supabase. Gestión posterior de personal y recuperación quedan pendientes.

## Probar sin exponer credenciales

Con PowerShell 7, solicitar credenciales en un diálogo de consola; los secretos
no se escriben como literales en el historial. No imprimir `$sesion`.

```powershell
$credencial = Get-Credential -Message 'Usuario local de la API'
$cuerpo = @{ email = $credencial.UserName; password = $credencial.GetNetworkCredential().Password } | ConvertTo-Json
$sesion = Invoke-RestMethod -Method Post -Uri http://127.0.0.1:3001/api/v1/auth/login -ContentType 'application/json' -Body $cuerpo
$cabeceras = @{ Authorization = "Bearer $($sesion.access_token)" }
Invoke-RestMethod -Uri http://127.0.0.1:3001/api/v1/auth/me -Headers $cabeceras
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:3001/api/v1/auth/logout -Headers $cabeceras
Remove-Variable credencial,cuerpo,sesion,cabeceras
```

Suite local de lógica: `npm test` y `npm run check` desde `backend`.
Integración HTTP/SQL sintética:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Se crean usuarios/productos sintéticos temporales y se eliminan al finalizar;
se conservan los contadores de IP hasta que venza su ventana. Evitar repetir la
suite numerosas veces en 15 minutos para no alcanzar el límite compartido.
El perfil test no se debe ejecutar contra datos operativos.

## Estado y siguiente paso

Implementado, con pruebas unitarias previas y ejecución real de las pruebas
HTTP/SQL contra MariaDB. Bootstrap, trigger, migración, permisos y persistencia
se comprobaron con datos sintéticos; ver [validación local](backend-local-validation.md).
No se presenta como una migración operativa de consumidores terminada.
Se mantienen intactos Auth/RLS/RPC de Supabase.

Siguiente paso: completar catálogo operativo y el plan de correspondencia de
IDs/pedidos antes de conectar Web. La instalación nueva, actualización desde fase 1
y flujo login → catálogo autorizado → logout ya fueron comprobados localmente.
Para sustituir el login de clientes también faltan recuperación/invitación,
gestión de personal, almacenamiento seguro de sesiones, TLS y orígenes definidos.

## Actualización: cuentas de personal (2026-10-01)

La API permite alta administrativa de personal y restablecimiento de contraseñas con auditoría y revocación transaccional. Migración SQL 021. Consultar [backend-staff-accounts.md](backend-staff-accounts.md) para contratos, permisos y evidencia. Web y Android conservan sus sesiones Supabase hasta la integración coordinada de sus consumidores.
