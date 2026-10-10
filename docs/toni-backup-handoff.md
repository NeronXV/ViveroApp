# Guía de respaldos para Toni

Revisión del 9 de octubre de 2026. Toni preparará automatización y destino externo.
No se configuró R2, programación, retención ni envío en este cierre.

## Estado verificado y límites

Consulta de solo lectura al VPS: ocho lotes con marcador COMPLETE en
`/var/backups/vivero`, cero entradas relacionadas con Vivero/Restic en el crontab
de root y cero timers correspondientes listados por systemd; configuración
`/srv/apps/vivero-dulcinea/shared/offsite.json` ausente. Esto no inspecciona
programadores externos o crontabs de otros usuarios. No acredita una copia externa.

Los scripts existentes son manuales y no borran lotes ni snapshots. Las políticas
diaria/semanal/mensual de la documentación son propuestas, no retención instalada.
La conexión SSH de Toni con la llave disponible en esta computadora fue rechazada;
Toni debe comprobar su propia llave y permiso antes de programar operaciones.

Esta guía usa el wrapper y scripts como autoridad. Corrige dos imprecisiones de
guías anteriores: `offsite-backup.py` **no descarga/restaura**; admite init/upload/check,
y restore extrae con `--no-same-owner --no-same-permissions`.

## Scripts y rutas

| Repositorio | Función / ruta operativa |
|---|---|
| `infra/docker/backup.mjs` | Dump SQL + imágenes; validación con `--verify` |
| `infra/docker/restore.mjs` | Restaura únicamente en un destino vacío verificado |
| `infra/host/viveroctl.sh` | `/srv/apps/vivero-dulcinea/ops/viveroctl.sh`; wrapper de operaciones |
| `infra/host/offsite-backup.py` | Subida cifrada manual Restic a SFTP externo o S3 HTTPS |
| `infra/host/offsite.config.example.json` | Plantilla privada; no obliga a elegir R2 |
| `backend/test/backup.test.js` | Pruebas simuladas de integridad, guardas y reanudación |

Fuentes desplegadas: `/srv/apps/vivero-dulcinea/current/ViveroApp` mediante enlace
al release. Configuración oficial: `/srv/apps/vivero-dulcinea/shared/.env.vps`.
Overlay del host: `/srv/apps/vivero-dulcinea/ops/compose.host.yaml`.
Lotes: `/var/backups/vivero/vivero-*`; verificar enlaces reales antes de operar.

## Contenido incluido y excluido

Incluye `database.sql` de la BD vivero con rutinas, triggers y eventos, e imágenes
de `/data/catalog-images` en `catalog-images.tar.gz`, manifest con SHA-256 y COMPLETE.
Son datos privados de operación y hashes de cuentas: cifrar al salir del servidor.
Pausa API/Web, comprueba que solo DB continúa activa, realiza el dump y reanuda los
servicios que estaban activos incluso si el respaldo falla. Hay una interrupción
operativa; elegir una ventana con Pedro/Toni. Nunca copiar `/var/lib/mysql` en caliente.

No incluye fuentes/bundle Web/APK, Compose/Caddyfile, configuración privada,
certificados/estado de Caddy, llaves SSH/Android ni datos locales Room/localStorage.
Conservar fuentes y versión exactas y configuración en otra copia cifrada.
La llave de descifrado debe estar custodiada **también fuera del VPS** y separada
de las credenciales de acceso al repositorio de respaldo. No enviar secretos por chat.

## Crear y comprobar un lote

```sh
sudo /srv/apps/vivero-dulcinea/ops/viveroctl.sh backup --acknowledge-downtime
sudo /srv/apps/vivero-dulcinea/ops/viveroctl.sh verify-backup /var/backups/vivero/LOTE
```

Sustituir LOTE por un directorio real `vivero-*`. Conservar stdout/estado seguro;
no volcar SQL ni archivos privados. COMPLETE e hashes válidos no sustituyen restore.
Un lote INCOMPLETE se conserva para diagnóstico y no se sube como copia recuperable.

## Copia cifrada fuera del VPS

Toni debe aportar destino independiente (SFTP o S3 HTTPS), titular/responsable,
capacidad, credencial limitada y custodia de la contraseña de cifrado. Preparar
Restic y archivos de root modo 600 en `shared`; comprobar los permisos del script.
No usar un repositorio en otro directorio del mismo VPS como copia externa.

Con configuración real revisada, el script existente ofrece:

```sh
python3 /RUTA/AL/offsite-backup.py init --acknowledge-new-repository
python3 /RUTA/AL/offsite-backup.py upload /var/backups/vivero/LOTE
python3 /RUTA/AL/offsite-backup.py check
```

init solo corresponde a un repositorio nuevo. El script valida el lote antes de
subirlo y `check` ejecuta `restic check --read-data`; no hace forget/prune. La ruta
de instalación del helper y permisos/sudo de Toni requieren preparación administrativa.

## Restauración desde la copia externa

1. En otra máquina o proyecto aislado, recuperar un snapshot real con Restic usando
   configuración privada y su contraseña. El helper actual no implementa restore;
   el administrador usa Restic directamente y elige snapshot/target explícitos.
2. Verificar los hashes del lote descargado con `backup.mjs --verify`; registrar
   fecha, snapshot y hashes, sin contenido privado. Demostrar que la fuente es externa.
3. Preparar proyecto `vivero-restore-*` nuevo, credenciales propias, redes/volúmenes
   separados, cero puertos públicos y DB con esquema oficial sin seed operativo.
   Iniciar solo DB; API/Web e imágenes destino deben estar vacíos/detenidos.
4. El wrapper del VPS exige archivos privados bajo `/opt/vivero/maintenance/` y
   lote bajo `/var/backups/vivero/`. Copiar allí el lote recuperado si se ensaya en
   ese host, manteniendo evidencia de su procedencia externa. Ejecutar:

```sh
sudo /srv/apps/vivero-dulcinea/ops/viveroctl.sh restore-isolated \
  /opt/vivero/maintenance/ENSAYO/.env.restore \
  vivero-restore-ENSAYO \
  /var/backups/vivero/LOTE \
  /opt/vivero/maintenance/ENSAYO/compose.restore.yaml \
  --acknowledge-empty-target
```

Usar un nombre real en minúsculas; el script comprueba destino vacío y no borra
datos para hacerlo encajar. En otra máquina se puede usar `restore.mjs` directamente
con los mismos argumentos y Docker local. Revisar overlay y rutas antes de ejecutarlo.
Ante fallo mantener el destino aislado/detenido y conservar evidencia.

5. Comparar tablas/migraciones, cantidades de ventas/pagos/compras/movimientos,
   totales monetarios y saldos de inventario, FK/triggers e imágenes. Arrancar solo
   API del ensayo y comprobar salud/login/catálogo y flujos con datos sintéticos.
6. El respaldo externo se cierra solo tras **copia real externa y restauración real**.
   El ensayo histórico del 3 de octubre fue desde un respaldo del VPS, no offsite.

## Programación, retención y aviso de fallos

Toni elegirá ventana, frecuencia, retención y canal de aviso; hoy no están configurados.
Usar bloqueo exclusivo para evitar trabajos solapados. La automatización debe
comprobar por separado: exit code de backup, lote COMPLETE/hashes, servicios
API/Web reanudados y saludables, subida externa y frescura de la última copia.
Una salida SERVICE_RESTART_FAILED_CHECK_PROJECT requiere intervención inmediata.

Registrar solo fecha, fase, estado y antigüedad; enviar a Toni un aviso al canal
acordado si cualquier fase falla, si faltan lotes recientes o si restore falla.
El éxito de crear un lote no debe ocultar un fallo de subida. Probar el aviso con
un fallo sintético y confirmar su recepción. No hay mecanismo de aviso instalado.

Conservar lotes/snapshots actuales hasta acordar retención y demostrar restauración.
No habilitar limpieza automática ni forget/prune como parte de esta entrega.
