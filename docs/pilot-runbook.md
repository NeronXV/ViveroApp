# Runbook del Piloto - ViveroApp

Este documento describe el recorrido operativo para el piloto del lunes. El objetivo es validar el flujo completo desde la gestión de inventario hasta la visualización de reportes, asegurando la integridad de los datos y la correcta sincronización con el servidor.

## Preparación

### Dispositivos e Infraestructura
- Tablet Android con build `debug` aprobado.
- Navegador web con acceso a **ViveroWeb**.
- Conexión a internet estable (para sincronización remota).
- Configuración local (`local.properties`) con `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY`.

### Cuentas y Datos Sintéticos
- **MANAGER**: Cuenta activa asignada a una sucursal de prueba.
- **SALES**: Cuenta activa asignada a la misma sucursal de prueba.
- **CASHIER**: Cuenta activa asignada a la misma sucursal de prueba.
- **Producto**: Al menos un producto activo en el catálogo con código interno conocido.
- **Cliente**: Un cliente sintético registrado para pruebas de asociación.

## Recorrido del Piloto

1. **Gestión de Inventario (MANAGER)**
   - Iniciar sesión como MANAGER.
   - Entrar al módulo **Inventario**.
   - Registrar una **Recepción** del producto elegido (ej. +50 unidades).
   - Verificar que el historial muestra el movimiento y el saldo se actualiza.

2. **Consulta y Venta (SALES)**
   - Iniciar sesión como SALES.
   - Abrir el **Catálogo** y confirmar que el producto tiene la existencia registrada por el gerente.
   - Agregar el producto al **Carrito**.
   - (Opcional) Buscar y **Asociar cliente** (mínimo 2 caracteres para la búsqueda).
   - **Enviar orden a caja**.
   - Confirmar que la pantalla muestra "Orden preparada" y el mensaje "Enviado a caja" (sincronización exitosa). Anotar el **Folio**.

3. **Cobro (CASHIER en ViveroWeb)**
   - En **ViveroWeb**, entrar como CASHIER.
   - Localizar el **Folio** en la bandeja de entrada de la sucursal.
   - Procesar el cobro con un método de pago aprobado.

4. **Cierre y Reportes (MANAGER)**
   - Volver a la tablet como MANAGER.
   - Comprobar en **Inventario** que las unidades vendidas se descontaron automáticamente (vía trigger remoto).
   - Entrar a **Reportes** y verificar que la venta aparece en el resumen diario y en el ranking de productos.

## Evidencia a Registrar
- Usuario y sucursal utilizados.
- Folio de la venta.
- Captura de pantalla de la confirmación de envío en Android.
- Captura de pantalla del cobro en Web.
- Captura de pantalla del reporte final en Android.

## Criterios para Detener el Piloto
- Una venta local aparece falsamente como sincronizada sin estar en el servidor.
- El folio generado en Android no aparece en la bandeja de Caja Web.
- Doble pulsación en "Enviar" produce duplicidad de ventas (idempotencia fallida).
- Caja muestra una sucursal distinta a la del vendedor.
- El inventario queda negativo o inconsistente.
- Un usuario accede a un módulo sin tener la capacidad/permiso correspondiente.

---

## Estado Técnico y Pendientes
Este piloto utiliza contratos backend reales pero no constituye una validación integral del sistema de producción.

**Pendientes de validación integral:**
- Aplicación de las 24 migraciones desde cero en entorno limpio.
- Ejecución de las aserciones pgTAP actuales de base de datos.
- Recorridos completos con datos sintéticos en entorno de piloto autorizado; no se deben usar datos de producción.
- Autorización formal de despliegue por parte de los responsables.
