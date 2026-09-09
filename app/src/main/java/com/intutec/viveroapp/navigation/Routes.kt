package com.intutec.viveroapp.navigation

import kotlinx.serialization.Serializable

@Serializable data object SplashRoute
@Serializable data object LoginRoute
@Serializable data object PasswordResetRoute
@Serializable data object HomeRoute
@Serializable data object CatalogRoute
@Serializable data class ProductDetailRoute(val productId: String)
@Serializable data object ScannerPreviewRoute
@Serializable data object CartRoute
@Serializable data object ProfileRoute
@Serializable data class CashierQueueRoute(val completedFolio: String? = null)
@Serializable data class CashierDetailRoute(val orderId: String)
@Serializable data class InventoryRoute(
    val productId: String? = null,
    val initialAction: String? = null,
)
@Serializable data object ReportsRoute
@Serializable data object MySalesRoute
@Serializable data object ProductAdminRoute
@Serializable data object StaffRoute
