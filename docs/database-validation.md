# Validación local de base de datos — 21 de septiembre de 2026

Resultado: **35 migraciones desde cero, 18 suites/499 aserciones pgTAP y cinco
escenarios de concurrencia correctos**. La verificación estática pasó sus 134
comprobaciones. Base del trabajo: `main`, `ee084b3`, más los cambios locales de
preparación de release; no se creó un commit ni se desplegó nada.

## Entorno y reproducción

La CLI Windows de Supabase fue bloqueada por Control de aplicaciones. Sin cambiar
esa política, se utilizó Docker Desktop local y las imágenes oficiales ya
descargadas:

| Componente | Imagen |
|---|---|
| PostgreSQL | `public.ecr.aws/supabase/postgres:17.6.1.158` |
| Migraciones Auth | `public.ecr.aws/supabase/gotrue:v2.196.0` |
| Migraciones Storage | `public.ecr.aws/supabase/storage-api:v1.71.0` |
| Ejecutor pgTAP | `public.ecr.aws/supabase/pg_prove:3.36` |

Desde PowerShell 7, con Docker iniciado:

```powershell
pwsh -NoProfile -File supabase\tests\run_docker_database_tests.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase\tests\verify_migrations.ps1
```

Cada ejecución crea nombres únicos, una red interna sin puertos publicados y un
volumen nuevo. Inicializa los esquemas oficiales, aplica las migraciones ordenadas
y ejecuta `pg_prove` como `postgres`; los tests cambian a los roles de cliente para
comprobar permisos/RLS. No usa el proyecto remoto vinculado ni volúmenes anteriores.
El runner no descarga imágenes y se detiene si falta alguna.

La ejecución completa quedó en `tmp/database-validation-20260921-130841-c9c812a9/`:
logs de inicialización, migraciones, pgTAP, concurrencia, IDs de imagen y un
`result.json` con hashes de los archivos probados. Posteriormente solo se añadió
el requisito explícito de PowerShell 7 a ambos scripts. La base queda detenida por
defecto, conservada para inspección. `-KeepRunning` evita detenerla.

## Correcciones respaldadas por los fallos iniciales

La primera ejecución de las 34 migraciones anteriores tuvo fallos en nueve suites.

- Dos funciones de presentación conservaban `EXECUTE` para `service_role` por
  privilegios predeterminados. Se añadió una migración no destructiva; no se
  reescribió el historial. Los clientes autenticados conservan su acceso.
- Los fixtures de Administración y Promociones usaban folios inválidos. Ahora
  respetan `VD-YYMMDD-XXXXXX`; la restricción del servidor se conserva.
- El test de promociones esperaba un descuento por unidad donde el contrato
  devuelve el descuento de toda la partida (dos unidades: 5000 centavos), y
  utilizaba `pg_catalog.current_date`, que no es una función calificable.
- Los tests de inventario convierten correctamente las cantidades decimales. La
  inspección de filas de auditoría se hace con el rol administrador del test,
  conservando la prohibición de lectura directa para clientes.
- Se corrigió la representación de `search_path` vacío y se actualizó la lista
  explícita de 72 firmas de funciones, cinco entradas anónimas y seis funciones
  de trigger. El test compara listas exactas; no acepta funciones nuevas sin revisión.
- Storage conserva escritura por `MANAGE_PRODUCTS`: se comprueba que ese rol
  pueda insertar en `catalog-images`, que no pueda insertar en otro bucket y que
  SALES no pueda subir imágenes. No se eliminó esa capacidad para satisfacer un
  test anterior a la administración del catálogo.

## Concurrencia real

Los fixtures son sintéticos y se ejecutan solo en contenedores etiquetados de
validación. Dos conexiones independientes prueban:

1. Dos cajeros intentan tomar una venta: solo uno obtiene el claim.
2. Dos confirmaciones con claves distintas: solo una cobra; la otra recibe
   `SALE_ALREADY_PAID`.
3. Dos confirmaciones con la misma clave: una crea y otra devuelve el resultado
   idempotente.
4. Dos cajeros intentan reemplazar un claim vencido: solo uno lo reemplaza.
5. Una confirmación espera un bloqueo de fila: se observa la espera en PostgreSQL
   y confirma después de liberarse el bloqueo.

Cada una de las tres ventas pagadas produjo exactamente un pago, una transición
PAID, un claim consumido y un movimiento de inventario. El saldo pasó de 50 a 47.

## Límites y pendientes

- Esta ejecución prueba la base y las migraciones oficiales de Auth/Storage;
  **no inicia ni certifica el stack HTTP completo de Supabase**.
- No sustituye pruebas de Android/Web, instalación y actualización del APK,
  pérdida de red del cliente, cámara, SMTP ni recuperación real de contraseña.
- No certifica la configuración, los datos, las versiones o los respaldos del
  proyecto remoto. La nueva migración sigue pendiente de despliegue autorizado.
- No prueba carga sostenida ni todas las carreras posibles entre operaciones.
- Docker arrancó tras apartar directorios de sockets temporales bloqueados. El
  problema reapareció al reabrirlo durante esta sesión; la recuperación no debe
  presentarse como una solución permanente. Los directorios se conservaron y no
  se eliminaron imágenes ni volúmenes anteriores.

## Archivos de esta validación

- Migración nueva: `supabase/migrations/202609210001_presentation_function_privileges.sql`.
- Ejecutor: `supabase/tests/run_docker_database_tests.ps1`.
- Concurrencia: `supabase/tests/concurrency/cashier.ps1` y `cashier_setup.sql`.
- Suites corregidas: `admin_web_contract`, `catalog_promotions`,
  `gradual_inventory_trigger`, `inventory_pilot_contract`, `my_sales_contract`,
  `public_catalog_images` y `security_rls` en `supabase/tests/database/`.
- Verificación estática: `supabase/tests/verify_migrations.ps1`.
- Documentación: `README.md`, `docs/testing.md`, `docs/database.md`,
  `docs/roles-and-permissions.md`, `docs/pilot-runbook.md`,
  `docs/production-release.md` y este informe.

Los cambios Android, de firma y respaldos del trabajo previo, los archivos IDE y
las carpetas preexistentes sin seguimiento se conservaron. Esta validación no
modifica código Android, no hace commit/push y no toca producción.
