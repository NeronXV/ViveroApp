package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.cart.sync.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackendPendingSaleSynchronizerTest {
    private val identity = BackendSaleIdentity(2, 3)
    private val attempt = BackendSaleAttempt.restore(identity, "a".repeat(64), listOf(BackendSaleLine(4, 2)), 900)
    private val receipt = BackendSaleReceipt(5, "VD-" + "A".repeat(24), identity, "SENT_TO_CASHIER", 900, "2026-10-01T00:00:00Z", false)
    private class Store(var saved: StoredBackendSaleAttempt) : BackendSaleOutboxStore {
        var onClaim: (() -> Unit)? = null
        var completed: BackendSaleReceipt? = null
        override suspend fun enqueue(attempt: BackendSaleAttempt): Long = error("not used")
        override suspend fun enqueueFromCart(attempt: BackendSaleAttempt, cart: BackendCartSnapshot): Long = error("not used")
        override suspend fun load(id: Long) = saved
        override suspend fun pending(identity: BackendSaleIdentity) = listOf(saved.id)
        override suspend fun claim(id: Long): Boolean {
            if (saved.state !in setOf("PENDING", "UNCERTAIN")) return false
            saved = saved.copy(state = "SYNCING"); onClaim?.invoke(); return true
        }
        override suspend fun uncertain(id: Long, message: String): Boolean { saved = saved.copy(state = "UNCERTAIN"); return true }
        override suspend fun complete(id: Long, receipt: BackendSaleReceipt): Boolean { saved = saved.copy(state = "SYNCED"); completed = receipt; return true }
        override suspend fun retire(id: Long): Boolean { saved = saved.copy(state = "RETIRED"); return true }
    }
    private inner class Remote : BackendSaleGateway {
        var submits = 0; var recovers = 0; var error: Exception? = null; var recoverError: Exception? = null
        var submitted: BackendSaleAttempt? = null; var gate: CompletableDeferred<Unit>? = null
        var retires = 0; var retirement: BackendSaleRetirement = BackendSaleRetirement.Retired
        var retirementError: Exception? = null; var retiredAttempt: BackendSaleAttempt? = null
        override suspend fun retire(token: String, attempt: BackendSaleAttempt): BackendSaleRetirement {
            retires++; retiredAttempt = attempt; gate?.await(); retirementError?.let { throw it }; return retirement
        }
        override suspend fun quote(token: String, identity: BackendSaleIdentity, items: List<BackendSaleLine>): BackendSaleQuote = error("not used")
        override suspend fun submit(token: String, attempt: BackendSaleAttempt): BackendSaleReceipt {
            submits++; submitted = attempt; gate?.await(); error?.let { throw it }; return receipt
        }
        override suspend fun recover(token: String, attempt: BackendSaleAttempt): BackendSaleReceipt { recovers++; recoverError?.let { throw it }; return receipt }
    }
    private fun session(actor: Long = 2, branch: Long = 3, expires: Long = System.currentTimeMillis() + 60000): SessionStore {
        val store = SessionStore()
        store.beginBackendChange(BackendSessionState(BackendSession("t".repeat(43), actor, expires),
            BackendAccessContext(BackendUser(actor, "demo@example.invalid", "Demo"), "ACTIVE", BackendRole(1, UserRole.SALES, "Ventas"), BackendBranch(branch, "DEMO", "Demo", true), setOf("CREATE_SALES")),
            BackendAuthStatus.AUTHENTICATED, BackendAccessStatus.READY))
        return store
    }
    @Test fun originalAttemptIsSubmittedAndReceiptPersisted() = runTest {
        val store = Store(StoredBackendSaleAttempt(1, attempt, "PENDING")); val remote = Remote()
        assertEquals(SaleSyncOutcome.Synced, BackendPendingSaleSynchronizer(store, remote, session()).synchronize(1))
        assertSame(attempt, remote.submitted); assertEquals(receipt, store.completed); assertEquals("SYNCED", store.saved.state)
    }
    @Test fun lostResponseIsRecoveredWithoutSecondSubmit() = runTest {
        val store = Store(StoredBackendSaleAttempt(1, attempt, "PENDING")); val remote = Remote()
        val sync = BackendPendingSaleSynchronizer(store, remote, session())
        remote.error = BackendSaleException(0, "CONNECTION_FAILED", true)
        assertTrue(sync.synchronize(1) is SaleSyncOutcome.Pending); assertEquals("UNCERTAIN", store.saved.state)
        assertEquals(SaleSyncOutcome.Synced, sync.synchronize(1)); assertEquals(1, remote.submits); assertEquals(1, remote.recovers)
        assertEquals("a".repeat(64), store.saved.attempt.key)
    }
    @Test fun recoveryNotFoundResubmitsOriginalPayload() = runTest {
        val store = Store(StoredBackendSaleAttempt(1, attempt, "UNCERTAIN")); val remote = Remote()
        remote.recoverError = BackendSaleException(404, "SALE_NOT_FOUND")
        assertEquals(SaleSyncOutcome.Synced, BackendPendingSaleSynchronizer(store, remote, session()).synchronize(1))
        assertSame(attempt, remote.submitted); assertEquals(1, remote.recovers)
    }
    @Test fun otherRecoveryErrorsDoNotSubmit() = runTest {
        for (error in listOf(BackendSaleException(404, "OTHER"), BackendSaleException(401, "UNAUTHORIZED"), BackendSaleException(503, "UNAVAILABLE"))) {
            val store = Store(StoredBackendSaleAttempt(1, attempt, "UNCERTAIN")); val remote = Remote(); remote.recoverError = error
            assertTrue(BackendPendingSaleSynchronizer(store, remote, session()).synchronize(1) is SaleSyncOutcome.Pending)
            assertEquals(0, remote.submits); assertEquals("UNCERTAIN", store.saved.state)
        }
    }
    @Test fun wrongAccountBranchExpiredOrMissingSessionCannotClaim() = runTest {
        for (sessions in listOf(session(actor = 9), session(branch = 9), session(expires = 1), SessionStore())) {
            val store = Store(StoredBackendSaleAttempt(1, attempt, "PENDING")); val remote = Remote()
            assertEquals(SaleSyncOutcome.SessionUnavailable, BackendPendingSaleSynchronizer(store, remote, sessions).synchronize(1))
            assertEquals("PENDING", store.saved.state); assertEquals(0, remote.submits)
        }
    }
    @Test fun cancellationPreservesAttemptForRecovery() = runTest {
        val store = Store(StoredBackendSaleAttempt(1, attempt, "PENDING")); val remote = Remote(); remote.error = CancellationException()
        try { BackendPendingSaleSynchronizer(store, remote, session()).synchronize(1); fail("Swallowed cancellation") } catch (_: CancellationException) { }
        assertEquals("UNCERTAIN", store.saved.state); assertSame(attempt, store.saved.attempt)
    }
    @Test fun logoutDuringClaimPreventsSendingAndPreservesAttempt() = runTest {
        val sessions = session(); val store = Store(StoredBackendSaleAttempt(1, attempt, "PENDING")); val remote = Remote()
        store.onClaim = { sessions.clearBackend() }
        assertEquals(SaleSyncOutcome.SessionUnavailable, BackendPendingSaleSynchronizer(store, remote, sessions).synchronize(1))
        assertEquals(0, remote.submits); assertEquals("UNCERTAIN", store.saved.state)
    }
    @Test fun concurrentCallDoesNotSubmitTwice() = runTest {
        val store = Store(StoredBackendSaleAttempt(1, attempt, "PENDING")); val remote = Remote(); val gate = CompletableDeferred<Unit>(); remote.gate = gate
        val sync = BackendPendingSaleSynchronizer(store, remote, session())
        val first = async { sync.synchronize(1) }; testScheduler.runCurrent()
        assertEquals(SaleSyncOutcome.AlreadyClaimed, sync.synchronize(1))
        gate.complete(Unit); assertEquals(SaleSyncOutcome.Synced, first.await()); assertEquals(1, remote.submits)
    }
    @Test fun serverRetirementPreservesPayloadAndPreventsAnyResubmit() = runTest {
        val store = Store(StoredBackendSaleAttempt(1, attempt, "UNCERTAIN")); val remote = Remote()
        val sync = BackendPendingSaleSynchronizer(store, remote, session())
        assertEquals(BackendSaleEmissionOutcome.Retired, sync.retire(1))
        assertEquals("RETIRED", store.saved.state); assertSame(attempt, remote.retiredAttempt)
        assertEquals(SaleSyncOutcome.AlreadyClaimed, sync.synchronize(1))
        assertEquals(BackendSaleEmissionOutcome.Retired, sync.retire(1))
        assertEquals(1, remote.retires); assertEquals(0, remote.submits); assertEquals(0, remote.recovers)
    }
    @Test fun retirementReturningCommittedSaleSavesReceiptInsteadOfClosingIt() = runTest {
        val store = Store(StoredBackendSaleAttempt(1, attempt, "UNCERTAIN")); val remote = Remote()
        remote.retirement = BackendSaleRetirement.Committed(receipt)
        assertEquals(BackendSaleEmissionOutcome.Synced, BackendPendingSaleSynchronizer(store, remote, session()).retire(1))
        assertEquals("SYNCED", store.saved.state); assertEquals(receipt, store.completed); assertEquals(0, remote.submits)
    }
    @Test fun failedOrCancelledRetirementNeverUnblocksOrChangesKey() = runTest {
        for (error in listOf(BackendSaleException(404, "SALE_NOT_FOUND"), BackendSaleException(401, "UNAUTHORIZED"), IllegalStateException("sensitive"), CancellationException())) {
            val store = Store(StoredBackendSaleAttempt(1, attempt, "UNCERTAIN")); val remote = Remote(); remote.retirementError = error
            try {
                val result = BackendPendingSaleSynchronizer(store, remote, session()).retire(1)
                assertTrue(result is BackendSaleEmissionOutcome.Pending)
                assertFalse((result as BackendSaleEmissionOutcome.Pending).message.contains("sensitive"))
                assertFalse(error is CancellationException)
            } catch (_: CancellationException) { assertTrue(error is CancellationException) }
            assertEquals("UNCERTAIN", store.saved.state); assertSame(attempt, store.saved.attempt); assertEquals(0, remote.submits)
        }
    }
    @Test fun lostRetirementResponseCanCloseSameAttemptAfterRestart() = runTest {
        val store = Store(StoredBackendSaleAttempt(1, attempt, "UNCERTAIN")); val remote = Remote()
        remote.retirementError = BackendSaleException(0, "CONNECTION_FAILED", true)
        assertTrue(BackendPendingSaleSynchronizer(store, remote, session()).retire(1) is BackendSaleEmissionOutcome.Pending)
        remote.retirementError = null
        assertEquals(BackendSaleEmissionOutcome.Retired, BackendPendingSaleSynchronizer(store, remote, session()).retire(1))
        assertEquals(2, remote.retires); assertSame(attempt, remote.retiredAttempt); assertEquals(0, remote.submits)
    }
    @Test fun retirementCannotClaimOtherAccountBranchOrExpiredSession() = runTest {
        for (sessions in listOf(session(actor = 9), session(branch = 9), session(expires = 1), SessionStore())) {
            val store = Store(StoredBackendSaleAttempt(1, attempt, "UNCERTAIN")); val remote = Remote()
            assertEquals(BackendSaleEmissionOutcome.SessionUnavailable, BackendPendingSaleSynchronizer(store, remote, sessions).retire(1))
            assertEquals("UNCERTAIN", store.saved.state); assertEquals(0, remote.retires)
        }
    }
    @Test fun logoutDuringClaimPreventsRetirement() = runTest {
        val sessions = session(); val store = Store(StoredBackendSaleAttempt(1, attempt, "UNCERTAIN")); val remote = Remote()
        store.onClaim = { sessions.clearBackend() }
        assertEquals(BackendSaleEmissionOutcome.SessionUnavailable, BackendPendingSaleSynchronizer(store, remote, sessions).retire(1))
        assertEquals("UNCERTAIN", store.saved.state); assertEquals(0, remote.retires)
    }
    @Test fun concurrentRetryCannotSendWhileRetirementIsClaimed() = runTest {
        val store = Store(StoredBackendSaleAttempt(1, attempt, "UNCERTAIN")); val remote = Remote(); val gate = CompletableDeferred<Unit>(); remote.gate = gate
        val sync = BackendPendingSaleSynchronizer(store, remote, session())
        val first = async { sync.retire(1) }; testScheduler.runCurrent()
        assertEquals(SaleSyncOutcome.AlreadyClaimed, sync.synchronize(1))
        assertEquals(BackendSaleEmissionOutcome.AlreadyClaimed, sync.retire(1))
        gate.complete(Unit); assertEquals(BackendSaleEmissionOutcome.Retired, first.await()); assertEquals(1, remote.retires); assertEquals(0, remote.submits)
    }
    @Test fun wrongCommittedReceiptDoesNotReleaseAttempt() = runTest {
        val store = Store(StoredBackendSaleAttempt(1, attempt, "UNCERTAIN")); val remote = Remote()
        remote.retirement = BackendSaleRetirement.Committed(receipt.copy(totalCents = 901))
        assertTrue(BackendPendingSaleSynchronizer(store, remote, session()).retire(1) is BackendSaleEmissionOutcome.Pending)
        assertEquals("UNCERTAIN", store.saved.state); assertNull(store.completed)
    }
}
