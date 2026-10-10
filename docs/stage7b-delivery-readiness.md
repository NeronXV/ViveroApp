# Etapa 7B — preparación segura de entrega

Revisión del 9 de octubre de 2026, zona America/Mazatlan. **Decisión: NO-GO para actualizar producción o instalar/distribuir la APK operativa en este momento.** GO únicamente para revisar esta propuesta y preparar las autorizaciones posteriores. Esta etapa no ejecuta el procedimiento.

Se revisaron los entregables de 7A, código/configuración locales, logs conservados, dump ficticio y APK ya generada. No se contactó Hostinger/VPS, no se utilizaron credenciales o datos productivos, no se inició Docker ni emulador y no se ejecutó adb. No se instalaron, desinstalaron ni actualizaron aplicaciones. No hubo desarrollo funcional, cambios de firma, migraciones, commit, push o despliegue. Sólo se añaden este informe y el inventario CSV; los archivos anteriores permanecen intactos.

## 1. Estado técnico y evidencia

[Informe 7A](stage7a-integration.md), [matriz](stage7a-integration-matrix.md), [SQL](stage7a-reconciliation.sql), [control](stage7a-change-control.md) y [plan anterior](stage7a-candidate-and-deployment.md) revisados. Sus pruebas se consideran **evidencia histórica de 7A**, no ejecuciones nuevas de 7B.

| Evidencia 7A revisada | Resultado y límite |
| --- | --- |
| Activity Android → Web real → API/MariaDB | Venta 90 VD-0086 única, pago único y salida de dos unidades; recuperación tras cortar respuesta después del commit y cierre/reapertura |
| Cancelación y pedido público | Auditoría única sin reposición indebida; VW-10003 vinculado una vez a VD-0090 y cobrado; concurrencia, permisos/sucursal y cancelación vinculada cubiertos por HTTP/SQL |
| Conciliación final | Cinco ventas, cuatro pagos/67500 centavos, una cancelación, devolución de 8500 con reposición de una unidad y corte de 60500 centavos esperado/contado, diferencia cero; stock 98/99/99 |
| Backend | 89 unitarias/check; 35 integraciones en instalación nueva y 35 después de actualizar; 9 pruebas de backup/restore |
| Web | 506 aprobadas, una HTTP optativa omitida; lint/build correctos |
| Android | 370 unitarias aprobadas, una HTTP optativa omitida; 56 instrumentadas aprobadas, dos de cámara omitidas; tres fases reales aprobadas antes del refuerzo del gate del harness; después tres omisiones intencionales antes de abrir Activity |
| PDF | 13 verificados, corto una página/largo tres; no equivale a Black Pos física |
| Restore | Igualdad de las 61 tablas/digests y de imágenes en destinos independientes; API/Web del destino quedaron apagadas: no acredita login/operación ni privilegios efectivos del API restaurado |

Se cotejaron nuevamente, sin DB en ejecución, `final-with-closing.json` y `restored-final-with-closing.json`: igualdad completa de los resultados guardados. Los fallos originales no se borraron: preparación de fixtures/selectores, sandbox, primer orden incorrecto de pruebas 032/031 y ejecución parcial en teléfono. Las pruebas HTTP optativas usan fixtures diferentes; su omisión no anula el recorrido real, pero no permite afirmar que toda la suite estuvo habilitada. No se repitieron suites ni compilaciones: esta entrega sólo analiza y documenta.

**Errata de 7A, sin sobrescribirlo:** el esquema tiene **31 archivos/marcadores, 002 a 032**, más `schema.sql`; no 32 migraciones. La versión final es `032_pending_sale_cancellations`. Coinciden el árbol SQL, el JSON conciliado (31 filas) y `backend/scripts/verify-local-install.js` (31 marcadores/61 tablas). Para GO comprobar nombres y contenido, no sólo un conteo.

## 2. Bloqueantes y decisiones

| Gate | Situación actual | Requisito de GO / responsable |
| --- | --- | --- |
| G0 Destino y versión exactos | Información VPS documental, no actualizada remotamente; fuentes dirty en ambos repositorios | Pedro autoriza inspección posterior; inventario vigente de proyecto/servicios/volúmenes, commits o manifiesto inmutable de fuentes/artefactos y estado SQL sin discrepancias |
| G1 Preservación de pendientes/dispositivo | Incidente Gradle sin inventario anterior; paquete físico observado en 7A distinto | Identificar cada paquete/firma/versiones y sus pendientes; conservar copia recuperable o conciliación suficiente, resolver ausencia/pérdida de información antes de instalar |
| G2 Proxy compartido | Web contiene Caddy y el backup actual detiene Web | Verificar todos los dominios dependientes y acordar impacto. Si otros proyectos dependen y no hay ventana autorizada, NO-GO: no detener/recrear Web ni separar proxy por cuenta propia |
| G3 Respaldo recuperable | Backup sólo `vivero` e imágenes; no cuentas/grants del motor ni configuración; sin copia externa acreditada | Lote coherente validado, versión de esquema/artefactos, permisos/DEFINER recuperables, copia cifrada fuera del VPS y restore real desde ella con integridad + API funcional y privilegios mínimos |
| G4 Migraciones | Se ensayó 030→031→032, instalación nueva y restore del esquema final; DDL no globalmente reversible | Copia representativa autorizada en esquema exacto de origen, ausencia de DDL parcial/lagunas; aplicar sólo faltantes en orden y reconciliar históricos/permisos |
| G5 API/Web | Software local comprobado; `/health` no identifica release ni todas las invariantes | IDs de imágenes/bundle y configuración exactos, health 200 + compatibilidad de contrato + consultas/invariantes; prueba de humo acordada |
| G6 APK | Debug, code 9 igual a referencia 6; firma release no configurada aquí | Paquete/certificado de cada instalación compatible, versión superior para entrega, origen verificado, restauración/persistencia y permisos probados; autorización específica de instalación/publicación |
| G7 Aceptación operativa | Cámara, Black Pos, tablets físicas y revisión Pedro/Toni pendientes | Pruebas presenciales controladas y registradas; SMTP/acceso resueltos si el piloto depende de invitaciones/recuperación |

G0–G7 no están cerrados por este informe. Un health verde o PDF correcto no convierte estos pendientes en GO. No se encontró otra falla comercial confirmada en 7A; los bloqueantes actuales son de liberación, conservación y recuperación.

## 3. Incidente Gradle y conservación de Android

El log `tmp/integration-stage7a/android-regression.log` registra pruebas iniciadas en SM-A225M además del emulador y fallos de Compose. El parámetro usado en Gradle no filtró dispositivos. Se interrumpió la corrida; la detención del teléfono fue inicialmente rechazada por revisión automática y después autorizada por Pedro. Esto no se reclasifica como validación aprobada ni se utiliza como autorización para 7B, que vuelve a prohibir instalaciones físicas.

Según la consulta histórica posterior, existía `com.intutec.viveroapp.vpsvalidation`, 1.0.1/code 2, actualizado el 3 de octubre; no se encontró `com.intutec.viveroapp`. No se dispone de inventario previo para demostrar si el paquete principal existía ni si sus datos se conservaron. La limpieza automática de una herramienta de pruebas debe considerarse un riesgo; la ausencia del paquete no demuestra por sí sola qué ocurrió. No prometer recuperación de datos perdidos ni reinstalar para investigarlo.

Antes de una instalación posterior autorizada:

1. Inventariar **sólo lectura**, con serial explícito: paquetes de Vivero, versión/code/minSdk, versión Android, fecha de instalación/actualización y APK/splits instalados. No consultar ni registrar contraseñas/tokens. No ejecutar tareas Gradle `connected*` con dispositivos operativos conectados; `ANDROID_SERIAL` o argumentos de instrumentación no sustituyen selección/verificación real.
2. Conservar el APK/base y splits de la instalación autorizada y verificar sus certificados con `apksigner`. Comparar **paquete y conjunto de firmantes**, no nombre/icono. El verificador existente acepta un firmante único; una rotación o firma múltiple necesita análisis explícito, no bypass.
3. Consultar los pendientes desde la app y servidor autorizado sin recrearlos: folio, usuario/sucursal, estado/clave original y resultado de venta/pago/inventario. Nunca usar datos sintéticos/locales como si fueran pendientes del VPS. Si hay incertidumbre o falta información, detener la actualización y conciliar con Pedro/Toni.
4. Room es `vivero.db`, versión 6, con migraciones explícitas 1→2→3→4→5→6 en `DatabaseModule`; no hay fallback destructivo en ese constructor. 2–6/7A no cambiaron su esquema. Esto no prueba la versión real de cada teléfono.
5. `allowBackup=false` y las reglas excluyen nube y transferencia de archivos/bases/preferencias: **no confiar en Google Backup, transferencia de teléfono o adb backup para salvar Room**. Con autorización posterior para datos privados y si la app es debuggable y permite `run-as`, acordar parada coherente y copia cifrada de almacenamiento pertinente, incluida DB y WAL/SHM, archivos y preferencias; comprobar esa copia en un entorno separado. No copiar SQLite en caliente, usar root/bypass, volcar sesiones al chat ni prometer acceso a release no debuggable. Si no existe copia/exportación segura y quedan operaciones sin conciliar: NO-GO.
6. Ensayar primero el mismo paquete/firma y migración sobre instalación representativa aislada. Sólo después, con autorización de ese dispositivo, actualizar conservando datos (`install -r` o instalador normal compatible); nunca `uninstall`, `pm clear`, downgrade forzado o cambio de applicationId. Comprobar pendientes y cantidades antes/después, reapertura y resultado original sin nuevo cobro.

La variante `.vpsvalidation` y la principal tienen almacenamientos separados. Instalar la principal no migra los datos de la variante ni la reemplaza; un versionCode mayor no soluciona esa diferencia. No se propone renombrar paquetes ni crear una migración de datos en 7B.

## 4. APK candidata — verificaciones nuevas, locales

Archivo conservado: `tmp/integration-stage7a/apk/ViveroApp-etapa7a-candidata-debug.apk`. Verificado con `apksigner verify --print-certs`, `aapt2 dump badging`, SHA-256 y lectura de los DEX del propio archivo, sin ejecutarlo.

| Campo | Resultado |
| --- | --- |
| Paquete | `com.intutec.viveroapp` |
| Versión | `1.0.8-vps`, code 9 |
| SDK | min 24 (Android 7.0), target 36, compile 37 |
| Variante | debuggable; certificado Android Debug, no release definitiva |
| Certificado SHA-256 | `54aae00dc34e2e48e287effbb513afb47e448e3e83470ccecb4b42851acf9cb6` |
| APK SHA-256 | `54b363af7ca79d8e48f37ed2b79806bd4fbda3fe4bec01654e2d101a8b99f86e` |
| Destino | DEX contiene `https://viverodulcinea.bajastack.network`; no contiene el proxy de ensayo `http://10.0.2.2:33034`. No se contactó ese dominio ni se verificó su HTTPS actual |
| Permisos efectivos | INTERNET, CAMERA, ACCESS_NETWORK_STATE y permiso propio DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION; cámara opcional. No aparecen permisos de ubicación ni almacenamiento en badging |
| Protección/diferencias debug | Reglas de backup restrictivas; tráfico claro sólo permitido para loopback de debug; actividades de prototipo exportadas en manifest debug. Adecuada sólo como candidata controlada expresamente autorizada |

Se ejecutó el verificador **existente** `scripts/verify-android-apk.ps1`, contra la APK de etapa 6, con versión esperada 9, SDK absoluto y `-AllowDebug`: **RECHAZADA por versionCode no superior**, ambas son 9. La firma y paquete sí coinciden. Hubo un primer intento con ruta SDK derivada del sandbox no disponible; al indicar el SDK real se obtuvo el rechazo de versión. No se cambió el script ni la versión para hacerlo pasar.

El `install -r` del emulador de 7A demuestra reemplazo técnico de la misma versión/firma y conservación del intento local, pero no cumple la regla de nueva versión de la entrega ni prueba un teléfono anterior. Para dispositivos con code inferior a 9, el gate puede ser distinto; no se conoce su inventario actual. `key.properties` no existe: no está acreditada aquí firma release. Antes de una entrega definitiva se debe usar la llave compatible existente, definir versionCode superior al máximo de dispositivos y validar `verifyReleaseConfiguration` y el artefacto final. No generar otra llave ni asumir que debug/release serán compatibles.

El APK `...ensayo-local-debug.apk` apunta al emulador; excluirlo de cualquier distribución. La Web candidata conservada `tmp/integration-stage7a/candidate/web-dist.zip` tiene SHA-256 `22c1e49b031ad54c621257c2e0b1e600bfa4184d659443c901c3292bb12dd090`, verificado ahora. El ID de imagen API que documentó 7A identifica lo ensayado, no su publicación en el VPS. Antes de construir otra candidata, volver a registrar hashes y entorno; no atribuir pruebas a un binario nuevo por compartir versionName.

## 5. Respaldo: contenido comprobado y faltantes

Autoridad: `infra/docker/backup.mjs`, `restore.mjs`, `infra/host/viveroctl.sh`, `offsite-backup.py`, `database/mysql/schema.sql`, `grants.sql` y migraciones. [Guía de Toni existente](toni-backup-handoff.md).

El dump utiliza `mariadb-dump --single-transaction --routines --triggers --events --hex-blob --databases vivero`. Pausa API/Web del proyecto, exige DB como único servicio activo, archiva `/data/catalog-images`, genera manifest/hash y COMPLETE y reanuda **los escritores previamente activos incluso al fallar**. `--verify` valida dos archivos/hash, no su capacidad de restauración.

| Elemento | Incluido / límite |
| --- | --- |
| Datos, DDL, índices, FKs, restricciones, marcadores | Incluidos en el dump de `vivero` |
| Roles/capacidades funcionales y sus asignaciones | Tablas de aplicación incluidas; no confundir con privilegios del motor |
| Triggers, procedimientos/rutinas y eventos de `vivero` | Flags incluidos; necesitan DEFINER existente y privilegios/versión compatibles |
| Imágenes | `catalog-images.tar.gz`, SHA-256; no incluye otros archivos de usuario fuera de ese volumen |
| Cuentas MariaDB, GRANT/privilegios, autenticación de `mysql.*` | **No incluidos** por un dump de una sola base. Requieren reconstrucción auditada mediante bootstrap oficial y grants/migraciones de la versión del respaldo, más evidencia privada de cuentas/grants/DEFINER reales; no otorgar privilegios generales para sortear errores |
| Configuración, SQL modes/versión, imágenes de runtime, fuentes/bundle/APK, Caddy/config/TLS y llaves | No incluidos; conservar manifiesto y copias apropiadas aparte, cifradas cuando tengan secretos |
| Room/localStorage/diarios de navegador | No incluidos por el backup VPS; conservar y conciliar en su instalación/origen |

Lectura nueva del **dump ficticio final**: 61 CREATE TABLE, 13 triggers, un procedimiento, 14 cláusulas DEFINER, cero sentencias GRANT y sin tablas de cuentas del motor. No se imprime su contenido ni hashes de contraseñas. Los eventos están habilitados en el comando; no se supone que existan eventos a restaurar. Este resultado no certifica el contenido de un respaldo productivo futuro.

**Esquema de destino:** restore importa el SQL dentro de una DB ya inicializada y sólo exige ausencia de filas operativas. No garantiza borrar objetos posteriores ausentes del dump. Restaurar un backup 029 sobre bootstrap 032 puede dejar tablas/rutinas/grants posteriores y bloquear luego CREATE de 030–032. La prueba 7A fue 032→032. Preparar una DB vacía con `schema.sql`, seed de **sólo roles**, grants y migraciones de la **misma release/esquema del respaldo**; no inicializarla con esquema más nuevo por conveniencia. Verificar también conjuntos de objetos/grants, no sólo tablas del dump. No se ejecutó ese ensayo adicional en 7B.

**Ventana de mantenimiento:** backup reanuda escritores por diseño. Para una actualización no debe abrirse operación entre snapshot y migración: establecer mantenimiento coordinado, detener explícitamente los escritores antes de invocarlo y verificar que sigan detenidos después. En el esquema actual eso incluye Web/Caddy; si afectaría otros proyectos sin permiso, G2 es NO-GO. No alterar el script para aceptar escritores ni improvisar dump/imágenes en caliente.

### Restauración aislada propuesta, pendiente de autorización/ejecución

1. Elegir backup completo y release/esquema de origen; comprobar manifest/hash, credibilidad del lote, rutas y custodia de configuración/grants/llaves. Para usar datos productivos en un ensayo se requiere **autorización específica adicional**, incluso si se hace localmente; esta etapa no la concede.
2. Proyecto nuevo `vivero-restore-<identificador>`, credenciales propias, redes/volúmenes nuevos, sin puertos públicos ni seed demo/operativo. Arrancar sólo DB, imágenes destino vacías; API/Web sin arrancar. Deshabilitar salida externa y transportes de correo en el ensayo.
3. Inicializar exactamente el esquema previo del respaldo y cuentas técnicas necesarias, con sus privilegios mínimos/DEFINER. Usar el helper de restore de versión revisada (incluida corrección 7A para contador inicial). El wrapper actual deriva los scripts desde `current`: comprobar qué versión ejecutará; una ruta nueva en el informe no actualiza el helper desplegado.
4. Ejecutar `restore.mjs --env-file <env-privado-del-ensayo> --project <proyecto-nuevo> --backup-dir <lote-verificado> --overlay <overlay-aislado-revisado> --acknowledge-empty-target` en un runtime Node 24 con Docker local, o el wrapper revisado correspondiente. Esto es una plantilla **no ejecutada**, no un permiso para apuntar al proyecto operativo.
5. Comparar todas las tablas presentes, conjuntos de objetos/DDL, migraciones, FKs/orfandad, conteos y sumas exactas monetarias; stock y suma de movimientos, pares venta/pago, cancelaciones/auditoría, devoluciones/cortes, IDs, folios/aliases, secuencias y relaciones pedido/venta. Comparar hashes/cantidad de imágenes y lectura por el UID real del API.
6. Verificar cuentas/grants efectivos, invocación de triggers/rutinas con el usuario runtime (no root) y ausencia de permisos extra. Arrancar sólo el API aislado y probar health y login/catálogo con cuentas ficticias nuevas exclusivamente del ensayo; correo sin envío externo. Los datos restaurados se comparan por lectura, no se crean ventas sobre cuentas reales.
7. Ensayar faltantes 030/031/032 sobre esa copia exacta, con sus históricos representativos autorizados; medir tiempo y preservar importes, IDs, folios anteriores, pagos/inventario y permisos. Recuperar un error DDL sin reiniciar automáticamente el migrador. Conservar logs sanitizados y resultado firmado por responsable.

### Toni: evidencias que debe entregar

- Lote, fecha UTC, estado COMPLETE, hashes, exit code y esquema/release de origen; tamaño, suficiencia de espacio y API/Web saludables tras la ventana. Reportar también fallo de reanudación, no sólo dump exitoso.
- Destino **fuera del VPS**, titular/responsable y permisos limitados; copia cifrada realmente subida, snapshot externo identificable y contraseña de descifrado custodiada aparte y accesible a responsables. No elegir/configurar R2 por defecto.
- `offsite-backup.py` actual sólo permite init/upload/check; `check` usa Restic `--read-data`, no descarga. Recuperar el snapshot mediante Restic en otro destino y demostrar que el restore proviene de esa descarga, no del lote que permanecía en el VPS.
- Manifiesto complementario recuperable de configuración, grants/DEFINER, fuentes/artefactos y certificados, sin divulgar secretos. Comprobar integridad **después** de descifrar.
- Restore real según los pasos anteriores, resultados de SQL/imágenes/grants/API y responsable. Una subida o `restic check` solos no cierran G3.
- Frecuencia/ventana y RPO/RTO acordados, retención y capacidad documentadas; bloqueo de trabajos simultáneos; aviso probado para fallo de dump, INCOMPLETE/hash, servicios no reanudados, upload/check fallidos y última copia externa vencida. Evidencia de recibir el aviso y procedimiento de reintento que conserva lotes fallidos.

No hay timers/service de backup en `infra/host/`; los scripts locales son manuales y no implementan eliminación/retención. La guía anterior reportó sin programación/offsite; no se comprobó su estado actual. No asumir programadores externos, crontabs de otros usuarios ni una política instalada. No ejecutar prune/forget ni crear automatización en 7B.

## 6. Inventario VPS para inspección posterior

Información de partida **documental**, no mediciones nuevas: [operación multiproyecto](vps-multiproject-operations.md), [primer despliegue](backend-vps-first-deployment.md), [guía de Toni](toni-backup-handoff.md). La inspección posterior necesita permiso remoto explícito; no se han usado herramientas Hostinger ni SSH.

| Comprobar después | Referencia local / evidencia requerida |
| --- | --- |
| Identidad de host/proyecto | Dominio de la candidata y host autorizado; Compose `vivero-vps`; etiquetas reales y relación con registro `/srv/apps/projects.json` |
| Release y rutas | `/srv/apps/vivero-dulcinea/current`, `releases`, `shared/.env.vps`, `ops/compose.host.yaml`; resolver enlaces y comprobar release/hash sin imprimir env |
| Servicios y contenedores | `db`, `api`, `web`; nombres documentados `vivero-vps-{db,api,web}-1`; confirmar running/healthy, reinicios, logs y qué otros proyectos/dominos comparten proxy |
| Volúmenes | `vivero-vps_mariadb_data`, `catalog_images`, `caddy_data`, `caddy_config` con prefijo `vivero-vps_`; confirmar montajes/propietarios, no recrear ni renombrar |
| Red y puertos | DB privada; API no publicada; Caddy 80/443; redes de Vivero y `platform_proxy`; confirmar alias existentes y que otras DB no queden expuestas |
| API/Web reales | IDs/digests de imágenes, release y hash de assets; `/health` no devuelve número de versión, por tanto no identifica el binario por sí solo |
| MariaDB | Imagen y versión servidor real, arquitectura, zona UTC, charset/collation, SQL modes; 11.4 es referencia de Compose, no medición actual |
| Migraciones | Listado ordenado y objetos asociados; documentación previa sitúa producción antes de 030, sin asumirlo actual. Detectar DDL parcial aunque falte su marcador |
| Health/compatibilidad | DB, API, almacenamiento de imágenes, Caddy y endpoints; contratos con/sin cabecera de folio y recuperación desde origen anterior |
| Dominios/HTTPS | Dominio principal, dominio anterior y rutas `/caja`, `/login`, `/api/*`, assets de recuperación; DNS, SAN, vencimiento/cadena TLS y configuración de Caddy sin cambiar otro dominio |
| Capacidad | `df`/disco e inodos, tamaños DB/imágenes, espacio para dump/copia/restore/releases, memoria y duración estimada de 031; no reutilizar cifras históricas de GB como disponibilidad actual |
| Backups | Lotes COMPLETE/INCOMPLETE, manifest/hash/fechas, restore acreditado, snapshot externo, programación/retención/avisos y custodia de permisos/configuración |

Usar consultas acotadas y `docker compose config --quiet`, IDs/labels selectivos y versiones; no compartir `docker inspect`/`config` completos ni logs con secretos. Esta lista no autoriza cambios remotos. No cambiar nombres Compose, volúmenes, firewall, redes, DNS o proxy de otros proyectos.

## 7. Secuencia exacta de actualización propuesta

Debe conservar ViveroApp y ViveroWeb como hermanos dentro de una release inmutable. Compose usa **las tres configuraciones oficiales** (base, VPS y host), el mismo proyecto `vivero-vps` y el env privado confirmado. No usar overlays/puertos/fixtures del ensayo ni cambiar `current` esperando que eso cambie contenedores.

| Paso | Acción después de autorizaciones correspondientes | Punto de decisión |
| --- | --- | --- |
| 0 | Revisar commits propuestos, dejar manifiesto de fuentes/SQL/APK/bundle y runtime exactos, secretos excluidos; construir/validar artefactos sin afectar servicios | G0: no desplegar un checkout sin identificar, ni intermediarios no probados |
| 1 | Inspección remota autorizada del destino, versiones, arquitectura, recursos y dependencias Caddy | G0/G2: coincidencia inequívoca; discrepancia detiene ejecución |
| 2 | Acordar mantenimiento, impacto de Caddy, responsables, duración, canal de aviso, RPO/RTO y umbral de abortar; conservar diarios y conciliar pendientes | G1/G2: si otro proyecto se afecta sin ventana autorizada, parar plan |
| 3 | Crear/verificar copia externa previa y restore acreditado con release/esquema de origen, grants y configuración | G3: falta de recuperación comprobada impide entrar a ventana |
| 4 | Entrar en ventana, impedir nuevos envíos/escritores, drenar solicitudes; detener servicios específicos ya identificados sin `down`; registrar estado SQL/financiero y tomar **lote final coherente**. Backup no debe reabrir escritores | G3: verificar hashes y cifrar/subir lote final; medir delta frente al restore acreditado. Si no es representativo o hay dudas, restaurar este lote antes de migrar |
| 5 | Con DB activa y escritores detenidos, usar migrador de la release candidata, perfil tools y env del destino revisado. Aplicar faltantes **030 → 031 → 032** si origen es 029; si 030, sólo 031→032; si 031, sólo 032. Otra secuencia/laguna requiere análisis previo | G4: marcadores + DDL + permisos + históricos conciliados. Un fallo detiene; no reintentar ciegamente |
| 6 | Actualizar únicamente API desde imagen candidata, sin recrear DB/volúmenes. Su health exige 032; API nueva no va antes de migraciones | G5: DB/API healthy, imágenes legibles, contrato corto/anterior y capacidades/sucursal correctos |
| 7 | Actualizar Web desde bundle/imagen identificados, conservar Caddyfile/montajes/certificados/dominos y origen antiguo de recuperación | G2/G5: validar Caddy antes de reload/recreación y comprobar sitios dependientes; revertir código si falla, sin destruir volúmenes |
| 8 | Pruebas de humo aprobadas: HTTPS/rutas/login, catálogo, pendientes, consultas de folio corto/anterior, comprobantes e inventario; escrituras sólo ficticias de ensayo o autorizadas expresamente y conciliadas | G5: health solo no basta; nunca ejecutar la suite integradora contra producción |
| 9 | Abrir operación VPS gradualmente con acuerdo Pedro/Toni y vigilar errores, diarios y stock; conservar origen anterior para recuperar localStorage | G5/G7: detener escrituras si hay discrepancias o cobro incierto |
| 10 | Preparar APK final/candidata de entrega con paquete/firma/code/destino verificados. Pilotar actualización sin desinstalar sólo en dispositivos identificados y posteriormente autorizados | G1/G6: bloquear mismatch, versión insuficiente, falta de preservación o envío de variante local |
| 11 | Pruebas presenciales y conciliación posterior: ventas/pagos/movimientos/cancelaciones/refunds/cortes/pedidos, claves persistidas, referencias y avisos. Preservar todo historial | G7: aprobación humana registrada; no inventar aceptación |
| 12 | Registrar release activa e IDs/hashes efectivos, activar `current` y helper de mantenimiento de versión verificada, backup posterior/offsite y vigilancia; conservar release anterior y copias | Cierre sólo con evidencia; no apagar Supabase ni activar CENTRO por este procedimiento |

`backend/scripts/migrate.js` obtiene un lock, aplica archivos en orden y omite marcadores existentes; avisa que DDL puede quedar parcial. 030 crea el fence de borradores de compras; 031 añade aliases sin reemplazar folios/PK y backfill; 032 agrega auditoría/guards sin conceder capacidades. `MANAGE_DISCOUNTS + OPERATE_CASHIER` sigue siendo la política conservadora de cancelación. Revisar runtime grants y una posible demora de backfill de 031 en copia representativa.

No se ofrecen comandos `down -v`, prune, borrar schema, grants generales, desinstalación o migraciones inversas. No hacer upgrade de MariaDB/base images simultáneo a este cambio de negocio sin validación separada; registrar/preservar el runtime probado y no depender de tags mutables para recuperación.

## 8. Contingencia y recuperación

- **Fallo antes de DDL:** mantener la evidencia, corregir causa de preparación o volver a servicios/artefactos anteriores exactos; conservar diarios. Confirmar que no hubo escrituras fuera de la ventana antes de abrir.
- **Fallo durante 030/031/032:** MariaDB DDL no admite rollback global. Conservar marcador, objetos realmente creados y logs; mantener escritores cerrados. Elegir reparación revisada o restore en destino vacío de la release/esquema anterior. Nunca borrar objetos/renumerar históricos ni repetir todo suponiendo que el fallo deshizo cambios.
- **Fallo de API/Web después de migrar, sin nuevas escrituras:** un rollback de imagen/bundle requiere demostrar compatibilidad con schema y estados CANCELLED/aliases/pedidos. Una API anterior sin guards de cancelación puede ser inadecuada: no dar por segura la reversión de código por aceptar columnas nuevas. Restaurar DB/config/imágenes si la compatibilidad no está probada, siguiendo procedimiento revisado.
- **Después de nuevos pagos/ventas:** congelar escritores y preservar primero una copia coherente del estado nuevo y los diarios de todos los orígenes. Restaurar un backup anterior perdería esas operaciones; conciliar diferencias/IDs/claves y acordar cómo conservarlas antes de volver a operar. El rollback de código nunca repone esos datos. No reenviar cobros con otra clave para compensar.
- **Durante actualización APK:** no desinstalar, borrar datos o forzar downgrade. Conservar paquete y datos; resolver firma/versión/Room con artefacto compatible. APK anterior puede no leer una DB más nueva: su reinstalación tampoco es una estrategia automática de rollback.
- **Proxy/TLS o servicios ajenos:** sólo actuar sobre los servicios/rutas previamente autorizados. Conservar Caddyfile/certificados y demostrar que otros dominios recuperan su estado. Una emergencia de Vivero no autoriza detener todas las aplicaciones del VPS.

## 9. Git: inventario y propuesta de commits

Estado inspeccionado: App `main`, HEAD `a58fd614b28920d7b39fee7cef2b67f1c16b0531`, **67 caminos dirty preexistentes**; Web `main`, HEAD `5f9c4853e42cace586cdaeec13998668b3d204aa`, **44**. No hay cambios staged al inicio. El [inventario completo](stage7b-change-inventory.csv) contiene los 111 caminos, clasificación, estado/borrado, SHA-256 y commit propuesto. Es una separación documental, no staging ni modificación de archivos.

| ID / repositorio | Mensaje propuesto | Archivos concretos y dependencias |
| --- | --- | --- |
| C01 Web | `fix(purchases): preserve and resolve rejected purchase drafts` | Tres archivos de `src/features/admin/purchases` inventariados y `tests/backend-workflow-http.test.ts`; trabajo anterior ajeno a 7A |
| C02 App/backend | `feat(api): deliver compatible receipts, folios and pending cancellations` | Todos los archivos backend/SQL/infra y docs de contrato asignados C02 en CSV; incluye `app.js`, `web-orders.js`, `short-folios.js`, `sale-cancellations.js`, 031/032, runner/init y pruebas. Agrupa contratos compartidos de etapas 2–5 para no separar health/schema/permisos de sus consumidores |
| C03 Web | `feat(public-orders): print confirmed receipts with readable folios` | PublicOrderTicket, CartDrawer, servicios/coordinador/parser/tests de pedidos, helper `folio-format`, `backend-http`, CSS de impresión y tests PDF listados C03; depende C02 |
| C04 Web | `feat(cashier): simplify checkout and safely cancel pending sales` | Componentes/servicios/diarios/CSS/tests de Caja y PanelPage listados C04, `docs/cashier-stage5.md`; depende C02/C03 y no incluye compras |
| C05 Android | `feat(android): support short and legacy sale folios` | Transporte, datasource/gateway/outbox y pruebas de folios listados C05; depende C02, sin cambio Room |
| C06 Android | `feat(android): polish sales screens and accessible recovery` | Pantallas/ViewModels/tema/SalesComponents/navegación y pruebas de presentación/persistencia listados C06; depende C05; conserva controles comerciales |
| C07 App/infra | `fix(restore): accept only the pristine short-folio counter` | Exactamente `infra/docker/restore.mjs`, `backend/test/backup.test.js`; corrección 7A, no mezclar con UX |
| C08 App/testing | `test(integration): document and verify the stage 7A workflow` | Stage7aRealWorkflowTest y seis documentos/SQL/CSV stage7a listados C08; depende conjunto final C02–C07; fixture/proxy ignorados son requisito local documentado, no CI autosuficiente |
| C09 App/ops | `chore(release): record candidate version and operational requirements` | `app/build.gradle.kts`, `scripts/verify-android-apk.ps1`, docs SMTP/stable/backup/release inventariados C09; preparación anterior. Revisar incremento 8→9 como cambio de entrega explícito, no resolver code repetido en 7B |
| C10 ambos, dos commits | `docs: align operational checkpoints with reviewed stages` | App README y Web README/PROJECT_STATUS/tasks/current listados C10; revisar hunks, cifras y enlaces. No adjudicarles nuevas validaciones ni incluirlos a ciegas con otras funciones |
| C11 App, futuro | `docs(delivery): define stage 7B release gates` | Este informe y `stage7b-change-inventory.csv`, únicos archivos nuevos 7B |
| X, excluir | Sin commit propuesto | `.idea/misc.xml`, borrado `.idea/planningMode.xml`; conservar el estado actual y pedir decisión específica si se quieren versionar |

Cada camino concreto aparece sin comodines en CSV; un mismo archivo puede abarcar varias etapas aunque tenga un commit coherente propuesto. No ejecutar `git add .` ni separar hunks sin comprobar compilación/contratos. Estos commits intermedios no fueron creados ni probados. Revisar dependencias y validar el conjunto final/release después de autorización; commits cruzados entre repositorios necesitan un manifiesto que los relacione. Las correcciones documentales de 31 marcadores y límites de restore constan aquí; los reportes 7A no se reescriben en esta tarea.

Excluir secretos/configuración real (`.env*` excepto plantillas revisadas, `local.properties`, `key.properties`, keystores/llaves); temporales (`tmp/`, fixtures/contraseñas ficticias, dumps, diarios, APK/PDF/capturas/perfiles/emulador), generados (`app/build`, `.gradle`, `dist`, `node_modules`) y artefactos IDE no aprobados. No limpiar/borrar estos caminos para hacer parecer limpio Git. El CSV no incluye valores privados. La entrega de APK/bundle y backups debe ir por canales/artifactos controlados autorizados, nunca al repositorio de código con datos privados.

## 10. Pruebas presenciales y cierre de gates

Para Pedro y Toni, **después** de revisar/autorizaciones: inventario seguro por dispositivo y ensayo de actualización sin pérdida; roles/sucursal/cuentas pendientes; catálogo/foto/precio/stock; cámara con etiqueta real, iluminación, enfoque/repetidos y entrada manual; carrito/cotización/envío; cobro y cambio, tarjeta/transferencia administrativas sin afirmar integración bancaria; dos cajeros, Wi-Fi perdido y reapertura sin duplicación; cancelación con motivo/permisos, devolución con producto/dinero reales y corte; pedido público confirmado/vinculado/entregado; teclado/texto ampliado/tablets; Black Pos 58 mm, papel/escala 100 %/encabezados desactivados, corte y reimpresión sin cobro nuevo; etiquetas legibles. Cualquier operación real de aceptación necesita conciliación y autorización explícitas.

Toni además: restore desde copia externa, acceso propio de mantenimiento, prueba del aviso de fallos, retención/RPO/RTO. Correo: el transporte actual es Resend HTTP, **SMTP aún no está implementado/configurado**; ver [evaluación](smtp-evaluation.md). Es importante y bloquea onboarding/recuperación si esas cuentas dependen de correo. No usar contraseñas compartidas como sustituto. CENTRO queda sin activación hasta conteo/responsables. AppCliente y nuevo desarrollo fuera.

## 11. Validaciones de esta etapa y estado final

7B ejecutó lectura de Git/diffs/documentación/scripts/manifest/Room, verificación local de certificado/badging/DEX/hash de artefactos existentes, gate de actualización APK con resultado negativo de versión, cotejo de JSON de reconciliación/restore ya conservados, conteo del dump ficticio sin revelar contenidos y auditoría SHA-256 del trabajo previo. Evidencias sanitizadas en `tmp/stage7b/` (ignorado). No ejecutó pruebas funcionales, build, servicios, restore/migraciones, inspección remota ni comandos sobre teléfonos.

La auditoría verifica los 67 caminos previos App y 44 Web, preservación de borrados, rama/HEAD e índice. `git diff --check` pasa en ambos. El nuevo inventario y este informe son la única diferencia adicional de código/documentación en 7B; configuración sensible, candidata y evidencia 7A se conservan. Estado sigue dirty por trabajo anterior, intencionalmente. Sin commit, push, instalación ni despliegue.

**Recomendación final: NO-GO de producción/instalación; entregar este informe para revisión.** Primero cerrar conservación del dispositivo, fuente/versión de entrega, recuperación externa/permisos/esquema, impacto del proxy e inventario vigente; después obtener autorización específica para las acciones posteriores. No iniciar otra etapa de desarrollo como consecuencia automática de este reporte.
