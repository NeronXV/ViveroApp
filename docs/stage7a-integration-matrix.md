# Matriz de integración 7A

Resultados **nuevos de esta sesión**. Referencias de logs relativas a `tmp/integration-stage7a/`. «HTTP/SQL» implica API y MariaDB reales; «componente» no equivale a cámara o impresora física.

| Área / escenario | Nivel y resultado | Evidencia / límite |
| --- | --- | --- |
| Android: login, sucursal, catálogo, fotografía, búsqueda, código | Activity real, aprobado | `android-create.log`; código ingresado manualmente, no cámara física |
| Android: carrito, cantidades, cotización y envío | Activity + API + Room, aprobado | VD-0086, 2×25000 centavos |
| Android: respuesta perdida y cierre/reapertura | Proxy tras commit + force-stop + install-r + Room, aprobado | misma clave antes/después; `android-recover.log` |
| Android: estado pagado por Web | Activity + API, aprobado | `android-paid-history.log`, misma venta 90 |
| Caja: búsqueda corta/anterior y origen | HTTP/SQL y componentes, aprobado | `cashier-desk` en suite de 35; venta Android visible en UI |
| Reserva de dos cajeros | API real, aprobado | segundo cajero rechazado 409; suite `cashier` verifica exclusión/expiración/liberación |
| Efectivo, cambio y confirmación | Web real + SQL, aprobado | VD-0086 $600 recibidos, $100 cambio; VD-0090 exacto $5 |
| Tarjeta y transferencia | Web real + SQL, aprobado | referencias ficticias, sin proveedor bancario; transferencia vacía bloqueada |
| Pago perdido, recarga y recuperación | Proxy después del commit + Web real, aprobado | diario retenido, ticket original, un pago |
| Cancelar pendiente, repetir, motivo y auditoría | Web real y HTTP/SQL, aprobado | VD-0089; suite cancelaciones verifica repetición y claves |
| Cancelación con respuesta perdida | Proxy + Web real, aprobado | recuperación de una cancelación/auditoría |
| Dos canceladores y carrera contra cobro/reserva | HTTP/SQL concurrente, aprobado | suite de cancelaciones; una operación gana |
| Pago incierto, pagada y reserva vencida | HTTP/SQL + componente, aprobado | se exige conciliación o devolución; no nuevo cobro inseguro |
| Falta de capacidades y otra sucursal | HTTP/SQL, aprobado | política conservadora OPERATE_CASHIER + MANAGE_DISCOUNTS, sin asignación nueva |
| Pedido público y comprobante tras carrito vacío/recarga | Web real, aprobado | VW-10003, datos confirmados $5 |
| Pedido→venta vinculada→cobro | Admin y Caja reales + SQL, aprobado | VW-10003→VD-0090, una relación y un pago |
| Cancelación vinculada e impedir checkout nuevo | HTTP/SQL, aprobado | suite `sale-cancellations`, sin reposición indebida |
| Privacidad por ID/folio predecible | HTTP local, aprobado | públicos 404; admin 401; ticket sin clave 400 |
| Precios históricos, folios y clientes anteriores | HTTP/SQL, aprobado | snapshots, cabecera de folio optativa, respuesta anterior preservada; aliases concurrentes y >9999 |
| Inventario, sobreventa, devoluciones y cierres | HTTP/SQL + devolución/corte Web real, aprobado | saldo final 98/99/99; corte $605 diferencia cero |
| Instalación DB nueva / actualización 030→032 | MariaDB real, aprobado tras corregir orden del harness | 35 integraciones en cada instalación; históricos/permisos conservados |
| Room/migraciones y accesibilidad de componentes | Instrumentación, 56 aprobadas | 2 cámara omitidas por paquete readiness; flujo real 3 fases adicional |
| Web computadora / tamaño tablet solicitado | UI real sin overflow, aprobado dentro del navegador | ancho observado 864 CSS px; tablet física pendiente |
| Comprobante A4 breve/largo, térmico, Caja y etiquetas | React + Chromium PDF + PNG, aprobado | 13 PDF, breve 1 página/largo 3; física pendiente |
| Reimpresión sin mutaciones | UI real + SQL/hash, aprobado | recupera VD-0086; SQL coincide antes/después del render |
| Respaldo, imágenes y restauración | Docker separado, aprobado | 61 tablas/hash + imágenes; API/Web destino apagadas |
| Actualización APK con datos persistidos | Emulador con install-r, aprobada | clave Room original conservada; firma debug idéntica |
| Actualización sobre paquete del teléfono | Pendiente | paquete encontrado `.vpsvalidation` distinto; no se fuerza desinstalación |
| Escaneo de planta/etiqueta, Black Pos, aceptación Toni/Pedro | Pendiente presencial | no sustituible por entrada manual, PDF o tests automáticos |
| Copia externa cifrada y restauración desde ella | Pendiente Toni | los respaldos locales de ensayo no la cierran |

No se oculta la ejecución accidental parcial en el teléfono ni sus fallos: [incidente y mitigación](stage7a-integration.md).
