package com.intutec.viveroapp.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.intutec.viveroapp.core.common.UiState
import com.intutec.viveroapp.core.model.UserRole
import com.intutec.viveroapp.core.session.SessionMode
import com.intutec.viveroapp.feature.cart.domain.model.Cart
import com.intutec.viveroapp.feature.cart.domain.model.CartItem
import com.intutec.viveroapp.feature.home.domain.model.Dashboard
import com.intutec.viveroapp.feature.home.domain.model.DashboardModule
import com.intutec.viveroapp.feature.home.presentation.HomeContent
import com.intutec.viveroapp.feature.home.presentation.HomeScreen
import com.intutec.viveroapp.ui.theme.ViveroAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun ownerWithEmptyCartShowsNewSaleAndOpensCatalog() {
        var catalogOpens = 0
        setHome(dashboard = dashboard(UserRole.OWNER, canCreateSales = true), cart = Cart(), onCatalog = { catalogOpens++ })

        composeRule.onNodeWithText("Nueva venta").assertIsDisplayed()
        composeRule.onNodeWithText("Comenzar").performClick()

        assertEquals(1, catalogOpens)
    }

    @Test
    fun ownerWithExistingCartShowsRoomQuantityAndTotalAndOpensSameCart() {
        var cartOpens = 0
        setHome(dashboard = dashboard(UserRole.OWNER, canCreateSales = true), cart = cart(), onCart = { cartOpens++ })

        composeRule.onNodeWithText("Continuar venta").assertIsDisplayed()
        composeRule.onNodeWithText("2 artículos  ·  $200.00").assertIsDisplayed()
        composeRule.onNodeWithText("Abrir carrito").performClick()

        assertEquals(1, cartOpens)
    }

    @Test
    fun salesWithCartOnlySeesRealAvailableActions() {
        setHome(dashboard = dashboard(UserRole.SALES, canCreateSales = true), cart = cart())

        composeRule.onNodeWithText("Ventas  ·  Sucursal Centro").assertIsDisplayed()
        composeRule.onNodeWithTag("home_module_catalog").assertIsDisplayed()
        composeRule.onNodeWithTag("home_module_cart").assertIsDisplayed()
        composeRule.onAllNodesWithText("Resumen operativo").assertCountEquals(0)
        composeRule.onAllNodesWithText("Atención requerida").assertCountEquals(0)
    }

    @Test
    fun userWithoutCreateSalesDoesNotSeeSaleActionOrCart() {
        setHome(dashboard = dashboard(UserRole.CASHIER, canCreateSales = false), cart = Cart())

        composeRule.onAllNodesWithTag("home_primary_action").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_module_cart").assertCountEquals(0)
        composeRule.onNodeWithTag("home_module_catalog").assertIsDisplayed()
    }

    @Test
    fun unavailableMetricsAndPrototypeCopyAreAbsent() {
        setHome(dashboard = dashboard(UserRole.OWNER, canCreateSales = true), cart = Cart())

        listOf("$18,450", "Tickets pendientes", "Stock bajo", "Promociones activas", "VISTA CONCEPTUAL", "Prototipo visual")
            .forEach { text -> composeRule.onAllNodesWithText(text).assertCountEquals(0) }
    }

    @Test
    fun longNameDoesNotPreventActionsFromRendering() {
        setHome(
            dashboard = dashboard(
                UserRole.OWNER,
                canCreateSales = true,
                userName = "Nombre compuesto considerablemente extenso para validar el encabezado",
            ),
            cart = Cart(),
        )

        composeRule.onNodeWithTag("home_primary_action").assertIsDisplayed()
        composeRule.onNodeWithText("Accesos rápidos").assertIsDisplayed()
    }

    @Test
    fun tabletPortraitKeepsPrimaryActionAndQuickAccesses() {
        setHome(dashboard(UserRole.OWNER, true), Cart(), size = 800 to 1280)

        composeRule.onNodeWithTag("home_primary_action").assertIsDisplayed()
        composeRule.onNodeWithTag("home_quick_accesses").assertIsDisplayed()
    }

    @Test
    fun tabletLandscapeKeepsPrimaryActionAndQuickAccesses() {
        setHome(dashboard(UserRole.OWNER, true), Cart(), size = 1280 to 800)

        composeRule.onNodeWithTag("home_primary_action").assertIsDisplayed()
        composeRule.onNodeWithTag("home_quick_accesses").assertIsDisplayed()
    }

    private fun setHome(
        dashboard: Dashboard,
        cart: Cart,
        size: Pair<Int, Int>? = null,
        onCatalog: () -> Unit = {},
        onCart: () -> Unit = {},
    ) {
        composeRule.setContent {
            ViveroAppTheme(darkTheme = false) {
                val content: @Composable () -> Unit = {
                    HomeScreen(
                        state = UiState.Success(HomeContent(dashboard, cart)),
                        onRetry = {},
                        onCatalogClick = onCatalog,
                        onCartClick = onCart,
                        onCashierClick = {},
                        onInventoryClick = {},
                        onProfileClick = {},
                    )
                }
                if (size == null) {
                    content()
                } else {
                    Box(Modifier.requiredSize(size.first.dp, size.second.dp)) { content() }
                }
            }
        }
    }

    private fun dashboard(
        role: UserRole,
        canCreateSales: Boolean,
        userName: String = "Usuario de prueba",
    ) = Dashboard(
        userName = userName,
        role = role,
        branchName = "Sucursal Centro",
        sessionMode = SessionMode.REMOTE,
        canCreateSales = canCreateSales,
        modules = buildList {
            add(DashboardModule("catalog", "Catálogo", "Consulta productos y categorías", true))
            if (canCreateSales) add(DashboardModule("cart", "Carrito actual", "Retoma la comanda guardada", true))
        },
    )

    private fun cart() = Cart(
        items = listOf(
            CartItem(
                productId = "00000000-0000-4000-8000-000000000001",
                internalCode = "TEST-001",
                name = "Producto de prueba",
                imageKey = "",
                unit = "pieza",
                listPriceCents = 10_000,
                unitPriceCents = 10_000,
                quantity = 2,
                stockAvailable = 0,
                stockKnown = false,
            ),
        ),
    )
}
