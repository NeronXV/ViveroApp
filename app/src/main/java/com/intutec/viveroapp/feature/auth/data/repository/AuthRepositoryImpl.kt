package com.intutec.viveroapp.feature.auth.data.repository

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.network.SupabaseProvider
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.auth.data.remote.ProfileDto
import com.intutec.viveroapp.feature.auth.data.remote.UserRoleDto
import com.intutec.viveroapp.feature.auth.domain.repository.AuthRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
    private val sessionStore: SessionStore,
) : AuthRepository {
    override val isRemoteConfigured: Boolean get() = supabaseProvider.isConfigured

    override suspend fun restoreSession(): Result<UserSession?> = runCatching {
        val client = supabaseProvider.client ?: return@runCatching null
        val user = client.auth.currentUserOrNull() ?: return@runCatching null
        loadProfile(user.id, user.email.orEmpty(), isDemo = false).also(sessionStore::update)
    }

    override suspend fun signIn(email: String, password: String): Result<UserSession> = runCatching {
        require(email.isNotBlank()) { "Escribe tu correo." }
        require(password.length >= 6) { "La contraseña debe tener al menos 6 caracteres." }
        val client = supabaseProvider.client
            ?: error("Supabase aún no está configurado. Usa el acceso de demostración.")
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val user = client.auth.currentUserOrNull() ?: error("No se pudo iniciar la sesión.")
        loadProfile(user.id, user.email ?: email, isDemo = false).also(sessionStore::update)
    }

    override suspend fun signInDemo(): Result<UserSession> = runCatching {
        delay(350)
        UserSession(
            userId = "demo-sales",
            email = "ventas@vivero.demo",
            fullName = "Mariana López",
            role = UserRole.SALES,
            branchName = "Vivero Centro",
            isDemo = true,
        ).also(sessionStore::update)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        require(email.contains('@')) { "Escribe un correo válido." }
        val client = supabaseProvider.client
            ?: error("La recuperación estará disponible al configurar Supabase.")
        client.auth.resetPasswordForEmail(email.trim())
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        supabaseProvider.client?.auth?.signOut()
        sessionStore.update(null)
    }

    private suspend fun loadProfile(userId: String, email: String, isDemo: Boolean): UserSession {
        val client = checkNotNull(supabaseProvider.client)
        val profile = client.from("profiles").select(
            columns = Columns.raw("id,full_name,branch:branches(name)"),
        ) {
            filter { eq("id", userId) }
        }.decodeSingle<ProfileDto>()
        val assignedRole = client.from("user_roles").select(columns = Columns.raw("role:roles(name)")) {
            filter { eq("user_id", userId) }
        }.decodeSingle<UserRoleDto>()
        val role = UserRole.entries.firstOrNull { it.name == assignedRole.role.name }
            ?: error("Tu perfil no tiene un rol válido. Contacta al administrador.")
        return UserSession(
            userId = userId,
            email = email,
            fullName = profile.fullName,
            role = role,
            branchName = profile.branch?.name ?: "Sin sucursal",
            isDemo = isDemo,
        )
    }
}
