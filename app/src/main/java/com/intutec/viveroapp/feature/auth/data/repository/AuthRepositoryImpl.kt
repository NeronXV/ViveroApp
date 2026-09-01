package com.intutec.viveroapp.feature.auth.data.repository

import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.network.SupabaseProvider
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.auth.data.remote.AuthRemoteDataSource
import com.intutec.viveroapp.feature.auth.data.remote.AuthenticatedUser
import com.intutec.viveroapp.feature.auth.data.remote.InitialAuthState
import com.intutec.viveroapp.feature.auth.data.remote.RefreshFailureKind
import com.intutec.viveroapp.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabaseProvider: SupabaseProvider,
    private val remote: AuthRemoteDataSource,
    private val sessionStore: SessionStore,
) : AuthRepository {
    override val isRemoteConfigured: Boolean get() = supabaseProvider.isConfigured

    override suspend fun restoreSession(): Result<UserSession?> {
        val initialState = try {
            remote.awaitInitialAuthState()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        val user = when (initialState) {
            is InitialAuthState.Authenticated -> initialState.user
            InitialAuthState.NotAuthenticated -> {
                sessionStore.clear()
                return Result.success(null)
            }
            InitialAuthState.InvalidSession -> {
                sessionStore.clear()
                return Result.failure(InvalidStoredSessionException())
            }
            is InitialAuthState.RefreshFailure -> {
                return Result.failure(TransientSessionRefreshException(initialState.kind))
            }
        }
        return try {
            Result.success(loadAndPublish(user))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            failClosed(error)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<UserSession> {
        if (email.isBlank()) return Result.failure(IllegalArgumentException("Escribe tu correo."))
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("La contraseña debe tener al menos 6 caracteres."))
        }
        if (!isRemoteConfigured) {
            return Result.failure(IllegalStateException("Supabase no está configurado para cuentas reales."))
        }

        return try {
            val user = remote.signIn(email.trim(), password)
            Result.success(loadAndPublish(user))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            failClosed(error)
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        require(email.contains('@')) { "Escribe un correo válido." }
        check(isRemoteConfigured) { "Supabase no está configurado para cuentas reales." }
        remote.sendPasswordReset(email.trim())
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            remote.signOut()
            Result.success(Unit)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(error)
        } finally {
            sessionStore.clear()
        }
    }

    private suspend fun loadAndPublish(user: AuthenticatedUser): UserSession {
        val profile = remote.loadProfiles(user.id).singleOrSessionError(
            "No existe un perfil único para esta cuenta.",
        )
        check(profile.id == user.id) { "El perfil recibido no corresponde a la cuenta autenticada." }
        check(profile.isActive) { "Tu perfil está inactivo. Contacta al administrador." }

        val assignment = remote.loadRoleAssignments(user.id).singleOrSessionError(
            "La cuenta debe tener exactamente un rol asignado.",
        )
        val role = UserRole.entries.firstOrNull { it.name == assignment.role.name }
            ?: error("La cuenta tiene un rol no reconocido por esta versión de la aplicación.")

        val capabilities = remote.loadCapabilities(assignment.roleId)
            .mapNotNull { permission ->
                AppPermission.entries.firstOrNull { it.name == permission.permissionName }
            }
            .toSet()

        val branch = profile.branchId?.let { branchId ->
            val remoteBranch = remote.loadBranches(branchId).singleOrSessionError(
                "La sucursal asignada no está activa o disponible.",
            )
            check(remoteBranch.id == branchId) { "La sucursal recibida no corresponde al perfil." }
            check(remoteBranch.isActive) { "La sucursal asignada está inactiva." }
            UserBranch(
                id = remoteBranch.id,
                code = remoteBranch.code,
                name = remoteBranch.name,
                isActive = true,
            )
        }

        return UserSession(
            userId = user.id,
            email = user.email,
            fullName = profile.fullName,
            role = role,
            capabilities = capabilities,
            branch = branch,
            mode = SessionMode.REMOTE,
        ).also(sessionStore::update)
    }

    private suspend fun <T> failClosed(error: Throwable): Result<T> {
        sessionStore.clear()
        try {
            remote.signOut()
        } catch (signOutError: Throwable) {
            if (signOutError is CancellationException) throw signOutError
            error.addSuppressed(signOutError)
        }
        return Result.failure(error)
    }
}

class TransientSessionRefreshException(kind: RefreshFailureKind) : IllegalStateException(
    when (kind) {
        RefreshFailureKind.NETWORK ->
            "No pudimos restaurar la sesión por un problema de red. Intenta de nuevo."
        RefreshFailureKind.SERVER ->
            "El servicio de sesión no está disponible temporalmente. Intenta de nuevo."
    },
)

class InvalidStoredSessionException : IllegalStateException(
    "Tu sesión ya no es válida. Inicia sesión nuevamente.",
)

private fun <T> List<T>.singleOrSessionError(message: String): T =
    singleOrNull() ?: throw IllegalStateException(message)
