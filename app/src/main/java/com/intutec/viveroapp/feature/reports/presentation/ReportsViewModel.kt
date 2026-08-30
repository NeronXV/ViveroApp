package com.intutec.viveroapp.feature.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.feature.reports.domain.model.DailySales
import com.intutec.viveroapp.feature.reports.domain.model.TopProduct
import com.intutec.viveroapp.feature.reports.domain.repository.ReportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class ReportRange { LAST_7_DAYS, LAST_30_DAYS, CUSTOM }

data class ReportsUiState(
    val isLoading: Boolean = true,
    val range: ReportRange = ReportRange.LAST_7_DAYS,
    val startDate: LocalDate = LocalDate.now().minusDays(7),
    val endDate: LocalDate = LocalDate.now(),
    val dailySales: List<DailySales> = emptyList(),
    val topProducts: List<TopProduct> = emptyList(),
    val error: String? = null,
) {
    val totalRevenueCents: Long get() = dailySales.sumOf { it.revenueCents }
    val totalSalesCount: Int get() = dailySales.sumOf { it.salesCount }
    val totalDiscountCents: Long get() = dailySales.sumOf { it.discountCents }
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: ReportsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReports()
    }

    fun setRange(range: ReportRange) {
        val (start, end) = when (range) {
            ReportRange.LAST_7_DAYS -> LocalDate.now().minusDays(7) to LocalDate.now()
            ReportRange.LAST_30_DAYS -> LocalDate.now().minusDays(30) to LocalDate.now()
            ReportRange.CUSTOM -> _uiState.value.startDate to _uiState.value.endDate
        }
        _uiState.update { it.copy(range = range, startDate = start, endDate = end) }
        if (range != ReportRange.CUSTOM) {
            loadReports()
        }
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        if (start.isAfter(end)) {
            _uiState.update { it.copy(error = "La fecha inicial no puede ser posterior a la final.") }
            return
        }
        _uiState.update { it.copy(range = ReportRange.CUSTOM, startDate = start, endDate = end, error = null) }
        loadReports()
    }

    fun loadReports() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val dailyResult = repository.getDailySales(
                startDate = _uiState.value.startDate,
                endDate = _uiState.value.endDate
            )
            
            val topResult = repository.getTopProducts(limit = 10)

            if (dailyResult.isSuccess && topResult.isSuccess) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        dailySales = dailyResult.getOrThrow(),
                        topProducts = topResult.getOrThrow()
                    ) 
                }
            } else {
                val errorMessage = dailyResult.exceptionOrNull()?.message 
                    ?: topResult.exceptionOrNull()?.message 
                    ?: "No pudimos cargar los reportes."
                _uiState.update { it.copy(isLoading = false, error = errorMessage) }
            }
        }
    }
}
