# Etapa 7C — bloqueantes y verificación local

9 de octubre de 2026. **NO-GO para producción y actualización de teléfonos.** GO para revisar esta entrega y, con autorización posterior, obtener inventarios de sólo lectura. No se modificaron funciones, pantallas, versiones, contratos ni scripts operativos existentes.

## Qué se resolvió y qué sigue pendiente

| Bloqueante de 7B | Avance comprobado en 7C | Lo que falta para cerrarlo |
| --- | --- | --- |
| Recuperación incompleta | Ensayo nuevo de backup 029 → restore 029 → migraciones 030/031/032; integridad y API funcional con cuenta técnica recuperada | Lote productivo autorizado, configuración/DEFINER reales y copia externa cifrada con restauración desde la descarga |
| Permisos del motor | Bootstrap reconstruye cuentas/grants equivalentes; además se recreó realmente `catalog_api` del ensayo desde CREATE USER/GRANT privados y volvió a operar la API | Inventario real de cuentas/plugins/grants; comprobar que no hay diferencias o cuentas adicionales fuera del bootstrap |
| Proxy compartido | Evidencia local más reciente de Catering registra separación ya realizada del proxy HTTPS | Confirmar remotamente, con permiso, que siguen activos los overlays y dueño independiente; no reconstruir desde el overlay local incompleto |
| versionCode 9 | Conflicto identificado, firma y paquete de los dos APK locales cotejados; criterio de versión definido | Inventario físico. Propuesta 10 únicamente si el máximo compatible instalado es ≤9; no se cambió Gradle ni se generó APK |
| Preservación Android | Procedimiento no destructivo preparado, Room/migraciones y exclusiones de backup revisadas | Inventario por dispositivo, pendientes y conservación comprobable; el incidente 7A no permite certificar datos anteriores |
| Control de cambios | 111 caminos originales y dos documentos 7B preservados; clasificación anterior conservada | Revisión de hunks y manifiesto de release; commits/push necesitan autorización separada |

Se revisaron el informe e inventario 7B, informe/matriz/SQL/control/plan 7A, reportes de comprobantes, folios, cancelación, Caja, Android y pulido, guías de Toni y scripts actuales. Sus pruebas históricas no se cuentan como nuevas de 7C. La errata sigue vigente: 31 marcadores 002–032, más `schema.sql`.

## Verificaciones realmente ejecutadas

Proyecto origen nuevo `vivero-stage7c-source-20261009` y destino nuevo `vivero-stage7c-target-20261009`. MariaDB **11.4.13**, imagen local `mariadb:11.4`; API reutiliza la imagen local ensayada en 7A. Volúmenes y redes distintos de los anteriores; redes internas, DB/API sin puertos publicados, sin Web ni correo externo. Credenciales aleatorias y personas/productos/imágenes ficticios. Ningún servicio previo fue detenido o actualizado.

| Ejecución nueva | Resultado |
| --- | --- |
| `node --test --test-isolation=none backend/test/backup.test.js` | 9 aprobadas, cero omitidas, en repetición fuera del bloqueo de rename del sandbox |
| Fixture sintético adaptado de `backend/test/backup-fixture.js` | Venta pagada 12345 centavos, recibido 20000, cambio 7655; stock 11.125 y mínimo 2.125; imagen WebP con hash verificado |
| Scripts oficiales `backup.mjs`, `--verify`, `restore.mjs` | Lote completo verificado y restaurado sólo en destino vacío del mismo esquema 029 |
| Comparación 029 origen/restauración | **55 tablas / 28 marcadores / 8 triggers**; igualdad de hashes de todas las filas, columnas, claves, triggers, rutinas, cuentas técnicas/plugins y grants de tabla/columna/esquema/rutina |
| Migrador oficial en destino restaurado | Sólo 030, 031 y 032 aplicadas en orden; final 61 tablas / 31 marcadores |
| Integridad después de actualizar | Las **54 tablas históricas** excepto marcadores conservan exactamente sus filas/hashes; seis tablas añadidas |
| API restaurada y actualizada | Health 200, login de cuenta ficticia restaurada y catálogo autenticado 200 |
| Usuario runtime `catalog_api` | Trigger de stock y procedimiento DEFINER invocado mediante trigger de venta funcionan; alias generado; DELETE de ventas, UPDATE de auditoría y CALL directo del procedimiento rechazados |
| Recuperación de cuenta técnica | API del ensayo detenida; captura privada CREATE USER/GRANT; recreación sólo de `catalog_api`; igualdad semántica de todos los grants y reinicio saludable con login/catálogo correctos. Root/healthcheck no se sustituyeron |
| Integridad posterior | Ventas, partidas, pagos, inventario/movimientos, aliases, contador y cancelaciones sin cambios de filas/hashes. Inserciones de verificación se revirtieron; el login sí creó sesiones sintéticas y los autoincrementos pueden tener huecos normales |
| Dump suplementario del motor | `mariadb-dump --system=users` probado; variante corregida con charset de cliente produjo SQL portátil privado con 7 CREATE USER y 57 GRANT. No se importó globalmente |
| APK existentes | `apksigner verify --print-certs`, `aapt2 dump badging`, comparación de certificados/hashes: paquete principal, code 9, 1.0.8-vps, min 24/target 36, certificado debug común |
| Git | Hashes/ausencias, rama, HEAD e índice conservados; `git diff --check` en App/Web correcto |

Evidencia ignorada en `tmp/stage7c/`: `restore-integrity.json`, `upgrade-integrity.json`, `account-recovery-integrity.json`, snapshots privados, `migrations.log`, `backup-unit.log`, `api-after-account-recovery.log`, `restored-fixture.log`, certificados/badging y `preservation.json`. Lote usado: `backups/vivero-2026-10-10T01-56-17-587Z-4657f466-3288-4d44-a6d0-462fb881b0ec`. Conservar env/dumps/SQL de cuentas privados: contienen autenticación, aunque sea ficticia. No incorporarlos a Git.

Los intentos fallidos no se ocultan: permisos Docker/rename del sandbox; rutas de dependencias del harness; fixture runtime inicialmente sin responsable/clave; parser de SHOW CREATE USER de una columna y comparación de SHOW GRANTS dependiente del orden. Se corrigieron sólo las herramientas nuevas de ensayo. La reconstrucción de cuenta se ejecutó aunque la primera comparación textual falló por orden; el cotejo posterior de metadatos ordenados y la API aprobaron. Un primer lote contiene la DB sintética todavía vacía y se conserva, pero no es la prueba de recuperación de datos.

Fallo real adicional: `--system=users` con charset predeterminado falla por mezcla `utf8mb4_unicode_ci`/`utf8mb4_uca1400_ai_ci` en esta versión. `--default-character-set=utf8mb3 --system=users` pasó para las cuentas ASCII sintéticas. No extrapolar a nombres con caracteres de cuatro bytes ni a otra versión; capturar por principal con SHOW CREATE USER/SHOW GRANTS si hace falta. No se alteró ninguna collation del servidor.

No se repitieron compilaciones Android/Web, suites comerciales completas, instrumentación, cámara, impresora ni pruebas humanas: no cambió su código y esta etapa prohíbe dispositivos. No se contactó VPS/Hostinger, dominios productivos ni SMTP. No se probaron datos reales, copia externa, root/healthcheck restaurados globalmente ni plugins externos. Los dos proyectos nuevos quedaron detenidos al cierre; volúmenes y lotes conservados.

## Caddy: corrección del diagnóstico 7B

Las fuentes locales de Vivero (`compose.host.yaml`) sólo añaden `platform_proxy`; `compose.vps.yaml` todavía declara 80/443 y TLS. **Ese conjunto local no representa por sí solo el overlay activo después de la separación.** No copiarlo sobre `/srv/apps/vivero-dulcinea/ops/compose.host.yaml`.

La evidencia local de `C:\Users\GAMER\proyectoswebs\Restauran\docs\18-despliegue-vps.md`, apartado «Resultado del despliegue», y sus scripts `separate-vps-proxy.py`/`finalize-vps-proxy.py` documenta:

- Proxy HTTPS dueño de 80/443: `platform-proxy-proxy-1`, Compose `platform-proxy`, `/srv/proxy/compose.yaml`, red `platform_proxy`, Caddy 2.11.6 fijado por digest.
- Certificados: volumen existente `vivero-vps_caddy_data`, propiedad de uso transferida al proxy; no borrar ni montar con dos escritores por compartir prefijo.
- Web Vivero: puertos eliminados con `!reset []`, montajes sustituidos con `!override` por `/srv/apps/vivero-dulcinea/ops/Caddyfile.http`, alias `vivero-dulcinea-proxy:80`.
- Catering: proyecto separado y alias `catering-oculto-web:3000`, sin segundo HTTPS público.

**Conclusión condicionada:** si esta configuración sigue activa, se pueden respaldar/actualizar API y Web de Vivero sin reiniciar el **proxy HTTPS compartido** ni los servicios de Catering. La Web interna sigue usando Caddy y sí se recrea al cambiar su imagen; Vivero tiene su propia interrupción de mantenimiento. No es una garantía de cero interrupción para Vivero ni una comprobación actual del VPS. No ejecutar nuevamente los scripts de separación: ya documentan una migración pasada.

El wrapper local `proxy-check`/`proxy-reload` aún apunta a `vivero-vps-web-1`: valida/recarga el Caddy interno, no el HTTPS compartido. Las instrucciones antiguas de operar `/srv/proxy/Caddyfile` también deben cotejarse: el script reciente monta `platform.Caddyfile`. Determinar el montaje efectivo antes de cualquier operación. La actualización propuesta no requiere reload del proxy si rutas/aliases no cambian.

## APK y conservación de datos

No se incrementó `versionCode=9` ni se construyó/distribuyó otra APK. Los APK conservados de etapas 6 y 7A tienen el mismo paquete `com.intutec.viveroapp`, code 9 y certificado SHA-256 `54aae00dc34e2e48e287effbb513afb47e448e3e83470ccecb4b42851acf9cb6`. La candidata conserva hash `54b363af7ca79d8e48f37ed2b79806bd4fbda3fe4bec01654e2d101a8b99f86e`. Coincidir entre estos dos archivos no acredita compatibilidad con teléfonos.

Próxima versión propuesta: **10**, sólo después de demostrar máximo instalado ≤9 para el mismo paquete y firmante compatible. Si hay otra versión, elegir `máximo + 1` dentro del límite Android. Un código superior no corrige firma diferente ni el paquete `.vpsvalidation`. `key.properties` no está disponible; no generar una nueva llave para sustituir una firma desconocida.

Room permanece en `vivero.db`, versión 6, migraciones 1→2→3→4→5→6, sin fallback destructivo en su constructor. Los callbacks de reapertura recuperan estados interrumpidos: abrir la app puede modificar estados locales y consultar la API. **No abrirla automáticamente como parte del inventario de sólo lectura.** Consultar pendientes con Pedro/Toni en fase posterior autorizada. `allowBackup=false` y exclusiones de nube/transferencia impiden confiar en Google Backup/adb backup. [Procedimiento de comprobación y conservación](stage7c-recovery-and-verification.md).

## Git y separación de entrega

App sigue `main` / `a58fd614b28920d7b39fee7cef2b67f1c16b0531`; Web `main` / `5f9c4853e42cace586cdaeec13998668b3d204aa`. Baseline 7C: **69 caminos App + 44 Web = 113**, incluyendo los 111 originales de 7B y sus dos documentos nuevos. Sin cambios al índice.

Se conserva íntegramente [el inventario de 111 caminos y propuesta C01–C10/X](stage7b-change-inventory.csv). C01 compras/estabilidad anterior; C02 contratos/migraciones; C03 comprobantes/folios Web; C04 Caja; C05 transporte/folios Android; C06 UX Android; C07 restore 7A; C08 integración; C09 preparación anterior; C10 documentos compartidos; X IDE excluido. Hunks compartidos requieren revisión antes de staging. Configuración local y artefactos/credenciales siguen excluidos.

Los únicos caminos nuevos de documentación en 7C son este informe y `docs/stage7c-recovery-and-verification.md`; podrían proponerse por separado como `docs(ops): document blocker resolution and verified recovery rehearsal`, después del grupo documental 7B. No se ejecutó ningún commit. No se actualizó automatización/n8n ni otro repositorio, incluido Catering.

## Toni, autorizaciones y GO/NO-GO actualizado

Toni: verificar acceso administrativo propio, inventario vigente y overlay real; completar lote SQL/imágenes + cuentas/permisos/configuración/release; destino externo cifrado y custodia independiente de llave; descargar/restaurar el snapshot externo y probar API/DEFINER; definir programación/retención y alarmas de fallo/antigüedad. No se configura R2 por defecto. `offsite-backup.py` sólo sube SQL/imágenes validados; no incorpora automáticamente suplementos ni descarga. Respaldar esos suplementos explícitamente con Restic/configuración revisada. No borrar lotes anteriores.

Pedro debe autorizar por separado: inspección remota de sólo lectura; inventario físico de metadatos; cualquier acceso/copia de datos privados; ensayo con datos productivos; ventana/cambios remotos; incremento/build/publicación/instalación de APK; commits/push. Esta entrega no ejecuta esas acciones.

| Gate | Estado para producción | Evidencia necesaria |
| --- | --- | --- |
| G0 Destino/release | NO-GO | Inventario vigente y manifiesto inmutable de fuentes/artefactos |
| G1 Datos físicos/pendientes | NO-GO | Paquete/firma/versiones, pendientes conciliados y conservación acreditada por dispositivo |
| G2 Servicios compartidos | Condicional, no cerrado | Confirmar separación/overlays/montajes/red/volúmenes y que Catering mantiene health; no cambiar proxy compartido |
| G3 Recuperación | Local sintética aprobada; producción NO-GO | Lote completo real + cifrado externo + restore desde descarga y API funcional |
| G4 Migraciones | Ensayo 029→032 aprobado; producción pendiente | Esquema real exacto, copia representativa autorizada, tiempo/DDL parcial y backfill medidos |
| G5 API/Web | Evidencia 7A y smoke restaurado aprobado | Compatibilidad y smoke en destino autorizado; no confundir health con release |
| G6 APK | NO-GO | Inventario físico, firma compatible y versión superior, Room/pendientes seguros |
| G7 Aceptación | Pendiente presencial | Cámara, Black Pos, tablet/red real, Pedro/Toni; CENTRO sigue pendiente de conteo/responsables |

**Siguiente paso mínimo y seguro:** revisión de este informe y autorización acotada de dos inventarios de sólo lectura: topología vigente del VPS y metadatos de los paquetes físicos. No incluir instalación, apertura automática de app, datos privados ni cambios remotos. Con esos resultados fijar el versionCode y confirmar G2; Toni puede preparar destino/custodia externa mientras tanto. Detenerse para revisión.
