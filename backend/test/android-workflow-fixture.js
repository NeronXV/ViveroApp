// Synthetic fixture only for the isolated local Compose and Android JVM HTTP test.
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { hashPassword, normalizeEmail } from '../src/auth/password.js';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Isolated local Compose required');
const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
try {
  if (process.argv[2] === 'create') {
    const suffix = randomBytes(12).toString('hex'), email = normalizeEmail(`android-${suffix}@example.invalid`), password = randomBytes(24).toString('hex');
    const hash = await hashPassword(password);
    await db.beginTransaction();
    try {
      const [branch] = await db.execute('INSERT INTO branches(code,name) VALUES(?,?)', [`AND-${suffix.slice(0, 12)}`, 'Sucursal sintetica Android']);
      const [user] = await db.execute("INSERT INTO users(email,full_name,password_hash,role_id,branch_id,is_active) VALUES(?,?,?,(SELECT id FROM roles WHERE name='OWNER'),?,1)", [email, 'Persona sintetica Android', hash, branch.insertId]);
      const [category] = await db.execute('INSERT INTO categories(name) VALUES(?)', [`AND-${suffix}`]);
      const [product] = await db.execute("INSERT INTO products(internal_code,barcode,common_name,category_id,price_cents) VALUES(?,?,'Planta sintetica Android',?,500)", [`AND-${suffix}`, `AND-${suffix}`, category.insertId]);
      await db.execute('INSERT INTO inventory(branch_id,product_id,quantity) VALUES(?,?,10)', [branch.insertId, product.insertId]);
      await db.execute('UPDATE branches SET inventory_enabled=1, inventory_activated_at=UTC_TIMESTAMP(6), inventory_activated_by=? WHERE id=?', [user.insertId, branch.insertId]);
      await db.commit();
      // Redirect directly into an ignored private file; never display this JSON.
      process.stdout.write(JSON.stringify({ email, password, userId: user.insertId, branchId: branch.insertId, productId: product.insertId, code: `AND-${suffix}` }));
    } catch (error) { await db.rollback(); throw error; }
  } else if (process.argv[2] === 'clean') {
    const email = normalizeEmail(process.env.ANDROID_FIXTURE_EMAIL);
    if (!/^android-[a-f0-9]{24}@example\.invalid$/.test(email)) throw new Error('Own synthetic fixture required');
    const [[user]] = await db.execute('SELECT id,branch_id FROM users WHERE email=?', [email]);
    if (!user) throw new Error('Fixture not found');
    const [products] = await db.execute("SELECT p.id,p.category_id,i.quantity FROM products p JOIN inventory i ON i.product_id=p.id WHERE i.branch_id=? AND p.internal_code LIKE 'AND-%'", [user.branch_id]);
    if (products.length !== 1) throw new Error('Fixture identity mismatch');
    const [sales] = await db.execute('SELECT s.id,s.status,p.amount_due_cents,p.change_cents FROM sales s LEFT JOIN cashier_payments p ON p.sale_id=s.id WHERE s.branch_id=? AND s.created_by=?', [user.branch_id, user.id]);
    const verified = sales.length === 1 && sales[0].status === 'PAID' && Number(sales[0].amount_due_cents) === 1000 && Number(sales[0].change_cents) === 200 && Number(products[0].quantity) === 8;
    await db.beginTransaction();
    try {
      await db.execute('DELETE FROM inventory_counts WHERE branch_id=?', [user.branch_id]);
      await db.execute('DELETE FROM inventory_movements WHERE branch_id=?', [user.branch_id]);
      for (const s of sales) {
        await db.execute('DELETE FROM payment_attempt_retirements WHERE sale_id=?', [s.id]);
        for (const table of ['cashier_payments', 'sale_payment_claims', 'sale_status_history', 'sale_items']) await db.execute(`DELETE FROM ${table} WHERE sale_id=?`, [s.id]);
        await db.execute('DELETE FROM sales WHERE id=?', [s.id]);
      }
      await db.execute('DELETE FROM sale_attempt_retirements WHERE actor_id=? AND branch_id=?', [user.id, user.branch_id]);
      await db.execute('DELETE FROM inventory WHERE branch_id=?', [user.branch_id]);
      await db.execute('UPDATE branches SET inventory_enabled=0,inventory_activated_at=NULL,inventory_activated_by=NULL WHERE id=?', [user.branch_id]);
      await db.execute('DELETE FROM auth_sessions WHERE user_id=?', [user.id]);
      await db.execute('DELETE FROM auth_login_limits WHERE key_hash=?', [sessionDigest(`email:${email}`)]);
      await db.execute('DELETE FROM users WHERE id=?', [user.id]);
      await db.execute('DELETE FROM products WHERE id=?', [products[0].id]);
      await db.execute('DELETE FROM categories WHERE id=?', [products[0].category_id]);
      await db.execute('DELETE FROM branches WHERE id=?', [user.branch_id]);
      await db.commit();
    } catch (error) { await db.rollback(); throw error; }
    console.log('Own synthetic fixture cleaned. Sale/payment/stock reconciliation:', verified ? 'PASS' : 'NOT_COMPLETED');
    if (!verified) process.exitCode = 1;
  } else throw new Error('Choose create or clean');
} finally { await db.end(); }
