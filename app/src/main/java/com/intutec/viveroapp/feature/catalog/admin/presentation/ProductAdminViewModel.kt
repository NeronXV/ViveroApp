package com.intutec.viveroapp.feature.catalog.admin.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.media.ImageProcessor
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.catalog.domain.model.Category
import com.intutec.viveroapp.feature.catalog.domain.repository.CatalogAdminRepository
import com.intutec.viveroapp.feature.catalog.domain.repository.ProductUpsertRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProductAdminMode { CREATE, EDIT }

enum class ProductAdminSessionGate {
    WAITING_SESSION,
    NO_SESSION,
    ACCESS_DENIED,
    READY,
}

enum class PhotoUiState {
    NONE, SELECTING, PREPARING, READY, SAVING_PRODUCT, UPLOADING, SUCCESS, ERROR
}

data class ProductAdminUiState(
    val sessionGate: ProductAdminSessionGate = ProductAdminSessionGate.WAITING_SESSION,
    val isLoadingCategories: Boolean = false,
    val categories: List<Category> = emptyList(),
    val categoriesError: String? = null,
    val internalCode: String = "",
    val barcode: String = "",
    val commonName: String = "",
    val scientificName: String = "",
    val selectedCategoryId: String? = null,
    val description: String = "",
    val priceInput: String = "",
    val wholesalePriceInput: String = "",
    val unit: String = "pieza",
    val minimumStockInput: String = "0",
    val wateringAdvice: String = "",
    val lightType: String = "",
    val recommendedClimate: String = "",
    val isActive: Boolean = true,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedProductId: String? = null,
    val savedProductName: String? = null,
    val branchName: String = "",
    val canManageProducts: Boolean = false,
    val canManagePrices: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isCreatingCategory: Boolean = false,
    val newCategoryName: String = "",
    val newCategoryError: String? = null,
    // Foto
    val photoState: PhotoUiState = PhotoUiState.NONE,
    val pendingPhotoUri: String? = null,
    val pendingPhotoBytes: ByteArray? = null,
    val pendingPhotoMime: String = "image/jpeg",
    val photoError: String? = null,
    val isProcessingPhoto: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val imageUploadError: String? = null,
    val pendingImageId: String? = null,
    val existingImagePath: String? = null, // para edición, ruta actual principal
    val editingProductId: String? = null,
)

@HiltViewModel
class ProductAdminViewModel @Inject constructor(
    private val repository: CatalogAdminRepository,
    private val sessionStore: SessionStore,
    private val imageProcessor: ImageProcessor,
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProductAdminUiState(
            pendingPhotoUri = savedStateHandle.get<String>("pending_uri"),
            photoState = if (savedStateHandle.get<String>("pending_uri") != null) PhotoUiState.READY else PhotoUiState.NONE,
        ),
    )
    val uiState: StateFlow<ProductAdminUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var lastUserId: String? = null
    private var uploadJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.session
                .map { it?.userId }
                .distinctUntilChanged()
                .collect { _ -> handleSessionChange() }
        }
        viewModelScope.launch {
            sessionStore.session.collect { session ->
                val gate = when {
                    session == null -> ProductAdminSessionGate.WAITING_SESSION
                    !session.hasCapability(AppPermission.MANAGE_PRODUCTS) -> ProductAdminSessionGate.ACCESS_DENIED
                    else -> ProductAdminSessionGate.READY
                }
                _uiState.update {
                    it.copy(
                        sessionGate = gate,
                        canManageProducts = session?.hasCapability(AppPermission.MANAGE_PRODUCTS) == true,
                        canManagePrices = session?.hasCapability(AppPermission.MANAGE_PRICES) == true,
                        branchName = session?.branch?.name ?: session?.branchName ?: "",
                    )
                }
            }
        }
    }

    private fun handleSessionChange() {
        val session = sessionStore.session.value
        val currentId = session?.userId
        if (currentId != lastUserId) {
            loadJob?.cancel()
            lastUserId = currentId
            _uiState.update { it.copy(categories = emptyList(), categoriesError = null, isLoadingCategories = false, selectedCategoryId = null, saveError = null) }
        }
        if (session != null && session.hasCapability(AppPermission.MANAGE_PRODUCTS)) {
            loadCategoriesOnce()
        } else if (session == null) {
            // No consultar categorías como anónimo; esperar sesión autenticada real
            _uiState.update { it.copy(isLoadingCategories = false) }
        } else {
            // ACCESS_DENIED: no consultar
            _uiState.update { it.copy(isLoadingCategories = false) }
        }
    }

    fun loadCategories() {
        val session = sessionStore.session.value
        if (session == null || !session.hasCapability(AppPermission.MANAGE_PRODUCTS)) {
            _uiState.update { it.copy(categoriesError = safeAccessDenied()) }
            return
        }
        loadCategoriesOnce()
    }

    private fun loadCategoriesOnce() {
        if (loadJob?.isActive == true) return
        // Solo una carga cuando READY; evita reintentos anónimos
        if (_uiState.value.sessionGate != ProductAdminSessionGate.READY && _uiState.value.sessionGate != ProductAdminSessionGate.WAITING_SESSION) {
            // Si es ACCESS_DENIED no cargar
            if (_uiState.value.sessionGate == ProductAdminSessionGate.ACCESS_DENIED) return
        }
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true, categoriesError = null) }
            repository.loadCategories().fold(
                onSuccess = { cats -> _uiState.update { it.copy(isLoadingCategories = false, categories = cats) } },
                onFailure = { e ->
                    val safe = e.toSafeMessage()
                    _uiState.update { it.copy(isLoadingCategories = false, categoriesError = safe) }
                },
            )
        }
    }

    fun onInternalCodeChanged(v: String) = _uiState.update { it.copy(internalCode = v.uppercase(), fieldErrors = it.fieldErrors - "internalCode") }
    fun onBarcodeChanged(v: String) = _uiState.update { it.copy(barcode = v) }
    fun onCommonNameChanged(v: String) = _uiState.update { it.copy(commonName = v, fieldErrors = it.fieldErrors - "commonName") }
    fun onScientificNameChanged(v: String) = _uiState.update { it.copy(scientificName = v) }
    fun onCategorySelected(id: String?) = _uiState.update { it.copy(selectedCategoryId = id, fieldErrors = it.fieldErrors - "category") }
    fun onDescriptionChanged(v: String) = _uiState.update { it.copy(description = v) }
    fun onPriceChanged(v: String) = _uiState.update { it.copy(priceInput = v.filter { c -> c.isDigit() || c == '.' || c == ',' }, fieldErrors = it.fieldErrors - "price") }
    fun onWholesalePriceChanged(v: String) = _uiState.update { it.copy(wholesalePriceInput = v.filter { c -> c.isDigit() || c == '.' || c == ',' }) }
    fun onUnitChanged(v: String) = _uiState.update { it.copy(unit = v, fieldErrors = it.fieldErrors - "unit") }
    fun onMinimumStockChanged(v: String) { if (v.all(Char::isDigit) && v.length <= 9) _uiState.update { it.copy(minimumStockInput = v, fieldErrors = it.fieldErrors - "minimumStock") } }
    fun onWateringChanged(v: String) = _uiState.update { it.copy(wateringAdvice = v) }
    fun onLightChanged(v: String) = _uiState.update { it.copy(lightType = v) }
    fun onClimateChanged(v: String) = _uiState.update { it.copy(recommendedClimate = v) }
    fun onActiveChanged(v: Boolean) = _uiState.update { it.copy(isActive = v) }

    fun onNewCategoryNameChanged(v: String) = _uiState.update { it.copy(newCategoryName = v, newCategoryError = null) }

    fun createCategory() = viewModelScope.launch {
        val session = sessionStore.session.value
        if (session == null || !session.hasCapability(AppPermission.MANAGE_PRODUCTS)) {
            _uiState.update { it.copy(newCategoryError = safeAccessDenied()) }
            return@launch
        }
        val name = _uiState.value.newCategoryName.trim()
        if (name.length !in 2..100) {
            _uiState.update { it.copy(newCategoryError = "El nombre debe tener 2 a 100 caracteres.") }
            return@launch
        }
        _uiState.update { it.copy(isCreatingCategory = true, newCategoryError = null) }
        repository.upsertCategory(name).fold(
            onSuccess = { cat ->
                _uiState.update { it.copy(isCreatingCategory = false, newCategoryName = "", selectedCategoryId = cat.id) }
                loadCategories()
            },
            onFailure = { e -> _uiState.update { it.copy(isCreatingCategory = false, newCategoryError = e.toSafeMessage()) } },
        )
    }

    // Fotografía
    fun onPhotoUriSelected(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        val session = sessionStore.session.value
        if (session == null || !session.hasCapability(AppPermission.MANAGE_PRODUCTS)) {
            _uiState.update { it.copy(photoError = safeAccessDenied()) }
            return
        }
        savedStateHandle["pending_uri"] = uriString
        _uiState.update { it.copy(pendingPhotoUri = uriString, photoState = PhotoUiState.PREPARING, isProcessingPhoto = true, photoError = null, imageUploadError = null) }
        viewModelScope.launch {
            val result = imageProcessor.process(appContext, Uri.parse(uriString))
            result.fold(
                onSuccess = { processed ->
                    _uiState.update { it.copy(pendingPhotoBytes = processed.bytes, pendingPhotoMime = processed.mimeType, photoState = PhotoUiState.READY, isProcessingPhoto = false, pendingImageId = UUID.randomUUID().toString()) }
                },
                onFailure = { e ->
                    val msg = when {
                        e.message?.contains("Formato", true) == true -> "Formato no compatible. Usa JPEG, PNG, WEBP o AVIF."
                        e.message?.contains("grande", true) == true -> "Archivo demasiado grande. Máximo 5 MB."
                        else -> "No pudimos preparar la imagen."
                    }
                    _uiState.update { it.copy(photoError = msg, photoState = PhotoUiState.ERROR, isProcessingPhoto = false, pendingPhotoUri = null, pendingPhotoBytes = null) }
                    savedStateHandle["pending_uri"] = null
                },
            )
        }
    }

    fun onRemovePhoto() {
        _uiState.update { it.copy(pendingPhotoUri = null, pendingPhotoBytes = null, photoState = PhotoUiState.NONE, photoError = null, imageUploadError = null, pendingImageId = null) }
        savedStateHandle["pending_uri"] = null
        // No borra archivos de galería; limpia temp cache si existe
        viewModelScope.launch {
            try { appContext.cacheDir.resolve("product_images").deleteRecursively() } catch (_: Exception) {}
        }
    }

    fun onCameraPhotoTaken(success: Boolean, uriString: String?) {
        if (!success) {
            _uiState.update { it.copy(photoError = "No se pudo capturar la fotografía.", photoState = PhotoUiState.ERROR) }
            return
        }
        if (uriString != null) onPhotoUriSelected(uriString)
    }

    fun onCameraPermissionDenied() {
        _uiState.update {
            it.copy(
                photoError = "Permite el acceso a la cámara para tomar la fotografía. También puedes elegir una imagen.",
                photoState = PhotoUiState.ERROR,
            )
        }
    }

    fun onCameraLaunchFailed() {
        _uiState.update {
            it.copy(
                photoError = "No pudimos abrir la cámara. Intenta de nuevo o elige una imagen.",
                photoState = PhotoUiState.ERROR,
            )
        }
    }

    fun loadForEdit(productId: String, existingImagePath: String?) {
        _uiState.update { it.copy(editingProductId = productId, existingImagePath = existingImagePath) }
    }

    fun retryImageUpload() {
        val state = _uiState.value
        val productId = state.savedProductId ?: state.editingProductId ?: return
        val bytes = state.pendingPhotoBytes ?: return
        val imageId = state.pendingImageId ?: UUID.randomUUID().toString()
        if (state.isUploadingPhoto) return
        uploadImage(productId, imageId, bytes, state.pendingPhotoMime)
    }

    private fun uploadImage(productId: String, imageId: String, bytes: ByteArray, mime: String) {
        if (uploadJob?.isActive == true) return
        uploadJob = viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, photoState = PhotoUiState.UPLOADING, imageUploadError = null) }
            repository.uploadAndRegisterImage(productId, imageId, bytes, mime).fold(
                onSuccess = {
                    _uiState.update { it.copy(isUploadingPhoto = false, photoState = PhotoUiState.SUCCESS, imageUploadError = null) }
                    // Limpiar temp tras éxito, mantener preview
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isUploadingPhoto = false, photoState = PhotoUiState.ERROR, imageUploadError = e.toSafeMessage()) }
                },
            )
        }
    }

    fun saveProduct() {
        if (_uiState.value.isSaving) return
        val s = _uiState.value
        val errors = mutableMapOf<String, String>()
        if (s.internalCode.trim().length !in 2..40) errors["internalCode"] = "Código 2 a 40 caracteres."
        if (s.commonName.trim().length !in 2..160) errors["commonName"] = "Nombre 2 a 160 caracteres."
        if (s.selectedCategoryId.isNullOrBlank()) errors["category"] = "Selecciona una categoría."
        if (s.unit !in VALID_UNITS) errors["unit"] = "Unidad inválida."
        val priceCents = s.priceInput.parsePriceCents()
        if (priceCents == null || priceCents < 0) errors["price"] = "Precio inválido."
        else if (!s.canManagePrices) errors["price"] = "No tienes permiso para definir precios (MANAGE_PRICES)."
        val minimumStock = s.minimumStockInput.toIntOrNull()
        if (minimumStock == null || minimumStock < 0) errors["minimumStock"] = "Mínimo inválido."
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, saveError = "Revisa los campos marcados.") }
            return
        }
        val wholesaleCents = s.wholesalePriceInput.trim().takeIf(String::isNotEmpty)?.parsePriceCents()
        if (s.wholesalePriceInput.isNotBlank() && wholesaleCents == null) {
            _uiState.update { it.copy(fieldErrors = mapOf("wholesalePrice" to "Precio mayoreo inválido.")) }
            return
        }
        val request = ProductUpsertRequest(
            id = s.editingProductId,
            internalCode = s.internalCode.trim().uppercase(),
            barcode = s.barcode.trim().takeIf(String::isNotEmpty),
            commonName = s.commonName.trim(),
            scientificName = s.scientificName.trim().takeIf(String::isNotEmpty),
            description = s.description.trim(),
            categoryId = checkNotNull(s.selectedCategoryId),
            priceCents = checkNotNull(priceCents),
            wholesalePriceCents = wholesaleCents,
            unit = s.unit,
            minimumStock = checkNotNull(minimumStock),
            wateringAdvice = s.wateringAdvice.trim(),
            lightType = s.lightType.trim(),
            recommendedClimate = s.recommendedClimate.trim(),
            isActive = s.isActive,
        )
        val session = sessionStore.session.value
        if (session == null) {
            _uiState.update { it.copy(saveError = safeSessionExpired()) }
            return
        }
        if (!session.hasCapability(AppPermission.MANAGE_PRODUCTS)) {
            _uiState.update { it.copy(saveError = safeAccessDenied()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null, fieldErrors = emptyMap(), photoState = if (_uiState.value.pendingPhotoBytes != null) PhotoUiState.SAVING_PRODUCT else _uiState.value.photoState) }
            repository.upsertProduct(request).fold(
                onSuccess = { product ->
                    val hasPhoto = _uiState.value.pendingPhotoBytes != null
                    _uiState.update { it.copy(isSaving = false, savedProductId = product.id, savedProductName = product.commonName, editingProductId = product.id) }
                    if (hasPhoto) {
                        val bytes = _uiState.value.pendingPhotoBytes!!
                        val mime = _uiState.value.pendingPhotoMime
                        val imageId = _uiState.value.pendingImageId ?: UUID.randomUUID().toString().also { id -> _uiState.update { s -> s.copy(pendingImageId = id) } }
                        uploadImage(product.id, imageId, bytes, mime)
                    } else {
                        _uiState.update { it.copy(photoState = PhotoUiState.SUCCESS) }
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSaving = false, saveError = e.toSafeMessage()) }
                },
            )
        }
    }

    // Test helper for photo without needing ContentResolver
    fun setPendingPhotoForTest(bytes: ByteArray, uri: String = "content://test/photo.jpg") {
        _uiState.update { it.copy(pendingPhotoUri = uri, pendingPhotoBytes = bytes, pendingPhotoMime = "image/jpeg", photoState = PhotoUiState.READY, pendingImageId = it.pendingImageId ?: UUID.randomUUID().toString(), photoError = null) }
        savedStateHandle["pending_uri"] = uri
    }

    fun clearSavedState() = _uiState.update { it.copy(savedProductId = null, savedProductName = null) }
    fun dismissError() = _uiState.update { it.copy(saveError = null, categoriesError = null) }
    fun retryCategories() = loadCategories()

    companion object {
        val VALID_UNITS = setOf("pieza", "maceta", "charola", "bolsa", "kg")
    }
}

private fun String.parsePriceCents(): Long? {
    val normalized = trim().replace(",", ".").replace(" ", "")
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    if (value < 0 || value > 9999999) return null
    return (value * 100).toLong()
}

private fun safeAccessDenied(): String = "No tienes permiso para administrar productos."
private fun safeSessionExpired(): String = "Tu sesión expiró. Vuelve a iniciar sesión."
private fun Throwable.toSafeMessage(): String {
    val raw = message ?: ""
    val code = (this as? io.github.jan.supabase.postgrest.exception.PostgrestRestException)?.code
    val status = (this as? io.github.jan.supabase.postgrest.exception.PostgrestRestException)?.statusCode
    return when {
        code == "42501" || status == 401 || status == 403 || raw.contains("permission denied", true) || raw.contains("42501") -> "No tienes permiso para administrar productos."
        raw.contains("MANAGE_PRICES", true) || raw.contains("Price management", true) -> "No tienes permiso para definir precios (MANAGE_PRICES)."
        raw.contains("already in use", true) || raw.contains("23505") -> "El código interno o de barras ya está en uso."
        raw.contains("JWT", true) || raw.contains("expired", true) || raw.contains("InvalidStoredSession", true) -> safeSessionExpired()
        else -> "No pudimos completar la operación. Intenta nuevamente."
    }
}
private fun Throwable.userMessage(): String = toSafeMessage()
