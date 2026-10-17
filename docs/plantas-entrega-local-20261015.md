# Plantas y recepción: entrega local del 15 de octubre de 2026

Implementación local para revisión. No se aplicó al VPS, no se operó con datos
reales y no se hizo commit, push ni despliegue. No es todavía una liberación
validada en el teléfono de la gerente.

## Recorridos

- Android: inicio → **Completar plantas** → identificar → fotografía → confirmar
  precio/existencia/publicación. Usa Compose, la sesión y las capacidades del API.
  Puede crear un borrador, completar uno existente o editar el mismo producto
  que se creó al recibir mercancía. Guardar permite continuar después.
- Web: **Recepción** → documento → revisar llegada → confirmar. Lee PDF con texto,
  Word DOCX o una lista pegada. La persona identifica coincidencias y confirma las
  cantidades efectivamente recibidas antes de afectar inventario. No incluye OCR
  ni promete interpretar automáticamente cualquier formato de factura.
- Las plantas nuevas de una recepción nacen inactivas y con precio cero pendiente
  de completar; las cantidades recibidas sí quedan en inventario. Completar la
  ficha en Android conserva ese producto y no vuelve a sumar existencias.
- Los precios de compra nunca sustituyen los precios de venta. El formulario
  muestra los costos interpretados y advierte si no detectó un costo (cero).
  No se debe confirmar un documento cuya interpretación no coincida: corregir
  la lista antes de guardarla. Los costos de una llegada ya guardada no se editan
  en este asistente.

## Contratos y recuperación

- `PATCH /api/v1/supplier-purchases/:id`: `{ revision, items }`, cada partida con
  `id`, `quantity`, `product_id` y `new_product`. Una partida recibida se vincula
  a un producto **o** propone `{ name, presentation, category_id }`. Cantidad cero
  marca una partida guardada como no recibida. Conserva `source_items` original.
- `POST /api/v1/supplier-purchases/:id/confirm`: admite `{ revision }`, conserva
  compatibilidad con `{}`. Requiere la clave de idempotencia existente; bloquea
  revisiones obsoletas. Producto, movimientos y comprobante se confirman juntos.
- Varias partidas nuevas de igual nombre, presentación y categoría dentro de una
  llegada comparten producto. Folios repetidos del mismo proveedor se rechazan.
  Sin folio, se detectan partidas idénticas del proveedor y sucursal; para otra
  entrega realmente distinta se debe proporcionar una referencia distinguible.
- Recibir mercancía existente no confirma un conteo físico histórico pendiente.
  Solo los productos creados por esa recepción reciben la confirmación inicial.
- Editar producto y subir foto aceptan `Idempotency-Key`, identidad/sucursal
  esperadas e `If-Match`. La repetición devuelve el comprobante original.
  Los consumidores Web anteriores sin clave conservan el contrato previo.
- Android conserva formulario e intento incierto en Room, separados por usuario
  y sucursal. **Comprobar guardado** reutiliza clave, datos y fotografía originales.
  Cambiar sesión no permite consumir el intento de otra cuenta. Web conserva
  también los intentos de revisión y confirmación; un conflicto definitivo se
  archiva para poder consultar la revisión actual.
- No se crearon roles. Se usan `MANAGE_PRODUCTS`, `MANAGE_PRICES`,
  `PUBLISH_CATALOGING` y la política de recepción existente. El servidor aplica
  los permisos; esconder botones no sustituye esa autorización.

## Esquemas y orden de actualización futura

1. Revisar/resguardar el entorno de destino y sus migraciones existentes.
2. Aplicar las migraciones pendientes autorizadas en orden. La nueva
   `037_receiving_pending_plants.sql` agrega columnas de revisión/propuesta y la
   tabla de comprobantes `plant_edit_events`; no elimina ni transforma datos
   existentes. Depende de las migraciones anteriores, incluidas 034–036.
3. Actualizar API, después Web y Android. `/health` exige también la migración
   037. No distribuir esta app suponiendo que el servidor antiguo ya la admite.
4. Android migra Room de 6 a 7 agregando `plant_work`, sin borrar tablas anteriores.

Este orden es una guía de revisión, no una autorización de despliegue.

## Validación de esta entrega

- `gradlew.bat assembleDebug testDebugUnitTest`: compilación correcta;
  381 pruebas, cero fallos y dos omitidas por la suite existente. Incluye cinco
  pruebas nuevas de persistencia, reintento, alcance de sesión y valores monetarios.
- `npm test` en backend: 116 aprobadas. Otros 14 casos específicos de compras,
  catalogación y preparación aprobados (algunos se solapan con la suite general).
- `backend/scripts/test-cataloging-local.ps1`: 22 pruebas aprobadas con una nueva
  MariaDB 11.4 local y datos sintéticos; incluye concurrencia, rollback, pérdida
  de respuesta, nuevas plantas, foto y conservación del comportamiento de Caja.
  Instancia detenida al terminar. Migraciones ejecutadas con los grants del API.
- Web: TypeScript y compilación Vite correctos. 92 pruebas específicas aprobadas
  en seis archivos. Cuatro casos nuevos cubren recuperación de revisión, conflicto,
  datos de planta nueva y cantidades desconocidas.
- `tests/reception-browser.mjs`: Chrome contra servidor sintético local, sin
  tráfico externo. Recorre la aplicación compilada, catálogo vacío, lista pegada,
  planta nueva, cantidad corregida, confirmación única y ancho móvil de 390 px.
  Este doble HTTP no sustituye las pruebas reales de MariaDB anteriores.
- Los bloqueos iniciales de conexión local/caché del sandbox se resolvieron al
  autorizar las mismas comprobaciones; no se eludieron los controles.

Pendiente de aceptación: cámara/galería y migración Room sobre un dispositivo,
reanudación tras cierre del proceso Android, ensayo con documentos representativos
del proveedor y revisión de usabilidad con la gerente. `adb devices -l` no mostró
ningún dispositivo conectado. No se instalaron dependencias ni se inició emulador.
El lector PDF/DOCX requiere revisión humana de todas las páginas y partidas.

## Archivos de esta entrega

ViveroApp:

- `feature/plants/domain/PlantWork.kt`, `data/PlantRepository.kt`,
  `data/local/PlantWorkDao.kt`, `presentation/PlantViewModel.kt`, `PlantScreen.kt`
  bajo `app/src/main/java/com/intutec/viveroapp/`.
- Integración: `core/database/ViveroDatabase.kt`, `core/di/DatabaseModule.kt`,
  `core/di/BackendNetworkModule.kt`, `core/network/BackendApiTransport.kt`,
  `feature/home/presentation/BackendHomeScreen.kt`, `navigation/Routes.kt` y
  `navigation/ViveroApp.kt` bajo esa misma raíz.
- Pruebas Android: `feature/plants/PlantRepositoryTest.kt`,
  `feature/home/BackendDashboardTest.kt`, `core/network/AndroidOperationalSafetyTest.kt`.
- Backend: `src/purchases.js`, `src/purchase-reception.js`, `src/plant-edits.js`,
  `src/app.js`, `scripts/verify-local-install.js`, `test/cataloging-integration.test.js`.
- `database/mysql/migrations/037_receiving_pending_plants.sql` y este documento.

ViveroWeb:

- `src/features/admin/purchases/ReceptionWizard.tsx`, `reception.css`,
  `PurchasesDashboard.tsx`, `purchases-service.ts`, `purchases-types.ts`,
  `purchases-extractor.ts`, `reception-service.test.ts`.
- `tests/reception-browser.mjs`.

## Trabajo anterior conservado y límites

Ambos repositorios ya tenían cambios locales extensos. En App se conservaron los
cambios de IDE, catalogación, políticas, servidor, pruebas y documentación, además
de las migraciones 035/036. En Web se conservaron cambios de navegación, acceso,
Plantas, servicios/pruebas y documentos anteriores. Los archivos compartidos se
ampliaron sin revertir ese trabajo; el diff total de Git no representa solo esta
entrega. La versión anterior de ReceptionWizard se resguardó en el directorio
ignorado `ViveroApp/tmp/weekend-baseline/`.

No se eliminó código histórico de otros módulos por apariencia de desuso. Ventas,
Caja, personal y clientes no se rediseñaron; las operaciones nativas comerciales
que antes estaban desactivadas continúan así. La limpieza global y la importación
sin revisión humana quedan fuera de esta entrega.
