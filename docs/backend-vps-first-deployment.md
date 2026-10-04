# Primer despliegue privado en VPS

Actualización posterior: dominio público con HTTPS y cuenta inicial ya verificados.
[Estado actual](backend-vps-public-access.md). Lo siguiente conserva la evidencia
del primer despliegue privado.

Verificado el 2026-10-02 (America/Chihuahua; servidor UTC: 2026-10-03).
Pedro autorizó conectar, preparar y desplegar después de inspeccionar el destino.
Se comprobó que no había Docker ni despliegues en /opt o /srv. Se conservaron las
sesiones SSH y la configuración de autenticación existentes.

## Estado real

- Hostinger: Ubuntu 24.04.5 LTS, 8 GB RAM, disco de unos 96 GB.
- Docker Engine 29.8.2 y Compose 5.6.0 instalados desde el repositorio APT oficial.
- UFW activo: entrada denegada por defecto, salida permitida, SSH 22 autorizado
  en IPv4/IPv6. No se abrieron puertos públicos Web, API ni MariaDB.
- Proyecto Compose `vivero-vps`: Web/Caddy, API y MariaDB levantados. DB/API
  saludables. Web responde por HTTPS con CA local validada.
- Web escucha solo en 127.0.0.1:33080 y 127.0.0.1:33443. API y DB no publican
  puertos al host. No hay dominio confirmado ni certificado público.
- MariaDB nueva: 45 tablas, 25 marcadores, última migración
  `026_payment_attempt_retirement`, seis roles; cero usuarios, sucursales,
  productos, ventas y pagos. No se creó una cuenta inicial ni se importaron datos.
- Configuración privada generada únicamente en el servidor, archivo modo 600.
  La llave SSH y su frase permanecen en Windows; no se copiaron al VPS.

Es una validación privada de infraestructura, no un corte operativo ni una
migración completa. No se puede iniciar sesión todavía porque no hay usuarios.
Supabase continúa pendiente de exportación/importación y conciliación real.

## Rutas y operación

Fuentes subidas desde el árbol de trabajo actual (incluye avances sin commit):

```text
/opt/vivero/releases/20261002-initial/
  ViveroApp/       backend/, database/mysql/, infra/docker/
  ViveroWeb/       fuentes y archivos de construcción
/opt/vivero/shared/.env.vps        configuración privada, no imprimir
/opt/vivero/shared/caddy-local-root.crt  CA pública del ensayo privado
/var/backups/vivero/              respaldos privados del servidor
```

Se seleccionaron 326 archivos de construcción/ejecución. No se transfirieron Git,
.env locales, claves, Android, node_modules ni cachés. SQL/shell se empaquetaron
con LF para Linux. SHA-256 del archivo fuente transferido y comprobado:
`4df36bc8d4cf7881e794f7e5ee45a013a7a699fc588f863f8b1a861242f8e29b`.

Desde la carpeta ViveroApp remota:

```sh
docker compose --env-file /opt/vivero/shared/.env.vps -p vivero-vps -f infra/docker/compose.yaml -f infra/docker/compose.vps.yaml -f infra/docker/compose.private.yaml ps
docker compose --env-file /opt/vivero/shared/.env.vps -p vivero-vps -f infra/docker/compose.yaml -f infra/docker/compose.vps.yaml -f infra/docker/compose.private.yaml config --quiet
```

`compose.private.yaml` es un overlay temporal del mismo stack: sustituye los
puertos públicos por localhost y ajusta WEB_ORIGIN. Conserva servicios y volúmenes
oficiales. No ejecutar `down -v` ni reutilizar el seed demo en esta base.

Acceso desde Windows mediante túnel, sin publicar puertos:

```powershell
ssh -N -L 33443:127.0.0.1:33443 -i "$env:USERPROFILE\.ssh\vivero_vps" root@179.236.238.111
```

HTTPS local utiliza una CA de ensayo propia; no instalar certificados ni omitir
verificación automáticamente. Para uso público se debe elegir dominio, apuntar
DNS y verificar HTTPS público. La prueba remota validó explícitamente la CA:

```sh
curl --cacert /opt/vivero/shared/caddy-local-root.crt https://localhost:33443/health
```

## Evidencia nueva

- Construcción API y Web dentro de Docker en el VPS: correcta (TypeScript/Vite).
- Configuración Compose validada sin imprimir variables privadas.
- HTTPS `/health`, `/login` y `/api/v1/products`: 200. Conteos de SQL comprobados;
  endpoints no equivalen a login o aceptación operativa.
- Puertos inspeccionados y nueva conexión SSH correcta después de activar UFW.
- Respaldo inicial SQL/imágenes mediante `infra/docker/backup.mjs`: correcto.
  API/Web se pausaron y reanudaron; DB permaneció levantada. Directorio:
  `/var/backups/vivero/vivero-2026-10-03T03-23-04-114Z-b91f4a4b-1cd6-4231-8432-35920e77bb58`.
- Verificación de los dos hashes del respaldo: correcta. Health HTTPS volvió a
  responder después de reanudar servicios. No se ensayó restauración en este VPS.
- El script de respaldo se ejecutó con Node 24 en contenedor de mantenimiento,
  CLI/plugins Docker y socket locales, rutas del host idénticas y overlay privado.
  No se instaló Node globalmente ni se copiaron secretos al equipo local.

No se repitieron suites Android/Web/backend sin cambios de lógica. Las pruebas
históricas conservan su alcance. No hay correo real, datos importados, aceptación
UI/dispositivo ni certificado público. La copia cifrada de respaldos fuera del
VPS sigue pendiente, al igual que la custodia independiente de secretos.

## Siguiente paso

Confirmar dominio/DNS y el acceso inicial (cuenta/sucursal o importación conciliada)
antes de abrir el proyecto al público. Completar los pendientes de migración,
usuario administrador sin root para mantenimiento, correo y respaldo externo.
No deshabilitar root/SSH mientras otros administradores trabajan sin coordinar
sus accesos. [Pendientes del corte](backend-complete-cutover.md).

Cambios locales: este documento, overlay privado, enlace en README y notas de
vigencia en preparación VPS/estado de corte. El resto del trabajo preexistente se
preservó. Rama main, HEAD a4621f6d42deb735714c047b05789ee83760939a.
Hubo operaciones remotas y despliegue privado autorizados; no hubo commit ni push.
