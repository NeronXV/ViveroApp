# ViveroApp: contexto selectivo

Consulta este mapa cuando la tarea necesite arquitectura o contexto relevante;
no es una lectura automática para ajustes pequeños y aislados.

## Arquitectura y responsabilidades

- Android operativo: módulo `:app`, una Activity y Compose. Kotlin en
  `app/src/main/java/com/intutec/viveroapp/`: `core/` (Room, DI, red y sesión),
  `feature/` por función y `navigation/` con rutas tipadas. Presentación -> dominio
  -> repositorio -> fuentes; Hilt/coroutines/StateFlow. Dinero en centavos `Long`.
- Backend compartido: `backend/` (Node 24, HTTP, mysql2), esquema/migraciones
  MariaDB en `database/mysql/`, ejecución Docker en `infra/docker/`. Autoridad
  servidor de precios, permisos, transacciones e idempotencia. No duplicarlo en Web.
- Supabase: código/migraciones conservados para historia y consumidores aún no
  migrados; no son un fallback del grafo operativo API. Mantener sus contratos,
  RLS y migraciones mientras sean aplicables; no retirarlos por esta clasificación.

## Leer según tarea, no todo el catálogo

- [Guía backend](../backend-api-mariadb.md) y [mapa de transición](../supabase-migration-map.md):
  arquitectura y contratos; sus secciones iniciales de fase pueden ser históricas.
- [Compras/recepción](../backend-purchases.md) y [lector Web](../../../ViveroWeb/docs/reception-document-review.md):
  entrega publicada y límites de aceptación.
- [Registro de plantas](../plants-direct-registration.md): contratos posteriores.
- [README](../../README.md): mapa y seguimiento; contrastar sus fechas con notas
  posteriores antes de asumir un estado de producción.
- [HANDOFF](HANDOFF.md): estado de entrega para retomar trabajo; [AGENTS](../../AGENTS.md): reglas obligatorias.

## Referencia estable, no comprobación en vivo

Etiqueta compartida `prod-reception-pdf-2026-10-20`; API corresponde al commit
`35149cfec94e07bdef692a35905b128e59e5a7a1` y Web al
`c5e7935e6de2b0bf853747ed280c01a6c1845faa`. Las guías de recepción contienen
procedencia de imágenes y límites de reproducibilidad; no repetir aquí manifiestos.
Pedro confirmó lectura de ticket y cotización. Guardar una recepción real sigue
pendiente. Este resumen no acredita nuevas pruebas, despliegues o permisos.
