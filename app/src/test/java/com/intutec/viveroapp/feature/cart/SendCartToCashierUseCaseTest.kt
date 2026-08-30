package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.RolePermissions
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartCustomer
import com.intutec.viveroapp.feature.cart.domain.model.CartItem
import com.intutec.viveroapp.feature.cart.domain.model.SaleStatus
import com.intutec.viveroapp.feature.cart.domain.model.SaleSyncState
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.domain.repository.CartRepository
import com.intutec.viveroapp.feature.cart.domain.usecase.SaleSubmissionException
import com.intutec.viveroapp.feature.cart.domain.usecase.SendCartToCashierUseCase
import com.intutec.viveroapp.feature.cart.sync.PendingSaleSynchronizer
import com.intutec.viveroapp.feature.cart.sync.SaleOutboxStore
import com.intutec.viveroapp.feature.cart.sync.SaleSyncRemoteDataSource
import com.intutec.viveroapp.feature.cart.sync.SaleSyncFailureType
import com.intutec.viveroapp.feature.cart.sync.SaleSyncRequest
import com.intutec.viveroapp.feature.cart.sync.SaleSyncResponse
import com.intutec.viveroapp.feature.cart.sync.SaleSyncRemoteException
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendCartToCashierUseCaseTest {
    @Test
    fun `successful send returns synchronized ticket with folio`() = runTest {
        val fixture = fixture { response() }

        val result = fixture.useCase().getOrThrow()

        assertEquals(FOLIO, result.folio)
        assertEquals(SaleSyncState.SYNCED, result.syncState)
        assertEquals(FOLIO, fixture.remoteRequest?.folio)
    }

    @Test
    fun `send preserves coroutine cancellation and leaves outbox retryable`() = runTest {
        val fixture = fixture { throw CancellationException("cancelled") }
        var caught: CancellationException? = null

        try {
            fixture.useCase()
        } catch (error: CancellationException) {
            caught = error
        }

        assertEquals("cancelled", caught?.message)
        assertEquals(SaleSyncState.PENDING, fixture.store.ticket.syncState)
    }

    @Test
    fun `temporary rejection is not reported as a successful send`() = runTest {
        val fixture = fixture {
            throw SaleSyncRemoteException(SaleSyncFailureType.TEMPORARY, "Servidor temporalmente no disponible.")
        }

        val error = fixture.useCase().exceptionOrNull() as SaleSubmissionException

        assertEquals("Servidor temporalmente no disponible.", error.message)
        assertEquals(SaleSyncState.PENDING, error.ticket.syncState)
        assertEquals(SaleSyncState.PENDING, fixture.store.ticket.syncState)
    }

    @Test
    fun `permanent rejection is not reported as a successful send`() = runTest {
        val fixture = fixture {
            throw SaleSyncRemoteException(SaleSyncFailureType.PERMANENT, "La cuenta no tiene permiso.")
        }

        val error = fixture.useCase().exceptionOrNull() as SaleSubmissionException

        assertEquals("La cuenta no tiene permiso.", error.message)
        assertEquals(SaleSyncState.FAILED, error.ticket.syncState)
        assertEquals(SaleSyncState.FAILED, fixture.store.ticket.syncState)
    }

    @Test
    fun `retry reuses the same sale and succeeds without creating another ticket`() = runTest {
        var attempts = 0
        val fixture = fixture {
            attempts += 1
            if (attempts == 1) {
                throw SaleSyncRemoteException(SaleSyncFailureType.PERMANENT, "Contrato remoto no disponible.")
            }
            response()
        }

        val firstError = fixture.useCase().exceptionOrNull() as SaleSubmissionException
        val retried = fixture.useCase.retry(firstError.ticket.id).getOrThrow()

        assertEquals(2, attempts)
        assertEquals(SALE_ID, retried.id)
        assertEquals(SaleSyncState.SYNCED, retried.syncState)
    }

    @Test
    fun `unavailable remote session keeps the local ticket retryable`() = runTest {
        val unavailableSession = session().copy(branch = null)
        val fixture = fixture(session = unavailableSession) { response() }

        val error = fixture.useCase().exceptionOrNull() as SaleSubmissionException

        assertEquals("La sesión remota o la sucursal ya no están disponibles.", error.message)
        assertEquals(SaleSyncState.PENDING, error.ticket.syncState)
        assertEquals(null, fixture.remoteRequest)
    }

    private fun fixture(
        session: UserSession = session(),
        remoteResult: suspend (SaleSyncRequest) -> SaleSyncResponse,
    ): Fixture {
        val initial = ticket()
        val store = SingleSaleStore(initial)
        val sessionStore = SessionStore().apply { update(session) }
        var capturedRequest: SaleSyncRequest? = null
        val remote = object : SaleSyncRemoteDataSource {
            override suspend fun submitSale(request: SaleSyncRequest): SaleSyncResponse {
                capturedRequest = request
                return remoteResult(request)
            }
        }
        val synchronizer = PendingSaleSynchronizer(store, remote, sessionStore)
        val repository = SingleSaleCartRepository(initial)
        return Fixture(
            useCase = SendCartToCashierUseCase(repository, synchronizer, sessionStore),
            store = store,
            request = { capturedRequest },
        )
    }

    private data class Fixture(
        val useCase: SendCartToCashierUseCase,
        val store: SingleSaleStore,
        val request: () -> SaleSyncRequest?,
    ) {
        val remoteRequest: SaleSyncRequest? get() = request()
    }

    private class SingleSaleCartRepository(private val ticket: SaleTicket) : CartRepository {
        override fun observeCart(): Flow<Cart> = flowOf(Cart())
        override suspend fun addProduct(product: Product) = Result.success(Unit)
        override suspend fun changeQuantity(productId: String, quantity: Int) = Result.success(Unit)
        override suspend fun removeProduct(productId: String) = Result.success(Unit)
        override suspend fun associateCustomer(customer: CartCustomer?) = Result.success(Unit)
        override suspend fun saveDraft() = Result.success(Unit)
        override suspend fun cancelCart() = Result.success(Unit)
        override suspend fun createPendingSale(session: UserSession) = Result.success(ticket)
    }

    private class SingleSaleStore(initial: SaleTicket) : SaleOutboxStore {
        var ticket = initial
        override suspend fun load(saleId: String) = ticket.takeIf { it.id == saleId }
        override suspend fun claimPending(saleId: String, attemptedAt: Instant): Boolean {
            if (ticket.id != saleId || ticket.syncState !in setOf(SaleSyncState.PENDING, SaleSyncState.FAILED)) return false
            ticket = ticket.copy(syncState = SaleSyncState.SYNCING, syncAttemptCount = ticket.syncAttemptCount + 1)
            return true
        }
        override suspend fun markPending(saleId: String, message: String): Boolean {
            ticket = ticket.copy(syncState = SaleSyncState.PENDING, syncLastError = message)
            return true
        }
        override suspend fun markFailed(saleId: String, message: String): Boolean {
            ticket = ticket.copy(syncState = SaleSyncState.FAILED, syncLastError = message)
            return true
        }
        override suspend fun markSynced(saleId: String, serverStatus: SaleStatus): Boolean {
            ticket = ticket.copy(syncState = SaleSyncState.SYNCED, status = serverStatus, syncLastError = null)
            return true
        }
        override suspend fun recoverInterrupted() = 0
    }

    companion object {
        private const val SALE_ID = "11111111-1111-4111-8111-111111111111"
        private const val USER_ID = "22222222-2222-4222-8222-222222222222"
        private const val BRANCH_ID = "33333333-3333-4333-8333-333333333333"
        private const val PRODUCT_ID = "44444444-4444-4444-8444-444444444444"
        private const val FOLIO = "VD-260829-111111"

        private fun response() = SaleSyncResponse(SALE_ID, FOLIO, USER_ID, BRANCH_ID, SaleStatus.SENT_TO_CASHIER.name)

        private fun session() = UserSession(
            userId = USER_ID,
            email = "sales@example.test",
            fullName = "Venta piloto",
            role = UserRole.SALES,
            capabilities = RolePermissions.permissionsFor(UserRole.SALES),
            branch = UserBranch(BRANCH_ID, "CENTRO", "Sucursal Centro", true),
            mode = SessionMode.REMOTE,
        )

        private fun ticket() = SaleTicket(
            id = SALE_ID,
            folio = FOLIO,
            items = listOf(CartItem(PRODUCT_ID, "PL-ALOE-001", "Aloe vera", "", "pieza", 12_500, 12_500, 1, 5)),
            customer = null,
            subtotalCents = 12_500,
            discountCents = 0,
            totalCents = 12_500,
            status = SaleStatus.SENT_TO_CASHIER,
            createdBy = USER_ID,
            branchId = BRANCH_ID,
            createdAt = Instant.EPOCH,
            syncState = SaleSyncState.PENDING,
            syncAttemptCount = 0,
            syncLastError = null,
            syncLastAttemptAt = null,
            history = emptyList(),
        )
    }
}
