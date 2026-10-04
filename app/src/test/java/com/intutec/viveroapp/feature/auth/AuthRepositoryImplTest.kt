package com.intutec.viveroapp.feature.auth

import com.intutec.viveroapp.core.network.SupabaseProvider
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.auth.data.remote.AuthRemoteDataSource
import com.intutec.viveroapp.feature.auth.data.remote.AuthenticatedUser
import com.intutec.viveroapp.feature.auth.data.remote.BranchDto
import com.intutec.viveroapp.feature.auth.data.remote.PermissionDto
import com.intutec.viveroapp.feature.auth.data.remote.ProfileDto
import com.intutec.viveroapp.feature.auth.data.remote.RoleDto
import com.intutec.viveroapp.feature.auth.data.remote.UserRoleAssignmentDto
import com.intutec.viveroapp.feature.auth.data.remote.InitialAuthState
import com.intutec.viveroapp.feature.auth.data.remote.RefreshFailureKind
import com.intutec.viveroapp.feature.auth.data.repository.AuthRepositoryImpl
import com.intutec.viveroapp.feature.auth.data.repository.InvalidStoredSessionException
import com.intutec.viveroapp.feature.auth.data.repository.TransientSessionRefreshException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryImplTest {
    @Test
    fun `initializing waits before restoring authenticated owner`() = runTest {
        val fixture = fixture()
        fixture.remote.initializationGate = CompletableDeferred()

        val restoration = async { fixture.repository.restoreSession() }

        assertFalse(restoration.isCompleted)
        assertNull(fixture.store.session.value)
        fixture.remote.initializationGate?.complete(Unit)

        assertEquals("OWNER", restoration.await().getOrThrow()?.role?.name)
        assertEquals("CENTRO", fixture.store.session.value?.branch?.code)
    }

    @Test
    fun `not authenticated after initialization clears operational context`() = runTest {
        val fixture = fixture(initialState = InitialAuthState.NotAuthenticated)

        val restored = fixture.repository.restoreSession().getOrThrow()

        assertNull(restored)
        assertNull(fixture.store.session.value)
        assertEquals(0, fixture.remote.signOutCalls)
    }

    @Test
    fun `transient refresh failure preserves published context and does not sign out`() = runTest {
        val fixture = fixture()
        val existing = fixture.repository.restoreSession().getOrThrow()
        fixture.remote.initialState = InitialAuthState.RefreshFailure(RefreshFailureKind.NETWORK)

        val error = fixture.repository.restoreSession().exceptionOrNull()

        assertTrue(error is TransientSessionRefreshException)
        assertEquals(existing, fixture.store.session.value)
        assertEquals(0, fixture.remote.signOutCalls)
    }

    @Test
    fun `definitely invalid stored session is cleared with explicit error`() = runTest {
        val fixture = fixture()
        fixture.repository.restoreSession().getOrThrow()
        fixture.remote.initialState = InitialAuthState.InvalidSession

        val error = fixture.repository.restoreSession().exceptionOrNull()

        assertTrue(error is InvalidStoredSessionException)
        assertNull(fixture.store.session.value)
        assertTrue(error?.message?.contains("ya no es válida") == true)
    }

    @Test
    fun `maps profile role remote capabilities and branch`() = runTest {
        val fixture = fixture()

        val session = fixture.repository.restoreSession().getOrThrow()!!

        assertEquals(USER_ID, session.userId)
        assertEquals("Owner", session.fullName)
        assertEquals("OWNER", session.role.name)
        assertEquals(
            setOf(AppPermission.MANAGE_BRANCHES, AppPermission.MANAGE_USERS, AppPermission.CREATE_SALES),
            session.capabilities,
        )
        assertEquals(BRANCH_ID, session.branchId)
        assertEquals("CENTRO", session.branch?.code)
        assertEquals("Sucursal Centro", session.branchName)
        assertEquals(SessionMode.REMOTE, session.mode)
        assertEquals(session, fixture.store.session.value)
    }

    @Test
    fun `real sign in loads remote context without using demo`() = runTest {
        val fixture = fixture()

        val session = fixture.repository.signIn("owner@example.test", "password").getOrThrow()

        assertEquals(SessionMode.REMOTE, session.mode)
        assertFalse(session.isDemo)
        assertEquals(1, fixture.remote.signInCalls)
    }

    @Test
    fun `inactive profile rejects operational session and signs out`() = runTest {
        val fixture = fixture(profile = profile(isActive = false))

        assertTrue(fixture.repository.restoreSession().isFailure)
        assertNull(fixture.store.session.value)
        assertEquals(1, fixture.remote.signOutCalls)
    }

    @Test
    fun `inactive branch rejects operational session and signs out`() = runTest {
        val fixture = fixture(branches = listOf(branch(isActive = false)))

        assertTrue(fixture.repository.restoreSession().isFailure)
        assertNull(fixture.store.session.value)
        assertEquals(1, fixture.remote.signOutCalls)
    }

    @Test
    fun `user without role is rejected`() = runTest {
        val fixture = fixture(assignments = emptyList())

        val error = fixture.repository.restoreSession().exceptionOrNull()

        assertTrue(error?.message?.contains("exactamente un rol") == true)
        assertNull(fixture.store.session.value)
    }

    @Test
    fun `ambiguous role assignment is rejected`() = runTest {
        val assignment = assignment()
        val fixture = fixture(assignments = listOf(assignment, assignment.copy(roleId = 6)))

        assertTrue(fixture.repository.restoreSession().isFailure)
        assertNull(fixture.store.session.value)
    }

    @Test
    fun `null branch is valid global context but cannot perform local operation`() = runTest {
        val fixture = fixture(profile = profile(branchId = null), branches = emptyList())

        val session = fixture.repository.restoreSession().getOrThrow()!!

        assertNull(session.branch)
        assertTrue(session.hasCapability(AppPermission.MANAGE_BRANCHES))
        assertTrue(session.hasCapability(AppPermission.CREATE_SALES))
        assertFalse(session.canOperateAtBranch(AppPermission.CREATE_SALES))
    }

    @Test
    fun `remote failure never publishes partial session`() = runTest {
        val fixture = fixture()
        fixture.remote.capabilityFailure = IllegalStateException("remote unavailable")

        assertTrue(fixture.repository.restoreSession().isFailure)
        assertNull(fixture.store.session.value)
        assertEquals(1, fixture.remote.signOutCalls)
    }

    @Test
    fun `sign out clears SessionStore even when remote sign out fails`() = runTest {
        val fixture = fixture()
        fixture.repository.restoreSession().getOrThrow()
        fixture.remote.signOutFailure = IllegalStateException("network")

        assertTrue(fixture.repository.signOut().isFailure)
        assertNull(fixture.store.session.value)
    }

    private fun fixture(
        profile: ProfileDto = profile(),
        assignments: List<UserRoleAssignmentDto> = listOf(assignment()),
        branches: List<BranchDto> = listOf(branch()),
        initialState: InitialAuthState = InitialAuthState.Authenticated(
            AuthenticatedUser(USER_ID, "owner@example.test"),
        ),
    ): Fixture {
        val remote = FakeAuthRemoteDataSource(
            profiles = listOf(profile),
            assignments = assignments,
            capabilities = listOf(
                PermissionDto("MANAGE_BRANCHES"),
                PermissionDto("MANAGE_USERS"),
                PermissionDto("CREATE_SALES"),
                PermissionDto("CAPABILITY_UNKNOWN_TO_THIS_APP"),
            ),
            branches = branches,
            initialState = initialState,
        )
        val store = SessionStore()
        val provider = org.mockito.Mockito.mock(SupabaseProvider::class.java)
        org.mockito.Mockito.`when`(provider.isConfigured).thenReturn(true)
        return Fixture(
            remote = remote,
            store = store,
            repository = AuthRepositoryImpl(provider, remote, store),
        )
    }

    private data class Fixture(
        val remote: FakeAuthRemoteDataSource,
        val store: SessionStore,
        val repository: AuthRepositoryImpl,
    )

    private class FakeAuthRemoteDataSource(
        var profiles: List<ProfileDto>,
        var assignments: List<UserRoleAssignmentDto>,
        var capabilities: List<PermissionDto>,
        var branches: List<BranchDto>,
        var initialState: InitialAuthState,
    ) : AuthRemoteDataSource {
        var signInCalls = 0
        var signOutCalls = 0
        var capabilityFailure: Throwable? = null
        var signOutFailure: Throwable? = null
        var initializationGate: CompletableDeferred<Unit>? = null

        override suspend fun awaitInitialAuthState(): InitialAuthState {
            initializationGate?.await()
            return initialState
        }

        override suspend fun signIn(email: String, password: String): AuthenticatedUser {
            signInCalls += 1
            return AuthenticatedUser(USER_ID, email)
        }

        override suspend fun sendPasswordReset(email: String) = Unit

        override suspend fun signOut() {
            signOutCalls += 1
            signOutFailure?.let { throw it }
        }

        override suspend fun loadProfiles(userId: String) = profiles
        override suspend fun loadRoleAssignments(userId: String) = assignments

        override suspend fun loadCapabilities(roleId: Int): List<PermissionDto> {
            capabilityFailure?.let { throw it }
            return capabilities
        }

        override suspend fun loadBranches(branchId: String) = branches
    }

    companion object {
        private const val USER_ID = "11111111-1111-4111-8111-111111111111"
        private const val BRANCH_ID = "22222222-2222-4222-8222-222222222222"

        private fun profile(branchId: String? = BRANCH_ID, isActive: Boolean = true) = ProfileDto(
            id = USER_ID,
            fullName = "Owner",
            branchId = branchId,
            isActive = isActive,
        )

        private fun assignment() = UserRoleAssignmentDto(
            roleId = 1,
            role = RoleDto("OWNER"),
        )

        private fun branch(isActive: Boolean = true) = BranchDto(
            id = BRANCH_ID,
            code = "CENTRO",
            name = "Sucursal Centro",
            isActive = isActive,
        )
    }
}
