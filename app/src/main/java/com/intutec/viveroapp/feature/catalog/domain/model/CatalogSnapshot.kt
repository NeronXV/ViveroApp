package com.intutec.viveroapp.feature.catalog.domain.model

data class CatalogSnapshot(
    val categories: List<Category>,
    val products: List<Product>,
)
