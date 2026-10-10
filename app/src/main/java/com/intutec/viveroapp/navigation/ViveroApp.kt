package com.intutec.viveroapp.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.intutec.viveroapp.BuildConfig
import com.intutec.viveroapp.core.network.backendApiOrigin
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.auth.presentation.*
import com.intutec.viveroapp.feature.catalog.presentation.BackendCatalogScreen
import com.intutec.viveroapp.feature.cart.presentation.BackendCartScreen
import com.intutec.viveroapp.feature.cashier.presentation.BackendCashierScreen
import com.intutec.viveroapp.feature.mysales.presentation.BackendHistoryScreen
import com.intutec.viveroapp.feature.inventory.presentation.BackendInventoryScreen
import com.intutec.viveroapp.feature.inventory.domain.repository.inventorySession
import com.intutec.viveroapp.feature.splash.presentation.SplashScreen
import com.intutec.viveroapp.feature.home.presentation.BackendHomeScreen

@Composable
fun ViveroApp(authViewModel: BackendAuthViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val auth by authViewModel.uiState.collectAsStateWithLifecycle()
    val passwordChange by authViewModel.passwordChange.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, authViewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) authViewModel.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(auth.status) {
        when (auth.status) {
            AuthStatus.AUTHENTICATED -> navController.navigate(HomeRoute) { popUpTo(navController.graph.id) { inclusive = true }; launchSingleTop = true }
            AuthStatus.SIGNED_OUT -> navController.navigate(LoginRoute) { popUpTo(navController.graph.id) { inclusive = true }; launchSingleTop = true }
            else -> Unit
        }
    }
    if (auth.status == AuthStatus.AUTHENTICATED && auth.backend.accessStatus != BackendAccessStatus.READY) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Consulta tus permisos para continuar.")
            if (auth.backend.accessStatus == BackendAccessStatus.LOADING) CircularProgressIndicator()
            auth.errorMessage?.let { Text(it) }
            Button(authViewModel::refresh) { Text("Actualizar permisos") }
            TextButton(authViewModel::signOut) { Text("Cerrar sesión") }
        }
        return
    }
    NavHost(navController, startDestination = SplashRoute) {
        composable<SplashRoute> { SplashScreen() }
        composable<LoginRoute> { LoginScreen(auth, authViewModel::onEmailChanged, authViewModel::onPasswordChanged,
            authViewModel::togglePasswordVisibility, authViewModel::signIn, { authViewModel.clearMessages(); navController.navigate(PasswordResetRoute) }) }
        composable<PasswordResetRoute> { PasswordResetScreen(auth, authViewModel::onEmailChanged, authViewModel::sendPasswordReset, { navController.navigateUp() }) }
        composable<HomeRoute> {
            val context = LocalContext.current
            val web = remember { runCatching { backendApiOrigin(BuildConfig.BACKEND_WEB_URL, BuildConfig.DEBUG) }.getOrNull() }
            BackendHomeScreen(
                session = auth.backend,
                onCatalog = { navController.navigate(CatalogRoute) },
                onCart = { navController.navigate(CartRoute) },
                onCashier = { navController.navigate(CashierQueueRoute()) },
                onInventory = { navController.navigate(InventoryRoute()) },
                onHistory = { navController.navigate(BackendHistoryRoute()) },
                onWeb = { web?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
                webAvailable = web != null,
                onRefresh = authViewModel::refresh,
                onSignOut = authViewModel::signOut,
                passwordChange = passwordChange,
                onChangePassword = authViewModel::changePassword,
                onClearPasswordChange = authViewModel::clearPasswordChange,
            )
        }
        composable<CatalogRoute> { if (auth.backend.authorizedSession("VIEW_CATALOG") != null) BackendCatalogScreen({ navController.navigateUp() }, { navController.navigate(CartRoute) }) else AccessUnavailable { navController.navigate(HomeRoute) } }
        composable<CartRoute> { if (auth.backend.authorizedSession("CREATE_SALES", true) != null) BackendCartScreen({ navController.navigateUp() }, { navController.navigate(CatalogRoute) { popUpTo<CartRoute> { inclusive = true }; launchSingleTop = true } }, { navController.navigate(BackendHistoryRoute()) }) else AccessUnavailable { navController.navigate(HomeRoute) } }
        composable<CashierQueueRoute> { if (auth.backend.authorizedSession("OPERATE_CASHIER", true) != null) BackendCashierScreen({ navController.navigateUp() }, { navController.navigate(BackendHistoryRoute("PAYMENTS")) }, { navController.navigate(BackendHistoryRoute("QUEUE", it)) }) else AccessUnavailable { navController.navigate(HomeRoute) } }
        composable<BackendHistoryRoute> { entry ->
            val route = entry.toRoute<BackendHistoryRoute>()
            val permitted = if (route.kind == "SALES") auth.backend.authorizedSession("VIEW_OWN_SALES", true) != null && auth.backend.authorizedSession("CREATE_SALES", true) != null else auth.backend.authorizedSession("OPERATE_CASHIER", true) != null
            if (permitted) BackendHistoryScreen(onBack = { navController.navigateUp() }) else AccessUnavailable { navController.navigate(HomeRoute) }
        }
        composable<InventoryRoute> { if (auth.backend.inventorySession() != null) BackendInventoryScreen(onBack = { navController.navigateUp() }) else AccessUnavailable { navController.navigate(HomeRoute) } }
    }
}

@Composable
private fun AccessUnavailable(onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tus permisos actuales no permiten abrir este módulo. Los intentos guardados se conservan.")
        Button(onHome) { Text("Volver al inicio") }
    }
}
