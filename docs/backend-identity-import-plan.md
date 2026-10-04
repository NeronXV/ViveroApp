# Preparación de correspondencias de identidad

`backend/scripts/identity-import-plan.js` prepara un informe de solo lectura a
partir del snapshot validado y un inventario privado de usuarios/sucursales del
destino. No abre conexiones, ejecuta SQL ni importa datos.

- Relaciona sucursales por código y usuarios por correo normalizado.
- Solo propone reutilizar cuando nombre, estado, rol y sucursal coinciden.
- Bloquea nombres de sucursal coincidentes con códigos distintos, correos
  duplicados, perfiles inválidos, roles incompatibles y sucursales sin resolver.
- Los nuevos IDs quedan pendientes de asignación por MariaDB; nunca se
  reinterpretan UUID como IDs enteros.
- Las cuentas nuevas requieren restablecimiento de contraseña. Una cuenta
  existente no se modifica ni recibe credenciales del origen.
- El informe expone índices de fila y códigos de conflicto, no correos,
  nombres personales, UUID, contraseñas ni filas del snapshot.

El inventario real del destino se consultó sin escrituras y quedó únicamente
en `tmp/target-identities.json`, ignorado por Git. La propuesta privada está en
`tmp/identity-import-plan.json`. Ninguno constituye una importación ejecutada.

El primer ensayo detectó conflictos de código de sucursal y de adscripción
del OWNER existente. Pedro decidió conservar la cuenta actual en Matriz.
La propuesta posterior reutiliza esa cuenta y sucursal, preserva sus valores
y propone crear la otra sucursal y las cinco cuentas restantes. No quedan
conflictos en el plan de identidad. Las resoluciones privadas registran el hash
del snapshot y sus índices; no deben reutilizarse sobre otra exportación.
La escritura e idempotencia ya se implementaron y ensayaron en bases locales
temporales, como se detalla abajo. No se han alterado cuentas, credenciales
ni sucursales del VPS.

Validación local:

```powershell
node --test --test-isolation=none backend/test/identity-import-plan.test.js backend/test/source-export-preflight.test.js
```

Las pruebas usan datos sintéticos y verifican reutilización, creación propuesta,
ausencia de datos personales en el informe y rechazo de conflictos. Las
correspondencias acordadas se ensayaron con el importador transaccional.

Las resoluciones explícitas solo vinculan destinos existentes: nunca permiten
vincular un correo diferente ni cambiar rol o estado. El plan conserva la
cuenta de destino completa cuando se selecciona esa resolución.

## Importador y ensayo MariaDB

La migración `027_identity_imports.sql` añade dos tablas necesarias de
correspondencias con IDs autoincrementales y claves foráneas reales. El usuario
API no recibe permisos nuevos. El importador `identity-import.js` es una
herramienta administrativa; los consumidores no escriben en estas tablas.

La operación exige clave estable del origen, hash SHA-256 del JSON completo y
resoluciones ligadas al mismo hash. Se ejecuta en una transacción y conserva los
usuarios/sucursales existentes. Las cuentas nuevas tienen `password_hash=NULL`;
no se generan sesiones ni se trasplantan contraseñas. Los timestamps de las nuevas
filas de identidad se asignan al importar; el snapshot privado conserva las fechas
del origen. No es todavía una importación del historial operativo.

Una repetición reutiliza las correspondencias. Si cambian los registros de origen
o los datos de identidad del destino, aborta para revisión; cambios de contraseña
no invalidan las correspondencias porque nunca se lee ni compara su hash.
Un fallo revierte tanto las filas nuevas como sus correspondencias.

### Comando local oficial

Preparar una carpeta privada ignorada con `source.json` (objeto convertido del
CSV) y `resolutions.json` (decisiones con `input_sha256`, `branches`, `users`).
No usar otra exportación con los índices de las resoluciones anteriores.
La carpeta por defecto es `tmp/identity-import`; puede indicarse otra mediante
`IDENTITY_IMPORT_DIR`. Su montaje es de solo lectura y no se crea automáticamente.

Después de aplicar las migraciones en el **entorno local confirmado**:

```powershell
$env:IDENTITY_IMPORT_DIR = (Resolve-Path tmp/identity-import).Path
$sourceHash = (Get-FileHash "$env:IDENTITY_IMPORT_DIR/source.json" -Algorithm SHA256).Hash.ToLowerInvariant()
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm import-identity --dry-run --source-key original-source --expected-sha256 $sourceHash
```

Para el ensayo de escritura local, sustituir `--dry-run` por `--apply` tras revisar
el informe. Mantener la misma clave de origen para las repeticiones. No ejecutar
este comando sobre el VPS como parte de este bloque.

### Validación de esta entrega

- 75 pruebas unitarias del backend correctas.
- Ensayo SQL sintético correcto en una base temporal independiente.
- Ensayo con la exportación privada real y una copia mínima del inventario de
  identidad del VPS correcto, también en una base temporal independiente.
- Se probaron hash incorrecto, reversión por fallo provocado, conservación exacta
  de la cuenta existente (con marcador de contraseña sintético), repetición sin
  duplicados, cuentas nuevas sin contraseña, claves foráneas y rechazo de cambios
  posteriores en origen/destino. No se copió la contraseña real del VPS.
- Ambas bases temporales se eliminaron al finalizar. Los datos reales del ensayo
  no se incluyeron en código, pruebas, informes versionados ni logs.

Docker Compose intentó recrear redes locales existentes y falló por sus endpoints
activos. No se retiraron contenedores ni redes; los ensayos se ejecutaron con
`docker run` sobre la red existente. Esto no certifica un arranque limpio nuevo.

Quedan la importación de catálogo/historial/inventario/imágenes y la conciliación
completa. No se aplicó la migración 027 ni la importación en el VPS.
