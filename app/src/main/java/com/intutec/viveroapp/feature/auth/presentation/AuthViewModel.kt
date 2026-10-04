package com.intutec.viveroapp.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.auth.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthStatus { CHECKING, SIGNED_OUT, WORKING, AUTHENTICATED }

data class AuthUiState(
    val status: AuthStatus = AuthStatus.CHECKING,
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val remoteConfigured: Boolean = false,
    val session: UserSession? = null,
    val backend: com.intutec.viveroapp.core.session.BackendSessionState = com.intutec.viveroapp.core.session.BackendSessionState(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AuthUiState(
            remoteConfigured = repository.isRemoteConfigured,
        ),
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        restoreSession()
    }

    fun onEmailChanged(value: String) = _uiState.update {
        it.copy(email = value, errorMessage = null, infoMessage = null)
    }

    fun onPasswordChanged(value: String) = _uiState.update {
        it.copy(password = value, errorMessage = null)
    }

    fun togglePasswordVisibility() = _uiState.update {
        it.copy(passwordVisible = !it.passwordVisible)
    }

    fun signIn() {
        val current = _uiState.value
        runAuthAction { repository.signIn(current.email, current.password) }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email
        viewModelScope.launch {
            _uiState.update { it.copy(status = AuthStatus.WORKING, errorMessage = null) }
            repository.sendPasswordReset(email).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            status = AuthStatus.SIGNED_OUT,
                            infoMessage = "Te enviamos instrucciones si el correo está registrado.",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            status = AuthStatus.SIGNED_OUT,
                            password = "",
                            session = null,
                            errorMessage = error.readableMessage(),
                        )
                    }
                },
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _uiState.value = AuthUiState(
                status = AuthStatus.SIGNED_OUT,
                remoteConfigured = repository.isRemoteConfigured,
            )
        }
    }

    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private fun restoreSession() {
        viewModelScope.launch {
            repository.restoreSession().fold(
                onSuccess = { session ->
                    _uiState.update {
                        it.copy(
                            status = if (session == null) AuthStatus.SIGNED_OUT else AuthStatus.AUTHENTICATED,
                            session = session,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            status = AuthStatus.SIGNED_OUT,
                            session = null,
                            errorMessage = error.readableMessage(),
                        )
                    }
                },
            )
        }
    }

    private fun runAuthAction(action: suspend () -> Result<UserSession>) {
        viewModelScope.launch {
            _uiState.update { it.copy(status = AuthStatus.WORKING, errorMessage = null, infoMessage = null) }
            action().fold(
                onSuccess = { session ->
                    _uiState.update {
                        it.copy(status = AuthStatus.AUTHENTICATED, session = session, password = "")
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            status = AuthStatus.SIGNED_OUT,
                            password = "",
                            session = null,
                            errorMessage = error.readableMessage(),
                        )
                    }
                },
            )
        }
    }
}

private fun Throwable.readableMessage(): String = when {
    message?.contains("Invalid login", ignoreCase = true) == true -> "Correo o contraseña incorrectos."
    message?.contains("network", ignoreCase = true) == true -> "No pudimos conectar con el servidor. Revisa tu conexión."
    else -> message ?: "Ocurrió un problema. Intenta de nuevo."
}
