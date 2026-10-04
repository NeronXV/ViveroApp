# Imágenes de catálogo en la API oficial

La migración `004_product_images` y `backend/src/images.js` agregan imágenes a
productos MariaDB. Web y Android conservan su integración Supabase: esta entrega
no cambia sus IDs, imágenes, login, pedidos, ventas ni caja.

## Endpoints

| Método/ruta | Comportamiento |
|---|---|
| `POST /api/v1/products/:productId/images` | Cuerpo binario JPEG, PNG o WebP con su Content-Type; devuelve 201 `{id,url}` |
| `GET /api/v1/products/:productId/images` | Lista imágenes activas; principal primero, después orden e ID |
| `PATCH /api/v1/products/:productId/images/:id` | Edita `alt_text`, `sort_order` o `is_primary: true` |
| `DELETE /api/v1/products/:productId/images/:id` | Baja lógica repetible; si era principal, selecciona la siguiente |
| `GET /api/v1/images/:id` | Bytes WebP; nunca acepta rutas o nombres de archivo del cliente |

Todas las escrituras requieren sesión Bearer y `MANAGE_PRODUCTS`. La carga
comprueba permisos antes de leer el cuerpo y vuelve a comprobarlos al publicar
la referencia en SQL. INVENTORY puede gestionar imágenes; SALES no puede.
Las lecturas públicas requieren imagen, producto y categoría activos. Un recurso
oculto devuelve 404. `?status=all` permite a MANAGE_PRODUCTS listar/previsualizar
imágenes activas de productos o categorías inactivos. No expone imágenes retiradas.
Las URL administrativas requieren añadir este parámetro y enviar el Bearer.

`GET /api/v1/products` ahora incluye `image: null` o
`image: {id, url, alt_text}` para la principal. URL relativa al origen de la API;
no contiene hostname del entorno ni clave de almacenamiento. No se habilita CORS
ni se sustituye el catálogo Web todavía.

## Validación y concurrencia

- Máximo 5 MiB de entrada, 16 millones de píxeles y dos cargas/decodificaciones
  simultáneas por proceso. Exceso de bytes: 413; saturación: 429.
- Firma y tipo declarado deben coincidir. Se decodifica completamente y se
  recodifica a WebP, orientado, máximo 2048 × 2048 sin ampliar; se eliminan
  metadatos originales. No se admiten SVG, GIF ni animaciones.
- `alt_text`: hasta 500 caracteres sin controles; `sort_order`: entero 0–65535.
  Campos desconocidos, parches vacíos e `is_primary: false` se rechazan. Para
  cambiar la principal se marca otra imagen; no hay reactivación de imágenes
  retiradas en esta entrega.
- Máximo 12 imágenes activas por producto. Primera carga: principal. Operaciones
  sobre un producto se serializan bloqueando su fila. SQL agrega unicidad de la
  principal mediante una columna generada nullable: también impide dos
  principales si una escritura evita la API.
- PK de imagen INT autoincremental y FK real a products con RESTRICT. Solo
  índices necesarios para PK, FK y unicidad. Cuenta API: SELECT/INSERT/UPDATE;
  no DELETE, DDL ni acceso nuevo a ventas/caja.

Implementación de decodificación: sharp 0.35.5 fijado en package-lock, usando
[límites de entrada](https://sharp.pixelplumbing.com/api-constructor/) y
[recodificación de salida](https://sharp.pixelplumbing.com/api-output/).

## Persistencia, fallos y mantenimiento

Docker monta el volumen `catalog_images` en `/data/catalog-images`. El proceso
continúa como usuario node y el resto de su filesystem sigue en solo lectura.
Dockerfile prepara propietario/permisos del directorio para volúmenes nuevos.
Para ejecución fuera de Docker, crear un directorio con permisos para el usuario
de la API y definir `IMAGE_STORAGE_DIR` con su ruta absoluta.

Los nombres son aleatorios de 64 caracteres hexadecimales más `.webp` (no UUID
ni PK). Cada archivo se escribe con creación exclusiva antes de guardar su
referencia SQL. El límite por producto se comprueba antes de escribir el archivo.
Una caída o fallo SQL tras escribir puede dejar un archivo huérfano. No se borra
automáticamente ante un COMMIT de resultado incierto, para no romper una
referencia que sí haya quedado confirmada. Una escritura interrumpida también
puede dejar bytes sin referencia; ningún directorio se sirve estáticamente.

La baja conserva fila y archivo, y deja de servirlos por HTTP. No hay purga ni
cuota global de disco todavía: antes del VPS se requiere acordar retención,
monitorear espacio y crear mantenimiento que contraste archivos con referencias
SQL, con periodo de gracia y sin cargas concurrentes. No eliminar archivos por
fecha ni vaciar el volumen como mecanismo de actualización. Si falta un archivo
referenciado, la API devuelve 503 genérico y no expone rutas internas.

Respaldar MariaDB **y** `catalog_images` en una ventana sin escrituras y probar
restauración conjunta antes de VPS. Recrear contenedores conserva los volúmenes;
`down -v` los destruiría. Health comprueba migración 004 y acceso al directorio,
pero no certifica espacio libre ni integridad de todos los archivos. Respuestas
de imágenes usan `no-store` para que futuras solicitudes respeten las bajas;
esto no retira copias ya descargadas por terceros.

## Levantar y probar

Desde ViveroApp, con `.env` propio configurado según `.env.example`:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait db
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up --build -d --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
```

Ejecutar la suite solo sobre datos sintéticos locales. Los fixtures SQL se
limpian, pero sus archivos quedan retenidos en el volumen de pruebas. Los
volúmenes existentes se actualizan con migrate; los vacíos aplican 004 mediante
el init. No modificar schema.sql para simular la actualización de un volumen.

Ejemplo tras obtener `$token` mediante login local, con un producto existente:

```powershell
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:3001/api/v1/products/1/images' -Headers $headers -ContentType 'image/jpeg' -InFile './foto-demo.jpg'
Invoke-RestMethod -Uri 'http://127.0.0.1:3001/api/v1/products/1/images'
```

No es multipart/base64: se envía el archivo directamente. El siguiente bloque
recomendado es promociones y cálculo de precio efectivo equivalente a Supabase;
después, importador y cambio coordinado de consumidores.

## Evidencia de validación de esta entrega

- `npm run check` correcto; `npm test`: 14/14. Se corrigió el fixture de animación
  para que sus cuadros fueran distintos (el codificador unificaba cuadros iguales).
- Proyecto local sintético `vivero-validation-20260929`: migración 004 aplicada
  sobre el volumen con 002/003; suite HTTP/MariaDB final 5/5. El primer intento
  detectó COUNT como string: se corrigió la elección inicial de principal.
- Proyecto sintético `vivero-images-fresh-20260929`: inicialización completa,
  volumen de archivos escribible por node, suite 5/5 y migrador posterior con
  002/003/004 `already applied`. No son diez casos distintos: son cinco casos
  ejecutados en dos escenarios.
- Imagen cargada por HTTP desde el host; hash SHA-256 idéntico y referencia de
  catálogo conservada después de `up --build --force-recreate -d --wait api`.
  Se retiró el fixture y se revocó la sesión de prueba al terminar.
- Verificador estático Supabase: 134 comprobaciones correctas. No hubo cambios
  PostgreSQL ni ejecución pgTAP. La migración nueva se ejecutó en MariaDB real.
- Sin compilación Android/Web: no cambiaron consumidores. No se validaron VPS,
  MySQL 8, restauración de backups, disco lleno, caídas durante COMMIT ni purga.
- Contenedores de estas pruebas detenidos al terminar; volúmenes conservados.

Archivos de esta entrega:

- `backend/src/images.js`, `backend/src/app.js`, `backend/src/catalog.js`,
  `backend/src/server.js`.
- `backend/package.json`, `backend/package-lock.json`, `backend/Dockerfile`.
- `backend/test/images.test.js`, `backend/test/integration.test.js`.
- `database/mysql/migrations/004_product_images.sql`, `infra/docker/compose.yaml`.
- `docs/backend-product-images.md`, `docs/backend-api-mariadb.md`,
  `docs/supabase-migration-map.md`.

El auxiliar local de persistencia está en `tmp/` ignorado y solo usa credenciales
del entorno sintético previo sin imprimirlas. No se añadieron secretos al código.
Estado Git: main, mismo HEAD; cambios anteriores de IDE, AGENTS, README,
presential-release, auditorías, automation y backend previo conservados. El
backend permanece sin seguimiento como al inicio. Sin staging, commit, push,
despliegue ni operaciones remotas de Supabase.
