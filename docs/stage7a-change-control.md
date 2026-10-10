# Control de cambios 7A

Los HEAD y ramas permanecen iguales a la línea base: App `main`/`a58fd614b28920d7b39fee7cef2b67f1c16b0531`; Web `main`/`5f9c4853e42cace586cdaeec13998668b3d204aa`. No hubo add, commit, push, nueva rama, reset, clean, pull, merge o rebase.

Antes del ensayo se inventariaron 58 caminos de App y 44 de Web con copia byte a byte y SHA-256, incluyendo archivos nuevos y borrados. El inventario final [CSV](stage7a-change-inventory.csv) distingue **todos** esos caminos de los cambios nuevos. La auditoría local `tmp/integration-stage7a/preservation.json` verifica sus hashes y estado de borrado; ninguno se sobrescribió. No se usaron los artefactos generados para ocultar el Git sucio.

## Trabajo preexistente protegido

- Etapa 2 Web: comprobante público, recuperación, coordinador del pedido, impresión CSS y pruebas/PDF. Fuente/backend del ticket y snapshots compartidos también preexistentes.
- Etapa 3: migración 031, `short-folios.js`, contratos/negociación backend y clientes Kotlin/React, buscadores, comprobantes y pruebas de actualización/concurrencia. Folios originales se conservan.
- Etapa 4: migración 032, cancelación/auditoría backend, capacidades, servicio/panel de cancelación y diarios/pruebas Web.
- Etapa 5: componentes y CSS de Caja, coordinación del estado, diarios y lenguaje del cajero, pruebas, origen y buscadores.
- Etapa 6/pulido: catálogo/carrito/confirmación/historial/Home/navegación/tema/SalesComponents, ViewModels y pruebas de presentación/persistencia/accesibilidad.
- Otros pendientes anteriores: corrección del borrador de compras, release/verificación de APK, documentación de estabilidad/SMTP/respaldo, README/estado y archivos IDE (incluido borrado anterior). Se mantuvieron intactos; no se adjudican a esta sesión.
- Algunos archivos compartidos tienen cambios de varias etapas: su clasificación es orientativa para revisión, no autorización para agruparlos sin examinar hunks.

## Cambios nuevos de esta sesión

1. `infra/docker/restore.mjs`: guardia que acepta únicamente el contador inicial 031 sin uso antes de importar en destino vacío.
2. `backend/test/backup.test.js`: escenarios de contador nuevo/usado/ausente/adicional y destino operativo; importación nunca ocurre cuando el destino no es vacío.
3. `app/src/androidTest/java/com/intutec/viveroapp/feature/sales/Stage7aRealWorkflowTest.kt`: tres fases del recorrido real con Activity/API/Room; exige opt-in, emulador, URL local y fixture de este proyecto. La protección externa evita abrir la Activity cuando se omite. Contiene datos ficticios/URL loopback, no credenciales.
4. Documentación nueva: `stage7a-integration.md`, `stage7a-integration-matrix.md`, `stage7a-reconciliation.sql`, `stage7a-candidate-and-deployment.md`, este control y `stage7a-change-inventory.csv`.

**No hubo cambios nuevos de código en ViveroWeb.** No se modificaron migraciones SQL ni lógica de pagos/inventario/roles/recuperación/Room/UX comercial. ViveroAppCliente no fue usado.

## Configuración y evidencia excluidas de Git

`local.properties` se cambió temporalmente para el ensayo y se restauró a sus bytes originales. Su copia privada, env Docker aleatorio, fixtures con contraseñas ficticias, claves de operaciones/diarios, dumps SQL, emulador, APK, PDF, capturas, perfiles Chrome y logs permanecen en `tmp/integration-stage7a/`, ignorado por Git. `.env*`, `key.properties`, keystores y claves privadas no se incorporan. `app/build`, `node_modules`, `dist` y cachés son generados. No añadir el directorio temporal ni pegar sus credenciales en un commit o reporte.

## Propuesta para revisión; sin ejecutarla

1. Separar la corrección anterior de compras/estabilidad y documentación operativa del trabajo UX. Los IDE no deben incorporarse automáticamente.
2. Revisar etapa 2 comprobantes públicos con sus pruebas.
3. Revisar etapa 3 backend/migración 031 + contratos compatibles y folios de ambos clientes como unidad de contrato.
4. Revisar etapa 4 backend/migración 032 + panel/diario Web y pruebas de seguridad.
5. Revisar etapa 5 Caja Web reutilizando los contratos previos.
6. Revisar etapa 6 Android y pulido con sus pruebas.
7. Revisar corrección 7A del script de restauración y prueba de guardias en un cambio separado; después prueba instrumentada/documentación de integración.

Compartir archivos entre etapas exige selección/revisión de hunks. Esta propuesta no autoriza `git add`, commit, push o despliegue. [Resultados y riesgos](stage7a-integration.md), [plan de entrega](stage7a-candidate-and-deployment.md).
