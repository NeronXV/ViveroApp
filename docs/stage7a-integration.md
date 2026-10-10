# Etapa 7A — integración real y candidata para revisión

Validación local realizada el 9 de octubre de 2026 (America/Chihuahua; algunos logs usan UTC del 10 de octubre). No hubo conexión al VPS, cambios de producción, commit, push ni despliegue. ViveroAppCliente quedó fuera.

## Resultado

Android creó una venta mediante la Activity real, ViewModels, cliente HTTP y Room. Caja Web cobró esa misma venta. MariaDB confirmó una sola venta, pago y salida de inventario. Se perdieron deliberadamente respuestas **después** de confirmar operaciones en el servidor: creación Android, pago Web y cancelación Web. Sus diarios conservaron la operación original y recuperaron el resultado sin duplicar efectos.

La validación comercial local cumple los doce criterios técnicos de la solicitud. La aceptación presencial y la entrega definitiva siguen pendientes. El incidente de selección del teléfono descrito abajo es una desviación del procedimiento, y no se presenta como una prueba aprobada.

## Protección y entorno

- App: `main`, HEAD `a58fd614b28920d7b39fee7cef2b67f1c16b0531`.
- Web: `main`, HEAD `5f9c4853e42cace586cdaeec13998668b3d204aa`.
- Se copiaron diff binario, inventario SHA-256 y archivos de los 58 caminos preexistentes de App y 44 de Web antes de empezar. Se conservan en `tmp/integration-stage7a/baseline/`. [Control de cambios](stage7a-change-control.md).
- Docker Desktop estaba bloqueado por sockets locales obsoletos. Se apartaron únicamente directorios verificados con sockets de cero bytes, conservando copias `run.stage7a-preserved-20261009`, `run.stage7a-preserved-attempt2-20261009` y `docker-secrets-engine.stage7a-preserved-20261009`. No se eliminaron imágenes, redes, contenedores, bases ni volúmenes ajenos.
- Proyectos propios: `vivero-stage7a-20261009`, `vivero-stage7a-upgrade-20261009`, `vivero-stage7a-restore-20261009`, `vivero-stage7a-restore-final-20261009`. MariaDB 11.4, API del repositorio, credenciales aleatorias ficticias y redes exclusivas `10.243.90.0/24` a `10.243.97.0/24`. DB sin puerto publicado.
- Web en `127.0.0.1:5177`, API en `127.0.0.1:33033`, proxy de fallos en `127.0.0.1:33034`; Android usa `10.0.2.2:33034`. Otro API de actualización en `127.0.0.1:33036`.
- Sucursal ficticia 31, `UI7A-d40cc02ced`, stock inicial 100 por producto, creado mediante recepciones y activación de ensayo. CENTRO y sucursales reales no se activaron ni se consultaron para estas pruebas. Los fixtures no contienen cuentas reales.
- Emulador `emulator-5560`, Android 15, userdata exclusivo bajo `tmp/integration-stage7a/emulator/`, sin borrar el AVD existente. Configuración local Android restaurada exactamente a sus bytes anteriores antes de construir la candidata.

## Recorridos y evidencias

1. Android: sesión ficticia de ventas/sucursal → catálogo API con fotografía → búsqueda por nombre → ingreso de código contra endpoint de escaneo → carrito con aumento/disminución → cotización del servidor de 2 Monstera a $250 → envío.
2. El proxy esperó el `201` real de la API y cortó la respuesta. Room mantuvo la clave original y el estado incierto, sin ofrecer un segundo envío. Se cerró el proceso con `am force-stop`, se actualizó la misma APK mediante `install -r`, se reabrió y consultó el resultado. Se recuperó **VD-0086**, venta 90 por $500. Antes de pagar: stock 100, cero pagos, cero movimientos de venta.
3. Web encontró VD-0086 por folio corto, reservó el cobro y rechazó la reserva de otro cajero con HTTP 409. Registró efectivo ficticio de $600, cambio de $100. La respuesta de pago se perdió después del commit; la recarga y nueva sesión conservaron el diario y bloquearon el cobro. «Comprobar resultado» recuperó el ticket original. Android después mostró la misma venta **Pagada**. Stock 98.
4. Web registró tarjeta VD-0087 y transferencia VD-0088, $85 cada una. Transferencia sin referencia fue bloqueada por el formulario; con referencia ficticia se registró una vez. Son registros administrativos de ensayo, **no cargos ni transferencias bancarias**.
5. Web canceló VD-0089, con motivo y confirmación. La respuesta `201` se perdió; la recuperación mostró la cancelación original. SQL: un registro de cancelación y una transición auditada, cero pagos y cero movimientos. Se conservan responsable, sucursal y hora del servidor.
6. Un cliente ficticio hizo el pedido **VW-10003** por una Planta HTTP de $5. El comprobante guardado sobrevivió al carrito vacío y recarga. Administración confirmó el pedido y lo envió a Caja según el contrato existente; se creó una sola venta vinculada **VD-0090**, cobrada por $5. El precio histórico del pedido permanece en su comprobante; no acredita el pago posterior.
7. Web verificó el ticket VD-0087 y registró una devolución ficticia total de $85 por tarjeta con devolución del producto. La venta y pago históricos permanecen; un movimiento REFUND repuso una unidad. La UI impidió repetir la devolución.
8. Web cerró la caja con fondo $100, efectivo esperado/contado $605 y diferencia $0. El corte contiene los cuatro pagos y una devolución exactamente una vez.

Capturas y evidencia: `tmp/integration-stage7a/runtime/stage7a/android-{cotizacion-real,respuesta-perdida,recuperada-tras-reapertura,venta-pagada-por-web}.png`, `runtime/web-public-paid.png`, `runtime/web-reprint-original.png`, `runtime/web-closing-refund.png`, `runtime/public-confirmed.png`. Los tres cortes de respuesta constan en `proxy-events.jsonl`; no registra credenciales ni cuerpos.

## Pruebas ejecutadas en esta sesión

| Validación | Resultado final | Evidencia en `tmp/integration-stage7a/` |
| --- | --- | --- |
| Web `npm test -- --reporter=dot` | 506 aprobadas, 1 optativa omitida | `web-unit.log` |
| Web `npm run lint`, `npm run build` | Aprobadas | `web-lint-final.log`, `web-build-final.log` |
| Backend `npm test`, `npm run check` | 89 aprobadas y sintaxis correcta | `backend-unit-final.log`, `backend-check-final.log` |
| Respaldo/restauración `node --test backend/test/backup.test.js` | 9 aprobadas | `backup-unit.log` |
| Compose integración HTTP/MariaDB, instalación nueva | 35 aprobadas, cero omitidas | `backend-integration.log` |
| Actualización 030→031 y 031→032 | Ambas aprobadas, sin alterar históricos ni permisos | `upgrade-tests.log` (031), `upgrade032-tests.log` (032) |
| Integración HTTP/MariaDB tras actualización | 35 aprobadas | `upgrade-backend-integration.log` |
| Android `assembleDebug testDebugUnitTest lintDebug` | Compilación correcta; 370 aprobadas, 1 optativa omitida; lint 0 errores y 34 advertencias | `android-candidate-build.log`, `android-unit-results.json` |
| Activity Android real, 3 fases optativas | 3 aprobadas por separado | `android-create.log`, `android-recover.log`, `android-paid-history.log` |
| Regresión instrumentada explícita `adb -s emulator-5560 shell am instrument ... -e notClass ...Stage7aRealWorkflowTest` | 56 aprobadas, 2 de cámara omitidas por exigir paquete `.readiness` | `android-regression-emulator.log` |
| Protección nueva del ensayo sin habilitar | 3 omitidas deliberadamente **antes** de abrir Activity | `android-gate-default.log`, `android-gate-build.log` |
| React render de perfiles de impresión | 1 aprobada, genera 8 fixtures | directorio `print-fixtures/` |
| Chrome headless sobre HTML local y verificación pypdf | 13 PDF aprobados | `pdf-validation.json`, `pdfs/` |
| Consultas públicas por ID/folio y ticket sin clave | 404/404, admin sin sesión 401, ticket sin clave 400 | `public-query-security.json` |
| Conciliación y restauración final | 61 tablas con filas y hashes idénticos | `runtime/final-with-closing.json`, `runtime/restored-final-with-closing.json` |
| Integridad de imágenes | SHA-256 original y restaurado coinciden | `image-digests-{original,restored}.txt` |

Las pruebas HTTP optativas de Kotlin y Vitest de etapas anteriores no se habilitaron con sus fixtures específicos del puerto 33003. Se ejecutó el recorrido de esta etapa mediante la Activity real y Web real, además de las suites HTTP/SQL. No se confunden los dos tipos de prueba. Cámara física, TalkBack manual y aceptación del usuario no quedan cubiertos por estos resultados.

La regresión cubre Room/migraciones SQLite, recuperación, estados de cobro, controles táctiles y componentes de catálogo/carrito. Caja se revisó en computadora y al solicitar tamaño tablet; el navegador informó un ancho efectivo de 864 CSS px, sin desbordamiento horizontal. No se atribuye esa comprobación a una tablet física. Avisos críticos y funciones secundarias siguen disponibles.

## Fallos encontrados y tratamiento

- **Corrección funcional:** `infra/docker/restore.mjs` rechazaba una base nueva con migración 031 por la fila inicial de `sale_folio_counter`. Ahora exige exactamente una fila `(id=1,last_value=0)` antes de excluirla del conteo operativo. Cualquier contador usado, ausente o adicional, alias u otra fila operativa mantiene el rechazo. Pruebas nuevas y restauración real lo verificaron; el intento sobre la base ya restaurada volvió a fallar con `TARGET_CONTAINS_DATA`, como corresponde.
- Docker: sockets obsoletos recuperados reversiblemente; no fue necesario cambiar datos o arquitectura.
- Primera ejecución conjunta de pruebas de actualización: Node ordenó nombres de archivos y evaluó 032 antes de 031. Ese caso falló por versión 030. Se ejecutó después 032 por separado y pasó. El fallo original permanece registrado; las migraciones SQL no cambiaron.
- Preparación del ensayo Android: rutas de fixture/captura sin permiso, selector de búsqueda incorrecto y «Ver detalle» ambiguo se corrigieron en el **test nuevo**. La app comercial no se modificó. Logs de intentos fallidos conservados.
- Conciliación: se corrigió el SQL de la herramienta de ensayo al comprobar nombres reales (`quantity`, `amount_due_cents`) y la relación de reposición mediante `refund_id`. No se ajustaron saldos para hacer pasar pruebas.
- Sandbox Windows: algunas pruebas npm y renombres temporales fallaron por EPERM/EACCES; su repetición autorizada fuera de esa restricción pasó. No se ocultaron los intentos fallidos.
- **Incidente de selección de dispositivo:** se lanzó `connectedDebugAndroidTest` con un parámetro que no filtra dispositivos. Gradle inició pruebas también en SM-A225M. Se interrumpió Gradle al detectarlo; el log parcial registra fallos de Compose en ese teléfono y no cuenta como aprobación. La revisión automática rechazó inicialmente detener la prueba en el teléfono, por la restricción original; Pedro autorizó después usarlo y se detuvieron los paquetes de la prueba, sin comandos de desinstalación ni borrado. La consulta posterior encontró el paquete distinto `com.intutec.viveroapp.vpsvalidation` 1.0.1/code 2, con fecha de actualización del 3 de octubre, y no encontró el paquete principal. No existe un inventario anterior del teléfono que permita certificar conservación de datos del paquete principal; no se afirma. El paquete de pruebas permanece. Las posteriores ejecuciones usan exclusivamente `adb -s emulator-5560`; **no volver a usar `connectedDebugAndroidTest` con varios dispositivos conectados**.

## Impresión y respaldo

El comprobante breve ocupa una página A4; 25 productos con descripciones largas ocupan tres páginas, con producto 25 y total $60 al final. Perfiles térmicos de 80/58 mm renderizados, sin catálogo ajeno. Los PDF de Caja y etiquetas tienen el mismo texto y paginación antes/después del CSS existente; tickets reales de efectivo, tarjeta, transferencia y pedido vinculado imprimen en una página. Se inspeccionaron PNG de páginas inicial/final, ticket real, térmico 58 mm y etiqueta. Los hashes SQL anteriores a los PDF coinciden con el respaldo posterior previo a la devolución: imprimir no creó operaciones.

Dos respaldos se guardaron en `backups/` y `backups-final/`, con SQL, rutinas/triggers/eventos, imágenes, manifest y COMPLETE. Cada uno se restauró en un volumen nuevo separado; el final incluye devolución y corte. Las 61 tablas coinciden completamente. **Una restauración local no cierra el respaldo externo:** Toni debe comprobar copia cifrada fuera del VPS y restauración desde esa copia. [Guía existente](toni-backup-handoff.md).

## Reproducir o revisar el ensayo

Las herramientas locales y evidencias quedan conservadas en `tmp/integration-stage7a/`, ignoradas por Git. `setup.cjs` genera env ficticio y se niega a sobrescribirlo; `runtime/fixture.mjs` prepara sucursal/cuentas/productos ficticios; `loss-proxy.mjs` sólo apunta al API loopback 33033 y nunca a un servidor remoto. No volver a ejecutar el generador de fixture sobre estos resultados: para un nuevo ensayo usar un proyecto/volumen nuevo, puertos libres y sus propios fixtures. La conciliación de esta corrida exige sus IDs específicos; no aplicarla a otra sucursal.

Para leer nuevamente la conciliación final del proyecto conservado, iniciar su DB/API y ejecutar:

```powershell
docker compose --env-file tmp/integration-stage7a/local.env -f infra/docker/compose.yaml -f tmp/integration-stage7a/networks.yaml -p vivero-stage7a-20261009 --profile test run --rm -e WITH_CLOSING=true -e RECONCILE_OUTPUT=review-reconciliation.json tests node /stage7a/reconcile.mjs
```

Para repetir el flujo Android, compilar contra los orígenes **locales**, usar userdata separado y cargar el fixture ficticio privado con `push-fixture.cjs`. Ejecutar individualmente los métodos `createSaleWithLostResponse`, `recoverOriginalAfterRealProcessRestart` e `historyShowsSameSalePaidByWeb`, con `adb -s emulator-5560 shell am instrument -w -r -e stage7a true -e class com.intutec.viveroapp.feature.sales.Stage7aRealWorkflowTest#METODO com.intutec.viveroapp.test/androidx.test.runner.AndroidJUnitRunner`. Entre fases: cerrar proceso y actualizar con `install -r`, restablecer proxy y recuperar; después cobrar **esa misma** venta desde Web antes de consultar historial. No ejecutar las tres fases en una sola corrida ni repetir creación sobre el fixture ya utilizado. La Activity real no se abre si el ensayo no está habilitado; emulador y URL local son condiciones obligatorias. La prueba de protección negativa se ejecutó después del refuerzo de RuleChain; las tres fases positivas corresponden al mismo flujo antes de ese refuerzo del harness.

Para PDF: `render-pdfs.cjs` usa los ocho HTML React y cinco fragmentos reales guardados; `verify-pdfs.py` comprueba texto, páginas y tamaños con pypdf y genera PNG con pypdfium2. No llama a endpoints comerciales. Scripts de backup/restore requieren proyecto/env/overlay explícitos; restaurar sólo en un destino vacío nuevo. Las carpetas de evidencia contienen claves ficticias y hashes de cuentas; no publicarlas ni versionarlas.

## Entrega y límites

[Matriz de integración](stage7a-integration-matrix.md), [conciliación SQL de lectura](stage7a-reconciliation.sql), [control de cambios](stage7a-change-control.md), [candidata y plan futuro](stage7a-candidate-and-deployment.md).

No se modificó la lógica comercial de API, Room, inventario, pagos, permisos, recuperaciones ni interfaces en esta etapa. El único ajuste operativo de código fue la guardia del script de restauración; se añadieron pruebas y documentación. Se conserva todo lo anterior. No iniciar despliegue ni integración de ViveroAppCliente sin revisión.

Al terminar se detuvieron exclusivamente DB/API de los cuatro proyectos propios, el proxy, Vite y el emulador aislado; pestañas y cambio temporal de tamaño cerrados/restablecidos. Se conservaron sus volúmenes, respaldos y userdata. Docker Desktop quedó disponible; los proyectos ajenos no se detuvieron ni se eliminaron. Estado Git final: ambos siguen en main con trabajo anterior sin commit; App añade únicamente los nueve caminos 7A del inventario, Web conserva su estado anterior. `git diff --check` pasa en ambos y la auditoría de 102 caminos confirma conservación exacta.
