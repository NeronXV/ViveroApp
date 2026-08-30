package com.intutec.viveroapp.feature.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryItem
import com.intutec.viveroapp.feature.inventory.domain.model.InventoryMovement
import com.intutec.viveroapp.feature.inventory.presentation.InventoryAction
import com.intutec.viveroapp.feature.inventory.presentation.InventoryScreen
import com.intutec.viveroapp.feature.inventory.presentation.InventoryUiState
import com.intutec.viveroapp.ui.theme.ViveroAppTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class InventoryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loadingStateShowsIndicator() {
        setScreen(InventoryUiState(isLoading = true))
        composeRule.onNodeWithTag("inventory_loading").assertIsDisplayed()
    }

    @Test
    fun errorStateShowsMessageAndRetry() {
        var retried = false
        setScreen(InventoryUiState(isLoading = false, error = "Falla de red"), onRetry = { retried = true })
        
        composeRule.onNodeWithTag("inventory_error").assertIsDisplayed()
        composeRule.onNodeWithText("Falla de red").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory_retry").performClick()
        
        assert(retried)
    }

    @Test
    fun productListDisplaysItems() {
        val item = testItem("1", "Monstera", "PL-001")
        setScreen(InventoryUiState(isLoading = false, items = listOf(item)))

        composeRule.onNodeWithTag("inventory_list").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory_card_1").assertIsDisplayed()
        composeRule.onNodeWithText("Monstera").assertIsDisplayed()
        composeRule.onNodeWithText("PL-001").assertIsDisplayed()
    }

    @Test
    fun lowStockShowsAlertText() {
        val item = testItem("1", "Lavanda", "PL-002", quantity = 2, minimum = 10, isLow = true)
        setScreen(InventoryUiState(isLoading = false, items = listOf(item)))

        composeRule.onNodeWithText("Existencia baja · mínimo 10").assertIsDisplayed()
    }

    @Test
    fun searchFiltersVisibleItems() {
        val items = listOf(testItem("1", "Rosa", "PL-001"), testItem("2", "Girasol", "PL-002"))
        setScreen(InventoryUiState(isLoading = false, items = items, query = "gira"))

        composeRule.onNodeWithTag("inventory_card_2").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory_card_1").assertDoesNotExist()
    }

    @Test
    fun openingReceptionShowsDialog() {
        val item = testItem("1", "Rosa", "PL-001")
        setScreen(InventoryUiState(isLoading = false, items = listOf(item), selectedItem = item, action = InventoryAction.RECEPTION))

        composeRule.onNodeWithTag("inventory_op_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Registrar recepción").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory_quantity_input").assertIsDisplayed()
    }

    @Test
    fun openingCountShowsDialogWithCurrentStock() {
        val item = testItem("1", "Rosa", "PL-001", quantity = 15)
        setScreen(InventoryUiState(isLoading = false, items = listOf(item), selectedItem = item, action = InventoryAction.COUNT, quantityInput = "15"))

        composeRule.onNodeWithTag("inventory_op_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Conciliar conteo").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory_quantity_input").assertIsDisplayed()
        composeRule.onNodeWithText("15").assertIsDisplayed()
    }

    @Test
    fun historyDialogShowsMovements() {
        val item = testItem("1", "Rosa", "PL-001")
        val history = listOf(
            InventoryMovement("m1", "1", "Rosa", "PL-001", "RECEPTION", 10, "Llegada", Instant.now(), "Admin")
        )
        setScreen(InventoryUiState(isLoading = false, items = listOf(item), selectedItem = item, history = history))

        composeRule.onNodeWithTag("inventory_history_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory_history_list").assertIsDisplayed()
        composeRule.onNodeWithText("Recepción · +10").assertIsDisplayed()
        composeRule.onNodeWithText("Llegada").assertIsDisplayed()
    }

    @Test
    fun submissionStateDisablesButtons() {
        val item = testItem("1", "Rosa", "PL-001")
        setScreen(InventoryUiState(isLoading = false, items = listOf(item), selectedItem = item, action = InventoryAction.RECEPTION, isSubmitting = true))

        composeRule.onNodeWithTag("inventory_confirm_btn").assertIsNotEnabled()
        composeRule.onNodeWithTag("inventory_cancel_btn").assertIsNotEnabled()
    }

    @Test
    fun searchInteractionUpdatesQuery() {
        var queryInput = ""
        setScreen(InventoryUiState(isLoading = false), onQueryChanged = { queryInput = it })

        composeRule.onNodeWithTag("inventory_search").performTextInput("cactus")
        assert(queryInput == "cactus")
    }

    @Test
    fun openingHistoryShowsLoadingThenList() {
        val item = testItem("1", "Rosa", "PL-001")
        setScreen(InventoryUiState(isLoading = false, items = listOf(item), selectedItem = item, isHistoryLoading = true))
        composeRule.onNodeWithTag("inventory_history_loading").assertIsDisplayed()
    }

    private fun setScreen(
        state: InventoryUiState,
        onRetry: () -> Unit = {},
        onQueryChanged: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            ViveroAppTheme(darkTheme = false) {
                InventoryScreen(
                    state = state,
                    onBack = {},
                    onRetry = onRetry,
                    onQueryChanged = onQueryChanged,
                    onOpenAction = { _, _ -> },
                    onOpenHistory = { },
                    onQuantityChanged = { },
                    onDetailChanged = { },
                    onSubmit = { },
                    onCloseDialog = { },
                    onCloseHistory = { },
                    onDismissMessage = { },
                )
            }
        }
    }

    private fun testItem(
        id: String,
        name: String,
        code: String,
        quantity: Int = 10,
        minimum: Int = 5,
        isLow: Boolean = false
    ) = InventoryItem(
        productId = id,
        productName = name,
        productCode = code,
        productUnit = "piezas",
        totalQuantity = quantity,
        minimumStock = minimum,
        isLowStock = isLow,
    )
}
