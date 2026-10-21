# Ruta MVP funcional

## Principio

Primero se completa un recorrido que el cliente pueda usar y evaluar. Después se corrigen los problemas observados y se endurecen las áreas que el uso real justifique.

## Fase A — Recorrido de venta completo

Estado: **en validación**.

- Acceso con rol y sucursal.
- Catálogo o escáner.
- Carrito.
- Envío a Caja.
- Cobro en ViveroWeb.
- Recuperación del resultado en Android.
- Comprobación del movimiento de inventario definido para el despliegue gradual.

Criterio de salida: el camino principal funciona de extremo a extremo con datos sintéticos y sin intervención manual en la base de datos.

## Fase B — Operación diaria mínima

Estado: **parcialmente implementada**.

- Alta y consulta de productos.
- Fotografías de catálogo.
- Recepción de inventario.
- Conteo físico.
- Consulta de existencias y movimientos.
- Consulta de ventas propias.

Criterio de salida: un responsable puede preparar catálogo e inventario y un vendedor puede operar sin datos demo ocultos.

## Fase C — Piloto con el cliente

Estado: **pendiente**.

- Preparar entorno de prueba.
- Crear usuarios y sucursal piloto.
- Cargar catálogo mínimo.
- Ejecutar una jornada controlada.
- Registrar errores y fricciones reales.

Criterio de salida: retroalimentación priorizada por impacto, no por posibilidades hipotéticas.

## Fase D — Estabilización

Estado: **pendiente**.

- Corregir errores observados.
- Agregar pruebas de regresión para esos errores.
- Revisar recuperación, permisos y consistencia de datos.
- Simplificar pantallas que generen confusión.

## Fase E — Expansión

Estado: **pospuesta hasta validar el piloto**.

- Promociones.
- Clientes y fidelidad.
- Reportes avanzados.
- Pedidos.
- Automatizaciones adicionales.
- Endurecimiento y escalabilidad de nivel superior.

## Regla de priorización

Ordenar el trabajo así:

1. bloqueo del recorrido principal;
2. pérdida de datos, autorización, secretos o cobro incorrecto;
3. fricción frecuente del usuario;
4. deuda técnica que ya impide avanzar;
5. mejoras opcionales.
