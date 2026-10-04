package com.intutec.viveroapp.core.session

import com.intutec.viveroapp.core.model.UserRole

// API identity is never represented as the UUID-based UserSession.
data class BackendUser(val id: Long, val email: String, val fullName: String)
data class BackendBranch(val id: Long, val code: String, val name: String, val isActive: Boolean)
data class BackendRole(val id: Long, val name: UserRole, val displayName: String)
data class BackendAccessContext(val user: BackendUser, val accessState: String, val role: BackendRole?,
    val branch: BackendBranch?, val capabilities: Set<String>) {
    fun canOperate(capability: String): Boolean = accessState == "ACTIVE" && capability in capabilities && branch?.isActive == true
}
class BackendSession(val token: String, val userId: Long, val expiresAtMillis: Long) {
    init {
        require(Regex("^[A-Za-z0-9_-]{43}$").matches(token))
        require(userId in 1..4294967295L)
        require(expiresAtMillis > 0)
    }
    override fun toString(): String = "BackendSession(userId=$userId, expiresAtMillis=$expiresAtMillis, token=<redacted>)"
}
enum class BackendAuthStatus { ANONYMOUS, AUTHENTICATING, AUTHENTICATED }
enum class BackendAccessStatus { IDLE, LOADING, READY, ERROR }
data class BackendSessionState(val session: BackendSession? = null, val context: BackendAccessContext? = null,
    val status: BackendAuthStatus = BackendAuthStatus.ANONYMOUS, val accessStatus: BackendAccessStatus = BackendAccessStatus.IDLE,
    val error: String? = null)
