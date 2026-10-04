"""Local SQLite verification of migration 3->4; no device or application DB access.

Run after Gradle generated ViveroDatabase_Impl.kt. This supplements, and does
not replace, instrumented Room migration validation.
"""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[4]
source = (ROOT / "app/src/main/java/com/intutec/viveroapp/core/database/ViveroDatabase.kt").read_text(encoding="utf-8")
generated = (ROOT / "app/build/generated/ksp/debug/kotlin/com/intutec/viveroapp/core/database/ViveroDatabase_Impl.kt").read_text(encoding="utf-8")
creation = re.findall(r'connection\.execSQL\("(CREATE [^"\n]+)"\)', generated)
assert creation, "Run Gradle first to generate the current Room schema"
block = source.split("val MIGRATION_3_4 =", 1)[1].split("val RECOVER_INTERRUPTED_BACKEND_SALES_SQL", 1)[0]
migration = [multi or single for multi, single in re.findall(r'db\.execSQL\((?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]+)")\)', block, re.S)]
assert len(migration) == 3, "Unexpected migration statements"

def database():
    db = sqlite3.connect(":memory:", isolation_level=None)
    db.execute("PRAGMA foreign_keys=ON")
    return db

expected, migrated = database(), database()
for sql in creation:
    expected.execute(sql)
    if "backend_" not in sql:
        migrated.execute(sql)

sale_id = "11111111-1111-4111-8111-111111111111"
migrated.execute("""INSERT INTO sales(id,folio,subtotal_cents,discount_cents,total_cents,status,created_by,
    branch_id,created_at_epoch_ms,sync_pending,sync_state) VALUES(?, 'DEMO',900,0,900,
    'SENT_TO_CASHIER','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333',1,1,'PENDING')""", (sale_id,))
migrated.execute("""INSERT INTO sale_items(sale_id,product_id,internal_code,name,image_key,unit,list_price_cents,
    unit_price_cents,quantity,stock_available) VALUES(?, '44444444-4444-4444-8444-444444444444','DEMO','Demo','','pieza',450,450,2,3)""", (sale_id,))
before = [migrated.execute(f"SELECT * FROM {table}").fetchall() for table in ("sales", "sale_items")]
with migrated:
    for sql in migration:
        migrated.execute(sql)
assert before == [migrated.execute(f"SELECT * FROM {table}").fetchall() for table in ("sales", "sale_items")]

def schema(db, table):
    columns = db.execute(f"PRAGMA table_info({table})").fetchall()
    foreign_keys = db.execute(f"PRAGMA foreign_key_list({table})").fetchall()
    indexes = []
    for _, name, unique, origin, partial in db.execute(f"PRAGMA index_list({table})"):
        indexes.append((name, unique, origin, partial, db.execute(f"PRAGMA index_info({name})").fetchall()))
    return columns, foreign_keys, sorted(indexes)

for table in ("backend_sale_attempts", "backend_sale_attempt_items"):
    assert schema(expected, table) == schema(migrated, table), table

key = "a" * 64
attempt_id = migrated.execute("INSERT INTO backend_sale_attempts(attempt_key,actor_id,branch_id,expected_total_cents,state) VALUES(?,2,3,900,'PENDING')", (key,)).lastrowid
assert attempt_id == 1
migrated.execute("INSERT INTO backend_sale_attempt_items VALUES(?,4,2)", (attempt_id,))
for sql, params in (
    ("INSERT INTO backend_sale_attempts(attempt_key,actor_id,branch_id,expected_total_cents,state) VALUES(?,2,3,900,'PENDING')", (key,)),
    ("INSERT INTO backend_sale_attempt_items VALUES(999,4,2)", ()),
    ("DELETE FROM backend_sale_attempts WHERE id=?", (attempt_id,)),
):
    try:
        migrated.execute(sql, params)
    except sqlite3.IntegrityError:
        pass
    else:
        raise AssertionError("Constraint did not protect attempt")

claim = "UPDATE backend_sale_attempts SET state='SYNCING' WHERE id=? AND state IN ('PENDING','UNCERTAIN')"
assert migrated.execute(claim, (attempt_id,)).rowcount == 1
assert migrated.execute(claim, (attempt_id,)).rowcount == 0
recovery = re.search(r'val RECOVER_INTERRUPTED_BACKEND_SALES_SQL\s*=\s*"([^"\n]+)"', source).group(1)
migrated.execute(recovery)
assert migrated.execute("SELECT attempt_key,actor_id,branch_id,expected_total_cents,state FROM backend_sale_attempts").fetchone() == (key, 2, 3, 900, "UNCERTAIN")
assert migrated.execute("SELECT product_id,quantity FROM backend_sale_attempt_items").fetchall() == [(4, 2)]
dao = (ROOT / "app/src/main/java/com/intutec/viveroapp/feature/cart/data/local/BackendSaleAttemptDao.kt").read_text(encoding="utf-8")
def query(method):
    return re.search(r'@Query\("([^"\n]+)"\)\s+abstract suspend fun ' + method + r'\(', dao).group(1)

# Execute the actual DAO predicates: only a claimed attempt may become terminal.
assert migrated.execute(query("release"), {"id": attempt_id, "state": "RETIRED", "error": "Demo closed"}).rowcount == 0
assert migrated.execute(query("claim"), {"id": attempt_id}).rowcount == 1
assert migrated.execute(query("release"), {"id": attempt_id, "state": "RETIRED", "error": "Demo closed"}).rowcount == 1
assert migrated.execute(query("pending"), {"actor": 2, "branch": 3}).fetchall() == []
assert migrated.execute(query("claim"), {"id": attempt_id}).rowcount == 0
assert migrated.execute(query("complete"), {"id": attempt_id, "saleId": 5, "folio": "Demo", "status": "PAID"}).rowcount == 0
migrated.execute(recovery)
assert migrated.execute("SELECT attempt_key,state FROM backend_sale_attempts WHERE id=?", (attempt_id,)).fetchone() == (key, "RETIRED")
assert migrated.execute("SELECT product_id,quantity FROM backend_sale_attempt_items").fetchall() == [(4, 2)]
assert before == [migrated.execute(f"SELECT * FROM {table}").fetchall() for table in ("sales", "sale_items")]
block5 = source.split("val MIGRATION_4_5 =", 1)[1].split("val MIGRATION_3_4 =", 1)[0]
ddl5 = [multi or single for multi, single in re.findall(r'db\.execSQL\((?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]+)")\)', block5, re.S)]
assert len(ddl5) == 5
for sql in ddl5:
    migrated.execute(sql)
for table in ("backend_cart_drafts", "backend_cart_items", "backend_payment_attempts"):
    assert schema(expected, table) == schema(migrated, table), table
cart_id = migrated.execute("INSERT INTO backend_cart_drafts(actor_id,branch_id,revision) VALUES(2,3,1)").lastrowid
migrated.execute("INSERT INTO backend_cart_items VALUES(?,4,'Demo','pieza',450,2)", (cart_id,))
consume = re.search(r'@Query\("([^"\n]+)"\)\s+protected abstract suspend fun consumeCart', dao).group(1)
args = {"cart": cart_id, "revision": 1, "actor": 2, "branch": 3}
assert migrated.execute(consume, {**args, "revision": 2}).rowcount == 0
assert migrated.execute(consume, {**args, "actor": 9}).rowcount == 0
# Simulate the DAO transaction: failed outbox insertion must restore the whole draft.
migrated.execute("BEGIN")
try:
    assert migrated.execute(consume, args).rowcount == 1
    migrated.execute("INSERT INTO backend_sale_attempts(attempt_key,actor_id,branch_id,expected_total_cents,state) VALUES(?,2,3,900,'PENDING')", (key,))
except sqlite3.IntegrityError:
    migrated.execute("ROLLBACK")
else:
    raise AssertionError("Duplicate-key failure expected")
assert migrated.execute("SELECT product_id,quantity FROM backend_cart_items WHERE cart_id=?", (cart_id,)).fetchall() == [(4, 2)]
migrated.execute("BEGIN")
assert migrated.execute(consume, args).rowcount == 1
new_id = migrated.execute("INSERT INTO backend_sale_attempts(attempt_key,actor_id,branch_id,expected_total_cents,state) VALUES(?,2,3,900,'PENDING')", ("b" * 64,)).lastrowid
migrated.execute("INSERT INTO backend_sale_attempt_items VALUES(?,4,2)", (new_id,))
migrated.execute("COMMIT")
assert migrated.execute("SELECT * FROM backend_cart_items WHERE cart_id=?", (cart_id,)).fetchall() == []
assert migrated.execute(consume, args).rowcount == 0
migrated.execute("INSERT INTO backend_payment_attempts(attempt_key,actor_id,branch_id,sale_id,body,state) VALUES(?,2,3,5,'{}','SYNCING')", ("c" * 64,))
payment_recovery = re.search(r'val RECOVER_INTERRUPTED_BACKEND_PAYMENTS_SQL\s*=\s*"([^"\n]+)"', source).group(1)
migrated.execute(payment_recovery)
assert migrated.execute("SELECT state FROM backend_payment_attempts").fetchone() == ("UNCERTAIN",)
payment_dao = (ROOT / "app/src/main/java/com/intutec/viveroapp/feature/cashier/data/local/BackendPaymentAttemptDao.kt").read_text(encoding="utf-8")
payment_retire = re.search(r'@Query\("([^"\n]+)"\)\s+abstract suspend fun retire\(', payment_dao).group(1)
assert migrated.execute(payment_retire, {"id": 1}).rowcount == 0
assert migrated.execute("UPDATE backend_payment_attempts SET state='SYNCING' WHERE id=1 AND state='UNCERTAIN'").rowcount == 1
assert migrated.execute(payment_retire, {"id": 1}).rowcount == 1
assert migrated.execute(payment_retire, {"id": 1}).rowcount == 0
assert migrated.execute("SELECT attempt_key,body,state FROM backend_payment_attempts WHERE id=1").fetchone() == ("c"*64, "{}", "RETIRED")
assert before == [migrated.execute(f"SELECT * FROM {table}").fetchall() for table in ("sales", "sale_items")]
block6 = source.split("val MIGRATION_5_6 =", 1)[1].split("val RECOVER_INTERRUPTED_BACKEND_INVENTORY_SQL", 1)[0]
ddl6 = [multi or single for multi, single in re.findall(r'db\.execSQL\((?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]+)")\)', block6, re.S)]
assert len(ddl6) == 2
for sql in ddl6:
    migrated.execute(sql)
assert schema(expected, "backend_inventory_attempts") == schema(migrated, "backend_inventory_attempts")
inv_key = "d" * 64
migrated.execute("INSERT INTO backend_inventory_attempts(actor_id,branch_id,product_id,action,quantity,notes,attempt_key,state) VALUES(2,3,4,'COUNT',0,'Demo',?,'SYNCING')", (inv_key,))
inv_recovery = re.search(r'val RECOVER_INTERRUPTED_BACKEND_INVENTORY_SQL\s*=\s*"([^"\n]+)"', source).group(1)
migrated.execute(inv_recovery)
assert migrated.execute("SELECT actor_id,branch_id,product_id,action,quantity,notes,attempt_key,state FROM backend_inventory_attempts").fetchone() == (2,3,4,"COUNT",0,"Demo",inv_key,"UNCERTAIN")
inventory_dao = (ROOT / "app/src/main/java/com/intutec/viveroapp/feature/inventory/data/local/BackendInventoryAttemptDao.kt").read_text(encoding="utf-8")
def inventory_query(method):
    return re.search(r'@Query\("([^"\n]+)"\)\s+abstract suspend fun ' + method + r'\(', inventory_dao).group(1)
assert migrated.execute(inventory_query("pending"), {"actor":9,"branch":3}).fetchall() == []
assert migrated.execute(inventory_query("claim"), {"id":1}).rowcount == 1
assert migrated.execute(inventory_query("claim"), {"id":1}).rowcount == 0
assert migrated.execute(inventory_query("complete"), {"id":1,"server":7}).rowcount == 1
migrated.execute(inv_recovery)
assert migrated.execute(inventory_query("claim"), {"id":1}).rowcount == 0
assert migrated.execute("SELECT attempt_key,quantity,notes,state,server_id FROM backend_inventory_attempts").fetchone() == (inv_key,0,"Demo","SUCCEEDED",7)
assert migrated.execute("SELECT attempt_key,body,state FROM backend_payment_attempts WHERE id=1").fetchone() == ("c"*64,"{}","RETIRED")
assert before == [migrated.execute(f"SELECT * FROM {table}").fetchall() for table in ("sales", "sale_items")]
print("PASS: Room 3->4->5->6 schemas, UUID preservation, FK, retirement, atomic checkout and interrupted inventory recovery verified on local SQLite")
