package com.intutec.viveroapp.feature.cart.domain.repository

import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartCustomer
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface CartRepository {
    fun observeCart(): Flow<Cart>
    suspend fun addProduct(product: Product): Result<Unit>
    suspend fun changeQuantity(productId: String, quantity: Int): Result<Unit>
    suspend fun removeProduct(productId: String): Result<Unit>
    suspend fun associateCustomer(customer: CartCustomer?): Result<Unit>
    suspend fun saveDraft(): Result<Unit>
    suspend fun cancelCart(): Result<Unit>
    suspend fun sendToCashier(userId: String): Result<SaleTicket>
}
