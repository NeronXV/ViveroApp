package com.intutec.viveroapp.feature.plants.domain

import kotlinx.serialization.Serializable

@Serializable
data class PlantWork(
    val id: Long = 0, val product: Boolean = false, val name: String = "", val presentation: String = "",
    val categoryId: Long? = null, val price: String = "", val quantity: String = "", val revision: Int = 1,
    val hasImage: Boolean = false, val photoPath: String? = null, val status: String = "DRAFT",
    val description: String = "", val code: String = "", val linkProductId: Long? = null,
    val needsInitialCount: Boolean = false, val readyForSale: Boolean = false, val imageId: Long? = null,
    val fromReceipt: Boolean = false, val baselineMovementId: Long? = null,
)
data class PlantCategory(val id: Long, val name: String)
data class PlantList(val plants: List<PlantWork>, val categories: List<PlantCategory>, val catalogProducts: List<PlantWork> = emptyList(), val canDirect: Boolean = false)
interface PlantGateway {
    suspend fun list(): PlantList
    suspend fun open(plant: PlantWork): PlantWork
    suspend fun preview(imageId: Long): ByteArray? = null
    suspend fun preview(plant: PlantWork): ByteArray? = plant.imageId?.let { preview(it) }
    suspend fun save(plant: PlantWork, publish: Boolean): PlantWork
    suspend fun recover(): PlantWork?
    suspend fun hasPending(): Boolean
    suspend fun remember(plant: PlantWork?)
    suspend fun remembered(): PlantWork?
    suspend fun count(plant: PlantWork, quantity: String, reason: String): PlantWork = error("Conteo no disponible")
    suspend fun history(productId: Long? = null): List<String> = emptyList()
}
fun plantCents(value: String): Long? {
    if(value.isBlank()) return null
    require(Regex("[0-9]+(\\.[0-9]{1,2})?").matches(value.trim())) { "Revisa el precio." }
    val cents=value.trim().toBigDecimal().movePointRight(2).longValueExact()
    require(cents in 1..9007199254740991L) { "El precio debe ser mayor que cero." }
    return cents
}
fun plantQuantity(value: String): Long? {
    if(value.isBlank())return null
    require(Regex("[0-9]+").matches(value)) { "La existencia debe ser un número entero." }
    return value.toLong().also { require(it in 0..1000000) { "Revisa la existencia." } }
}
