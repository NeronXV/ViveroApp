package com.intutec.viveroapp.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.auth.domain.repository.BackendAuthGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@HiltViewModel
class BackendAuthViewModel @Inject constructor(
    gateway: Provider<BackendAuthGateway>,
    private val transport: Provider<BackendApiTransport>,
    sessions: SessionStore,
) : ViewModel() {
    private val _passwordChange = MutableStateFlow(BackendPasswordChangeState())
    val passwordChange = _passwordChange.asStateFlow()
    private val repository = runCatching { gateway.get() }.getOrNull()
    private val _uiState = MutableStateFlow(AuthUiState(status = AuthStatus.SIGNED_OUT, remoteConfigured = repository != null,
        errorMessage = if (repository == null) "Configura la URL pública del Backend API." else null))
    val uiState = _uiState.asStateFlow()
    private var action: Job? = null
    init {
        // No UUID identity or Supabase session is granted to API consumers.
        sessions.update(null)
        repository?.let { repo ->
            viewModelScope.launch { repo.monitorExpiry() }
            viewModelScope.launch {
                repo.state.collect { state -> _uiState.update {
                    it.copy(backend = state, password = if (state.session != null) "" else it.password,
                        status = when (state.status) {
                            BackendAuthStatus.AUTHENTICATED -> AuthStatus.AUTHENTICATED
                            BackendAuthStatus.AUTHENTICATING -> AuthStatus.WORKING
                            BackendAuthStatus.ANONYMOUS -> AuthStatus.SIGNED_OUT
                        }, errorMessage = state.error)
                } }
            }
        }
    }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, errorMessage = null, infoMessage = null) }
    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun togglePasswordVisibility() = _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    fun signIn() {
        val repo = repository ?: return
        if (action?.isActive == true || repo.state.value.session != null) return
        val state = _uiState.value
        action = viewModelScope.launch {
            repo.signIn(state.email, state.password)
            _uiState.update { it.copy(password = "") }
        }
    }
    fun refresh() {
        val repo = repository ?: return
        if (repo.state.value.session == null || action?.isActive == true) return
        action = viewModelScope.launch { repo.refresh() }
    }
    fun signOut() {
        val repo = repository ?: return
        action?.cancel()
        action = viewModelScope.launch {
            val result = repo.signOut()
            _uiState.update { it.copy(password = "", infoMessage = result.exceptionOrNull()?.message) }
        }
    }
    fun clearPasswordChange() { if (!_passwordChange.value.working) _passwordChange.value = BackendPasswordChangeState() }
    fun changePassword(current: String, next: String) {
        val repo = repository ?: return
        if (action?.isActive == true || repo.state.value.session == null) return
        _passwordChange.value = BackendPasswordChangeState(working = true)
        action = viewModelScope.launch {
            try {
                val result = repo.changePassword(current, next)
                _passwordChange.value = BackendPasswordChangeState(error = result.exceptionOrNull()?.message)
                if (result.isSuccess) _uiState.update { it.copy(password = "", passwordVisible = false,
                    infoMessage = "Contraseña actualizada. Inicia sesión con tu nueva contraseña.") }
                else if (repo.state.value.session == null) _uiState.update { it.copy(password = "", infoMessage = result.exceptionOrNull()?.message) }
            } finally { _passwordChange.update { it.copy(working = false) } }
        }
    }
    fun sendPasswordReset() {
        if (repository == null || action?.isActive == true || repository.state.value.session != null) return
        val email = _uiState.value.email.trim()
        action = viewModelScope.launch {
            _uiState.update { it.copy(status = AuthStatus.WORKING, errorMessage = null, infoMessage = null) }
            try {
                require(email.length in 3..254 && email.contains('@'))
                val response = transport.get().recovery(buildJsonObject { put("email", email) }.toString())
                val body = Json.parseToJsonElement(response.body).jsonObject
                check(response.status == 202 && body.keys == setOf("accepted") && body["accepted"] == JsonPrimitive(true))
                _uiState.update { it.copy(infoMessage = "Si tu cuenta permite recuperación, recibirás instrucciones para continuar en la Web.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(errorMessage = "No se confirmó la solicitud de recuperación. Intenta más tarde.") } }
            finally { _uiState.update { it.copy(status = AuthStatus.SIGNED_OUT, password = "") } }
        }
    }
}

data class BackendPasswordChangeState(val working: Boolean = false, val error: String? = null)
