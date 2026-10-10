# Panel premium publicado — Vivero Dulcinea

**Estado vigente:** Pedro autorizó la publicación final y se completó
**033 → API premium → Web premium**. Vivero está abierto por HTTPS; API y
MariaDB saludables. Catering y el proxy compartido permanecen intactos.
La comprobación autenticada por usuarios y las pruebas físicas siguen pendientes.
El historial del intento inicial y su recuperación se conserva debajo; el
resultado de publicación definitivo está al final.
**Actualización local posterior:** el respaldo cifrado 032 ya fue restaurado y
verificado completamente tras reconciliar PROXY exclusivamente en un clon nuevo.
Esto no autoriza publicar premium ni sustituye un respaldo futuro actualizado.

## Estado real

VPS Hostinger 2030059, `srv2030059`, confirmado mediante Hostinger y SSH con
la clave y verificación de host existentes. El paquete congelado y sus hashes
coinciden con el manifiesto aprobado. Se transfirieron fuentes, overlay e imágenes;
se cargaron sin reconstruir y se comprobó que el overlay sólo cambia las imágenes
API/Web y la fuente SQL del migrador. No se publicaron los candidatos.

La ventana de mantenimiento detuvo exclusivamente `vivero-vps-web-1` y
`vivero-vps-api-1`. Se verificaron cero conexiones restantes a `vivero`, cero
transacciones InnoDB, ausencia de eventos habilitados y ausencia de otros servicios
escritores de ese proyecto. MariaDB no tiene puertos públicos. Android, Caja y
pedidos quedan sin acceso a la API a través de la Web detenida.

## Respaldo nuevo

- Base productiva actual: MariaDB `11.4.13-MariaDB-ubu2404`, esquema hasta
  `032_pending_sale_cancellations`, 61 tablas, 31 marcadores de migración.
- Dump nuevo con datos, estructura, rutinas, triggers y eventos; fotografías;
  suplemento privado con definiciones de usuarios/grants, configuración,
  metadatos de runtime y las imágenes anteriores API/Web/MariaDB.
- Carpeta VPS:
  `/var/backups/vivero/vivero-2026-10-10T08-34-08-972Z-c3e1006f-6087-4e5d-81c0-7587b281de2d`.
- Archivo completo SHA-256:
  `e88279ef9c05b34b74569a1b47305ba23615958e9ea5c14c3d51f0f7b801fb4c`.
- Copia externa cifrada AES-256-GCM:
  `tmp/pilot-backup/ViveroDulcinea-premium-032-20261010-083408.aesgcm`.
  SHA-256 `43d3af0d959d98bac140e190e8a144616fb79396c1ef5909d8d4c652167c1c88`.
- Clave DPAPI existente separada del respaldo, bajo custodia de Pedro; no se
  mostró ni se incluyó en el archivo. Copias anteriores conservadas.
- Descifrado autenticado, hash del archivo y hashes del manifiesto completo
  verificados antes de la restauración.

## Condición de parada

Se creó un destino local vacío y aislado, sin puertos publicados, con el esquema
032 y MariaDB 11.4.13. El helper canónico importó SQL y fotografías. La reproducción
de las definiciones/permisos de las cuentas técnicas encontró
`TECHNICAL_ACCOUNT_GRANTS_DIFFER` y el procedimiento se detuvo.

**No se acredita una restauración completamente verificada.** Las comparaciones
posteriores de las 61 tablas, objetos y fotografías no se ejecutaron al fallar
el control previo de cuentas. La instancia local quedó detenida y conservada
para diagnóstico. No se intentaron reparaciones ni se aplicó 033 en producción.

## Verificación final de producción

- API anterior `sha256:6f3edb73ade54b068c2e3d90964b65d5ffc09ac19b061c9d493edc31805e28d5`:
  mismo contenedor, detenido por mantenimiento.
- Web POS anterior `sha256:02000e02d752abf210db1b59af52fe176f7078b5c38cce0d5968b3ed4cb1454f`:
  mismo contenedor, detenido por mantenimiento.
- MariaDB saludable, último marcador 032, sin columna de preparación 033.
- DB, proxy y Catering: IDs, imágenes, inicios y reinicios iguales al inventario
  previo. Configuraciones productivas protegidas sin modificaciones.
- Vivero HTTPS responde **502 por mantenimiento**; no se presenta como disponible.
- Catering HTTPS `/health/ready` responde **200**.

## Decisión necesaria

**NO-GO para migrar/publicar.** Solicitar autorización para investigar y corregir
exclusivamente la restauración local, verificándola antes de retomar, o para
reabrir la versión anterior sin migración. No reabrir automáticamente.

Evidencia ignorada por Git: `tmp/deploy-premium/` (inventario, staging,
mantenimiento, respaldo, cifrado, log de restauración y estado final).
No se repitieron builds ni suites previas. No hubo ventas/cobros/movimientos de
prueba, cambios Android, commit, push, migración productiva ni cambios a Catering
o al proxy compartido. Se preservaron los cambios locales aprobados; sólo este
informe es un archivo versionable nuevo de esta ejecución.

## Reapertura autorizada de la versión anterior

Se confirmó nuevamente Hostinger 2030059 y el estado remoto. Se usó
`docker start vivero-vps-api-1`, se comprobó su health y acceso al esquema 032,
y después `docker start vivero-vps-web-1`. No se recrearon contenedores ni se
usó el overlay premium. Se conservaron IDs, imágenes, montajes y redes anteriores.
El Caddy HTTP original se validó antes de arrancar Web, sin modificar su archivo.

- API activa: `sha256:6f3edb73ade54b068c2e3d90964b65d5ffc09ac19b061c9d493edc31805e28d5`.
- Web POS activa: `sha256:02000e02d752abf210db1b59af52fe176f7078b5c38cce0d5968b3ed4cb1454f`.
- Esquema: todos los marcadores originales, hasta 032; sin 033 parcial.
- HTTPS 200: `/health`, `/`, `/login`, `/caja`, `/catalogo` y consulta pública
  de productos. Los tres assets de login/Caja comprobados también responden 200.
- Sin sesión, `/api/v1/auth/me`, `/api/v1/cashier/sales` y
  `/api/v1/cashier/receipts` responden 401, conservando la protección de acceso.
- No se realizó login con credenciales ni un cobro autenticado. La disponibilidad
  y controles anteriores no sustituyen la comprobación humana de esos flujos.
- Catering HTTPS `/health/ready` 200. DB, proxy y Catering mantienen IDs,
  imágenes, inicios y reinicios originales. Sus configuraciones protegidas
  mantienen hashes idénticos. MariaDB no se reinició ni se modificó.
- Copia cifrada nueva: SHA-256 idéntico. Artefactos premium y trabajo local
  conservados. Sin migraciones, nueva publicación, commit ni push.

Evidencia: `tmp/deploy-premium/reopen-result.json` y `reopened-routes.json`.

## Diagnóstico local de la diferencia de permisos

Se arrancó exclusivamente el clon
`vivero-premium-current-032-20261010-083408-db-1`, tras confirmar su proyecto,
ausencia de puertos y redes internas. Se consultaron `SHOW GRANTS` y metadatos
de `mysql.proxies_priv`; no se aplicaron grants ni correcciones. El clon quedó
nuevamente detenido. No se consultaron permisos productivos para este diagnóstico.

La única diferencia en las listas de grants es `root@localhost`: respaldo con
dos líneas, clon con tres. No faltan ni aparecen sentencias distintas; el clon
repite dos veces `GRANT PROXY ON ``@`%` TO `root`@`localhost` WITH GRANT OPTION`.
Los metadatos locales muestran una delegación inicial con host delegado vacío
y otra con `%`; ambas se representan igual en `SHOW GRANTS`.

El bootstrap creó una delegación PROXY. El restablecimiento usó
`REVOKE ALL PRIVILEGES, GRANT OPTION`, que no eliminó esa delegación; al reproducir
el grant del respaldo se conservó también la inicial. La comparación estricta
de arrays detectó la línea repetida. **Esto no demuestra aún una restauración
completa: las verificaciones restantes del intento inicial siguen pendientes.**

Solución propuesta, todavía no ejecutada: reconciliar las delegaciones PROXY del
clon con las del respaldo, retirando primero la delegación de bootstrap sobrante
y reproduciendo la registrada. Mantener la comprobación estricta; no ocultar el
problema eliminando duplicados del comparador. Después verificar definiciones,
grants, las 61 tablas, objetos y fotos en ese entorno aislado. Explicar esta
corrección a Pedro antes de continuar; cualquier publicación premium requiere
una autorización nueva y un respaldo coherente con el estado productivo de esa
futura ventana.

Evidencia sanitizada: `tmp/deploy-premium/grants-diagnosis.json`. Ninguna clave,
hash de autenticación ni dato de clientes se incluyó en el diagnóstico público.

## Corrección autorizada y restauración completa verificada

Pedro autorizó exclusivamente resolver la recuperación local. Se añadió
`infra/docker/verify-encrypted-restore.mjs`, una utilidad reproducible que requiere
sólo la copia cifrada, la clave separada y software local. No usa los archivos
del intento anterior, manifiestos temporales ni el paquete premium: recupera
fuentes, configuración e imágenes del propio archivo cifrado.

Se utilizó el proyecto nuevo `vivero-restore-032-20261010`, con volúmenes nuevos,
redes internas y sin puertos. Se retiró únicamente la entrada inicial PROXY
exactamente identificada, antes de reproducir las cuentas respaldadas. La
comparación de grants se mantuvo estricta; no se ocultaron duplicados.

**Resultado PASS: respaldo 032 restaurable y verificado en esta PC.**

- Autenticación AES-256-GCM, SHA-256 externo y manifiesto completo coincidentes.
- 61 tablas, 670 registros: conteos/checksums idénticos al respaldo.
- 103 relaciones FK sin huérfanos; `CHECK TABLE` correcto para todas las tablas.
- Cuatro cuentas técnicas: definiciones y grants coincidentes, sin PROXY duplicado.
- Usuarios de aplicación, roles, permisos y sucursales incluidos en las tablas
  comparadas. Acceso real de `catalog_api` comprobado con lecturas, sin iniciar
  servidor API, crear sesiones ni producir ventas/pagos.
- Columnas, rutinas, triggers, eventos, definers y permisos de tablas/columnas
  idénticos. MariaDB 11.4.13 y parámetros comparados coincidentes.
- Marcadores originales hasta 032; no se ejecutó 033.
- Archivo de fotografías vacío y volumen restaurado vacío: 0 fotografías en
  este respaldo. No se presenta como una prueba de restauración de fotos existentes.
- Cuentas de healthcheck regeneradas por bootstrap para el volumen nuevo;
  definiciones originales conservadas en el suplemento cifrado. Health correcto.
- Clon detenido al finalizar; volúmenes y evidencia conservados.

Procedimiento permanente: `docs/mariadb-encrypted-recovery-032.md`.
Evidencia ejecutada: `tmp/restore-032-verified-20261010/restore-verification.json`.
No hubo conexión a producción en esta corrección, actualizaciones API/Web,
reinicios productivos, commit ni push. Imágenes premium y cambios previos intactos.

Límites: la clave DPAPI requiere conservar el usuario/perfil Windows y su archivo
separado; no se verificó recuperación en otra PC. El respaldo es la instantánea
032 de las 08:34 UTC, anterior a la reapertura; para una nueva publicación habrá
que cerrar escritores, tomar otra copia coherente y obtener nueva autorización.

## Nueva preparación: respaldo actualizado y punto de autorización final

Pedro autorizó expresamente **sólo mantenimiento y respaldo** al aclarar el
alcance de la preparación. No autorizó aún 033 ni reemplazar imágenes.

Antes de detener servicios se confirmó nuevamente Hostinger 2030059:
API anterior saludable, Web POS anterior activa, MariaDB 11.4.13 hasta 032,
sin 033 parcial; Vivero y Catering HTTPS 200. IDs, imágenes y configuraciones
coincidían con los originales. Los ocho artefactos locales y los artefactos
remotos de despliegue, las imágenes premium y el SQL 033 coincidían por hash
con el manifiesto aprobado. Sin compilaciones ni suites adicionales.

Se detuvieron sólo API/Web; no quedaron otras conexiones a `vivero`, transacciones
InnoDB, eventos habilitados ni otros servicios escritores del proyecto.
MariaDB, Catering y el proxy conservaron sus contenedores/inicios/configuraciones.

### Respaldo nuevo de esta ventana

- Instantánea: **2026-10-10 08:58:34 UTC**, esquema 032.
- Carpeta VPS:
  `/var/backups/vivero/vivero-2026-10-10T08-58-34-264Z-b0221827-f8cd-4336-8485-45be741950f1`.
- Archivo completo SHA-256:
  `d60d9ee41f5eaa9d8d14cd57a1347eebed8ebcf3f18b480c9acf61c4b0263120`.
- Copia externa cifrada:
  `tmp/pilot-backup/ViveroDulcinea-premium-032-20261010-085834.aesgcm`.
  SHA-256 `4cc90c9a9d94f52012cce91fa5eb5420b0a33c6a77c799a6d01d1fce366b61dc`.
- Datos/esquema/rutinas/triggers/eventos, cuentas/grants/definers, configuración
  privada, fuentes e imágenes anteriores incluidos. Clave DPAPI separada,
  sin mostrarla; copias anteriores preservadas.
- Se inventarió expresamente `vivero-vps_catalog_images` con montaje de sólo
  lectura antes y después del respaldo: **0 archivos, sin cambios**. El inventario
  quedó incluido en el suplemento cifrado. Archivo de fotos y volumen restaurado
  coinciden también vacíos; no se afirma haber recuperado fotos inexistentes.

### Restauración ejecutada y verificada

Se usó la utilidad permanente corregida, desde la copia cifrada nueva, en el
proyecto local nuevo `vivero-restore-032-resume-085834`, sin puertos y con redes
internas. No dependió de las extracciones anteriores ni del paquete premium.

**PASS:** 61 tablas y 669 registros actuales con conteos/checksums coincidentes;
103 relaciones sin referencias huérfanas; `CHECK TABLE` correcto; cuatro cuentas
técnicas con definiciones/grants estrictamente iguales; usuarios de aplicación,
roles/permisos/sucursales preservados; login DB de `catalog_api` mediante lecturas;
objetos, definers y configuración MariaDB iguales; marcadores hasta 032 y volumen
de fotos coincidente. Reconciliación PROXY aplicada sólo en el clon. Healthcheck
regenerado para su volumen nuevo; clon detenido y conservado al terminar.

Evidencia: `tmp/restore-032-resume-20261010-085834/restore-verification.json`,
`tmp/deploy-premium/resume-backup-approved.json` y `resume-final-gate.json`.
La utilidad `infra/docker/verify-encrypted-restore.mjs` recibe la nueva ruta,
SHA externo/cifrado, y nombres nuevos de proyecto/directorio; el procedimiento
permanece en `docs/mariadb-encrypted-recovery-032.md`.

### Estado al solicitar aprobación

**GO condicionado a autorización final:** Web/API anteriores están detenidas por
mantenimiento, MariaDB sigue saludable en 032, Catering HTTPS 200; proxy, DB y
Catering sin cambios. Respaldo remoto conserva su hash y no hay escritores.
No se ejecutó 033 ni se sustituyeron contenedores. No hubo commit, push ni cambios
de desarrollo. Las imágenes listas siguen siendo:

- API `vivero-api-premium:20261010-2502d7f8f5f0`, ID `sha256:29dec291775828f18e7a4e5d9452b8a8dca1b73ab016fad0546cf4314c20efb1`.
- Web `vivero-web-premium:20261010-2502d7f8f5f0`, ID `sha256:aea52cabcad1c6d76a9be8e5f5200786db57624845a8dd2f140a464900d4ea54`.

Detenerse aquí y solicitar autorización para **033 → API → Web**. Mantener fuera
de la actualización MariaDB/contenedor/volumen, Catering y el proxy. La clave
DPAPI sigue ligada al perfil Windows; no se verificó recuperación en otra PC.
Si se reabren escrituras antes de publicar, esta copia dejará de ser el respaldo
final coherente y habrá que actualizarla en la próxima ventana.

## Publicación final autorizada y completada

10 de octubre de 2026. Web activa desde aproximadamente 02:12, hora de Mazatlán
(inicio de contenedor `2026-10-10T09:12:13.941894838Z`).

Pedro autorizó las imágenes congeladas y la secuencia 033 → API → Web, con
reapertura si las verificaciones técnicas pasaban. Se confirmó nuevamente
Hostinger 2030059, mantenimiento sin escritores, copia externa cifrada y evidencia
PASS de su restauración; el archivo de respaldo remoto conservaba su SHA-256.
Antes del SQL, todas las tablas aún coincidían con la instantánea respaldada.

### Resultado y versiones activas

- **Única migración aplicada:** `033_inventory_preparation`; el migrador omite
  los marcadores anteriores. Verificados marcador, tablas y permisos nuevos.
- Campos históricos de productos conservados por hash y otras 59 tablas
  históricas conservadas por conteos/checksums, incluidas ventas, pagos,
  inventario, usuarios/roles/permisos y auditoría.
- API: `vivero-api-premium:20261010-2502d7f8f5f0`,
  ID `sha256:29dec291775828f18e7a4e5d9452b8a8dca1b73ab016fad0546cf4314c20efb1`.
  Contenedor `0bf5d2e72f44fa525a8b9615ca2741d50f50da3e4c230eac339b6bd5e6b92dba`,
  saludable, cero reinicios.
- Web: `vivero-web-premium:20261010-2502d7f8f5f0`,
  ID `sha256:aea52cabcad1c6d76a9be8e5f5200786db57624845a8dd2f140a464900d4ea54`.
  Contenedor `90ce046727a15cb9fd4a79daee7da7f2112fe5430d6f4fcb1fb58e61b9529349`,
  activo, cero reinicios.
- Se utilizó Compose efectivo original más `images.premium.yaml` de esta entrega,
  `--no-deps --no-build --pull never`. No hubo build, pull ni recreación de DB.
- Volúmenes, montajes, redes, puertos internos y alias `vivero-dulcinea-proxy`
  conservados. Caddy HTTP interno validado con su configuración original.
- No se movió `current`: los contenedores activos documentan los cinco archivos
  Compose efectivos en sus labels. Para operar usar ese conjunto completo, con
  el overlay premium; no inferir la versión activa sólo desde `current`.

### Comprobaciones realmente ejecutadas

Antes de abrir Web: API saludable compatible con 033, consulta pública de
productos/categorías 200; rutas protegidas de identidad, Caja, inventario,
permisos, observaciones, referencia de conteo y preparación de productos 401
sin sesión. Se verificaron objetos/grants 033, hashes completos del bundle de
la imagen y Caddy HTTP original antes de publicar el contenedor Web.

Tras publicar, mediante HTTPS:

- 200 en `/`, `/health`, `/login`, `/catalogo`, `/caja`, `/admin`, productos
  y categorías públicos.
- 401 sin sesión en `/api/v1/auth/me`, Caja, permisos de inventario,
  observaciones de conteo y preparación de producto.
- **Los 24 archivos** de la Web servidos por HTTPS coinciden por SHA-256 con el
  manifiesto aprobado, incluidos los módulos de Caja y panel administrativo.
- Catering `/health/ready` 200. Contenedores DB/proxy/Catering mantienen IDs,
  imágenes, inicios y reinicios originales; hashes de configuraciones protegidas
  idénticos. MariaDB saludable y sin reinicio.
- Comprobación final: todos los servicios activos, marcador 033 presente,
  imágenes anteriores disponibles y ninguna señal crítica detectada en logs API
  de `uncaught`, `unhandled`, `fatal`, acceso DB denegado o campo SQL inexistente.

**Acceso a Vivero restablecido.** No se hizo login con credenciales ni consultas
autenticadas de gerente/propietarios; HTTP 200 de pantallas y 401 de protecciones
no acreditan esos flujos. Pedro debe comprobar login/sucursal/roles, menú premium,
fichas de plantas e inventario, recepción exclusiva de propietarios, observación
y revisión de conteos, y Caja POS. Cobro, impresión y operación física siguen
siendo aceptación manual; no se crearon ventas, pagos ni movimientos ficticios.

### Recuperación y límites

Se conservan imágenes anteriores, fuente/configuración productiva y la copia
cifrada nueva `ViveroDulcinea-premium-032-20261010-085834.aesgcm` restaurada/verificada.
No se borraron respaldos ni datos históricos. Volver a imágenes no revierte 033:
una recuperación SQL exige escritores cerrados, evaluación del estado real y
autorización específica; cualquier operación posterior debe conciliarse antes
de recuperar la instantánea 032. El procedimiento aislado documentado se verificó
para ese respaldo 032, no para futuros respaldos ya tomados sobre 033.

Evidencia: `tmp/deploy-premium/authorized-gate.json`, `migrate.json`, `api.json`,
`web.json`, `published-final-state.json`; estado remoto en la carpeta de esta
entrega, `deployment-state.json` con fase `COMPLETE`. No se modificó Android,
no se repitieron compilaciones ni suites locales, no hubo commit ni push.
Sólo se actualizó este informe versionable; el trabajo local previo quedó intacto.
