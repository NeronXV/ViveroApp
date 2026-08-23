package com.intutec.viveroapp.feature.cart

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.intutec.viveroapp.core.database.ViveroDatabase
import com.intutec.viveroapp.feature.cart.data.local.CartHeaderEntity
import com.intutec.viveroapp.feature.cart.data.local.CartItemEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleItemEntity
import com.intutec.viveroapp.feature.cart.data.repository.RoomCartRepository
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.RolePermissions
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.feature.cashier.data.local.CashierPaymentAttemptEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ViveroDatabaseV2Test {
    private lateinit var context: Context
    private val databaseNames = mutableSetOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun migration1To2PreservesSaleAndItemsAndQuarantinesLegacyPendingSale() = runBlocking {
        val name = databaseName("migration")
        val version1 = createVersion1Database(name)
        try {
            val db = version1
            db.execSQL(
                """
                INSERT INTO sales(id, folio, customer_id, customer_name, member_number,
                    subtotal_cents, discount_cents, total_cents, status, created_by,
                    created_at_epoch_ms, sync_pending)
                VALUES('$SALE_ID', '$FOLIO', NULL, NULL, NULL, 200, 0, 200,
                    'SENT_TO_CASHIER', '$USER_ID', 1, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO sale_items(sale_id, product_id, internal_code, name, image_key, unit,
                    list_price_cents, unit_price_cents, quantity, stock_available, promotion_name)
                VALUES('$SALE_ID', '$PRODUCT_ID', 'P-1', 'Producto', '', 'pieza', 100, 100, 2, 4, NULL)
                """.trimIndent(),
            )
        } finally {
            version1.close()
        }

        val database = openVersion2(name)
        try {
            val migrated = database.cartDao().getSale(SALE_ID)
            assertNotNull(migrated)
            assertEquals(FOLIO, migrated?.sale?.folio)
            assertEquals("FAILED", migrated?.sale?.syncState)
            assertEquals(1, migrated?.items?.size)
            assertTrue(migrated?.items?.single()?.stockKnown == true)
            assertNull(migrated?.sale?.branchId)
        } finally {
            database.close()
        }
    }

    @Test
    fun createsStableUuidAndFolioPersistsUnknownStockItemAndClearsCartAtomically() = runBlocking {
        val database = openVersion2(databaseName("persist"))
        try {
            val dao = database.cartDao()
            val repository = RoomCartRepository(dao)

            repository.addProduct(remoteProduct()).getOrThrow()
            val ticket = repository.createPendingSale(remoteSession()).getOrThrow()

            assertNull(dao.getCart(CART_ID))
            assertEquals(ticket.id, UUID.fromString(ticket.id).toString())
            assertTrue(ticket.folio.matches(Regex("^VD-[0-9]{6}-[A-F0-9]{6}$")))
            val persisted = dao.getSale(ticket.id)
            assertEquals(ticket.folio, persisted?.sale?.folio)
            assertEquals(1, persisted?.items?.size)
            assertTrue(persisted?.items?.single()?.stockKnown == false)
            assertEquals("PENDING", persisted?.sale?.syncState)
            assertEquals(BRANCH_ID, persisted?.sale?.branchId)
        } finally {
            database.close()
        }
    }

    @Test
    fun failedSaleInsertKeepsCartAndItems() = runBlocking {
        val database = openVersion2(databaseName("atomic_failure"))
        try {
            val dao = database.cartDao()
            dao.upsertSale(sale(id = OTHER_SALE_ID))
            dao.upsertCart(cartHeader())
            dao.upsertItem(cartItem())

            val result = runCatching {
                dao.persistSentSale(sale(), listOf(saleItem()), emptyList(), CART_ID)
            }

            assertTrue(result.isFailure)
            assertEquals(1, dao.getCart(CART_ID)?.items?.size)
            assertNull(dao.getSale(SALE_ID))
        } finally {
            database.close()
        }
    }

    @Test
    fun demoProductIdIsRejectedAndCartRemainsIntact() = runBlocking {
        val database = openVersion2(databaseName("demo_product"))
        try {
            val repository = RoomCartRepository(database.cartDao())
            repository.addProduct(remoteProduct().copy(id = "monstera", stockAvailable = 4, stockKnown = true)).getOrThrow()

            val result = repository.createPendingSale(remoteSession())

            assertTrue(result.isFailure)
            assertEquals(1, database.cartDao().getCart(CART_ID)?.items?.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun recoversInterruptedSyncingSaleToPending() = runBlocking {
        val name = databaseName("recovery")
        val database = openVersion2(name)
        try {
            val dao = database.cartDao()
            dao.upsertSale(sale())
            assertEquals(1, dao.claimPendingSale(SALE_ID, 500))
            assertEquals("SYNCING", dao.getSale(SALE_ID)?.sale?.syncState)
        } finally {
            database.close()
        }

        val reopened = openVersion2(name)
        try {
            val recovered = reopened.cartDao().getSale(SALE_ID)?.sale
            assertEquals("PENDING", recovered?.syncState)
            assertEquals(1, recovered?.syncAttemptCount)
            assertEquals(500L, recovered?.syncLastAttemptAtEpochMs)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun synchronizationStateUpdatesAreConditionalAndAtomic() = runBlocking {
        val database = openVersion2(databaseName("state"))
        try {
            val dao = database.cartDao()
            dao.upsertSale(sale())

            assertEquals(1, dao.claimPendingSale(SALE_ID, 900))
            assertEquals(0, dao.claimPendingSale(SALE_ID, 901))
            assertEquals(1, dao.markSaleSynced(SALE_ID, "SENT_TO_CASHIER"))
            assertEquals(0, dao.markSaleFailed(SALE_ID, "No debe sobrescribir SYNCED"))

            val synced = dao.getSale(SALE_ID)?.sale
            assertEquals("SYNCED", synced?.syncState)
            assertTrue(synced?.syncPending == false)
            assertEquals(1, synced?.syncAttemptCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun migration2To3PreservesExistingDataAndCreatesDurablePaymentAttempt() = runBlocking {
        val name = databaseName("payment-migration")
        createVersion2Database(name).also { db ->
            db.execSQL(
                """
                INSERT INTO sales(id, folio, customer_id, customer_name, member_number,
                    subtotal_cents, discount_cents, total_cents, status, created_by,
                    created_at_epoch_ms, sync_pending, branch_id, sync_state,
                    sync_attempt_count, sync_last_error, sync_last_attempt_at_epoch_ms)
                VALUES('$SALE_ID', '$FOLIO', NULL, NULL, NULL, 200, 0, 200,
                    'SENT_TO_CASHIER', '$USER_ID', 1, 0, '$BRANCH_ID', 'SYNCED', 1, NULL, 2)
                """.trimIndent(),
            )
            db.close()
        }
        val database = openVersion2(name)
        try {
            assertEquals(FOLIO, database.cartDao().getSale(SALE_ID)?.sale?.folio)
            val attempt = paymentAttempt("CLAIMED", false)
            database.cashierPaymentAttemptDao().upsert(attempt)
            val restored = database.cashierPaymentAttemptDao().get(SALE_ID)
            assertEquals(PAYMENT_KEY, restored?.idempotencyKey)
            assertEquals(CLAIM_TOKEN, restored?.claimToken)
            assertTrue(restored?.payloadLocked == false)
        } finally {
            database.close()
        }
    }

    @Test
    fun freshInstallCreatesVersion3AndAllExistingTables() = runBlocking {
        val database = openVersion2(databaseName("fresh-v3"))
        try {
            val sql = database.openHelper.readableDatabase
            val names = sql.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            assertTrue(names.containsAll(setOf("cart_drafts", "cart_items", "sales", "sale_items", "sale_status_history", "cashier_payment_attempts")))
            assertEquals(3, sql.version)
        } finally {
            database.close()
        }
    }

    @Test
    fun terminalAttemptCleanupIsLimitedAndNeverDeletesUncertain() = runBlocking {
        val name = databaseName("payment-cleanup")
        openVersion2(name).also { database ->
            database.cashierPaymentAttemptDao().upsert(paymentAttempt("SUCCEEDED", true))
            database.cashierPaymentAttemptDao().upsert(
                paymentAttempt("UNCERTAIN", true).copy(saleId = OTHER_SALE_ID, idempotencyKey = OTHER_PAYMENT_KEY),
            )
            database.close()
        }
        val reopened = openVersion2(name)
        try {
            assertNull(reopened.cashierPaymentAttemptDao().get(SALE_ID))
            assertEquals("UNCERTAIN", reopened.cashierPaymentAttemptDao().get(OTHER_SALE_ID)?.state)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun succeededAttemptCannotBeLockedOrConfirmedAgain() = runBlocking {
        val database = openVersion2(databaseName("payment-succeeded"))
        try {
            database.cashierPaymentAttemptDao().upsert(
                paymentAttempt("SUCCEEDED", true).copy(updatedAtEpochMs = System.currentTimeMillis()),
            )
            val relock = runCatching {
                database.cashierPaymentAttemptDao().persistAndLock(SALE_ID, "CASH", 10_000, null, System.currentTimeMillis())
            }
            assertTrue(relock.isFailure)
            assertEquals("SUCCEEDED", database.cashierPaymentAttemptDao().get(SALE_ID)?.state)
        } finally {
            database.close()
        }
    }

    @Test
    fun interruptedPaymentConfirmationRecoversAsUncertainWithoutChangingKeyOrPayload() = runBlocking {
        val name = databaseName("payment-recovery")
        val database = openVersion2(name)
        database.cashierPaymentAttemptDao().upsert(paymentAttempt("CONFIRMING", true))
        database.close()

        val reopened = openVersion2(name)
        try {
            val restored = reopened.cashierPaymentAttemptDao().get(SALE_ID)
            assertEquals("UNCERTAIN", restored?.state)
            assertEquals("RESPONSE_UNKNOWN", restored?.lastErrorCode)
            assertEquals(PAYMENT_KEY, restored?.idempotencyKey)
            assertEquals(10_000L, restored?.amountReceivedCents)
            assertTrue(restored?.payloadLocked == true)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun succeededPaymentRepairIsIdempotentLeavesUncertainSaleCartAndHistoryUntouched() = runBlocking {
        val name = databaseName("payment-sale-reconciliation")
        openVersion2(name).also { database ->
            val dao = database.cartDao()
            dao.upsertSale(sale().copy(syncPending = false, syncState = "SYNCED"))
            dao.upsertSale(
                sale(OTHER_SALE_ID).copy(
                    folio = "VD-260815-999999",
                    syncPending = false,
                    syncState = "SYNCED",
                ),
            )
            dao.upsertCart(cartHeader())
            dao.upsertItem(cartItem())
            val now = System.currentTimeMillis()
            database.cashierPaymentAttemptDao().upsert(
                paymentAttempt("SUCCEEDED", true).copy(updatedAtEpochMs = now),
            )
            database.cashierPaymentAttemptDao().upsert(
                paymentAttempt("UNCERTAIN", true).copy(
                    saleId = OTHER_SALE_ID,
                    idempotencyKey = OTHER_PAYMENT_KEY,
                    updatedAtEpochMs = now,
                ),
            )
            database.close()
        }

        repeat(2) {
            openVersion2(name).also { database ->
                val paid = database.cartDao().getSale(SALE_ID)?.sale
                val uncertain = database.cartDao().getSale(OTHER_SALE_ID)?.sale
                assertEquals("PAID", paid?.status)
                assertEquals("SYNCED", paid?.syncState)
                assertTrue(paid?.syncPending == false)
                assertEquals("SENT_TO_CASHIER", uncertain?.status)
                assertEquals("UNCERTAIN", database.cashierPaymentAttemptDao().get(OTHER_SALE_ID)?.state)
                assertEquals(1, database.cartDao().getCart(CART_ID)?.items?.size)
                val historyCount = database.openHelper.readableDatabase.query(
                    "SELECT count(*) FROM sale_status_history WHERE sale_id = ?",
                    arrayOf(SALE_ID),
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
                assertEquals(0, historyCount)
                database.close()
            }
        }
    }

    private fun createVersion1Database(name: String): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) = createV1Schema(db)
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        return helper.writableDatabase
    }

    private fun createVersion2Database(name: String): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createV1Schema(db)
                            ViveroDatabase.MIGRATION_1_2.migrate(db)
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        return helper.writableDatabase
    }

    private fun openVersion2(name: String): ViveroDatabase =
        Room.databaseBuilder(context, ViveroDatabase::class.java, name)
            .addMigrations(ViveroDatabase.MIGRATION_1_2, ViveroDatabase.MIGRATION_2_3)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL(ViveroDatabase.RECOVER_INTERRUPTED_SYNC_SQL)
                        db.execSQL(ViveroDatabase.RECOVER_INTERRUPTED_PAYMENT_SQL)
                        db.execSQL(ViveroDatabase.RECONCILE_SUCCEEDED_PAYMENT_SALES_SQL)
                        db.execSQL(ViveroDatabase.CLEANUP_TERMINAL_PAYMENT_ATTEMPTS_SQL)
                    }
                },
            )
            .build()

    private fun createV1Schema(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cart_drafts (id TEXT NOT NULL, customer_id TEXT, customer_name TEXT, member_number TEXT, updated_at_epoch_ms INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS cart_items (cart_id TEXT NOT NULL, product_id TEXT NOT NULL, internal_code TEXT NOT NULL, name TEXT NOT NULL, image_key TEXT NOT NULL, unit TEXT NOT NULL, list_price_cents INTEGER NOT NULL, unit_price_cents INTEGER NOT NULL, quantity INTEGER NOT NULL, stock_available INTEGER NOT NULL, promotion_name TEXT, PRIMARY KEY(cart_id, product_id), FOREIGN KEY(cart_id) REFERENCES cart_drafts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cart_items_cart_id ON cart_items(cart_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sales (id TEXT NOT NULL, folio TEXT NOT NULL, customer_id TEXT, customer_name TEXT, member_number TEXT, subtotal_cents INTEGER NOT NULL, discount_cents INTEGER NOT NULL, total_cents INTEGER NOT NULL, status TEXT NOT NULL, created_by TEXT NOT NULL, created_at_epoch_ms INTEGER NOT NULL, sync_pending INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_folio ON sales(folio)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sale_items (sale_id TEXT NOT NULL, product_id TEXT NOT NULL, internal_code TEXT NOT NULL, name TEXT NOT NULL, image_key TEXT NOT NULL, unit TEXT NOT NULL, list_price_cents INTEGER NOT NULL, unit_price_cents INTEGER NOT NULL, quantity INTEGER NOT NULL, stock_available INTEGER NOT NULL, promotion_name TEXT, PRIMARY KEY(sale_id, product_id), FOREIGN KEY(sale_id) REFERENCES sales(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_sale_id ON sale_items(sale_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sale_status_history (id TEXT NOT NULL, sale_id TEXT NOT NULL, previous_status TEXT, new_status TEXT NOT NULL, user_id TEXT NOT NULL, changed_at_epoch_ms INTEGER NOT NULL, note TEXT, PRIMARY KEY(id), FOREIGN KEY(sale_id) REFERENCES sales(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_status_history_sale_id ON sale_status_history(sale_id)")
    }

    private fun databaseName(label: String) = "vivero-$label-${System.nanoTime()}.db".also(databaseNames::add)

    private fun cartHeader() = CartHeaderEntity(CART_ID, null, null, null, 1)
    private fun cartItem() = CartItemEntity(CART_ID, PRODUCT_ID, "P-1", "Producto", "", "pieza", 100, 100, 2, 0, false, null)
    private fun saleItem() = SaleItemEntity(SALE_ID, PRODUCT_ID, "P-1", "Producto", "", "pieza", 100, 100, 2, 0, false, null)
    private fun sale(id: String = SALE_ID) = SaleEntity(
        id, FOLIO, null, null, null, 200, 0, 200, "SENT_TO_CASHIER", USER_ID,
        BRANCH_ID, 1, true, "PENDING", 0, null, null,
    )

    private fun paymentAttempt(state: String, locked: Boolean) = CashierPaymentAttemptEntity(
        saleId = SALE_ID,
        claimToken = CLAIM_TOKEN,
        idempotencyKey = PAYMENT_KEY,
        method = "CASH",
        amountReceivedCents = 10_000,
        reference = null,
        state = state,
        payloadLocked = locked,
        claimCreatedAtEpochMs = 1,
        claimExpiresAtEpochMs = 301_000,
        serverTimeAtClaimEpochMs = 1_000,
        observedAtEpochMs = 1_000,
        createdAtEpochMs = 1_000,
        updatedAtEpochMs = 1_000,
        lastErrorCode = null,
    )

    private fun remoteSession() = UserSession(
        userId = USER_ID,
        email = "",
        fullName = "Owner",
        role = UserRole.OWNER,
        capabilities = RolePermissions.permissionsFor(UserRole.OWNER),
        branch = UserBranch(BRANCH_ID, "CENTRO", "Sucursal Centro", true),
        mode = SessionMode.REMOTE,
    )

    private fun remoteProduct() = Product(
        id = PRODUCT_ID,
        internalCode = "P-1",
        barcode = null,
        commonName = "Producto remoto",
        scientificName = null,
        description = "",
        category = Category("55555555-5555-4555-8555-555555555555", "Categoría"),
        priceCents = 100,
        wholesalePriceCents = null,
        unit = "pieza",
        stockAvailable = 0,
        minimumStock = 0,
        imageKey = "",
        wateringAdvice = "",
        lightType = "",
        recommendedClimate = "",
        isActive = true,
        promotion = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        stockKnown = false,
    )

    companion object {
        private const val CART_ID = "active-cart"
        private const val SALE_ID = "11111111-1111-4111-8111-111111111111"
        private const val OTHER_SALE_ID = "99999999-9999-4999-8999-999999999999"
        private const val USER_ID = "22222222-2222-4222-8222-222222222222"
        private const val BRANCH_ID = "33333333-3333-4333-8333-333333333333"
        private const val PRODUCT_ID = "44444444-4444-4444-8444-444444444444"
        private const val FOLIO = "VD-260815-111111"
        private const val CLAIM_TOKEN = "66666666-6666-4666-8666-666666666666"
        private const val PAYMENT_KEY = "77777777-7777-4777-8777-777777777777"
        private const val OTHER_PAYMENT_KEY = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    }
}
