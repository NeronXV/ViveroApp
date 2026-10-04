package com.intutec.viveroapp.feature.cart.data.local

import androidx.room.*

@Entity(tableName = "backend_sale_attempts", indices = [Index(value = ["attempt_key"], unique = true)])
data class BackendSaleAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("attempt_key") val key: String,
    @ColumnInfo("actor_id") val actorId: Long,
    @ColumnInfo("branch_id") val branchId: Long,
    @ColumnInfo("expected_total_cents") val expectedTotalCents: Long,
    val state: String = "PENDING",
    @ColumnInfo("server_sale_id") val serverSaleId: Long? = null,
    @ColumnInfo("server_folio") val serverFolio: String? = null,
    @ColumnInfo("server_status") val serverStatus: String? = null,
    @ColumnInfo("last_error") val lastError: String? = null,
)

@Entity(tableName = "backend_sale_attempt_items", primaryKeys = ["attempt_id", "product_id"], foreignKeys = [
    ForeignKey(entity = BackendSaleAttemptEntity::class, parentColumns = ["id"], childColumns = ["attempt_id"], onDelete = ForeignKey.RESTRICT),
])
data class BackendSaleAttemptItemEntity(
    @ColumnInfo("attempt_id") val attemptId: Long,
    @ColumnInfo("product_id") val productId: Long,
    val quantity: Int,
)
data class BackendSaleAttemptWithItems(
    @Embedded val attempt: BackendSaleAttemptEntity,
    @Relation(parentColumn = "id", entityColumn = "attempt_id") val items: List<BackendSaleAttemptItemEntity>,
)

@Dao
abstract class BackendSaleAttemptDao {
    @Insert protected abstract suspend fun insertHeader(row: BackendSaleAttemptEntity): Long
    @Insert protected abstract suspend fun insertItems(rows: List<BackendSaleAttemptItemEntity>)
    @Transaction
    open suspend fun insert(header: BackendSaleAttemptEntity, products: List<Pair<Long, Int>>): Long {
        val id = insertHeader(header)
        insertItems(products.map { (productId, quantity) -> BackendSaleAttemptItemEntity(id, productId, quantity) })
        return id
    }
    @Query("DELETE FROM backend_cart_drafts WHERE id = :cart AND revision = :revision AND actor_id = :actor AND branch_id = :branch")
    protected abstract suspend fun consumeCart(cart: Long, revision: Long, actor: Long, branch: Long): Int
    @Transaction
    open suspend fun insertFromCart(header: BackendSaleAttemptEntity, products: List<Pair<Long, Int>>, cart: Long, revision: Long): Long {
        check(consumeCart(cart, revision, header.actorId, header.branchId) == 1) { "El carrito cambió; vuelve a cotizar antes de enviar." }
        // Both operations commit or roll back together, including the cascade of draft items.
        return insert(header, products)
    }
    @Transaction @Query("SELECT * FROM backend_sale_attempts WHERE id = :id")
    abstract suspend fun load(id: Long): BackendSaleAttemptWithItems?
    @Query("SELECT id FROM backend_sale_attempts WHERE actor_id = :actor AND branch_id = :branch AND state IN ('PENDING','UNCERTAIN','SYNCING') ORDER BY id LIMIT 100")
    abstract suspend fun pending(actor: Long, branch: Long): List<Long>
    @Transaction @Query("SELECT * FROM backend_sale_attempts WHERE actor_id = :actor AND branch_id = :branch ORDER BY id DESC LIMIT 100")
    abstract suspend fun journal(actor: Long, branch: Long): List<BackendSaleAttemptWithItems>
    @Query("UPDATE backend_sale_attempts SET state = 'SYNCING', last_error = NULL WHERE id = :id AND state IN ('PENDING','UNCERTAIN')")
    abstract suspend fun claim(id: Long): Int
    @Query("UPDATE backend_sale_attempts SET state = :state, last_error = :error WHERE id = :id AND state = 'SYNCING'")
    abstract suspend fun release(id: Long, state: String, error: String): Int
    @Query("UPDATE backend_sale_attempts SET state = 'SYNCED', server_sale_id = :saleId, server_folio = :folio, server_status = :status, last_error = NULL WHERE id = :id AND state = 'SYNCING'")
    abstract suspend fun complete(id: Long, saleId: Long, folio: String, status: String): Int
}
