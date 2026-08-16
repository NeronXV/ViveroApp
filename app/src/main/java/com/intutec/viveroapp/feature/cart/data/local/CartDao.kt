package com.intutec.viveroapp.feature.cart.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {
    @Transaction
    @Query("SELECT * FROM cart_drafts WHERE id = :cartId")
    fun observeCart(cartId: String): Flow<CartWithItems?>

    @Transaction
    @Query("SELECT * FROM cart_drafts WHERE id = :cartId")
    suspend fun getCart(cartId: String): CartWithItems?

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :saleId")
    suspend fun getSale(saleId: String): SaleWithItems?

    @Upsert
    suspend fun upsertCart(cart: CartHeaderEntity)

    @Upsert
    suspend fun upsertItem(item: CartItemEntity)

    @Query("DELETE FROM cart_items WHERE cart_id = :cartId AND product_id = :productId")
    suspend fun deleteItem(cartId: String, productId: String)

    @Query("DELETE FROM cart_drafts WHERE id = :cartId")
    suspend fun deleteCart(cartId: String)

    @Upsert
    suspend fun upsertSale(sale: SaleEntity)

    @Upsert
    suspend fun upsertSaleItems(items: List<SaleItemEntity>)

    @Upsert
    suspend fun upsertHistory(history: List<SaleStatusHistoryEntity>)

    @Transaction
    suspend fun persistSentSale(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        history: List<SaleStatusHistoryEntity>,
        cartId: String,
    ) {
        upsertSale(sale)
        upsertSaleItems(items)
        upsertHistory(history)
        deleteCart(cartId)
    }

    @Query(
        """
        UPDATE sales
        SET sync_state = 'SYNCING', sync_pending = 1,
            sync_attempt_count = sync_attempt_count + 1,
            sync_last_error = NULL, sync_last_attempt_at_epoch_ms = :attemptedAt
        WHERE id = :saleId AND sync_state = 'PENDING'
        """,
    )
    suspend fun claimPendingSale(saleId: String, attemptedAt: Long): Int

    @Query(
        """
        UPDATE sales
        SET sync_state = 'PENDING', sync_pending = 1, sync_last_error = :message
        WHERE id = :saleId AND sync_state = 'SYNCING'
        """,
    )
    suspend fun markSalePending(saleId: String, message: String): Int

    @Query(
        """
        UPDATE sales
        SET sync_state = 'FAILED', sync_pending = 1, sync_last_error = :message
        WHERE id = :saleId AND sync_state = 'SYNCING'
        """,
    )
    suspend fun markSaleFailed(saleId: String, message: String): Int

    @Query(
        """
        UPDATE sales
        SET sync_state = 'SYNCED', sync_pending = 0, sync_last_error = NULL, status = :serverStatus
        WHERE id = :saleId AND sync_state = 'SYNCING'
        """,
    )
    suspend fun markSaleSynced(saleId: String, serverStatus: String): Int

    @Query(
        """
        UPDATE sales
        SET sync_state = 'PENDING', sync_pending = 1,
            sync_last_error = 'Sincronización interrumpida; lista para reintentar.'
        WHERE sync_state = 'SYNCING'
        """,
    )
    suspend fun recoverInterruptedSales(): Int
}
