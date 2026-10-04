package com.intutec.viveroapp.feature.cart.domain.repository

// Only the emitter creates a confirmation handle from a server quote.
class PreparedBackendSale internal constructor(
    val quote: BackendSaleQuote,
    internal val attempt: BackendSaleAttempt,
    internal val cart: BackendCartSnapshot? = null,
) {
    internal var localId: Long? = null
}
sealed interface BackendSaleEmissionOutcome {
    data object Synced : BackendSaleEmissionOutcome
    data object Retired : BackendSaleEmissionOutcome
    data object AlreadyClaimed : BackendSaleEmissionOutcome
    data object SessionUnavailable : BackendSaleEmissionOutcome
    data class Pending(val message: String) : BackendSaleEmissionOutcome
    data class Failed(val message: String) : BackendSaleEmissionOutcome
}
data class BackendSaleEmission(val localId: Long, val outcome: BackendSaleEmissionOutcome)
data class BackendStoredSaleReceipt(val saleId: Long, val folio: String, val status: String)
data class BackendSaleJournalEntry(val localId: Long, val state: String, val totalCents: Long,
    val items: List<BackendSaleLine>, val lastError: String?, val receipt: BackendStoredSaleReceipt?)

interface BackendSaleEmitter {
    suspend fun quote(items: List<BackendSaleLine>): PreparedBackendSale
    suspend fun quoteCart(cart: BackendCartSnapshot): PreparedBackendSale
    suspend fun send(prepared: PreparedBackendSale): BackendSaleEmission
    suspend fun resume(localId: Long): BackendSaleEmission
    suspend fun pending(): List<Long>
    suspend fun journal(): List<BackendSaleJournalEntry>
    suspend fun checkResult(localId: Long): BackendSaleEmission
    suspend fun retire(localId: Long): BackendSaleEmission
}
