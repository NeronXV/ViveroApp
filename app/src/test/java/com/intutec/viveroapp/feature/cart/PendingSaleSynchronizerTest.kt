package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.RolePermissions
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.cart.domain.model.CartItem
import com.intutec.viveroapp.feature.cart.domain.model.SaleStatus
import com.intutec.viveroapp.feature.cart.domain.model.SaleSyncState
import com.intutec.viveroapp.feature.cart.domain.model.SaleTicket
import com.intutec.viveroapp.feature.cart.sync.PendingSaleSynchronizer
import com.intutec.viveroapp.feature.cart.sync.SaleOutboxStore
import com.intutec.viveroapp.feature.cart.sync.SaleSyncFailureType
import com.intutec.viveroapp.feature.cart.sync.SaleSyncItem
import com.intutec.viveroapp.feature.cart.sync.SaleSyncOutcome
import com.intutec.viveroapp.feature.cart.sync.SaleSyncRemoteDataSource
import com.intutec.viveroapp.feature.cart.sync.SaleSyncRemoteException
import com.intutec.viveroapp.feature.cart.sync.SaleSyncRequest
import com.intutec.viveroapp.feature.cart.sync.SaleSyncResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PendingSaleSynchronizerTest {
    @Test
    fun `pending transitions through syncing to synced`() = runTest {
        val fixture = fixture(
            ticket = ticket().copy(
                syncAttemptCount = 1,
                syncLastError = "Error temporal anterior.",
            ),
        )

        val outcome = fixture.synchronizer.synchronize(SALE_ID)

        assertTrue(outcome is SaleSyncOutcome.Synced)
        assertEquals(SaleSyncState.SYNCED, fixture.store.ticket.syncState)
        assertEquals(2, fixture.store.ticket.syncAttemptCount)
        assertEquals(false, fixture.store.ticket.syncPending)
        assertEquals(null, fixture.store.ticket.syncLastError)
        assertEquals(1, fixture.remote.requests.size)
    }

    @Test
    fun `temporary network error returns sale to pending`() = runTest {
        val fixture = fixture(
            remote = RecordingRemote { throw SaleSyncRemoteException(SaleSyncFailureType.TEMPORARY, "Fallo temporal.") },
        )

        val outcome = fixture.synchronizer.synchronize(SALE_ID)

        assertTrue(outcome is SaleSyncOutcome.Pending)
        assertEquals(SaleSyncState.PENDING, fixture.store.ticket.syncState)
        assertEquals("Fallo temporal.", fixture.store.ticket.syncLastError)
    }

    @Test
    fun `functional error marks sale failed`() = runTest {
        val fixture = fixture(
            remote = RecordingRemote { throw SaleSyncRemoteException(SaleSyncFailureType.PERMANENT, "Producto inválido.") },
        )

        val outcome = fixture.synchronizer.synchronize(SALE_ID)

        assertTrue(outcome is SaleSyncOutcome.Failed)
        assertEquals(SaleSyncState.FAILED, fixture.store.ticket.syncState)
    }

    @Test
    fun `inconsistent response fields never mark synced`() = runTest {
        val invalidResponses = listOf(
            response().copy(id = OTHER_ID),
            response().copy(folio = "VD-260815-555555"),
            response().copy(createdBy = OTHER_ID),
            response().copy(branchId = OTHER_ID),
            response().copy(status = "PAYMENT_PENDING"),
        )

        invalidResponses.forEach { invalid ->
            val fixture = fixture(remote = RecordingRemote { invalid })
            val outcome = fixture.synchronizer.synchronize(SALE_ID)
            assertTrue(outcome is SaleSyncOutcome.Failed)
            assertEquals(SaleSyncState.FAILED, fixture.store.ticket.syncState)
        }
    }

    @Test
    fun `different authenticated user does not call remote`() = runTest {
        val fixture = fixture(session = session(userId = OTHER_ID))

        val outcome = fixture.synchronizer.synchronize(SALE_ID)

        assertTrue(outcome is SaleSyncOutcome.Failed)
        assertEquals(0, fixture.remote.requests.size)
    }

    @Test
    fun `retry reuses exact sale uuid folio and payload`() = runTest {
        var call = 0
        val remote = RecordingRemote {
            call += 1
            if (call == 1) throw SaleSyncRemoteException(SaleSyncFailureType.TEMPORARY, "Temporal")
            response()
        }
        val fixture = fixture(remote = remote)

        fixture.synchronizer.synchronize(SALE_ID)
        fixture.synchronizer.synchronize(SALE_ID)

        assertEquals(2, remote.requests.size)
        assertEquals(remote.requests[0], remote.requests[1])
        assertEquals(SALE_ID, remote.requests[1].saleId)
        assertEquals(FOLIO, remote.requests[1].folio)
        assertEquals(SaleSyncState.SYNCED, fixture.store.ticket.syncState)
    }

    @Test
    fun `concurrent attempts claim once and never duplicate remote call`() = runTest {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val remote = RecordingRemote {
            entered.complete(Unit)
            release.await()
            response()
        }
        val fixture = fixture(remote = remote)

        val first = async { fixture.synchronizer.synchronize(SALE_ID) }
        entered.await()
        val second = async { fixture.synchronizer.synchronize(SALE_ID) }
        assertTrue(second.await() is SaleSyncOutcome.AlreadyClaimed)
        release.complete(Unit)
        assertTrue(first.await() is SaleSyncOutcome.Synced)
        assertEquals(1, remote.requests.size)
    }

    @Test
    fun `demo product id is rejected before remote submission`() = runTest {
        val fixture = fixture(ticket = ticket(productId = "monstera"))

        val outcome = fixture.synchronizer.synchronize(SALE_ID)

        assertTrue(outcome is SaleSyncOutcome.Failed)
        assertEquals(0, fixture.remote.requests.size)
    }

    @Test
    fun `closed or unavailable session does not claim or submit`() = runTest {
        val fixture = fixture(session = null)

        val outcome = fixture.synchronizer.synchronize(SALE_ID)

        assertTrue(outcome is SaleSyncOutcome.SessionUnavailable)
        assertEquals(SaleSyncState.PENDING, fixture.store.ticket.syncState)
        assertEquals(0, fixture.remote.requests.size)
    }

    private fun fixture(
        ticket: SaleTicket = ticket(),
        remote: RecordingRemote = RecordingRemote { response() },
        session: UserSession? = session(),
    ): Fixture {
        val store = FakeSaleOutboxStore(ticket)
        val sessionStore = SessionStore().apply { update(session) }
        return Fixture(store, remote, PendingSaleSynchronizer(store, remote, sessionStore))
    }

    private data class Fixture(
        val store: FakeSaleOutboxStore,
        val remote: RecordingRemote,
        val synchronizer: PendingSaleSynchronizer,
    )

    private class RecordingRemote(
        private val result: suspend (SaleSyncRequest) -> SaleSyncResponse,
    ) : SaleSyncRemoteDataSource {
        val requests = mutableListOf<SaleSyncRequest>()
        override suspend fun submitSale(request: SaleSyncRequest): SaleSyncResponse {
            requests += request
            return result(request)
        }
    }

    private class FakeSaleOutboxStore(initial: SaleTicket) : SaleOutboxStore {
        var ticket = initial
        override suspend fun load(saleId: String) = ticket.takeIf { it.id == saleId }
        override suspend fun claimPending(saleId: String, attemptedAt: Instant): Boolean {
            if (ticket.id != saleId || ticket.syncState != SaleSyncState.PENDING) return false
            ticket = ticket.copy(
                syncState = SaleSyncState.SYNCING,
                syncAttemptCount = ticket.syncAttemptCount + 1,
                syncLastAttemptAt = attemptedAt,
                syncLastError = null,
            )
            return true
        }
        override suspend fun markPending(saleId: String, message: String): Boolean {
            if (ticket.syncState != SaleSyncState.SYNCING) return false
            ticket = ticket.copy(syncState = SaleSyncState.PENDING, syncLastError = message)
            return true
        }
        override suspend fun markFailed(saleId: String, message: String): Boolean {
            if (ticket.syncState != SaleSyncState.SYNCING) return false
            ticket = ticket.copy(syncState = SaleSyncState.FAILED, syncLastError = message)
            return true
        }
        override suspend fun markSynced(saleId: String, serverStatus: SaleStatus): Boolean {
            if (ticket.syncState != SaleSyncState.SYNCING) return false
            ticket = ticket.copy(syncState = SaleSyncState.SYNCED, status = serverStatus, syncLastError = null)
            return true
        }
        override suspend fun recoverInterrupted(): Int {
            if (ticket.syncState != SaleSyncState.SYNCING) return 0
            ticket = ticket.copy(syncState = SaleSyncState.PENDING)
            return 1
        }
    }

    companion object {
        private const val SALE_ID = "11111111-1111-4111-8111-111111111111"
        private const val USER_ID = "22222222-2222-4222-8222-222222222222"
        private const val BRANCH_ID = "33333333-3333-4333-8333-333333333333"
        private const val PRODUCT_ID = "44444444-4444-4444-8444-444444444444"
        private const val OTHER_ID = "55555555-5555-4555-8555-555555555555"
        private const val FOLIO = "VD-260815-111111"

        private fun response() = SaleSyncResponse(SALE_ID, FOLIO, USER_ID, BRANCH_ID, "SENT_TO_CASHIER")

        private fun session(userId: String = USER_ID) = UserSession(
            userId = userId,
            email = "",
            fullName = "Owner",
            role = UserRole.OWNER,
            capabilities = RolePermissions.permissionsFor(UserRole.OWNER),
            branch = UserBranch(BRANCH_ID, "CENTRO", "Sucursal Centro", true),
            mode = SessionMode.REMOTE,
        )

        private fun ticket(productId: String = PRODUCT_ID) = SaleTicket(
            id = SALE_ID,
            folio = FOLIO,
            items = listOf(
                CartItem(productId, "P-1", "Producto", "", "pieza", 100, 100, 2, 0, false),
            ),
            customer = null,
            subtotalCents = 200,
            discountCents = 0,
            totalCents = 200,
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
