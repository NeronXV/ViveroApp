package com.intutec.viveroapp.feature.plants.data

import com.intutec.viveroapp.core.database.ViveroDatabase
import com.intutec.viveroapp.core.network.*
import com.intutec.viveroapp.core.session.*
import com.intutec.viveroapp.feature.plants.domain.*
import com.intutec.viveroapp.feature.plants.data.local.PlantWorkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class Attempt(val path:String,val method:String,val body:String,val key:String,val work:PlantWork,val photo:Boolean=false)
@Singleton
class PlantRepository @Inject constructor(private val api:BackendApiTransport,private val sessions:SessionStore,database:ViveroDatabase):PlantGateway {
    private val dao=database.plantWorkDao()
    private val mutex=Mutex()
    private val json=Json { ignoreUnknownKeys=true }
    private data class Scope(val token:String,val actor:Long,val branch:Long)
    private fun scope():Scope {
        val state=sessions.backend.value
        val session=state.authorizedSession("MANAGE_PRODUCTS",true) ?: error("Inicia sesión y comprueba tus permisos.")
        return Scope(session.token,session.userId,state.context!!.branch!!.id)
    }
    private fun unchanged(s:Scope){ check(scope()==s) { "Cambió la sesión. El intento se conserva en la cuenta y sucursal originales." } }
    private fun response(r:BackendApiResponse):JsonObject {
        if(r.status !in 200..299) {
            val code=runCatching { json.parseToJsonElement(r.body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            throw PlantFailure(r.status,code?:"UNKNOWN")
        }
        return json.parseToJsonElement(r.body).jsonObject
    }
    private suspend fun get(path:String,s:Scope,query:Map<String,String> = emptyMap()):JsonObject {
        val result=response(api.plantRead("/api/v1/$path",s.token,mapOf("X-Expected-Actor-Id" to s.actor.toString(),"X-Expected-Branch-Id" to s.branch.toString()),query));unchanged(s);return result
    }
    private suspend fun pages(path:String,s:Scope,query:Map<String,String>):List<JsonObject> {
        val rows=mutableListOf<JsonObject>();var after=0L
        do {
            val page=get(path,s,query+mapOf("limit" to "20","after_id" to after.toString()).filterValues { it!="0" })
            val items=page.getValue("items").jsonArray.map { it.jsonObject }
            check(items.all { it.long("id")>after }) { "Respuesta de catálogo incompatible." }
            rows.addAll(items)
            val next=page["next_after_id"]?.jsonPrimitive?.longOrNull
            if(next==null)break
            check(next>after);after=next
        }while(true)
        return rows
    }
    private fun draft(r:JsonObject):PlantWork {
        val f=r.getValue("fields").jsonObject
        return PlantWork(id=r.long("id"),name=f.text("approved_name").ifBlank { r.text("original_name") },presentation=f.text("presentation"),categoryId=f["category_id"]?.jsonPrimitive?.longOrNull,
            price=f["suggested_price_cents"]?.jsonPrimitive?.longOrNull?.takeIf { it>0 }?.let(::money)?:"",quantity=f["initial_quantity"]?.jsonPrimitive?.longOrNull?.toString()?:"",
            revision=r.long("revision").toInt(),hasImage=r["has_image"]?.jsonPrimitive?.boolean==true,status=r.text("status"),description=f.text("description"),code=f.text("internal_code"),
            needsInitialCount=r["publication"]?.jsonObject?.get("needs_initial_count")?.jsonPrimitive?.booleanOrNull==true,
            readyForSale=r["publication"]?.jsonObject?.get("ready")?.jsonPrimitive?.booleanOrNull==true,fromReceipt=r.text("source_key")=="supplier_purchase_item")
    }
    private fun product(r:JsonObject):PlantWork {
        val name=r.text("common_name");val split=name.lastIndexOf(" · ")
        return PlantWork(id=r.long("id"),product=true,name=if(split<0)name else name.take(split),presentation=if(split<0)"" else name.substring(split+3),categoryId=r.long("category_id"),price=r.long("price_cents").takeIf{it>0}?.let(::money)?:"",hasImage=r["image"]!=null&&r["image"]!=JsonNull,status="PRODUCT",description=r.text("description"),code=r.text("internal_code"),
            imageId=r["image"]?.takeIf{it!=JsonNull}?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull)
    }
    override suspend fun preview(imageId:Long):ByteArray? {
        val s=scope();val bytes=api.plantImage(imageId,s.token);unchanged(s);return bytes
    }
    override suspend fun preview(plant:PlantWork):ByteArray? {
        if(plant.product)return plant.imageId?.let { preview(it) }
        if(!plant.hasImage || plant.id==0L)return null
        val s=scope()
        val bytes=api.plantDraftImage(plant.id,s.token,mapOf("X-Expected-Actor-Id" to s.actor.toString(),"X-Expected-Branch-Id" to s.branch.toString()))
        unchanged(s);return bytes
    }
    override suspend fun list():PlantList {
        val s=scope()
        val categories=pages("categories",s,mapOf("status" to "active")).map { PlantCategory(it.long("id"),it.text("name")) }
        val drafts=pages("cataloging",s,mapOf("status" to "ALL")).filter { it.text("status") in listOf("DRAFT","REVIEW") }.map(::draft)
        val rows=pages("products",s,mapOf("status" to "all"))
        val catalog=rows.map(::product)
        val products=rows.filter { it["is_active"]?.jsonPrimitive?.booleanOrNull==false || it.long("price_cents")==0L }.map(::product)
        val direct=if("MANAGE_INVENTORY" in sessions.backend.value.context!!.capabilities)get("inventory/permissions",s)["permissions"]?.jsonObject?.get("can_apply_count")?.jsonPrimitive?.booleanOrNull==true else false
        unchanged(s);return PlantList(drafts+products,categories,catalog,direct)
    }
    override suspend fun open(plant:PlantWork):PlantWork {
        val s=scope()
        if(!plant.product)return draft(get("cataloging/${plant.id}",s))
        val before=get("products/${plant.id}/edit-version",s).long("revision").toInt()
        val row=pages("products",s,mapOf("status" to "all")).firstOrNull { it.long("id")==plant.id }?:error("No se encontró la planta.")
        val prep=get("products/${plant.id}/preparation",s)
        val after=get("products/${plant.id}/edit-version",s).long("revision").toInt()
        check(before==after) { "La ficha cambió. Vuelve a consultarla." }
        val baseline=if("MANAGE_INVENTORY" in sessions.backend.value.context!!.capabilities)get("inventory/count-baseline",s,mapOf("product_id" to plant.id.toString())) else null
        return product(row).copy(revision=after,status=prep.text("commercial_state"),quantity=baseline?.text("quantity")?:"No disponible",baselineMovementId=baseline?.long("movement_id"),
            needsInitialCount=prep["initial_count_confirmed"]?.jsonPrimitive?.booleanOrNull==false,
            readyForSale=prep["ready"]?.jsonPrimitive?.booleanOrNull==true)
    }
    override suspend fun remember(plant:PlantWork?) {
      val s=scope()
      mutex.withLock {
        unchanged(s);val old=dao.get(s.actor,s.branch)
        dao.put(PlantWorkEntity(s.actor,s.branch,plant?.let { json.encodeToString(it) },old?.attempt))
      }
    }
    override suspend fun remembered():PlantWork? {val s=scope();return dao.get(s.actor,s.branch)?.form?.let { json.decodeFromString<PlantWork>(it) }}
    override suspend fun hasPending():Boolean {val s=scope();return dao.get(s.actor,s.branch)?.attempt!=null}
    private suspend fun write(s:Scope,attempt:Attempt):PlantWork {
        val old=dao.get(s.actor,s.branch)
        check(old?.attempt==null) { "Comprueba el guardado pendiente antes de continuar." }
        dao.put(PlantWorkEntity(s.actor,s.branch,json.encodeToString(attempt.work),json.encodeToString(attempt)))
        return send(s,attempt)
    }
    private suspend fun send(s:Scope,a:Attempt):PlantWork {
        unchanged(s)
        val headers=mutableMapOf("Idempotency-Key" to a.key,"X-Expected-Actor-Id" to s.actor.toString(),"X-Expected-Branch-Id" to s.branch.toString())
        if(a.work.product)headers["If-Match"]="\"${a.work.revision}\""
        val bytes=if(a.photo){
            headers["Content-Type"]="image/jpeg";headers["X-Cataloging-Revision"]=a.work.revision.toString()
            withContext(Dispatchers.IO) { File(requireNotNull(a.work.photoPath)).readBytes().also { require(it.size<=5*1024*1024) } }
        }else a.body.toByteArray(Charsets.UTF_8)
        try {
            val result=response(api.plantWrite(a.path,a.method,s.token,headers,bytes));unchanged(s)
            val updated=if(a.path=="/api/v1/inventory/direct-counts") {
                check(result.text("status") in listOf("APPLIED","CLOSED")) { "El conteo no se aplicó." }
                a.work.copy(quantity=result.text("observed_quantity"),needsInitialCount=false,baselineMovementId=null)
            } else if(!a.work.product) {
                val saved=draft(result)
                if(a.photo)a.work.copy(revision=saved.revision,hasImage=saved.hasImage,photoPath=null)
                else saved.copy(photoPath=a.work.photoPath,linkProductId=a.work.linkProductId)
            }
                else a.work.copy(revision=result["revision"]?.jsonPrimitive?.intOrNull?:a.work.revision,hasImage=a.work.hasImage||a.photo,photoPath=if(a.photo)null else a.work.photoPath,
                    imageId=if(a.photo)result["id"]?.jsonPrimitive?.longOrNull?:a.work.imageId else a.work.imageId,
                    status=result["commercial_state"]?.jsonPrimitive?.content?:a.work.status,
                    needsInitialCount=result["needs_initial_count"]?.jsonPrimitive?.booleanOrNull?:a.work.needsInitialCount,
                    readyForSale=result["ready"]?.jsonPrimitive?.booleanOrNull?:a.work.readyForSale)
            dao.put(PlantWorkEntity(s.actor,s.branch,json.encodeToString(updated),null));return updated
        }catch(e:PlantFailure){
            unchanged(s)
            if(e.status in listOf(400,404,409,413,415,422)&&e.code !in listOf("CATALOGING_SESSION_CHANGED","CATALOGING_KEY_CONFLICT")){
                val old=dao.get(s.actor,s.branch);dao.put(PlantWorkEntity(s.actor,s.branch,old?.form,null))
            }
            throw e
        }
    }
    override suspend fun recover():PlantWork?=mutex.withLock {
        val s=scope();val a=dao.get(s.actor,s.branch)?.attempt?.let { json.decodeFromString<Attempt>(it) }?:return@withLock null
        send(s,a)
    }
    override suspend fun count(plant:PlantWork,quantity:String,reason:String):PlantWork=mutex.withLock {
        val s=scope();require(plant.product && plant.baselineMovementId!=null) { "Vuelve a abrir la ficha para consultar las existencias." }
        val count=requireNotNull(plantQuantity(quantity)) { "Indica la cantidad contada." }
        require(reason.trim().length in 3..240) { "Escribe el motivo del ajuste." }
        val body=buildJsonObject {put("product_id",plant.id);put("counted_quantity",count.toString());put("reason",reason.trim());put("baseline_quantity",plant.quantity);put("baseline_movement_id",plant.baselineMovementId)}
        write(s,Attempt("/api/v1/inventory/direct-counts","POST",body.toString(),UUID.randomUUID().toString(),plant))
    }
    override suspend fun history(productId:Long?):List<String> {
        val s=scope();val rows=get("plants/history",s,productId?.let{mapOf("product_id" to it.toString())}?:emptyMap())["items"]!!.jsonArray
        val labels=mapOf("CREATE" to "Alta", "EDIT" to "Edición", "CONFIRM" to "Confirmación", "PHOTO" to "Fotografía", "DIRECT_COUNT" to "Conteo directo", "APPROVED_COUNT" to "Conteo aprobado", "RECEPTION" to "Entrada", "PURCHASE" to "Compra recibida")
        val fields=mapOf("common_name" to "Nombre","price_cents" to "Precio MXN","quantity" to "Existencias","difference" to "Diferencia","initial_quantity" to "Cantidad inicial","image_id" to "Fotografía","ready" to "Habilitada","category_id" to "Categoría","description" to "Descripción","scientific_name" to "Nombre científico","light_type" to "Luz","watering_advice" to "Riego","is_active" to "Activa")
        fun value(k:String,v:JsonElement?)=if(v==null||v==JsonNull)"—" else if(k=="price_cents")"$${money(v.jsonPrimitive.long)}" else v.jsonPrimitive.content
        return rows.map { raw->val r=raw.jsonObject;val before=r.getValue("before_values").jsonObject;val after=r.getValue("after_values").jsonObject
            val changes=after.filter{(k,v)->before[k]!=v}.map{(k,v)->"${fields[k]?:k}: ${value(k,before[k])} → ${value(k,v)}"}.joinToString("\n")
            "${labels[r.text("action")]?:r.text("action")} · ${r.text("product_name")}\n${r.text("actor_name")} · ${r.text("created_at")}\n${r.text("reason")}\n$changes"
        }
    }
    override suspend fun save(plant:PlantWork,publish:Boolean):PlantWork=mutex.withLock {
        val s=scope();val capabilities=sessions.backend.value.context!!.capabilities
        require(plant.name.isNotBlank()) { "Indica el nombre de la planta." }
        if(publish)require("PUBLISH_CATALOGING" in capabilities) { "No tienes permiso para publicar." }
        val cents=plantCents(plant.price);val qty=if(plant.product)null else plantQuantity(plant.quantity)
        if(publish)require(plant.categoryId!=null&&plant.presentation.isNotBlank()) { "Completa presentación y categoría." }
        val fields=buildJsonObject { put("approved_name",plant.name.trim());put("presentation",plant.presentation.trim());put("category_id",plant.categoryId?.let(::JsonPrimitive)?:JsonNull);put("internal_code",plant.code);put("description",plant.description);put("suggested_price_cents",cents?.let(::JsonPrimitive)?:JsonNull);put("initial_quantity",qty?.let(::JsonPrimitive)?:JsonNull) }
        if(publish){
            require("MANAGE_PRICES" in capabilities) { "No tienes permiso para confirmar el precio." }
            var work=plant
            val directNew=!plant.product&&!plant.fromReceipt&&plant.linkProductId==null&&"MANAGE_INVENTORY" in capabilities&&
                get("inventory/permissions",s)["permissions"]?.jsonObject?.get("can_apply_count")?.jsonPrimitive?.booleanOrNull==true
            val initial=if(directNew)requireNotNull(plantQuantity(plant.quantity)){"Indica la cantidad inicial; incluye cero si no hay unidades."} else null
            if(directNew)require(cents!=null){"Indica el precio para habilitar la venta."}
            if(!work.product&&work.id==0L){
                val create=buildJsonObject {put("original_name",work.name.trim());put("fields",fields)}
                work=write(s,Attempt("/api/v1/cataloging","POST",create.toString(),UUID.randomUUID().toString(),work))
            }
            if(work.photoPath!=null)work=write(s,Attempt("/api/v1/${if(work.product)"products/${work.id}/catalog-photo" else "cataloging/${work.id}/photo"}","PUT","",UUID.randomUUID().toString(),work,true))
            val confirmFields=if(work.product)buildJsonObject {
                put("common_name",work.name.trim()+" · "+work.presentation.trim());put("category_id",requireNotNull(work.categoryId));put("description",work.description.trim())
            } else buildJsonObject {
                put("approved_name",work.name.trim());put("presentation",work.presentation.trim());put("category_id",requireNotNull(work.categoryId))
                put("internal_code",work.code.trim());put("description",work.description.trim())
            }
            val confirm=buildJsonObject {put("target",if(work.product)"product" else "draft");put("id",work.id);put("revision",work.revision)
                put("fields",confirmFields);put("price_cents",cents?:0L);initial?.let{put("initial_quantity",it)};if(!work.product)work.linkProductId?.let{put("product_id",it)} }
            work=write(s,Attempt("/api/v1/plants/confirm","POST",confirm.toString(),UUID.randomUUID().toString(),work))
            dao.put(PlantWorkEntity(s.actor,s.branch,json.encodeToString(work),null));return@withLock work
        }
        val path=if(plant.product)"products/${plant.id}" else if(plant.id==0L)"cataloging" else "cataloging/${plant.id}"
        val body=if(plant.product)buildJsonObject {
            put("common_name",plant.name.trim()+if(plant.presentation.isBlank())"" else " · ${plant.presentation.trim()}")
            plant.categoryId?.let { put("category_id",it) };put("description",plant.description)
            if("MANAGE_PRICES" in capabilities&&cents!=null)put("price_cents",cents)
        } else buildJsonObject {
            put("fields",fields)
            if(plant.id==0L)put("original_name",plant.name.trim()) else {put("revision",plant.revision);put("action","SAVE")}
        }
        var work=write(s,Attempt("/api/v1/$path",if(plant.id==0L)"POST" else "PATCH",body.toString(),UUID.randomUUID().toString(),plant))
        if(work.photoPath!=null)work=write(s,Attempt("/api/v1/${if(work.product)"products/${work.id}/catalog-photo" else "cataloging/${work.id}/photo"}","PUT","",UUID.randomUUID().toString(),work,true))
        dao.put(PlantWorkEntity(s.actor,s.branch,json.encodeToString(work),null));work
    }
}
private class PlantFailure(val status:Int,val code:String):IllegalStateException(when {
    status==401->"La sesión venció. Inicia sesión para comprobar el guardado."
    status==403->"No tienes permiso para esta acción."
    code=="CATALOGING_EDIT_CONFLICT"->"Otra persona modificó la ficha. Consulta la versión guardada antes de continuar."
    code=="INVENTORY_COUNT_STALE"->"Hubo movimientos después de abrir la ficha. Vuelve a consultarla y registra un nuevo conteo."
    code=="PRODUCT_PREPARATION_INCOMPLETE"->"Información guardada. Aún faltan requisitos de precio o inventario para habilitar la venta."
    else->"No se confirmó la operación. Comprueba el guardado antes de repetirla."
})
private fun JsonObject.text(key:String)=this[key]?.jsonPrimitive?.contentOrNull?:""
private fun JsonObject.long(key:String)=getValue(key).jsonPrimitive.long
private fun money(cents:Long)="${cents/100}.${(cents%100).toString().padStart(2,'0')}"
