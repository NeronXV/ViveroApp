# VPS compartido: guía para Pedro y Toni

Estado inspeccionado y ajustado el 3 de octubre de 2026, America/Mazatlan.
VPS Hostinger `2030059`, hostname `srv2030059.hstgr.cloud`, Ubuntu 24.04 LTS,
plan KVM 2, 8 GB RAM. La inspección encontró aproximadamente 93 GB libres y
7 GB de memoria disponible; son una medición puntual, no una reserva de capacidad.

## Cómo funciona Vivero hoy

`bajastack.network` apunta al VPS por IPv4/IPv6. Un único Caddy, dentro de
`vivero-vps-web-1`, termina HTTPS con certificado público, sirve la Web compilada
y envía `/api/*` y `/health` a `api:3001`. La API consulta MariaDB por una red
interna. Los únicos puertos públicos son SSH 22 y Web 80/443; API 3001 y DB 3306
no están publicados. El puerto administrador 2019 de Caddy tampoco se publica.
UFW permite únicamente SSH y Web. No se encontró otro Caddy, nginx ni Apache.

El proyecto Compose real es `vivero-vps`: Web, API y DB en ejecución, API/DB
saludables, reinicio `unless-stopped` y logs con rotación 10 MB × 3 por servicio.
La etiqueta histórica de DB aún menciona `compose.private.yaml`; eso no significa
que Web siga privada. La configuración pública vigente usa `compose.yaml`,
`compose.vps.yaml` y ahora el overlay de red `compose.host.yaml`.

La base operativa `vivero` tiene 28 marcadores hasta 029. Se comprobaron seis
cuentas, dos sucursales, quince productos, catorce ventas/pagos, veinticinco
proyecciones de inventario, treinta y ocho movimientos y seis conteos.
La imagen original se excluyó por decisión de Pedro. Cero imágenes actuales.
Cinco cuentas requieren establecer contraseña y ninguna sucursal tiene
activado el control de stock. El correo de acceso y newsletter carecen de
configuración; un health correcto no prueba envío de correo.

Hostinger confirmó el VPS activo. Su API devolvió cero respaldos del proveedor
y snapshot con ID 0, por lo que no hay un punto de restauración válido acreditado.
Docker Manager respondió que la plantilla instalada no es compatible; Docker
se administra por SSH y Compose. No se instaló un panel alternativo.

## Estructura final y compatibilidad

Se creó una entrada consistente bajo `/srv`; los enlaces conservan los archivos
actuales en `/opt/vivero` y respaldos en `/var/backups/vivero`. No se movieron
datos persistentes, renombraron volúmenes ni recrearon servicios operativos.

```text
/srv/apps/
  projects.json                    registro de proyectos, sin secretos
  vivero-dulcinea/
    current -> /opt/vivero/releases/20261002-initial
    releases -> /opt/vivero/releases
    shared -> /opt/vivero/shared    .env.vps y acceso inicial, privados
    maintenance -> /opt/vivero/maintenance
    backups -> /var/backups/vivero
    ops/
      viveroctl.sh
      compose.host.yaml
      project.compose.example.yaml
      project.env.example
/srv/proxy/
  Caddyfile -> Caddyfile del despliegue actual
  owner.json                       identifica el Caddy existente
/srv/backups/
  vivero-dulcinea -> /var/backups/vivero
```

Mantener `vivero-vps` como nombre único de este proyecto. Cambiar `-p` sin migrar
explícitamente volúmenes puede crear una base nueva y aparentar pérdida de datos.
Los volúmenes actuales son:

| Volumen | Contenido |
|---|---|
| `vivero-vps_mariadb_data` | MariaDB operativa |
| `vivero-vps_catalog_images` | Archivos de imágenes |
| `vivero-vps_caddy_data` | Certificados y estado TLS de Caddy |
| `vivero-vps_caddy_config` | Estado de configuración de Caddy |

`vivero-vps_database` es interna y conecta únicamente API/DB.
`vivero-vps_api` conecta Web/API. La nueva red externa `platform_proxy` conecta
actualmente solo Caddy/Web, con alias `vivero-dulcinea-proxy`. Se añadió al
contenedor activo sin recrearlo y se declaró en el overlay para futuras
recreaciones. Las bases de datos nunca se conectan a esa red compartida.

## Proyecto futuro

Usar `/srv/apps/<slug>/releases/<version>`, `current`, `shared`, `ops` y
`/srv/backups/<slug>`. Un slug y nombre Compose únicos, por ejemplo
`proyecto-ejemplo`; volúmenes y red de base propios derivados de ese nombre.
Credenciales independientes por aplicación y privilegios mínimos para su API.

Los ejemplos versionados están en `infra/host/`; contienen placeholders, no
credenciales. Adaptar el contrato de la imagen, healthchecks, puerto interno,
migraciones y volúmenes de archivos de cada aplicación antes de desplegar.
El ejemplo no levanta otro backend del vivero ni contiene datos demo.
El frontend/entrada HTTP conecta a `platform_proxy` con alias `<slug>-web`.
No publicar puertos del frontend, API o DB: Caddy es la única entrada Web.

Reutilizar `/srv/proxy/Caddyfile` añadiendo bloques por dominio:

```caddyfile
proyecto.example.invalid {
    reverse_proxy proyecto-ejemplo-web:3000
}
```

Ese dominio es únicamente un ejemplo. Su DNS, titularidad y TLS deben verificarse
cuando exista el proyecto. Respaldar el Caddyfile antes de editar; el bind mount
actual monta un archivo, por lo que debe escribirse sobre el archivo existente,
conservando su inode, en vez de reemplazarlo mediante rename. Validar y recargar
después; ante error, restaurar el contenido anterior y validar de nuevo.

El proxy compartido todavía pertenece al Compose y a la imagen Web de Vivero.
Mientras se mantenga así, parar/actualizar `web` o respaldar Vivero afecta todos
los dominios que se añadan. Coordinar esa ventana. Separar Caddy a un Compose
`platform-proxy` es requisito antes de incorporar el segundo proyecto, después
de respaldar certificados y explicar el corte. No se creó otro proxy.

## Revisar estado, logs y proxy

Después de conectar por SSH con una llave autorizada:

```sh
/srv/apps/vivero-dulcinea/ops/viveroctl.sh status
/srv/apps/vivero-dulcinea/ops/viveroctl.sh validate
/srv/apps/vivero-dulcinea/ops/viveroctl.sh logs api
/srv/apps/vivero-dulcinea/ops/viveroctl.sh logs web
/srv/apps/vivero-dulcinea/ops/viveroctl.sh logs db
/srv/apps/vivero-dulcinea/ops/viveroctl.sh proxy-check
/srv/apps/vivero-dulcinea/ops/viveroctl.sh proxy-reload
```

Revisar logs en el servidor; sanitizar antes de compartirlos. No ejecutar ni
publicar `docker inspect` o `docker compose config` completos, porque incluyen
entornos privados. Usar `config --quiet`. No versionar ni imprimir `.env`.
Los archivos privados existentes permanecen con modo 600. El acceso individual
de Pedro se habilitó y probó en la continuación descrita al final. Toni sigue
pendiente de su llave pública; no se deshabilitó el acceso actual.

## Actualizar Vivero

1. Preparar una release nueva con `ViveroApp/` y `ViveroWeb/` hermanos; revisar
   hashes y excluir `.env`, claves, Git, cachés y datos privados. Preservar la
   release anterior y usar los contratos/migraciones oficiales del repositorio.
2. Validar Compose con el mismo `-p vivero-vps`, archivo privado y overlay host.
   Construir las imágenes necesarias; registrar sus IDs anteriores antes de
   reemplazar tags. Cambiar `current` por sí solo no actualiza contenedores.
3. Crear/verificar respaldo, explicar ventana e impacto y detener escritores
   antes de migraciones/importaciones. Aplicar solo migraciones pendientes.
4. Recrear únicamente servicios afectados usando los volúmenes existentes.
   No incluir el overlay privado histórico. No usar `down -v`, prune ni reset.
5. Comprobar health, login/roles, catálogo, caja e inventario, y respaldar de nuevo.
   Cambiar `current` solo al cerrar la actualización verificada. Un rollback de
   imagen requiere compatibilidad con el esquema nuevo; no restaura datos ni DDL.

Prefijo de comandos Compose para operaciones específicas:

```sh
cd /srv/apps/vivero-dulcinea/current/ViveroApp
docker compose --env-file /srv/apps/vivero-dulcinea/shared/.env.vps \
  -p vivero-vps -f infra/docker/compose.yaml -f infra/docker/compose.vps.yaml \
  -f /srv/apps/vivero-dulcinea/ops/compose.host.yaml config --quiet
```

Para administración SQL usar SSH y `compose exec -it db mariadb --user=root -p`
con ese mismo prefijo, introduciendo la contraseña en la consola del servidor.
Las herramientas automáticas usan el perfil `tools` y su configuración privada.
No abrir 3306 público. Si se necesita una GUI, establecer un túnel SSH temporal
al IP interno confirmado del contenedor; no guardar ese IP como endpoint estable.

## Respaldos y copia fuera del VPS

```sh
/srv/apps/vivero-dulcinea/ops/viveroctl.sh backup --acknowledge-downtime
/srv/apps/vivero-dulcinea/ops/viveroctl.sh verify-backup /var/backups/vivero/CARPETA
```

El respaldo pausa y reanuda API/Web, conserva DB activa y captura SQL e imágenes.
El wrapper exige reconocer la pausa. No ejecutar importadores ni SQL externo
durante esa ventana. No copia secretos ni certificados: proteger por separado
configuración, llaves de cifrado y estado de Caddy. Los certificados se conservan
actualmente en sus volúmenes originales.

Hay seis respaldos locales completos con hashes comprobados; el último es
`vivero-2026-10-03T17-54-23-720Z-a30dcf01-8aa6-4fdc-9dc3-311e8150dfbe`.
Están en el mismo VPS y no constituyen protección frente a perder el servidor.

Estrategia propuesta: repositorio cifrado externo por proyecto (por ejemplo,
Restic sobre S3 compatible o SFTP de otra máquina), con credenciales específicas
y contraseña de cifrado custodiada por Pedro/Toni fuera del VPS. Copiar únicamente
lotes COMPLETE cuyos hashes se hayan verificado. Retención propuesta: siete
diarios, cuatro semanales y seis mensuales; revisar capacidad antes de adoptarla.
Conservar también fuentes/versiones, Compose, Caddyfile y configuración privada
en copia cifrada; separar credenciales de acceso del repositorio y llave de
recuperación. Tras copiar, comprobar integridad externa y hacer una restauración
periódica en un proyecto nuevo sin puertos públicos. No activar borrado automático
ni retención destructiva sin revisar el alcance.

Al terminar la organización inicial no se configuró almacenamiento externo,
Restic ni tareas automáticas: faltaban
destino, titularidad, presupuesto si aplica y custodia de la llave. No hay una
copia externa ni respaldo Hostinger acreditados en esta inspección.

## Restaurar: ensayo real completado

Se restauró el respaldo más reciente mediante `infra/docker/restore.mjs` en
`vivero-restore-check-20261003`, con credenciales nuevas en un archivo privado,
redes/volúmenes propios y cero puertos publicados. La herramienta comprobó
hashes y destino vacío antes de importar. Se conciliaron las 55 tablas,
28 migraciones, datos e importes; OWNER conservado, huellas de los cuatro
importadores reutilizables, mínimos/existencias exactos. Se comprobó trigger y
FK mediante operaciones sintéticas revertidas; API restaurada saludable y
catálogo disponible. No se arrancó Web del ensayo.

El proyecto de ensayo queda detenido, con dos contenedores y volúmenes
`vivero-restore-check-20261003_mariadb_data` y
`vivero-restore-check-20261003_catalog_images` conservados. Su configuración y
evidencia privada están en `/opt/vivero/maintenance/20261003-restore/`.
No sustituir producción con ese proyecto ni eliminar sus volúmenes a ciegas.

Para otra recuperación, crear un proyecto nuevo con credenciales independientes,
iniciar solo DB con esquema oficial y seed sin demo, verificar que no hay datos
operativos ni imágenes, y ejecutar en el host Docker:

```sh
node infra/docker/restore.mjs --env-file /RUTA/PRIVADA/.env.restore \
  --project NOMBRE_NUEVO --overlay /RUTA/compose.restore.yaml \
  --backup-dir /var/backups/vivero/CARPETA --acknowledge-empty-target
```

En este VPS no se instaló Node global. El wrapper ejecuta la herramienta dentro
del contenedor de mantenimiento Node 24, con las mismas rutas del host y Docker
CLI/socket montados. Después de preparar el destino vacío y su overlay:

```sh
/srv/apps/vivero-dulcinea/ops/viveroctl.sh restore-isolated \
  /opt/vivero/maintenance/NUEVO_ENSAYO/.env.restore \
  vivero-restore-NUEVO \
  /var/backups/vivero/CARPETA \
  /opt/vivero/maintenance/NUEVO_ENSAYO/compose.restore.yaml \
  --acknowledge-empty-target
```

Sustituir los nombres de ejemplo por rutas/proyecto reales; el nombre Compose
debe usar minúsculas y ser nuevo. El wrapper rechaza el nombre de producción
y configura únicamente proyectos `vivero-restore-*`. La herramienta comprueba
el destino vacío; no borra datos para poder restaurar. El overlay de ensayo
quita puertos Web y utiliza imágenes revisadas.
Reconciliar SQL/imágenes antes de arrancar la API; ante importación interrumpida,
mantener el destino detenido, conservar evidencia y revisar antes de repetir.

## Verificado y pendiente

Nuevos: inventario real, seis hashes de respaldos, estructura por enlaces,
red compartida sin recreación, sintaxis del wrapper, ambas configuraciones
Compose, Caddy, HTTPS/login operativo después de cambios y restauración real.
Las 83 pruebas del bloque de importación son evidencia anterior;
no se repitieron al organizar el host. Sin cambios de lógica
Android/Web/backend ni SQL PostgreSQL que justifiquen esas compilaciones/pgTAP.

Pendientes: copia cifrada externa, llave/acceso personal de Toni,
correo y cinco cuentas nuevas, aceptación UI/dispositivos, activación de stock
tras revisión e imagen manual. Los consumidores restantes deben completar sus
contratos API antes de retirar Supabase. No se enviaron correos ni se alteraron
contraseñas/roles o stock durante esta organización.

## Continuación de cierre: copia externa y accesos

Inspección adicional inicial del 3 de octubre: no había Restic, Rclone ni Borg instalados;
no se encontraron sus configuraciones en `/srv`, configuración compartida de
Vivero, directorios habituales de `/etc` y `/root/.config`, ni unidades propias
o entradas de cron con esos nombres. Los archivos privados compartidos no
declaran variables de destino externo. Solo se encontró el usuario interactivo
`root`, con autenticación de contraseña y llave habilitadas. Esto describe las
rutas revisadas; no garantiza la ausencia de integraciones fuera de ellas.

Se prepararon `infra/host/offsite-backup.py` y `offsite.config.example.json`.
Son herramientas manuales para un repositorio cifrado Restic por proyecto,
S3 sobre HTTPS o SFTP externo. No compran almacenamiento, instalan servicios,
programan tareas ni ejecutan `forget`, `prune` o eliminación de respaldos.
El script verifica cada lote con el verificador oficial antes de subirlo,
rechaza destinos locales y exige configuración/contraseña privadas de root.
La salida del proveedor no se imprime. El envío y la verificación remota
todavía no se han ejecutado: falta un destino real.

Opciones y datos necesarios:

| Opción | Datos de conexión pendientes |
|---|---|
| Bucket S3 compatible fuera del VPS | Proveedor, endpoint HTTPS, bucket existente, región y prefijo exclusivo; credenciales limitadas al repositorio por vía privada |
| Servidor/NAS externo con SFTP | Host, puerto, usuario, carpeta, llave dedicada y huella del servidor confirmada por su administrador |

Restic cifra también el repositorio remoto; la contraseña de recuperación debe
guardarse además en el gestor de contraseñas de Pedro/Toni fuera del VPS.
Para SFTP, usar `sftp:ALIAS_SSH:/RUTA/vivero-dulcinea` y `environment: {}`;
configurar el alias en `/root/.ssh/config` con llave exclusiva, BatchMode y
StrictHostKeyChecking, sin aceptar una huella a ciegas. Para S3 usar la plantilla.
Referencia: [repositorios y cifrado de Restic](https://restic.readthedocs.io/en/stable/030_preparing_a_new_repo.html).

Se instaló después únicamente Restic desde Ubuntu noble-updates/security,
paquete `0.16.4-2ubuntu0.24.04.3`, sin actualizar/eliminar otros paquetes ni
reiniciar servicios. Pasó un ensayo local con datos sintéticos: repositorio
cifrado, `check --read-data`, rechazo de contraseña incorrecta y recuperación
idéntica byte a byte. El ensayo temporal se limpió; no leyó datos reales ni
transmitió al exterior. Esto no prueba todavía el acceso a R2.

Una vez confirmado el destino, preparar en el servidor `shared/offsite.json`
y `shared/offsite.password`, ambos root:root modo 600. Nunca copiar estos archivos
al repositorio. Custodiar la contraseña externamente antes del primer envío.
Los archivos de ejemplo solo contienen valores de reemplazo.

```sh
python3 /srv/apps/vivero-dulcinea/ops/offsite-backup.py init --acknowledge-new-repository
python3 /srv/apps/vivero-dulcinea/ops/offsite-backup.py upload /var/backups/vivero/LOTE_COMPLETE_VERIFICADO
python3 /srv/apps/vivero-dulcinea/ops/offsite-backup.py check
```

`init` solo se usa para un repositorio nuevo confirmado. `check` lee todos los
datos remotos y puede generar tráfico facturable. Tras la primera copia, recuperar
un snapshot a una carpeta nueva en maintenance usando Restic, verificar hashes
del lote recuperado y repetir la restauración aislada SQL/imágenes. No declarar
protección externa completa hasta pasar esa recuperación. El script copia SQL
e imágenes; la copia cifrada de configuración/secretos y certificados sigue
siendo un bloque separado pendiente. No copia volúmenes de DB en vivo.

Pedro identificó `C:\Users\GAMER\.ssh\vivero_vps.pub` como su llave personal.
Se comprobó contra la llave ya autorizada en root, se habilitó el usuario `pedro`
y se probó un ingreso SSH real con esa llave. Su contraseña permanece bloqueada.
`/etc/sudoers.d/vivero-pedro` permite exclusivamente el wrapper root:root con
`status`, `validate`, `logs api`, `logs web`, `logs db`, `proxy-check`,
`proxy-reload` y `backup --acknowledge-downtime`. `visudo` pasó; estado/validación
por sudo funcionaron y sudo general fue rechazado. No pertenece al grupo Docker.
No se ejecutó otro respaldo ni reload al probar estos permisos.

```powershell
& 'C:\Windows\System32\OpenSSH\ssh.exe' -i "$env:USERPROFILE\.ssh\vivero_vps" pedro@179.236.238.111
```

Dentro de esa sesión, anteponer `sudo -n` a los comandos autorizados del wrapper.
Toni todavía no tiene usuario/acceso; no usar la llave de Pedro para habilitarlo.
Despliegue, restauración, copia externa y cambios de sistema requieren por ahora
la sesión administrativa existente. Acordar por separado sudo administrativo
si se necesita. Root, contraseña y firewall actuales se conservaron; restringirlos
solo después de probar ambas sesiones personales y el acceso de recuperación.

Recomendación inicial de destino: R2 Standard en una cuenta Cloudflare de Pedro,
con bucket privado exclusivo para respaldos y credencial limitada al bucket.
Pedro eligió R2 Standard; falta conectar su cuenta y habilitación. Se propone
bucket privado `vivero-dulcinea-backups`, prefijo `restic`, región `auto` y endpoint
`https://<ACCOUNT_ID>.r2.cloudflarestorage.com`. No publicar bucket ni activar
ciclo de vida que borre objetos de Restic. Usar Object Read & Write limitado
solo al bucket; no una credencial global de la cuenta.
[Conexión oficial S3](https://developers.cloudflare.com/r2/get-started/s3/).
La tarifa consultada el 3 de octubre de 2026 incluye 10 GB-mes, un millón de
operaciones A y diez millones B mensuales; el exceso de almacenamiento cuesta
US$0.015/GB-mes y las operaciones excedentes se cobran aparte. No cobra salida.
[Tarifas oficiales](https://developers.cloudflare.com/r2/pricing/).
Backblaze B2 o un NAS/servidor SFTP externo son alternativas. No se creó cuenta,
bucket, suscripción ni credencial; falta confirmar la cuenta y conectar el destino.

## Validación nueva de Web y preparación Android

En esta continuación el navegador mostró login y recuperación Web sin errores
visibles; no se enviaron formularios/correos ni se completó un recorrido de caja
en UI. La prueba HTTPS de servidor pasó login OWNER, sucursal/capacidades,
catálogo, inventario/historial, comprobantes y revocación de sesión.

Android: `assembleDebug testDebugUnitTest` pasó usando JDK 21 instalado y
`-I tmp/vps-debug.init.gradle`, que fija solo durante la compilación los orígenes
API/Web públicos a `https://bajastack.network`. No se cambió `local.properties`,
la firma ni la configuración de release. Se verificaron ambos campos generados.
Resultado XML: 345 casos, 344 aprobados, un HTTP opcional omitido sin fixture,
cero fallos/errores. APK debug en `app/build/outputs/apk/debug/app-debug.apk`,
SHA-256 `73a6287a016a52ae29f2327ef6020307a0c508bf7bc03b8d4ddcd2d90c7a0914`.
Una compilación normal vuelve a tomar los orígenes privados actuales, que aún
apuntan a otro destino. El script temporal y APK no se versionan.

No hay dispositivo conectado: falta instalar ese APK y aceptar login/roles,
venta/cobro, consulta de inventario y recuperación de intentos en tablet.
No usar ventas reales como fixtures. Para probar escrituras, acordar un entorno
aislado y datos sintéticos. Correo y contraseñas nuevas quedan aplazados por
decisión de Pedro. No se cambió ni retiró Supabase.

## Separar el proxy antes del segundo proyecto

Este es un plan pendiente, no una descripción de servicios ya desplegados:

1. Guardar Caddyfile y estado TLS/config de los volúmenes actuales en un respaldo
   privado verificado; registrar imagen y configuración de rollback. Los seis
   lotes SQL/imágenes existentes **no** incluyen certificados. Explicar la breve
   interrupción HTTPS al transferir 80/443 y acordar la ventana antes de recrear.
2. Crear Compose `platform-proxy` bajo `/srv/proxy`, conectado exclusivamente a
   `platform_proxy`, con 80/443 públicos. Referenciar los volúmenes existentes
   como externos por nombre exacto, sin borrarlos ni permitir dos escritores.
3. Convertir `web` de Vivero en entrada HTTP interna `:80`: conserva frontend
   compilado y rutas API; quitar sus puertos públicos y montajes TLS/config.
   El Caddy externo reenvía el dominio al alias `vivero-dulcinea-proxy:80`.
   Así el servidor Web puede actualizarse sin detener HTTPS de otras apps.
4. Detener el dueño anterior de los puertos antes de arrancar el nuevo proxy;
   comprobar certificado, redirección HTTPS, rutas profundas, API, login/logout
   y permisos. Ante fallo, detener el nuevo proxy y restituir la configuración
   anterior sin modificar ni restaurar la base de datos.
5. Adaptar el respaldo de Vivero para pausar solo sus escritores internos.
   Respaldar el proxy por separado y registrar su propietario independiente.
   Comprobar que parar Vivero no interrumpe un servicio de prueba del proxy antes
   de dar de alta el segundo proyecto. No añadir otro panel.

Supabase se conserva hasta aceptación Web y Android, conciliación de intentos
anteriores y cierre de pendientes de acceso, correo y operación. Las pruebas
unitarias, un health correcto o un APK compilado no sustituyen esa aceptación.
