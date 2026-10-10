# Evaluación de SMTP para Vivero Dulcinea

Revisión de código y configuración del 9 de octubre de 2026. Responsable de
elegir/proveer el servidor: Pedro y Toni. No se contrató ni configuró un proveedor.

## Compatibilidad actual

El backend **todavía no admite SMTP**. `backend/src/auth/mail.js` envía por HTTPS
a Resend y `backend/src/server.js` construye ese transporte con `RESEND_API_KEY`.
Compose no declara variables SMTP y no existe una biblioteca SMTP en package.json.
Poner host/usuario SMTP en un `.env` por sí solo no habilita el envío.

La lógica de enlaces ya está separada del transporte: `createAccountLinks` recibe
un mailer. Se puede añadir un adaptador SMTP al mismo backend, seleccionarlo en
server.js y reutilizar el contenido del correo, sin crear otro sistema de cuentas.
Hace falta implementar y probar ese adaptador; este cierre documenta la opción
porque todavía no hay servidor ni credenciales con los que comprobar entrega.

## Datos necesarios para decidir e implementar

- Host, puerto y modalidad exigida por el administrador SMTP: TLS desde conexión
  o STARTTLS obligatorio, con certificado válido. No desactivar su validación.
- Usuario y secreto individual del servicio; mecanismo de autenticación permitido.
  Guardarlos solo en configuración privada del servidor, nunca en VITE/Android/Git.
- Remitente y dominio autorizados, dirección de respuesta y límites de envío.
- Registros SPF/DKIM/DMARC requeridos por ese servicio y responsable de configurarlos.
- Acceso saliente del VPS al puerto elegido, destinatarios de ensayo y canal para
  recibir fallos. No se presume que esos recursos estén disponibles.

Propuesta de nombres para el futuro adaptador: `MAIL_TRANSPORT=smtp`, `SMTP_HOST`,
`SMTP_PORT`, `SMTP_SECURITY`, `SMTP_USER`, `SMTP_PASSWORD`, conservando
`ACCOUNT_MAIL_FROM` y `WEB_ORIGIN`. **No son variables admitidas actualmente.**
La selección debe ser explícita y fallar si la configuración está incompleta,
sin sustituir silenciosamente SMTP por Resend. El newsletter tiene su propio
sender/configuración; no quedaría migrado automáticamente al adaptar cuentas.

## Contratos que deben conservarse

- Recuperación: respuesta pública uniforme 202 para evitar revelar cuentas;
  límites por dirección y correo, hash del token en BD, vencimiento de 30 minutos.
- Invitación: requiere MANAGE_USERS; no concede rol ni sucursal al crear la cuenta.
- Enlace de un solo uso en `/recuperar#token=…`; contraseña individual; completar
  revoca sesiones y enlaces anteriores y registra auditoría en la misma transacción.
- No devolver tokens en respuestas administrativas ni imprimirlos en logs.
- Timeout de transporte y errores seguros MAIL_UNAVAILABLE. Un timeout puede
  corresponder a correo aceptado: no generar contraseñas compartidas como salida.
- SMTP no garantiza la idempotencia del proveedor HTTP actual. Revisar reintentos
  y Message-ID estable; aun con correo duplicado el enlace debe consumirse una vez.

Hoy no hay cola durable de correo: un reinicio puede perder un envío de recuperación.
Solicitar un enlace nuevo es el mecanismo vigente; aceptación por SMTP tampoco
demuestra recepción en bandeja. Probar entrega real, spam, expiración, reenvío,
consumo doble, revocación y caída del servidor antes de declarar el correo listo.

## Cuentas importadas y situación actual

La consulta de solo lectura del VPS del 9 de octubre encontró una cuenta con hash
de contraseña y cinco sin él. Recuperación solo envía a cuentas activas que ya
tienen contraseña. Invitación rechaza cuentas que ya tienen rol/sucursal asignados.
Por eso configurar SMTP no habilita por sí solo esas cinco cuentas importadas.
Hace falta acordar un flujo individual de incorporación seguro para cuentas
preasignadas, o usar el procedimiento administrativo existente con credenciales
individuales entregadas de forma privada. No compartir una contraseña entre personas.

Pendiente: datos de SMTP, adaptador probado, recepción real y acceso de cada cuenta.
Referencia del contrato existente: [recuperación e invitaciones](backend-account-links.md).
