package com.intutec.viveroapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.intutec.viveroapp.feature.auth.presentation.AuthStatus
import com.intutec.viveroapp.feature.auth.presentation.AuthViewModel
import com.intutec.viveroapp.feature.auth.presentation.LoginScreen
import com.intutec.viveroapp.feature.auth.presentation.PasswordResetScreen
import com.intutec.viveroapp.feature.catalog.presentation.CatalogScreenRoute
import com.intutec.viveroapp.feature.catalog.presentation.ProductDetailScreenRoute
import androidx.navigation.toRoute
import com.intutec.viveroapp.feature.home.presentation.HomeScreenRoute
import com.intutec.viveroapp.feature.profile.presentation.ProfileScreen
import com.intutec.viveroapp.feature.splash.presentation.SplashScreen
import com.intutec.viveroapp.core.designsystem.PlaceholderScreen
import com.intutec.viveroapp.feature.scanner.presentation.ScannerScreenRoute
import com.intutec.viveroapp.feature.cart.presentation.CartScreenRoute
import com.intutec.viveroapp.core.security.AppPermission
import com.intutec.viveroapp.core.security.RolePermissions

@Composable
fun ViveroApp(authViewModel: AuthViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(authState.status) {
        when (authState.status) {
            AuthStatus.AUTHENTICATED -> navController.navigate(HomeRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
            AuthStatus.SIGNED_OUT -> navController.navigate(LoginRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
            AuthStatus.CHECKING, AuthStatus.WORKING -> Unit
        }
    }

    NavHost(navController = navController, startDestination = SplashRoute) {
        composable<SplashRoute> { SplashScreen() }
        composable<LoginRoute> {
            LoginScreen(
                state = authState,
                onEmailChanged = authViewModel::onEmailChanged,
                onPasswordChanged = authViewModel::onPasswordChanged,
                onTogglePassword = authViewModel::togglePasswordVisibility,
                onSignIn = authViewModel::signIn,
                onDemoLogin = authViewModel::signInDemo,
                onForgotPassword = {
                    authViewModel.clearMessages()
                    navController.navigate(PasswordResetRoute)
                },
            )
        }
        composable<PasswordResetRoute> {
            PasswordResetScreen(
                state = authState,
                onEmailChanged = authViewModel::onEmailChanged,
                onSubmit = authViewModel::sendPasswordReset,
                onBack = navController::navigateUp,
            )
        }
        composable<HomeRoute> {
            HomeScreenRoute(
                onCatalogClick = { navController.navigate(CatalogRoute) },
                onCartClick = { navController.navigate(CartRoute) },
                onProfileClick = { navController.navigate(ProfileRoute) },
            )
        }
        composable<CatalogRoute> {
            val role = authState.session?.role
            if (role != null && RolePermissions.can(role, AppPermission.VIEW_CATALOG)) {
                CatalogScreenRoute(
                    onBack = navController::navigateUp,
                    onProductClick = { navController.navigate(ProductDetailRoute(it)) },
                    onScanClick = { navController.navigate(ScannerPreviewRoute) },
                )
            } else {
                PlaceholderScreen("Acceso restringido", "permiso correspondiente", navController::navigateUp)
            }
        }
        composable<ProductDetailRoute> { entry ->
            val route = entry.toRoute<ProductDetailRoute>()
            ProductDetailScreenRoute(
                productId = route.productId,
                onBack = navController::navigateUp,
                onScanClick = { navController.navigate(ScannerPreviewRoute) },
            )
        }
        composable<ScannerPreviewRoute> {
            val role = authState.session?.role
            if (role != null && RolePermissions.can(role, AppPermission.SCAN_PRODUCTS)) {
                ScannerScreenRoute(
                    onBack = navController::navigateUp,
                    onProductDetails = { navController.navigate(ProductDetailRoute(it)) },
                )
            } else {
                PlaceholderScreen("Acceso restringido", "permiso correspondiente", navController::navigateUp)
            }
        }
        composable<CartRoute> {
            val role = authState.session?.role
            if (role != null && RolePermissions.can(role, AppPermission.MANAGE_CART)) {
                CartScreenRoute(
                    onBack = navController::navigateUp,
                    onBrowseCatalog = { navController.navigate(CatalogRoute) },
                )
            } else {
                PlaceholderScreen("Acceso restringido", "permiso correspondiente", navController::navigateUp)
            }
        }
        composable<ProfileRoute> {
            ProfileScreen(
                session = authState.session,
                onBack = navController::navigateUp,
                onSignOut = authViewModel::signOut,
            )
        }
    }
}
