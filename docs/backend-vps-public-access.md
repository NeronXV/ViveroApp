# Acceso público y administrador inicial del VPS

Actualización del 3 de octubre: identidad, catálogo, historial e inventario ya
importados y conciliados; OWNER conservado y HTTPS verificado con datos.
[Resultado y respaldos](backend-vps-data-cutover.md). Las referencias siguientes
a catálogo vacío corresponden al acceso inicial del 2 de octubre.

Verificado el 2026-10-02, America/Chihuahua (UTC servidor: 2026-10-03).
Continúa el despliegue autorizado; no constituye importación/corte completo.

## Resultado actual

- Dominio `bajastack.network`: A y AAAA correctos, comprobados en Google DNS y
  servidor autoritativo. Algunos resolvers conservaron NXDOMAIN durante la
  propagación. Las dos direcciones corresponden al VPS.
- Caddy emitió certificado público Let’s Encrypt. `/health` y `/login` responden
  por HTTPS; se verificó TLS con raíces públicas, sin `-k` ni CA de ensayo.
- Compose oficial usa `compose.yaml` + `compose.vps.yaml`, proyecto `vivero-vps`.
  Ya no se aplica `compose.private.yaml`: Web publica 80/443 en IPv4/IPv6.
  API y MariaDB siguen sin puertos publicados. UFW permite solo SSH y Web.
- Creada una cuenta OWNER y una sucursal principal con los datos proporcionados
  por Pedro. Contraseña aleatoria creada únicamente en el VPS, fichero privado
  `/opt/vivero/shared/.env.bootstrap`, modo 600. No se imprimió ni transfirió.
- Verificado login HTTPS, contexto OWNER, sucursal, capacidades, catálogo e
  inventario vacíos y logout que revoca la sesión. Se validó Origin del dominio.
  La conexión de prueba se dirigió al VPS con SNI/Host del dominio para evitar
  cachés DNS negativas; la validación del certificado no se omitió.

La Web está disponible en https://bajastack.network/login. Catálogo vacío: todavía
no se importaron productos ni historial real. No se activó inventario de ventas
sin conteo inicial. No se declara paridad Android/Cliente ni abandono de Supabase.
El subdominio www no se configuró en Caddy: usar el dominio principal indicado.

## Herramienta de acceso inicial

`backend/scripts/bootstrap-owner.js` conserva el modo con sucursal existente.
Opcionalmente acepta `BOOTSTRAP_BRANCH_NAME`, validada con el contrato oficial
`branchInput`; crea sucursal y OWNER en una sola transacción. La clave se cifra
con el scrypt existente. Usa bloqueo exclusivo y rechaza repetir bootstrap,
cuentas existentes, sucursal inactiva o nombre incompatible. No renombra ni
sobrescribe usuarios/sucursales existentes. Un fallo revierte ambas altas.

El servicio Compose bootstrap transmite el nombre opcional. Es herramienta de
mantenimiento dentro de la red Docker autorizada, no un endpoint público.
Se construyó su imagen en el VPS; no se cambiaron rutas ni lógica de la API.
Se preservaron copias de los dos archivos remotos anteriores. El archivo fuente
original del primer despliegue conserva su hash, pero el script y Compose de esa
release ahora incluyen esta actualización explícita.

Comando de bootstrap, únicamente para una instalación sin acceso inicial:

```sh
cd /opt/vivero/releases/20261002-initial/ViveroApp
docker compose --env-file /opt/vivero/shared/.env.vps --env-file /opt/vivero/shared/.env.bootstrap -p vivero-vps -f infra/docker/compose.yaml -f infra/docker/compose.vps.yaml --profile tools run --rm --no-deps bootstrap
```

Ya se ejecutó: no repetir para cambiar una contraseña o crear personal. No
imprimir `docker compose config` completo porque contiene secretos; usar
`config --quiet`. La copia `.env.vps.pre-public-20261003` conserva los secretos y
el dominio previo para recuperación; no usarla ciegamente ni versionarla.

Para obtener la contraseña, Pedro puede ejecutar personalmente en PowerShell:

```powershell
ssh -i "$env:USERPROFILE\.ssh\vivero_vps" root@179.236.238.111 "sed -n 's/^BOOTSTRAP_PASSWORD=//p' /opt/vivero/shared/.env.bootstrap"
```

No pegar el resultado en chats, archivos versionados ni tickets. Conservarlo en
un gestor de contraseñas. El mecanismo de cambio/rotación y correo real debe
validarse antes de borrar la copia de acceso inicial.

## Pruebas y respaldo

- Docker local: `npm test`, 69 casos correctos; `npm run check` correcto.
- Pruebas específicas: entrada existente/nueva, nombre/código inválidos y
  MariaDB real en base temporal sintética. Rollback al fallar alta de usuario,
  FK/rol/contraseña y bloqueo de repetición correctos; base temporal eliminada.
- La prueba SQL nueva se ejecuta explícitamente con `tests` y bind read-only
  `database/mysql` hacia `/database`, archivo
  `test/bootstrap-owner-integration.test.js`. No se ejecutó contra el VPS.
- HTTPS privado validado primero y público después. Sesiones de prueba cerradas.
- Respaldo posterior al acceso inicial creado y hashes comprobados:
  `/var/backups/vivero/vivero-2026-10-03T04-32-36-283Z-82b42d91-b199-462e-b2bd-41219abfc798`.
  Conserva el respaldo anterior; servicios reanudados y saludables. No se
  ensayó restauración ni copia cifrada externa en esta entrega.

No hubo cambios de esquema MariaDB/PostgreSQL, fuentes Web/Android ni dependencias.
No se repitieron pruebas de dispositivo, Android o Web; construcción Web del VPS
corresponde al despliegue anterior. No se envió correo real ni importó Supabase.
No hubo commit ni push. Hubo alta inicial y publicación remota autorizadas.

Archivos locales: `backend/scripts/bootstrap-owner.js`, `backend/package.json`,
`backend/test/bootstrap-owner.test.js`, `bootstrap-owner-integration.test.js`,
`infra/docker/compose.yaml`, esta guía y notas en README/guía del primer despliegue.
Trabajo preexistente preservado; rama main, HEAD
`a4621f6d42deb735714c047b05789ee83760939a`.

Siguiente: probar UI con Pedro, preparar exportación/importación conciliada,
configurar correo verificado y copia cifrada externa. La cuenta/sucursal nuevas
deben contemplarse en el mapeo del importador; no duplicar personas ni reemplazar
historial por datos demo. [Pendientes completos](backend-complete-cutover.md).
