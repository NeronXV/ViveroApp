package com.intutec.viveroapp.feature.sales

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.intutec.viveroapp.feature.catalog.presentation.*
import com.intutec.viveroapp.feature.catalog.domain.repository.*
import com.intutec.viveroapp.feature.cart.presentation.*
import com.intutec.viveroapp.feature.cart.domain.repository.*
import com.intutec.viveroapp.feature.scanner.presentation.CameraCodeDialog
import com.intutec.viveroapp.ui.theme.ViveroAppTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real Compose rendering and interaction in an emulator; synthetic state, no operational API. */
class BackendSalesUiTest {
    @get:Rule val ui = createComposeRule()
    private val plant = BackendCatalogProduct(1,"PL-001",null,"Monstera deliciosa",null,"Planta de interior",1,25000,25000,"maceta",
        "Moderado","Indirecta","Templado",BackendCatalogImage(1,"/drawable/plant_monstera","Monstera deliciosa"),null)
    private val catalog = BackendCatalogUiState(enabled=true,canSell=true,canScan=true,branchName="Sucursal de ensayo",
        categories=listOf(BackendCategory(1,"Plantas",""),BackendCategory(2,"Macetas","")),
        products=listOf(plant,plant.copy(id=2,commonName="Echeveria",image=null,effectivePriceCents=8500,priceCents=8500)))
    private val cart = BackendCartUiState(enabled=true,journalReady=true,branchName="Sucursal de ensayo",canViewHistory=true,
        cart=BackendCartSnapshot(1,1,BackendSaleIdentity(2,3),listOf(BackendCartLine(1,"Monstera deliciosa","maceta",25000,2))),
        photos=mapOf(1L to "android.resource://com.intutec.viveroapp/drawable/plant_monstera"))
    @Before fun emulatorOnly() {
        assumeTrue("Synthetic UI checks only run in the isolated emulator", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator"))
    }
    private fun capture(name: String) {
        ui.waitUntil(5000) { ui.onAllNodes(hasStateDescription("Cargando fotografía"),useUnmergedTree=true).fetchSemanticsNodes().isEmpty() }
        ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"stage6").apply { mkdirs() }
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    @Test fun sellingShowsPhotosAndFallbackAndSeparatesManualScanFromAdd() {
        var state by mutableStateOf(catalog); var adds=0; var code=""; var selected:Long?=null
        ui.setContent { ViveroAppTheme { BackendCatalogContent(state,"android.resource://com.intutec.viveroapp",{},{},
            { state=state.copy(query=it) },{ selected=it;state=state.copy(category=it) },{}, { code=it }, { adds++ },{}, {}) } }
        ui.onNodeWithText("Vender").assertIsDisplayed()
        ui.onNodeWithText("Monstera deliciosa").assertIsDisplayed()
        ui.onNodeWithContentDescription("Producto sin fotografía: Echeveria").assertExists()
        capture("vender-catalogo")
        ui.onNodeWithText("Buscar plantas y productos").performTextInput("Monstera")
        ui.onNodeWithText("Plantas",useUnmergedTree=true).performClick()
        ui.runOnIdle { assertEquals("Monstera",state.query); assertEquals(1L,selected) }
        ui.onNodeWithText("Ingresar código").performClick()
        ui.onNodeWithText("Código de barras o interno").performTextInput("PL-001")
        ui.onNodeWithText("Buscar",useUnmergedTree=true).performClick()
        ui.runOnIdle { assertEquals("PL-001",code);assertEquals(0,adds) }
        ui.onAllNodesWithText("Agregar")[0].performClick();ui.runOnIdle { assertEquals(1,adds) }
    }
    @Test fun cartQuantityAndRemovalRequireAnExplicitAction() {
        var state by mutableStateOf(cart);var removed=0;var quotes=0
        ui.setContent { ViveroAppTheme { BackendCartContent(state,{},{},{},
            { _,quantity -> state=state.copy(cart=state.cart!!.copy(items=state.cart!!.items.map { it.copy(quantity=quantity) })) },
            { removed++;state=state.copy(cart=null) },{}, { quotes++ },{},{},{},{},{}) } }
        capture("carrito-articulos")
        ui.onNodeWithContentDescription("Aumentar Monstera deliciosa").performClick()
        ui.runOnIdle { assertEquals(3,state.cart!!.items.single().quantity) }
        ui.onNodeWithContentDescription("Disminuir Monstera deliciosa").performClick()
        ui.runOnIdle { assertEquals(2,state.cart!!.items.single().quantity) }
        ui.onNodeWithText("Enviar a caja").assertIsEnabled().performClick();ui.runOnIdle { assertEquals(1,quotes) }
        ui.onNodeWithContentDescription("Quitar Monstera deliciosa").performClick();ui.runOnIdle { assertEquals(0,removed) }
        ui.onNodeWithText("Quitar producto").performClick();ui.onNodeWithText("Tu carrito está vacío").assertIsDisplayed()
        capture("carrito-vacio")
    }
    @Test fun uncertainSubmissionNeverOffersAnotherSendAndKeepsRecoveryVisible() {
        var checked:Long?=null
        val state=cart.copy(journal=listOf(BackendSaleJournalEntry(8,"UNCERTAIN",50000,cart.cart!!.saleLines(),"Sin conexión",null)))
        ui.setContent { ViveroAppTheme { BackendCartContent(state,{},{},{},{_,_->},{},{},{},{},{},{},{checked=it},{}) } }
        ui.onNodeWithText("Enviar a caja").assertIsNotEnabled()
        ui.onNodeWithText("Consultar resultado").assertIsDisplayed().performClick();ui.runOnIdle { assertEquals(8L,checked) }
        capture("envio-por-comprobar")
    }
    @Test fun successShowsOriginalShortFolioAndTotalWithNewSaleAndHistory() {
        var newSale=0;var history=0
        val state=cart.copy(cart=null,sent=BackendSaleJournalEntry(8,"SYNCED",50000,listOf(BackendSaleLine(1,2)),null,BackendStoredSaleReceipt(90,"VD-0090","SENT_TO_CASHIER")))
        ui.setContent { ViveroAppTheme { BackendCartContent(state,{},{},{history++},{_,_->},{},{},{},{},{},{newSale++},{},{}) } }
        ui.onNodeWithText("Venta enviada a Caja").assertIsDisplayed();ui.onNodeWithText("VD-0090").assertIsDisplayed()
        capture("venta-confirmada")
        ui.onNodeWithText("Mis ventas").performClick();ui.onNodeWithText("Nueva venta").performClick()
        ui.runOnIdle { assertEquals(1,newSale);assertEquals(1,history) }
    }
    @Test fun quotedPricesAreReviewedAndConfirmationDoesNotResendWhileBusy() {
        var sent=0;var state by mutableStateOf(cart.copy(quote=BackendSaleQuote(3,52000,0,52000,
            listOf(BackendSaleQuotedLine(1,2,"Monstera deliciosa","PL-001",26000,26000,52000)))))
        ui.setContent { ViveroAppTheme { BackendCartContent(state,{},{},{},{_,_->},{},{},{},{sent++;state=state.copy(working=true)},{},{},{},{}) } }
        ui.onNodeWithText("El importe se actualizó. Revisa los precios antes de confirmar.").assertIsDisplayed()
        capture("confirmar-precios")
        ui.onNodeWithText("Confirmar y enviar").performClick();ui.onNodeWithText("Confirmar y enviar").assertIsNotEnabled()
        ui.runOnIdle { assertEquals(1,sent) }
    }
    @Test fun networkErrorAndCameraManualFallbackRemainVisible() {
        var camera by mutableStateOf(false)
        ui.setContent { ViveroAppTheme {
            if(camera) CameraCodeDialog({camera=false},{})
            else BackendCatalogContent(catalog.copy(products=emptyList(),error="No se pudo consultar el catálogo."),"",{},{},{},{},{},{},{},{camera=true},{})
        } }
        ui.onNodeWithText("No se pudo consultar el catálogo.").assertIsDisplayed();capture("catalogo-error")
        ui.onNodeWithText("Volver a intentar").performClick()
        ui.onNodeWithText("Ingresar código manualmente").assertIsDisplayed()
    }
}
