package com.intutec.viveroapp.feature.cashier

import com.intutec.viveroapp.feature.cashier.data.local.CashierPaymentAttemptStore
import com.intutec.viveroapp.feature.cashier.data.remote.CashierPaymentRemoteDataSource
import com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierClaimDto
import com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierClaimReleaseDto
import com.intutec.viveroapp.feature.cashier.data.remote.RemoteCashierPaymentResultDto
import com.intutec.viveroapp.feature.cashier.data.remote.RemotePaidSaleDto
import com.intutec.viveroapp.feature.cashier.data.remote.RemotePaymentDto
import com.intutec.viveroapp.feature.cashier.data.repository.SupabaseCashierPaymentRepository
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttempt
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttemptState
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentClaim
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentException
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentFailureCode
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentMethod
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierPaymentRepositoryTest {
    @Test fun `successful exact RPC response marks durable attempt succeeded`() = runTest {
        val order = sampleDetail()
        val store = FakeStore()
        val remote = FakeRemote(order)
        val repository = SupabaseCashierPaymentRepository(remote, store)
        remote.beforeConfirm = {
            assertEquals(CashierPaymentAttemptState.CONFIRMING, store.attempt.state)
            assertTrue(store.attempt.payloadLocked)
            assertEquals(KEY, store.attempt.idempotencyKey)
        }

        val result = repository.confirm(order, CashierRepositoryTest.USER_ID, CashierPaymentInput(CashierPaymentMethod.CASH, order.summary.totalCents), false)

        assertEquals(order.summary.id, result.saleId)
        assertEquals(order.summary.folio, result.folio)
        assertEquals(CashierPaymentAttemptState.SUCCEEDED, store.attempt.state)
        assertEquals(KEY, remote.keys.single())
        assertEquals(listOf(order.summary.id), store.reconciledSales)
    }

    @Test fun `lost response becomes uncertain and retry reuses identical key and payload`() = runTest {
        val order = sampleDetail()
        val store = FakeStore()
        val remote = FakeRemote(order).apply { loseFirstResponse = true }
        val repository = SupabaseCashierPaymentRepository(remote, store)
        val input = CashierPaymentInput(CashierPaymentMethod.TRANSFER, reference = "SPEI-42")

        assertThrows(CashierPaymentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.confirm(order, CashierRepositoryTest.USER_ID, input, false) }
        }
        assertEquals(CashierPaymentAttemptState.UNCERTAIN, store.attempt.state)
        repository.confirm(order, CashierRepositoryTest.USER_ID, null, true)

        assertEquals(listOf(KEY, KEY), remote.keys)
        assertEquals(listOf(input, input), remote.inputs)
        assertEquals(CashierPaymentAttemptState.SUCCEEDED, store.attempt.state)
    }

    @Test fun `mismatched server response never marks paid locally`() = runTest {
        val order = sampleDetail()
        val store = FakeStore()
        val remote = FakeRemote(order).apply { wrongBranch = true }
        val repository = SupabaseCashierPaymentRepository(remote, store)

        assertThrows(CashierPaymentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.confirm(order, CashierRepositoryTest.USER_ID, CashierPaymentInput(CashierPaymentMethod.CARD), false)
            }
        }
        assertEquals(CashierPaymentAttemptState.UNCERTAIN, store.attempt.state)
        assertTrue(store.attempt.payloadLocked)
        assertTrue(store.reconciledSales.isEmpty())
    }

    @Test fun `card response with contradictory received amount stays uncertain`() = runTest {
        val order = sampleDetail()
        val store = FakeStore()
        val remote = FakeRemote(order).apply { amountOverride = order.summary.totalCents + 1 }
        val repository = SupabaseCashierPaymentRepository(remote, store)

        assertThrows(CashierPaymentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.confirm(order, CashierRepositoryTest.USER_ID, CashierPaymentInput(CashierPaymentMethod.CARD), false)
            }
        }
        assertEquals(CashierPaymentAttemptState.UNCERTAIN, store.attempt.state)
        assertTrue(store.reconciledSales.isEmpty())
    }

    @Test fun `local projection failure after canonical success does not degrade succeeded attempt`() = runTest {
        val order = sampleDetail()
        val store = FakeStore().apply { failReconciliation = true }
        val repository = SupabaseCashierPaymentRepository(FakeRemote(order), store)

        val result = repository.confirm(
            order,
            CashierRepositoryTest.USER_ID,
            CashierPaymentInput(CashierPaymentMethod.CASH, order.summary.totalCents),
            false,
        )

        assertEquals(order.summary.id, result.saleId)
        assertEquals(CashierPaymentAttemptState.SUCCEEDED, store.attempt.state)
        assertEquals(listOf(order.summary.id), store.reconciledSales)
    }

    private class FakeRemote(private val order: com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail) : CashierPaymentRemoteDataSource {
        var loseFirstResponse = false
        var wrongBranch = false
        var amountOverride: Long? = null
        var beforeConfirm: (() -> Unit)? = null
        val keys = mutableListOf<String>()
        val inputs = mutableListOf<CashierPaymentInput>()
        override suspend fun claim(saleId: String, claimToken: String?) = error("not used")
        override suspend fun release(saleId: String, claimToken: String): RemoteCashierClaimReleaseDto = error("not used")
        override suspend fun confirm(saleId: String, claimToken: String, idempotencyKey: String, input: CashierPaymentInput): RemoteCashierPaymentResultDto {
            beforeConfirm?.invoke()
            keys += idempotencyKey; inputs += input
            if (loseFirstResponse && keys.size == 1) throw CashierPaymentException(CashierPaymentFailureCode.TEMPORARY, true, "lost")
            val amount = amountOverride ?: input.amountReceivedCents ?: order.summary.totalCents
            return RemoteCashierPaymentResultDto(
                keys.size > 1,
                RemotePaidSaleDto(order.summary.id, order.summary.folio, if (wrongBranch) OTHER_BRANCH else order.summary.branchId, "PAID", order.summary.totalCents),
                RemotePaymentDto(PAYMENT_ID, order.summary.id, CashierRepositoryTest.USER_ID, idempotencyKey, input.method.name,
                    order.summary.totalCents, amount, amount - order.summary.totalCents, input.reference, "2026-08-22T10:01:00.123456+00:00"),
            )
        }
    }

    private class FakeStore : CashierPaymentAttemptStore {
        var attempt = baseAttempt()
        var failReconciliation = false
        val reconciledSales = mutableListOf<String>()
        var historicalRepairs = 0
        override suspend fun get(saleId: String) = attempt
        override suspend fun saveClaim(claim: CashierPaymentClaim) = attempt
        override suspend fun renewClaim(claim: CashierPaymentClaim) = attempt
        override suspend fun lockDraft(saleId: String, input: CashierPaymentInput): CashierPaymentAttempt {
            attempt = attempt.copy(input = input, state = CashierPaymentAttemptState.CONFIRMING, payloadLocked = true); return attempt
        }
        override suspend fun lockUncertainRetry(saleId: String): CashierPaymentAttempt {
            check(attempt.state == CashierPaymentAttemptState.UNCERTAIN)
            attempt = attempt.copy(state = CashierPaymentAttemptState.CONFIRMING); return attempt
        }
        override suspend fun markState(saleId: String, state: CashierPaymentAttemptState, errorCode: String?): CashierPaymentAttempt {
            attempt = attempt.copy(state = state, lastErrorCode = errorCode); return attempt
        }
        override suspend fun reconcileSucceededSale(saleId: String): Int {
            reconciledSales += saleId
            if (failReconciliation) error("local projection unavailable")
            return 1
        }
        override suspend fun reconcileAllSucceededSales(): Int {
            historicalRepairs += 1
            return 0
        }
    }

    companion object {
        private const val CLAIM = "88888888-8888-4888-8888-888888888888"
        private const val KEY = "77777777-7777-4777-8777-777777777777"
        private const val PAYMENT_ID = "99999999-9999-4999-8999-999999999999"
        private const val OTHER_BRANCH = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        private fun baseAttempt() = CashierPaymentAttempt(
            CashierRepositoryTest.SALE_ID, CLAIM, KEY, null, CashierPaymentAttemptState.CLAIMED, false,
            Instant.EPOCH, Instant.EPOCH.plusSeconds(300), Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH,
        )
    }
}
