# Validación real de API/MariaDB — 2026-09-29

## Resultado

Docker Desktop se recuperó y el proyecto arrancó con MariaDB y API saludables.
Se comprobaron instalación nueva, actualización desde fase 1, conservación de
datos, bootstrap, sesiones/permisos, catálogo y persistencia después de reiniciar.
Las cuatro pruebas de integración pasaron en ambos escenarios: ocho ejecuciones
aprobadas, no ocho pruebas distintas. No se cambió código de API ni SQL para
hacerlas pasar; la corrección necesaria fue la red de publicación de Compose.

Esto acredita los escenarios locales descritos. No acredita migración de cuentas
reales, integración Web/Android, recuperación de cuentas, despliegue ni preparación
completa de producción. Supabase sigue atendiendo los consumidores existentes.

## Entorno y aislamiento

- Docker Engine 29.7.2, Compose v5.4.0, contenedores Linux.
- MariaDB `11.4.13-MariaDB-ubu2404`, Node `v24.21.0` en la imagen.
- Proyecto `vivero-validation-20260929`: volumen nuevo; puerto loopback 33001.
- Proyecto `vivero-upgrade-20260929`: baseline de fase 1 y migración incremental;
  puerto loopback 33002. Se usó un override temporal que sustituía únicamente el
  montaje de inicialización de identidad por un SQL vacío durante el primer arranque.
- Cada proyecto tuvo red, volumen y credenciales aleatorias propias, generadas
  en archivos ignorados de `tmp/`. No se leyó ni modificó el `.env` del usuario.
- No son backends alternativos del producto: son dos escenarios aislados que
  ejecutan el mismo Compose/código oficial. No se modificaron servicios Supabase.

## Recuperación de Docker

El intento de arranque confirmó errores de acceso a sockets residuales de Windows.
Con el backend detenido, se conservaron por renombrado las carpetas temporales:

- `%LOCALAPPDATA%/Docker/run.stale-20260929-2137`.
- `%LOCALAPPDATA%/docker-secrets-engine.stale-20260929-2137`.
- `%LOCALAPPDATA%/Docker/run.stale-20260929-2139`, generada tras otro intento fallido.

Las carpetas inspeccionadas contenían sockets vacíos; no archivos de credenciales.
El intento de renombrar el socket individual falló sin modificarlo. Después se
renombró su directorio y se recrearon las rutas temporales. Se cerró únicamente
el lanzador fallido iniciado durante esta tarea. No se borraron volúmenes,
configuración ni respaldos, ni se cambiaron ACL/permisos. Docker pudo arrancar
cuando ambas carpetas temporales quedaron libres de sockets residuales.
No repetir este procedimiento con Docker activo ni extrapolarlo a otros errores.

## Defecto corregido

La API arrancaba y superaba su health check interno, pero no era accesible desde
Windows. `HostConfig.PortBindings` contenía el puerto solicitado y
`NetworkSettings.Ports` mostraba una lista vacía. Estaba conectada exclusivamente
a `database`, una red `internal`.

Se agregó la red bridge `api` al servicio API. La base continúa únicamente en la
red privada `database`. Después de recrear API se confirmó:

- Publicación efectiva `127.0.0.1:33001 -> 3001/tcp`.
- `GET /health` desde Windows: HTTP 200, database `mariadb`.
- MariaDB: `HostConfig.PortBindings` vacío, sin puerto publicado.

Referencia de configuración: [redes de Compose](https://docs.docker.com/compose/how-tos/networking/).

## Escenarios ejecutados

| Escenario | Evidencia / resultado |
|---|---|
| Instalación desde cero | Imagen construida con npm ci, SQL/seed/grants/002_identity inicializados; db/api healthy |
| Integración sobre instalación nueva | 4/4 pruebas aprobadas: CRUD, restricciones/grants, sesiones/revocación, límites de login |
| Bootstrap inicial | Creó un OWNER sintético sin imprimir credenciales |
| Segundo bootstrap | Salida 1 esperada; rechazado sin sobrescribir la cuenta |
| Migrador sobre instalación nueva | `002_identity: already applied` |
| HTTP desde Windows | Login, contexto OWNER, alta de categoría y producto correctos |
| Reinicio de db/api | Mismo usuario y token válidos; producto y precio preservados |
| Después del reinicio | Actualización, baja lógica y logout correctos; token revocado devuelve 401 |
| Baseline aislado de fase 1 | 9 tablas, antes de incorporar identidad; se agregaron producto/categoría/inventario sintéticos |
| Migración incremental | `002_identity: applied`; 14 tablas finales, 18 capacidades, 59 asignaciones rol/capacidad |
| Conservación | CHECKSUM TABLE idéntico para las 9 tablas previas; datos sintéticos adicionales incluidos |
| Integración sobre base actualizada | 4/4 pruebas aprobadas de nuevo |
| Segundo migrador sobre actualización | `002_identity: already applied` |

Los checksums verifican conservación en este fixture, no equivalen a una prueba
universal de migración de cualquier base. No se probaron fallos parciales de DDL
ni bootstrap concurrente. Las pruebas de permisos/revocación reales son las de
`backend/test/integration.test.js`, sin simular MariaDB.

## Reproducir el camino normal

Desde la raíz de ViveroApp, preparar `.env` conforme a `.env.example`. No usar
credenciales del entorno operativo. Docker debe estar ejecutándose:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml config --quiet
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up --build -d --wait api
curl.exe --fail http://127.0.0.1:3001/health
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Para el propietario inicial y el recorrido de sesión seguir
[backend-identity.md](backend-identity.md). Las pruebas de esta sesión usaron
`--env-file tmp/backend-validation-20260929.env -p vivero-validation-20260929`
y el equivalente `backend-upgrade-20260929.env` / `vivero-upgrade-20260929`.
Esos archivos ignorados contienen credenciales solo de pruebas y no son parte
de un checkout nuevo. El script HTTP temporal revocó y eliminó su token guardado.

## Alcance del cambio y estado final

Cambio funcional: `infra/docker/compose.yaml`, red adicional para publicar API.
Documentación: README, guías de backend/identidad, mapa de migración, enlaces en
informes históricos de fases 1/2 y este informe. No cambian API, SQL, dependencias,
Android ni Web. Se conservaron todos los cambios preexistentes, incluidos los
archivos no versionados de las fases anteriores, auditorías, IDE y automatización.

Archivos modificados en esta sesión:

- `infra/docker/compose.yaml`.
- `README.md`.
- `docs/backend-api-mariadb.md`.
- `docs/backend-identity.md`.
- `docs/supabase-migration-map.md`.
- `docs/backend-phase1-validation.md`.
- `docs/backend-phase2-validation.md`.
- `docs/backend-local-validation.md` (nuevo).

Al finalizar se detuvieron ambos proyectos de prueba con `compose stop`,
conservando contenedores y volúmenes para inspección. Docker Desktop permanece
disponible. No se dejó una API de pruebas escuchando ni se borraron volúmenes.

No se repitieron las 9 pruebas unitarias ni las 134 comprobaciones estáticas
Supabase: son evidencia de la sesión anterior; no cambió el código que cubren.
Tampoco se ejecutaron Gradle, Web ni pgTAP, pues no hubo cambios en esos módulos.
Se validó Compose, el diff y el estado Git final. Rama `main`, HEAD
`a4621f6d42deb735714c047b05789ee83760939a`; cambios sin staging, sin commit ni push.
No hubo despliegue, importación de datos reales, modificación remota ni n8n.

Siguiente módulo: completar catálogo operativo y plan de correspondencia de IDs
y pedidos, conservando login/ventas/caja de Supabase hasta un corte coordinado.
