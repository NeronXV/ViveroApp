package com.intutec.viveroapp.feature.cashier

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderDetail
import com.intutec.viveroapp.feature.cashier.domain.model.CashierOrderSummary
import com.intutec.viveroapp.feature.cashier.domain.repository.CashierRepository
import com.intutec.viveroapp.feature.cashier.domain.usecase.GetCashierOrderDetailUseCase
import com.intutec.viveroapp.feature.cashier.domain.usecase.GetPendingCashierOrdersUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierUseCasesTest {
    @Test
    fun `all authorized roles use their assigned branch including owner and admin`() = runTest {
        listOf(UserRole.CASHIER, UserRole.MANAGER, UserRole.ADMIN, UserRole.OWNER).forEach { role ->
            val repository = RecordingRepository()
            val sessionStore = SessionStore().apply { update(session(role)) }

            GetPendingCashierOrdersUseCase(repository, sessionStore)().getOrThrow()
            GetCashierOrderDetailUseCase(repository, sessionStore)(CashierRepositoryTest.SALE_ID).getOrThrow()

            assertEquals(CashierRepositoryTest.BRANCH_ID, repository.queueBranchId)
            assertEquals(CashierRepositoryTest.BRANCH_ID, repository.detailBranchId)
        }
    }

    @Test
    fun `missing permission blocks remote access`() = runTest {
        val repository = RecordingRepository()
        val sessionStore = SessionStore().apply {
            update(session(UserRole.CASHIER, capabilities = emptySet()))
        }

        assertTrue(GetPendingCashierOrdersUseCase(repository, sessionStore)().isFailure)
        assertNull(repository.queueBranchId)
    }

    @Test
    fun `inactive or missing branch blocks remote access`() = runTest {
        val inactiveRepository = RecordingRepository()
        val inactiveStore = SessionStore().apply { update(session(UserRole.OWNER, branchActive = false)) }
        val globalRepository = RecordingRepository()
        val globalStore = SessionStore().apply { update(session(UserRole.OWNER, branch = null)) }

        assertTrue(GetPendingCashierOrdersUseCase(inactiveRepository, inactiveStore)().isFailure)
        assertTrue(GetPendingCashierOrdersUseCase(globalRepository, globalStore)().isFailure)
        assertNull(inactiveRepository.queueBranchId)
        assertNull(globalRepository.queueBranchId)
    }

    @Test
    fun `demo session cannot query cashier`() = runTest {
        val repository = RecordingRepository()
        val store = SessionStore().apply { update(session(UserRole.OWNER, mode = SessionMode.DEMO)) }

        assertTrue(GetPendingCashierOrdersUseCase(repository, store)().isFailure)
        assertNull(repository.queueBranchId)
    }

    private class RecordingRepository : CashierRepository {
        var queueBranchId: String? = null
        var detailBranchId: String? = null

        override suspend fun getPendingOrders(branchId: String): Result<List<CashierOrderSummary>> {
            queueBranchId = branchId
            return Result.success(emptyList())
        }

        override suspend fun getPendingOrder(branchId: String, orderId: String): Result<CashierOrderDetail> {
            detailBranchId = branchId
            return Result.success(sampleDetail())
        }
    }

    private fun session(
        role: UserRole,
        capabilities: Set<AppPermission> = setOf(AppPermission.OPERATE_CASHIER),
        branchActive: Boolean = true,
        branch: UserBranch? = UserBranch(
            CashierRepositoryTest.BRANCH_ID,
            "CENTRO",
            "Sucursal Centro",
            branchActive,
        ),
        mode: SessionMode = SessionMode.REMOTE,
    ) = UserSession(
        userId = CashierRepositoryTest.USER_ID,
        email = "cashier@example.test",
        fullName = "Caja Test",
        role = role,
        capabilities = capabilities,
        branch = branch,
        mode = mode,
    )
}
