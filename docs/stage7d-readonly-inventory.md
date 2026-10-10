# Etapa 7D — inventario real de VPS y Android

9 de octubre de 2026. Captura principal VPS: **2026-10-10 02:28:13 UTC**. **NO-GO para despliegue y actualización de teléfonos.** El inventario de sólo lectura autorizado quedó realizado; no convierte los pendientes de conservación, respaldo o entrega en aprobaciones.

## 1. Identidad y alcance

Antes de SSH se consultaron lista y detalle mediante el conector Hostinger existente: VPS **2030059**, `srv2030059.hstgr.cloud`, IPv4 `179.236.238.111`, KVM 2, estado running. Coincide con la documentación local. SSH confirmó hostname `srv2030059`, Ubuntu **24.04.5 LTS**, usando llave existente y huella ED25519 conocida `SHA256:/WRISCh2bIoTLHSRwzQY8niIR+z46J6Y7789J9Y8RC8`. Se utilizó BatchMode, StrictHostKeyChecking, UpdateHostKeys=no y selección ED25519. No se cambiaron permisos, credenciales, known_hosts ni configuración SSH.

Se consultaron metadatos Docker, rutas/montajes, recursos, versiones, archivos de migración por hash y marcadores SQL. La conexión SQL fue a `vivero-vps-db-1`, con transacción READ ONLY y únicamente VERSION, schema_migrations y parámetros de servidor. No se consultaron usuarios, clientes, ventas, inventario, hashes de contraseña o grants. La credencial del motor se utilizó dentro del contenedor sin imprimirse; no se mostraron archivos `.env` ni llaves privadas.

Los scripts Python se enviaron por stdin a `python3 -B -`; no se guardaron scripts ni evidencia en el VPS. Health se consultó con GET HTTPS y validación TLS normal, mostrando sólo status HTTP. No se ejecutaron login ni operaciones comerciales, backup, migrador, Caddy reload/validate, compose up/down/restart/pull, Docker run, despliegue o limpieza. No se inició ni reinició ADB: se consultó su servidor ya activo y luego el dispositivo por serial explícito.

## 2. Servicios y proyectos reales

Motor Docker **29.8.2**, Compose **5.6.0**, kernel `6.8.0-142-generic`. Inventario total: **15 contenedores, nueve running y seis exited**.

| Proyecto / contenedores activos | Estado | Puertos publicados |
| --- | --- | --- |
| `platform-proxy` / `platform-proxy-proxy-1` | running, Caddy 2.11.6; sin health check Docker declarado | 80/TCP, 443/TCP y 443/UDP públicos |
| `vivero-vps` / `vivero-vps-web-1` | running; sin health check Docker declarado | Ninguno; Caddy HTTP interno |
| `vivero-vps` / `vivero-vps-api-1` | running, healthy; Node 24.21.0 | Ninguno; 3001 interno |
| `vivero-vps` / `vivero-vps-db-1` | running, healthy; MariaDB 11.4.13 | Ninguno; 3306 interno |
| `catering-oculto` / `catering-oculto-app-1` | running, healthy; release `20261008-simple-flow-v2` | Ninguno; 3000 interno |
| `catering-oculto` / `catering-oculto-db-1` | running, healthy; imagen MariaDB 11.4 | Ninguno |
| `vivero-acceptance-20261003` / API, Web, DB | running; API y DB healthy | Sólo Web `127.0.0.1:38003` |

Contenedores exited: `catering-oculto-migrate-1`, tres de `catering-oculto-review` y dos de `vivero-restore-check-20261003`. Algunos conservan health histórico unhealthy; **no son fallos actuales de los servicios productivos activos**. Una imagen de contenedor detenido no se pudo inspeccionar por ID; se conserva el ID registrado en el contenedor. No se descargó/recreó ni eliminó nada para completar metadatos de ensayos antiguos.

Compose ls presenta cuatro proyectos activos: Catering, proxy, aceptación Vivero y producción Vivero. Los dos proyectos detenidos adicionales se identificaron por labels. El ensayo de aceptación continúa activo con su propia DB/red/volúmenes: no confundirlo con producción ni detenerlo por cuenta propia.

## 3. Separación del proxy confirmada

**Confirmada realmente en 7D**, mediante contenedores, puertos, montajes, red, aliases y overlay vigente:

- Dueño exclusivo de 80/443 públicos: `platform-proxy-proxy-1`, Compose `/srv/proxy/compose.yaml`. Imagen fijada `caddy:2.11.6-alpine@sha256:c776e0c6413b544d0459665e54ec7b8b2a15000c0cbee8b254da0067b1d184ff`.
- Montaje efectivo de configuración HTTPS: `/srv/proxy/platform.Caddyfile` → `/etc/caddy/Caddyfile`, sólo lectura. Rutas seleccionadas: Vivero y dominio anterior → `vivero-dulcinea-proxy:80`; Catering → `catering-oculto-web:3000`.
- Estado del proxy: `platform-proxy_config` → `/config`; certificados `vivero-vps_caddy_data` → `/data`. El volumen TLS conserva prefijo Vivero, pero pertenece al uso del proxy compartido; no borrarlo ni tratarlo como descartable al actualizar Vivero.
- Web Vivero monta únicamente `/srv/apps/vivero-dulcinea/ops/Caddyfile.http` → `/etc/caddy/Caddyfile`; no monta los volúmenes TLS compartidos. Atiende HTTP interno y conserva rutas Vivero/legacy hacia `api:3001`.
- Overlay real `/srv/apps/vivero-dulcinea/ops/compose.host.yaml`: comprobadas eliminación de puertos (`!reset []`), sustitución de montajes (`!override`), archivo HTTP interno, red externa `platform_proxy` y alias de Vivero.

Red `platform_proxy`: sólo proxy, Web Vivero y app Catering conectados. No conecta las DB. Red interna `vivero-vps_database`: sólo API Vivero y DB Vivero. Red `vivero-vps_api`: sólo API/Web Vivero. Red interna `catering-oculto_database`: sólo app y DB Catering.

**Es viable actualizar exclusivamente API/Web Vivero sin reiniciar el proxy HTTPS ni Catering**, conservando proyecto, overlays, aliases y volúmenes activos. Es conclusión de topología, no un despliegue ensayado ni autorización. La Web interna de Vivero sí se recrearía al actualizar su imagen y Vivero requiere mantenimiento. No sustituir el overlay real por el local antiguo, que conserva 80/443 y montajes TLS.

## 4. Estado de Vivero, imágenes y MariaDB

Release resuelta: `/srv/apps/vivero-dulcinea/current` → `/opt/vivero/releases/20261002-initial`. Catering resuelve a `/srv/apps/catering-oculto/releases/20261008-simple-flow-v2`.

| Servicio | Imagen / ID efectivo |
| --- | --- |
| API Vivero | `vivero-vps-api`, `sha256:a63e49422463c1261dd677e16236c8b7d2f531bb28356f3f597ae25416e36b9d` |
| Web Vivero | `vivero-vps-web`, `sha256:9534ad0c5276e2fb93d255f4b497ed8b800ca79496f3c01422eb9ccdac85b5a0` |
| DB Vivero y DB Catering | `mariadb:11.4`, `sha256:1292844148b311e4ed4300022a996d39083f415a963e970cf47cad1b3b18e3a6` |
| App Catering | `catering-oculto-app:20261008-simple-flow-v2`, `sha256:ca2dcd1177d9c73e2fd9b0b2c0860ad922d1b5316e08a974cd639ac07745c0fe` |

Compartir imagen MariaDB no significa compartir instancia ni volumen. Volúmenes productivos Vivero: `vivero-vps_mariadb_data` y `vivero-vps_catalog_images`. Catering: `catering-oculto_db_data` y `catering-oculto_media`. Montajes privados de Catering se identificaron sólo por metadatos; su contenido no fue leído.

SQL confirma MariaDB **11.4.13**, UTC `+00:00`, utf8mb4/utf8mb4_unicode_ci y `event_scheduler=OFF`. SQL mode consultado: STRICT_TRANS_TABLES, ERROR_FOR_DIVISION_BY_ZERO, NO_AUTO_CREATE_USER, NO_ENGINE_SUBSTITUTION.

**28 marcadores aplicados, 002_identity a 029_inventory_imports, sin lagunas en esa secuencia.** Faltan:

1. `030_purchase_draft_retirement.sql`.
2. `031_short_folios.sql`.
3. `032_pending_sale_cancellations.sql`.

Los 28 archivos de migración de la release activa coinciden con los locales después de normalizar BOM/finales de línea. Los hashes crudos 028/029 difieren, pero su contenido normalizado coincide. Esto confirma archivos y marcadores, **no una auditoría de todos los objetos DDL efectivos ni de datos productivos**; no se aplicó ni repitió una migración histórica.

La API/Web de etapas 2–7A **no están desplegadas**: el ID API local candidato 7A es distinto del remoto. Health verde del software anterior no prueba folios/cancelaciones/comprobantes nuevos ni permite instalar la candidata sobre un backend todavía 029. Antes de producción se requiere el orden de migraciones → API → Web, con backup/restore y mantenimiento autorizados.

Los labels Compose tienen rutas históricas distintas por servicio: API utiliza base/vps + overlay host; DB conserva `compose.private.yaml` del arranque anterior y montajes bajo `/opt/vivero/releases/20261002-initial`. No ejecutar un up genérico ni recrear DB para uniformarlos. Preparar configuración fusionada/manifest de entrega que preserve DB/volúmenes/seed de producción y overlay HTTP real antes de autorizar cambios.

## 5. Health, recursos y riesgos para Catering

GET HTTPS, sin desactivar verificación de certificados, devolvió **200** en:

- `https://viverodulcinea.bajastack.network/health`.
- `https://bajastack.network/health`.
- `https://cateringoculto.bajastack.network/health/ready`.

No se consultaron sus cuerpos ni se verificaron login/cobro/operaciones reales. Proxy y Web carecen de health check Docker; los GET son una observación puntual, no monitorización continua.

Recursos puntuales: dos CPU; 7940 MiB RAM total, 6596 MiB disponible; sin swap; aproximadamente **89 GiB libres**, 8 % de disco ocupado. Carga 0.14/0.17/0.17, uptime siete días. Docker registra 24 imágenes, 15 volúmenes y caché de build de 2.963 GB. No se hizo prune/limpieza ni se evaluó carga máxima/backfill productivo.

Riesgos compartidos restantes: detener/recrear `platform-proxy`, alterar su Caddyfile/TLS/red externa, tomar 80/443 nuevamente con Vivero, un down/prune global o consumir recursos de todo el host. La actualización acotada a Vivero puede evitarlos. El backup oficial de Vivero pausa sus API/Web, **no el proxy independiente ni Catering**, si usa el proyecto y overlay efectivos confirmados. No se ejecutó backup para demostrarlo en esta etapa.

Segundo cotejo remoto: los **15 contenedores** conservaron ID, imagen, estado, fecha de arranque y contador de reinicios respecto del primer inventario. No se ejecutó ninguna operación de escritura sobre VPS.

## 6. Android por dispositivo

Un dispositivo conectado en ADB: **Samsung SM-A225M**, Android **13 / API 33**, estado `device`. Es el mismo modelo autorizado en el historial; se confirmó su identidad mediante la lista del servidor ADB existente antes de consultar, y cada comando posterior usó su serial explícito. El serial queda en evidencia privada ignorada, no en este informe. No apareció otro teléfono ni emulador en esa lista.

| Campo de la instalación Vivero observada | Resultado real |
| --- | --- |
| Paquete | `com.intutec.viveroapp.vpsvalidation` |
| versionCode / versionName | **2 / 1.0.1** |
| minSdk / targetSdk | 24 / 36; Android 13 cumple mínimo |
| Variante observable | DEBUGGABLE |
| Primera instalación / actualización | `2026-10-03 12:31:17`, tiempo presentado por el dispositivo |
| Instalador | `com.google.android.packageinstaller` |
| Estado usuario 0 | installed=true, hidden=false, suspended=false, stopped=true, enabled=3 (**deshabilitada por usuario**) |
| Firma disponible en dumpsys | PackageSignatures versión 2, un identificador interno `b6a8cdad`, sin firmas pasadas listadas |

El identificador interno de PackageSignatures **no es una huella SHA-256 del certificado**. `pm get-app-links` no proporcionó certificado y la ayuda de package no expuso una consulta directa de firma aplicable. Por tanto la firma criptográfica instalada permanece **sin verificar**. No se copió el APK instalado ni se accedió a almacenamiento privado para completar ese dato.

`com.intutec.viveroapp` **no aparece instalado** en la enumeración actual. También se encontraron `.readiness.test`, `.test` y `com.intutec.viveroappcliente`; sólo se anotaron nombres, sin abrir ni inspeccionar funcionalmente AppCliente ni limpiar paquetes de pruebas. La presencia de los paquetes de test no acredita pruebas aprobadas ni determina qué ocurrió con datos anteriores.

No se abrió, habilitó, detuvo, instaló, actualizó o desinstaló ninguna app; no se leyó Room, archivos privados, datos/cachés ni operaciones pendientes. El estado stopped/deshabilitada se dejó intacto. Tampoco se pidió una nueva autorización USB ni se cambió depuración/ADB.

## 7. Comparación con candidata 7A

Candidata conservada: `tmp/integration-stage7a/apk/ViveroApp-etapa7a-candidata-debug.apk`, paquete **`com.intutec.viveroapp`**, code **9**, versión **1.0.8-vps**, min 24/target 36, debug. Huella previamente comprobada y conservada: `54aae00dc34e2e48e287effbb513afb47e448e3e83470ccecb4b42851acf9cb6`. SHA-256 del archivo sigue `54b363af7ca79d8e48f37ed2b79806bd4fbda3fe4bec01654e2d101a8b99f86e`; destino compilado documentado: VPS Vivero HTTPS. No se generó ni instaló otro APK.

- **Paquete incompatible como actualización de la instalación observada:** principal y `.vpsvalidation` son distintos. Aunque coincidieran las firmas, no reemplazaría esa app ni migraría Room/pendientes; sería otra instalación con almacenamiento separado.
- Firma instalada: compatibilidad **no determinada** por los metadatos disponibles. No atribuirle el certificado de una copia local antigua sin cotejar el código instalado real.
- Próxima entrega del paquete principal: **10 continúa como propuesta**, superior a la candidata/referencia local 9. El code 2 pertenece a otro paquete y no fija el contador de versiones del principal. Antes de fijarlo, inventariar los demás equipos operativos o confirmar que este es el único; verificar máximo instalado del paquete elegido y su firma.
- No elegir code 3 ni cambiar applicationId para convertir esta entrega en una actualización de `.vpsvalidation`. La decisión de paquete, custodia de firma y transición de datos exige una revisión específica fuera de este inventario.
- No hay autorización de build, cambio Gradle o distribución. El gate de entrega que exige versión superior sigue rechazando repetir code 9 sobre la referencia local 9.

Para conservación siguen faltando: versión SQLite real y pendientes (no leídos), conciliación del servidor/origen/sucursal, copia/exportación recuperable autorizada cuando corresponda y ensayo representativo de actualización compatible. Room local sigue versión 6 con migraciones explícitas y backups Android excluidos según 7C; eso no prueba el estado real del teléfono. No desinstalar para resolver paquete/firma distintos ni habilitar la variante para que reprocese pendientes desconocidos.

## 8. Bloqueantes y checklist GO/NO-GO

| Gate | Resultado 7D | Pendiente |
| --- | --- | --- |
| Identidad VPS / inventario | Aprobado para esta consulta | Manifiesto/configuración fusionada de la futura release |
| Separación proxy / Catering | **Confirmada** | Preservar overlay/aliases/TLS y comprobar continuidad durante una ventana autorizada |
| Salud actual | Aprobada puntualmente | No sustituye pruebas de la versión nueva ni aceptación |
| Esquema productivo | 029 confirmado, 28 marcadores y fuentes históricas normalizadas coincidentes | 030/031/032 todavía sin aplicar; auditoría DDL/volumen real y plan de migración/restauración |
| Backup recuperable | Evidencia local 7C conservada, no repetida aquí | Toni: lote completo real, suplementos/configuración, copia cifrada externa y restore desde descarga; requiere autorización de acciones nuevas |
| Android | Equipo y versión identificados | Otro paquete, firma SHA-256 pendiente, variante deshabilitada; no hay prueba de Room/pendientes |
| APK | Artefacto anterior preservado | Paquete/firma compatibles, versión superior definitiva y transición/conservación revisadas |
| Aceptación | Pendiente | Cámara, Black Pos, tablets/red real y Pedro/Toni; CENTRO no se habilita |

**NO-GO global mantenido.** Se cierra la incertidumbre sobre separación actual del proxy y versión base productiva. No se autoriza desplegar por haberlas confirmado.

## 9. Validaciones, evidencia y control de cambios

Ejecutado: conector Hostinger lista/detalle; huella local/SSH estricto; hostname/OS; docker ps/inspect selectivo/compose ls/network y volume inspect; versiones; df/free/nproc/uptime/docker system df; SQL READ ONLY de marcadores/parámetros; hashes de migraciones; GET health; consulta al ADB existente; getprop, pm list packages, dumpsys filtrado, pm get-app-links, pm path y ayuda package. Estos últimos son sólo metadatos; ninguna instrumentación o actividad iniciada.

Evidencia ignorada `tmp/stage7d/`: inventarios VPS JSON, comparación de hashes/marcadores, `vps-preservation.json`, metadatos Android, baseline/preservación Git. Las rutas APK y serial se guardaron privadamente. Hubo errores del harness de lectura al manejar contenedores sin campo Health y una imagen antigua no disponible; se corrigieron únicamente herramientas temporales y se registró el límite, sin reparar ni cambiar el servidor. Las consultas Windows iniciales en sandbox no veían ADB; fuera de esa restricción se comprobó su proceso y se consultó el servidor ya activo. No se inició un daemon para resolverlo.

Único archivo nuevo de entrega: **`docs/stage7d-readonly-inventory.md`**. Código Android, backend, SQL, configuración operativa y Web intactos. Baseline: 71 caminos App + 44 Web = **115 preexistentes**, incluidos los 111 iniciales y cuatro documentos 7B/7C. Se comprobaron bytes/ausencias, rama, HEAD e índice; `git diff --check` correcto. Ambas ramas siguen main, HEAD App `a58fd614b28920d7b39fee7cef2b67f1c16b0531`, Web `5f9c4853e42cace586cdaeec13998668b3d204aa`. Sin staging, commit, push, reset, limpieza o cambios de rama. No se actualizó n8n ni se modificó Catering.

No se repitieron builds/suites/ensayos: no cambió código ni está autorizada esta etapa para pruebas que escriban. No hubo migraciones, backup, despliegue o modificaciones del VPS/teléfono.

## 10. Siguiente acción mínima y segura

Revisar con Pedro/Toni qué paquete debe conservarse como instalación operativa y qué otros equipos faltan por inventariar. Para verificar firma sin datos privados, solicitar permiso acotado de **lectura del APK instalado sólo para calcular certificado**, sin instalación, habilitación ni Room. Por separado, acordar revisión de pendientes/copia privada recuperable y transición entre paquetes; no usar reenvíos para descubrir su estado.

Toni puede organizar destino externo/custodia y ventana propuesta; la captura real de backup/suplementos/restauración exige una autorización nueva. Preparar después el manifiesto exacto que conserve overlay productivo/DB/proxy y orden 030→031→032→API→Web. No incrementar versión ni construir APK hasta resolver inventario/firma/paquete. Detenerse al entregar este informe para revisión.
