# Lanzamiento piloto: despliegue ejecutado

9 de octubre de 2026, verificación final **20:36 America/Mazatlan** (03:36 UTC del 10). Autorización explícita de Pedro para VPS Hostinger **2030059 / srv2030059 / 179.236.238.111**, migraciones 030–032 y actualización únicamente API/Web.

## Resultado

- Release activa: `/opt/vivero/releases/20261010-pilot-239411c`, accesible por `/srv/apps/vivero-dulcinea/current`. Release anterior conservada.
- API: `sha256:6f3edb73ade54b068c2e3d90964b65d5ffc09ac19b061c9d493edc31805e28d5`, healthy, cero reinicios. Web: `sha256:802b51cf2564621dc3fb125ed86a85ef63bee8ae473a70ce79efa4e3d0681818`, running, cero reinicios; sin health Docker declarado, disponibilidad HTTP verificada.
- MariaDB **11.4.13**, mismo contenedor, imagen, inicio y volúmenes. Migraciones aplicadas en orden **030_purchase_draft_retirement → 031_short_folios → 032_pending_sale_cancellations**; 31 marcadores hasta 032.
- Tras migrar y antes de abrir API/Web, los **54 conjuntos históricos** (todos salvo el registro de migraciones) mantuvieron conteos y checksums exactos. Alias de ventas/pedidos completos. No se eliminaron históricos ni se crearon ventas/cobros de prueba.
- Proxy HTTPS compartido y contenedores API/DB de Catering conservaron ID, imagen, inicio y reinicios. Caddyfiles/overlay host real conservaron sus hashes. Sólo se detuvieron y sustituyeron API/Web Vivero.

## Respaldo final y recuperación

Escritores detenidos y operaciones drenadas antes del respaldo; permanecieron cerrados hasta completar la verificación y migraciones.

- Lote final: `/var/backups/vivero/vivero-2026-10-10T03-31-09-447Z-015635b2-19e1-4197-a8b3-826c46e67023`. SQL completo, imágenes, cuentas/grants/definers, configuración y fuentes/imágenes anteriores API/Web.
- Copia externa: `tmp/pilot-backup/ViveroDulcinea-final-20261010.aesgcm`, SHA-256 `5b43027a152b634f99ce8ae39920c25bc1bd22b937354c5b1ab8d6ead9e4a11b`. Clave DPAPI separada y custodiada por Pedro según `pilot-launch-gate.md`.
- Descarga, cifrado y descifrado autenticado verificados. Todos los hashes de archivos finales coinciden. Las 55 tablas, objetos, cuentas/configuración, release de recuperación e imágenes son **idénticos al lote productivo restaurado y verificado anteriormente**. Se reutilizó esa prueba real de restauración; no se afirma haber ejecutado otra restauración del lote final ni se repitieron suites.
- Todos los respaldos y releases anteriores permanecen. Si hay fallo, cerrar escritores y conservar el estado nuevo; reparación revisada o recuperación en destino nuevo. Volver a una imagen anterior no revierte SQL; si hubo ventas posteriores, conciliarlas antes de cambiar a un respaldo previo.

## Comprobación mínima realmente ejecutada

HTTPS 200: `/health`, `/`, `/login`, `/caja`, `/catalogo`, productos y categorías públicos; Catering `/health/ready` 200. HTML y JS/CSS principales servidos coinciden por SHA-256 con el bundle aprobado. Contratos públicos del catálogo válidos. API healthy, servicios estables y sin errores críticos detectados en logs del arranque nuevo; no se muestran logs privados.

**Inicio de sesión autenticado y Caja bajo una cuenta autorizada pendientes de Pedro**, según su elección de comprobarlos personalmente. Que `/login` y `/caja` respondan HTML no acredita esa aceptación.

## Próximo paso

Pedro inicia sesión en `https://viverodulcinea.bajastack.network/login`, revisa su sucursal y acceso a Caja/folios. Después, autorización independiente para instalar la APK principal código 10 en paralelo en Samsung; no se utilizó ADB ni se modificó el teléfono en esta ventana. Prueba presencial: venta → cobro → impresión real → inventario y cancelación de otra venta pendiente; no repetir cobros ante resultado incierto. Comenzar ventas reales con el equipo operativo tras aceptar ese circuito.

Para operaciones futuras usar la configuración completa de la release, **incluido `images.yaml`**, con imágenes fijadas y overlay host real; no un `compose up` genérico ni recreación de DB. El helper/Compose de DB conserva montajes históricos efectivos; no uniformarlos durante el piloto.

Evidencia sanitizada en `tmp/deploy-pilot/`: preflight, preparación, mantenimiento, respaldo final aprobado, migraciones, API, Web, finish y verificación final. Herramientas temporales ignoradas; sin cambios funcionales a código. Este informe es el único archivo nuevo versionable de la ventana; IDE preexistente intacto. Sin commits ni push nuevos, sin cambios a Supabase ni otros proyectos, sin instalación física ni pruebas comerciales automáticas.
