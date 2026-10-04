package com.intutec.viveroapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.intutec.viveroapp.feature.cart.data.local.CartDao
import com.intutec.viveroapp.feature.cart.data.local.BackendSaleAttemptDao
import com.intutec.viveroapp.feature.cart.data.local.BackendSaleAttemptEntity
import com.intutec.viveroapp.feature.cart.data.local.BackendSaleAttemptItemEntity
import com.intutec.viveroapp.feature.cart.data.local.BackendCartDao
import com.intutec.viveroapp.feature.cart.data.local.BackendCartEntity
import com.intutec.viveroapp.feature.cart.data.local.BackendCartItemEntity
import com.intutec.viveroapp.feature.cart.data.local.CartHeaderEntity
import com.intutec.viveroapp.feature.cart.data.local.CartItemEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleItemEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleStatusHistoryEntity
import com.intutec.viveroapp.feature.cashier.data.local.CashierPaymentAttemptDao
import com.intutec.viveroapp.feature.cashier.data.local.CashierPaymentAttemptEntity
import com.intutec.viveroapp.feature.cashier.data.local.BackendPaymentAttemptDao
import com.intutec.viveroapp.feature.cashier.data.local.BackendPaymentAttemptEntity
import com.intutec.viveroapp.feature.inventory.data.local.BackendInventoryAttemptDao
import com.intutec.viveroapp.feature.inventory.data.local.BackendInventoryAttemptEntity

@Database(
    entities = [
        CartHeaderEntity::class,
        CartItemEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SaleStatusHistoryEntity::class,
        CashierPaymentAttemptEntity::class,
        BackendSaleAttemptEntity::class,
        BackendSaleAttemptItemEntity::class,
        BackendCartEntity::class,
        BackendCartItemEntity::class,
        BackendPaymentAttemptEntity::class,
        BackendInventoryAttemptEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class ViveroDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao
    abstract fun backendSaleAttemptDao(): BackendSaleAttemptDao
    abstract fun backendCartDao(): BackendCartDao
    abstract fun backendPaymentAttemptDao(): BackendPaymentAttemptDao
    abstract fun backendInventoryAttemptDao(): BackendInventoryAttemptDao
    abstract fun cashierPaymentAttemptDao(): CashierPaymentAttemptDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS backend_inventory_attempts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        actor_id INTEGER NOT NULL,
                        branch_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        action TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        notes TEXT,
                        attempt_key TEXT NOT NULL,
                        state TEXT NOT NULL,
                        server_id INTEGER
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backend_inventory_attempts_attempt_key ON backend_inventory_attempts(attempt_key)")
            }
        }
        val RECOVER_INTERRUPTED_BACKEND_INVENTORY_SQL =
            "UPDATE backend_inventory_attempts SET state='UNCERTAIN' WHERE state='SYNCING'"
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS backend_cart_drafts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        actor_id INTEGER NOT NULL,
                        branch_id INTEGER NOT NULL,
                        revision INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backend_cart_drafts_actor_id_branch_id ON backend_cart_drafts(actor_id, branch_id)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS backend_cart_items (
                        cart_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        price_cents INTEGER NOT NULL,
                        quantity INTEGER NOT NULL,
                        PRIMARY KEY(cart_id, product_id),
                        FOREIGN KEY(cart_id) REFERENCES backend_cart_drafts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS backend_payment_attempts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        attempt_key TEXT NOT NULL,
                        actor_id INTEGER NOT NULL,
                        branch_id INTEGER NOT NULL,
                        sale_id INTEGER NOT NULL,
                        body TEXT NOT NULL,
                        state TEXT NOT NULL,
                        receipt TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backend_payment_attempts_attempt_key ON backend_payment_attempts(attempt_key)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS backend_sale_attempts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        attempt_key TEXT NOT NULL,
                        actor_id INTEGER NOT NULL,
                        branch_id INTEGER NOT NULL,
                        expected_total_cents INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        server_sale_id INTEGER,
                        server_folio TEXT,
                        server_status TEXT,
                        last_error TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backend_sale_attempts_attempt_key ON backend_sale_attempts(attempt_key)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS backend_sale_attempt_items (
                        attempt_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        quantity INTEGER NOT NULL,
                        PRIMARY KEY(attempt_id, product_id),
                        FOREIGN KEY(attempt_id) REFERENCES backend_sale_attempts(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
            }
        }
        val RECOVER_INTERRUPTED_BACKEND_SALES_SQL =
            "UPDATE backend_sale_attempts SET state = 'UNCERTAIN', last_error = 'Envío interrumpido; recuperar antes de reenviar.' WHERE state = 'SYNCING'"
        val RECOVER_INTERRUPTED_BACKEND_PAYMENTS_SQL =
            "UPDATE backend_payment_attempts SET state = 'UNCERTAIN' WHERE state = 'SYNCING'"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cart_items ADD COLUMN stock_known INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sale_items ADD COLUMN stock_known INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sales ADD COLUMN branch_id TEXT")
                db.execSQL("ALTER TABLE sales ADD COLUMN sync_state TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE sales ADD COLUMN sync_attempt_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sales ADD COLUMN sync_last_error TEXT")
                db.execSQL("ALTER TABLE sales ADD COLUMN sync_last_attempt_at_epoch_ms INTEGER")
                db.execSQL(
                    """
                    UPDATE sales
                    SET sync_state = CASE WHEN sync_pending = 0 THEN 'SYNCED' ELSE 'FAILED' END,
                        sync_last_error = CASE WHEN sync_pending = 1
                            THEN 'Venta anterior sin contexto de sucursal; requiere revisión.'
                            ELSE NULL END
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sales_sync_state_created_at_epoch_ms " +
                        "ON sales(sync_state, created_at_epoch_ms)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cashier_payment_attempts (
                        sale_id TEXT NOT NULL,
                        claim_token TEXT NOT NULL,
                        idempotency_key TEXT NOT NULL,
                        method TEXT,
                        amount_received_cents INTEGER,
                        reference TEXT,
                        state TEXT NOT NULL,
                        payload_locked INTEGER NOT NULL,
                        claim_created_at_epoch_ms INTEGER NOT NULL,
                        claim_expires_at_epoch_ms INTEGER NOT NULL,
                        server_time_at_claim_epoch_ms INTEGER NOT NULL,
                        observed_at_epoch_ms INTEGER NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        last_error_code TEXT,
                        PRIMARY KEY(sale_id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_cashier_payment_attempts_idempotency_key " +
                        "ON cashier_payment_attempts(idempotency_key)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cashier_payment_attempts_state_updated_at_epoch_ms " +
                        "ON cashier_payment_attempts(state, updated_at_epoch_ms)",
                )
            }
        }

        val RECOVER_INTERRUPTED_SYNC_SQL =
            """
            UPDATE sales
            SET sync_state = 'PENDING', sync_pending = 1,
                sync_last_error = 'Sincronización interrumpida; lista para reintentar.'
            WHERE sync_state = 'SYNCING'
            """.trimIndent()

        val RECOVER_INTERRUPTED_PAYMENT_SQL =
            """
            UPDATE cashier_payment_attempts
            SET state = 'UNCERTAIN',
                last_error_code = 'RESPONSE_UNKNOWN'
            WHERE state = 'CONFIRMING'
            """.trimIndent()

        // A SUCCEEDED attempt is canonical evidence of a remote PAID response. No history or outbox row is created.
        val RECONCILE_SUCCEEDED_PAYMENT_SALES_SQL =
            """
            UPDATE sales
            SET status = 'PAID', sync_state = 'SYNCED', sync_pending = 0,
                sync_last_error = NULL
            WHERE status = 'SENT_TO_CASHIER'
              AND EXISTS (
                  SELECT 1 FROM cashier_payment_attempts attempt
                  WHERE attempt.sale_id = sales.id AND attempt.state = 'SUCCEEDED'
              )
            """.trimIndent()

        // Terminal attempts are diagnostic only after settlement. UNCERTAIN and active attempts are never removed.
        val CLEANUP_TERMINAL_PAYMENT_ATTEMPTS_SQL =
            """
            DELETE FROM cashier_payment_attempts
            WHERE state IN ('SUCCEEDED', 'FAILED', 'RELEASED', 'EXPIRED')
              AND updated_at_epoch_ms <
                  (CAST(strftime('%s', 'now', '-30 days') AS INTEGER) * 1000)
            """.trimIndent()
    }
}
