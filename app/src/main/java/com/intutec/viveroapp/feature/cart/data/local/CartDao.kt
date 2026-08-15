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
}
