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
import com.intutec.viveroapp.feature.catalog.admin.presentation.ProductAdminViewModel
import com.intutec.viveroapp.feature.catalog.data.remote.CatalogAdminRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.CatalogImageRemoteDataSource
import com.intutec.viveroapp.feature.catalog.data.remote.RemoteCategoryDto
import com.intutec.viveroapp.feature.catalog.data.repository.SupabaseCatalogAdminRepository
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeImageRemoteP : CatalogImageRemoteDataSource {
    override suspend fun uploadImage(productId: String, imageId: String, bytes: ByteArray, mimeType: String): String = "$productId/$imageId.jpg"
    override suspend fun insertProductImage(productId: String, imageId: String, storagePath: String): String = imageId
    override suspend fun setPrimaryImage(imageId: String) {}
    override suspend fun deleteProductImage(imageId: String) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProductAdminViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private fun sessionStore(role: UserRole = UserRole.MANAGER): SessionStore = SessionStore().apply {
        update(UserSession("u1", "a@b.com", "Pepe", role, RolePermissions.permissionsFor(role), UserBranch("b1", "CENTRO", "Vivero Centro", true), SessionMode.REMOTE))
    }
    private fun fakeContext(): android.content.Context = org.mockito.Mockito.mock(android.content.Context::class.java)

    @Test
    fun `capacidades se exponen sin comparar roles`() = runTest(dispatcher.testDispatcher) {
        val vm = ProductAdminViewModel(SupabaseCatalogAdminRepository(FakeAdminRemote(), FakeImageRemoteP()), sessionStore(UserRole.MANAGER), ImageProcessor(), fakeContext(), SavedStateHandle())
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canManageProducts)
        assertTrue(vm.uiState.value.canManagePrices)
        assertEquals("Vivero Centro", vm.uiState.value.branchName)
    }

    @Test
    fun `validacion impide guardar con codigo corto`() = runTest(dispatcher.testDispatcher) {
        val vm = ProductAdminViewModel(SupabaseCatalogAdminRepository(FakeAdminRemote(), FakeImageRemoteP()), sessionStore(), ImageProcessor(), fakeContext(), SavedStateHandle())
        advanceUntilIdle()
        vm.onInternalCodeChanged("A")
        vm.onCommonNameChanged("Rosa")
        vm.onCategorySelected("11111111-1111-4111-8111-111111111111")
        vm.onPriceChanged("10")
        vm.saveProduct()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.fieldErrors["internalCode"])
    }

    @Test
    fun `creacion de categoria valida 2 a 100 caracteres`() = runTest(dispatcher.testDispatcher) {
        val vm = ProductAdminViewModel(SupabaseCatalogAdminRepository(FakeAdminRemote(), FakeImageRemoteP()), sessionStore(), ImageProcessor(), fakeContext(), SavedStateHandle())
        advanceUntilIdle()
        vm.onNewCategoryNameChanged("A")
        vm.createCategory()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.newCategoryError)
        vm.onNewCategoryNameChanged("Suculentas")
        vm.createCategory()
        advanceUntilIdle()
        assertNull(vm.uiState.value.newCategoryError)
        assertEquals("11111111-1111-4111-8111-111111111111", vm.uiState.value.selectedCategoryId)
    }

    @Test
    fun `guardado exitoso expone savedProductId para registrar ingreso`() = runTest(dispatcher.testDispatcher) {
        val vm = ProductAdminViewModel(SupabaseCatalogAdminRepository(FakeAdminRemote(), FakeImageRemoteP()), sessionStore(), ImageProcessor(), fakeContext(), SavedStateHandle())
        advanceUntilIdle()
        vm.onInternalCodeChanged("ROS-999")
        vm.onCommonNameChanged("Rosa Test")
        vm.onCategorySelected("11111111-1111-4111-8111-111111111111")
        vm.onPriceChanged("120.50")
        vm.onMinimumStockChanged("5")
        vm.saveProduct()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.savedProductId)
        assertEquals("Rosa Test", vm.uiState.value.savedProductName)
    }

    @Test
    fun `precio requiere MANAGE_PRICES`() = runTest(dispatcher.testDispatcher) {
        val vm = ProductAdminViewModel(SupabaseCatalogAdminRepository(FakeAdminRemote(), FakeImageRemoteP()), sessionStore(UserRole.INVENTORY), ImageProcessor(), fakeContext(), SavedStateHandle())
        advanceUntilIdle()
        assertTrue(!vm.uiState.value.canManagePrices)
        vm.onInternalCodeChanged("ROS-100")
        vm.onCommonNameChanged("Rosa")
        vm.onCategorySelected("11111111-1111-4111-8111-111111111111")
        vm.onPriceChanged("10")
        vm.saveProduct()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.fieldErrors.containsKey("price") || vm.uiState.value.saveError?.contains("MANAGE_PRICES") == true)
    }
}

private class FakeAdminRemote : CatalogAdminRemoteDataSource {
    override suspend fun loadCategoriesRaw(): List<RemoteCategoryDto> = listOf(RemoteCategoryDto("11111111-1111-4111-8111-111111111111", "Suculentas", true))
    override suspend fun upsertCategory(name: String, description: String?): RemoteCategoryDto = RemoteCategoryDto("11111111-1111-4111-8111-111111111111", name, true)
    override suspend fun upsertProduct(id: String?, internalCode: String, barcode: String?, commonName: String, scientificName: String?, description: String, categoryId: String, priceCents: Long, wholesalePriceCents: Long?, unit: String, minimumStock: Double, wateringAdvice: String, lightType: String, recommendedClimate: String, isActive: Boolean): String {
        return """{"id":"99999999-9999-4999-8999-999999999999","internal_code":"$internalCode","common_name":"$commonName","category_id":"$categoryId","price_cents":$priceCents,"unit":"$unit","minimum_stock":$minimumStock,"is_active":$isActive,"created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}"""
    }
}
