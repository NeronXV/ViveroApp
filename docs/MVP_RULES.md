# Reglas de desarrollo del MVP

## Propósito

Entregar pronto una versión funcional de Vivero Dulcinea sin sacrificar un piso mínimo de seguridad y consistencia.

## Obligatorio en cada cambio

- Mantener el alcance limitado a la tarea activa.
- Compilar o ejecutar la validación técnica directamente relacionada.
- Probar el camino exitoso principal.
- Probar el error más probable cuando pueda causar pérdida de datos o un estado engañoso.
- Preservar autenticación, permisos, RLS y contratos autoritativos existentes.
- Mantener secretos fuera de Git y de los reportes.
- Registrar como deuda lo importante que no sea necesario resolver ahora.

## Piso no negociable

No se posponen:

- protección de secretos;
- autorización básica;
- RLS y privilegios de las superficies expuestas;
- total de venta y confirmación de pago autoritativos;
- idempotencia en envío y cobro;
- migraciones ordenadas y revisables;
- prevención de corrupción o pérdida de datos;
- separación explícita entre datos demo y reales.

## Puede posponerse

- casos extremos no observados;
- cobertura total de combinaciones;
- optimización prematura;
- refactors sin beneficio funcional;
- tolerancia empresarial a carga o ataques sofisticados;
- documentación duplicada;
- módulos fuera del recorrido activo.

## Pruebas proporcionales

Cambio visual o de texto:

- revisión manual;
- compilación del módulo afectado si aplica.

Cambio funcional normal:

- prueba del camino principal;
- prueba unitaria específica si existe una frontera lógica;
- compilación.

Cambio crítico de venta, pago, permisos, migración o inventario:

- pruebas específicas del contrato;
- camino principal;
- comprobación de idempotencia o autorización pertinente;
- suites amplias sólo cuando el riesgo o el checkpoint integral lo justifique.

## Clasificación de hallazgos

- **Bloqueante:** impide el flujo, rompe datos, permisos, secretos o cobros. Corregir ahora.
- **Importante:** afectará pronto al piloto, pero existe una ruta segura. Programar en la fase actual o siguiente.
- **Deuda técnica:** mejora futura sin impacto inmediato demostrado. Documentar y continuar.

## Cierre de una tarea

Una tarea MVP termina cuando:

- cumple sus criterios de aceptación;
- el camino principal fue comprobado;
- no introduce un riesgo bloqueante conocido;
- el diff no contiene cambios ajenos;
- el estado y la siguiente tarea se actualizaron si cambiaron.
