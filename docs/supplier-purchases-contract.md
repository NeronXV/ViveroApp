# Compras a proveedores y recepción de inventario

## Objetivo

Una compra se importa primero como `DRAFT`. En ese estado conserva las descripciones
y costos del proveedor, pero no modifica inventario. Cada renglón debe vincularse a
un producto activo o marcarse `IGNORED`; después puede confirmarse como `RECEIVED`.

Los costos de compra se almacenan en `unit_cost_cents`. Nunca se copian a
`products.price_cents`, que continúa siendo el precio de venta autorizado.

## Flujo Web

1. Crear o recuperar el proveedor mediante `upsert_supplier`.
2. Extraer el PDF y enviar los renglones a `create_supplier_purchase_draft`.
3. Mostrar la bandeja con `get_my_supplier_purchases`.
4. Abrir el detalle con `get_supplier_purchase`.
5. Completar el significado de códigos como `M06` mediante
   `set_supplier_presentation` cuando la medida sea conocida.
6. Para cada renglón:
   - buscar un producto existente;
   - crear el producto mediante `upsert_product` cuando sea necesario;
   - llamar `resolve_supplier_purchase_item` con `MATCHED`; o
   - marcar `IGNORED` con una justificación visible en la interfaz.
7. Habilitar **Confirmar recepción** solamente cuando `unmatchedCount` sea cero.
8. Confirmar mediante `confirm_supplier_purchase` con una UUID estable por intento.
9. Ofrecer las etiquetas QR de los productos vinculados.

Las UUID de idempotencia deben conservarse durante reintentos de red. Nunca debe
generarse una clave distinta simplemente porque la primera respuesta tardó.

## Contrato del borrador

`create_supplier_purchase_draft` recibe entre 1 y 250 objetos:

```json
{
  "lineNumber": 13,
  "rawDescription": "CLOROFITO",
  "containerCode": "M10",
  "suggestedCommonName": "Clorofito",
  "suggestedPresentation": "M10 - medida por confirmar",
  "quantity": 7,
  "unitCostCents": 6000
}
```

PostgreSQL calcula cada total y exige que la suma coincida con
`p_expected_total_cents`. El archivo `supplier-purchase-pilot-items.json` contiene
12 renglones, 199 unidades y un total esperado de `782500` centavos.

## Aprendizaje de equivalencias

Al vincular manualmente un renglón se guarda la combinación normalizada de:

- proveedor;
- descripción original;
- código de envase.

Una compra futura con la misma combinación se crea como `AUTO_MATCHED`. Si el
producto fue desactivado, vuelve a `UNMATCHED` y requiere revisión.

Los códigos `B02`, `M06`, `M10` y similares se registran como presentaciones del
proveedor. Su tamaño nominal queda vacío hasta que una persona confirme qué medida
representan; el sistema no infiere centímetros ni pulgadas.

## Responsabilidad Android

Android no importa documentos ni decide costos. Después de una confirmación,
consulta el saldo actualizado y usa el QR del producto para conteo, venta y
recepción visual. Una futura pantalla operativa puede mostrar el historial de
recepciones, pero la confirmación y sus reglas permanecen en PostgreSQL.

## Seguridad

- Todas las tablas tienen RLS y ningún cliente posee acceso directo.
- Las RPC requieren `MANAGE_INVENTORY` y una sucursal activa.
- Los borradores y consultas están limitados a la sucursal del actor.
- La confirmación es transaccional e idempotente.
- Cada movimiento usa el identificador del renglón como referencia única.
