# Instalación integral desde cero: bloque 022

Validada el 2026-10-01 con Docker Desktop 29.7.2, MariaDB 11.4.13 y Node 24.
Se usa el Compose oficial, no otra arquitectura ni otro backend. Proyecto de
pruebas aislado con puerto 33002 y volúmenes vacíos; entorno anterior 33001
preservado. Solo datos sintéticos y configuración ignorada por Git.

## Corrección del arranque inicial

El primer volumen vacío falló en 014: el cliente MariaDB interpretaba los `;`
internos de un trigger como fin de sentencia. Las instalaciones actualizadas por
mysql2 no sufrían ese fallo porque envían SQL por protocolo. No se modificó el
SQL autoritativo de las migraciones.

Compose ahora monta el directorio de migraciones completo y ejecuta
`infra/docker/init-migrations.sh` después de schema, seed y grants. El script
recorre los archivos en orden y añade delimitadores exclusivamente a los triggers
compuestos al transmitirlos al cliente. Tolera SQL LF/CRLF, falla si queda un
trigger incompleto y usa las funciones del entrypoint oficial MariaDB. Funciona
tanto cargado por el entrypoint como ejecutado por él como proceso hijo.
`.gitattributes` mantiene los scripts shell en LF al clonar en Windows.
No introduce una copia del esquema ni cambia el migrador incremental.

Segundo intento detectó que el script hijo necesitaba inicializar las variables
de conexión del entrypoint; corregido con docker_setup_env. Intento final
vivero-fresh-20261001c arrancó desde volúmenes nuevos con todas las migraciones.
Los dos intentos fallidos se conservaron para inspección, sin resets ni borrados.

## Resultado comprobado

- Arranque saludable desde cero; 39 tablas, 19 marcadores 002–020.
- `migrate` posterior informa todas already applied, sin repetir DDL.
- 21/21 pruebas de integración y 55/55 unitarias aprobadas en el entorno nuevo.
  Fixtures eliminados. Se probó último OWNER bajo concurrencia antes del bootstrap.
- Primer OWNER creado por bootstrap; segundo bootstrap rechazado (exit 1 esperado).
- Script `backend/scripts/verify-local-install.js` aprobó esquema/seeds,
  sucursal DEMO sin activar inventario, dos productos demo con stock 10,
  ventas/pagos/pedidos/compras vacíos, login OWNER, capacidades, catálogo,
  reportes, logout e invalidez de sesión revocada.
- Stop/up y segunda ejecución del verificador aprobados: datos y cuenta persisten.
- Sintaxis, Compose, git diff --check y 134 controles estáticos Supabase aprobados.
  Estos últimos no validan MariaDB; esa evidencia viene de la instalación/pruebas.
- Android/Web/pgTAP no se ejecutaron: no se modificaron consumidores/PostgreSQL.
  Tampoco importación real, VPS ni despliegue.

## Para Tony: instalación local

La copia recibida debe contener estos cambios. Siguen sin commit/push: clonar el
remoto actual no garantiza recibir el backend nuevo hasta que se publique con
autorización. Requisitos: Git, Docker Desktop con motor Linux disponible;
Node 24 solo si se desea ejecutar comandos npm fuera de Docker.

Desde la raíz de ViveroApp, copiar `.env.example` a `.env` (ignorado por Git),
reemplazar ambos passwords por valores aleatorios independientes y elegir
API_PORT libre. Para primer acceso, habilitar los campos BOOTSTRAP de la plantilla:
email sintético, nombre, branch_code DEMO y password único de al menos 15 caracteres.
No versionar `.env` ni mostrar sus valores. No usar credenciales operativas.

```powershell
docker desktop start
docker compose --env-file .env -f infra/docker/compose.yaml config --quiet
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --rm tests npm test
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm bootstrap
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm bootstrap node scripts/verify-local-install.js
```

El verificador es solo para base de **demo recién instalada**, no para una base
operativa con ventas/datos propios; requiere configuración BOOTSTRAP y no imprime
tokens/passwords. Usa el servicio bootstrap como transporte de esas variables,
pero el comando reemplazado no vuelve a crear la cuenta. Solo consulta datos y
comprueba una sesión real. Bootstrap no se repite en instalaciones existentes.

Backend en backend/src; migraciones en database/mysql/migrations; infraestructura
en infra/docker; contratos de módulos en docs/backend-*.md. API expuesta solo en
127.0.0.1:API_PORT; MariaDB no tiene puerto publicado. Web/Android aún no apuntan
a esta API. El seed nunca genera pagos ni activa inventario operativo.

En volúmenes existentes init-migrations.sh no vuelve a ejecutarse: usar migrate
para actualización normal. Si el primer arranque de una base nueva queda a medio
inicializar, no asumir que restart completó la instalación; revisar logs/esquema.
Esta prueba usó otro proyecto con volumen vacío y conservó el fallido.

## Entornos de esta prueba

Archivo local ignorado tmp/fresh-install-20261001.env; no está en la entrega.
Intentos fallidos: vivero-fresh-20261001 y vivero-fresh-20261001b.
Instalación final: vivero-fresh-20261001c. Se detienen sus contenedores al terminar,
conservando todos los volúmenes. Entorno previo vivero-validation-20260929 queda
activo y saludable. No se usó down -v, prune, cambio de ACL ni reset.

Archivos: .gitattributes, infra/docker/compose.yaml,
infra/docker/init-migrations.sh, backend/scripts/verify-local-install.js,
backend/package.json, esta guía, README.md, docs/backend-api-mariadb.md y
docs/supabase-migration-map.md. Trabajo previo preservado; sin staging/commit/push.

Siguiente paso: coordinar la conexión de consumidores por módulo, empezando por
identidad/sesión y catálogo, con correspondencia de IDs y rollback documentado.
Preparación y despliegue VPS siguen pendientes hasta contar con el servidor.
