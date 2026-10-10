# Candidata 7A y plan de despliegue futuro

Este documento es una preparación para revisión. Ningún paso remoto se ejecutó. Las rutas y servicios VPS proceden de documentación local; deben confirmarse otra vez cuando Pedro autorice el despliegue.

## APK

| Dato | Candidata |
| --- | --- |
| Archivo | `tmp/integration-stage7a/apk/ViveroApp-etapa7a-candidata-debug.apk` |
| Variante | debug, no APK release definitiva |
| applicationId | `com.intutec.viveroapp` |
| versionName / versionCode | `1.0.8-vps` / `9`, conservados |
| minSdk / targetSdk / compileSdk | 24 / 36 / 37 |
| Android mínimo | 7.0 |
| Firma SHA-256 | `54aae00dc34e2e48e287effbb513afb47e448e3e83470ccecb4b42851acf9cb6` |
| SHA-256 APK | `54b363af7ca79d8e48f37ed2b79806bd4fbda3fe4bec01654e2d101a8b99f86e` |
| Destino público compilado | `https://viverodulcinea.bajastack.network`, comprobado en BuildConfig; no se contactó durante pruebas |

La firma coincide exactamente con la APK debug conservada de etapa 6. La compilación final pasó `assembleDebug testDebugUnitTest lintDebug` (370 aprobadas/una HTTP optativa omitida, lint sin errores). No se cambió firma ni versión para forzar una actualización.

Se conserva por separado el APK probado contra el **ensayo local**, `tmp/integration-stage7a/apk/ViveroApp-etapa7a-ensayo-local-debug.apk`, hash `0b9999b7620c9ae916abdd046fdbf83532760c61df48a1b8e0fce09b6515b492`. Apunta a `10.0.2.2:33034`; sólo funciona en el emulador con este ensayo. No distribuirlo como APK operativa.

En el emulador se actualizó ese paquete mediante `adb -s emulator-5560 install -r`, manteniendo la clave y estado incierto original de Room, y se recuperó la venta única. Es evidencia de persistencia al actualizar con **el mismo paquete y firma**, no certificación de todas las instalaciones antiguas.

El SM-A225M consultado después del incidente tenía `com.intutec.viveroapp.vpsvalidation`, versión 1.0.1/code 2, actualizado el 3 de octubre. Es un **applicationId distinto**. La candidata principal no lo reemplaza ni migra sus datos. Antes de entregar: inventariar paquete/firma/versión de cada teléfono operativo, conciliar sus pendientes y comprobar la actualización sobre una copia segura o instalación representativa. No desinstalar, borrar almacenamiento ni cambiar paquete para simular compatibilidad. No está comprobada la conservación de datos del paquete principal en el teléfono durante el incidente: faltó inventario previo.

Para APK definitiva: confirmar el paquete de entrega y almacén de firma existente, custodia de la llave y compatibilidad; configurar los orígenes HTTPS release; escoger un versionCode mayor que el instalado; ejecutar `verifyReleaseConfiguration`, compilación y pruebas de release; verificar certificado, hash y actualización con Room/pendientes. No se generó una nueva llave ni se dio por configurada una firma release. La distribución acordada continúa como APK directo.

## Web, API y migraciones

- Web: 506 tests aprobados/una HTTP optativa omitida, lint y build correctos. Bundle candidato de `ViveroWeb/dist` archivado en `tmp/integration-stage7a/candidate/web-dist.zip`, SHA-256 `22c1e49b031ad54c621257c2e0b1e600bfa4184d659443c901c3292bb12dd090`. No es un despliegue.
- API: 89 pruebas unitarias, check y 35 integraciones HTTP/MariaDB aprobadas tanto en nueva instalación como en esquema actualizado. Imagen de ensayo: `vivero-stage7a-20261009-api`, ID `sha256:6f3edb73ade54b068c2e3d90964b65d5ffc09ac19b061c9d493edc31805e28d5`. Identifica lo probado, no una imagen remota publicada.
- Esquema: instalación completa hasta `032_pending_sale_cancellations` (32 migraciones). Deben aplicarse **todas las faltantes**, no asumir que producción ya tiene 030. No se consultó su versión en esta sesión.
- 031: alias adicionales persistentes; IDs y folios originales intactos, numeración segura concurrente y crecimiento después de 9999. Negociación de formato conserva el contrato de clientes anteriores.
- 032: cancelación pendiente, auditoría e invariantes de pedido vinculado. No concede capacidades adicionales. Cobros y cancelaciones conservan bloqueos y transacciones existentes.
- Corrección 7A de `infra/docker/restore.mjs`: desplegarla junto al código de mantenimiento cuando se autorice; admite sólo el contador inicial sin usar, no destinos ocupados.

## Identificar el destino antes de ejecutar

Según [operación multiproyecto](vps-multiproject-operations.md) y [guía de Toni](toni-backup-handoff.md): proyecto Compose **`vivero-vps`**, servicios `web` (Caddy/bundle), `api` y `db`; configuración base `infra/docker/compose.yaml`, overlay `compose.vps.yaml` y `/srv/apps/vivero-dulcinea/ops/compose.host.yaml`. Fuente activa `/srv/apps/vivero-dulcinea/current/ViveroApp`, configuración privada `/srv/apps/vivero-dulcinea/shared/.env.vps`.

Volúmenes documentados: `vivero-vps_mariadb_data`, `vivero-vps_catalog_images`; respaldos `/var/backups/vivero/`. Web comparte el Caddy del VPS: no recrear otro proxy, cambiar proyecto Compose, publicar API/DB o alterar otros sitios. Confirmar enlaces y etiquetas reales antes de actuar. No copiar los nombres o puertos del ensayo local a producción.

## Orden propuesto — requiere autorización posterior

1. Revisar y separar cambios de etapas anteriores y 7A; confirmar versión exacta de fuentes, bundles, imágenes y contratos. No publicar con secretos ni fixtures.
2. Inventariar remotamente el destino autorizado, Compose/volúmenes, servicios, dominio, versión de esquema, stock/caja y diarios Android/Web. Conservar Supabase y datos históricos. Conciliar operaciones inciertas; no reenviarlas con claves nuevas.
3. Programar una ventana de mantenimiento con Pedro/Toni; impedir escritores y validar que no queden solicitudes en vuelo. Tomar respaldo coherente de SQL, rutinas/triggers/eventos e imágenes mediante los scripts existentes. Conservar aparte configuración privada, certificados, fuentes y firma Android cifrados.
4. Comprobar COMPLETE, hashes y restauración en un destino **nuevo aislado**, utilizando la guardia corregida. Toni debe guardar y verificar además una copia cifrada **fuera del VPS**, recuperar desde ella y custodiar la llave separadamente. Un snapshot o carpeta en el mismo VPS no cumple ese requisito.
5. Con escritores detenidos, aplicar secuencialmente las migraciones que falten hasta 032 usando el migrador existente. Comprobar marcadores, FKs, triggers, aliases, auditorías, permisos y conteos originales. 031 puede tardar por asignar aliases a históricos: medir antes en copia representativa. No renumerar ni borrar históricos.
6. Actualizar API del mismo proyecto conservando configuración/volúmenes y comprobar salud/versión. API nueva exige el esquema completo. Actualizar después bundle Web/Caddy, conservando rutas, HTTPS y otros sitios. Reactivar escritores sólo tras validar integridad.
7. Verificar acceso/roles/sucursal, catálogo, pedidos y consultas de folios corto/anterior. Cualquier venta de aceptación en producción requiere autorización y conciliación separadas, no esta etapa de ensayo. Verificar que un cliente anterior siga funcionando y que no se pueda cobrar una venta cancelada.
8. Distribuir la APK adecuada sólo después de confirmar esquema/API y compatibilidad de cada instalación. Actualizar sobre el mismo paquete/firma, sin desinstalación. Comprobar Room y pendientes antes/después, sin reenvíos automáticos de operaciones antiguas.

## Reversión

MariaDB DDL no se revierte mediante una transacción global. Si una migración falla, mantener escritores detenidos, registrar la versión/DDL realmente aplicado y decidir reparación o restauración desde el respaldo verificado. No ejecutar DROP o renumeración para volver atrás.

Un rollback de API/Web sólo revierte código. Debe conservar alias/históricos/auditorías y comprobar que ese código entiende el esquema, incluido CANCELLED y pedidos vinculados. No usar una Web antigua para omitir advertencias de pago incierto.

Restaurar SQL requiere un destino vacío verificado y preservar también imágenes/configuración apropiadas. Si ya hubo operaciones nuevas, congelar y conservar esas escrituras y diarios antes de restaurar; conciliarlas expresamente para no perder pagos ni duplicar ventas. No volver a abrir el servicio sobre una copia anterior sin esa conciliación. Preservar Supabase como se solicitó; no es sustituto automático de datos nuevos MariaDB.

## Pruebas presenciales para Pedro y Toni

- Verificar paquete/firma/versiones de tablet y teléfonos antes de actualizar. Pendientes locales intactos; sin desinstalar ni borrar datos.
- Login por rol y sucursal; cuentas aún sin acceso, recuperación segura de credenciales y correo SMTP/proveedor. No compartir contraseñas.
- Cámara con etiqueta real, iluminación del vivero, enfoque, código repetido y producto no encontrado; fotografía y disponibilidad. Entrada manual como alternativa.
- Recorrido completo con productos reales **sólo después de autorización operativa**; verificar importe, cambio, referencia, comprobante y stock. Caja en computadora/tablet, teclado, texto ampliado y avisos de incertidumbre.
- Dos cajeros, cortes de Wi-Fi y reapertura con pendientes; conciliar antes de reintentar.
- Cancelación autorizada y devolución con entrega física de dinero/producto; no probar cancelando históricos reales.
- Black Pos 58 mm: seleccionar papel, escala 100 %, sin encabezados/pies; folios, importes, legibilidad y corte de papel, reimpresión y etiquetas. Los PDF no aprueban la impresora.
- Pedido público, confirmación/admin, venta vinculada y entrega al cliente.
- Conteo físico/responsables antes de habilitar CENTRO. No quedó activada.
- Toni: copia externa cifrada, restauración desde ella, monitoreo/aviso de fallos y retención; correo e infraestructura externa siguen fuera de este ensayo.

Detenerse para revisión antes de cualquier acción de producción. El incidente de dispositivo y los límites de actualización están descritos en [el informe](stage7a-integration.md).
