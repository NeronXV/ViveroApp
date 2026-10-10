package com.intutec.viveroapp.feature.sales

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.intutec.viveroapp.feature.catalog.presentation.*
import com.intutec.viveroapp.feature.catalog.domain.repository.*
import com.intutec.viveroapp.feature.cart.presentation.*
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.ui.theme.ViveroAppTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Display-only emulator scenarios. No network requests or operational data. */
class BackendSalesPolishTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val plant = BackendCatalogProduct(1,"PL-001",null,"Monstera deliciosa",null,"Planta de interior",1,25000,25000,"maceta",
        "Moderado","Indirecta","Templado",BackendCatalogImage(1,"/drawable/plant_monstera","Monstera deliciosa"),null)
    private val catalog = BackendCatalogUiState(enabled=true,canSell=true,canScan=true,branchName="Sucursal de ensayo",products=listOf(plant))
    private val cart = BackendCartUiState(enabled=true,journalReady=true,branchName="Sucursal de ensayo",
        cart=BackendCartSnapshot(1,1,BackendSaleIdentity(2,3),listOf(BackendCartLine(1,"Monstera deliciosa","maceta",25000,2))),
        photos=mapOf(1L to "android.resource://com.intutec.viveroapp/drawable/plant_monstera"))

    @Before fun emulatorOnly() {
        assumeTrue("Only the isolated emulator is allowed",Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator"))
        ui.runOnUiThread { ui.activity.enableEdgeToEdge() }
    }
    private fun capture(name: String) {
        ui.waitUntil(5000) { ui.onAllNodes(hasStateDescription("Cargando fotografía"),useUnmergedTree=true).fetchSemanticsNodes().isEmpty() }
        ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"stage6-polish").apply { mkdirs() }
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    private fun target(node: SemanticsNodeInteraction) {
        node.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).assertIsDisplayed()
    }
    @Test fun themeControlsSystemContrastInLightAndDark() {
        var dark by mutableStateOf(false)
        ui.setContent { ViveroAppTheme(darkTheme=dark) {
            BackendCatalogContent(catalog,"android.resource://com.intutec.viveroapp",{},{},{},{},{},{},{},{},{})
        } }
        ui.runOnIdle { assertTrue(WindowCompat.getInsetsController(ui.activity.window,ui.activity.window.decorView).isAppearanceLightStatusBars) }
        capture("catalogo-claro")
        ui.runOnIdle { dark=true }
        ui.runOnIdle { assertFalse(WindowCompat.getInsetsController(ui.activity.window,ui.activity.window.decorView).isAppearanceLightStatusBars) }
        capture("catalogo-oscuro")
    }
    @Test fun cartActionsKeepTouchTargetsAndSendAccessible() {
        ui.setContent { ViveroAppTheme { BackendCartContent(cart,{},{},{},{_,_->},{},{},{},{},{},{},{},{}) } }
        target(ui.onNodeWithContentDescription("Aumentar Monstera deliciosa"))
        target(ui.onNodeWithContentDescription("Disminuir Monstera deliciosa"))
        target(ui.onNodeWithContentDescription("Quitar Monstera deliciosa"))
        target(ui.onNodeWithText("Enviar a caja"))
        capture("carrito-accesible")
    }
    @Test fun longQuoteKeepsTotalAndSafeActionsWhileEveryProductCanBeRead() {
        var sent=0; var dismissed=0
        val items=(1L..20L).map { BackendSaleQuotedLine(it,1,"Planta de ensayo $it","PL-$it",2500,2500,2500) }
        val state=cart.copy(quote=BackendSaleQuote(3,50000,0,50000,items))
        ui.setContent { ViveroAppTheme { BackendCartContent(state,{},{},{},{_,_->},{},{},{},{sent++},{dismissed++},{},{},{}) } }
        target(ui.onNodeWithText("Confirmar y enviar"))
        target(ui.onNodeWithText("Volver al carrito"))
        ui.onNodeWithText("Total: $500.00").assertIsDisplayed()
        capture("confirmacion-larga")
        items.forEachIndexed { index, item ->
            ui.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToIndex(index)
            ui.onNodeWithText(item.productName,substring=true).assertIsDisplayed()
        }
        ui.onNodeWithText("Total: $500.00").assertIsDisplayed()
        capture("confirmacion-ultimo-producto")
        ui.runOnIdle { assertEquals(0,sent);assertEquals(0,dismissed) }
    }
    @Test fun manualCodeWithRealKeyboardKeepsSearchAndBackVisible() {
        var scanned=""
        ui.setContent { ViveroAppTheme { BackendCatalogContent(catalog,"android.resource://com.intutec.viveroapp",{},{},{},{},{},{scanned=it},{},{},{}) } }
        ui.onNodeWithText("Ingresar código").performScrollTo().performClick()
        ui.onNodeWithText("Código de barras o interno").performClick().performTextInput("PL-001")
        ui.waitUntil(5000) { ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        ui.onNodeWithText("Buscar",useUnmergedTree=true).assertIsDisplayed().assertIsEnabled()
        ui.onNodeWithText("Volver",useUnmergedTree=true).assertIsDisplayed()
        capture("codigo-con-teclado")
        ui.onNodeWithText("Buscar",useUnmergedTree=true).performClick()
        ui.runOnIdle { assertEquals("PL-001",scanned) }
    }
    @Test fun changedPriceQuoteKeepsProductWarningTotalAndConfirmationReadable() {
        val state=cart.copy(quote=BackendSaleQuote(3,52000,0,52000,
            listOf(BackendSaleQuotedLine(1,2,"Monstera deliciosa","PL-001",26000,26000,52000))))
        ui.setContent { ViveroAppTheme { BackendCartContent(state,{},{},{},{_,_->},{},{},{},{},{},{},{},{}) } }
        ui.onNodeWithText("El importe se actualizó. Revisa los precios antes de confirmar.").assertIsDisplayed()
        ui.onNodeWithText("Monstera deliciosa\n2 × $260.00 · $520.00").assertIsDisplayed()
        ui.onNodeWithText("Total: $520.00").assertIsDisplayed()
        target(ui.onNodeWithText("Confirmar y enviar"))
        target(ui.onNodeWithText("Volver al carrito"))
        capture("confirmacion-precio-accesible")
    }
    @Test fun catalogWithKeyboardCanScrollToAddAndDetailWithoutHidingActions() {
        var state by mutableStateOf(catalog); var added=0
        ui.setContent { ViveroAppTheme { BackendCatalogContent(state,"android.resource://com.intutec.viveroapp",{},{},
            {state=state.copy(query=it)},{},{},{},{added++},{},{}) } }
        ui.onNodeWithText("Buscar plantas y productos").performClick().performTextInput("Monstera")
        ui.waitUntil(5000) { ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        val verticalList=SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ui.onNode(verticalList).performScrollToNode(hasText("Agregar"))
        target(ui.onNodeWithText("Agregar"))
        capture("catalogo-agregar-con-teclado")
        ui.onNodeWithText("Agregar").performClick()
        ui.runOnIdle { assertEquals(1,added) }
        ui.onNode(verticalList).performScrollToNode(hasText("Ver detalle"))
        target(ui.onNodeWithText("Ver detalle"))
    }
}
