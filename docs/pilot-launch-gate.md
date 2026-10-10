# Vivero Dulcinea: punto de autorización del piloto

Preparación terminada el 9 de octubre de 2026 (hora de Chihuahua; evidencia UTC del 10). **Detenido antes de migraciones y actualización productiva.** Pedro confirmó que todas las ventas existentes son pruebas; no se borraron ni reenviaron.

## Resultado real

- Se crearon 12 commits locales coherentes según C01–C11 del inventario. Se conservaron los 926 archivos de la copia congelada, verificando sus hashes antes/después, y los siete artefactos originales. Sin push ni cambios funcionales. Los dos cambios IDE siguen intactos y fuera de commits. Una línea vacía al final de una prueba preexistente se conservó; el control de whitespace se ejecutó sin `blank-at-eof` para ese cierre.
- Se generó un respaldo nuevo con el helper oficial en el VPS Hostinger **2030059 / srv2030059 / 179.236.238.111**. Pausa y reanudación únicamente de API/Web Vivero; MariaDB permaneció activa. No se reinició ni recreó proxy/Catering/DB. Los respaldos anteriores permanecen.
- Lote VPS: `/var/backups/vivero/vivero-2026-10-10T03-06-16-335Z-5978be61-af25-47e0-b88f-48e469b4496e`. Incluye SQL (datos, estructura, rutinas, triggers, eventos), imágenes, manifest, cuentas/grants, definers, configuración privada, fuentes de la release anterior e imágenes Docker anteriores de API/Web.
- Copia externa autorizada: `tmp/pilot-backup/ViveroDulcinea-20261010.aesgcm`, cifrada AES-256-GCM. SHA-256: `65bef3839ca43eb025b5fd2afbb9557a190b70d6fa9a7339b8ff5a1212cecc3f`. Descarga, autenticación y descifrado coinciden con SHA-256 del archivo completo VPS. No se publicaron datos ni claves.
- Restauración **real** desde esa copia en `vivero-restore-pilot-20261010`, Docker local sin puertos públicos, redes internas ni correo. Las **55 tablas** coinciden en conteos y checksums extendidos; columnas, triggers, rutinas, eventos y privilegios coinciden. `catalog_api` recuperó su definición de autenticación y grants exactos. Root/DEFINER se preservan mediante bootstrap privado; cuentas healthcheck se generan para el nuevo motor y sus originales quedan en el suplemento. MariaDB local 11.4.13 y API anterior restaurada con health 200. Contenedores del ensayo detenidos; volúmenes conservados. El volumen de imágenes original y restaurado está vacío (0 archivos), comprobado por nombres/hashes.
- Comprobación final: MariaDB productiva **11.4.13**, 28 marcadores hasta **029**, sin 030–032; health HTTPS Vivero/Catering 200. Los cuatro contenedores protegidos DB Vivero, proxy y API/DB Catering conservan ID, imagen, inicio y reinicios de 7D.

## Qué se instalará después de autorización

- API validada 7A: `sha256:6f3edb73ade54b068c2e3d90964b65d5ffc09ac19b061c9d493edc31805e28d5`, exportada en `tmp/delivery-functional/api-image.tar`.
- Web: los 23 archivos exactos del bundle validado 7A, empaquetados sin npm/build/dependencias nuevas. Runtime anterior fijado; imagen candidata `sha256:802b51cf2564621dc3fb125ed86a85ef63bee8ae473a70ce79efa4e3d0681818`, exportada como `web-image.tar`. Comparación de todos sus archivos SHA-256 satisfactoria. No se cambia el Caddyfile productivo ni se sirve el contenido anterior del runtime.
- Pendientes SQL: **030_purchase_draft_retirement → 031_short_folios → 032_pending_sale_cancellations**. API después de SQL; Web después de API. Conservar IDs, históricos y permisos.
- Impacto: ventana breve de indisponibilidad de Vivero para cerrar escritores, respaldo final, migración y reemplazo sólo API/Web. No se promete tiempo exacto del backfill. MariaDB no se recrea. No modificar el overlay host real, volúmenes ni redes; proxy compartido y Catering quedan fuera de la actualización.

## Recuperación y custodia

Ante fallo mantener escritores cerrados, conservar estado/logs y decidir reparación revisada o restauración en un destino nuevo. Recuperar SQL/imágenes y cuentas/configuración desde el lote externo verificado y usar las imágenes API/Web anteriores conservadas. Un rollback de imagen no revierte DDL. Si hubo operaciones posteriores al respaldo final, preservarlas y conciliarlas antes de cambiar destino.

Pedro custodia la clave aleatoria de 256 bits, protegida con DPAPI de su usuario Windows, en `C:/Users/GAMER/.codex/secrets/vivero-pilot-20261010.dpapi`, separada del respaldo y con ACL exclusiva. No necesita compartir contraseña ni mostrarla. Conservar ese archivo y el perfil Windows: copiar sólo el archivo DPAPI a otra cuenta/equipo no permite descifrarlo. Antes de reinstalar Windows o cambiar equipo, solicitar una exportación cifrada con contraseña elegida localmente o custodia independiente, sin compartirla en chat. No borrar el perfil ni la clave.

Herramientas de cifrado y evidencia sanitizada: `tmp/delivery-functional/pilot-crypt.cjs`, `pilot-key.ps1`, `pilot-restoration.json`, `pilot-file-verification.json`, `pilot-final-vps.json`, `pilot-commits.json`, `pilot-release-manifest.json`. Los descifrados temporales están en `tmp/pilot-restore/` con ACL exclusiva; el ensayo permanece detenido y ninguna copia privada se incorpora a Git.

## Decisión

**GO para solicitar autorización de actualización; despliegue todavía no ejecutado.** Falta la autorización explícita de Pedro para migraciones/API/Web y, por separado, la instalación Samsung. No se repitieron suites, no se crearon ventas ficticias en producción ni se realizaron pruebas físicas. APK principal código 10 existente permanece igual. Después del despliegue: humo mínimo, instalación paralela autorizada, venta/cobro/ticket/inventario/cancelación presencial y aceptación antes de comenzar ventas reales con el equipo operativo.
