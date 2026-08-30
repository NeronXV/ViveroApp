package com.intutec.viveroapp.feature.catalog.data.remote

interface CatalogImageRemoteDataSource {
    suspend fun uploadImage(productId: String, imageId: String, bytes: ByteArray, mimeType: String): String
    suspend fun insertProductImage(productId: String, imageId: String, storagePath: String): String
    suspend fun setPrimaryImage(imageId: String)
    suspend fun deleteProductImage(imageId: String)
}
