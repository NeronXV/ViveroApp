package com.intutec.viveroapp.feature.auth.domain.repository

import com.intutec.viveroapp.core.session.UserSession

interface AuthRepository {
    val isRemoteConfigured: Boolean
    val isDemoAvailable: Boolean
    suspend fun restoreSession(): Result<UserSession?>
    suspend fun signIn(email: String, password: String): Result<UserSession>
    suspend fun signInDemo(): Result<UserSession>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
}
