package com.intutec.viveroapp.feature.inventory.domain.repository

import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import java.security.SecureRandom

const val MAX_INVENTORY_MILLI = 99999999999999L
fun BackendSessionState.inventorySession(write: Boolean = false): BackendSession? =
    authorizedSession("MANAGE_INVENTORY", true) ?: if (!write) authorizedSession("VIEW_INVENTORY_ALERTS", true) else null
data class BackendInventoryItem(val id: Long, val name: String, val code: String, val unit: String, val quantityMilli: Long, val minimumMilli: Long, val low: Boolean, val updatedAt: String?)
data class BackendInventoryPage(val items: List<BackendInventoryItem>, val next: Long?)
data class BackendInventoryMovement(val id: Long, val productId: Long, val name: String, val code: String, val type: String, val quantityMilli: Long, val notes: String?, val createdAt: String, val actorLabel: String?)
data class BackendInventoryHistory(val items: List<BackendInventoryMovement>, val next: Long?)
enum class BackendInventoryAction(val path: String) { RECEPTION("receptions"), COUNT("counts") }
class BackendInventoryAttempt(val identity: BackendSaleIdentity, val action: BackendInventoryAction, val productId: Long, val quantity: Long, val notes: String?, val key: String) {
    init {
        require(productId in 1..4294967295L && quantity in (if (action == BackendInventoryAction.RECEPTION) 1L else 0L)..99999999999L)
        require(Regex("^[a-f0-9]{64}$").matches(key))
        notes?.let { require(it == it.trim() && it.isNotBlank() && it.codePointCount(0,it.length) <= 240 && it.none { c -> c.code < 32 || c.code in 127..159 } && String(it.toByteArray(Charsets.UTF_8), Charsets.UTF_8) == it) }
        require(action != BackendInventoryAction.COUNT || (notes != null && notes.codePointCount(0, notes.length) >= 3))
    }
    override fun toString() = "BackendInventoryAttempt(productId=$productId, action=$action, key=<redacted>)"
    companion object {
        fun create(identity: BackendSaleIdentity, action: BackendInventoryAction, productId: Long, quantity: Long, notes: String?) = BackendInventoryAttempt(identity,action,productId,quantity,notes?.trim()?.ifBlank { null }, ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) })
    }
}
data class BackendInventoryReceipt(val action: BackendInventoryAction, val id: Long, val productId: Long, val quantityMilli: Long, val totalMilli: Long, val previousMilli: Long? = null, val adjustmentMilli: Long? = null)
data class BackendInventoryPending(val id: Long, val productId: Long, val action: BackendInventoryAction, val quantity: Long, val state: String)
class BackendInventoryResultMissing : IllegalStateException("No se encontró el resultado original.")
interface BackendInventoryGateway {
    suspend fun dashboard(token: String, identity: BackendSaleIdentity, after: Long? = null): BackendInventoryPage
    suspend fun history(token: String, identity: BackendSaleIdentity, product: Long, before: Long? = null): BackendInventoryHistory
    suspend fun submit(token: String, attempt: BackendInventoryAttempt): BackendInventoryReceipt
    suspend fun result(token: String, attempt: BackendInventoryAttempt): BackendInventoryReceipt
}
interface BackendInventoryOperations {
    suspend fun pending(): List<BackendInventoryPending>
    suspend fun start(action: BackendInventoryAction, product: Long, quantity: Long, notes: String?): BackendInventoryReceipt
    suspend fun recover(id: Long, retryMissing: Boolean = false): BackendInventoryReceipt
}
