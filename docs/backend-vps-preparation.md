# Preparación VPS y ensayo local

Estado operativo posterior: estructura multiproyecto, Caddy reutilizado y ensayo
de restauración con datos reales comprobados en el VPS.
[Guía vigente para Pedro y Toni](vps-multiproject-operations.md).

Actualización posterior: VPS contratado y despliegue privado realizado; Docker,
Web/API/MariaDB, HTTPS local y respaldo inicial verificados.
[Estado real y comandos](backend-vps-first-deployment.md). Las afirmaciones
de ausencia de servidor/despliegue que siguen son evidencia histórica.

Actualización de acceso: recuperación, invitaciones y newsletter Web usan API/MariaDB
(migraciones 024/025; esquema vigente 44 tablas/24 marcadores). Contrato de
boletín y clave privada necesaria para restaurar envíos: [backend-newsletter.md](backend-newsletter.md).
Configurar correo
privado después de contratar dominio: [backend-account-links.md](backend-account-links.md).
La evidencia VPS/respaldo de abajo corresponde al esquema anterior 023.


Estado 2026-10-02: infraestructura ensayada localmente, **corte operativo pendiente**.
No usar la imagen actual como sustituto completo de Supabase. Consultar primero
[el estado vigente](backend-complete-cutover.md).

Pedro confirmó que todavía no tiene VPS ni dominio y considera Hostinger.
La recomendación para este proyecto es Ubuntu 24.04 LTS con Docker, plantilla
que Hostinger ofrece con Docker y Compose preinstalados. No se contrató nada ni
se eligió un dominio. La preparación local no depende de esa contratación.
Referencia: [plantilla Docker de Hostinger](https://www.hostinger.com/support/8306612-how-to-use-the-docker-vps-template-at-hostinger/).

## Estructura para Tony

Mantener los dos repositorios hermanos, conservando sus nombres:

```text
proyectos/
  ViveroApp/       # backend, database/mysql, infra/docker, documentación
  ViveroWeb/       # React/Vite y Dockerfile de Web
```

El entorno diario se levanta con `infra/docker/compose.yaml`, siguiendo
[la instalación local](backend-fresh-install.md). El VPS utiliza ese mismo
archivo más `infra/docker/compose.vps.yaml`; no hay otro backend o esquema.

El overlay sustituye el seed demo por `database/mysql/seed-production.sql`,
quita el puerto publicado de API y añade Web/Caddy. Solo Web publica 80/443.
Persisten MariaDB, imágenes y certificados en volúmenes independientes.
Los contenedores se reinician automáticamente y sus logs tienen rotación.
No contiene Supabase keys ni incorpora `.env` al contexto de construcción Web.

## Preparar, sin publicar todavía

Requisitos: Docker Engine/Desktop con Compose compatible con `!reset`, acceso
al registro de imágenes y ambos repositorios completos. No es necesario instalar
Node en el servidor: las imágenes realizan las compilaciones.
La herramienta opcional de respaldo descrita abajo sí requiere Node 24 en el
equipo donde se ejecuta, además del CLI de Docker.

Copiar `infra/docker/vps.env.example` a `.env.vps` (ignorado por Git), asignar
contraseñas independientes y el dominio real. Mantener ese archivo privado.
Desde ViveroApp:

```sh
docker compose --env-file .env.vps -f infra/docker/compose.yaml -f infra/docker/compose.vps.yaml -p vivero-vps config --quiet
docker compose --env-file .env.vps -f infra/docker/compose.yaml -f infra/docker/compose.vps.yaml -p vivero-vps build api web
```

Estos comandos validan y construyen; no publican el sitio. No imprimir `config`
sin `--quiet`, porque incluiría valores privados interpolados. No reutilizar el
proyecto/volumen demo como producción: el seed solo se ejecuta con volumen nuevo.
La base sin demo no tiene sucursales ni cuentas; falta completar y verificar el
proceso de importación/acceso inicial antes de comenzar a operar.

Antes del despliegue real hay que cerrar los pendientes de código/datos, probar
respaldo y restauración de SQL e imágenes, fijar el destino, dominio y DNS,
configurar correo y ejecutar aceptación Web/Android. El dominio debe apuntar al
VPS para obtener un certificado público. No se comprobó ese proceso contra un
servidor real. No ejecutar `down -v`: elimina los volúmenes del proyecto.

## Evidencia local

Se creó `vivero-vps-rehearsal-20261002` con configuración privada en `tmp/`:

- API y MariaDB saludables, sin puertos publicados al host.
- Web solamente en `127.0.0.1:33080` y `127.0.0.1:33443`.
- HTTPS en `https://localhost:33443`, validado con Python y la CA local de Caddy.
  No se instaló la CA en Windows ni se omitió la validación del certificado.
- `/health`, `/login` y `/api/v1/products` respondieron 200.
- Base nueva: 40 tablas, 22 marcadores de migración, seis roles; sin usuarios,
  sucursales, productos, inventario, ventas ni pagos demo.
- Imagen Web compilada dentro de Docker sin configuración Supabase.
  El SDK aún está en el bundle por módulos pendientes: compilar no demuestra
  que esos módulos ya funcionen con la API.

El ensayo no incluye login operativo con datos importados, correo, respaldo/
restauración ni dispositivos Android. Los archivos privados de `tmp/` no se
distribuyen al clonar. Para repetirlo se necesitan credenciales sintéticas propias
y un override local que limite los puertos a loopback y configure
`VIVERO_DOMAIN=localhost` y `WEB_ORIGIN=https://localhost:33443`.

## Respaldo y restauración ensayados con datos sintéticos

`infra/docker/backup.mjs` utiliza el Compose oficial más el overlay VPS.
Se ejecuta en el propio host Docker: rechaza endpoints TCP/SSH remotos para
evitar respaldar otro equipo por accidente. Exige proyecto, archivo privado
de entorno, directorio de destino y aceptación explícita de la pausa.

```sh
node infra/docker/backup.mjs --env-file .env.vps --project vivero-vps --output /var/backups/vivero --acknowledge-downtime
node infra/docker/backup.mjs --verify /var/backups/vivero/CARPETA_GENERADA
```

No se ejecutaron esos comandos sobre producción. Para un ensayo local se puede
añadir `--overlay ruta/al/override-local.yaml` y elegir el proyecto de ensayo.

La herramienta pausa únicamente API/Web que estaban funcionando, mantiene DB
levantada y captura SQL (incluidos triggers) e imágenes durante esa pausa.
Reanuda esos mismos servicios al terminar o ante errores recuperables, sin
arrancar servicios que ya estaban detenidos. Rechaza otros servicios activos
del proyecto, como un importador o migrador. No deben existir escritores
externos ni operadores aplicando SQL durante el respaldo.

Crea una carpeta privada nueva con `database.sql`, `catalog-images.tar.gz`,
`manifest.json` y hashes SHA-256. Solo marca `COMPLETE` al terminar ambos archivos.
Si algo falla, conserva `INCOMPLETE`; no sobrescribe ni borra respaldos anteriores.
La verificación compara hashes, no ejecuta SQL ni extrae archivos. Una copia con
hashes válidos todavía necesita restaurarse en otro entorno y conciliar datos.

Si informa `SERVICE_RESTART_FAILED_CHECK_PROJECT`, revisar inmediatamente
`docker compose ... ps` y reanudar los servicios del proyecto. Una terminación
forzada del proceso o del host también exige esa revisión manual. SIGINT/SIGTERM
normales esperan a terminar el comando en curso antes de intentar reanudar.

Guardar una copia cifrada fuera del VPS. El archivo de entorno privado y las
credenciales deben conservarse por separado; el script no los copia ni imprime.
Los certificados/configuración de Caddy tampoco se incluyen en este respaldo.

`infra/docker/restore.mjs` permite restaurar una copia propia y confiable en otro
proyecto: exige hashes correctos, únicamente DB encendida, tablas operativas sin
filas y volumen de imágenes vacío. Rechaza rutas extrañas o enlaces en el archivo
de imágenes. No borra datos para hacer espacio ni arranca API/Web al terminar.

```sh
node infra/docker/restore.mjs --env-file .env.restore --project vivero-restore --backup-dir /var/backups/vivero/CARPETA_GENERADA --acknowledge-empty-target
```

Antes de ese comando, inicializar solo DB del proyecto nuevo con el Compose
oficial y el overlay VPS. Usar configuración privada propia y un proyecto distinto
al origen. Se puede añadir `--overlay` para la configuración local. El script
restaura SQL con privilegios de mantenimiento: no aceptar archivos de terceros.
Una restauración interrumpida no es atómica; mantener el destino fuera de servicio
y examinar el fallo. No repetirla ciegamente ni borrar volúmenes con datos.

Evidencia nueva del 2 de octubre:

- Sintaxis de ambos scripts y fixture correctas; `npm --prefix backend run
  test:backup`: ocho pruebas de fallos, pausa/reinicio, hashes y protección del
  destino, con transporte Docker simulado.
- Respaldo real de `vivero-vps-rehearsal-20261002` con datos exclusivamente
  sintéticos: sucursal, cuenta desactivada, categoría, producto, venta, pago,
  existencias y una imagen WebP de dos píxeles.
- Restauración en el volumen nuevo de `vivero-restore-rehearsal-20261002`.
  Conciliación: venta/pago 12345 centavos, recibido 20000, cambio 7655;
  existencias 11.125 y mínimo 2.125; archivo de imagen idéntico por SHA-256.
- El fixture `backend/test/backup-fixture.js` verificó 40 tablas y 22 migraciones,
  integridad referencial y el funcionamiento del trigger mediante una recepción
  dentro de una transacción que se revirtió. No duplicó existencias al restaurar.
- Un segundo intento sobre el destino ocupado fue rechazado antes de importar.
- API del destino restaurado comprobada sin publicar puertos: health, catálogo,
  precio e imagen. Web/API del origen reanudadas y verificadas por HTTPS.
- La revisión de permisos volvió a estar disponible; build, 456 pruebas y lint
  Web finalizaron correctamente también después de actualizar el lockfile.

Los dos proyectos y sus copias se conservan en Docker/`tmp/`; no contienen datos
reales. Esta evidencia no sustituye la conciliación del futuro snapshot Supabase,
la copia cifrada fuera del host ni un ensayo con el volumen real de información.

## Archivos de esta entrega

En ViveroApp:

- `backend/src/app.js`, `sales.js`, `catalog.js`, `purchases.js`.
- `backend/scripts/verify-local-install.js`, `backend/package.json`.
- `backend/test/sales-integration.test.js`, `integration.test.js`, `backup.test.js`, `backup-fixture.js`.
- `database/mysql/migrations/022_counter_sale_completion.sql`,
  `023_catalog_branch_minimum.sql`, `database/mysql/seed-production.sql`.
- `infra/docker/compose.vps.yaml`, `Caddyfile`, `vps.env.example`, `backup.mjs`, `restore.mjs`.
- `README.md`, `docs/backend-complete-cutover.md`,
  `docs/supabase-migration-map.md`, esta guía.

En ViveroWeb:

- `src/features/cashier/CounterSaleComposer.tsx`, `counter-sale-service.ts`,
  `counter-sale-service.test.ts`, `backend-counter-sale-service.ts`.
- `src/features/admin/admin-catalog-service.ts`, `admin-catalog-parser.ts`,
  `admin-promotions-service.ts`, `AdminCatalog.tsx`, `AdminPromotions.tsx`,
  `admin-catalog-api.test.ts`, `admin-boundary-service.test.ts`.
- `src/features/admin/purchases/purchases-service.ts`, `purchases-types.ts`,
  `purchases-parser.ts`, `usePurchases.ts`, `usePurchaseDetail.ts`,
  `PurchaseDraftWizard.tsx`, `SupplierCreateModal.tsx`, `purchases-api.test.ts`.
- `src/lib/backend-http.ts`, `Dockerfile`, `.dockerignore`, `package-lock.json`,
  `README.md`.

Los cambios de Android, IDE, APIs preparadas y otros consumidores que ya existían
se conservaron. No se modificó AppCliente, no hubo commit/push/despliegue remoto.

Referencias de configuración:
[merge/reset de Compose](https://docs.docker.com/reference/compose-file/merge/)
y [reverse proxy de Caddy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy).
