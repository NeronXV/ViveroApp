package com.intutec.viveroapp.feature.cart.domain.model

import java.time.Instant

data class Cart(
    val id: String = ACTIVE_CART_ID,
    val items: List<CartItem> = emptyList(),
    val customer: CartCustomer? = null,
    val updatedAt: Instant = Instant.now(),
) {
    val itemCount: Int get() = items.sumOf(CartItem::quantity)
    val subtotalCents: Long get() = items.moneySum { Math.multiplyExact(it.listPriceCents, it.quantity.toLong()) }
    val discountCents: Long get() = items.moneySum {
        Math.multiplyExact(it.listPriceCents - it.unitPriceCents, it.quantity.toLong())
    }
    val totalCents: Long get() = Math.subtractExact(subtotalCents, discountCents)

    companion object {
        const val ACTIVE_CART_ID = "active-cart"
    }
}

data class CartItem(
    val productId: String,
    val internalCode: String,
    val name: String,
    val imageKey: String,
    val unit: String,
    val listPriceCents: Long,
    val unitPriceCents: Long,
    val quantity: Int,
    val stockAvailable: Int,
    val stockKnown: Boolean = true,
    val promotionName: String? = null,
) {
    init {
        require(quantity > 0) { "La cantidad debe ser mayor que cero." }
        require(stockAvailable >= 0) { "La existencia no puede ser negativa." }
        require(!stockKnown || quantity <= stockAvailable) { "La cantidad supera la existencia disponible." }
        require(listPriceCents >= 0 && unitPriceCents >= 0) { "Los precios no pueden ser negativos." }
        require(unitPriceCents <= listPriceCents) { "El descuento no puede aumentar el precio." }
    }

    val lineTotalCents: Long get() = Math.multiplyExact(unitPriceCents, quantity.toLong())
}

data class CartCustomer(
    val id: String,
    val name: String,
    val memberNumber: String,
)

private inline fun List<CartItem>.moneySum(value: (CartItem) -> Long): Long =
    fold(0L) { total, item -> Math.addExact(total, value(item)) }
