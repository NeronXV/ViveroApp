package com.intutec.viveroapp.feature.catalog.data.remote

interface CatalogRemoteDataSource {
    suspend fun loadVisibleCategories(): List<RemoteCategoryDto>
    suspend fun loadActiveProducts(): List<RemoteProductDto>
}
