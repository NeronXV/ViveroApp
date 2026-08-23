package com.intutec.viveroapp.feature.cashier

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttempt
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentAttemptState
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentException
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentFailureCode
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentMethod
import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentResult
import com.intutec.viveroapp.feature.cashier.domain.repository.CashierPaymentRepository
import com.intutec.viveroapp.feature.cashier.domain.usecase.ClaimCashierPaymentUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.ConfirmCashierPaymentUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.ReleaseCashierPaymentClaimUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.RenewCashierPaymentClaimUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.RestoreCashierPaymentAttemptUseCase
import com.intutec.viveroapp.feature.cashier.presentation.CashierPaymentStage
import com.intutec.viveroapp.feature.cashier.presentation.CashierPaymentViewModel
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CashierPaymentViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test fun `successful claim exposes form and renewal keeps idempotency key`() = runTest(dispatcherRule.testDispatcher) {
        val repo = FakePaymentRepository()
        val vm = viewModel(repo)
        vm.attachOrder(sampleDetail()); advanceUntilIdle(); vm.start(); advanceUntilIdle()
        val first = vm.uiState.value.attempt!!
        assertEquals(CashierPaymentStage.FORM, vm.uiState.value.stage)
        vm.renew(); advanceUntilIdle()
        assertEquals(first.idempotencyKey, vm.uiState.value.attempt?.idempotencyKey)
        assertTrue(repo.renewed)
    }

    @Test fun `occupied claim and expired claim use explicit states`() = runTest(dispatcherRule.testDispatcher) {
        val occupied = FakePaymentRepository(claimFailure = paymentError(CashierPaymentFailureCode.CLAIM_UNAVAILABLE))
        val occupiedVm = viewModel(occupied)
        occupiedVm.attachOrder(sampleDetail()); advanceUntilIdle(); occupiedVm.start(); advanceUntilIdle()
        assertEquals(CashierPaymentStage.CONFLICT, occupiedVm.uiState.value.stage)

        val expired = FakePaymentRepository(claimFailure = paymentError(CashierPaymentFailureCode.CLAIM_EXPIRED))
        val expiredVm = viewModel(expired)
        expiredVm.attachOrder(sampleDetail()); advanceUntilIdle(); expiredVm.start(); advanceUntilIdle()
        assertEquals(CashierPaymentStage.EXPIRED, expiredVm.uiState.value.stage)
    }

    @Test fun `cash success validates form and navigable success`() = runTest(dispatcherRule.testDispatcher) {
        val repo = FakePaymentRepository()
        val vm = claimedViewModel(repo)
        vm.selectMethod(CashierPaymentMethod.CASH)
        vm.updateCashAmount("100.00")
        vm.requestConfirmation()
        assertEquals(CashierPaymentStage.CONFIRMATION, vm.uiState.value.stage)
        vm.confirm(); advanceUntilIdle()
        assertEquals(CashierPaymentStage.SUCCESS, vm.uiState.value.stage)
        assertEquals(10_000L, repo.lastInput?.amountReceivedCents)
    }

    @Test fun `insufficient cash and transfer without reference never call repository`() = runTest(dispatcherRule.testDispatcher) {
        val repo = FakePaymentRepository()
        val vm = claimedViewModel(repo)
        vm.selectMethod(CashierPaymentMethod.CASH); vm.updateCashAmount("99.99"); vm.requestConfirmation()
        assertEquals(CashierPaymentStage.FORM, vm.uiState.value.stage)
        vm.selectMethod(CashierPaymentMethod.TRANSFER); vm.requestConfirmation()
        assertEquals(CashierPaymentStage.FORM, vm.uiState.value.stage)
        assertEquals(0, repo.confirmCalls)
    }

    @Test fun `lost response is uncertain and retry reuses locked payload`() = runTest(dispatcherRule.testDispatcher) {
        val repo = FakePaymentRepository(confirmFailure = CashierPaymentException(
            CashierPaymentFailureCode.TEMPORARY, true, "technical network detail",
        ))
        val vm = claimedViewModel(repo)
        vm.selectMethod(CashierPaymentMethod.CARD); vm.updateReference("TERM-A8Z21")
        vm.requestConfirmation(); vm.confirm(); advanceUntilIdle()
        assertEquals(CashierPaymentStage.UNCERTAIN, vm.uiState.value.stage)
        assertFalse(vm.uiState.value.message.orEmpty().contains("technical network detail"))
        repo.confirmFailure = null
        vm.retryUncertain(); advanceUntilIdle()
        assertEquals(CashierPaymentStage.SUCCESS, vm.uiState.value.stage)
        assertEquals(listOf(false, true), repo.retryFlags)
        assertEquals(listOf("TERM-A8Z21", "TERM-A8Z21"), repo.inputs.map { it?.reference ?: repo.attempt.input?.reference })
    }

    @Test fun `restored uncertain attempt stays locked across recreation`() = runTest(dispatcherRule.testDispatcher) {
        val repo = FakePaymentRepository().apply {
            attempt = attempt.copy(
                input = CashierPaymentInput(CashierPaymentMethod.TRANSFER, reference = "SPEI-1"),
                state = CashierPaymentAttemptState.UNCERTAIN,
                payloadLocked = true,
            )
            restored = attempt
        }
        val first = viewModel(repo); first.attachOrder(sampleDetail()); advanceUntilIdle()
        val second = viewModel(repo); second.attachOrder(sampleDetail()); advanceUntilIdle()
        assertEquals(CashierPaymentStage.UNCERTAIN, second.uiState.value.stage)
        assertEquals(first.uiState.value.attempt?.idempotencyKey, second.uiState.value.attempt?.idempotencyKey)
        second.updateReference("CHANGED")
        assertEquals("SPEI-1", second.uiState.value.reference)
    }

    @Test fun `voluntary cancel releases only editable claim`() = runTest(dispatcherRule.testDispatcher) {
        val repo = FakePaymentRepository()
        val vm = claimedViewModel(repo)
        var finished = false
        vm.cancel { finished = true }; advanceUntilIdle()
        assertTrue(repo.released)
        assertTrue(finished)
    }

    @Test fun `server conflict is friendly and countdown derives from server duration`() = runTest(dispatcherRule.testDispatcher) {
        val repo = FakePaymentRepository(confirmFailure = paymentError(CashierPaymentFailureCode.SALE_ALREADY_PAID))
        val vm = claimedViewModel(repo)
        assertEquals(300, vm.secondsRemaining(BASE_TIME))
        vm.selectMethod(CashierPaymentMethod.CARD); vm.requestConfirmation(); vm.confirm(); advanceUntilIdle()
        assertEquals(CashierPaymentStage.CONFLICT, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.message.orEmpty().contains("ya fue cobrada"))
    }

    @Test fun `double confirmation tap launches only one request`() = runTest(dispatcherRule.testDispatcher) {
        val repo = FakePaymentRepository()
        val vm = claimedViewModel(repo)
        vm.selectMethod(CashierPaymentMethod.CARD); vm.requestConfirmation()
        vm.confirm(); vm.confirm(); advanceUntilIdle()
        assertEquals(1, repo.confirmCalls)
    }

    @Test fun `known expired claim cannot open confirmation`() = runTest(dispatcherRule.testDispatcher) {
        val repo = FakePaymentRepository().apply {
            attempt = attempt.copy(
                claimExpiresAt = Instant.EPOCH.plusSeconds(300),
                serverTimeAtClaim = Instant.EPOCH,
                observedAt = Instant.EPOCH,
            )
            restored = attempt
        }
        val vm = viewModel(repo)
        vm.attachOrder(sampleDetail()); advanceUntilIdle()
        vm.selectMethod(CashierPaymentMethod.CARD); vm.requestConfirmation()
        assertEquals(CashierPaymentStage.EXPIRED, vm.uiState.value.stage)
        assertEquals(0, repo.confirmCalls)
    }

    @Test fun `claim not owned becomes conflict without internal detail`() = runTest(dispatcherRule.testDispatcher) {
        val repo = FakePaymentRepository(confirmFailure = paymentError(CashierPaymentFailureCode.CLAIM_NOT_OWNED))
        val vm = claimedViewModel(repo)
        vm.selectMethod(CashierPaymentMethod.CARD); vm.requestConfirmation(); vm.confirm(); advanceUntilIdle()
        assertEquals(CashierPaymentStage.CONFLICT, vm.uiState.value.stage)
        assertFalse(vm.uiState.value.message.orEmpty().contains("technical"))
    }

    private suspend fun claimedViewModel(repo: FakePaymentRepository): CashierPaymentViewModel {
        val vm = viewModel(repo)
        vm.attachOrder(sampleDetail()); kotlinx.coroutines.yield(); vm.start(); kotlinx.coroutines.yield()
        return vm
    }

    private fun viewModel(repo: CashierPaymentRepository): CashierPaymentViewModel {
        val store = SessionStore().apply {
            update(UserSession(
                CashierRepositoryTest.USER_ID, "", "Owner", UserRole.OWNER,
                setOf(AppPermission.OPERATE_CASHIER),
                UserBranch(CashierRepositoryTest.BRANCH_ID, "CENTRO", "Sucursal Centro", true),
                SessionMode.REMOTE,
            ))
        }
        return CashierPaymentViewModel(
            RestoreCashierPaymentAttemptUseCase(repo, store), ClaimCashierPaymentUseCase(repo, store),
            RenewCashierPaymentClaimUseCase(repo, store), ReleaseCashierPaymentClaimUseCase(repo, store),
            ConfirmCashierPaymentUseCase(repo, store),
        )
    }

    private class FakePaymentRepository(var claimFailure: Throwable? = null, var confirmFailure: Throwable? = null) : CashierPaymentRepository {
        var attempt = attempt()
        var restored: CashierPaymentAttempt? = null
        var renewed = false
        var released = false
        var confirmCalls = 0
        val retryFlags = mutableListOf<Boolean>()
        val inputs = mutableListOf<CashierPaymentInput?>()
        var lastInput: CashierPaymentInput? = null
        override suspend fun restore(saleId: String) = restored
        override suspend fun claim(order: CashierOrderDetail, cashierId: String): CashierPaymentAttempt {
            claimFailure?.let { throw it }; restored = attempt; return attempt
        }
        override suspend fun renew(saleId: String): CashierPaymentAttempt { renewed = true; return attempt }
        override suspend fun release(saleId: String) { released = true }
        override suspend fun confirm(order: CashierOrderDetail, cashierId: String, input: CashierPaymentInput?, retryUncertain: Boolean): CashierPaymentResult {
            confirmCalls++; retryFlags += retryUncertain; inputs += input; if (input != null) { lastInput = input; attempt = attempt.copy(input = input, payloadLocked = true) }
            confirmFailure?.let { throw it }
            return result(attempt.idempotencyKey, lastInput ?: attempt.input!!)
        }
    }

    companion object {
        private const val KEY = "77777777-7777-4777-8777-777777777777"
        private const val CLAIM = "88888888-8888-4888-8888-888888888888"
        private val BASE_TIME: Instant = Instant.parse("2099-08-22T10:00:00Z")
        private fun attempt() = CashierPaymentAttempt(
            CashierRepositoryTest.SALE_ID, CLAIM, KEY, null, CashierPaymentAttemptState.CLAIMED, false,
            BASE_TIME.minusSeconds(60), BASE_TIME.plusSeconds(300),
            BASE_TIME, BASE_TIME, BASE_TIME, BASE_TIME,
        )
        private fun result(key: String, input: CashierPaymentInput) = CashierPaymentResult(
            CashierRepositoryTest.SALE_ID, "VD-TEST", CashierRepositoryTest.BRANCH_ID,
            CashierRepositoryTest.USER_ID, "99999999-9999-4999-8999-999999999999", key, input.method,
            10_000, input.amountReceivedCents ?: 10_000, (input.amountReceivedCents ?: 10_000) - 10_000,
            input.reference, Instant.parse("2026-08-22T10:01:00Z"), false,
        )
        private fun paymentError(code: CashierPaymentFailureCode) = CashierPaymentException(code, false, "technical")
    }
}
