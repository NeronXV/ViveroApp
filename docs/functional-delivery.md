# Entrega funcional de Vivero Dulcinea

Preparación local del 9 de octubre de 2026. No autoriza ejecutar producción ni instalar. Se reutiliza evidencia 2–7D; no hay una nueva etapa de desarrollo ni repetición de suites.

## Entrega preparada

- `tmp/delivery-functional/source-snapshot.zip`: copia de fuentes App/Web con manifiesto SHA-256, sin configuración privada, llaves, artefactos IDE ni binarios. Conserva también trabajo anterior: no significa aprobar todos sus archivos para un commit.
- `api-image.tar`: imagen API validada en 7A, ID `sha256:6f3edb73ade54b068c2e3d90964b65d5ffc09ac19b061c9d493edc31805e28d5`; etiqueta `vivero-stage7a-20261009-api:latest`. Para despliegue cargar y fijar este contenido, no reconstruir una etiqueta mutable sin cotejarlo.
- `web-dist.zip`: bundle Web validado en 7A. Preparar imagen HTTP interna con este bundle y runtime Caddy fijado; registrar su ID antes de recrear exclusivamente Web. No volver a compilar el frontend en producción.
- `ViveroApp-1.0.9-code10-candidata.apk`: paquete principal `com.intutec.viveroapp`, debug, código 10, nombre `1.0.9-vps-candidate`, destino `https://viverodulcinea.bajastack.network`. Firma debug igual a candidata 7A; metadatos y firma verificados, compilación satisfactoria. No es una release definitiva.
- `candidate.init.gradle`: receta de versión/destino usada sólo durante esta compilación; no cambió Gradle versionado ni `local.properties`. Reproducir con el mismo JDK/SDK/Gradle y llave privada custodiada, sin incluirla en el paquete de fuentes. El manifiesto identifica el binario exacto, no promete que una recompilación sea idéntica byte por byte.
- `delivery-manifest.json`: hashes y relación de artefactos. Los ZIP/TAR/APK permanecen ignorados, fuera de commits. Su copia a un canal de entrega aún requiere autorización.

## Git

Los 116 caminos preexistentes (App 72, Web 44) se registraron antes de trabajar. Incluyen los 111 históricos más cinco documentos de preparación. Se guardaron patches y copia de fuentes sin reset, limpieza, staging ni cambios de rama. Configuración privada conservada en su ubicación; no se archiva como código.

Usar los caminos concretos del inventario `stage7b-change-inventory.csv` y sus grupos C01–C11. Orden: contratos backend/SQL; comprobantes y Caja Web; consumidores y UX Android; corrección de restauración 7A; pruebas/documentación. Mantener compras anteriores y documentación operativa en sus grupos propios. Excluir IDE, secretos y generados. Esta guía se añade al grupo documental. No hace falta rediagnosticar Git ni inventar otra partición. Los commits siguen pendientes de autorización; la copia con hashes permite revisar y recuperar el estado previo entretanto.

## Únicos requisitos antes de publicar

1. Toni: respaldo productivo completo, copia cifrada fuera del VPS y restauración verificable desde esa copia. Necesita autorización específica para usar datos reales en el ensayo. Incluir DB con datos/esquema/rutinas/triggers/eventos, imágenes, suplementos privados de cuentas/grants/DEFINER, configuración operativa y releases/imágenes. Custodiar llave fuera del VPS. Una copia local no cierra este requisito.
2. Autorizar ventana y ejecutar actualización acotada: destino confirmado, migraciones 030–032, API y Web. Preservar overlay host real y DB existente; no sustituirlo por el overlay local antiguo. La preparación de imagen Web/overlay final se completa antes de tocar escritores. Una configuración que exponga nuevamente 80/443 o recree DB/proxy es NO-GO.
3. Autorizar instalación paralela y aprobar la prueba presencial breve. La candidata no debe usarse para cobros sobre API/esquema anterior. No afirmar aceptación física a partir de tests locales.

## Secuencia de ejecución para autorizar

1. **Cerrar lote de entrega.** Revisar manifiesto y grupos Git; autorizar commits si se desea etiquetar la versión. Preparar release con carpetas hermanas `ViveroApp`/`ViveroWeb`, sin editar `current` todavía. Cargar API exportada, preparar Web desde bundle conservado y fijar imágenes API/Web/migrate en overlay de entrega. Comparar configuración efectiva sin mostrar secretos: proyecto `vivero-vps`, redes y volúmenes existentes, Web HTTP interno con alias `vivero-dulcinea-proxy`, sin puertos públicos. Preservar `/srv/apps/vivero-dulcinea/ops/compose.host.yaml`, `/srv/proxy/platform.Caddyfile`, proxy `platform-proxy` y todos los recursos de Catering.
2. **Toni acredita recuperación.** Usar scripts `infra/docker/backup.mjs` y `restore.mjs` revisados y guía `stage7c-recovery-and-verification.md`. Restaurar descarga externa en proyecto nuevo `vivero-restore-*`, volúmenes/credenciales propios, esquema de origen 029, sin puertos públicos ni salida de correo. Verificar hashes, tablas/conteos/agregados, rutinas/triggers, grants, imágenes y acceso API; registrar responsable, snapshot externo y resultado sin datos personales. Ensayar sólo 030–032 sobre esa copia real autorizada: aporta evidencia de duración/backfill con los históricos, que el ensayo sintético no cubre. **GO sólo con recuperación acreditada**.
3. **Ventana autorizada.** Identificar nuevamente host y recursos seleccionados; bloquear nuevos envíos y drenar operaciones. Detener únicamente API/Web de `vivero-vps`, conservar DB/proxy/Catering activos. Tomar respaldo final coherente y verificar/cifrar/copiar fuera; el helper no debe reabrir escritores. Si cambió significativamente frente al lote restaurado, restaurar también el lote final antes de migrar. No resolver ni reenviar pendientes antiguos.
4. **Migrar y actualizar.** Migrador de la release candidata, DB activa, herramientas y secretos privados correctos, `run --rm --no-deps migrate`: 030 `purchase_draft_retirement` → 031 `short_folios` → 032 `pending_sale_cancellations`. Comprobar marcadores y objetos; detenerse ante DDL parcial. Luego actualizar sólo API y después sólo Web con `up -d --no-deps --no-build api` y equivalente `web`, usando configuración revisada e imágenes fijadas. Nunca `up` general, `down`, recreación de DB ni reinicio del proxy compartido.
5. **Humo y acceso.** API/DB healthy, HTTPS y Web accesibles, login propio, folios nuevos y anteriores consultables, permisos/sucursal correctos. Verificar también disponibilidad de Catering. Registrar imágenes y release efectivas; activar `current` y helper revisado sólo cuando se apruebe el resultado. No iniciar suites destructivas sobre producción ni activar CENTRO.
6. **Samsung y prueba presencial.** Tras autorización independiente, instalar paquete principal al lado de `com.intutec.viveroapp.vpsvalidation`; no desinstalar, habilitar ni borrar la variante anterior. El nuevo paquete tiene Room independiente: no transporta ni concilia pendientes de la variante vieja. Prueba con cuenta/sucursal autorizadas: venta Android → cobro Web → impresión real; reimprimir sin nuevo cobro; cancelar otra venta pendiente y comprobar inventario sin cambio; comprobar decremento exacto de la venta pagada. Acordar importe/productos y conservar los registros reales. **GO operativo sólo con estos resultados aceptados por Pedro y Toni**.

## Contingencia mínima

Ante fallo, conservar escritores cerrados y evidencia. Cambiar imagen no revierte migraciones. Si SQL queda parcial, reparación revisada o restauración en otro destino vacío, nunca importar encima para ocultar el fallo. Si ya hubo ventas nuevas, preservar primero ese estado y conciliar antes de recuperar un respaldo anterior. Conservar release anterior, diarios, backups y Supabase. No tocar Catering ni el proxy compartido.

## Validación de esta preparación

Sólo compilación `assembleDebug` con receta aislada, verificación de firma/paquete/versiones, constantes de URL generadas, hashes de artefactos y conservación del trabajo previo. No suites completas, pruebas físicas, SSH, instalaciones, migraciones ni despliegue. Git final y resultado de hashes se guardan en evidencia local. SMTP, AppCliente, mejoras y firma release definitiva no amplían esta entrega candidata de pruebas.
