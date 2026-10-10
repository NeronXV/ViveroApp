// Versioned opt-in representation. Legacy response fields and values remain
// unchanged unless the caller explicitly requests short-v1.
export async function shortFolioResponse(db, payload) {
  const sales = new Set(), orders = new Set();
  function visit(value, action) {
    if (value instanceof Date || Buffer.isBuffer(value)) return value;
    if (Array.isArray(value)) return value.map(row => visit(row, action));
    if (!value || typeof value !== 'object') return value;
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [key,
      ['folio', 'order_number'].includes(key) && typeof child === 'string' ? action(key, child) : visit(child, action)]));
  }
  visit(payload, (key, value) => {
    if (key === 'folio') sales.add(value);
    else if (/^VW-[0-9]+$/.test(value)) orders.add(Number(value.slice(3)));
    return value;
  });
  const aliases = new Map(), orderAliases = new Map();
  if (sales.size) {
    const values = [...sales], placeholders = values.map(() => '?').join(',');
    const [rows] = await db.execute(`SELECT s.folio, a.short_folio FROM sale_folio_aliases a
      JOIN sales s ON s.id = a.sale_id WHERE BINARY s.folio IN (${placeholders}) OR a.short_folio IN (${placeholders})`, [...values, ...values]);
    for (const row of rows) { aliases.set(row.folio, row.short_folio); aliases.set(row.short_folio, row.short_folio); }
    if (values.some(value => !aliases.has(value))) throw new Error('Alias unavailable');
  }
  if (orders.size) {
    const [rows] = await db.execute(`SELECT order_id, short_folio FROM web_order_folio_aliases
      WHERE order_id IN (${[...orders].map(() => '?').join(',')})`, [...orders]);
    for (const row of rows) orderAliases.set(row.order_id, row.short_folio);
    if ([...orders].some(value => !orderAliases.has(value))) throw new Error('Alias unavailable');
  }
  return visit(payload, (key, value) => key === 'folio' ? aliases.get(value) : orderAliases.get(Number(value.slice(3))));
}
