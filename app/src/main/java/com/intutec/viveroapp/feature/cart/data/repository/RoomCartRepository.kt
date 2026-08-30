package com.intutec.viveroapp.feature.cart.data.repository

import com.intutec.viveroapp.feature.cart.data.local.CartDao
import com.intutec.viveroapp.feature.cart.data.local.CartHeaderEntity
import com.intutec.viveroapp.feature.cart.data.local.CartItemEntity
import com.intutec.viveroapp.feature.cart.data.local.CartWithItems
import com.intutec.viveroapp.feature.cart.data.local.SaleEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleItemEntity
import com.intutec.viveroapp.feature.cart.data.local.SaleStatusHistoryEntity
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartCustomer
import com.intutec.viveroapp.feature.cart.domain.model.CartItem
import com.intutec.viveroapp.feature.cart.domain.model.SaleStatus
import com.intutec.viveroapp.feature.cart.domain.model.SaleStatusChange
import com.intutec.viveroapp.feature.cart.domain.model.SaleSyncState
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class RoomCartRepository @Inject constructor(
    private val dao: CartDao,
) : CartRepository {
    private val mutationMutex = Mutex()

    override fun observeCart(): Flow<Cart> = dao.observeCart(Cart.ACTIVE_CART_ID)
        .map { it?.toDomain() ?: Cart() }

    override suspend fun addProduct(product: Product): Result<Unit> = runCatching {
        mutationMutex.withLock {
            require(product.isActive) { "Este producto no está activo." }
            require(!product.stockKnown || product.isAvailable) { "Este producto no tiene existencia disponible." }
            val current = dao.getCart(Cart.ACTIVE_CART_ID)?.toDomain() ?: Cart()
            val existing = current.items.firstOrNull { it.productId == product.id }
            dao.upsertCart(current.copy(updatedAt = Instant.now()).toHeaderEntity())
            dao.upsertItem(nextCartItem(existing, product).toEntity(current.id))
        }
    }

    override suspend fun changeQuantity(productId: String, quantity: Int): Result<Unit> = runCatching {
        mutationMutex.withLock {
            require(quantity > 0) { "La cantidad debe ser mayor que cero." }
            val current = dao.getCart(Cart.ACTIVE_CART_ID)?.toDomain() ?: error("El carrito está vacío.")
            val item = current.items.firstOrNull { it.productId == productId } ?: error("El producto ya no está en el carrito.")
            if (item.stockKnown) {
                require(quantity <= item.stockAvailable) { "Solo hay ${item.stockAvailable} ${item.unit}(s) disponibles." }
            }
            dao.upsertCart(current.copy(updatedAt = Instant.now()).toHeaderEntity())
            dao.upsertItem(item.copy(quantity = quantity).toEntity(current.id))
        }
    }

    override suspend fun removeProduct(productId: String): Result<Unit> = runCatching {
        mutationMutex.withLock { dao.deleteItem(Cart.ACTIVE_CART_ID, productId) }
    }

    override suspend fun associateCustomer(customer: CartCustomer?): Result<Unit> = runCatching {
        mutationMutex.withLock {
            val current = dao.getCart(Cart.ACTIVE_CART_ID)?.toDomain() ?: Cart()
            dao.upsertCart(current.copy(customer = customer, updatedAt = Instant.now()).toHeaderEntity())
        }
    }

    override suspend fun saveDraft(): Result<Unit> = runCatching {
        mutationMutex.withLock {
            val current = dao.getCart(Cart.ACTIVE_CART_ID)?.toDomain() ?: error("Agrega al menos un producto para guardar el borrador.")
            require(current.items.isNotEmpty()) { "Agrega al menos un producto para guardar el borrador." }
            dao.upsertCart(current.copy(updatedAt = Instant.now()).toHeaderEntity())
        }
    }

    override suspend fun cancelCart(): Result<Unit> = runCatching {
        mutationMutex.withLock { dao.deleteCart(Cart.ACTIVE_CART_ID) }
    }

    override suspend fun createPendingSale(session: UserSession): Result<SaleTicket> = runCatching {
        mutationMutex.withLock {
            require(session.mode == SessionMode.REMOTE) { "Solo una sesión remota puede enviar comandas." }
            requireCanonicalUuid(session.userId, "La cuenta autenticada no tiene un identificador válido.")
            val branch = requireNotNull(session.branch) { "Asigna una sucursal antes de enviar comandas." }
            require(branch.isActive) { "La sucursal asignada está inactiva." }
            requireCanonicalUuid(branch.id, "La sucursal no tiene un identificador válido.")
            val cart = dao.getCart(Cart.ACTIVE_CART_ID)?.toDomain() ?: error("El carrito está vacío.")
            require(cart.items.isNotEmpty()) { "Agrega al menos un producto antes de enviar a caja." }
            cart.items.forEach {
                requireCanonicalUuid(it.productId, "El carrito contiene un producto que no pertenece al catálogo remoto.")
                require(!it.stockKnown || it.quantity <= it.stockAvailable) { "${it.name} supera la existencia disponible." }
            }

            val now = Instant.now()
            val saleId = UUID.randomUUID().toString()
            val folio = createFolio(saleId, now)
            val history = listOf(
                SaleStatusChange(UUID.randomUUID().toString(), null, SaleStatus.DRAFT, session.userId, now, "Borrador creado en el dispositivo."),
                SaleStatusChange(UUID.randomUUID().toString(), SaleStatus.DRAFT, SaleStatus.SENT_TO_CASHIER, session.userId, now, "Orden pendiente de confirmación remota."),
            )
            val ticket = SaleTicket(
                id = saleId,
                folio = folio,
                items = cart.items,
                customer = cart.customer,
                subtotalCents = cart.subtotalCents,
                discountCents = cart.discountCents,
                totalCents = cart.totalCents,
                status = SaleStatus.SENT_TO_CASHIER,
                createdBy = session.userId,
                branchId = branch.id,
                createdAt = now,
                syncState = SaleSyncState.PENDING,
                syncAttemptCount = 0,
                syncLastError = null,
                syncLastAttemptAt = null,
                history = history,
            )
            dao.persistSentSale(
                sale = ticket.toEntity(),
                items = ticket.items.map { it.toSaleItemEntity(saleId) },
                history = history.map { it.toEntity(saleId) },
                cartId = cart.id,
            )
            ticket
        }
    }

    private fun createFolio(id: String, now: Instant): String {
        val date = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneId.systemDefault()).format(now)
        return "VD-$date-${id.take(6).uppercase()}"
    }
}

internal fun nextCartItem(existing: CartItem?, product: Product): CartItem {
    require(existing == null || existing.productId == product.id) { "El producto no coincide con la fila del carrito." }
    val quantity = (existing?.quantity ?: 0) + 1
    if (product.stockKnown) {
        require(quantity <= product.stockAvailable) { "Solo hay ${product.stockAvailable} ${product.unit}(s) disponibles." }
    }
    return CartItem(
        productId = product.id,
        internalCode = product.internalCode,
        name = product.commonName,
        imageKey = product.imageKey,
        unit = product.unit,
        listPriceCents = product.priceCents,
        unitPriceCents = product.effectivePriceCents,
        quantity = quantity,
        stockAvailable = product.stockAvailable,
        stockKnown = product.stockKnown,
        promotionName = product.promotion?.name,
    )
}

private fun requireCanonicalUuid(value: String, message: String) {
    val parsed = runCatching { UUID.fromString(value) }.getOrNull()
    require(parsed != null && parsed.toString().equals(value, ignoreCase = true)) { message }
}

private fun CartWithItems.toDomain(): Cart = Cart(
    id = header.id,
    items = items.map(CartItemEntity::toDomain).sortedBy(CartItem::name),
    customer = header.customerId?.let { id ->
        CartCustomer(id, header.customerName.orEmpty())
    },
    updatedAt = Instant.ofEpochMilli(header.updatedAtEpochMs),
)

private fun CartItemEntity.toDomain() = CartItem(
    productId, internalCode, name, imageKey, unit, listPriceCents, unitPriceCents,
    quantity, stockAvailable, stockKnown, promotionName,
)

private fun Cart.toHeaderEntity() = CartHeaderEntity(
    id, customer?.id, customer?.name, null, updatedAt.toEpochMilli(),
)

private fun CartItem.toEntity(cartId: String) = CartItemEntity(
    cartId, productId, internalCode, name, imageKey, unit, listPriceCents, unitPriceCents,
    quantity, stockAvailable, stockKnown, promotionName,
)

private fun SaleTicket.toEntity() = SaleEntity(
    id, folio, customer?.id, customer?.name, null, subtotalCents,
    discountCents, totalCents, status.name, createdBy, branchId, createdAt.toEpochMilli(), syncPending,
    syncState.name, syncAttemptCount, syncLastError, syncLastAttemptAt?.toEpochMilli(),
)

private fun CartItem.toSaleItemEntity(saleId: String) = SaleItemEntity(
    saleId, productId, internalCode, name, imageKey, unit, listPriceCents, unitPriceCents,
    quantity, stockAvailable, stockKnown, promotionName,
)

private fun SaleStatusChange.toEntity(saleId: String) = SaleStatusHistoryEntity(
    id, saleId, previousStatus?.name, newStatus.name, userId, changedAt.toEpochMilli(), note,
)
