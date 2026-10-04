package com.intutec.viveroapp.feature.cashier

import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.activeBackend
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.cashier.data.local.*
import com.intutec.viveroapp.feature.cashier.data.repository.BackendCashierPayments
import com.intutec.viveroapp.feature.cashier.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackendCashierPaymentsTest {
    private class Dao : BackendPaymentAttemptDao() {
        val rows = mutableMapOf<Long, BackendPaymentAttemptEntity>(); var writes = 0
        override suspend fun insert(row: BackendPaymentAttemptEntity): Long { val id = (++writes).toLong(); rows[id] = row.copy(id = id); return id }
        override suspend fun load(id: Long) = rows[id]
        override suspend fun pending(actor: Long, branch: Long) = rows.values.filter { it.actorId == actor && it.branchId == branch && it.state in setOf("PENDING", "SYNCING", "UNCERTAIN") }
        override suspend fun claim(id: Long): Int { val row = rows.getValue(id); if (row.state !in setOf("PENDING", "UNCERTAIN")) return 0; rows[id] = row.copy(state = "SYNCING"); return 1 }
        override suspend fun uncertain(id: Long): Int { val row = rows.getValue(id); if (row.state != "SYNCING") return 0; rows[id] = row.copy(state = "UNCERTAIN"); return 1 }
        override suspend fun complete(id: Long, receipt: String): Int { val row = rows.getValue(id); if (row.state != "SYNCING") return 0; rows[id] = row.copy(state = "SUCCEEDED", receipt = receipt); return 1 }
        override suspend fun retire(id: Long): Int { val row = rows.getValue(id); if (row.state != "SYNCING") return 0; rows[id] = row.copy(state = "RETIRED"); return 1 }
    }
    private class Remote(val dao: Dao) : BackendCashierGateway {
        var payments = 0; var recoveries = 0; var claims = 0; var onClaim: (() -> Unit)? = null
        var error: Exception? = null; var recoverError: Exception? = null; var gate: CompletableDeferred<Unit>? = null
        var submittedKey: String? = null; var recoveredKey: String? = null; var submittedBody: String? = null; var recoveredBody: String? = null
        var retirement: BackendPaymentRetirement = BackendPaymentRetirement.Retired; var retireError: Exception? = null; var retirements = 0
        override suspend fun retire(token: String, identity: BackendSaleIdentity, sale: Long, key: String, body: String): BackendPaymentRetirement {
            retirements++; assertEquals(submittedKey, key); assertEquals(submittedBody, body); retireError?.let { throw it }; return retirement
        }
        override suspend fun list(token: String, before: Long?) = BackendCashierPage(emptyList(), null)
        override suspend fun claim(token: String, identity: BackendSaleIdentity, sale: Long): BackendCashierClaim { claims++; onClaim?.invoke(); return BackendCashierClaim(sale, identity, "a".repeat(64)) }
        override suspend fun pay(token: String, identity: BackendSaleIdentity, sale: Long, key: String, body: String): BackendPaymentReceipt {
            assertTrue(dao.rows.values.any { it.key == key && it.body == body }); payments++; submittedKey = key; submittedBody = body; gate?.await(); error?.let { throw it }
            return BackendPaymentReceipt(6, sale, "VD-DEMO", 500, 600, 100)
        }
        override suspend fun recover(token: String, identity: BackendSaleIdentity, sale: Long, key: String, body: String): BackendPaymentReceipt {
            recoveries++; recoveredKey = key; recoveredBody = body; recoverError?.let { throw it }; return BackendPaymentReceipt(6, sale, "VD-DEMO", 500, 600, 100)
        }
    }
    @Test fun paymentIsPersistedBeforePostAndReceiptSurvivesCompletion() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao)
        val service = BackendCashierPayments(sessions, dao, remote)
        assertEquals(100L, service.start(5, "CASH", 600, null).changeCents)
        assertEquals("SUCCEEDED", dao.rows.getValue(1).state); assertNotNull(dao.rows.getValue(1).receipt); assertTrue(service.pending().isEmpty())
        assertEquals(1, remote.payments); assertEquals(1, dao.writes)
    }
    @Test fun lostResponseBlocksAnotherPaymentAndRecoversOriginalKeyAfterRestart() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao); remote.error = IllegalStateException("lost reply")
        val service = BackendCashierPayments(sessions, dao, remote)
        try { service.start(5, "CASH", 600, null); fail("Lost response") } catch (_: IllegalStateException) { }
        assertEquals("UNCERTAIN", dao.rows.getValue(1).state)
        try { service.start(7, "CASH", 600, null); fail("Started another payment") } catch (_: IllegalStateException) { }
        assertEquals(1, remote.claims); assertEquals(1, remote.payments)
        assertEquals(6L, BackendCashierPayments(sessions, dao, remote).recover(1).id)
        assertEquals(remote.submittedKey, remote.recoveredKey); assertEquals(remote.submittedBody, remote.recoveredBody); assertEquals(1, remote.payments)
    }
    @Test fun missingRecoveryDoesNotDeleteAttemptOrResubmit() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao); remote.error = IllegalStateException()
        val service = BackendCashierPayments(sessions, dao, remote)
        try { service.start(5, "CASH", 600, null) } catch (_: IllegalStateException) { }
        val original = dao.rows.getValue(1); remote.recoverError = IllegalStateException("404")
        try { service.recover(1); fail("Missing payment accepted") } catch (_: IllegalStateException) { }
        assertEquals(original.key, dao.rows.getValue(1).key); assertEquals("UNCERTAIN", dao.rows.getValue(1).state); assertEquals(1, remote.payments)
    }
    @Test fun explicitRetryOnlyReplaysTheOriginalBodyAndKeyAfterExactMissingResult() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao)
        remote.error = IllegalStateException("lost response")
        val service = BackendCashierPayments(sessions, dao, remote)
        try { service.start(5, "CASH", 600, null) } catch (_: IllegalStateException) { }
        val original = dao.rows.getValue(1)
        remote.error = null; remote.recoverError = BackendPaymentNotFound()
        assertEquals(6L, service.retry(1).id)
        assertEquals(original.key, remote.submittedKey); assertEquals(original.body, remote.submittedBody)
        assertEquals(2, remote.payments); assertEquals(1, remote.claims); assertEquals(1, dao.writes)
    }
    @Test fun retryObservesCommittedPaymentAndNeverPostsOnAmbiguousRecoveryFailure() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao)
        remote.error = IllegalStateException("lost response")
        val service = BackendCashierPayments(sessions, dao, remote)
        try { service.start(5, "CASH", 600, null) } catch (_: IllegalStateException) { }
        remote.recoverError = IllegalStateException("network failure")
        try { service.retry(1); fail("Ambiguous recovery accepted") } catch (_: IllegalStateException) { }
        assertEquals(1, remote.payments); assertEquals("UNCERTAIN", dao.rows.getValue(1).state)
        remote.recoverError = null
        assertEquals(6L, service.retry(1).id); assertEquals(1, remote.payments)
    }
    @Test fun logoutDuringClaimDoesNotPersistOrPay() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao); remote.onClaim = { sessions.clearBackend() }
        try { BackendCashierPayments(sessions, dao, remote).start(5, "CASH", 600, null); fail("Paid after logout") } catch (_: IllegalStateException) { }
        assertEquals(0, dao.writes); assertEquals(0, remote.payments)
    }
    @Test fun otherAccountCannotRecoverStoredPayment() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao); remote.error = IllegalStateException()
        val service = BackendCashierPayments(sessions, dao, remote)
        try { service.start(5, "CASH", 600, null) } catch (_: IllegalStateException) { }
        activeBackend(sessions, actor = 9)
        try { service.recover(1); fail("Recovered another account") } catch (_: IllegalStateException) { }
        assertEquals(0, remote.recoveries); assertEquals("UNCERTAIN", dao.rows.getValue(1).state); assertTrue(service.pending().isEmpty())
    }
    @Test fun cancellationAfterSavingKeepsAttemptAndDoesNotSwallowCancellation() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao); remote.error = CancellationException()
        try { BackendCashierPayments(sessions, dao, remote).start(5, "CASH", 600, null); fail("Swallowed cancellation") } catch (_: CancellationException) { }
        assertEquals("UNCERTAIN", dao.rows.getValue(1).state); assertEquals(1, dao.writes)
    }
    @Test fun duplicateOperationCannotClaimOrPostAgain() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao); val gate = CompletableDeferred<Unit>(); remote.gate = gate
        val service = BackendCashierPayments(sessions, dao, remote)
        val first = async { service.start(5, "CASH", 600, null) }; testScheduler.runCurrent()
        try { service.start(5, "CASH", 600, null); fail("Concurrent payment accepted") } catch (_: IllegalStateException) { }
        gate.complete(Unit); first.await(); assertEquals(1, remote.payments); assertEquals(1, remote.claims)
    }
    @Test fun cardSecretsAndInvalidAmountsNeverReachClaimOrStorage() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao); val service = BackendCashierPayments(sessions, dao, remote)
        for (reference in listOf("123", "1234", "4111 1111 1111 1111")) {
            try { service.start(5, "CARD", null, reference); fail("Stored card secret") } catch (_: IllegalArgumentException) { }
        }
        try { service.start(5, "CASH", Long.MAX_VALUE, null); fail("Unsafe money") } catch (_: IllegalArgumentException) { }
        assertEquals(0, dao.writes); assertEquals(0, remote.claims)
    }
    @Test fun serverRetirementPreservesAttemptAndUnlocksAnotherPayment() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao); remote.error = IllegalStateException()
        val service = BackendCashierPayments(sessions, dao, remote)
        try { service.start(5, "CASH", 600, null) } catch (_: IllegalStateException) { }
        val original = dao.rows.getValue(1)
        assertEquals(BackendPaymentRetirement.Retired, service.retire(1))
        assertEquals(original.key, dao.rows.getValue(1).key); assertEquals(original.body, dao.rows.getValue(1).body)
        assertEquals("RETIRED", dao.rows.getValue(1).state); assertTrue(service.pending().isEmpty())
        remote.error = null; service.start(7, "CASH", 600, null); assertEquals(2, dao.rows.size)
    }
    @Test fun committedRetirementPreservesPaymentReceiptInsteadOfClosingLocally() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao); remote.error = IllegalStateException()
        val service = BackendCashierPayments(sessions, dao, remote)
        try { service.start(5, "CASH", 600, null) } catch (_: IllegalStateException) { }
        remote.retirement = BackendPaymentRetirement.Committed(BackendPaymentReceipt(6, 5, "VD-DEMO", 500, 600, 100))
        assertTrue(service.retire(1) is BackendPaymentRetirement.Committed)
        assertEquals("SUCCEEDED", dao.rows.getValue(1).state); assertNotNull(dao.rows.getValue(1).receipt)
        assertEquals(1, remote.payments)
    }
    @Test fun lostRetirementResponseStaysPendingAndCanRepeatOriginalKeyAfterRestart() = runTest {
        val sessions = SessionStore(); activeBackend(sessions); val dao = Dao(); val remote = Remote(dao); remote.error = IllegalStateException()
        val service = BackendCashierPayments(sessions, dao, remote)
        try { service.start(5, "CASH", 600, null) } catch (_: IllegalStateException) { }
        remote.retireError = IllegalStateException("lost response")
        try { service.retire(1); fail("Closed locally") } catch (_: IllegalStateException) { }
        assertEquals("UNCERTAIN", dao.rows.getValue(1).state)
        activeBackend(sessions, actor = 9)
        try { service.retire(1); fail("Closed another account") } catch (_: IllegalStateException) { }
        assertEquals(1, remote.retirements)
        activeBackend(sessions); remote.retireError = null
        assertEquals(BackendPaymentRetirement.Retired, BackendCashierPayments(sessions, dao, remote).retire(1))
        assertEquals(1, remote.payments); assertEquals(1, dao.writes)
    }
}
