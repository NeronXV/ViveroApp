package com.intutec.viveroapp.feature.auth.data.remote

data class AuthenticatedUser(
    val id: String,
    val email: String,
)

interface AuthRemoteDataSource {
    fun currentUserOrNull(): AuthenticatedUser?
    suspend fun signIn(email: String, password: String): AuthenticatedUser
    suspend fun sendPasswordReset(email: String)
    suspend fun signOut()
    suspend fun loadProfiles(userId: String): List<ProfileDto>
    suspend fun loadRoleAssignments(userId: String): List<UserRoleAssignmentDto>
    suspend fun loadCapabilities(roleId: Int): List<PermissionDto>
    suspend fun loadBranches(branchId: String): List<BranchDto>
}
