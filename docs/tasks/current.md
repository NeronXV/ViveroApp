# Tarea activa — Validar recorrido Android → Caja → Inventario

Estado: **lista para ejecutar**.

## Objetivo

Demostrar localmente el recorrido funcional principal con datos sintéticos, sin agregar módulos ni realizar endurecimiento general.

## Alcance

1. Levantar Supabase local desde este repositorio.
2. Aplicar las migraciones actuales desde cero.
3. Preparar únicamente los usuarios, sucursal, producto y existencia sintéticos necesarios.
4. Ejecutar Android: acceso, catálogo o escáner, carrito y envío a Caja.
5. Ejecutar ViveroWeb: recibir la venta, cobrarla y recuperar el resultado.
6. Volver a Android y confirmar el estado final.
7. Verificar el comportamiento de inventario correspondiente al despliegue gradual vigente.
8. Registrar fallos reales encontrados.

## Criterios de aceptación

- La misma venta conserva identidad y folio durante todo el recorrido.
- El backend mantiene total y estado autoritativos.
- Un reintento no duplica la venta ni el pago.
- Android muestra el resultado final correcto.
- Caja deja de presentar la venta como pendiente después del cobro.
- El comportamiento de inventario coincide con la configuración gradual documentada.
- No se usan credenciales ni datos personales reales.

## Validación proporcional

- Compilación Android.
- Pruebas específicas sólo de los componentes que fallen o cambien.
- Un recorrido manual completo.
- No ejecutar una campaña exhaustiva adicional si el camino principal pasa y no hubo cambios de contrato.

## Fuera de alcance

- Promociones y fidelidad.
- Reportes avanzados.
- Refactors generales.
- Nuevas migraciones salvo que un defecto bloqueante del recorrido lo requiera.
- Despliegue remoto.
- Blindaje adicional sin un riesgo concreto observado.

## Resultado esperado

Un reporte corto con: comandos utilizados, venta sintética creada, pasos que pasaron, fallos observados y siguiente corrección mínima.
