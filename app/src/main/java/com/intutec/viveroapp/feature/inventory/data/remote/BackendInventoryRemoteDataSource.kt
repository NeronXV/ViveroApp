package com.intutec.viveroapp.feature.inventory.data.remote

import com.intutec.viveroapp.core.network.*
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.inventory.domain.repository.*
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class BackendInventoryRemoteDataSource @Inject constructor(private val transport: BackendApiTransport) : BackendInventoryGateway {
    override suspend fun dashboard(token: String, identity: BackendSaleIdentity, after: Long?): BackendInventoryPage {
        val row = call { transport.get("/api/v1/inventory/dashboard", token, buildMap { put("limit", "20"); after?.let { require(it in 1..4294967295L); put("after_product_id", it.toString()) } }) }
        return checked {
            exact(row,"schema_version","branch_id","permissions","items","has_more","next_product_id"); identity(row,identity)
            val permissions = row["permissions"]!!.jsonObject
            exact(permissions,"can_record_count","can_receive","can_approve_counts")
            permissions.keys.forEach { boolean(permissions,it) }
            val items = row["items"]!!.jsonArray.map { raw -> val r=raw.jsonObject
                exact(r,"product_id","product_name","product_code","product_unit","total_quantity","minimum_stock","is_low_stock","balance_updated_at")
                val quantity=milli(r,"total_quantity"); val minimum=milli(r,"minimum_stock")
                check(boolean(r,"is_low_stock") == (quantity <= minimum))
                BackendInventoryItem(id(r,"product_id"), text(r,"product_name"), text(r,"product_code"), text(r,"product_unit"),quantity,minimum,boolean(r,"is_low_stock"),nullableText(r,"balance_updated_at")?.also { Instant.parse(it) }) }
            check(items.size <= 20 && items.zipWithNext().all { (a,b)->a.id < b.id } && items.all { it.id > (after ?: 0L) })
            val next=cursor(row,"next_product_id",items.map { it.id })
            BackendInventoryPage(items,next)
        }
    }
    override suspend fun history(token: String, identity: BackendSaleIdentity, product: Long, before: Long?): BackendInventoryHistory {
        require(product in 1..4294967295L)
        val row=call { transport.get("/api/v1/inventory/history",token,buildMap { put("limit","20"); put("product_id",product.toString()); before?.let { require(it in 1..4294967295L);put("before_id",it.toString()) } }) }
        return checked {
            exact(row,"schema_version","branch_id","items","has_more","next_before_id"); identity(row,identity)
            val items=row["items"]!!.jsonArray.map { raw-> val r=raw.jsonObject
                exact(r,"id","product_id","product_name","product_code","movement_type","quantity","notes","created_at","created_by_label")
                check(id(r,"product_id")==product)
                val type=text(r,"movement_type"); val quantity=milli(r,"quantity",signed=true)
                check(type in setOf("OPENING","RECEPTION","ADJUSTMENT_ADD","ADJUSTMENT_SUB","SALE","REFUND","TRANSFER_IN","TRANSFER_OUT"))
                check(if(type in setOf("ADJUSTMENT_SUB","SALE","TRANSFER_OUT")) quantity < 0 else if(type=="OPENING") quantity >= 0 else quantity > 0)
                BackendInventoryMovement(id(r,"id"),product,text(r,"product_name"),text(r,"product_code"),type,quantity,nullableText(r,"notes"),text(r,"created_at").also { Instant.parse(it) },nullableText(r,"created_by_label")) }
            check(items.size<=20 && items.zipWithNext().all { (a,b)->a.id>b.id } && items.all { it.id < (before ?: Long.MAX_VALUE) })
            BackendInventoryHistory(items,cursor(row,"next_before_id",items.map { it.id }))
        }
    }
    override suspend fun submit(token: String, attempt: BackendInventoryAttempt) = operation(token,attempt,false)
    override suspend fun result(token: String, attempt: BackendInventoryAttempt) = operation(token,attempt,true)
    private suspend fun operation(token: String, a: BackendInventoryAttempt, recovery: Boolean): BackendInventoryReceipt {
        check(recovery || a.action != BackendInventoryAction.COUNT) {
            "Registra el conteo físico en el portal Web; el endpoint nativo anterior está retirado."
        }
        val body=buildJsonObject { put("product_id",a.productId)
            put(if(a.action==BackendInventoryAction.RECEPTION) "quantity" else "counted_quantity","${a.quantity}.000")
            put(if(a.action==BackendInventoryAction.RECEPTION) "notes" else "reason",a.notes?.let(::JsonPrimitive) ?: JsonNull) }.toString()
        val row=call { transport.post("/api/v1/inventory/${a.action.path}"+(if(recovery) "/result" else ""),token,mapOf("Idempotency-Key" to a.key,"X-Expected-Actor-Id" to a.identity.userId.toString(),"X-Expected-Branch-Id" to a.identity.branchId.toString()),body) }
        return checked {
            check(n(row,"schema_version")==1L && id(row,"product_id")==a.productId)
            boolean(row,"idempotent_replay").also { if(recovery) check(it) }
            if(a.action==BackendInventoryAction.RECEPTION) {
                exact(row,"schema_version","idempotent_replay","movement_id","product_id","quantity","total_quantity")
                check(milli(row,"quantity")==a.quantity*1000)
                BackendInventoryReceipt(a.action,id(row,"movement_id"),a.productId,milli(row,"quantity"),milli(row,"total_quantity"))
            } else {
                exact(row,"schema_version","idempotent_replay","count_id","product_id","previous_quantity","counted_quantity","adjustment_quantity","total_quantity")
                val previous=milli(row,"previous_quantity");val counted=milli(row,"counted_quantity");val adjustment=milli(row,"adjustment_quantity",true)
                check(counted==a.quantity*1000 && previous+adjustment==counted && milli(row,"total_quantity")==counted)
                BackendInventoryReceipt(a.action,id(row,"count_id"),a.productId,counted,counted,previous,adjustment)
            }
        }
    }
    private suspend fun call(action:suspend ()->BackendApiResponse): JsonObject = try {
        val response=action(); val row=Json.parseToJsonElement(response.body).jsonObject
        if(response.status==404 && row==buildJsonObject { put("error","INVENTORY_OPERATION_NOT_FOUND") }) throw BackendInventoryResultMissing()
        check(response.status in 200..299);row
    } catch(cancelled:CancellationException) { throw cancelled } catch(missing:BackendInventoryResultMissing) { throw missing }
    catch(_:Exception) { error("No se confirmó la operación de inventario; conserva el intento.") }
    private fun <T> checked(action:()->T):T=try {action()}catch(_:Exception){error("Respuesta de inventario incompatible.")}
    private fun exact(r:JsonObject,vararg keys:String){check(r.keys==keys.toSet())}
    private fun n(r:JsonObject,key:String):Long { val v=r[key]!!.jsonPrimitive;check(!v.isString);return checkNotNull(v.longOrNull) }
    private fun id(r:JsonObject,key:String)=n(r,key).also {check(it in 1..4294967295L)}
    private fun text(r:JsonObject,key:String)=r[key]!!.jsonPrimitive.also {check(it.isString && it.content.isNotBlank())}.content
    private fun nullableText(r:JsonObject,key:String)=if(r[key]==JsonNull)null else text(r,key)
    private fun boolean(r:JsonObject,key:String)=r[key]!!.jsonPrimitive.also {check(!it.isString)}.boolean
    private fun identity(r:JsonObject,who:BackendSaleIdentity){check(n(r,"schema_version")==1L && id(r,"branch_id")==who.branchId)}
    private fun cursor(r:JsonObject,key:String,ids:List<Long>):Long? {
        val next=if(r[key]==JsonNull)null else id(r,key)
        check(boolean(r,"has_more")== (next!=null) && (next==null || (ids.size==20 && next==ids.last())))
        return next
    }
    private fun milli(r:JsonObject,key:String,signed:Boolean=false):Long {
        val text=text(r,key);check(Regex(if(signed) "^-?(?:0|[1-9][0-9]{0,10})\\.[0-9]{3}$" else "^(?:0|[1-9][0-9]{0,10})\\.[0-9]{3}$").matches(text))
        val absolute=text.removePrefix("-").replace(".","").toLong();check(absolute<=MAX_INVENTORY_MILLI)
        check(text!="-0.000");return if(text.startsWith('-')) -absolute else absolute
    }
}
