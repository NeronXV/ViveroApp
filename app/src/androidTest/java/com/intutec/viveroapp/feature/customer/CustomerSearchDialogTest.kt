package com.intutec.viveroapp.feature.customer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.intutec.viveroapp.feature.cart.presentation.CustomerSearchDialog
import com.intutec.viveroapp.feature.customer.domain.model.Customer
import com.intutec.viveroapp.ui.theme.ViveroAppTheme
import org.junit.Rule
import org.junit.Test

class CustomerSearchDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun showsSearchResults() {
        val customers = listOf(
            Customer("1", "Juan Perez", "juan@example.com", "5551234567")
        )
        
        composeRule.setContent {
            ViveroAppTheme {
                CustomerSearchDialog(
                    query = "juan",
                    searching = false,
                    results = customers,
                    error = null,
                    onQueryChange = {},
                    onSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Juan Perez").assertIsDisplayed()
        composeRule.onNodeWithText("juan@example.com · 5551234567").assertIsDisplayed()
    }

    @Test
    fun showsLoadingState() {
        composeRule.setContent {
            ViveroAppTheme {
                CustomerSearchDialog(
                    query = "juan",
                    searching = true,
                    results = emptyList(),
                    error = null,
                    onQueryChange = {},
                    onSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("customer_search_loading").assertIsDisplayed()
    }
}
