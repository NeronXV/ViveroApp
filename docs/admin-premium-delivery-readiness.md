# Panel premium — entrega preparada, sin publicación

10 de octubre de 2026. **GO técnico local, condicionado a autorización,
verificación del destino y un respaldo productivo nuevo recuperable.**
No se accedió al VPS ni se modificó producción en esta preparación.

## Imágenes verificadas

| Servicio | Imagen preparada | ID inmutable |
|---|---|---|
| API | `vivero-api-premium:20261010-2502d7f8f5f0` | `sha256:29dec291775828f18e7a4e5d9452b8a8dca1b73ab016fad0546cf4314c20efb1` |
| Web | `vivero-web-premium:20261010-2502d7f8f5f0` | `sha256:aea52cabcad1c6d76a9be8e5f5200786db57624845a8dd2f140a464900d4ea54` |

Entrega pública local: `tmp/premium-release/package/`. Manifiesto con hashes,
archivos fuente exactos, bundle, imágenes `.tar`, runtimes fijados, fuente
congelada en `sources.zip`, contextos exactos en `build-contexts.zip`, Dockerfiles
y overlay limitado a API/Web/migrate. Los ocho artefactos y las tres familias de
fuentes se verificaron nuevamente por SHA-256 al cerrar la preparación.
Los datos del respaldo, configuración privada y claves quedan fuera del paquete.

La API reutiliza las dependencias bloqueadas del runtime publicado: hash del
lockfile idéntico. Se sustituyen únicamente fuentes/scripts y package.json.
La Web empaqueta el bundle aprobado del pulido final, con su Caddy HTTP interno
validado; los 24 archivos servidos coinciden exactamente. Construcción local sin
red con IDs base fijados, usando el builder clásico que admite IDs locales.
Para reproducción cargar los runtimes exportados y usar `DOCKER_BUILDKIT=0`;
para publicar cargar las imágenes verificadas, sin reconstruir en el VPS.

## Alcance exacto y compatibilidad

- Nueva migración `033_inventory_preparation.sql`, SHA-256
  `e6ad3f436343610a4d6d2115166cc695e79b2899e6eac8d41b408120ee011187`.
- API: 15 archivos fuente/runtime distintos de la imagen piloto publicada;
  permisos efectivos, observaciones/revisión de conteos, preparación comercial
  y validación de recepción/compras/productos. Sin cambios de dependencias.
- Web: 24 archivos fuente distintos del snapshot POS publicado, incluyendo
  pruebas; navegación, fichas, permisos visuales y nuevos contratos de inventario.
  Las listas exactas están en `manifest.json` (`api_runtime_changes`,
  `web_runtime_changes`). Fuentes de Caja POS idénticas a las publicadas.
- 033 → API → Web forman una entrega conjunta: health nuevo exige 033. No
  publicar la Web premium contra la API anterior.
- Android no necesita cambios para catálogo, ventas y recuperación existentes.
  El conteo directo antiguo recibe `INVENTORY_OBSERVATION_REQUIRED`; usar Web.
  No borrar Room ni reprocesar intentos antiguos: conciliarlos antes. Esta
  incompatibilidad deliberada no se presenta como soporte completo de conteos
  Android. No se compiló ni modificó Android en esta preparación.
- Históricos de precio positivo mantienen `LEGACY`; precio cero requiere
  preparación antes de nuevas ventas. No se borran ni se renumeran operaciones.

## Ensayos realmente realizados

- Copia externa cifrada del piloto, `tmp/pilot-backup/ViveroDulcinea-final-20261010.aesgcm`:
  SHA-256 `5b43027a152b634f99ce8ae39920c25bc1bd22b937354c5b1ab8d6ead9e4a11b`.
  Descifrado autenticado y hashes completos verificados; clave DPAPI separada,
  sin mostrarse ni incorporarse al paquete.
- Restauración nueva en Compose local vacío, redes internas y sin puertos:
  55 tablas con conteos/checksums exactos, imágenes, columnas, triggers, rutinas,
  eventos y grants iguales al respaldo. Cuenta técnica restaurada desde su
  definición privada; root mediante configuración de bootstrap conservada.
- El respaldo fue tomado **antes** del piloto 030–032: contiene esquema 029.
  Los guards detectaron que inicializar un destino más nuevo deja objetos y
  permisos adicionales. Se restauró finalmente con su esquema original,
  reproduciendo grants sin acumulaciones. Destinos de ensayo anteriores se
  conservaron detenidos; no hubo borrados ni reparaciones productivas.
- Esa copia aislada se llevó a 032 con el migrador canónico y después a 033,
  incluyendo grants reales, sin omitirlos. 033 tardó **361 ms localmente**.
  Los campos históricos de productos y los otros 59 conjuntos de tablas de la
  referencia 032 comparados conservaron sus datos. Roles/permisos de usuarios,
  ventas, inventario y registros históricos permanecieron iguales.
- Imagen API candidata: 11 pruebas de integración MariaDB/API de inventario,
  recepción, permisos, concurrencia con pago, devolución, recuperación y
  compatibilidad; 2 de sesiones/login. Todas aprobadas, con datos sintéticos.
- Web: 43 pruebas de contratos, preparación y regresión de Caja POS aprobadas
  en cinco archivos. Build/lint y revisión visual son evidencia previa del
  pulido final aprobado; no se repitieron esas compilaciones ni suites masivas.
- Ambas imágenes juntas: `/`, `/login`, `/caja`, `/admin`, `/health`, productos
  y categorías responden 200 mediante Caddy HTTP interno. No acredita HTTPS,
  login humano ni impresión física productivos.
- Ensayos detenidos; fuentes aprobadas comparadas con contenido de las imágenes.
  Evidencia: `tmp/premium-release/backup-migration-rehearsal.json`,
  `functional.json`, logs focalizados y manifiesto del paquete.

## Respaldo nuevo y recuperación independiente

El respaldo anterior está comprobado, pero **no sustituye uno actual antes de
033**. Después de autorización y con escritores de Vivero cerrados:

1. Confirmar operaciones drenadas y detener sólo API/Web de Vivero. Mantener
   cerradas las escrituras hasta completar backup, migración y verificaciones.
2. Dump MariaDB con `--single-transaction --routines --triggers --events
   --hex-blob`; incluir esquema/datos, fotos, definición de cuentas/grants/
   definers, entorno privado, overlays y configuración, imágenes anteriores.
   El helper `infra/docker/backup.mjs` cubre SQL/fotos, **no todo el suplemento**;
   conservar el procedimiento completo del piloto para cuentas/configuración.
3. Copia cifrada en la PC autorizada, `tmp/pilot-backup/`, con clave separada.
   Verificar autenticación de cifrado y todos los hashes. No borrar copias previas.
4. Restaurar en destino vacío de la **misma versión/esquema del nuevo respaldo**,
   sin puertos ni contacto con producción. Verificar todas las tablas, imágenes,
   objetos, grants/cuentas y definers. Healthchecks regenerados por bootstrap
   deben funcionar; conservar las definiciones originales en el suplemento.
5. Si falla la copia externa o restauración, **NO-GO: no aplicar 033**.

Volver a una imagen anterior no revierte SQL. Ante DDL parcial: conservar estado,
cerrar escritores y detenerse; no reejecutar ni ejecutar un down SQL a ciegas.
La recuperación de datos usa una instancia/volumen nuevo restaurado y verificado,
sin sobrescribir el original; requiere decisión/autorización específica.
Si se abrieron operaciones después, conciliarlas antes de cambiar a un respaldo.
Revertir sólo Web a POS es una decisión distinta; no volver a la API antigua
sin evaluar las protecciones y productos nuevos del esquema 033.

## Orden exacto de publicación, todavía no ejecutado

1. **Autorización expresa de Pedro** para mantenimiento, respaldo actual y
   publicación coordinada en Hostinger **2030059 / srv2030059**.
2. Confirmar VPS, imágenes activas y migraciones. Según última publicación:
   API `6f3edb73…`, Web POS `02000e02…`, MariaDB 11.4.13 hasta 032.
   No se hizo inventario remoto nuevo. Si sólo 033 no está pendiente, detenerse.
3. Capturar IDs/inicios/redes/montajes de MariaDB, `platform-proxy` y Catering;
   hashes de overlay host/Caddy HTTP/proxy. Conservar imágenes activas para volver.
   Transferir paquete, verificar hashes e IDs y cargar imágenes sin pull/build.
4. Mantener proyecto `vivero-vps`, entorno `shared/.env.vps` y los cuatro Compose
   efectivos del contenedor vigente, incluidos `ops/compose.host.yaml` e
   `images.yaml`. Agregar **sólo** `images.premium.yaml` preparado al final.
   Verificar configuración resuelta sin imprimir secretos: ningún cambio de
   DB, redes, volúmenes, puertos o alias `vivero-dulcinea-proxy`.
5. Abrir mantenimiento, detener únicamente Web/API y completar el respaldo
   externo/restauración anteriores. **GO/NO-GO antes de migrar.**
6. Ejecutar migrador canónico con candidato fijado y SQL congelado de la entrega:
   `compose run --rm -T --no-deps --pull never migrate`. Debe omitir hasta 032
   y aplicar únicamente 033. Comprobar marcador, objetos y conservación de datos.
7. Actualizar únicamente API: `compose up -d --no-deps --no-build --pull never api`.
   Verificar health/033, contratos y ausencia de errores críticos.
8. Actualizar únicamente Web con las mismas opciones. Verificar bundle por hash,
   HTTPS, catálogo y rutas. Pedro revisa login, roles/sucursal, Caja POS y panel
   premium autenticados; no crear ventas/cobros ficticios automáticos.
9. Confirmar Catering disponible y proxy/DB/Catering sin cambios de contenedor,
   inicio, imagen ni configuración. Abrir operación después de aceptación mínima.

En esos comandos `compose` significa la invocación completa validada de los
archivos anteriores, no un `docker compose up` genérico. El staging previsto es
`/srv/apps/vivero-dulcinea/releases/20261010-premium-2502d7f8f5f0/`; verificarlo
antes de usar el overlay. No mover automáticamente `current`, recrear MariaDB,
reiniciar el proxy, detener Catering ni ejecutar limpiezas Docker.

## Pendientes imprescindibles y tiempo

- Autorización, inventario actual del VPS, respaldo nuevo verificado y cuentas
  OWNER/sucursales utilizables. No se conceden permisos automáticamente.
- Reservar **15–30 minutos de mantenimiento**, estimación de planificación;
  depende del volumen actual, transferencia y restauración. Los 361 ms de DDL
  local no son una medida de indisponibilidad productiva.
- Aceptación autenticada y prueba física de cámara/impresión pendientes; las
  listas reales se cargarán después, en una operación separada y autorizada.

Sólo este informe es nuevo versionable en esta preparación; scripts, snapshots,
respaldos y artefactos quedan ignorados. Ambos repositorios siguen en `main`,
con trabajo previo conservado: App `239411caeefc715feb55c9a8fa21163c218a9fde`,
Web `f208ffb1dabf8db5d740a5c17cab7a09d33236d6`. Sin commit, push, Android,
SSH, migraciones remotas, despliegue o modificación de producción/Catering/proxy.
