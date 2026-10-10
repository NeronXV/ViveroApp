-- Lectura únicamente. Ejecutar sólo contra la BD aislada de esta etapa.
-- La sucursal se limita por el código ficticio; ninguna escritura de inventario.
SELECT id, code, name FROM branches WHERE code = 'UI7A-d40cc02ced';

SELECT s.id, s.folio AS original_folio, a.short_folio, s.status,
       s.total_cents, s.web_order_id,
       (SELECT COUNT(*) FROM cashier_payments p WHERE p.sale_id=s.id) AS payments,
       (SELECT COUNT(*) FROM sale_cancellations c WHERE c.sale_id=s.id) AS cancellations,
       (SELECT COUNT(*) FROM sale_refunds r WHERE r.sale_id=s.id) AS refunds
FROM sales s JOIN branches b ON b.id=s.branch_id
JOIN sale_folio_aliases a ON a.sale_id=s.id
WHERE b.code='UI7A-d40cc02ced' ORDER BY s.id;

SELECT p.internal_code, i.quantity AS final_quantity,
       COALESCE(SUM(m.quantity),0) AS movements_sum,
       i.quantity-COALESCE(SUM(m.quantity),0) AS unexplained_difference
FROM inventory i JOIN branches b ON b.id=i.branch_id
JOIN products p ON p.id=i.product_id
LEFT JOIN inventory_movements m ON m.branch_id=i.branch_id AND m.product_id=i.product_id
WHERE b.code='UI7A-d40cc02ced'
GROUP BY p.internal_code,i.quantity ORDER BY p.internal_code;

SELECT m.id,m.movement_type,m.product_id,m.quantity,m.sale_id,m.refund_id
FROM inventory_movements m JOIN branches b ON b.id=m.branch_id
WHERE b.code='UI7A-d40cc02ced' ORDER BY m.id;

SELECT p.sale_id,p.method,p.amount_due_cents,p.amount_received_cents,p.change_cents
FROM cashier_payments p JOIN branches b ON b.id=p.branch_id
WHERE b.code='UI7A-d40cc02ced' ORDER BY p.id;

SELECT a.short_folio,w.status,w.total_cents,s.id AS linked_sale,s.status AS sale_status
FROM web_orders w JOIN branches b ON b.id=w.branch_id
JOIN web_order_folio_aliases a ON a.order_id=w.id
LEFT JOIN sales s ON s.web_order_id=w.id
WHERE b.code='UI7A-d40cc02ced' ORDER BY w.id;

SELECT c.sale_id,c.reason,c.cancelled_by,c.branch_id,c.created_at,
       (SELECT COUNT(*) FROM sale_status_history h
        WHERE h.sale_id=c.sale_id AND h.new_status='CANCELLED') AS cancellation_audit_rows
FROM sale_cancellations c JOIN branches b ON b.id=c.branch_id
WHERE b.code='UI7A-d40cc02ced';

SELECT r.sale_id,r.amount_cents,r.method,r.restock,r.refunded_by,r.created_at
FROM sale_refunds r JOIN branches b ON b.id=r.branch_id
WHERE b.code='UI7A-d40cc02ced';

SELECT c.id,c.opening_cash_cents,c.cash_sales_cents,c.card_sales_cents,
       c.transfer_sales_cents,c.other_refunds_cents,c.expected_cash_cents,
       c.counted_cash_cents,c.difference_cents,
       (SELECT COUNT(*) FROM cashier_closing_payments p WHERE p.closing_id=c.id) AS payments,
       (SELECT COUNT(*) FROM cashier_closing_refunds r WHERE r.closing_id=c.id) AS refunds
FROM cashier_closings c JOIN branches b ON b.id=c.branch_id
WHERE b.code='UI7A-d40cc02ced';

-- Esperado: 5 ventas; 4 pagos por 67500 centavos; 1 cancelación sin movimiento;
-- 1 devolución por 8500 y reposición de Echeveria; stock 98/99/99;
-- un pedido VW-10003 vinculado a VD-0090; un corte con 4 pagos/1 devolución,
-- esperado=contado=60500, diferencia=0. Suma de movimientos=saldo, sin ajuste manual.
