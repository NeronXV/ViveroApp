package com.intutec.viveroapp.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionStore @Inject constructor() {
    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    private val backendLock = Any()
    private var backendRevision = 0L
    private val _backend = MutableStateFlow(BackendSessionState())
    val backend: StateFlow<BackendSessionState> = _backend.asStateFlow()

    internal fun beginBackendChange(state: BackendSessionState): Long = synchronized(backendLock) {
        backendRevision += 1
        _backend.value = state
        backendRevision
    }
    internal fun publishBackend(expectedRevision: Long, state: BackendSessionState): Boolean = synchronized(backendLock) {
        if (backendRevision != expectedRevision) false else { _backend.value = state; true }
    }
    fun clearBackend() = synchronized(backendLock) {
        backendRevision += 1
        _backend.value = BackendSessionState()
    }
    fun expireBackend(nowMillis: Long) = synchronized(backendLock) {
        val current = _backend.value.session
        if (current != null && nowMillis >= current.expiresAtMillis) {
            backendRevision += 1
            _backend.value = BackendSessionState(error = "La sesión venció. Inicia sesión nuevamente.")
        }
    }

    fun update(session: UserSession?) {
        _session.value = session
    }

    fun clear() {
        clearBackend()
        _session.value = null
    }
}
