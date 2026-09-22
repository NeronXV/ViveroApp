package com.intutec.viveroapp.feature.inventory

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.security.RolePermissions
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryItem
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryMovement
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryOperationResult
import com.intutec.viveroapp.feature.inventory.domain.repository.InventoryRepository
import com.intutec.viveroapp.feature.inventory.presentation.InventoryAction
import com.intutec.viveroapp.feature.inventory.presentation.InventoryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun sessionStoreWithBranch(): SessionStore = SessionStore().apply {
    update(
        UserSession(
            userId = "u1",
            email = "test@vivero.test",
            fullName = "Test User",
            role = UserRole.MANAGER,
            capabilities = RolePermissions.permissionsFor(UserRole.MANAGER),
            branch = UserBranch("b1", "CENTRO", "Vivero Centro", true),
            mode = com.intutec.viveroapp.core.session.SessionMode.REMOTE,
        ),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `loads and filters branch inventory`() = runTest(dispatcherRule.testDispatcher) {
        val repository = FakeInventoryRepository()
        val viewModel = InventoryViewModel(repository, sessionStoreWithBranch())
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.items.size)
        viewModel.updateQuery("ROS")
        assertEquals(listOf("Rosa"), viewModel.uiState.value.visibleItems.map(InventoryItem::productName))
    }

    @Test
    fun `reception reloads authoritative total`() = runTest(dispatcherRule.testDispatcher) {
        val repository = FakeInventoryRepository()
        val viewModel = InventoryViewModel(repository, sessionStoreWithBranch())
        advanceUntilIdle()

        viewModel.openAction(repository.items.first(), InventoryAction.RECEPTION)
        viewModel.updateQuantity("5")
        viewModel.updateDetail("Proveedor local")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.receptions.size)
        assertEquals(15, viewModel.uiState.value.items.first().totalQuantity)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `initial reception waits for loading and is not reopened after submission`() = runTest(dispatcherRule.testDispatcher) {
        val repository = FakeInventoryRepository()
        val viewModel = InventoryViewModel(repository, sessionStoreWithBranch())
        val productId = repository.items.first().productId

        viewModel.selectProduct(productId, "RECEPTION")
        advanceUntilIdle()
        viewModel.selectProduct(productId, "RECEPTION")
        assertEquals(productId, viewModel.uiState.value.selectedItem?.productId)
        assertEquals(InventoryAction.RECEPTION, viewModel.uiState.value.action)

        viewModel.updateQuantity("5")
        viewModel.submit()
        advanceUntilIdle()
        viewModel.selectProduct(productId, "RECEPTION")

        assertEquals(1, repository.receptions.size)
        assertEquals(15, viewModel.uiState.value.items.first().totalQuantity)
        assertNull(viewModel.uiState.value.action)
        assertNull(viewModel.uiState.value.selectedItem)
    }

    @Test
    fun `failed retry reuses the idempotency key`() = runTest(dispatcherRule.testDispatcher) {
        val repository = FakeInventoryRepository(failFirstReception = true)
        val viewModel = InventoryViewModel(repository, sessionStoreWithBranch())
        advanceUntilIdle()
        viewModel.openAction(repository.items.first(), InventoryAction.RECEPTION)
        viewModel.updateQuantity("3")

        viewModel.submit()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(2, repository.receptions.size)
        assertEquals(repository.receptions[0].key, repository.receptions[1].key)
    }

    @Test
    fun `count requires a reason before calling backend`() = runTest(dispatcherRule.testDispatcher) {
        val repository = FakeInventoryRepository()
        val viewModel = InventoryViewModel(repository, sessionStoreWithBranch())
        advanceUntilIdle()
        viewModel.openAction(repository.items.first(), InventoryAction.COUNT)
        viewModel.updateQuantity("8")

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(repository.counts.isEmpty())
        assertEquals("Escribe el motivo del conteo.", viewModel.uiState.value.error)
    }
}

private class FakeInventoryRepository(
    private val failFirstReception: Boolean = false,
) : InventoryRepository {
    var items = listOf(
        InventoryItem("11111111-1111-4111-8111-111111111111", "Rosa", "ROS-01", "pieza", 10, 3, false),
        InventoryItem("22222222-2222-4222-8222-222222222222", "Lavanda", "LAV-01", "pieza", 2, 3, true),
    )
    val receptions = mutableListOf<ReceptionCall>()
    val counts = mutableListOf<String>()

    override suspend fun getDashboard() = Result.success(items)

    override suspend fun recordReception(productId: String, quantity: Int, notes: String?, idempotencyKey: String): Result<InventoryOperationResult> {
        receptions += ReceptionCall(quantity, idempotencyKey)
        if (failFirstReception && receptions.size == 1) return Result.failure(IllegalStateException("Sin red"))
        items = items.map { if (it.productId == productId) it.copy(totalQuantity = it.totalQuantity + quantity) else it }
        return Result.success(InventoryOperationResult(productId, items.first { it.productId == productId }.totalQuantity, idempotentReplay = false))
    }

    override suspend fun reconcileCount(productId: String, countedQuantity: Int, reason: String, idempotencyKey: String): Result<InventoryOperationResult> {
        counts += idempotencyKey
        return Result.success(InventoryOperationResult(productId, countedQuantity, idempotentReplay = false))
    }

    override suspend fun getHistory(productId: String): Result<List<InventoryMovement>> = Result.success(emptyList())
}

private data class ReceptionCall(val quantity: Int, val key: String)
