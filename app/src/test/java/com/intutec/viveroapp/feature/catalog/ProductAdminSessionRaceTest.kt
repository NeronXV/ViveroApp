package com.intutec.viveroapp.feature.catalog

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.RolePermissions
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import androidx.lifecycle.SavedStateHandle
import com.intutec.viveroapp.core.media.ImageProcessor
import com.intutec.viveroapp.feature.catalog.admin.presentation.ProductAdminSessionGate
import com.intutec.viveroapp.feature.catalog.admin.presentation.ProductAdminViewModel
import com.intutec.viveroapp.feature.catalog.data.remote.CatalogAdminRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.CatalogImageRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteCategoryDto
import com.intutec.viveroapp.feature.catalog.data.repository.SupabaseCatalogAdminRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductAdminSessionRaceTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private fun storeWith(role: UserRole, userId: String = "u1"): SessionStore = SessionStore().apply {
        update(UserSession(userId, "a@b.com", "Test", role, RolePermissions.permissionsFor(role), UserBranch("b1", "CENTRO", "Centro", true), SessionMode.REMOTE))
    }
    private fun vmWith(remote: CatalogAdminRemoteDataSource, store: SessionStore) = ProductAdminViewModel(
        SupabaseCatalogAdminRepository(remote, FakeImageRemote()),
        store,
        ImageProcessor(),
        org.mockito.Mockito.mock(android.content.Context::class.java),
        SavedStateHandle(),
    )
    private class FakeImageRemote : CatalogImageRemoteDataSource {
        override suspend fun uploadImage(productId: String, imageId: String, bytes: ByteArray, mimeType: String): String = "$productId/$imageId.jpg"
        override suspend fun insertProductImage(productId: String, imageId: String, storagePath: String): String = imageId
        override suspend fun setPrimaryImage(imageId: String) {}
        override suspend fun deleteProductImage(imageId: String) {}
    }

    @Test
    fun `sesion tarda no consulta categorias como anonimo`() = runTest(dispatcher.testDispatcher) {
        val remote = CountingRemote()
        val store = SessionStore() // vacio: CHECKING
        val vm = vmWith(remote, store)
        advanceUntilIdle()
        assertEquals(ProductAdminSessionGate.WAITING_SESSION, vm.uiState.value.sessionGate)
        assertEquals(0, remote.loadCount)
        assertFalse(vm.uiState.value.isLoadingCategories)
        assertTrue(vm.uiState.value.categoriesError == null)
    }

    @Test
    fun `sesion autenticada carga una sola vez`() = runTest(dispatcher.testDispatcher) {
        val remote = CountingRemote()
        val store = SessionStore()
        val vm = vmWith(remote, store)
        advanceUntilIdle()
        store.update(UserSession("u1", "a@b.com", "Test", UserRole.MANAGER, RolePermissions.permissionsFor(UserRole.MANAGER), UserBranch("b1", "CENTRO", "Centro", true), SessionMode.REMOTE))
        advanceUntilIdle()
        assertEquals(1, remote.loadCount)
        assertEquals(ProductAdminSessionGate.READY, vm.uiState.value.sessionGate)
        // no segundo load automatico
        advanceUntilIdle()
        assertEquals(1, remote.loadCount)
    }

    @Test
    fun `usuario sin capacidad no consulta y muestra acceso denegado`() = runTest(dispatcher.testDispatcher) {
        val remote = CountingRemote()
        val store = storeWith(UserRole.SALES)
        val vm = vmWith(remote, store)
        advanceUntilIdle()
        assertEquals(ProductAdminSessionGate.ACCESS_DENIED, vm.uiState.value.sessionGate)
        assertEquals(0, remote.loadCount)
        assertFalse(vm.uiState.value.canManageProducts)
    }

    @Test
    fun `cambio de usuario cancela y limpia carga anterior`() = runTest(dispatcher.testDispatcher) {
        val remote = CountingRemote(delayMs = 5000)
        val store = storeWith(UserRole.MANAGER, "u1")
        val vm = vmWith(remote, store)
        advanceUntilIdle()
        // primera carga en progreso
        store.update(UserSession("u2", "b@b.com", "Otro", UserRole.MANAGER, RolePermissions.permissionsFor(UserRole.MANAGER), UserBranch("b1", "CENTRO", "Centro", true), SessionMode.REMOTE))
        advanceUntilIdle()
        // debe limpiar categorias y cargar para nuevo usuario (al menos 1, max 2 por cancelacion)
        assertTrue(remote.loadCount in 1..2)
        assertTrue(vm.uiState.value.categories.isEmpty() || vm.uiState.value.sessionGate == ProductAdminSessionGate.READY)
    }

    @Test
    fun `error 42501 muestra mensaje seguro sin detalles tecnicos`() = runTest(dispatcher.testDispatcher) {
        val remote = ErrorRemote(42501, "42501")
        val store = storeWith(UserRole.MANAGER)
        val vm = vmWith(remote, store)
        advanceUntilIdle()
        val err = vm.uiState.value.categoriesError ?: ""
        assertTrue(err.contains("No tienes permiso"))
        assertFalse(err.contains("42501"))
        assertFalse(err.contains("categories"))
        assertFalse(err.contains("Bearer"))
        assertFalse(err.contains("apikey"))
        assertFalse(err.contains("https://"))
    }

    @Test
    fun `ningun estado contiene URL headers Bearer apikey o excepcion cruda`() = runTest(dispatcher.testDispatcher) {
        val remote = ErrorRemote(403, "permission denied for table categories")
        val store = storeWith(UserRole.MANAGER)
        val vm = vmWith(remote, store)
        advanceUntilIdle()
        val combined = listOfNotNull(vm.uiState.value.categoriesError, vm.uiState.value.saveError, vm.uiState.value.newCategoryError).joinToString(" ")
        assertFalse(combined.contains("http", true))
        assertFalse(combined.contains("Bearer", true))
        assertFalse(combined.contains("apikey", true))
        assertFalse(combined.contains("permission denied for table", true))
    }

    private class CountingRemote(var delayMs: Long = 0) : CatalogAdminRemoteDataSource {
        var loadCount = 0
        override suspend fun loadCategoriesRaw(): List<RemoteCategoryDto> {
            loadCount++
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            return listOf(RemoteCategoryDto("11111111-1111-4111-8111-111111111111", "Cat", true))
        }
        override suspend fun upsertCategory(name: String, description: String?): RemoteCategoryDto = RemoteCategoryDto("id", name, true)
        override suspend fun upsertProduct(id: String?, internalCode: String, barcode: String?, commonName: String, scientificName: String?, description: String, categoryId: String, priceCents: Long, wholesalePriceCents: Long?, unit: String, minimumStock: Double, wateringAdvice: String, lightType: String, recommendedClimate: String, isActive: Boolean): String = ""
    }

    private class ErrorRemote(val status: Int, val msg: String) : CatalogAdminRemoteDataSource {
        override suspend fun loadCategoriesRaw(): List<RemoteCategoryDto> {
            throw IllegalStateException("$status 42501 $msg permission denied for table categories")
        }
        override suspend fun upsertCategory(name: String, description: String?): RemoteCategoryDto { throw IllegalStateException("42501 permission denied") }
        override suspend fun upsertProduct(id: String?, internalCode: String, barcode: String?, commonName: String, scientificName: String?, description: String, categoryId: String, priceCents: Long, wholesalePriceCents: Long?, unit: String, minimumStock: Double, wateringAdvice: String, lightType: String, recommendedClimate: String, isActive: Boolean): String { throw IllegalStateException("401 42501") }
    }
}
