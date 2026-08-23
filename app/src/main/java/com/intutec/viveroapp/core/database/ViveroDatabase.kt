package com.intutec.viveroapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.intutec.viveroapp.feature.cart.data.local.CartDao
import com.intutec.viveroapp.feature.cart.data.local.CartHeaderEntity
import com.intutec.viveroapp.feature.cart.data.local.CartItemEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleItemEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleStatusHistoryEntity
import com.intutec.viveroapp.feature.cashier.data.local.CashierPaymentAttemptDao
import com.intutec.viveroapp.feature.cashier.data.local.CashierPaymentAttemptEntity

@Database(
    entities = [
        CartHeaderEntity::class,
        CartItemEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SaleStatusHistoryEntity::class,
        CashierPaymentAttemptEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class ViveroDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao
    abstract fun cashierPaymentAttemptDao(): CashierPaymentAttemptDao

    companion object {
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
