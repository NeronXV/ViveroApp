# Recuperación aislada del respaldo cifrado 032

Este procedimiento no usa SSH ni se conecta al VPS. No aplica 033 ni inicia los
servidores API/Web. Restaura en volúmenes nuevos de un proyecto Docker local,
sin puertos publicados y con redes internas, y detiene el clon al terminar.

## Elementos que deben conservarse

1. Copia cifrada autorizada:
   `C:/Users/GAMER/AndroidStudioProjects/ViveroApp/tmp/pilot-backup/ViveroDulcinea-premium-032-20261010-083408.aesgcm`.
   SHA-256: `43d3af0d959d98bac140e190e8a144616fb79396c1ef5909d8d4c652167c1c88`.
2. Clave protegida **separada**:
   `C:/Users/GAMER/.codex/secrets/vivero-pilot-20261010.dpapi`, bajo custodia de
   Pedro. No imprimirla, adjuntarla al respaldo ni compartirla por chat.
3. Utilidad pública permanente del repositorio:
   `infra/docker/verify-encrypted-restore.mjs`, y este procedimiento.
4. Node.js, PowerShell 7, Docker local con Compose compatible con `!reset` y
   `!override`, y `tar`. No hace falta acceso a producción ni descarga de imágenes.

El archivo cifrado incluye SQL, fotografías, manifiestos de integridad, definiciones
de cuentas/permisos, configuración privada, fuentes del servicio anterior e
imágenes API/Web/MariaDB. No se requieren `tmp/deploy-premium`, la extracción previa,
el paquete premium ni sus manifiestos. El directorio de trabajo que se indica abajo
se crea desde cero durante la recuperación; no es una dependencia previa.

La clave DPAPI exige el mismo usuario/perfil de Windows que la protegió. No basta
copiar el archivo `.dpapi` a otro equipo. Pedro debe conservar ese perfil y la
clave; para una recuperación independiente de esta PC hace falta preparar y
comprobar por separado una custodia/exportación segura de la clave. Este ensayo
no acredita todavía recuperación en otro equipo.

## Ejecución

Desde la raíz de ViveroApp, con Docker Desktop local disponible:

```powershell
node infra/docker/verify-encrypted-restore.mjs `
  --encrypted tmp/pilot-backup/ViveroDulcinea-premium-032-20261010-083408.aesgcm `
  --key-file C:/Users/GAMER/.codex/secrets/vivero-pilot-20261010.dpapi `
  --workdir tmp/restore-032-verified-20261010 `
  --project vivero-restore-032-20261010 `
  --archive-sha256 e88279ef9c05b34b74569a1b47305ba23615958e9ea5c14c3d51f0f7b801fb4c `
  --encrypted-sha256 43d3af0d959d98bac140e190e8a144616fb79396c1ef5909d8d4c652167c1c88
```

Para repetir, usar nombres nuevos de `--workdir` y `--project`; el script rechaza
destinos existentes y nunca limpia ni reutiliza sus volúmenes. No borrar el
respaldo ni el clon anterior para repetir. Reservar espacio para el descifrado,
extracción, imágenes Docker y volúmenes. La extracción contiene datos privados;
no publicarla ni incorporarla a Git. El destino `tmp/pilot-backup` es el almacén
autorizado de respaldo y debe preservarse aunque su nombre incluya `tmp`.

El script verifica la autenticación AES-256-GCM antes de usar el SQL, los hashes
externos y todos los hashes del manifiesto, y extrae las fuentes y runtimes del
propio respaldo. Inicializa únicamente hasta 032, comprueba que el destino no
contiene operaciones ni fotografías, y entonces importa la copia.

## Reconciliación PROXY, exclusivamente local

MariaDB inicializa `root@localhost` con una entrada en `mysql.proxies_priv` cuyo
usuario y host delegados están vacíos. La definición respaldada concede PROXY al
usuario vacío con host `%`. `REVOKE ALL PRIVILEGES, GRANT OPTION` no eliminó la
entrada inicial, y `SHOW GRANTS` presentó ambas como la misma sentencia.

Antes de reproducir las cuentas, la utilidad comprueba la política respaldada y
la presencia de exactamente una fila de bootstrap con:
`Host='localhost'`, `User='root'`, `Proxied_host=''`, `Proxied_user=''`,
`With_grant=1`. Sólo en el clon vacío elimina esa fila exacta de
`mysql.proxies_priv` y ejecuta `FLUSH PRIVILEGES`. Después reproduce los permisos
del respaldo y compara las listas completas, sin eliminar duplicados del
comparador. Una política distinta hace detener el script.

No copiar esta operación a producción ni usarla como limpieza general de
permisos. Las cuentas de healthcheck se regeneran para el volumen nuevo; las
definiciones originales permanecen conservadas en el suplemento cifrado.

## Evidencia requerida

Éxito requiere código de salida 0 y `restore-verification.json` con `status=PASS`
en el directorio de trabajo. Se verifican:

- Conteos y checksums de todas las tablas, incluidos usuarios de aplicación,
  roles, capacidades, sucursales, ventas, pagos e inventario.
- Definiciones exactas de cuentas técnicas y grants; acceso real de `catalog_api`
  mediante consultas de sólo lectura, sin iniciar API ni crear sesiones.
- Columnas, rutinas, triggers, eventos, definers y grants de tablas/columnas.
- Versión/configuración de MariaDB y marcadores de esquema 032.
- Todas las relaciones FK, buscando referencias huérfanas; `CHECK TABLE`.
- Fotografías restauradas comparadas por SHA-256 y health de MariaDB.

Cualquier diferencia impide marcar el respaldo verificado. El clon queda
detenido, con evidencia conservada; no hay recuperación productiva automática.
Esta copia representa el estado del 10 de octubre de 2026 a las 08:34 UTC.
Después de reabrir operaciones no sustituye un respaldo nuevo para una futura
publicación, que sigue requiriendo autorización separada.
