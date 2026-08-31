# Estado actual del proyecto

Última revisión documental: 2026-08-31.

## Objetivo actual

Completar y demostrar el recorrido funcional principal del MVP antes de ampliar el blindaje, la cobertura exhaustiva o los módulos secundarios.

## Estado comprobable en `main`

- Aplicación Android con Compose, MVVM, Hilt, Room y Supabase.
- Autenticación, perfiles, roles y sucursal activa integrados.
- Catálogo real, búsqueda, detalle, imágenes y escáner implementados.
- Carrito persistente y envío idempotente a Caja implementados.
- Mis comandas y seguimiento del estado de cobro implementados.
- Caja y recuperación de pagos implementadas; falta validar nuevamente el recorrido integral actual.
- Inventario piloto, recepción, conteo e historial implementados.
- Administración esencial y contratos backend disponibles para la Web.
- Último commit observado al crear este documento: `0cc9de1d84a2aad7178f58fe5b137d05e04b42ce`.

## Siguiente resultado demostrable

Una prueba local completa con datos sintéticos:

1. iniciar sesión con un usuario autorizado;
2. localizar o escanear un producto;
3. agregarlo al carrito;
4. enviar la venta a Caja;
5. cobrarla desde ViveroWeb;
6. confirmar el estado pagado en Android;
7. comprobar el efecto esperado en inventario según el modo gradual vigente.

## Bloqueadores que sí detienen el MVP

- El proyecto no compila o no inicia.
- El flujo principal pierde o corrompe datos.
- Un usuario sin permiso puede ejecutar una operación crítica.
- Un secreto aparece en código, logs o Git.
- La venta puede cobrarse dos veces o quedar confirmada con un total no autoritativo.
- Android y Web consumen contratos incompatibles.

## Deuda conocida que no bloquea por sí sola

- Cobertura exhaustiva de casos extremos.
- Optimización de rendimiento sin problema observado.
- Refactors puramente estructurales.
- Blindaje empresarial adicional.
- Módulos de promociones, fidelidad, clientes avanzados y reportes avanzados.
- Automatización integral de todos los escenarios mientras el recorrido manual principal siga pendiente.

## Cómo mantener este archivo

Actualizar únicamente cuando cambien el estado funcional, el bloqueo principal o la siguiente tarea. No convertirlo en historial; para eso están Git y los documentos técnicos específicos.
