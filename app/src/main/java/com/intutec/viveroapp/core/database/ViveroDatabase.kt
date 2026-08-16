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

@Database(
    entities = [
        CartHeaderEntity::class,
        CartItemEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SaleStatusHistoryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ViveroDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao

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

        val RECOVER_INTERRUPTED_SYNC_SQL =
            """
            UPDATE sales
            SET sync_state = 'PENDING', sync_pending = 1,
                sync_last_error = 'Sincronización interrumpida; lista para reintentar.'
            WHERE sync_state = 'SYNCING'
            """.trimIndent()
    }
}
