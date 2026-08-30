package com.intutec.viveroapp.feature.mysales.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.feature.mysales.domain.model.MySale
import com.intutec.viveroapp.feature.mysales.domain.repository.MySalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class MySalesUiState(
    val items: List<MySale> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isMoreLoading: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val nextCursorCreatedAt: Instant? = null,
    val nextCursorId: String? = null,
)

@HiltViewModel
class MySalesViewModel @Inject constructor(
    private val repository: MySalesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MySalesUiState())
    val uiState: StateFlow<MySalesUiState> = _uiState.asStateFlow()

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getMyRecentSales(limit = 20)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = page.items,
                            hasMore = page.hasMore,
                            nextCursorCreatedAt = page.nextCursorCreatedAt,
                            nextCursorId = page.nextCursorId
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "No pudimos cargar tus ventas.") }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            repository.getMyRecentSales(limit = 20)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            items = page.items,
                            hasMore = page.hasMore,
                            nextCursorCreatedAt = page.nextCursorCreatedAt,
                            nextCursorId = page.nextCursorId
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isRefreshing = false, error = e.message ?: "Error al actualizar.") }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isMoreLoading || !state.hasMore || state.nextCursorId == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isMoreLoading = true) }
            repository.getMyRecentSales(
                limit = 20,
                afterCreatedAt = state.nextCursorCreatedAt,
                afterId = state.nextCursorId
            )
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            isMoreLoading = false,
                            items = state.items + page.items,
                            hasMore = page.hasMore,
                            nextCursorCreatedAt = page.nextCursorCreatedAt,
                            nextCursorId = page.nextCursorId
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isMoreLoading = false, error = e.message ?: "Error al cargar más ventas.") }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
