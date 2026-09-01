package com.intutec.viveroapp.feature.staff.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.staff.domain.model.StaffBranch
import com.intutec.viveroapp.feature.staff.domain.model.StaffMember
import com.intutec.viveroapp.feature.staff.domain.repository.StaffRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val repository: StaffRepository,
    sessionStore: SessionStore,
) : ViewModel() {
    private val session = checkNotNull(sessionStore.session.value) { "La sesión ya no está disponible." }
    private val _uiState = MutableStateFlow(
        StaffUiState(
            canAssignRoles = session.hasCapability(AppPermission.ASSIGN_ROLES),
            actorRole = session.role,
        ),
    )
    val uiState: StateFlow<StaffUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.operationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, message = null) }
            repository.getDirectory().fold(
                onSuccess = { directory -> _uiState.update { it.copy(loading = false, members = directory.members, branches = directory.branches) } },
                onFailure = { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "No pudimos cargar Personal.") } },
            )
        }
    }

    fun assignRole(member: StaffMember, role: UserRole) = mutate("Rol actualizado.") {
        repository.assignRole(member.id, role)
    }

    fun assignBranch(member: StaffMember, branch: StaffBranch) = mutate("Sucursal actualizada.") {
        repository.assignBranch(member.id, branch.id)
    }

    fun setActive(member: StaffMember, active: Boolean) = mutate(
        if (active) "Personal reactivado." else "Personal desactivado.",
    ) { repository.setActive(member.id, active) }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun mutate(successMessage: String, operation: suspend () -> Result<Unit>) {
        if (_uiState.value.operationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true, error = null, message = null) }
            operation().fold(
                onSuccess = {
                    _uiState.update { it.copy(operationInProgress = false, message = successMessage) }
                    refresh()
                },
                onFailure = { error -> _uiState.update { it.copy(operationInProgress = false, error = error.message ?: "No pudimos guardar el cambio.") } },
            )
        }
    }
}

data class StaffUiState(
    val loading: Boolean = false,
    val operationInProgress: Boolean = false,
    val members: List<StaffMember> = emptyList(),
    val branches: List<StaffBranch> = emptyList(),
    val canAssignRoles: Boolean,
    val actorRole: UserRole,
    val error: String? = null,
    val message: String? = null,
)
