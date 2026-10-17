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
            readyForSale=r["publication"]?.jsonObject?.get("ready")?.jsonPrimitive?.booleanOrNull==true)
    }
    private fun product(r:JsonObject):PlantWork {
        val name=r.text("common_name");val split=name.lastIndexOf(" · ")
        return PlantWork(id=r.long("id"),product=true,name=if(split<0)name else name.take(split),presentation=if(split<0)"" else name.substring(split+3),categoryId=r.long("category_id"),price=r.long("price_cents").takeIf{it>0}?.let(::money)?:"",hasImage=r["image"]!=null&&r["image"]!=JsonNull,status="PRODUCT",description=r.text("description"),code=r.text("internal_code"),
            imageId=r["image"]?.takeIf{it!=JsonNull}?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull)
    }
    override suspend fun preview(imageId:Long):ByteArray? {
        val s=scope();val bytes=api.plantImage(imageId,s.token);unchanged(s);return bytes
    }
    override suspend fun list():PlantList {
        val s=scope()
        val categories=pages("categories",s,mapOf("status" to "active")).map { PlantCategory(it.long("id"),it.text("name")) }
        val drafts=pages("cataloging",s,mapOf("status" to "ALL")).filter { it.text("status") in listOf("DRAFT","REVIEW") }.map(::draft)
        val rows=pages("products",s,mapOf("status" to "all"))
        val catalog=rows.map(::product)
        val products=rows.filter { it["image"]==JsonNull || it["is_active"]?.jsonPrimitive?.booleanOrNull==false || it.long("price_cents")==0L }.map(::product)
        unchanged(s);return PlantList(drafts+products,categories,catalog)
    }
    override suspend fun open(plant:PlantWork):PlantWork {
        val s=scope()
        if(!plant.product)return draft(get("cataloging/${plant.id}",s))
        val before=get("products/${plant.id}/edit-version",s).long("revision").toInt()
        val row=pages("products",s,mapOf("status" to "all")).firstOrNull { it.long("id")==plant.id }?:error("No se encontró la planta.")
        val prep=get("products/${plant.id}/preparation",s)
        val after=get("products/${plant.id}/edit-version",s).long("revision").toInt()
        check(before==after) { "La ficha cambió. Vuelve a consultarla." }
        val quantity=if("MANAGE_INVENTORY" in sessions.backend.value.context!!.capabilities)get("inventory/count-baseline",s,mapOf("product_id" to plant.id.toString())).text("quantity") else "No disponible"
        return product(row).copy(revision=after,status=prep.text("commercial_state"),quantity=quantity,
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
            val updated=if(!a.work.product)draft(result).copy(photoPath=if(a.photo)null else a.work.photoPath)
                else a.work.copy(revision=result["revision"]?.jsonPrimitive?.intOrNull?:a.work.revision,hasImage=a.work.hasImage||a.photo,photoPath=if(a.photo)null else a.work.photoPath,
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
    override suspend fun save(plant:PlantWork,publish:Boolean):PlantWork=mutex.withLock {
        val s=scope();val capabilities=sessions.backend.value.context!!.capabilities
        require(plant.name.isNotBlank()) { "Indica el nombre de la planta." }
        if(publish)require("PUBLISH_CATALOGING" in capabilities) { "No tienes permiso para publicar." }
        val cents=plantCents(plant.price);val qty=if(plant.product||publish)null else plantQuantity(plant.quantity)
        if(publish)require(plant.categoryId!=null&&plant.presentation.isNotBlank()) { "Completa presentación y categoría." }
        val fields=buildJsonObject { put("approved_name",plant.name.trim());put("presentation",plant.presentation.trim());put("category_id",plant.categoryId?.let(::JsonPrimitive)?:JsonNull);put("internal_code",plant.code);put("description",plant.description);put("suggested_price_cents",cents?.let(::JsonPrimitive)?:JsonNull);put("initial_quantity",qty?.let(::JsonPrimitive)?:JsonNull) }
        if(publish){
            require("MANAGE_PRICES" in capabilities) { "No tienes permiso para confirmar el precio." }
            var work=plant
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
                put("fields",confirmFields);put("price_cents",cents?:0L);if(!work.product)work.linkProductId?.let{put("product_id",it)} }
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
    code=="PRODUCT_PREPARATION_INCOMPLETE"->"Información guardada. Aún faltan requisitos de precio o inventario para habilitar la venta."
    else->"No se confirmó la operación. Comprueba el guardado antes de repetirla."
})
private fun JsonObject.text(key:String)=this[key]?.jsonPrimitive?.contentOrNull?:""
private fun JsonObject.long(key:String)=getValue(key).jsonPrimitive.long
private fun money(cents:Long)="${cents/100}.${(cents%100).toString().padStart(2,'0')}"
