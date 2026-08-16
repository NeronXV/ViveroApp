package com.intutec.viveroapp.feature.cart

import com.intutec.viveroapp.feature.cart.sync.SaleSyncItem
import com.intutec.viveroapp.feature.cart.sync.SaleSyncRequest
import com.intutec.viveroapp.feature.cart.sync.toRpcParameters
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SaleSyncRpcContractTest {
    @Test
    fun `rpc payload uses exact names values and null customer`() {
        val parameters = SaleSyncRequest(
            saleId = "11111111-1111-4111-8111-111111111111",
            folio = "VD-260815-111111",
            items = listOf(SaleSyncItem("22222222-2222-4222-8222-222222222222", 2)),
        ).toRpcParameters()

        assertEquals(setOf("p_sale_id", "p_folio", "p_items", "p_customer_id"), parameters.keys)
        assertEquals(JsonPrimitive("11111111-1111-4111-8111-111111111111"), parameters["p_sale_id"])
        assertEquals(JsonPrimitive("VD-260815-111111"), parameters["p_folio"])
        assertEquals(JsonNull, parameters["p_customer_id"])
        assertEquals(
            """[{"product_id":"22222222-2222-4222-8222-222222222222","quantity":2}]""",
            parameters["p_items"].toString(),
        )
    }
}
