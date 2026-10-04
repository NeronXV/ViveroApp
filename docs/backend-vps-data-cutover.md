# Importación de datos al VPS

Continuación posterior del 3 de octubre: estructura multiproyecto en `/srv/apps`,
red para el Caddy compartido y restauración del respaldo real en proyecto aislado
verificadas. [Guía operativa y pendientes actuales](vps-multiproject-operations.md).
La restauración indicada abajo como pendiente pertenece al cierre de importación.

## Resultado verificado: 3 de octubre de 2026 (America/Mazatlan)

Pedro autorizó usar el perfil oficial de mantenimiento y la configuración privada
existente para comprobar, respaldar y aplicar la migración. La importación al VPS
se completó, conservando la cuenta inicial. Esto no certifica la migración de todos
los consumidores ni autoriza apagar Supabase.

- Migraciones 027, 028 y 029 aplicadas: 28 marcadores hasta
  `029_inventory_imports`; las 25 anteriores se reconocieron sin repetirse.
- Identidad: seis cuentas y dos sucursales. La cuenta OWNER inicial conserva
  identidad, rol, sucursal, estado y hash de contraseña. Las cinco cuentas nuevas
  mantienen sus roles de origen y requieren restablecimiento de contraseña.
- Catálogo: dos categorías y quince productos. Cero imágenes importadas, por
  instrucción de Pedro; la referencia original permanece en el snapshot privado.
- Historial: catorce ventas, dieciocho partidas, catorce pagos, cuarenta y dos
  estados y quince reclamaciones cerradas. Totales de cobrado, recibido y cambio
  conciliados exactamente contra el origen, sin reproducir cobros operativos.
- Inventario: veinticinco proyecciones, treinta y ocho movimientos y seis conteos.
  Cantidades y mínimos conciliados por producto/sucursal. Las dos proyecciones
  adicionales tienen saldo cero y conservan mínimos. Stock operativo desactivado.
- Revisión posterior de los cuatro importadores en simulación: todas las filas
  reutilizables, sin conflictos. Se verificaron las huellas de registros completos,
  incluidos traspasos y fechas, sin volver a escribir el lote.

### Ejecución y comprobaciones nuevas

El archivo transferido coincidió con SHA-256
`a4f4a643cdf47b03937917319732dcd875f661fa9ab53c2fb35876dd6cec55ef`.
Se verificaron 154 archivos de fuentes/datos contra su manifiesto. El código
`backend/src` coincide con el desplegado: no se reemplazaron las imágenes activas
de API/Web. La imagen de mantenimiento construida en el VPS pasó `npm test`
(83 casos) y `npm run check`; Compose pasó `config --quiet`.

Web/API se pausaron durante el corte y se reanudaron después de conciliar.
MariaDB permaneció activa. La primera ejecución del migrador no podía leer el
directorio privado; se comprobó que seguía en 026 sin tablas parciales y se usó
el usuario de mantenimiento autorizado, conservando los permisos privados.
Una comprobación auxiliar asumía un único OWNER; el origen también contiene
otros OWNER legítimos. Se corrigió para comparar la cuenta inicial por ID; no
se cambiaron roles ni se repitió la importación para resolver esa comprobación.

HTTPS con certificado público comprobó login, contexto OWNER/Matriz, capacidades,
catálogo importado, inventario, historial, respuesta de recibos y logout con
revocación. Las sesiones de prueba se cerraron. Tras el respaldo posterior se
repitió esa comprobación; `/health` y `/login` también respondieron 200 desde
fuera del VPS. API y MariaDB saludables; Web activa en 80/443.

### Respaldo y fuentes de mantenimiento

Respaldos conservados, ambos con hashes SQL/imágenes verificados:

- Antes: `/var/backups/vivero/vivero-2026-10-03T17-00-35-882Z-46abc464-5677-4afd-922b-2d93ec458bdb`.
- Después: `/var/backups/vivero/vivero-2026-10-03T17-05-15-682Z-2abf2b0c-e059-4439-999f-cb4b3f28ae44`.

Paquete, snapshot y reportes privados en
`/opt/vivero/maintenance/20261002-data-cutover/`. No contienen una exportación
de contraseñas del origen. La huella de conservación del OWNER se calculó
en el servidor; no se extrajeron credenciales. La revisión automática rechazó
descargar el informe agregado de conciliación; permaneció en el VPS.

Se actualizaron 33 archivos de mantenimiento, pruebas, Compose y migraciones en
`/opt/vivero/releases/20261002-initial/ViveroApp`, preservando versiones anteriores
en `previous-source/` dentro del directorio de mantenimiento. No se tocaron
archivos `.env`, secretos, fuentes Web ni código Android. El archivo fuente del
primer despliegue se conserva como evidencia histórica, no como la fuente actual
de mantenimiento.

### Pendientes fuera de este corte de datos

Subida manual de la imagen; recuperación de las cinco cuentas nuevas y correo
real; aceptación UI/dispositivos y revisión antes de activar stock; respaldo
cifrado externo y ensayo de restauración con los datos reales. No se hizo ese
ensayo de restauración en el VPS. La validación local anterior no lo sustituye.
Sin compilaciones Android/Web ni pgTAP nuevos: no cambiaron sus fuentes ni SQL
PostgreSQL. Sin commit ni push; hubo migración/importación remota autorizada.

## Preparación anterior (evidencia histórica)

Continuación del 2 de octubre de 2026 (America/Mazatlan).

Pedro confirmó que no hubo operaciones posteriores a la exportación privada
recibida y que puede utilizarse para el corte. Indicó excluir la única imagen
del origen: la subirá manualmente después. El snapshot original conserva su
referencia; no se descarga, importa ni sustituye por una imagen demo.

## Paquete local

`tmp/cutover-data-ready/`, ignorado por Git, contiene la exportación intacta,
resoluciones de identidad ligadas a su hash, inventario anterior del destino,
catálogo adaptado, metadatos y reportes de conciliación. `manifest.json` registra
SHA-256 de nueve archivos, confirmación del snapshot, exclusión de la imagen
y requisito de migración `029_inventory_imports`. No compartir estos archivos:
contienen datos personales y operativos. No incluyen contraseñas ni sesiones.

- Identidad: dos sucursales y seis cuentas; el plan previo no tiene conflictos.
  Revalidado contra el VPS en esta continuación: reutilizar la cuenta OWNER y
  sucursal actuales, crear otra sucursal y cinco cuentas. Sin conflictos.
- Catálogo: dos categorías y quince productos.
- Historial: catorce ventas, dieciocho partidas, catorce pagos, cuarenta y dos
  estados y quince reclamaciones cerradas.
- Inventario: veintitrés saldos originales, treinta y ocho movimientos, seis
  conteos y once pares de traspaso. El destino tendrá veinticinco proyecciones:
  dos adicionales con cantidad cero conservan mínimos de producto por sucursal.
- No hay filas operativas en los otros módulos del snapshot que requieran otro
  importador. Roles/permisos se resuelven con los contratos oficiales del backend;
  no se copian literalmente las tablas de permisos del origen.

## Validación ejecutada en esta continuación

Dieciocho pruebas específicas de preflight, identidad, catálogo, historial e
inventario pasaron con Node 24. El ensayo conjunto
`backend/test/inventory-import-integration.test.js` pasó con la copia privada
real sobre la red local `vivero-cutover-20261001_database`, en una base temporal
independiente eliminada al finalizar. Usó las fuentes actuales montadas en
solo lectura y las migraciones hasta 029: 55 tablas y 28 marcadores.

Comprobó importación conjunta, saldos y mínimos exactos, traspasos, conteos sin
ajuste, reversión por fallo provocado, repetición sin duplicados y rechazo de
cambios posteriores. No activó inventario ni repitió pagos como operaciones
nuevas. El ensayo omite GRANT/REVOKE para conservar los permisos locales
compartidos; no acredita aplicar los privilegios en un arranque nuevo.

Los hashes de los nueve archivos iniciales se verificaron. Después se añadieron
el inventario actual autorizado del VPS y su plan de identidad, con sus hashes.
El manifiesto registra por separado que falta la inspección administrativa.

La consulta SSH de servicios confirmó `vivero-vps` activo: API y MariaDB
saludables, Web en 80/443 y sin puertos publicados de API/DB. No equivale a
verificar login, contenido o aceptación operativa. Tras autorización explícita
de Pedro se guardó el inventario de identidad únicamente en `tmp/` y se comprobó
la conservación del OWNER. Hay una cuenta, una sucursal y cero categorías,
productos y ventas. La cuenta API denegó el SELECT de pagos: no se certifican
sus conteos ni los restantes conteos restringidos con esta consulta.

La revisión automática rechazó obtener la contraseña root desde el entorno
del contenedor para ampliar la inspección. No se ejecutó ese acceso ni se
cambiaron permisos. Antes de continuar con operaciones administrativas debe
autorizarse el uso del perfil de mantenimiento oficial y su configuración
privada existente; no extraer ni imprimir secretos. No se ejecutaron cambios
remotos de esquema ni importaciones.

## Secuencia pendiente

1. Completar los conteos restringidos y comprobar la versión real del esquema
   con el perfil de mantenimiento autorizado. Volver a comprobar identidad si
   cambia el destino entre esta inspección y la escritura.
2. Preparar la actualización de fuentes y migraciones 027–029; verificar hashes
   y comandos del Compose oficial. Respaldar SQL/imágenes antes de modificar.
3. Pausar escritores durante el corte; aplicar migraciones y luego identidad,
   catálogo, historial e inventario, en ese orden, con la misma clave estable
   `original-source`. Revisar cada simulación antes de su aplicación. No ejecutar
   el importador de imágenes. Ante resultado incierto, recuperar con el mismo
   lote/clave, sin asumir rollback ni crear nuevas claves.
4. Conciliar correspondencias, importes, existencias, mínimos y cuenta existente;
   verificar API/Web y crear un respaldo posterior. Activación de inventario,
   restablecimiento de cuentas nuevas, correo real y aceptación en dispositivos
   requieren sus propias comprobaciones. No apagar Supabase por este ensayo.

No se ejecutaron compilaciones Android/Web: esta continuación no modifica sus
fuentes. No hay SQL nuevo que requiera verificación estática PostgreSQL o pgTAP.
El trabajo preexistente del repositorio permanece conservado. Sin commit ni push.
