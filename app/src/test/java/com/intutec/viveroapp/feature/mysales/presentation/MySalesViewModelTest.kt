package com.intutec.viveroapp.feature.mysales.presentation

import com.intutec.viveroapp.feature.mysales.domain.model.MySale
import com.intutec.viveroapp.feature.mysales.domain.model.MySaleStatus
import com.intutec.viveroapp.feature.mysales.domain.model.MySalesPage
import com.intutec.viveroapp.feature.mysales.domain.repository.MySalesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MySalesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads my sales`() = runTest {
        val repository = FakeMySalesRepository()
        val viewModel = MySalesViewModel(repository)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.items.size)
        assertEquals("VD-1", state.items[0].folio)
    }

    @Test
    fun `refresh updates items`() = runTest {
        val repository = FakeMySalesRepository()
        val viewModel = MySalesViewModel(repository)

        viewModel.refresh()

        assertFalse(state(viewModel).isRefreshing)
        assertEquals(2, repository.loadCalls)
    }

    @Test
    fun `load more appends items`() = runTest {
        val repository = FakeMySalesRepository()
        repository.nextPage = MySalesPage(
            items = listOf(testSale("VD-2")),
            hasMore = false,
            nextCursorCreatedAt = null,
            nextCursorId = null
        )
        val viewModel = MySalesViewModel(repository)
        
        viewModel.loadMore()

        assertEquals(2, state(viewModel).items.size)
        assertEquals("VD-2", state(viewModel).items[1].folio)
    }

    @Test
    fun `load more does not duplicate items if called multiple times`() = runTest {
        val repository = FakeMySalesRepository()
        repository.nextPage = MySalesPage(
            items = listOf(testSale("VD-2")),
            hasMore = false,
            nextCursorCreatedAt = null,
            nextCursorId = null
        )
        val viewModel = MySalesViewModel(repository)
        
        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(2, state(viewModel).items.size)
    }

    @Test
    fun `error state is handled correctly`() = runTest {
        val repository = FakeMySalesRepository()
        repository.shouldFail = true
        val viewModel = MySalesViewModel(repository)

        assertTrue(state(viewModel).error != null)

        repository.shouldFail = false
        viewModel.loadInitial()

        assertNull(state(viewModel).error)
        assertEquals(1, state(viewModel).items.size)
    }

    private fun state(vm: MySalesViewModel) = vm.uiState.value

    private fun testSale(folio: String) = MySale(
        id = folio,
        folio = folio,
        status = MySaleStatus.SENT_TO_CASHIER,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        subtotalCents = 1000,
        discountCents = 0,
        totalCents = 1000,
        itemCount = 1,
        totalQuantity = 1,
        paidAt = null
    )

    private class FakeMySalesRepository : MySalesRepository {
        var shouldFail = false
        var loadCalls = 0
        var nextPage: MySalesPage? = null

        override suspend fun getMyRecentSales(limit: Int, afterCreatedAt: Instant?, afterId: String?): Result<MySalesPage> {
            loadCalls++
            if (shouldFail) return Result.failure(Exception("Network error"))
            
            return if (afterId == null) {
                Result.success(MySalesPage(
                    items = listOf(MySale("id1", "VD-1", MySaleStatus.SENT_TO_CASHIER, Instant.now(), Instant.now(), 1000, 0, 1000, 1, 1, null)),
                    hasMore = true,
                    nextCursorCreatedAt = Instant.now(),
                    nextCursorId = "id1"
                ))
            } else {
                Result.success(nextPage ?: MySalesPage(emptyList(), false, null, null))
            }
        }
    }
}
