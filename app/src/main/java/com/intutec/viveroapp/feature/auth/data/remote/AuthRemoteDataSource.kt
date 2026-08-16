package com.intutec.viveroapp.feature.auth.data.remote

data class AuthenticatedUser(
    val id: String,
    val email: String,
)

sealed interface InitialAuthState {
    data class Authenticated(val user: AuthenticatedUser) : InitialAuthState
    data object NotAuthenticated : InitialAuthState
    data object InvalidSession : InitialAuthState
    data class RefreshFailure(val kind: RefreshFailureKind) : InitialAuthState
}

enum class RefreshFailureKind { NETWORK, SERVER }

interface AuthRemoteDataSource {
    suspend fun awaitInitialAuthState(): InitialAuthState
    suspend fun signIn(email: String, password: String): AuthenticatedUser
    suspend fun sendPasswordReset(email: String)
    suspend fun signOut()
    suspend fun loadProfiles(userId: String): List<ProfileDto>
    suspend fun loadRoleAssignments(userId: String): List<UserRoleAssignmentDto>
    suspend fun loadCapabilities(roleId: Int): List<PermissionDto>
    suspend fun loadBranches(branchId: String): List<BranchDto>
}
