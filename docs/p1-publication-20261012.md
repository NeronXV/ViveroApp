# Publicación coordinada P1 — 12 de octubre de 2026

Los cuatro lotes se integraron y publicaron como `20261012-p1-84002c6f88e3`.
No se ejecutaron migraciones ni se modificaron operaciones comerciales. El
esquema permanece en `034_cataloging_drafts`.

## Alcance exacto

| Lote | Repositorio | Archivos incluidos |
| --- | --- | --- |
| 1 | ViveroWeb | `src/features/admin/plants/cataloging-service.ts`, `src/features/admin/plants/cataloging-service.test.ts` |
| 2 | ViveroWeb | `src/features/public-orders/backend-order-coordinator.ts`, `src/features/public-orders/backend-order-service.ts`, `src/features/public-catalog/CartDrawer.tsx`, `src/features/public-orders/backend-order-coordinator.test.ts`, `src/features/public-orders/public-order-ticket.test.tsx`, `tests/public-cart-browser.mjs` |
| 3 | ViveroWeb | `src/features/cashier/useCashierSales.ts`, `src/features/cashier/cashier-refresh.test.tsx` |
| 4 | ViveroApp | `backend/src/reports.js`, `backend/test/reports.test.js`, `backend/test/web-order-checkout.test.js`, `backend/test/reports-native.test.js`, `docs/backend-reports.md`, este informe |

Los cuatro lotes previos conservan sus hashes de trabajo. En esta integración se
añadieron la prueba nativa de reportes y este informe; no se cambiaron otros
módulos. `backend/src/web-order-checkout.js` permanece sin modificaciones.
Quedaron excluidos los cambios preexistentes de `.idea/misc.xml`, la eliminación
de `.idea/planningMode.xml`, y los archivos Web
`docs/public-cart-publication-20261010.md` y `tests/plants-browser.mjs`.

## Integración nueva ejecutada

- MariaDB nativa **11.4.10** portátil, exclusivamente en loopback y con un
  directorio nuevo dentro de `tmp/p1-release-20261012/mariadb-data`: **21/21**
  pruebas Node, incluidas las subpruebas. No se usó SQLite para acreditar esta
  condición. El servidor local se apagó al finalizar.
- Fixtures sintéticos: ventas directas y Web, promociones, descuentos generales
  independientes, solapamientos parciales, históricos/importados, varias partidas,
  cantidades decimales exactas, cancelaciones y devoluciones, alcance de sucursal,
  fechas y límites enteros del contrato JSON.
- Agregado nativo: **17 cobros**, ingresos **15,800 centavos**, descuento anterior
  **5,150**, descuento corregido **3,850**. Ingresos y filas completas de ventas,
  partidas, cobros y devoluciones permanecieron idénticos en transacción READ ONLY.
- Backend: `npm test`, **116/116**; `npm run check`, aprobado. Las pruebas unitarias
  SQL en memoria del lote 4 son adicionales a la prueba nativa obligatoria.
- Web: **269/269** pruebas focalizadas de Caja, catalogación y pedidos;
  **48/48** de parsers de reportes y peticiones administrativas; **1/1** del
  consumidor Web con la respuesta exacta exportada desde MariaDB nativa.
- `npm run lint` y `npm run build`, aprobados en Web y en el candidato aislado.
  El candidato se obtuvo de `git archive HEAD` y solo los archivos enumerados.
- Chrome: **8 escenarios sintéticos** del carrito aprobado, con tráfico externo
  bloqueado: rechazos corregibles, cambio de precio/producto, respuesta perdida,
  recarga, comprobante pendiente, reintento y dos pestañas sin duplicados.
- Caja: cobertura de 25, 50, 51 y 100 ventas, páginas cargadas, nuevas ventas,
  estados modificados, selección, actualización manual/polling, fallos y respuestas
  tardías; se preserva el orden por ID descendente del contrato vigente.
- `git diff --check`, aprobado en ambos repositorios. No hubo cambios Android ni
  SQL de esquema; Gradle, pgTAP y ejecución de migraciones no aplican.

Comandos focalizados principales, desde cada raíz correspondiente:

```powershell
# ViveroApp: configuración sintética privada bajo tmp, instancia nativa 11.4
$env:REPORTS_NATIVE_CONFIG = '<ruta absoluta privada bajo tmp>'
node --test --test-isolation=none backend/test/reports-native.test.js
npm --prefix backend test
npm --prefix backend run check

# ViveroWeb: sin duplicar fixtures históricos bajo artifacts
node node_modules/vitest/vitest.mjs run src/features/cashier src/features/public-orders/backend-order-coordinator.test.ts src/features/public-orders/backend-order-service.test.ts src/features/public-orders/public-order-ticket.test.tsx src/features/admin/plants/cataloging-service.test.ts --exclude 'artifacts/**' --no-cache --configLoader runner --reporter=dot
npm run lint
npm run build
node tests/public-cart-browser.mjs
```

La configuración nativa contiene únicamente puerto y contraseña sintética. La
prueba verifica versión y directorio de datos y rechaza reutilizar una base
existente. El navegador requiere Playwright y Chrome locales; durante esta sesión
se utilizó el runtime disponible mediante `BROWSER_TEST_MODULES`.

## Imágenes publicadas y reproducibilidad

| Servicio | Tag | ID de imagen |
| --- | --- | --- |
| API | `vivero-api-p1:20261012-p1-84002c6f88e3` | `sha256:442f49d9f3118af3608392387343ae6db8078d8231f9f4dcbad1fd1a355243e1` |
| Web | `vivero-web-p1:20261012-p1-84002c6f88e3` | `sha256:b2d0fc4948e8141ce5aafa0eec7dd3a7bf5b92d9c61fd526b472ab936b03aba8` |

Las bases previas se conservaron:

- API `vivero-api-cataloging:20261010-825a14bddbc6`, ID
  `sha256:b25f70ae55bc63aae2075e0b57b07fb4c16e5d9a140d8b70b9115a2a6c90010e`.
- Web `vivero-web-cart:20261010-cart-70e454e6c128`, ID
  `sha256:47688a4acf8a68140cc6f2547d3db97a5d6a59b50da6753e76cc96c25f993867`.

Directorio de entrega:
`/srv/apps/vivero-dulcinea/releases/20261012-p1-84002c6f88e3`.
Contiene el paquete, manifiestos de hashes, Dockerfiles, bases/overlays exactos,
imágenes exportadas y verificaciones. Paquete determinista:
`f8a1d0a5671404e7f43d45eb96cb9a17ed346acc6cf6ef9f7e5b228dcdb0f384`.
Se construyó sin red ni descargas, con bases verificadas y entradas fechadas de
forma fija. La API conserva todos sus archivos salvo `reports.js`; Web incorpora
un build de 24 archivos y conserva assets anteriores para sesiones abiertas.

## Respaldo actualizado y recuperación

Respaldo posterior a la precarga, generado **2026-10-12 23:12:33 UTC**:

`/var/backups/vivero/vivero-p1-2026-10-12T23-12-33Z-5be8b7876bb3-complete-recovery.tar.gz`

SHA256:
`bd3f504a7ee0f4611ce6ebd3d5d4973302110aa6163d9d94d7e0cd7696a3241e`.
Incluye SQL completo, fotografía actual, cuentas/permisos, configuración de
recuperación, fuentes previas y las tres imágenes API/Web/MariaDB anteriores.
Archivos privados permanecen restringidos en el VPS y no se incluyeron en Git.

Se verificaron los 30 archivos del paquete y los tres identificadores de imágenes
guardadas. La restauración en MariaDB **11.4.13** aislada, sin puertos públicos ni
volúmenes productivos, aprobó 65 tablas por conteo/checksum, 120 relaciones,
integridad, objetos, 7 cuentas técnicas y sus permisos, fotografía por hash y
**174 borradores**. No se ejecutaron migraciones. Se concilió exclusivamente el
permiso PROXY del bootstrap aislado conforme al procedimiento existente y se
mantuvo comparación estricta de permisos. Los recursos efímeros fueron retirados.

Una segunda restauración con la imagen candidata verificó los descuentos de los
**19 pagos históricos en 9 grupos**, mediante cálculo independiente en enteros y
transacción READ ONLY. Los ingresos y todas las tablas permanecieron idénticos.
La misma comprobación aprobó antes y después del despliegue sobre los datos
actuales, exclusivamente en lectura.

## Estado comprobado después de publicar

- MariaDB productiva **11.4.13**, saludable, mismo contenedor, volumen y esquema.
- **65 tablas idénticas** por checksum antes/después, fotografía actual preservada
  y **174 borradores** accesibles por paginación de catalogación.
- Caja conserva **5 pendientes**: paginación por sucursal, estados y recuperación
  operativa consultados sin reservar, cobrar, cancelar ni crear ventas.
- HTTPS aprobado en Web, login, catálogo, Caja y administración; API saludable;
  catálogo y opciones públicas de pedidos consultados mediante GET.
- Rutas API de sesión, catalogación, Caja, comprobantes, pedidos administrativos y
  reportes rechazan accesos sin autenticación con **401**.
- **61 assets públicos** verificados por hash, incluidos los anteriores.
- Catering Oculto saludable y HTTPS `/health/ready` **200**. Sus contenedores y el
  proxy compartido conservan IDs, imágenes, inicios y reinicios originales.

La comprobación de funciones administrativas utilizó un proceso interno en
transacción READ ONLY, sin abrir una sesión de cliente ni eludir la protección
HTTP. No sustituye la aceptación operativa con usuarios reales.

## Rollback compatible con el esquema

`baseline.json` conserva la lista completa de archivos Compose efectivos y los
hashes de configuración. `rollback.p1.yaml` fija las dos imágenes anteriores.
Usar esa lista con el mismo proyecto `vivero-vps` y archivo de entorno existente,
añadir `rollback.p1.yaml`, y ejecutar `up -d --no-deps --no-build --pull never api`;
esperar API saludable y ejecutar lo mismo para `web`. Conservar MariaDB y sus
volúmenes, fotografías y esquema 034. No ejecutar `down`, migraciones ni restaurar
SQL para un rollback de aplicaciones. El respaldo completo se reserva para
recuperación de datos ante una necesidad real.

## Git y pendientes de aceptación

La publicación se validó antes de crear commits. Web registra los lotes por
separado: `0204630` (catalogación), `3f6e80d` (carrito) y `b9359d1` (Caja).
El lote 4 y esta evidencia se registran juntos en ViveroApp. Las bases remotas
confirmadas antes de registrar cambios fueron `ae546290` para Web y `c54596fa`
para App; no se incorporaron cambios locales ajenos.

Pendiente real: aceptación operativa autenticada con el responsable del vivero y
pruebas de negocio autorizadas para la entrega al cliente. No se crearon pedidos,
ventas, cobros ni movimientos ficticios en producción para estas validaciones.
