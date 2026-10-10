package com.intutec.viveroapp.feature.sales

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.intutec.viveroapp.BuildConfig
import com.intutec.viveroapp.MainActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Opt-in real Activity/API/Room rehearsal, restricted to the dedicated local emulator fixture. */
class Stage7aRealWorkflowTest {
    private val ui = createAndroidComposeRule<MainActivity>()
    // Gate before the Activity rule: a normal regression must never open a
    // potentially authenticated operational app just to skip this rehearsal.
    private val rehearsalGate = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue("Opt-in isolated workflow", InstrumentationRegistry.getArguments().getString("stage7a") == "true")
                require(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator"))
                require(BuildConfig.BACKEND_API_URL == "http://10.0.2.2:33034")
                base.evaluate()
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(rehearsalGate).around(ui)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: JSONObject
    private val output get() = File(context.filesDir, "stage7a").apply { mkdirs() }
    @Before fun isolatedOnly() {
        assumeTrue("Opt-in isolated workflow", InstrumentationRegistry.getArguments().getString("stage7a") == "true")
        require(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator"))
        require(BuildConfig.BACKEND_API_URL == "http://10.0.2.2:33034")
        fixture = JSONObject(File(context.filesDir,"stage7a-fixture.private.json").readText())
        require(fixture.getString("project") == "vivero-stage7a-20261009")
    }
    private fun waitText(text: String) = ui.waitUntil(25000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun login() {
        waitText("Correo electrónico")
        val seller=fixture.getJSONObject("actors").getJSONObject("seller")
        ui.onNodeWithText("Correo electrónico").performTextInput(seller.getString("email"))
        ui.onNodeWithText("Contraseña").performTextInput(seller.getString("password"))
        ui.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        waitText("Vender")
        ui.onNodeWithText("Ensayo integración 7A",substring=true).assertExists()
    }
    private fun snapshot(name: String) {
        ui.waitForIdle()
        val image=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(output,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    private fun attempt(): JSONObject {
        val seller=fixture.getJSONObject("actors").getJSONObject("seller")
        SQLiteDatabase.openDatabase(context.getDatabasePath("vivero.db").path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT attempt_key,state,expected_total_cents,server_sale_id,server_folio FROM backend_sale_attempts WHERE actor_id=? AND branch_id=?",
                arrayOf(seller.getLong("userId").toString(),fixture.getLong("branchId").toString())).use { c ->
                assertEquals("Exactly one persisted original attempt",1,c.count); assertTrue(c.moveToFirst())
                return JSONObject().put("key",c.getString(0)).put("state",c.getString(1)).put("totalCents",c.getLong(2))
                    .put("saleId",if(c.isNull(3)) JSONObject.NULL else c.getLong(3)).put("folio",if(c.isNull(4)) JSONObject.NULL else c.getString(4))
            }
        }
    }
    private fun proxy(body: String) {
        val connection=URL("http://10.0.2.2:33034/__stage7a").openConnection() as HttpURLConnection
        try { connection.requestMethod="POST";connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }; assertEquals(200,connection.responseCode)
        } finally { connection.disconnect() }
    }
    private fun cartQuantity(): Int {
        val seller=fixture.getJSONObject("actors").getJSONObject("seller")
        SQLiteDatabase.openDatabase(context.getDatabasePath("vivero.db").path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT i.quantity FROM backend_cart_items i JOIN backend_cart_drafts d ON d.id=i.cart_id WHERE d.actor_id=? AND d.branch_id=? AND i.product_id=?",
                arrayOf(seller.getLong("userId").toString(),fixture.getLong("branchId").toString(),fixture.getJSONArray("products").getJSONObject(0).getLong("id").toString())).use { c ->
                assertTrue(c.moveToFirst()); return c.getInt(0)
            }
        }
    }
    @Test fun createSaleWithLostResponse() {
        login()
        ui.onNodeWithText("Vender").performClick()
        waitText("Buscar plantas y productos")
        ui.onNodeWithText("Buscar plantas y productos").performTextInput("Monstera ensayo 7A")
        waitText("Monstera ensayo 7A")
        ui.onNodeWithText("Ingresar código").performScrollTo().performClick()
        ui.onNodeWithText("Código de barras o interno").performTextInput(fixture.getJSONArray("products").getJSONObject(0).getString("code"))
        ui.onNodeWithText("Buscar",useUnmergedTree=true).performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("Agregar").fetchSemanticsNodes().size == 1 }
        ui.waitUntil(10000) { ui.onAllNodes(hasStateDescription("Fotografía disponible"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        val list=SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ui.onNode(list).performScrollToNode(hasText("Agregar")); ui.onNodeWithText("Agregar").performClick()
        ui.onNodeWithContentDescription("Abrir carrito").performClick()
        waitText("Enviar a caja")
        var quantity=cartQuantity()
        while (quantity > 1) {
            ui.onNodeWithContentDescription("Disminuir Monstera ensayo 7A").performClick()
            val previous=quantity
            ui.waitUntil(5000) { cartQuantity() < previous }
            quantity=cartQuantity()
        }
        ui.onNodeWithContentDescription("Aumentar Monstera ensayo 7A").performClick()
        ui.onNodeWithContentDescription("Disminuir Monstera ensayo 7A").performClick()
        ui.onNodeWithContentDescription("Aumentar Monstera ensayo 7A").performClick()
        ui.onNodeWithText("Enviar a caja").assertIsEnabled().performClick()
        waitText("Confirmar y enviar")
        ui.onNodeWithText("Total: $500.00").assertIsDisplayed()
        ui.onNodeWithText("Monstera ensayo 7A\n2 × $250.00 · $500.00").assertIsDisplayed()
        snapshot("android-cotizacion-real")
        proxy("{\"path\":\"/api/v1/sales\",\"offlineAfter\":true}")
        ui.onNodeWithText("Confirmar y enviar").performClick()
        waitText("Estamos comprobando el envío. No envíes otra venta hasta conocer el resultado.")
        ui.waitUntil(25000) { runCatching { attempt().getString("state") == "UNCERTAIN" }.getOrDefault(false) }
        File(output,"attempt-before-reopen.private.json").writeText(attempt().toString())
        ui.onNodeWithText("Enviar a caja").assertDoesNotExist() // The consumed draft cannot be sent a second time.
        snapshot("android-respuesta-perdida")
    }
    @Test fun recoverOriginalAfterRealProcessRestart() {
        login()
        val before=JSONObject(File(output,"attempt-before-reopen.private.json").readText())
        val pending=attempt();assertEquals(before.getString("key"),pending.getString("key"));assertEquals("UNCERTAIN",pending.getString("state"))
        ui.onNodeWithText("Carrito").performClick()
        waitText("Estamos comprobando el envío. No envíes otra venta hasta conocer el resultado.")
        ui.onNodeWithText("Consultar resultado").performScrollTo().performClick()
        waitText("Venta enviada a Caja")
        val recovered=attempt();assertEquals(before.getString("key"),recovered.getString("key"));assertEquals("SYNCED",recovered.getString("state"))
        assertEquals(50000L,recovered.getLong("totalCents")); assertTrue(recovered.getString("folio").matches(Regex("VD-[0-9]{4,}")))
        File(output,"android-sale.json").writeText(JSONObject().put("id",recovered.getLong("saleId")).put("folio",recovered.getString("folio")).put("totalCents",50000).toString())
        snapshot("android-recuperada-tras-reapertura")
    }
    @Test fun historyShowsSameSalePaidByWeb() {
        login()
        val sale=JSONObject(File(output,"android-sale.json").readText())
        ui.onNodeWithText("Mis ventas").performClick()
        waitText(sale.getString("folio"))
        ui.onNodeWithText(sale.getString("folio")).assertIsDisplayed()
        ui.onNode(hasText("Ver detalle") and hasAnySibling(hasText(sale.getString("folio")))).performScrollTo().performClick()
        waitText("Monstera ensayo 7A")
        ui.onNodeWithText("Pagada").assertIsDisplayed()
        snapshot("android-venta-pagada-por-web")
    }
}
