package com.intutec.viveroapp.feature.catalog

import androidx.lifecycle.SavedStateHandle
import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.media.ImageProcessor
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.security.RolePermissions
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.UserBranch
import com.intutec.viveroapp.core.session.UserSession
import com.intutec.viveroapp.feature.catalog.admin.presentation.PhotoUiState
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductPhotoTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private fun store(role: UserRole = UserRole.MANAGER) = SessionStore().apply {
        update(UserSession("u1", "a@b.com", "P", role, RolePermissions.permissionsFor(role), UserBranch("b1", "CENTRO", "Centro", true), SessionMode.REMOTE))
    }
    private fun ctx() = org.mockito.Mockito.mock(android.content.Context::class.java)
    private fun vm(remote: CatalogAdminRemoteDataSource, imgRemote: CatalogImageRemoteDataSource = FakeImg(), role: UserRole = UserRole.MANAGER): ProductAdminViewModel {
        return ProductAdminViewModel(SupabaseCatalogAdminRepository(remote, imgRemote), store(role), ImageProcessor(), ctx(), SavedStateHandle())
    }

    @Test
    fun `crear sin foto guarda solo producto`() = runTest(dispatcher.testDispatcher) {
        val remote = FakeRemote()
        val img = FakeImg()
        val vm = vm(remote, img)
        advanceUntilIdle()
        vm.onInternalCodeChanged("AB-01")
        vm.onCommonNameChanged("Rosa")
        vm.onCategorySelected("11111111-1111-4111-8111-111111111111")
        vm.onPriceChanged("10")
        vm.saveProduct()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.savedProductId)
        assertEquals(0, img.uploadCount)
        assertEquals(PhotoUiState.SUCCESS, vm.uiState.value.photoState)
    }

    @Test
    fun `crear con foto subida despues de producto`() = runTest(dispatcher.testDispatcher) {
        val remote = FakeRemote()
        val img = FakeImg()
        val vm = vm(remote, img)
        advanceUntilIdle()
        vm.setPendingPhotoForTest(ByteArray(1000) { 0 }, "content://photo.jpg")
        vm.onInternalCodeChanged("AB-02")
        vm.onCommonNameChanged("Rosa2")
        vm.onCategorySelected("11111111-1111-4111-8111-111111111111")
        vm.onPriceChanged("20")
        vm.saveProduct()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.savedProductId)
        assertEquals(1, img.uploadCount)
        assertEquals(1, img.insertCount)
        assertEquals(1, img.primaryCount)
        assertEquals(PhotoUiState.SUCCESS, vm.uiState.value.photoState)
    }

    @Test
    fun `fallo al crear producto no intenta subir`() = runTest(dispatcher.testDispatcher) {
        val remote = FakeRemote(failProduct = true)
        val img = FakeImg()
        val vm = vm(remote, img)
        advanceUntilIdle()
        vm.setPendingPhotoForTest(ByteArray(500) { 1 })
        vm.onInternalCodeChanged("AB-03")
        vm.onCommonNameChanged("Rosa3")
        vm.onCategorySelected("11111111-1111-4111-8111-111111111111")
        vm.onPriceChanged("15")
        vm.saveProduct()
        advanceUntilIdle()
        assertNull(vm.uiState.value.savedProductId)
        assertEquals(0, img.uploadCount)
        assertTrue(vm.uiState.value.saveError?.isNotBlank() == true)
    }

    @Test
    fun `producto creado y subida fallida conserva producto y permite reintentar`() = runTest(dispatcher.testDispatcher) {
        val remote = FakeRemote()
        val img = FakeImg(failUpload = true)
        val vm = vm(remote, img)
        advanceUntilIdle()
        vm.setPendingPhotoForTest(ByteArray(500) { 2 })
        vm.onInternalCodeChanged("AB-04")
        vm.onCommonNameChanged("Rosa4")
        vm.onCategorySelected("11111111-1111-4111-8111-111111111111")
        vm.onPriceChanged("30")
        vm.saveProduct()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.savedProductId)
        assertEquals(PhotoUiState.ERROR, vm.uiState.value.photoState)
        assertNotNull(vm.uiState.value.imageUploadError)
        assertFalse(vm.uiState.value.imageUploadError!!.contains("http", true))
        // reintento con img que ahora sucede
        img.failUpload = false
        vm.retryImageUpload()
        advanceUntilIdle()
        assertEquals(PhotoUiState.SUCCESS, vm.uiState.value.photoState)
        assertEquals(2, img.uploadCount) // 2 intentos, idempotente path
    }

    @Test
    fun `reintento idempotente no duplica imagen principal`() = runTest(dispatcher.testDispatcher) {
        val remote = FakeRemote()
        val img = CountingPrimaryFake()
        val vm = vm(remote, img)
        advanceUntilIdle()
        vm.setPendingPhotoForTest(ByteArray(300) { 3 })
        vm.onInternalCodeChanged("AB-05")
        vm.onCommonNameChanged("Rosa5")
        vm.onCategorySelected("11111111-1111-4111-8111-111111111111")
        vm.onPriceChanged("40")
        vm.saveProduct()
        advanceUntilIdle()
        val firstId = vm.uiState.value.pendingImageId
        vm.retryImageUpload()
        advanceUntilIdle()
        assertEquals(firstId, vm.uiState.value.pendingImageId)
        assertEquals(1, img.primaryIds.distinct().size)
        assertEquals(2, img.primaryCount)
    }

    @Test
    fun `usuario sin capacidad no puede modificar imagen`() = runTest(dispatcher.testDispatcher) {
        val remote = FakeRemote()
        val vm = vm(remote, FakeImg(), UserRole.SALES)
        advanceUntilIdle()
        vm.onPhotoUriSelected("content://photo.jpg")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.photoError)
        assertTrue(vm.uiState.value.photoError!!.contains("permiso"))
    }

    @Test
    fun `permiso de camara denegado muestra error seguro sin cerrar flujo`() = runTest(dispatcher.testDispatcher) {
        val vm = vm(FakeRemote())
        advanceUntilIdle()

        vm.onCameraPermissionDenied()

        assertEquals(PhotoUiState.ERROR, vm.uiState.value.photoState)
        assertTrue(vm.uiState.value.photoError!!.contains("cámara"))
        assertFalse(vm.uiState.value.photoError!!.contains("SecurityException"))
    }

    @Test
    fun `fallo al abrir camara ofrece alternativa segura`() = runTest(dispatcher.testDispatcher) {
        val vm = vm(FakeRemote())
        advanceUntilIdle()

        vm.onCameraLaunchFailed()

        assertEquals(PhotoUiState.ERROR, vm.uiState.value.photoState)
        assertTrue(vm.uiState.value.photoError!!.contains("elige una imagen"))
        assertFalse(vm.uiState.value.photoError!!.contains("content://"))
    }

    @Test
    fun `restauracion con URI pendiente conserva preview`() = runTest(dispatcher.testDispatcher) {
        val handle = SavedStateHandle(mapOf("pending_uri" to "content://pending.jpg"))
        val vm = ProductAdminViewModel(SupabaseCatalogAdminRepository(FakeRemote(), FakeImg()), store(), ImageProcessor(), ctx(), handle)
        advanceUntilIdle()
        // debe cargar URI pendiente como READY si no hay procesamiento
        assertEquals("content://pending.jpg", vm.uiState.value.pendingPhotoUri)
    }

    @Test
    fun `error UI no contiene secretos`() = runTest(dispatcher.testDispatcher) {
        val remote = FakeRemote(failProduct = true)
        val vm = vm(remote, FakeImg())
        advanceUntilIdle()
        vm.onInternalCodeChanged("A")
        vm.saveProduct()
        advanceUntilIdle()
        val combined = listOfNotNull(vm.uiState.value.saveError, vm.uiState.value.photoError, vm.uiState.value.imageUploadError).joinToString()
        assertFalse(combined.contains("Bearer", true))
        assertFalse(combined.contains("apikey", true))
        assertFalse(combined.contains("http", true))
    }

    private class FakeRemote(val failProduct: Boolean = false) : CatalogAdminRemoteDataSource {
        override suspend fun loadCategoriesRaw() = listOf(RemoteCategoryDto("11111111-1111-4111-8111-111111111111", "Cat", true))
        override suspend fun upsertCategory(name: String, description: String?) = RemoteCategoryDto("11111111-1111-4111-8111-111111111111", name, true)
        override suspend fun upsertProduct(id: String?, internalCode: String, barcode: String?, commonName: String, scientificName: String?, description: String, categoryId: String, priceCents: Long, wholesalePriceCents: Long?, unit: String, minimumStock: Double, wateringAdvice: String, lightType: String, recommendedClimate: String, isActive: Boolean): String {
            if (failProduct) throw IllegalStateException("fail")
            return """{"id":"99999999-9999-4999-8999-999999999999","internal_code":"$internalCode","common_name":"$commonName","category_id":"$categoryId","price_cents":$priceCents,"unit":"$unit","minimum_stock":$minimumStock,"is_active":$isActive,"created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}"""
        }
    }
    private open class FakeImg(var failUpload: Boolean = false) : CatalogImageRemoteDataSource {
        var uploadCount = 0; var insertCount = 0; var primaryCount = 0
        override suspend fun uploadImage(productId: String, imageId: String, bytes: ByteArray, mimeType: String): String {
            uploadCount++
            if (failUpload) throw IllegalStateException("upload fail")
            return "$productId/$imageId.jpg"
        }
        override suspend fun insertProductImage(productId: String, imageId: String, storagePath: String): String { insertCount++; return imageId }
        override suspend fun setPrimaryImage(imageId: String) { primaryCount++ }
        override suspend fun deleteProductImage(imageId: String) {}
    }
    private class CountingPrimaryFake : FakeImg() { val primaryIds = mutableListOf<String>(); override suspend fun setPrimaryImage(imageId: String) { super.setPrimaryImage(imageId); primaryIds.add(imageId) } }
}
