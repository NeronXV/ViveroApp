package com.intutec.viveroapp.feature.auth.domain.repository

import com.intutec.viveroapp.core.session.BackendSessionState
import kotlinx.coroutines.flow.StateFlow

interface BackendAuthGateway {
    val state: StateFlow<BackendSessionState>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun refresh(): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun changePassword(current: String, next: String): Result<Unit>
    // Launch from the owning ViewModel scope when this session is activated.
    suspend fun monitorExpiry()
}
