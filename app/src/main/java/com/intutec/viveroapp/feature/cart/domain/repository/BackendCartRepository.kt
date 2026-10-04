package com.intutec.viveroapp.feature.cart.domain.repository

import com.intutec.viveroapp.feature.catalog.domain.repository.BackendCatalogProduct
import kotlinx.coroutines.flow.Flow

data class BackendCartLine(val productId: Long, val name: String, val unit: String, val priceCents: Long, val quantity: Int)
data class BackendCartSnapshot(val id: Long, val revision: Long, val identity: BackendSaleIdentity, val items: List<BackendCartLine>) {
    fun saleLines(): List<BackendSaleLine> = items.map { BackendSaleLine(it.productId, it.quantity) }
}
interface BackendCartRepository {
    fun observe(identity: BackendSaleIdentity): Flow<BackendCartSnapshot?>
    suspend fun add(product: BackendCatalogProduct)
    suspend fun quantity(productId: Long, quantity: Int)
    suspend fun remove(productId: Long)
    suspend fun clear()
}
