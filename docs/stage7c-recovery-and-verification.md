# Etapa 7C — recuperación e inventarios preparados

Preparado el 9 de octubre de 2026. Los comandos VPS/teléfonos siguientes **no se ejecutaron** y requieren autorización posterior. Usar credenciales por archivo privado/entorno del servidor, nunca argumentos, chat ni logs. No ejecutar plantillas con marcadores sin revisar destino.

## Conjunto completo recuperable

1. Lote oficial: `database.sql` (`--single-transaction --routines --triggers --events --hex-blob --databases vivero`), `catalog-images.tar.gz`, manifest SHA-256 y COMPLETE. Contiene usuarios/roles funcionales; no cuentas del motor.
2. Suplemento privado del motor: inventario de principales/hosts/plugins/roles, SHOW CREATE USER y SHOW GRANTS por principal relevante; DEFINER de triggers/rutinas/eventos/vistas y sus dependencias. En MariaDB de ensayo también se probó dump SQL portátil `--system=users`; conservar cifrado, no importarlo globalmente sin revisión. No usar un dump bruto de `mysql.*` entre versiones.
3. Configuración: versión/digest de MariaDB/API/Web/Node; SQL mode, charset/collation, UTC, eventos/plugins requeridos; Compose y todos los overlays/montajes/redes/volúmenes; env privado correspondiente; fuentes, `schema.sql`, grants, seed de sólo roles y hashes de migraciones de la release exacta. Conservar llaves y certificados por su responsable. TLS del proxy se respalda separadamente y no se restaura para revertir Vivero.
4. Secretos y firma Android custodiados cifrados aparte con acceso de responsables. Los diarios Room/localStorage quedan en sus clientes, fuera del lote VPS.
5. Manifiesto suplementario: hashes, tamaños, fecha, esquema y objetos, releases/digests, alcance de principales, responsable, ruta/procedencia externa. COMPLETE oficial sólo certifica dos archivos; no supone validación del suplemento.

El script oficial `infra/docker/backup.mjs` detiene API/Web sólo de su proyecto y reanuda los que encontró activos, incluso al fallar. Para actualización, mantenimiento acordado y escritores detenidos **antes** del respaldo; comprobar que no se reabren después. Cerrar o conciliar solicitudes en vuelo. No iniciar mientras se cobra o envía inventario. El proxy independiente debe quedar activo.

## Toni: captura privada posterior autorizada

Validar primero identidad/labels/montajes de la DB exclusivamente de Vivero. En sesión administrativa, `umask 077`; directorio privado suplementario ligado al lote. No mandar el resultado de estos comandos al chat:

```sh
# PLANTILLA futura; confirmar DB, directorio y versión antes.
umask 077
supplement=/RUTA/PRIVADA/DEL/LOTE/supplement
mkdir -p "$supplement"
docker exec vivero-vps-db-1 sh -c \
 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb-dump --user=root --default-character-set=utf8mb3 --system=users' \
 > "$supplement/engine-users.sql"
```

Exigir exit code cero y salida completa, no sólo archivo no vacío. La opción utf8mb3 resolvió el conflicto de collations del **motor sintético 11.4.13 con cuentas ASCII**; verificar versión/nombres/plugins reales. No usarla si pierde caracteres. Alternativa: capturar SHOW CREATE USER/SHOW GRANTS mediante conexión con charset/collation adecuados y resultados privados estructurados; cotejar autenticación sin publicarla. Para `catalog_api`, incluir grants de columna y restricciones de escritura, no sólo SELECT/INSERT de tablas. No conceder ALL PRIVILEGES ni EXECUTE general para resolver un fallo.

No ejecutar DROP USER ni importar el dump global sobre producción, Catering o una instancia compartida. Conservar administradores/healthcheck y sus archivos de configuración. Root y cualquier principal DEFINER se reconstruyen en el **destino nuevo** mediante bootstrap compatible/configuración privada; decidir restauración o rotación coordinada de contraseñas con Toni. Se ensayó la restauración exacta de `catalog_api`, no una sustitución completa de los administradores del motor.

## Restauración aislada verificable

1. Recuperar desde snapshot externo con Restic en otra máquina/destino, snapshot y target explícitos; configuración privada. `offsite-backup.py check` verifica datos remotos, pero no descarga. Probar además descifrado con llave custodiada fuera del VPS. Subir suplementos mediante un procedimiento Restic revisado: el helper actual sólo respalda el directorio oficial validado.
2. Verificar manifest/hashes del lote descargado y del suplemento. Conservar prueba del snapshot externo que originó la descarga. No acceder a datos reales para ensayo sin autorización específica.
3. Crear proyecto exclusivo, redes internas, credenciales/volúmenes nuevos, sin puertos ni correo externo. Iniciar sólo DB; imágenes vacías. Inicializar **la misma release/esquema del respaldo**, seed sólo roles, grants/migraciones exactos. Restore no limpia objetos posteriores ausentes: nunca importar 029 sobre bootstrap 032 para ahorrar pasos.
4. Reconstruir principales/DEFINER requeridos antes de importar. Usar bootstrap oficial y suplemento revisado, con privilegios mínimos y autenticación coherente con el env destino. No alterar usuarios de otras instancias/proyectos. Mantener `event_scheduler` sin actividad de negocio mientras se ensaya; revisar eventos y dependencias antes de activarlos.
5. Ejecutar script revisado `restore.mjs --env-file ENV_PRIVADO --project PROYECTO_NUEVO --backup-dir LOTE --overlay OVERLAY_AISLADO --acknowledge-empty-target`. No apuntar al proyecto operativo. Requiere DB única activa y contador 031 sin uso si existe. Rechazo por destino ocupado exige otro destino, no borrar datos.
6. Cotejar todos los datos/DDL/índices/FKs/checks/triggers/rutinas/eventos/vistas, marcadores y hashes de migración. Verificar IDs/folios/aliases, secuencias, pagos/cancelaciones/devoluciones/cortes, importes, inventario y relaciones pedido/venta. Comparar imágenes y lectura por UID real del API. Cotejar cuentas, grants de columna/tabla/esquema/rutina, DEFINER/plugins/configuración.
7. Antes de tocar históricos, guardar conciliación. Aplicar sólo migraciones faltantes en orden con el migrador oficial. No existe rollback DDL global: ante fallo guardar estado y mantener escritores cerrados, no repetir ni resetear ciegamente.
8. Iniciar sólo API aislada con usuario runtime, no root. Health, login/catálogo y permisos efectivos; triggers/DEFINER vía acciones permitidas; operaciones no permitidas siguen rechazadas. Con datos productivos restaurados usar lectura y cuentas sintéticas autorizadas separadas: no crear ventas sobre clientes reales ni enviar correo.
9. Guardar evidencias sanitizadas, tiempo/RPO/RTO, procedencia externa, versión y responsable. Detener sólo el ensayo, conservar lotes y volúmenes. El ensayo 7C prueba 029 sintético; no certifica el esquema/volumen real ni recuperaciones de plugins no disponibles.

Si una migración o actualización falla en una ventana futura: mantener escritores detenidos, conservar estado actual y diarios, decidir reparación compatible o restore en **otro destino nuevo**. Rollback de imagen no revierte SQL. Si hubo operaciones posteriores al respaldo, conservarlas y conciliarlas antes de cambiar a una copia anterior. No borrar históricos ni repasar ventas con claves nuevas. Mantener Supabase.

## VPS: comandos de verificación preparados, sólo lectura

Después de autorización y de confirmar host/huella, ejecutar en el VPS correcto. Sin `docker inspect` completo, `.Config.Env`, `compose config` sin filtro, SQL de clientes ni logs con secretos:

```sh
readlink -f /srv/apps/vivero-dulcinea/current
docker ps --format '{{.Names}} {{.Image}} {{.Status}} {{.Ports}}'
docker inspect platform-proxy-proxy-1 vivero-vps-web-1 vivero-vps-api-1 vivero-vps-db-1 \
 --format '{{.Name}} {{.Id}} {{.Image}} {{.State.StartedAt}} {{json .Config.Labels}} {{json .HostConfig.PortBindings}}'
docker inspect platform-proxy-proxy-1 vivero-vps-web-1 \
 --format '{{.Name}} {{json .Mounts}} {{json .NetworkSettings.Networks}}'
docker network inspect platform_proxy --format '{{json .Containers}}'
docker volume inspect vivero-vps_mariadb_data vivero-vps_catalog_images vivero-vps_caddy_data \
 --format '{{.Name}} {{.Mountpoint}}'
docker compose -p platform-proxy -f /srv/proxy/compose.yaml config --quiet
/srv/apps/vivero-dulcinea/ops/viveroctl.sh status
/srv/apps/vivero-dulcinea/ops/viveroctl.sh validate
docker exec platform-proxy-proxy-1 caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
df -h /srv /var/backups
docker system df
curl -fsS --max-time 15 -o /dev/null -w '%{http_code}\n' https://viverodulcinea.bajastack.network/health
curl -fsS --max-time 15 -o /dev/null -w '%{http_code}\n' https://cateringoculto.bajastack.network/health/ready
```

Confirmar nombres reales de Catering antes de inspeccionar su ID/StartedAt. Cotejar rutas efectivas de `platform.Caddyfile`/`Caddyfile.http`, ausencia de 80/443 en Web Vivero, TLS montado únicamente en proxy, alias/red y puertos de Catering intactos. Registrar ID/StartedAt del proxy y Catering antes/después de la futura ventana. Health no identifica release: registrar IDs/digests de imágenes y hash de bundle/configuración; SQL VERSION/marcadores y hashes de migración mediante conexión privada, sólo lectura.

Si no coincide topología, **NO-GO** y revisar configuración; no ejecutar separación de nuevo. No usar el overlay local de Vivero en sustitución del activo. No `compose down`, recreación del proxy, restart de DB o actualización de imagen MariaDB en esta entrega.

Con GO y autorización de despliegue posterior, el selector de actualización será exclusivamente el Compose confirmado `vivero-vps`, incluyendo overlay activo del host: `up -d --no-deps --no-build api` y después `up -d --no-deps --no-build web` con imágenes preparadas/revisadas. Son comandos de cambio **no ejecutados ni autorizados aquí**. No incluir `platform-proxy` ni `catering-oculto`; no requieren reload HTTPS si no cambian rutas. Antes: mantenimiento, backup completo/restaurable y migraciones; después: compatibilidad, smoke y conciliación. Mantener recuperación del origen Web anterior para diarios locales.

## Android: inventario futuro sin tocar datos privados

Sólo después de autorización específica para metadatos de cada teléfono; obtener serial acordado por Pedro, sin publicarlo. Todos los comandos requieren `-s SERIAL_CONFIRMADO`. No correr Gradle `connected*`, abrir Activity, instalar/desinstalar, limpiar datos o detener procesos durante este inventario:

```powershell
# PLANTILLAS; no ejecutadas en 7C.
adb -s SERIAL_CONFIRMADO shell getprop ro.build.version.sdk
adb -s SERIAL_CONFIRMADO shell pm list packages com.intutec.viveroapp
adb -s SERIAL_CONFIRMADO shell dumpsys package PAQUETE_CONFIRMADO |
  Select-String 'versionCode=|versionName=|minSdk=|targetSdk=|firstInstallTime=|lastUpdateTime=|DEBUGGABLE'
adb -s SERIAL_CONFIRMADO shell pm path PAQUETE_CONFIRMADO
```

Con permiso para copiar **sólo código APK público instalado**, `adb -s SERIAL_CONFIRMADO pull RUTA_BASE_APK DIRECTORIO_PRIVADO`; conservar también splits enumerados. Verificar `apksigner verify --print-certs` de base/splits y `aapt2 dump badging`; comparar paquete, conjunto de firmantes/linaje, SDK y versionCode con candidata futura. Rotación/múltiples firmas no se resuelven forzando el verificador de firmante único. No `run-as`, explorar `/data/user/0`, copiar Room/sesiones ni acceder a fotos como parte de este permiso de metadatos.

Pendientes/Room requieren una fase distinta autorizada: Pedro/Toni consultan la UI, sin reenviar; concilian resultados originales en servidor/sucursal. No inferir versión SQLite real a partir de versionName. Si se autoriza acceso privado y la app debuggable permite `run-as`, un especialista prepara copia coherente con proceso detenido y conserva `vivero.db`, WAL/SHM si existen, archivos/preferencias/diarios pertinentes; cifra la copia, verifica hashes y restauración en un entorno separado. Nunca copiar SQLite en caliente, usar root/bypass ni asumir exportación disponible. La parada o copia privada también requiere autorización; no se ejecutó aquí.

Compatibilidad negativa (firma/paquete distinto, downgrade, API incompatible, pendientes inciertos o ausencia de copia recuperable): detener actualización, conservar instalación, recuperar llave compatible o acordar migración/exportación específica. `.vpsvalidation` posee almacenamiento separado; instalar el principal no lo migra. No recomendar desinstalación automática, `pm clear`, downgrade `-d` ni cambiar applicationId.

Tras inventario compatible, permiso de versión/build y pruebas de copia representativa: generar candidata con versión superior, comprobar destino HTTPS y firma existente; después permiso específico de instalación, actualización compatible sin desinstalar (`install -r` para APK único; splits requieren conjunto compatible), reapertura y conciliación antes/después. No prometer recuperación del paquete ausente tras el incidente 7A.

## Alarmas y retención a cargo de Toni

No hay programación/retención externa acreditada actualmente. Registrar por separado éxito de backup local, reanudación de servicios, subida, verificación externa y restore. Alarmar exit code distinto de cero, INCOMPLETE, hash inválido, espacio insuficiente, fallo de reanudación o antigüedad del último lote/snapshot superior al objetivo acordado. No marcar éxito de copia externa por existir COMPLETE local. Programación, canal de aviso y política concreta diaria/semanal/mensual requieren acuerdo de responsables. No activar forget/prune ni eliminar respaldos hasta verificar restauración y aprobar retención. Probar una alarma ficticia en entorno aislado cuando Toni tenga su automatización, sin simular fallo deteniendo producción.
