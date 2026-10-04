package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.cart.data.repository.BackendSaleEmitterRepository
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.cart.sync.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackendSaleEmitterTest {
    private val identity = BackendSaleIdentity(2, 3)
    private val lines = listOf(BackendSaleLine(4, 2))
    private class Store : BackendSaleOutboxStore {
        val rows = mutableMapOf<Long, StoredBackendSaleAttempt>(); var writes = 0; var error: Exception? = null
        var afterEnqueue: (() -> Unit)? = null; var corruptRead = false
        override suspend fun journal(identity: BackendSaleIdentity) = rows.values.filter { it.attempt.identity == identity }.map {
            BackendSaleJournalEntry(it.id, it.state, it.attempt.expectedTotalCents, it.attempt.items, null, null)
        }
        override suspend fun enqueue(attempt: BackendSaleAttempt): Long {
            error?.let { throw it }; writes++; val id = writes.toLong()
            rows[id] = StoredBackendSaleAttempt(id, attempt, "PENDING"); afterEnqueue?.invoke(); return id
        }
        var cartRevision = 1L; var consumed = false
        override suspend fun enqueueFromCart(attempt: BackendSaleAttempt, cart: BackendCartSnapshot): Long {
            check(cartRevision == cart.revision && !consumed)
            return enqueue(attempt).also { consumed = true }
        }
        override suspend fun load(id: Long): StoredBackendSaleAttempt? = if (corruptRead) null else rows[id]
        override suspend fun pending(identity: BackendSaleIdentity) = rows.values.filter { it.attempt.identity == identity && it.state in setOf("PENDING", "UNCERTAIN", "SYNCING") }.map { it.id }
        override suspend fun claim(id: Long): Boolean {
            val row = rows.getValue(id); if (row.state !in setOf("PENDING", "UNCERTAIN")) return false
            rows[id] = row.copy(state = "SYNCING"); return true
        }
        override suspend fun uncertain(id: Long, message: String): Boolean { rows[id] = rows.getValue(id).copy(state = "UNCERTAIN"); return true }
        override suspend fun complete(id: Long, receipt: BackendSaleReceipt): Boolean { rows[id] = rows.getValue(id).copy(state = "SYNCED"); return true }
        override suspend fun retire(id: Long): Boolean { rows[id] = rows.getValue(id).copy(state = "RETIRED"); return true }
    }
    private inner class Remote(private val store: Store) : BackendSaleGateway {
        var submits = 0; var recovers = 0; var quotes = 0; var error: Exception? = null
        var recoverError: Exception? = null
        var retires = 0
        override suspend fun retire(token: String, attempt: BackendSaleAttempt): BackendSaleRetirement { retires++; return BackendSaleRetirement.Retired }
        var onQuote: (() -> Unit)? = null; var gate: CompletableDeferred<Unit>? = null
        override suspend fun quote(token: String, identity: BackendSaleIdentity, items: List<BackendSaleLine>): BackendSaleQuote {
            quotes++; gate?.await(); onQuote?.invoke()
            return BackendSaleQuote(3, 1000, 100, 900, items.map { BackendSaleQuotedLine(it.productId, it.quantity, "Demo", "DEMO", 500, 450, 900) })
        }
        override suspend fun submit(token: String, attempt: BackendSaleAttempt): BackendSaleReceipt {
            assertTrue(store.rows.values.any { it.attempt.key == attempt.key }); submits++; error?.let { throw it }; return receipt()
        }
        override suspend fun recover(token: String, attempt: BackendSaleAttempt): BackendSaleReceipt { recovers++; recoverError?.let { throw it }; return receipt() }
        private fun receipt() = BackendSaleReceipt(5, "VD-" + "A".repeat(24), identity, "SENT_TO_CASHIER", 900, "2026-10-01T00:00:00Z", false)
    }
    private fun sessions(actor: Long = 2): SessionStore = SessionStore().also { store ->
        store.beginBackendChange(BackendSessionState(BackendSession("t".repeat(43), actor, System.currentTimeMillis() + 60000),
            BackendAccessContext(BackendUser(actor, "demo@example.invalid", "Demo"), "ACTIVE", BackendRole(1, UserRole.SALES, "Ventas"), BackendBranch(3, "DEMO", "Demo", true), setOf("CREATE_SALES")),
            BackendAuthStatus.AUTHENTICATED, BackendAccessStatus.READY))
    }
    private fun emitter(store: Store, remote: Remote, sessions: SessionStore) = BackendSaleEmitterRepository(remote, store, BackendPendingSaleSynchronizer(store, remote, sessions), sessions)
    @Test fun confirmsServerQuoteAndPersistsBeforeSubmitWithoutDoubleEnqueue() = runTest {
        val store = Store(); val remote = Remote(store); val emitter = emitter(store, remote, sessions())
        val prepared = emitter.quote(lines); assertEquals(900L, prepared.quote.totalCents); assertEquals(0, store.writes)
        assertEquals(BackendSaleEmissionOutcome.Synced, emitter.send(prepared).outcome)
        assertEquals(BackendSaleEmissionOutcome.AlreadyClaimed, emitter.send(prepared).outcome)
        assertEquals(1, store.writes); assertEquals(1, remote.submits)
    }
    @Test fun failedPersistenceOrReadbackNeverSends() = runTest {
        val store = Store(); val remote = Remote(store); val emitter = emitter(store, remote, sessions()); val prepared = emitter.quote(lines)
        store.error = IllegalStateException("disk full")
        try { emitter.send(prepared); fail("Ignored disk error") } catch (_: IllegalStateException) { }
        assertEquals(0, remote.submits)
        store.error = null; store.corruptRead = true
        try { emitter.send(prepared); fail("Ignored missing readback") } catch (_: IllegalStateException) { }
        assertEquals(0, remote.submits); assertEquals(1, store.writes)
    }
    @Test fun lostResponseBlocksNewSaleAndRestoresSameAttemptAfterRestart() = runTest {
        val store = Store(); val remote = Remote(store); val session = sessions(); val emitter = emitter(store, remote, session)
        val prepared = emitter.quote(lines); remote.error = BackendSaleException(0, "CONNECTION_FAILED", true)
        val result = emitter.send(prepared); assertTrue(result.outcome is BackendSaleEmissionOutcome.Pending)
        try { emitter.quote(lines); fail("Ignored unresolved attempt") } catch (_: IllegalStateException) { }
        val restored = emitter(store, remote, session)
        assertEquals(listOf(result.localId), restored.pending())
        assertEquals(BackendSaleEmissionOutcome.Synced, restored.resume(result.localId).outcome)
        assertEquals(1, remote.submits); assertEquals(1, remote.recovers); assertEquals(1, store.writes)
    }
    @Test fun sessionChangeDuringQuoteRejectsResult() = runTest {
        val store = Store(); val remote = Remote(store); val session = sessions(); remote.onQuote = { session.clearBackend() }
        try { emitter(store, remote, session).quote(lines); fail("Accepted stale session") } catch (_: IllegalStateException) { }
        assertEquals(0, store.writes)
    }
    @Test fun logoutAfterPersistKeepsAttemptAndDoesNotSend() = runTest {
        val store = Store(); val remote = Remote(store); val session = sessions(); val emitter = emitter(store, remote, session)
        val prepared = emitter.quote(lines); store.afterEnqueue = { session.clearBackend() }
        try { emitter.send(prepared); fail("Sent after logout") } catch (_: IllegalStateException) { }
        assertEquals(1, store.writes); assertEquals(0, remote.submits)
        assertEquals(listOf(1L), emitter(store, remote, sessions()).pending())
    }
    @Test fun wrongAccountCannotRestoreOrSend() = runTest {
        val store = Store(); val remote = Remote(store); val original = emitter(store, remote, sessions()); val prepared = original.quote(lines)
        remote.error = BackendSaleException(0, "CONNECTION_FAILED", true); original.send(prepared)
        val other = emitter(store, remote, sessions(9))
        assertTrue(other.pending().isEmpty())
        try { other.send(prepared); fail("Sent another account's quote") } catch (_: IllegalStateException) { }
        try { other.resume(1); fail("Restored another account's attempt") } catch (_: IllegalStateException) { }
        assertEquals(1, remote.submits)
    }
    @Test fun concurrentQuoteIsRejectedAndCallerListIsCopied() = runTest {
        val store = Store(); val remote = Remote(store); val emitter = emitter(store, remote, sessions())
        val gate = CompletableDeferred<Unit>(); remote.gate = gate; val mutable = lines.toMutableList()
        val first = async { emitter.quote(mutable) }; testScheduler.runCurrent(); mutable.clear()
        try { emitter.quote(lines); fail("Accepted concurrent operation") } catch (_: IllegalStateException) { }
        gate.complete(Unit); val prepared = first.await(); assertEquals(lines, prepared.attempt.items)
    }
    @Test fun cancelledSendKeepsPersistedAttemptForRecovery() = runTest {
        val store = Store(); val remote = Remote(store); val emitter = emitter(store, remote, sessions())
        val prepared = emitter.quote(lines); remote.error = CancellationException()
        try { emitter.send(prepared); fail("Swallowed cancellation") } catch (_: CancellationException) { }
        assertEquals(listOf(1L), emitter.pending()); assertEquals("UNCERTAIN", store.rows.getValue(1L).state)
    }
    @Test fun checkingMissingResultDoesNotResubmitOrRemoveAttempt() = runTest {
        val store = Store(); val remote = Remote(store); val session = sessions(); val emitter = emitter(store, remote, session)
        val prepared = emitter.quote(lines); remote.error = BackendSaleException(0, "CONNECTION_FAILED", true)
        emitter.send(prepared); remote.recoverError = BackendSaleException(404, "SALE_NOT_FOUND")
        assertTrue(emitter.checkResult(1).outcome is BackendSaleEmissionOutcome.Pending)
        assertEquals(1, remote.submits); assertEquals("UNCERTAIN", store.rows.getValue(1L).state)
        assertEquals(1, emitter.journal().size)
    }
    @Test fun journalIsScopedAndConfirmedRecoveryDoesNotSend() = runTest {
        val store = Store(); val remote = Remote(store); val emitter = emitter(store, remote, sessions())
        val prepared = emitter.quote(lines); remote.error = BackendSaleException(0, "CONNECTION_FAILED", true); emitter.send(prepared)
        assertEquals(BackendSaleEmissionOutcome.Synced, emitter.checkResult(1).outcome)
        assertEquals("SYNCED", emitter.journal().single().state); assertEquals(1, remote.submits)
        assertTrue(emitter(store, remote, sessions(9)).journal().isEmpty())
    }
    @Test fun retirementUnblocksNewQuoteWithoutDeletingJournalOrSendingOldHandle() = runTest {
        val store = Store(); val remote = Remote(store); val emitter = emitter(store, remote, sessions())
        val original = emitter.quote(lines); remote.error = BackendSaleException(409, "SALE_PRICE_CHANGED"); emitter.send(original)
        assertEquals(BackendSaleEmissionOutcome.Retired, emitter.retire(1).outcome)
        assertTrue(emitter.pending().isEmpty()); assertEquals("RETIRED", emitter.journal().single().state)
        val next = emitter.quote(lines); assertNotEquals(original.attempt.key, next.attempt.key)
        assertEquals(BackendSaleEmissionOutcome.AlreadyClaimed, emitter.send(original).outcome)
        assertEquals(1, remote.submits); assertEquals(1, remote.retires); assertEquals(1, store.writes)
    }
    @Test fun wrongAccountCannotRetireAnotherAccountsAttempt() = runTest {
        val store = Store(); val remote = Remote(store); val emitter = emitter(store, remote, sessions())
        val original = emitter.quote(lines); remote.error = BackendSaleException(409, "SALE_PRICE_CHANGED"); emitter.send(original)
        assertEquals(BackendSaleEmissionOutcome.SessionUnavailable, emitter(store, remote, sessions(9)).retire(1).outcome)
        assertEquals("UNCERTAIN", store.rows.getValue(1).state); assertEquals(0, remote.retires)
    }
    @Test fun cartCheckoutIsPersistedOnceAndOldQuoteCannotConsumeEditedDraft() = runTest {
        val store = Store(); val remote = Remote(store); val emitter = emitter(store, remote, sessions())
        val cart = BackendCartSnapshot(1, 1, identity, listOf(BackendCartLine(4, "Demo", "pieza", 450, 2)))
        val stale = emitter.quoteCart(cart); store.cartRevision = 2
        try { emitter.send(stale); fail("Consumed edited draft") } catch (_: IllegalStateException) { }
        assertEquals(0, remote.submits); assertEquals(0, store.writes); assertFalse(store.consumed)
        val current = emitter.quoteCart(cart.copy(revision = 2))
        assertEquals(BackendSaleEmissionOutcome.Synced, emitter.send(current).outcome)
        assertTrue(store.consumed); assertEquals(1, store.writes)
        assertEquals(BackendSaleEmissionOutcome.AlreadyClaimed, emitter.send(current).outcome)
        assertEquals(1, remote.submits)
    }
}
