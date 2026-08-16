package com.intutec.viveroapp.feature.auth.data.remote

import com.intutec.viveroapp.core.network.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject

class SupabaseAuthRemoteDataSource @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
) : AuthRemoteDataSource {
    override fun currentUserOrNull(): AuthenticatedUser? =
        supabaseProvider.client?.auth?.currentUserOrNull()?.let {
            AuthenticatedUser(id = it.id, email = it.email.orEmpty())
        }

    override suspend fun signIn(email: String, password: String): AuthenticatedUser {
        val client = requireClient()
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val user = client.auth.currentUserOrNull() ?: error("No se pudo iniciar la sesión.")
        return AuthenticatedUser(id = user.id, email = user.email.orEmpty())
    }

    override suspend fun sendPasswordReset(email: String) {
        requireClient().auth.resetPasswordForEmail(email)
    }

    override suspend fun signOut() {
        supabaseProvider.client?.auth?.signOut()
    }

    override suspend fun loadProfiles(userId: String): List<ProfileDto> =
        requireClient().from("profiles").select(
            columns = Columns.raw("id,full_name,branch_id,is_active"),
        ) {
            filter { eq("id", userId) }
        }.decodeList()

    override suspend fun loadRoleAssignments(userId: String): List<UserRoleAssignmentDto> =
        requireClient().from("user_roles").select(
            columns = Columns.raw("role_id,role:roles(name)"),
        ) {
            filter { eq("user_id", userId) }
        }.decodeList()

    override suspend fun loadCapabilities(roleId: Int): List<PermissionDto> =
        requireClient().from("role_permissions").select(
            columns = Columns.raw("permission_name"),
        ) {
            filter { eq("role_id", roleId) }
        }.decodeList()

    override suspend fun loadBranches(branchId: String): List<BranchDto> =
        requireClient().from("branches").select(
            columns = Columns.raw("id,code,name,is_active"),
        ) {
            filter { eq("id", branchId) }
        }.decodeList()

    private fun requireClient() = checkNotNull(supabaseProvider.client) {
        "Supabase no está configurado para cuentas reales."
    }
}
