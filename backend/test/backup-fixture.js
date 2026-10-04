// Synthetic rehearsal only, invoked explicitly through the local tools profile.
import mysql from 'mysql2/promise';
import sharp from 'sharp';
import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';

if (process.env.BACKUP_REHEARSAL !== '1' || process.env.NODE_ENV !== 'development' ||
    process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero' || process.env.DB_USER !== 'root') throw new Error('Local rehearsal required');
const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD, supportBigNumbers: true, bigNumberStrings: true });
try {
  if (process.env.BACKUP_FIXTURE_MODE === 'create') {
    for (const table of ['branches', 'users', 'products', 'sales', 'product_images']) {
      const [[row]] = await db.execute(`SELECT COUNT(*) n FROM ${table}`);
      assert.equal(Number(row.n), 0, 'Requires a new empty rehearsal database');
    }
    const image = await sharp({ create: { width: 2, height: 2, channels: 3, background: '#247c44' } }).webp().toBuffer();
    const key = createHash('sha256').update(image).digest('hex') + '.webp';
    await db.beginTransaction();
    const [branch] = await db.execute("INSERT INTO branches(code,name) VALUES('BACKUP-DEMO','Sucursal sintética de respaldo')");
    const [user] = await db.execute("INSERT INTO users(email,full_name,branch_id,role_id,is_active) VALUES('backup@example.invalid','Persona sintética',?,(SELECT id FROM roles WHERE name='OWNER'),0)", [branch.insertId]);
    const [category] = await db.execute("INSERT INTO categories(name) VALUES('Respaldo sintético')");
    const [product] = await db.execute("INSERT INTO products(internal_code,common_name,category_id,price_cents) VALUES('BACKUP-TEST','Planta sintética',?,12345)", [category.insertId]);
    await db.execute('INSERT INTO inventory(branch_id,product_id,minimum_stock) VALUES(?,?,2.125)', [branch.insertId, product.insertId]);
    await db.execute("INSERT INTO inventory_movements(branch_id,product_id,movement_type,quantity) VALUES(?,?,'OPENING',11.125)", [branch.insertId, product.insertId]);
    const [sale] = await db.execute("INSERT INTO sales(folio,branch_id,created_by,status,subtotal_cents,total_cents) VALUES('BACKUP-SYNTHETIC',?,?,'PAID',12345,12345)", [branch.insertId, user.insertId]);
    await db.execute("INSERT INTO sale_items(sale_id,product_id,product_name,quantity,unit_price_cents,line_total_cents) VALUES(?,?,'Planta sintética',1,12345,12345)", [sale.insertId, product.insertId]);
    await db.execute("INSERT INTO cashier_payments(sale_id,cashier_id,idempotency_key,method,amount_due_cents,amount_received_cents,change_cents) VALUES(?,?,'backup-synthetic-payment','CASH',12345,20000,7655)", [sale.insertId, user.insertId]);
    await db.execute('INSERT INTO product_images(product_id,storage_key,is_primary,width,height,byte_size) VALUES(?,?,1,2,2,?)', [product.insertId, key, image.length]);
    await writeFile('/data/catalog-images/' + key, image, { flag: 'wx' });
    await db.commit();
  } else if (process.env.BACKUP_FIXTURE_MODE !== 'verify') throw new Error('Invalid rehearsal mode');
  const [[tables]] = await db.execute("SELECT COUNT(*) n FROM information_schema.tables WHERE table_schema='vivero'");
  assert.equal(Number(tables.n), 44);
  const [[versions]] = await db.execute('SELECT COUNT(*) n FROM schema_migrations'); assert.equal(Number(versions.n), 24);
  const [[payment]] = await db.execute("SELECT p.amount_due_cents,p.amount_received_cents,p.change_cents,s.total_cents FROM cashier_payments p JOIN sales s ON s.id=p.sale_id WHERE s.folio='BACKUP-SYNTHETIC'");
  assert.deepEqual(payment, { amount_due_cents: '12345', amount_received_cents: '20000', change_cents: '7655', total_cents: '12345' });
  const [[stock]] = await db.execute("SELECT i.branch_id,i.product_id,i.quantity,i.minimum_stock FROM inventory i JOIN products p ON p.id=i.product_id WHERE p.internal_code='BACKUP-TEST'");
  assert.equal(stock.quantity, '11.125'); assert.equal(stock.minimum_stock, '2.125');
  const [[imageRow]] = await db.execute('SELECT storage_key,byte_size FROM product_images');
  assert.match(imageRow.storage_key, /^[a-f0-9]{64}\.webp$/);
  const bytes = await readFile('/data/catalog-images/' + imageRow.storage_key);
  assert.equal(bytes.length, imageRow.byte_size);
  assert.equal(createHash('sha256').update(bytes).digest('hex') + '.webp', imageRow.storage_key);
  const [[user]] = await db.execute("SELECT id FROM users WHERE email='backup@example.invalid'");
  await db.beginTransaction();
  await db.execute("INSERT INTO inventory_movements(branch_id,product_id,movement_type,quantity,idempotency_hash,created_by) VALUES(?,?,'RECEPTION',2.125,?,?)", [stock.branch_id, stock.product_id, randomBytes(32), user.id]);
  const [[changed]] = await db.execute('SELECT quantity FROM inventory WHERE branch_id=? AND product_id=?', [stock.branch_id, stock.product_id]);
  assert.equal(changed.quantity, '13.250', 'Restored trigger must update inventory');
  await assert.rejects(db.execute("INSERT INTO products(internal_code,common_name,category_id,price_cents) VALUES('INVALID-FK','Invalid fixture',4294967295,1)"), error => error.code === 'ER_NO_REFERENCED_ROW_2');
  await db.rollback();
  console.log('Synthetic backup fixture verified: money, relationships, stock, image bytes, trigger and FK enforcement.');
} finally { await db.rollback(); await db.end(); }
