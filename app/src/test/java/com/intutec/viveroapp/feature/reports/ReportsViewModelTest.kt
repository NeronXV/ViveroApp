package com.intutec.viveroapp.feature.reports

import com.intutec.viveroapp.feature.reports.domain.model.DailySales
import com.intutec.viveroapp.feature.reports.domain.model.TopProduct
import com.intutec.viveroapp.feature.reports.domain.repository.ReportsRepository
import com.intutec.viveroapp.feature.reports.presentation.ReportRange
import com.intutec.viveroapp.feature.reports.presentation.ReportsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

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
    fun `initial state loads reports for last 7 days`() = runTest {
        val repository = FakeReportsRepository()
        val viewModel = ReportsViewModel(repository)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ReportRange.LAST_7_DAYS, state.range)
        assertEquals(1, state.dailySales.size)
        assertEquals(1, state.topProducts.size)
    }

    @Test
    fun `changing range reloads reports`() = runTest {
        val repository = FakeReportsRepository()
        val viewModel = ReportsViewModel(repository)

        viewModel.setRange(ReportRange.LAST_30_DAYS)

        assertEquals(ReportRange.LAST_30_DAYS, viewModel.uiState.value.range)
        assertEquals(2, repository.dailyCalls)
    }

    @Test
    fun `invalid custom range shows error`() = runTest {
        val repository = FakeReportsRepository()
        val viewModel = ReportsViewModel(repository)

        val today = LocalDate.now()
        viewModel.setCustomRange(today, today.minusDays(1))

        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error?.contains("posterior") == true)
    }

    @Test
    fun `retry after error works`() = runTest {
        val repository = FakeReportsRepository()
        repository.shouldFail = true
        val viewModel = ReportsViewModel(repository)

        assertTrue(viewModel.uiState.value.error != null)

        repository.shouldFail = false
        viewModel.loadReports()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.dailySales.size)
    }

    private class FakeReportsRepository : ReportsRepository {
        var shouldFail = false
        var dailyCalls = 0

        override suspend fun getDailySales(branchId: String?, startDate: LocalDate?, endDate: LocalDate?): Result<List<DailySales>> {
            dailyCalls++
            if (shouldFail) return Result.failure(Exception("Error context"))
            return Result.success(listOf(
                DailySales("b1", "Sucursal", LocalDate.now(), 1, 100L, 0L)
            ))
        }

        override suspend fun getTopProducts(branchId: String?, limit: Int): Result<List<TopProduct>> {
            if (shouldFail) return Result.failure(Exception("Error context"))
            return Result.success(listOf(
                TopProduct("p1", "Producto", "CODE", 5.0, 500L)
            ))
        }
    }
}
