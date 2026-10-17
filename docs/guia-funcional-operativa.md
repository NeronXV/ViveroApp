# Vivero Dulcinea — Guía funcional y pruebas de aceptación

Documento de trabajo para Sebastián, Dulce, la gerente y el personal del vivero.
Describe el funcionamiento confirmado en el código y la documentación disponibles
el **12 de octubre de 2026**. Sirve como base para el manual definitivo; no es un
acta de aceptación ni autoriza realizar operaciones comerciales de prueba.

## 1. Alcance, versiones y forma de leer esta guía

| Elemento | Referencia utilizada |
| --- | --- |
| API y Web publicadas | `20261012-p1-84002c6f88e3` |
| Sitio y portal | `https://viverodulcinea.bajastack.network` |
| Código Web revisado | `main`, `b9359d1f290e4872ff019b3a3e2d01716d35dfea` |
| Código API/Android revisado | ViveroApp `main`, `5db143648ef085d30c1b018053689b93d5a50d9c` |
| Android principal | `1.0.11-vps`, código 12, paquete `com.intutec.viveroapp` |
| Base documentada en la publicación | MariaDB 11.4.13; esquema hasta `034_cataloging_drafts` |

La investigación de esta guía fue **local y documental**. No se inició sesión en
producción, no se consultaron cuentas reales, no se enviaron correos, ni se
ejecutaron ventas, cobros, conteos, recepciones o cambios de permisos. La evidencia
de publicación y pruebas citada pertenece a tareas anteriores, no a nuevas pruebas
ejecutadas para escribir este documento.

Se distinguen tres evidencias:

- **Implementada:** existe el flujo en el código activo y se pueden identificar sus
  controles, contrato y restricciones. No significa que una persona lo haya usado.
- **Verificada técnicamente:** hay pruebas o comprobaciones documentadas. Se indica
  si fueron sintéticas, de lectura en producción o de compilación Android.
- **Aceptada por usuarios:** requiere un resultado registrado por el usuario en el
  equipo y sucursal de trabajo. No se deduce de un build, un HTTP 200 o una prueba
  automática. Los casos de la matriz de este documento quedan **sin ejecutar**.

Los estados funcionales usados son **operativa según permisos**, **restringida** y
**pendiente de aceptación física/operativa**. Una función puede estar implementada
y operativa técnicamente y, al mismo tiempo, pendiente de aceptación humana.

La publicación P1 acreditó 174 borradores, 65 tablas preservadas, fotografías
intactas y validaciones en lectura de Caja/reportes. Son datos de esa comprobación;
las cantidades de ventas o estados de fichas pueden evolucionar con el trabajo
real. **174 borradores no significa 174 productos comerciales ni 174 unidades en
inventario.** La disponibilidad de cada ficha debe consultarse con los filtros.

Las guías antiguas contienen etapas Supabase, candidatas Android anteriores y
resultados intermedios. Aquí prevalecen el código activo y la publicación P1.
No se consideran operativos módulos antiguos por el solo hecho de existir en el
repositorio.

## 2. Cuentas, roles y autorizaciones

### 2.1 No asignar un rol a una persona por su nombre

Antes de aceptar los flujos, cada participante debe comprobar su cuenta en Web
`/panel` y Android **Mi cuenta**. Esta investigación no verificó qué rol tienen
Sebastián, Dulce ni una gerente concreta.

| Participante | Rol efectivo | Sucursal asignada | Equipo/navegador | Confirmado por la persona |
| --- | --- | --- | --- | --- |
| Sebastián | Por verificar | Por verificar | Por registrar | Pendiente |
| Dulce | Por verificar | Por verificar | Por registrar | Pendiente |
| Gerente | Por verificar; el cargo no acredita `MANAGER` | Por verificar | Por registrar | Pendiente |
| Personal participante | Registrar individualmente | Por verificar | Por registrar | Pendiente |

### 2.2 Asignación base y permisos efectivos

El servidor entrega un rol y capacidades efectivas. La siguiente tabla describe
la **asignación base del esquema**, no una lectura de la configuración actual de
cada cuenta. La sucursal debe estar activa para operar los flujos que la exigen.

| Rol del sistema | Funciones base relevantes | Límites importantes del recorrido actual |
| --- | --- | --- |
| `SALES` | Consultar catálogo, consultar códigos, crear ventas y consultar ventas propias por contrato | No tiene Caja Web ni administración por defecto. Android 1.0.11 bloquea las ventas nativas; no hay un botón Web de venta para este rol confirmado en el panel actual. |
| `CASHIER` | Consultar catálogo y operar Caja | No tiene `CREATE_SALES` por defecto: **Venta directa** no debe darse por disponible. No tiene consulta de códigos `SCAN_PRODUCTS` ni administración por defecto. |
| `INVENTORY` | Catálogo, códigos, fichas de plantas, consulta de existencias y registro de conteos | No puede editar precios por defecto. No puede recibir mercancía, aprobar diferencias ni revisar borradores como propietario. |
| `MANAGER` | Catálogo, códigos, venta directa/Caja, ventas de su sucursal, fichas/precios, promociones, inventario/conteos y reportes | No tiene gestión de personal/sucursales/clientes administrativos ni Boletín por defecto. No sustituye a `OWNER` para recepciones o aprobación de conteos. |
| `ADMIN` | Capacidades administrativas y comerciales base, incluida visión global de ventas | No puede modificar un `OWNER` ni conceder ese rol. Tampoco sustituye a `OWNER` en las operaciones de inventario/catalogación reservadas a propiedad. |
| `OWNER` | Capacidades base completas, con las comprobaciones particulares de cada operación | Caja, recepción y conteos siguen limitados a su sucursal asignada activa. Ser propietario no elimina la validación de datos ni habilita escrituras Android bloqueadas. |

Capacidades que explican la visibilidad de las funciones:

| Capacidad | Uso real en esta guía |
| --- | --- |
| `VIEW_CATALOG` | Catálogo interno y productos en el recorrido de venta directa. El catálogo público no exige login. |
| `SCAN_PRODUCTS` | Consulta por código; cámara Android o código/lector en la venta Web autorizada. |
| `CREATE_SALES` | Crear venta; la pantalla Web de venta directa exige además acceso a Caja y `VIEW_CATALOG`. |
| `OPERATE_CASHIER` | Pendientes, atención/reserva de venta, cobro, comprobantes, cancelación pendiente y cortes. |
| `MANAGE_PRODUCTS` | Fichas, categorías y borradores de catalogación. El espacio Plantas Web exige sucursal activa. |
| `MANAGE_PRICES` | Crear productos junto con `MANAGE_PRODUCTS`, cambiar/confirmar precio y habilitar preparación comercial. |
| `MANAGE_INVENTORY` | Compras y conteos; las entradas y decisiones de conteos añaden la exigencia de rol `OWNER`. |
| `VIEW_INVENTORY_ALERTS` | Consulta nativa de inventario, junto con sucursal activa; el menú Stock Web exige `MANAGE_INVENTORY`. |
| `VIEW_BRANCH_SALES` / `VIEW_ALL_SALES` | Pedidos administrativos: propia sucursal / consulta global. No sustituyen a `VIEW_REPORTS`. |
| `VIEW_REPORTS` | Reportes; `VIEW_ALL_SALES` permite pedir ámbito global. |
| `MANAGE_DISCOUNTS` | Promociones y, junto con `OPERATE_CASHIER`, devolución total de ventas. |
| `MANAGE_USERS` | Personal, invitaciones, asignación de sucursal, activación de cuentas y directorio administrativo de clientes. |
| `ASSIGN_ROLES` | Asignación de roles, además de jerarquía `OWNER`/`ADMIN`. |
| `MANAGE_BRANCHES` | Crear, editar y activar/desactivar sucursales. |
| `MANAGE_SETTINGS` | Boletín Verde administrativo. No acredita otros paneles de configuración. |

No se confirma una pantalla genérica para editar capacidades individuales, crear
roles nuevos, auditar todo el sistema o administrar ajustes globales.

### 2.3 Cuándo se exige propietario

**Restricción del sistema:** la sesión que ejecuta las siguientes acciones debe
ser `OWNER` y conservar las capacidades y sucursal requeridas:

- Devolver un borrador en revisión, preparar su ficha inactiva o vincularlo a un
  producto existente.
- Aprobar/rechazar observaciones de conteo; una aprobación puede aumentar o
  disminuir existencias.
- Registrar una entrada directa y confirmar una recepción de compra nueva.
- Activar el descuento de existencias al cobrar para la sucursal.
- Modificar una cuenta con rol `OWNER` o conceder ese rol, respetando la
  protección del último propietario activo.

**No son exclusivas de propietario por el contrato actual:** cambiar precios o
promociones, habilitar un producto preparado, cobrar, registrar un corte o una
devolución. Requieren sus capacidades concretas. No se debe inventar una
restricción `OWNER` donde el código permite gerencia/administración.

**Autorización de las pruebas de aceptación:** una prueba que cambie productos,
precios, cuentas, existencias, pedidos, pagos o envíe correos debe acordarse con el
responsable antes de ejecutarse. Esta coordinación no concede permisos técnicos
ni permite usar la cuenta de otra persona. Los casos sintéticos de estrés y
pérdida de respuesta corresponden a un entorno aislado.

## 3. Menús y rutas reales de Web

Todas las rutas siguientes se agregan al origen del sitio indicado en §1. No son
endpoints de API. Las acciones dentro de una pantalla no siempre tienen una URL
individual.

| Navegación visible | Ruta | Acceso |
| --- | --- | --- |
| Inicio público | `/` | Público |
| Catálogo | `/catalogo`; admite búsqueda inicial `?q=texto` | Público |
| Guía de Cuidado / Asistente | `/#care-quiz` / `/#care-chat` | Público; orientación basada en las fichas, no un operador comercial |
| Inicio de sesión | `/login` | Cuenta autorizada |
| Panel de Acceso Interno | `/panel` | Sesión; muestra módulos según contexto |
| Olvidé mi contraseña | `/recuperar` | Flujo por enlace; depende del correo |
| Confirmación/baja de boletín | `/boletin`, mediante el enlace recibido | Enlace válido; no publicar su token |
| Panel → Abrir Caja; o Ventas → Caja | `/caja` | `OPERATE_CASHIER` y sucursal activa |
| Panel → Abrir Administración | `/admin` | Alguna capacidad administrativa; muestra solo módulos autorizados |
| Plantas → Catálogo y revisión de plantas | `/admin?tab=inventario` | `MANAGE_PRODUCTS`; sucursal activa para este espacio |
| Plantas → Existencias y conteos | `/admin?tab=stock` | `MANAGE_INVENTORY`; sucursal activa |
| Recepción → Compras y proveedores | `/admin?tab=compras` | `MANAGE_INVENTORY`; sucursal activa |
| Ventas → Historial y reportes | `/admin?tab=ventas` | Entrada por capacidad de ventas/reportes; consultas de reportes requieren `VIEW_REPORTS` |
| Ventas → Pedidos de clientes | `/admin?tab=pedidos` | `VIEW_BRANCH_SALES` o `VIEW_ALL_SALES` |
| Ventas → Promociones | `/admin?tab=promociones` | `MANAGE_DISCOUNTS` |
| Administración → Personal y permisos | `/admin?tab=personal` | `MANAGE_USERS` o `ASSIGN_ROLES`; cada acción vuelve a validar su permiso |
| Administración → Sucursales | `/admin?tab=sucursales` | Consulta con `MANAGE_BRANCHES` o `MANAGE_USERS`; escritura con `MANAGE_BRANCHES` |
| Administración → Clientes | `/admin?tab=clientes` | `MANAGE_USERS` |
| Administración → Boletín Verde | `/admin?tab=boletin` | `MANAGE_SETTINGS` |

**Precisiones de navegación:**

- El nombre de la tarea **Plantas** no cambia la URL a `tab=plants`: el identificador
  real es **`tab=inventario`**. Un tab desconocido o no autorizado conduce al primer
  módulo autorizado; no constituye un acceso a ese módulo desconocido.
- Una ficha comercial usa `/admin?tab=inventario&plant={id-real}`; su conteo usa
  `/admin?tab=inventario&plant={id-real}&view=count`. No usar IDs inventados.
- La bandeja de borradores y la ficha de borrador se abren dentro de **Revisión de
  plantas y nuevas fichas**. No se confirma una URL dedicada por borrador.
- Caja, pedidos, compras, clientes, promociones y personal usan paneles, formularios
  o modales internos; no inventar rutas como `/admin/compras` o `/caja/pagar`.
- **Editorial** está declarado como pendiente de permiso backend y no es un módulo
  administrativo autorizado. No hay un flujo editorial operativo confirmado.
- En administración, **Mi sesión** contiene **Volver al panel**, **Actualizar acceso**,
  **Cerrar sesión** y cambio de tema. Cambiar tema es una preferencia de presentación.

## 4. Inicio de sesión, recuperación, personal y sucursales

### 4.1 Iniciar sesión, revisar acceso y cerrar sesión

**Acceso/ruta:** cualquier cuenta autorizada, `/login` → `/panel`; Android → pantalla
de inicio de sesión → inicio → icono de perfil → **Mi cuenta**.

1. Introducir **Correo electrónico** y **Contraseña**, y pulsar **Iniciar sesión**.
2. Esperar la carga de perfil, rol, sucursal y capacidades. Web dirige a `/panel`
   o a una ruta protegida autorizada que se hubiera solicitado previamente.
3. Comprobar el nombre, rol y sucursal. Abrir solo los módulos disponibles.
4. Si la asignación cambió, usar **Actualizar acceso** en Web o **Actualizar permisos**
   en Android. Si se revocó la sesión, iniciar sesión nuevamente.
5. Al terminar, usar **Cerrar sesión** en el panel correspondiente.

**Resultado:** módulos adaptados al acceso efectivo. Sin sucursal activa, Caja se
bloquea aunque exista permiso de cajero. Una cuenta sin rol no obtiene capacidades.

**Datos:** login crea sesión y registra control de intentos; actualización consulta
identidad/permiso; logout revoca la sesión presentada. Estas acciones no son ventas
ni borran por sí mismas diarios comerciales pendientes. Android y navegador tienen
sesiones independientes; abrir el portal no transfiere la sesión Android.

**Advertencias:** correo inválido, contraseña incorrecta, cuenta desactivada, sesión
vencida o demasiados intentos. El token API vence aproximadamente a la hora según
el contrato actual. Un error de acceso permite reintentar; no habilita permisos
provisionales. En Plantas, los cambios sin guardar o una operación en curso pueden
bloquear salida/cambio de acceso hasta confirmar o resolver el trabajo.

**Propietario/estado:** login/logout no requieren propietario. Implementado y con
evidencia técnica; acceso individual de cada participante pendiente de aceptación.

### 4.2 Sucursal asignada frente a selección en un formulario

**Acceso/ruta:** asignar sucursal: Administración → **Personal y permisos**
(`/admin?tab=personal`), con `MANAGE_USERS`, respetando jerarquía de propietarios.

1. Buscar al trabajador en el directorio y seleccionar **Asignar Sucursal**.
2. Elegir una sucursal activa y guardar la asignación.
3. El trabajador debe volver a iniciar sesión y comprobar su nueva sucursal.

**Resultado/datos:** cambia la sucursal del usuario, registra auditoría y revoca sus
sesiones cuando existe un cambio real. Repetir la misma asignación no es un traslado.

**Advertencias:** un trabajador inactivo no admite asignación; una sucursal inactiva
no es destino válido. No resolver un pendiente de una sucursal anterior con otra
cuenta/sucursal; conservarlo para revisión con su identidad original.

**Propietario:** `ADMIN` no puede modificar cuentas `OWNER`; estas requieren
propietario. La asignación ordinaria no exige exclusivamente `OWNER`.

**Otras selecciones que no cambian la cuenta:** **Sucursal** en reportes es un filtro;
**Sucursal de recolección** del carrito público es el destino del pedido. No hay
selector libre de sucursal de trabajo confirmado en `/panel` ni en **Mi cuenta**
Android. Caja, compras y conteos usan la sucursal asignada, no esos filtros.

**Estado:** implementada; recepción de permisos nuevos y conservación de pendientes
entre sucursales requieren aceptación con las cuentas designadas.

### 4.3 Personal y permisos

**Acceso/ruta:** `/admin?tab=personal`; directorio con `MANAGE_USERS`. Asignar roles
requiere `ASSIGN_ROLES` y actor `OWNER`/`ADMIN`.

1. Buscar al trabajador y revisar si está activo, su rol y sucursal.
2. Para cambiar rol, abrir la acción de asignación y **Asignar Rol Administrativo**;
   seleccionar uno de los roles ofrecidos y confirmar. Roles elevados muestran una
   confirmación adicional de privilegios.
3. Para activar/desactivar, utilizar la acción del trabajador y confirmar el cambio.
4. Actualizar el directorio y solicitar al afectado un nuevo login si hubo cambios.
5. **Invitar nuevo trabajador** pide nombre y correo; **Enviar invitación** inicia
   establecimiento de contraseña por enlace. Después se asignan rol y sucursal.

**Resultado/datos:** rol/estado y auditoría persistidos; revocación de sesiones cuando
cambia el acceso. Invitar crea/prepara cuenta y enlace, pero no concede rol ni
sucursal automáticamente. El indicador de personal activo no acredita asistencia
o fichaje de un turno.

**Advertencias:** cuenta existente, inactividad, jerarquía de propietario, último
`OWNER` activo protegido y operación administrativa ocupada. La invitación depende
del correo: que el servicio acepte envío no prueba recepción en el buzón.

**Propietario/estado:** asignar/modificar `OWNER` está reservado a propiedad;
administración puede actuar sobre roles inferiores según permisos. Directorio y
mutaciones implementados con pruebas previas; cambios reales de acceso pendientes
de aceptación. Invitaciones dependen de configuración/entrega de correo, no
confirmadas aquí; no se afirma que estén deshabilitadas en producción.

### 4.4 Administrar sucursales

**Acceso/ruta:** `/admin?tab=sucursales`; consulta con `MANAGE_BRANCHES` o
`MANAGE_USERS`, escritura con `MANAGE_BRANCHES` (base `OWNER`/`ADMIN`).

1. Consultar ubicaciones, estado y contadores.
2. Para alta, abrir **Crear Sucursal**, capturar código y nombre, y guardar.
3. Para corrección, abrir **Editar Sucursal** y guardar código/nombre.
4. Para activación/desactivación, usar la acción de la fila y confirmar.

**Resultado/datos:** sucursal persistida y cambio auditado; no se borra físicamente.
La desactivación se rechaza si hay personal activo, ventas pendientes o pedidos
abiertos. Códigos duplicados se rechazan. Las cifras visibles corresponden a los
registros cargados, no deben interpretarse como un conteo físico del vivero.

**Propietario/estado:** no hay exclusividad `OWNER` para esta acción si existe la
capacidad; cualquier cambio de una sucursal operativa requiere coordinación de
negocio. Implementada; aceptación de cambios reales pendiente.

### 4.5 Contraseña: cambio personal y recuperación por enlace

**Cambio personal Android:** cualquier usuario con sesión válida → icono de perfil
→ **Mi cuenta** → **Cambiar contraseña**.

1. Escribir contraseña actual, nueva y confirmación.
2. La nueva debe diferir de la actual y tener **6–128 caracteres** en este flujo.
3. Pulsar **Guardar contraseña**. Tras confirmar, volver a iniciar sesión.

**Recuperación Web:** `/login` → **Olvidé mi contraseña** → `/recuperar` → correo
→ **Enviar enlace**. Abrir el enlace recibido, introducir nueva contraseña y
confirmación, y pulsar **Guardar contraseña**. Este formulario exige **15–128
caracteres**; no unificar su límite con el cambio personal Android. La solicitud
Android de recuperación remite a completar el proceso en Web.

**Resultado/datos:** cambio de credencial y revocación de sesiones/enlaces. El
restablecimiento por correo usa enlace temporal; solicitarlo no modifica ventas.
El cambio personal no requiere rol administrativo ni autorización `OWNER`.

**Advertencias:** contraseña actual incorrecta, confirmación distinta, enlace
inválido/vencido y límite de intentos. Ante respuesta perdida del cambio Android,
el acceso local se cierra: comprobar login con la nueva contraseña y, si no
funciona, con la anterior; no declarar éxito sin confirmarlo.

**Estado:** cambio personal implementado y probado previamente; aceptación en el
dispositivo pendiente. Recuperación/invitación por correo implementadas, pero
configuración y recepción real pendientes de verificación. No se confirma un
formulario Web de cambio personal desde `/panel`.

## 5. Catálogo, fichas comerciales y fotografías

### 5.1 Consultar el catálogo público

**Acceso/ruta:** público, sitio → **Catálogo** (`/catalogo`).

1. Usar **Buscar en el catálogo** y los botones de categoría; **Todas** quita el
   filtro de categoría.
2. Leer en la tarjeta nombre, fotografía, categoría, descripción, cuidados y precio
   por unidad. Si hay promoción, se muestra el precio efectivo y, cuando corresponde,
   el precio anterior y nombre de la promoción.
3. Usar **Cargar más** para páginas adicionales; **Reintentar** ante un fallo.
4. **Agregar** introduce el producto en el carrito y abre su panel (§9).

**Resultado/datos:** consulta de productos comerciales habilitados según el servidor;
agregar modifica el carrito local. No crea pedido ni reserva existencias. No se
confirma un botón público de **Ver detalle**: la ficha pública visible es la tarjeta.
Android sí tiene detalle nativo (§12).

**Advertencias:** catálogo vacío/no coincidencias, fallo de conexión y fotografía
ausente (imagen de respaldo). El catálogo no informa cantidades disponibles por
sucursal; ver un producto no garantiza que se pueda cobrar cualquier cantidad.
Los borradores no aparecen en este catálogo.

**Propietario/estado:** consulta sin propietario; implementada y lectura pública
verificada en la publicación. Claridad de las fichas para clientes pendiente de
aceptación humana. Guía de Cuidado/Asistente consultan información de fichas y no
crean pedidos ni acreditan una conversación con personal humano.

### 5.2 Consultar y completar una ficha comercial

**Acceso/ruta:** `MANAGE_PRODUCTS` y sucursal activa, `/admin?tab=inventario` →
**Buscar una planta** / categoría / **Por completar** → **Abrir ficha**.

1. Comprobar nombre, SKU, categoría, precio y estado comercial de la planta.
2. Revisar las razones de preparación: precio, conteo inicial, categoría o activación
   pendientes. La existencia mostrada corresponde a la sucursal de trabajo.
3. Editar nombre y los campos necesarios de **Más detalles**. Solo con
   `MANAGE_PRICES` editar o confirmar el precio de venta en MXN.
4. Usar **Guardar ficha** o **Guardar y siguiente**. La segunda acción recorre la
   selección de plantas pendientes; no mueve inventario.
5. Si queda resultado incierto, usar **Comprobar datos guardados** y comparar la
   ficha consultada con el formulario conservado antes de repetir el guardado.

**Resultado/datos:** actualiza el catálogo global y su revisión. El precio del
producto es global; no es un precio diferente por sucursal. Editar una ficha no
reescribe partidas o importes de ventas/pedidos históricos. Ver existencia o
consultar movimientos es lectura; no se debe introducir un saldo en el precio.

**Advertencias:** falta de permisos de precio, código duplicado, categoría inactiva,
edición concurrente, cambios sin guardar o fotografía pendiente. **Guardar y
siguiente** no debe abandonar una fotografía cuyo guardado no se confirmó.

**Propietario/estado:** no toda edición exige `OWNER`; la preparación de borradores
y aprobación del conteo sí tienen su circuito de propietario (§6–7). Disponible
según permisos; pruebas técnicas previas. Revisión botánica/precio reales pendientes
de validación por el responsable de cada ficha.

### 5.3 Fotografías: selección y guardado son pasos diferentes

**Acceso/ruta:** misma ficha comercial o ficha de borrador en estado **Por completar**.

1. Usar **Tomar foto** o **Elegir fotografía** en ficha comercial; **Tomar foto** o
   **Galería** en borradores. El comportamiento de cámara depende del dispositivo.
2. Revisar la vista previa. Aún no está guardada en el servidor.
3. Pulsar **Guardar fotografía** y esperar la confirmación. En borradores, guardar
   primero los avances/ficha cuando corresponda.
4. Consultar la fotografía registrada si hubo una respuesta perdida o conflicto.
   En ficha comercial existe **Consultar fotografía guardada**. En borrador existe
   **Consultar ficha actual sin borrar mi formulario**.
5. Solo descartar la selección pendiente si la registrada es la correcta, o elegir
   otra imagen y volver a guardar con la revisión vigente.

**Resultado/datos:** imagen de identificación en borrador o imagen del producto en
catálogo. Formatos JPEG, PNG y WEBP, hasta **5 MiB**; el servidor normaliza la imagen.
La fotografía no modifica stock ni acredita autorización de precio. No es obligatoria
para habilitar venta según la preparación comercial actual.

**Advertencias:** archivo vacío, formato/tamaño inválidos, imagen no decodificable,
ficha modificada por otro usuario o conexión perdida. Una vista previa y el diálogo
de selección cerrado no prueban guardado. El flujo conserva la selección ante fallo
cuando sigue abierta; no promete conservar un archivo seleccionado tras cerrar el
navegador. No hay editor de retoque/recorte confirmado en estas pantallas.

**Propietario/estado:** guardado ordinario con permiso de catálogo; no exclusivo de
`OWNER`. Flujo y recuperación verificados técnicamente; la documentación del piloto
recoge confirmación humana anterior de fotos/catalogación Web, sin una aceptación
individual de las 174 fichas. Cámara/galería de cada teléfono pendientes de aceptación.

### 5.4 Herramientas del catálogo y QR

**Acceso/ruta:** `/admin?tab=inventario` → **Herramientas del catálogo**.

1. Consultar productos/categorías y sus filtros.
2. **+ Nuevo Producto** abre creación comercial; requiere `MANAGE_PRODUCTS` y
   `MANAGE_PRICES`. Capturar datos reales, código, categoría y precio; **Registrar
   Planta** guarda el producto. No confundirlo con **Nueva planta** de la bandeja
   de borradores.
3. **Editar** y **Guardar Cambios** modifican el producto existente; cambios de
   precio requieren su capacidad. Crear/editar categoría conserva su propio estado.
4. Para etiquetar, usar **Ver QR** desde ficha o **Generar etiquetas QR** desde la
   tabla; para un lote, seleccionar productos y **Imprimir varias etiquetas**.
5. En **Etiquetas QR**, ajustar copias y **Imprimir … etiquetas en A4**, escala
   100 %, sin encabezados/pies. La impresión del catálogo admite 1–100 copias por
   producto y hasta 1000 etiquetas por impresión.

**Resultado/datos:** alta/edición modifica catálogo, no recepción de mercancía. Un
producto nuevo puede seguir pendiente de preparación; el checkbox de activo no
elimina requisitos. El QR codifica el código interno; imprimir solo genera una
presentación local. Cambiar el código obliga a reimprimir sus etiquetas; cambiar
precio no cambia el contenido del QR.

**Advertencias/propietario:** permisos, SKU/código de barras duplicados y datos
incompletos; la aprobación de stock se hace aparte por propietario. No hay creación
de existencias al imprimir etiquetas. Operativa según permisos; legibilidad de
etiqueta impresa y lectura por cámara/lector pendientes de aceptación física.

## 6. Los 174 borradores y su revisión

### 6.1 Completar y enviar a revisión

**Acceso/ruta:** base `INVENTORY`, `MANAGER`, `ADMIN`, `OWNER`, siempre con
`MANAGE_PRODUCTS` y sucursal activa → `/admin?tab=inventario` → **Revisión de
plantas y nuevas fichas** → **Estado: Por completar** o **Todos** → **Completar ficha**.
Los filtros de esta bandeja son distintos de los filtros del catálogo comercial.

1. Localizar la entrada por nombre o SKU y revisar estado y revisión guardada.
2. Abrir **Documento original y dudas** cuando exista evidencia de origen.
   Conservar el nombre original y las dudas; no convertir abreviaturas o tamaños
   ambiguos en datos confirmados por deducción.
3. Completar los datos que se conocen: nombre comercial por confirmar, presentación
   literal, SKU interno por confirmar y categoría. **Más información** permite
   nombre científico, código de barras real y notas de identificación.
4. **Guardar avances** permite conservar información incompleta. No asigna precio
   ni existencias. **Comparar con los datos guardados** muestra el estado persistido.
5. Guardar fotografía si se tiene (§5.3); terminar los cambios pendientes del
   formulario y de la foto antes de **Enviar a revisión**.
6. Comprobar estado **En revisión**; no continuar editando como si siguiera abierto.

**Resultado/datos:** cambios de campos del borrador, revisión, responsable y eventos
de catalogación. El original/evidencia permanecen separados de los datos propuestos.
Los borradores son compartidos; no son una lista privada por trabajador. La sucursal
controla identidad/autorización, no crea otra copia de los 174 registros.

**Errores y recuperación:**

- **Comprobar guardado** recupera un intento pendiente con el mismo contenido y
  clave. No crear otra ficha para sustituir un resultado incierto.
- Si cambió cuenta/sucursal o se cerró sesión durante el guardado, el lote P1
  conserva el intento original y evita borrar el de otra identidad. Volver a la
  cuenta y sucursal originales para comprobarlo; no borrar almacenamiento local.
- Ante conflicto, **Consultar ficha actual sin borrar mi formulario** permite
  comparar. Consultar no equivale a guardar automáticamente los cambios propios.
- Un SKU ocupado exige revisar si ya existe la planta; no inventar otro código
  para ocultar una posible duplicidad. Revisiones cerradas no se editan por este flujo.

**Propietario/estado:** completar, guardar y enviar no son exclusivos de `OWNER`.
Disponibilidad de los 174 y recuperación P1 verificadas técnicamente; identidad,
presentación, categoría y SKU de cada entrada pendientes de aceptación del vivero.

### 6.2 Decisión del propietario y preparación comercial posterior

**Acceso/ruta:** `OWNER` con `MANAGE_PRODUCTS`, sucursal activa → misma bandeja →
**En revisión** → abrir la ficha.

1. Comparar identidad, presentación, categoría, SKU, evidencia y foto.
2. Elegir una sola decisión que corresponda:
   - **Devolver para completar:** vuelve a **Por completar** para corregir.
   - **Confirmar y preparar ficha inactiva:** exige nombre, presentación, SKU y
     categoría válida; confirmar el diálogo de creación.
   - **O vincular al producto existente:** seleccionar la coincidencia exacta y
     **Confirmar vínculo sin modificar producto**.
3. Comprobar **Ficha preparada** o **Vinculado al existente** y el producto asociado.
4. Si se creó ficha nueva, abrirla en el catálogo comercial; confirmar precio
   positivo con `MANAGE_PRICES`, registrar conteo físico, obtener aprobación de
   propietario y habilitarla en la sucursal cuando el sistema permita hacerlo.

**Resultado/datos:** preparar crea producto **inactivo**, con precio cero pendiente
y sin inventario, y puede trasladar su foto de identificación a la ficha. Vincular
solo registra el vínculo; no sobrescribe el producto existente. Preparar/vincular
cierran la revisión del borrador, sin eliminar su evidencia histórica.

**Advertencias:** no preparado por datos incompletos, categoría inactiva, SKU/barcode
ocupados o revisión modificada. Las fichas nuevas requieren preparación por
sucursal. **Habilitar venta en esta sucursal** exige `MANAGE_PRODUCTS` y
`MANAGE_PRICES` y requisitos satisfechos; esa activación no es exclusiva `OWNER`,
pero su conteo inicial sí requiere decisión de propietario.

**Estado:** revisión y preparación implementadas y verificadas con datos sintéticos;
no existe constancia aquí de que las 174 estén aprobadas/completadas. Los productos
históricos `LEGACY` pueden conservar compatibilidad sin un conteo nuevo; que se
vean listos no acredita una aceptación física reciente.

## 7. Existencias: consulta, conteos, aprobación y activación

### 7.1 Consultar existencias e historial

**Acceso/ruta:** Web `MANAGE_INVENTORY` → `/admin?tab=stock` → **Control de
Existencias y Stock**. En Android, **Inventario** con permiso de inventario/alertas
y sucursal activa (§12).

1. Revisar sucursal, productos, cantidades, mínimos y alertas; ajustar filtros.
2. Usar **Actualizar** si hace falta nueva consulta.
3. Consultar historial del producto. Desde su ficha también existe **Consultar
   movimientos** en **Conteos e historial**.

**Resultado/datos:** lectura de saldos y movimientos. **Existencias bajas** y
**Agotados** dependen del saldo/mínimo consultado; no representan un conteo físico
recién realizado. Las consultas no crean movimientos ni habilitan inventario.

**Advertencias/propietario/estado:** falta de sucursal, permiso o red; no se necesita
propietario para leer. Implementada; lectura técnica acreditada, concordancia con
mercancía física pendiente de aceptación.

### 7.2 Registrar una observación de conteo

**Acceso/ruta:** `MANAGE_INVENTORY` y sucursal activa; base `INVENTORY`, `MANAGER`,
`ADMIN`, `OWNER`. Preferible `/admin?tab=inventario` → ficha → **Registrar conteo**;
alternativa `/admin?tab=stock` → **Registrar conteo**.

1. Seleccionar el producto correcto y comprobar la sucursal.
2. Esperar la consulta de **Existencia registrada antes de contar**.
3. Contar físicamente y capturar una **cantidad entera**, incluido cero si no hay
   unidades. Añadir motivo/observaciones reales.
4. En ficha, **Enviar conteo**; en Stock, **Guardar conteo para revisión**.
5. Confirmar que quedó pendiente de revisión; ante duda usar **Comprobar resultado**
   o la acción de resultado pendiente del formulario.

**Resultado/datos:** guarda observación, autor/hora, producto, sucursal, cantidad y
referencia de saldo/movimiento. **Todavía no modifica existencias**, aunque haya
una diferencia grande. No reemplaza la referencia del intento al reintentar.

**Advertencias:** si hubo movimientos desde la referencia, el conteo puede estar
desactualizado (`INVENTORY_COUNT_STALE`), incluso si el saldo volvió al mismo
número. Revisar/rechazar y contar nuevamente; no forzar el valor anterior.

**Propietario/estado:** enviar observación no requiere exclusivamente `OWNER`;
aplicarla sí. Implementada y con pruebas sintéticas; conteo y concordancia física
de cada producto pendientes de aceptación. Android 1.0.11 no registra conteos nuevos.

### 7.3 Revisar, aprobar o rechazar un conteo

**Acceso/ruta:** `OWNER`, `MANAGE_INVENTORY`, sucursal activa → Plantas → **Revisar
conteos pendientes**, ficha → **Conteos e historial**, o Stock → **Conteos por revisar**.

1. Abrir **Revisar conteo** de la planta correcta.
2. Comparar cantidad registrada al contar, observada, diferencia y motivo.
3. Capturar **Motivo de la decisión**.
4. Elegir **Confirmar aprobación** o **Rechazar conteo**.
5. Consultar nuevamente saldo, estado de preparación e historial. Si no se confirma
   la decisión, **Comprobar resultado pendiente** antes de iniciar otra.

**Resultado/datos:** aprobar verifica vigencia y aplica la diferencia mediante
movimiento autorizado, pudiendo sumar o restar. Sin diferencia, cierra el conteo
auditado y acredita el conteo inicial sin movimiento. Rechazar guarda decisión,
sin cambiar saldo. Una observación recibe una sola decisión; quedan responsables
y motivos. No se borran conteos históricos.

**Advertencias/estado:** revisión ya decidida, movimiento concurrente, otra sucursal
o permiso insuficiente. No confiar solo en el signo de la diferencia. Restricción
de propietario aplicada en servidor; aceptación física y de decisiones reales pendiente.

### 7.4 Activación del control de existencias al cobrar

**Acceso/ruta:** `OWNER` con `MANAGE_INVENTORY`, sucursal activa →
`/admin?tab=stock` → **Descuento de existencias al cobrar**.

1. Leer si la sucursal indica **Activo** o **carga inicial**.
2. Completar y revisar el inventario real y los conteos antes de activar.
3. Solo con autorización operativa, marcar **Confirmo que el inventario inicial de
   esta sucursal está revisado** y pulsar **Activar descuento al cobrar**.
4. Ante respuesta incierta, consultar el estado antes de repetir.

**Resultado/datos:** activa el control para cobros nuevos de esa sucursal; no
descuenta retroactivamente ventas anteriores. Si está activo, un cobro con stock
insuficiente se rechaza y no registra el pago. Si no está activo, los cobros no
generan ese descuento de stock, pero siguen validando preparación comercial.

**Advertencias/estado:** no se confirmó aquí el estado actual del interruptor por
cada sucursal. No hay acción de desactivación en esta pantalla confirmada para
volver atrás. La mención de Android en su texto describe el contrato común de
cobro; **no habilita cobros nativos en Android 1.0.11**. Implementada, restringida a
propietario; activación y prueba comercial real pendientes de aceptación autorizada.

## 8. Compras, proveedores y recepción

### 8.1 Preparar una compra y relacionar sus partidas

**Acceso/ruta:** `MANAGE_INVENTORY` y sucursal activa → Recepción → **Compras y
proveedores** (`/admin?tab=compras`) → **+ Registrar compra**. No exige `OWNER`
para preparar/revisar el borrador.

1. **Proveedor:** seleccionar proveedor; el formulario permite **Registrar Proveedor**
   con código y nombre cuando hace falta. Capturar fecha del documento, referencia
   externa, condiciones de pago y nombre del archivo de origen si corresponde.
2. **Renglones:** captura manual o entrada JSON/CSV/PDF local. Revisar literalmente
   descripción, código de envase, presentación sugerida, cantidad y costo unitario;
   corregir antes de crear. **+ Agregar otro renglón** añade una línea.
3. **Borrador:** usar **Revisar y Crear Borrador** y el paso **Confirmar y Crear
   Borrador (DRAFT)**. Revisar el total calculado en MXN y guardar.
4. **Relacionar:** para cada partida, seleccionar producto existente o marcarla
   ignorada cuando corresponda. Confirmar también las asociaciones automáticas.
   **Configurar Presentación / Envase** registra descripción/medida del proveedor;
   no deduce una medida física de un código ambiguo.
5. **Pausar y volver a la lista** conserva el documento guardado; después abrir
   **Gestionar**. El borrador no cambia existencias.

**Resultado/datos:** proveedores/presentaciones y equivalencias de proveedor son
globales; el documento de compra pertenece a la sucursal asignada. Guardar crea
documento/partidas. Relacionar puede aprender una equivalencia para compras futuras,
sin cambiar nombre/precio del producto. Las sugerencias no crean automáticamente
una planta aceptada. Si se abre creación de producto, rigen sus permisos (§5.4).

**Advertencias:** referencia duplicada, cantidades/costos/totales inválidos, proveedor
inactivo, productos no válidos, partidas sin resolver y extracción PDF incompleta.
El PDF se interpreta localmente y puede requerir captura manual; no se confirma
OCR de imágenes ni exactitud automática de una factura escaneada. El nombre del
archivo no acredita un adjunto original conservado en el servidor.

**Recuperación:** **Recuperar borrador pendiente** conserva el intento original.
**Resolver intento y corregir borrador** consulta/retira de forma autoritativa o
recupera el documento ya creado antes de permitir una corrección; no equivale a
borrar ciegamente una compra. Reabrir el documento si ya aparece guardado.

**Estado:** implementada con pruebas previas sintéticas; documentos, envases,
equivalencias y PDF reales pendientes de aceptación. Contadores/listado de compras
reflejan documentos cargados, no una contabilidad completa de proveedores.

### 8.2 Confirmar mercancía recibida e imprimir etiquetas

**Acceso/ruta:** `OWNER`, `MANAGE_INVENTORY`, sucursal activa → compra **DRAFT** →
**Relacionar** → **Continuar a Confirmación de Recepción** → **Confirmar**.

1. Verificar físicamente proveedor, sucursal, productos y unidades recibidas.
2. Resolver todas las partidas: ninguna puede seguir sin relacionar y debe existir
   al menos una asociada. Las ignoradas no entran al inventario.
3. Revisar unidades/total y pulsar **Confirmar recepción** una sola vez.
4. Esperar documento **RECEIVED / Recibido**. Ante comunicación incierta, actualizar
   el detalle/listado y comprobar su estado antes de cualquier nueva confirmación.
5. **Etiquetas QR** ofrece impresión para los productos recibidos con código válido.
   Revisar sus copias y ejecutar la impresión local; no repetir la recepción para
   volver a imprimir.

**Resultado/datos:** confirmación crea movimientos de recepción y aumenta saldos
solo de partidas asociadas; guarda responsable y vínculos a movimientos. Repetir
el resultado con su clave válida no duplica entradas. La compra recibida ya no se
revisa como borrador y no existe una cancelación/deshacer recepción desde este flujo.

**Advertencias:** falta de permiso de propietario, líneas pendientes, producto/categoría
inactivos o recepción ya registrada. Puede aparecer una advertencia heredada que
menciona Supabase: el servicio activo de compras utiliza API/MariaDB. No usar esa
palabra para elegir otro sistema o repetir la operación.

**Límites:** costo de compra no cambia precio de venta; **Condiciones de Pago** no
registra pago en Caja ni genera cuentas por pagar automáticas. Confirmar compra no
activa automáticamente el control de existencias al cobrar.

**Estado:** operativa restringida a propietario, con evidencia técnica previa;
recepción física, documentos reales y etiquetas impresas pendientes de aceptación.

### 8.3 Entrada directa de inventario

**Acceso/ruta:** `OWNER`, `MANAGE_INVENTORY`, sucursal activa → `/admin?tab=stock`
→ acción de entrada → **Registrar Entrada de Inventario**.

1. Seleccionar **Planta o Producto**, capturar **Cantidad Recibida** real y las notas
   o remisión que correspondan.
2. Revisar y confirmar el formulario; consultar saldo e historial al terminar.
3. Si hubo una respuesta incierta, revisar el resultado antes de reenviar y conservar
   los datos originales. No cambiar cantidades para sustituir una entrada dudosa.

**Resultado/datos:** crea recepción directa y aumenta stock. No crea documento de
compra, costo de proveedor ni pago. Si la misma mercancía ya se recibió mediante
compra, registrarla aquí otra vez duplicaría su entrada.

**Advertencias/estado:** cantidad inválida, permisos/sucursal y comunicación. Solo
propietario; implementada, aceptación sobre recepción real pendiente.

## 9. Caja Web: pendientes, cobro, recuperación y comprobantes

### 9.1 Consultar pendientes y actualizar la bandeja

**Acceso/ruta:** `OPERATE_CASHIER` y sucursal activa; base `CASHIER`, `MANAGER`,
`ADMIN`, `OWNER` → `/panel` → **Abrir Caja** (`/caja`) → **Cobrar pendientes**.

1. Confirmar sucursal y nombre de quien opera Caja.
2. Consultar **Por cobrar**; **Buscar por folio** admite el folio actual o anterior.
3. Abrir detalle antes de recibir dinero. **Ver más ventas** carga más páginas;
   **Actualizar** recorre la ventana cargada, también cuando supera 50 ventas.
4. Quitar la búsqueda con **Mostrar todas** si corresponde.

**Resultado/datos:** consulta ventas enviadas a Caja, sus productos e importes
capturados. La lista mantiene el orden del contrato por ID descendente; no asumir
que su primer registro sea el más antiguo. Actualizar no crea ventas ni modifica
pagos. Una selección se conserva si sigue válida; una venta cobrada/cancelada
puede dejar de aparecer como pendiente.

**Advertencias:** sin coincidencias no equivale a venta inexistente en otra sucursal.
Sin conexión se conserva la última lista, que puede estar desactualizada. Polling
se pausa con pestaña oculta, sin conexión o durante operaciones críticas/recuperación.
El bloqueo de **Actualizar** durante un cobro es una protección normal.

**Propietario/estado:** consulta no exclusiva de `OWNER`. P1 validado sintéticamente
con 25, 50, 51 y 100 ventas y lectura productiva; atención real de la bandeja
pendiente de aceptación. No generar 51 ventas ficticias en producción para probarlo.

### 9.2 Abrir atención y confirmar un cobro

**Acceso/ruta:** mismo acceso → venta pendiente → **Cobrar venta**.

1. Verificar folio, productos, cantidades y total antes de recibir el dinero.
2. **Cobrar venta** toma atención/reserva temporal para evitar cobro simultáneo.
   Otra persona puede ver que la venta está siendo atendida; la reserva puede vencer.
3. Elegir **Efectivo**, **Tarjeta** o **Transferencia**:
   - Efectivo: capturar **Efectivo recibido ($)**; debe cubrir el total. Revisar cambio.
   - Tarjeta: comprobar pago en el terminal; referencia de autorización opcional.
   - Transferencia: comprobar recepción bancaria y capturar referencia obligatoria.
4. Marcar **Confirmo que recibí el pago de esta venta** y **Confirmar cobro**.
5. Esperar **Pago registrado** y el comprobante. Volver a pendientes al concluir.

**Resultado/datos:** crea pago y cambio de estado de venta, conserva los importes
históricos y genera comprobante. Si el control de inventario está activo, registra
la salida correspondiente una sola vez; si no lo está, aplica el régimen de carga
inicial (§7.4). No modifica precios de catálogo. Tarjeta/transferencia son registro
del dinero recibido fuera del sistema: la pantalla no carga automáticamente una
tarjeta ni ejecuta una transferencia bancaria.

**Advertencias:** otra persona atiende la venta, atención vencida, efectivo insuficiente,
referencia faltante, producto no preparado o stock insuficiente cuando aplica.
No introducir CVV ni número completo de tarjeta en referencia. **Volver a abrir
cobro** sirve para atención vencida; no sustituye la conciliación de un pago incierto.

**Propietario/estado:** cobrar no exige `OWNER` adicional si se tiene permiso de Caja.
Flujo implementado y probado técnicamente; cobro y concordancia de efectivo/banco/
inventario reales pendientes de aceptación autorizada. Esta guía no autoriza recibir
dinero para una venta de prueba.

### 9.3 Resultado incierto y recuperación de un cobro

**Acceso/ruta:** misma cuenta, sucursal y navegador donde se inició → `/caja` →
**Comprobar pago pendiente** o **Comprobar resultado**.

1. Si falta confirmación, conservar la pantalla y datos del navegador.
2. No volver a pedir dinero al cliente ni iniciar otro cobro para la misma venta.
3. Recuperar conexión/acceso y pulsar **Comprobar resultado**.
4. El sistema consulta el intento y conserva clave, método, importe y referencia
   originales para la recuperación que proceda. Si confirma pago, mostrar su ticket.
5. Si no logra resolverlo, conservar el intento y solicitar revisión con el folio
   y hora; no borrar almacenamiento ni improvisar una venta sustituta.

**Resultado/datos:** consulta o recupera el mismo cobro. Puede reanudar su operación
original según el estado autoritativo; no es un botón para cobrar de nuevo. Los
datos de un intento recuperado pueden aparecer bloqueados para conservar su cuerpo.
Mientras exista incertidumbre, se bloquean cambio de venta y otras operaciones.

**Advertencias/estado:** cerrar sesión/recargar no acredita que un cobro falló. La
recuperación es de la identidad original y requiere conexión/permiso vigente.
Implementada y verificada técnicamente; aceptación operativa de recuperación
pendiente. Simular pérdida de respuesta solo en un entorno aislado.

### 9.4 Imprimir y reimprimir

**Acceso/ruta:** pago confirmado → **Imprimir ticket**. Para referencias guardadas:
`/caja` → **Comprobantes y reimpresión** → **Ver ticket {folio}** → **Reimprimir ticket**.

1. Comprobar sucursal, folio, fecha, productos, total, método y efectivo/cambio.
2. Imprimir mediante el diálogo del navegador. La pantalla indica Black Pos, papel
   **58 mm**, escala **100 %**, sin encabezados ni pies de página.
3. Si se cancela o falla la impresión, volver a imprimir el mismo comprobante.
4. Para reimpresión posterior, seleccionar su referencia local; necesita conexión
   para consultar el comprobante vigente. La copia lleva **REIMPRESIÓN**.

**Resultado/datos:** impresión/presentación local y consulta del pago; **no confirma
otro cobro**. El ticket es **comprobante interno sin validez fiscal**, no CFDI.
La lista guarda hasta 100 referencias de ese usuario/sucursal en ese navegador;
no es un buscador completo de todas las ventas históricas ni de otros equipos.

**Advertencias:** borrar datos del navegador pierde esas referencias; una falla de
almacenamiento exige conservar el ticket antes de cerrar. El diálogo de impresión
no acredita salida física correcta. Tickets largos pueden ocupar varias páginas.
No hay impresión silenciosa ni apertura automática del cajón confirmadas.

**Propietario/estado:** permisos del cobro/consulta, sin `OWNER` exclusivo. Presentación
probada técnicamente; impresora/controlador, márgenes, acentos, longitud y reimpresión
físicos pendientes de aceptación.

### 9.5 Venta directa desde Web

**Acceso/ruta:** `/caja` → **Venta directa**. Exige `OPERATE_CASHIER`, `CREATE_SALES`,
`VIEW_CATALOG` y sucursal activa; base `MANAGER`, `ADMIN`, `OWNER`. El cajero base
sin `CREATE_SALES` no ve esta opción; `SALES` base no puede entrar a esta ruta.

1. En **Buscar por**, elegir nombre o código/lector QR. La consulta de código
   requiere `SCAN_PRODUCTS`; no se confirma cámara Web en este formulario.
2. **Buscar** / **Agregar por código**, añadir productos y capturar cantidades enteras.
3. Opcionalmente abrir **Cliente opcional**, buscar y elegir un cliente existente;
   sin selección se usa **Público general**.
4. **Cotizar venta** solicita precios actuales al servidor; revisar el resumen.
5. **Confirmar venta y abrir cobro** crea una venta pendiente y conduce al cobro (§9.2).
6. Si quedó intento pendiente, **Recuperar venta y abrir cobro** o **Resolver intento
   para volver al carrito** comprueban primero el resultado autoritativo.

**Resultado/datos:** carrito/cotización, luego venta y partidas persistidas; todavía
no hay pago por confirmar la venta. Los productos en borrador/no preparados no
se convierten en mercancía vendible por agregarlos. La cotización confirma precios
y promociones, no un descuento manual inventado por el operador.

**Advertencias/estado:** precio/disponibilidad cambiados, producto/código no encontrado,
permisos o almacenamiento del intento. Implementada con pruebas previas; venta
directa/cobro reales pendientes de aceptación. No tiene equivalente nativo habilitado
en Android 1.0.11.

### 9.6 Cancelación pendiente, cortes y devoluciones

**Cancelación sin dinero recibido:** permiso de Caja, venta sin cobro/atención crítica
→ detalle → **Cancelar venta pendiente** → motivo → confirmar que no se recibió
dinero → **Confirmar cancelación**. Si hay intento incierto, **Consultar cancelación
pendiente**. Guarda cancelación e historial, sin salida de inventario; si procede
de pedido Web, cancela también el pedido ligado. No cancela una venta ya pagada.

**Corte:** `/caja` → **Cierre de caja y devoluciones** → **Actualizar resumen**;
revisar operaciones de ese usuario/sucursal no incluidas en un corte, fondo inicial
y efectivo contado; marcar revisión y **Registrar corte**. Persiste corte y vincula
operaciones. El primer corte incluye su historial sin cerrar, no necesariamente
solo el día de hoy. No es un cierre de todas las cajas de todas las sucursales.

**Devolución total:** en la misma sección, requiere `OPERATE_CASHIER` y
`MANAGE_DISCOUNTS` (base gerencia/administración/propiedad). Capturar folio,
**Verificar ticket e importe**, motivo y medio usado; marcar reposición solo si se
recibieron todos los productos vendibles, confirmar que ya se devolvió el dinero
y **Registrar devolución total**. Registra devolución y cambio de estado; puede
crear reposición de stock según contrato y opción. No devuelve dinero en banco
automáticamente ni admite una devolución parcial en esta pantalla.

**Advertencias:** venta ya pagada/cancelada, atendida por otro, sin devolución válida,
devolución ya registrada o resultado incierto. Estos flujos no sustituyen **Comprobar
resultado** del pago. Cortes/devoluciones conservan sus claves para recuperación
y pueden bloquear otras operaciones hasta resolver el resultado.

**Propietario/estado:** no exclusivos de `OWNER` por contrato; capacidades aplican.
Son cambios comerciales de riesgo alto, implementados con pruebas previas, pendientes
de aceptación autorizada. No ejecutarlos para documentar ni para corregir un ticket
mal impreso. La devolución no se resta del reporte diario bruto de cobros (§11.4).

## 10. Carrito público y pedidos recibidos

### 10.1 Cliente público: cotizar, registrar y recuperar

**Acceso/ruta:** sin cuenta interna, `/catalogo` → **Agregar** o icono de carrito
**Ver carrito** → panel **Tu pedido**.

1. Revisar productos/cantidades; usar `+`, `−` o eliminar según corresponda.
2. Capturar **Nombre completo**, teléfono y/o correo válido; se necesita al menos
   un medio de contacto. Seleccionar **Sucursal de recolección**; notas opcionales.
3. **Cotizar pedido** consulta al servidor. El total estimado se convierte en
   **Total cotizado**; revisar antes de continuar.
4. **Confirmar pedido con total cotizado** registra el pedido; esperar comprobante.
5. Usar **Copiar folio** y **Imprimir / Guardar comprobante** si se desea. Formatos
   disponibles: PDF/papel A4, térmica 80 mm o 58 mm; escala 100 %, sin encabezados/pies.
6. **Ver último comprobante guardado** permite abrir la copia registrada en el
   navegador. Es la fotografía del pedido al registrarse, no seguimiento actualizado
   de pago/entrega por sí sola.

**Resultado/datos:** cotización consulta precios/promociones y disponibilidad
comercial; confirmación crea pedido, partidas y contacto. No crea un pago, no cobra
en línea ni descuenta stock. El comprobante indica **Pendiente de pago en sucursal**
y no sustituye el ticket de Caja. No se confirma entrega a domicilio en este carrito.

**Cuatro resultados que debe distinguir el cliente:**

| Situación | Acción visible/esperada | Tratamiento |
| --- | --- | --- |
| Pedido registrado con comprobante | Ver comprobante/folio | Conservarlo; no enviar otro para obtener otra copia. |
| Resultado incierto | **Recuperar o reenviar pedido original** | Conservar clave, productos, contacto y total originales; consultar/reintentar el mismo intento. |
| Rechazo definitivo confirmado por el servidor | Mensaje para corregir y volver a cotizar | Corregir contacto/productos y volver a cotizar; se retira solo el intento definitivamente rechazado. |
| Pedido registrado pero falta comprobante | **Recuperar comprobante** | Recuperar el mismo pedido; no crearlo como uno nuevo. |

**Advertencias:** contacto inválido, producto retirado/no preparado, precio cambiado,
sucursal no disponible, tienda ocupada, falta de conexión o almacenamiento bloqueado.
Un 404 de recuperación aislado no demuestra que nunca se creó el pedido. Dos pestañas
se coordinan mediante protección del navegador; si otra confirmó, consultar su
comprobante antes de preparar otro. No usar otro dispositivo ni borrar datos para
eludir un intento incierto. Sin soporte de bloqueo entre pestañas, el envío puede
quedar restringido.

**Propietario/estado:** público, sin autorización `OWNER` para un pedido ordinario.
P1 verificado con ocho escenarios sintéticos; aceptación real de contacto, folio,
recuperación y comprobante público pendientes. Imprimir no genera otro pedido.

### 10.2 Personal: recepción, preparación y envío a Caja

**Acceso/ruta:** `VIEW_BRANCH_SALES` o `VIEW_ALL_SALES` → Ventas → **Pedidos de
clientes** (`/admin?tab=pedidos`). Gerencia base ve su sucursal; propiedad/admin
pueden consultar globalmente. Las escrituras usan su sucursal asignada activa.

1. **Actualizar**; buscar por folio, cliente o producto, y **Ver detalle**.
2. Revisar contacto, sucursal, notas, productos, descuento y total aceptados.
3. Usar las transiciones permitidas: **Pendiente → Confirmado → Listo para recoger
   → Completado**. Cancelación está sujeta al estado y vínculo con Caja.
4. En **Confirmado** o **Listo para recoger**, **Enviar a Caja para cobro presencial**
   crea/recupera la venta asociada y muestra **Folio en Caja**.
5. Cobrar con la cuenta/sucursal autorizadas en Caja. Actualizar los pedidos para
   consultar **Cobrado**; completar entrega solo cuando el backend lo autorice.

**Resultado/datos:** cambios de estado con historial; envío a Caja crea una única
venta ligada, con precios/partidas del pedido. Reenviar a Caja recupera el vínculo,
sin crear otra venta. Consultar globalmente no permite operar otra sucursal sin
asignación vigente. El indicador **Valor no cancelado** no es ingreso cobrado.

**Advertencias:** revisión cambiada por otro usuario, transición inválida, producto
ya no disponible y pago requerido para completar. Una vez ligado a Caja, el servidor
rechaza cancelación desde Pedidos, aunque la interfaz llegue a ofrecerla para una
venta no cobrada: usar cancelación de venta pendiente en Caja cuando proceda. Una
venta pagada necesita el flujo de devolución, no cancelación ordinaria.

**Propietario/estado:** estados/envío no exclusivos de `OWNER`; se validan capacidades
y propia sucursal. Implementado con evidencia técnica previa; recorrido real
pedido → personal → Caja → entrega pendiente de aceptación. No se confirma una
notificación automática de cada cambio por correo/WhatsApp al cliente.

## 11. Clientes, promociones, Boletín Verde y reportes

### 11.1 Clientes

**Acceso/ruta:** `MANAGE_USERS`, base `OWNER`/`ADMIN` → `/admin?tab=clientes`.
La búsqueda de cliente opcional en venta directa tiene su acceso de venta separado.

1. Escribir al menos dos caracteres en **Buscar cliente**; consultar coincidencias.
2. Para alta, abrir **Registrar Cliente**, capturar nombre completo y contacto;
   guardar mediante el formulario.
3. Para corrección, **Editar** y guardar datos actuales.
4. En venta directa autorizada, elegir un existente o **Usar público general**.

**Resultado/datos:** alta/edición del directorio de clientes. Asociar un cliente a
una venta no equivale a una cuenta de personal. No se confirma un botón de borrado
o desactivación de clientes en esta pantalla; la búsqueda trae clientes activos.
Cambiar contacto no reescribe el comprobante histórico del pedido ya aceptado.
Registrar correo aquí no suscribe automáticamente al Boletín Verde.

**Advertencias/propietario/estado:** nombre/contacto inválidos, ausencia de coincidencias,
sesión/permisos o red. No exclusivo de `OWNER` si se tiene la capacidad; datos
personales requieren su uso legítimo por el operador. Implementado con pruebas
previas; directorio y asociaciones reales pendientes de aceptación.

### 11.2 Promociones de catálogo

**Acceso/ruta:** `MANAGE_DISCOUNTS`, base `MANAGER`, `ADMIN`, `OWNER` →
`/admin?tab=promociones` → **Campañas y Promociones de Catálogo**.

1. **+ Nueva Campaña / Oferta**: indicar nombre y alcance, todo el catálogo o
   productos seleccionados.
2. Elegir porcentaje o monto fijo **por unidad**, valor, descripción y las
   restricciones/vigencia que correspondan.
3. **Crear Campaña** o, para una existente, **Editar** → **Actualizar Campaña**.
4. **Pausar** / **Activar** cambia su estado. Consultar catálogo y cotización para
   comprobar precio efectivo; no cobrar una venta ficticia para verificarla.

**Resultado/datos:** crea/edita promoción y sus productos asociados. La API usa
reloj del servidor y elige el mayor descuento aplicable por producto; no acumula
todas las campañas. Monto fijo, tope y porcentaje se aplican por unidad.
**Compra Mínima** se compara en el cálculo vigente con el precio base unitario,
no con la suma completa del carrito: no prometer una promoción por total de compra.
Las ventas/pedidos ya aceptados conservan sus snapshots históricos.

**Advertencias:** valor/fechas inválidos, selección vacía, campaña pausada/fuera de
vigencia o sin cumplimiento de umbral. La etiqueta de campaña activa no garantiza
que aplique a todos los productos o en toda fecha. No se confirma un editor de
descuento manual de venta en la pantalla de Caja.

**Propietario/estado:** no exclusiva de `OWNER`, pero cambiar oferta afecta precios
futuros y requiere acuerdo comercial. Implementada y pruebas técnicas previas;
condiciones de promociones reales pendientes de aceptación.

### 11.3 Boletín Verde y correo

**Suscripción pública:** pie del sitio → **Boletín Verde** → correo → aceptar
consentimiento → **Solicitar suscripción**. Revisar bandeja/spam, abrir enlace y
confirmar en `/boletin`. Abrir el enlace por sí solo no confirma. La baja usa su
enlace correspondiente y confirmación de la pantalla; no una cuenta administrativa.

**Campañas:** `MANAGE_SETTINGS`, base `OWNER`/`ADMIN` → `/admin?tab=boletin`.

1. Capturar **Asunto** y **Mensaje** en **Preparar campaña**, y **Guardar campaña**.
2. Revisar destinatarios, enviados, bajas omitidas y advertencias de revisión.
3. Solo con autorización del envío real, **Enviar siguiente lote (hasta 20 correos)**.
4. **Actualizar** para consultar resultado. Si la creación quedó incierta, recuperar
   su texto/clave con **Recuperar campaña pendiente**.

**Resultado/datos:** suscriptor y consentimiento/confirmación/baja; campañas,
destinatarios y estado de entregas. Guardar campaña no equivale a enviarla. Solo se
envía a confirmados; bajas se revalidan. **Enviados** acredita aceptación por el
proveedor de correo, no recepción/lectura en el buzón. No hay programación automática
de campañas confirmada en este flujo.

**Advertencias:** proveedor no configurado, correo no recibido, límites de solicitudes,
envío incierto o **envíos antiguos sin confirmar**. Si el sistema pide revisión del
proveedor, no crear otra campaña para eludir el bloqueo; no hay resolución de esos
envíos inciertos confirmada como botón administrativo. No es un sistema de WhatsApp.

**Propietario/estado:** campaña requiere la capacidad, no rol `OWNER` exclusivo;
consentimiento público no requiere propietario. Implementado, pero el estado real de
Resend/remitentes y recepción de correos no se comprobó para esta guía; documentos
de configuración conservan pendientes. **Disponibilidad actual pendiente de
verificación y aceptación de correo real**, al igual que invitación/recuperación
por email. Si falta configuración, el envío queda restringido; no se comprobó
aquí que esa condición exista en la instalación publicada.

### 11.4 Historial y reportes

**Acceso/ruta:** `/admin?tab=ventas` → **Historial y reportes**. Consultas exigen
`VIEW_REPORTS`; base `MANAGER` para su sucursal y `ADMIN`/`OWNER` con visión global.

1. Elegir **Fecha Inicio** y **Fecha Fin** para el resumen diario.
2. Si existe `VIEW_ALL_SALES`, elegir **Sucursal** o **Todas las Sucursales**.
   El filtro no cambia la sucursal de operación de la cuenta.
3. **Actualizar** y revisar Ingresos Totales, Descuentos Otorgados, Transacciones
   Realizadas, Ticket Promedio y la tabla por fecha/sucursal.
4. Revisar **Top 10 Productos Más Vendidos** teniendo en cuenta su alcance distinto.

**Resultado/datos:** solo consulta; no corrige ventas, pagos ni precios guardados.
El reporte diario usa **fecha de pago en UTC**, importe debido cobrado y días del
intervalo pedido; no fecha de creación de la venta ni efectivo recibido antes del
cambio. El ingreso es **bruto histórico de cobros**: no resta devoluciones ni elimina
un pago porque luego la venta se canceló. Aunque la tabla se titule
**Historial Diario de Ventas (PAID)**, rige esta semántica del servidor.

La corrección P1 suma, por venta, descuento de cabecera más la parte positiva del
total de lista de partidas que no está ya dentro del subtotal guardado. Así evita
duplicar descuentos Web y conserva descuentos generales independientes. No calcula
históricos con precios/promociones actuales. Ejemplo conceptual: lista 1000,
final 800, descuento real 200 centavos; se reporta 200, no 400.

**Top 10:** la Web actual solicita sucursal y límite 10, **sin enviar las fechas del
filtro**. El ranking usa ventas actualmente `PAID` y snapshots de partidas; puede
abarcar todo su historial. No comparar su total con el resumen diario como si ambos
tuvieran el mismo periodo. La pantalla no acredita un listado individual completo
de transacciones, exportación Excel/PDF ni contabilidad neta de utilidad.

**Advertencias:** permiso faltante aunque exista entrada al módulo por capacidad de
ventas, fecha/rango inválidos, no datos, fallo de red o totales incompatibles. El
servidor limita el intervalo diario a 366 días. La consulta Web actual recibe hasta
**200 grupos de día/sucursal**; su adaptador no conserva el indicador `has_more`
y la pantalla no ofrece paginación del reporte. Si hay más grupos, los indicadores
calculados con las filas recibidas no acreditan el total completo del intervalo.
Reducir el periodo y cotejar resultados en lectura antes de aceptar rangos amplios;
no interpretar la ausencia de advertencia como prueba de que no se omitieron grupos.

**Propietario/estado:** consulta no exclusiva de `OWNER`; visión global requiere su
capacidad. P1 probado en MariaDB 11.4 nativa con datos sintéticos y 19 pagos históricos
en 9 grupos en lectura, con ingresos/transacciones preservados. Interpretación y
conciliación por usuarios reales pendientes de aceptación.

## 12. Android 1.0.11: qué está disponible y qué se hace en Web

La versión principal usa API/MariaDB; el grafo activo no navega a pantallas
Supabase/demo antiguas. **`NATIVE_OPERATIONS_ENABLED=false` se aplica tanto a Debug
como a Release**. No se habilitan ventas/cobros por cambiar de variante o de rol.
La compilación y pruebas documentadas no demuestran que cada celular ya tenga esta
versión instalada; comprobarla antes de aceptar el recorrido.

| Función | Navegación Android real | Rol/capacidad | Datos y resultado | Estado |
| --- | --- | --- | --- | --- |
| Acceso y cuenta | Login → inicio → perfil → **Mi cuenta** | Cuenta autorizada | Sesión API; muestra identidad, rol/sucursal; **Actualizar permisos**, **Cerrar sesión** | Implementada/probada; aceptación en cada equipo pendiente |
| Contraseña personal | **Mi cuenta → Cambiar contraseña → Guardar contraseña** | Usuario con sesión válida | Cambia su credencial y revoca sesiones (§4.5) | Operativa según sesión; aceptación pendiente |
| Consulta de catálogo | Inicio → **Catálogo** → búsqueda/categoría → **Ver detalle** | `VIEW_CATALOG`, todos los roles base | Nombre, código, precio/promoción, ficha y foto; **Más productos** / **Más categorías** | Consulta implementada; sin alta de venta |
| Consulta por código/cámara | Catálogo → **Ingresar código** o icono **Escanear código** → **Buscar** | `SCAN_PRODUCTS`; base SALES, INVENTORY, MANAGER, ADMIN, OWNER | Consulta producto por código real; no agrega venta en esta versión | Código probado; cámara/permisos/luz/etiqueta física pendientes |
| Existencias/movimientos | Inicio → **Inventario → Actualizar → Ver historial → Más movimientos** | Inventario/alertas y sucursal activa | Consulta saldo, mínimo, alertas, movimientos; **Más productos** para paginar | Modo consulta; concordancia física pendiente |
| Borradores/fotos/revisión | Inicio → **Catalogar plantas**; o **Mi cuenta → Abrir portal Web** | Acceso ofrecido según permisos; Web vuelve a validar `MANAGE_PRODUCTS` y sucursal | Abre navegador externo en `/admin`; continuar en la bandeja §6. No almacena allí los 174 en una pantalla nativa | Flujo Web; sesión independiente |
| Conteos y aprobaciones | Inventario → **Abrir conteos en Web** | Capacidad Web correspondiente; aprobación requiere OWNER | Abre `/admin`, navegar a Plantas/Stock y aplicar §7 | Nativo restringido; Web disponible según permisos |
| Reportes / Equipo | Accesos **Reportes** / **Equipo** cuando el inicio los ofrece, o **Abrir portal Web** | `VIEW_REPORTS` / `MANAGE_USERS` y requisitos de acceso del inicio | También abren `/admin`, no directamente el tab específico; elegir módulo Web | Función Web; no reporte/directorio nativo confirmado |
| Carrito comercial, Mis ventas, Caja, cobro | No ofrecidos por el inicio operativo en este modo | La restricción aplica incluso a OWNER | Se conserva código/datos históricos, pero no se habilitan ventas/pagos nuevos | **Restringida en 1.0.11**; usar Web autorizada |
| Recepción y cambios nativos de inventario | Controles de escritura no habilitados | Restricción de modo y permisos | No inicia recepciones, ajustes ni conteos nuevos | **Restringida** |
| Compras/proveedores, promociones, clientes, boletín | Abrir portal Web y elegir tab autorizado | Permisos descritos en esta guía | Sin flujo nativo actual confirmado | Web; no atribuir paridad nativa al código legado |

Pasos de aceptación de consulta Android: comprobar versión instalada → login
propio → **Mi cuenta** → catálogo/búsqueda/detalle → código manual → cámara si se
autoriza → inventario/historial → portal Web y su cuenta independiente.

**Advertencias normales:** permiso de cámara denegado, código no encontrado,
fotografía pendiente, error de conexión o sesión no validada. El inicio puede
ofrecer **Actualizar permisos** y conservar datos guardados sin habilitar módulos.
No desinstalar/borrar almacenamiento como solución a un intento histórico.

**Pendientes históricos:** existen contratos y código para consultar resultados
antiguos de inventario, pero la pantalla actual condiciona sus botones a `canWrite`,
que es falso en 1.0.11. No afirmar que el usuario puede resolver esos intentos desde
un botón nativo habilitado. Conservarlos y solicitar revisión; no habilitar el modo
comercial para resolverlos como parte del manual. El historial nativo de ventas
existe en código, pero no tiene acceso cotidiano confirmado desde el inicio actual.

## 13. Qué está verificado y qué falta aceptar

| Área | Evidencia técnica anterior utilizable | Aceptación que no acredita |
| --- | --- | --- |
| Publicación P1 | API/Web publicadas, HTTPS, rutas API sin sesión protegidas; 61 assets; DB/esquema preservados | Login y recorrido completo de cada usuario real |
| Catalogación | Pruebas de cambio de usuario/sucursal/logout, respuesta perdida, misma clave; 174 disponibles en comprobación de publicación | Aprobación individual de identidad/presentación/SKU de las 174 entradas |
| Carrito | Ocho escenarios sintéticos, incluida corrección de rechazo definitivo, comprobante pendiente y dos pestañas | Pedido real atendido y entregado por el vivero |
| Caja | Pruebas sintéticas 25/50/51/100; selección/polling/fallos; lectura de pendientes durante publicación | Recepción real del pago y conciliación física; impresora/cajón |
| Reportes | MariaDB 11.4.10 nativa sintética: 21 pruebas; ingresos 15800 intactos, descuentos 5150 → 3850 centavos. Clon 11.4.13 e históricos en lectura: 19 pagos/9 grupos | Aprobación de interpretación del reporte bruto y alcance distinto del Top 10 |
| Inventario/preparación/compras | Contratos y pruebas sintéticas previas; restricciones OWNER en código | Mercancía física, equivalencias/documentos reales, activación por sucursal |
| Android 1.0.11 | Compilación, firma/configuración y 57 pruebas focalizadas documentadas; modo consulta confirmado en código | Instalación/versión en cada teléfono, cámara, ergonomía y lectura física de QR |
| Correo/Boletín | Funciones/API y pruebas previas; configuración con pendientes documentados | Disponibilidad actual de correo, recepción, confirmación, baja y envíos reales |

Esta guía no transforma confirmaciones humanas anteriores generales de fotos/Web
en un acta de aceptación de todos estos casos. Registrar explícitamente quién hizo
cada caso, con qué rol/sucursal/equipo y qué observó.

## 14. Matriz de pruebas de aceptación propuesta

**Estado inicial de todos los casos: pendiente / no ejecutado en esta tarea.**
Cada caso debe realizarse con cuenta propia y permisos confirmados. La columna de
riesgo mide el efecto potencial de ejecutarlo, no la gravedad de un defecto.

- **Bajo:** consulta o presentación; no cambia operaciones comerciales. Login sí
  crea sesión/control de acceso; cerrar impresión no es un cobro.
- **Medio:** cambia ficha, cuenta, catálogo, borrador o consentimiento, sin movimiento
  directo de dinero/stock. Acordar registro real o fixture aislado y responsable.
- **Alto:** crea/afecta compromiso comercial, stock, pago, corte o devolución;
  requiere operación real previamente acordada o entorno aislado. La escritura
  restringida a OWNER debe ejecutarse con ese rol, no con autorización verbal.

No producir ventas/cobros ficticios ni provocar respuestas perdidas en producción
para completar la matriz. Un caso de alto riesgo puede aceptarse en aislamiento;
su evidencia no debe presentarse como prueba física productiva.

| ID | Función/ruta y acción | Rol de ejecución | Resultado esperado | Riesgo / condición |
| --- | --- | --- | --- | --- |
| A01 | `/login` → login propio → `/panel` | Cada participante | Nombre, rol y sucursal coinciden con asignación aprobada; solo módulos autorizados | Bajo |
| A02 | Entrar a `/caja`/`/admin` sin sesión o sin permisos | Cuenta sin capacidad / sin sesión | Login o acceso restringido; no datos ni acciones concedidas por URL | Bajo |
| A03 | Actualizar acceso; cerrar sesión; volver a entrar | Cada participante | Contexto vigente; logout no elimina pendientes comerciales | Bajo; no resolver cobros ajenos |
| A04 | Asignar rol/sucursal en `/admin?tab=personal` | OWNER/ADMIN con capacidades | Auditoría y nuevo acceso; sesiones revocadas al cambio; nuevo login confirma sucursal | Medio; acordar cuenta objetivo |
| A05 | Jerarquía OWNER y último propietario | OWNER/ADMIN; fixture aislado | ADMIN no modifica/concede OWNER; último propietario activo protegido | Alto para acceso; aislamiento |
| A06 | Android → Mi cuenta → cambio de contraseña | Usuario autorizado | Contraseña cambiada; sesiones cerradas; nuevo login funciona | Medio; no registrar contraseñas en evidencia |
| A07 | `/recuperar` e invitación de personal | Usuario / MANAGE_USERS | Correo recibido; enlace válido; contraseña creada; invitado aún necesita rol/sucursal | Medio; correo y destinatario autorizados |
| A08 | `/admin?tab=sucursales` → consulta | OWNER/ADMIN autorizado | Sucursales/estados visibles; contadores interpretados como registros | Bajo |
| A09 | Alta/edición/inactivación de sucursal | MANAGE_BRANCHES; fixture aislado | Código único; sucursal ocupada no se desactiva; cambios auditados | Alto por afectar operación; aislamiento |
| P01 | `/catalogo` → búsqueda, categoría, Cargar más | Público | Productos reales/páginas; sin coincidencias es estado vacío, sin mezclas demo | Bajo |
| P02 | Ficha comercial → comparar datos | MANAGE_PRODUCTS | SKU/categoría/precio/estado de preparación correctos y sucursal visible | Bajo |
| P03 | Ficha → Guardar ficha / Guardar y siguiente | INVENTORY/MANAGER/ADMIN/OWNER autorizado | Solo campos aceptados guardados; sin modificación de stock; precio bloqueado sin capacidad | Medio; ficha real autorizada o aislada |
| P04 | Foto → selección → Guardar fotografía → consultar | MANAGE_PRODUCTS | Selección no equivale a guardado; foto persiste tras consulta; formato/tamaño inválidos explicados | Medio + aceptación física de cámara |
| P05 | Catálogo → QR → imprimir y leer | MANAGE_PRODUCTS; lectura SCAN_PRODUCTS | QR corresponde al código; lectura obtiene producto correcto; no cambios de stock/precio | Bajo + impresora/cámara físicas |
| C01 | Bandeja → Todos / Por completar; buscar entrada | MANAGE_PRODUCTS | Se localizan las entradas de la precarga; originales/evidencia conservados; no aparecen como stock comercial | Bajo; cotejar contra lista autorizada, no exigir 174 en cada filtro |
| C02 | Borrador → Guardar avances incompletos → reabrir | MANAGE_PRODUCTS | Datos propuestos persistidos sin inventar pendientes; borrador no vende | Medio |
| C03 | Borrador → foto → Enviar a revisión | MANAGE_PRODUCTS | Foto guardada y estado En revisión; edición ordinaria bloqueada | Medio |
| C04 | OWNER devuelve / prepara / vincula | OWNER + MANAGE_PRODUCTS | Devuelve a completar; o producto inactivo/precio pendiente; o vínculo sin alterar producto existente | Medio; una decisión por entrada autorizada |
| C05 | Guardado, cambio de usuario/sucursal/logout, respuesta tardía | Dos identidades autorizadas; sintético | Respuesta original no borra el pendiente de otra identidad; recovery original conserva clave/cuerpo | Medio; aislamiento, sin cambiar cuentas reales para forzar fallo |
| C06 | Respuesta perdida, recarga, Comprobar guardado | MANAGE_PRODUCTS; sintético | Recupera mismo avance una vez; no duplica ficha/evento operativo | Medio; aislamiento |
| I01 | Stock e historial; comparar físicamente sin guardar | INVENTORY/MANAGER/ADMIN/OWNER | Saldo, unidad, mínimo y movimientos comprendidos; divergencias se anotan para conteo | Bajo |
| I02 | Registrar conteo y observar saldo | MANAGE_INVENTORY | Observación pendiente con motivo; saldo no cambia al enviarla | Medio; conteo real autorizado o aislado |
| I03 | Aprobar diferencia positiva/negativa/cero | OWNER + MANAGE_INVENTORY | Diferencia aplicada una vez; cero cierra sin movimiento; inicial queda confirmado | Alto; propietario, stock real acordado o aislamiento |
| I04 | Rechazar conteo; aprobar referencia vencida | OWNER; fixture aislado | Rechazo no mueve saldo; conteo desactualizado no aplica aunque el saldo se haya igualado | Alto; aislamiento para movimientos concurrentes |
| I05 | Preparar precio/conteo y habilitar producto | MANAGE_PRODUCTS + MANAGE_PRICES; OWNER aprueba conteo | Requisitos incompletos bloquean; ficha preparada queda habilitada solo para sucursal válida | Alto; modifica disponibilidad comercial |
| I06 | Consultar activación de descuento al cobrar | MANAGE_INVENTORY | Se registra estado real de esa sucursal: activo o carga inicial | Bajo; consulta antes de cualquier cobro de aceptación |
| I07 | Activar inventario y comprobar salida/stock insuficiente | OWNER; después cajero autorizado | No retroactividad; salida nueva exacta; insuficiencia rechaza sin pago registrado | Alto; no activar producción solo para probar |
| R01 | Compra → proveedor/documento → borrador | MANAGE_INVENTORY | Datos/costos conservados; total exacto; stock no cambia | Medio; documento autorizado o aislado |
| R02 | Importar CSV/PDF y revisar partidas | MANAGE_INVENTORY | Se detectan omisiones/errores; datos corregidos antes de guardar; no falsa garantía OCR | Medio; usar documento sintético inicialmente |
| R03 | Relacionar/ignorar/configurar envase; reabrir | MANAGE_INVENTORY | Equivalencias correctas; ignorados no entran; DRAFT permanece sin stock | Medio |
| R04 | Confirmar recepción y reconsultar/reintentar | OWNER + MANAGE_INVENTORY | RECEIVED; cada partida asociada suma una sola vez; precio de venta/pagos intactos | Alto; cotejo físico y permiso del responsable |
| R05 | Entrada directa → saldo/historial | OWNER + MANAGE_INVENTORY | Recepción exacta y auditada; no duplicar compra ya recibida | Alto |
| K01 | Caja → buscar folio, Ver más ventas, Actualizar | OPERATE_CASHIER | Ventanas de 25/50/51/100 actualizan; sin duplicados; selección válida conservada; orden vigente | Bajo en consulta; carga >50 en aislamiento |
| K02 | Polling/venta cobrada o cancelada/errores tardíos | OPERATE_CASHIER; sintético | Estados actualizados; ventanas previas conservadas ante fallo; sin interferencia en cobro crítico | Alto si cambia ventas; aislamiento |
| K03 | Dos operadores abren la misma venta | Dos cuentas OPERATE_CASHIER; fixture aislado | Una atención válida; otro bloqueado; no doble pago | Alto; aislamiento |
| K04 | Cobro efectivo/insuficiencia/cambio | OPERATE_CASHIER | Importe debido y cambio exactos; insuficiencia no registra pago; ticket confirmado | Alto; pago real acordado o aislamiento |
| K05 | Tarjeta/transferencia | OPERATE_CASHIER | Medio y referencia correctos; transferencia exige referencia; no se ejecuta operación bancaria automática | Alto; pago previamente acreditado |
| K06 | Cobro con respuesta perdida/recarga | OPERATE_CASHIER; sintético | Comprobar resultado recupera misma clave/datos; no nuevo dinero ni otro pago | Alto; aislamiento |
| K07 | Imprimir/cancelar impresión/reimprimir | OPERATE_CASHIER sobre pago existente autorizado | Black Pos 58 mm legible; acentos, cambio y ticket largo correctos; reimpresión sin cobro | Bajo + prueba física; sin pago nuevo |
| K08 | Venta directa → cotizar → confirmar → recuperar | OPERATE_CASHIER + CREATE_SALES + VIEW_CATALOG | Una venta pendiente; precios del servidor; cliente opcional; fallo conserva intento | Alto; operación autorizada o aislamiento |
| K09 | Cancelar venta sin dinero; intentar pagada | OPERATE_CASHIER; fixture aislado | Cancelación auditada sin movimiento; pedido ligado cancelado; venta pagada protegida | Alto; aislamiento para negativos |
| K10 | Corte con historial sin cerrar | OPERATE_CASHIER | Fondo/efectivo/diferencia exactos; solo operaciones propias de sucursal no cerradas | Alto; no crear corte de prueba productivo |
| K11 | Devolución total y reposición opcional | OPERATE_CASHIER + MANAGE_DISCOUNTS | Una devolución; motivo/medio; reposición solo cuando corresponde; no transferencia automática | Alto; dinero/mercancía reales acordados o aislamiento |
| W01 | Público → cotizar → confirmar → comprobante | Público | Un pedido con contacto/sucursal/total correctos; no pago; folio conservado | Alto por crear compromiso; pedido genuino autorizado o aislado |
| W02 | Rechazo de teléfono/precio/producto y corrección | Público; sintético | Rechazo autoritativo libera intento; corregir y recotizar funciona; 404 aislado no borra incierto | Medio/alto; aislamiento |
| W03 | Respuesta perdida/comprobante pendiente/dos pestañas | Público; sintético | Misma clave y cuerpo para incierto; un pedido; ticket recuperable; no usar otro dispositivo | Alto; aislamiento |
| W04 | Imprimir/guardar comprobante público existente | Público | A4/PDF/80/58 mm; folio/total y pendiente de pago; no crea pedido ni acredita pago | Bajo + aceptación física |
| W05 | Personal → confirmar pedido → enviar a Caja → entregar | VIEW_BRANCH_SALES/VIEW_ALL_SALES, propia sucursal; cobro OPERATE_CASHIER | Una venta ligada; importes conservados; completar requiere pago válido; global no permite escribir otra sucursal | Alto; recorrido real acordado o aislamiento |
| D01 | Clientes → búsqueda → registro/edición | MANAGE_USERS | Contacto correcto; relación con venta opcional; no suscripción automática | Medio; usar datos autorizados |
| D02 | Promoción → crear/pausar/editar → cotización | MANAGE_DISCOUNTS | Mayor descuento unitario aplicable; vigencia/umbral/tope correctos; históricos intactos | Alto por precio futuro; aislamiento o oferta real aprobada |
| B01 | Boletín → solicitar → confirmar → baja | Público, correo propio autorizado | No activo hasta confirmar; baja impide nuevo envío; entrega real comprobada | Medio; primero confirmar configuración |
| B02 | Campaña → guardar → enviar lote → resultado incierto | MANAGE_SETTINGS | Solo confirmados; hasta 20 por lote; aceptación ≠ recepción; revisión antigua bloquea duplicación | Alto por comunicación externa; envío expresamente acordado |
| T01 | Reporte diario por fechas/sucursal | VIEW_REPORTS; global VIEW_ALL_SALES | UTC/fecha de pago; bruto de cobros; descuentos sin duplicación; solo lectura | Bajo; cotejo de transacciones autorizado |
| T02 | Directas/Web/promos/general+partida/importados/devoluciones | VIEW_REPORTS; fixtures aislados | Centavos exactos; ingresos/transacciones idénticos; devolución no resta bruto diario | Bajo en lectura; preparación de fixtures aislada |
| T03 | Comparar diario y Top 10 | VIEW_REPORTS | Usuario comprende periodo diario frente a ranking sin fechas y PAID actual; no los trata como equivalentes | Bajo |
| M01 | Comprobar Android 1.0.11 y Mi cuenta | Cada participante | Versión instalada y rol/sucursal correctos; modo consulta sin ventas/cobros habilitados | Bajo; instalación separadamente autorizada si hace falta |
| M02 | Android catálogo/foto/detalle/código/cámara | VIEW_CATALOG; SCAN_PRODUCTS para código | Mismo producto/precio/foto; código desconocido controlado; lectura física correcta | Bajo + teléfono/cámara físicos |
| M03 | Android inventario/historial → Web | Inventario/alertas y permisos Web | Consulta propia sucursal; portal pide su propia sesión; no escritura nativa | Bajo |
| M04 | Intentos/carrito antiguos; cambio de modo/rol | Responsable técnico en entorno aislado | Se conservan; botones nativos no prometen recuperación habilitada ni ventas nuevas | Medio; no borrar/reprocesar datos del teléfono operativo |

### Registro para cada resultado

| Campo | Completar al ejecutar |
| --- | --- |
| Caso y entorno | ID de matriz; producción en lectura / operación real autorizada / entorno aislado |
| Persona, rol y sucursal | Nombre del participante y rol observado; sin contraseñas ni tokens |
| Fecha y equipo | Fecha/hora, navegador o versión Android, teléfono/impresora cuando aplique |
| Datos usados | Folio/registro permitido para el acta privada; no copiar contactos reales al repositorio |
| Resultado esperado y observado | Describir coincidencia o desviación; fotografía física si corresponde y está autorizada |
| Dictamen | Aprobado / rechazado / bloqueado / no aplica, con motivo |
| Aceptación | Responsable que confirma y fecha; pendiente si falta intervención humana |

## 15. Pendientes que debe resolver el manual definitivo

1. Confirmar roles/capacidades y sucursales de los participantes; no asumir que el
   cargo de gerente o propietaria coincide con la cuenta usada.
2. Confirmar versión instalada en cada Android, cámara/galería, lectura de QR y
   ergonomía con el teléfono real. No habilitar ventas nativas para esta aceptación.
3. Verificar/cerrar identificación de las 174 entradas, SKU/presentación, precio
   autorizado, conteo aprobado y preparación por sucursal; no aceptar en bloque por
   la existencia de la precarga.
4. Registrar estado de activación de inventario de cada sucursal y acordar la
   conciliación física; no activarlo sin inventario revisado.
5. Aceptar recepción real, Caja/medios de pago, recuperación y papel/lector con los
   equipos del vivero y una operación acordada. La matriz no se ejecutó aquí.
6. Confirmar configuración y entrega de invitaciones, recuperación y Boletín.
   No publicar como operativo el correo solo por la existencia del formulario.
7. Aceptar explícitamente límites del sistema actual: reporte bruto en UTC, Top 10
   sin filtro de fechas, reimpresión local limitada, comprobantes no fiscales,
   devolución total y ausencia de cobro online/contabilidad de proveedores.
8. Registrar textos heredados y estados UI/backend discrepantes para revisión
   posterior: advertencia Supabase en compras, título PAID del reporte diario,
   cancelación de pedido ya ligado a Caja y botones de pendientes nativos bloqueados.
   Este documento los describe; no introduce correcciones de código.

## 16. Fuentes de comprobación

Los enlaces siguientes son evidencia local para quien prepare el manual, no rutas
que el personal deba escribir en el navegador. La guía no obtiene funcionalidades
de las pantallas demo ni de contratos Supabase antiguos.

- Navegación y acceso Web:
  [router](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/app/router.tsx),
  [reglas de módulos](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/access/access-rules.ts),
  [tareas y nombres](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/access/admin-navigation.ts),
  [panel administrativo](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/AdminPage.tsx),
  [panel de acceso](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/internal-home/PanelPage.tsx).
- Roles y autoridad:
  [asignación base](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/database/mysql/migrations/002_identity.sql),
  [personal/sucursales](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/backend/src/administration.js),
  [política OWNER de inventario](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/backend/src/inventory-policy.js).
- Plantas, borradores y fotos:
  [espacio Plantas](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/plants/AdminPlants.tsx),
  [bandeja de borradores](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/plants/CatalogingBoard.tsx),
  [ficha comercial](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/plants/PlantDetail.tsx),
  [contrato de catalogación](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/backend/src/cataloging.js),
  [preparación](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/backend/src/product-preparation.js).
- Conteos/recepción:
  [Stock](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/AdminInventory.tsx),
  [revisión](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/InventoryCountReview.tsx),
  [observaciones backend](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/backend/src/inventory-observations.js),
  [compras Web](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/purchases/PurchaseDraftWizard.tsx),
  [compras backend](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/backend/src/purchases.js).
- Caja:
  [pantalla](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/cashier/CashierPage.tsx),
  [panel de pago](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/cashier/CashierPaymentPanel.tsx),
  [reimpresión](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/cashier/CashierReceiptHistory.tsx),
  [cortes/devoluciones](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/cashier/CashierOperations.tsx),
  [impresión física pendiente](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/docs/receipt-printing.md).
- Pedidos:
  [carrito](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/public-catalog/CartDrawer.tsx),
  [comprobante público](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/public-orders/PublicOrderTicket.tsx),
  [gestión de pedidos](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/AdminOrders.tsx),
  [restricciones de estados](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/backend/src/web-order-admin.js).
- Clientes, promociones, correo y reportes:
  [clientes](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/AdminCustomers.tsx),
  [promociones](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/AdminPromotions.tsx),
  [cálculo de precio](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/backend/src/catalog-pricing.js),
  [boletín](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/newsletter/AdminNewsletter.tsx),
  [correo pendiente](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/docs/backend-email-config.md),
  [consulta Web de reportes](C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/src/features/admin/useAdminReports.ts),
  [fórmula/contrato](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/backend/src/reports.js).
- Android actual:
  [versión/restricción común](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/app/build.gradle.kts),
  [grafo activo](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/app/src/main/java/com/intutec/viveroapp/navigation/ViveroApp.kt),
  [inicio y cuenta](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/app/src/main/java/com/intutec/viveroapp/feature/home/presentation/BackendHomeScreen.kt),
  [consolidación y pruebas previas](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/docs/android-main-consolidation.md).
- Evidencia previa de publicación/aceptación limitada:
  [entrega P1](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/docs/p1-publication-20261012.md),
  [piloto Android y confirmación anterior de fotos Web](C:/Users/GAMER/AndroidStudioProjects/ViveroApp/docs/android-cataloging-pilot.md).
