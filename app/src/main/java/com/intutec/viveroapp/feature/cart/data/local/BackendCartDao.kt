package com.intutec.viveroapp.feature.cart.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "backend_cart_drafts", indices = [Index(value = ["actor_id", "branch_id"], unique = true)])
data class BackendCartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("actor_id") val actorId: Long,
    @ColumnInfo("branch_id") val branchId: Long,
    val revision: Long = 1,
)
@Entity(tableName = "backend_cart_items", primaryKeys = ["cart_id", "product_id"], foreignKeys = [
    ForeignKey(entity = BackendCartEntity::class, parentColumns = ["id"], childColumns = ["cart_id"], onDelete = ForeignKey.CASCADE),
])
data class BackendCartItemEntity(
    @ColumnInfo("cart_id") val cartId: Long,
    @ColumnInfo("product_id") val productId: Long,
    val name: String,
    val unit: String,
    @ColumnInfo("price_cents") val priceCents: Long,
    val quantity: Int,
)
data class BackendCartWithItems(
    @Embedded val cart: BackendCartEntity,
    @Relation(parentColumn = "id", entityColumn = "cart_id") val items: List<BackendCartItemEntity>,
)
@Dao
abstract class BackendCartDao {
    @Transaction @Query("SELECT * FROM backend_cart_drafts WHERE actor_id = :actor AND branch_id = :branch")
    abstract fun observe(actor: Long, branch: Long): Flow<BackendCartWithItems?>
    @Transaction @Query("SELECT * FROM backend_cart_drafts WHERE actor_id = :actor AND branch_id = :branch")
    abstract suspend fun load(actor: Long, branch: Long): BackendCartWithItems?
    @Insert protected abstract suspend fun insert(row: BackendCartEntity): Long
    @Upsert protected abstract suspend fun put(row: BackendCartItemEntity)
    @Query("UPDATE backend_cart_drafts SET revision = revision + 1 WHERE id = :id")
    protected abstract suspend fun bump(id: Long): Int
    @Query("DELETE FROM backend_cart_items WHERE cart_id = :cart AND product_id = :product")
    protected abstract suspend fun deleteItem(cart: Long, product: Long): Int
    @Query("DELETE FROM backend_cart_drafts WHERE actor_id = :actor AND branch_id = :branch")
    abstract suspend fun clear(actor: Long, branch: Long)

    @Transaction
    open suspend fun add(actor: Long, branch: Long, product: Long, name: String, unit: String, price: Long) {
        val saved = load(actor, branch)
        val previous = saved?.items?.firstOrNull { it.productId == product }
        require(previous != null || (saved?.items?.size ?: 0) < 25) { "La comanda admite hasta 25 productos distintos." }
        val quantity = (previous?.quantity ?: 0) + 1
        require(quantity <= 100000) { "Cantidad fuera del límite." }
        val id = saved?.cart?.id ?: insert(BackendCartEntity(actorId = actor, branchId = branch))
        put(BackendCartItemEntity(id, product, name, unit, price, quantity))
        if (saved != null) check(bump(id) == 1)
    }
    @Transaction
    open suspend fun quantity(actor: Long, branch: Long, product: Long, quantity: Int) {
        require(quantity in 1..100000) { "Cantidad fuera del límite." }
        val saved = checkNotNull(load(actor, branch)) { "El carrito está vacío." }
        val previous = checkNotNull(saved.items.firstOrNull { it.productId == product }) { "El producto ya no está en el carrito." }
        put(previous.copy(quantity = quantity)); check(bump(saved.cart.id) == 1)
    }
    @Transaction
    open suspend fun remove(actor: Long, branch: Long, product: Long) {
        val saved = load(actor, branch) ?: return
        if (deleteItem(saved.cart.id, product) == 1) check(bump(saved.cart.id) == 1)
    }
}
