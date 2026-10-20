package com.intutec.viveroapp.feature.plants

import com.intutec.viveroapp.core.database.ViveroDatabase
import com.intutec.viveroapp.core.network.*
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.activeBackend
import com.intutec.viveroapp.feature.plants.data.PlantRepository
import com.intutec.viveroapp.feature.plants.data.local.*
import com.intutec.viveroapp.feature.plants.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class PlantRepositoryTest {
    private class Memory : PlantWorkDao {
        val rows=mutableMapOf<Pair<Long,Long>,PlantWorkEntity>()
        override suspend fun get(actor:Long,branch:Long)=rows[actor to branch]
        override suspend fun put(value:PlantWorkEntity){rows[value.actorId to value.branchId]=value}
    }
    private class Api : BackendApiTransport {
        data class Write(val path:String,val method:String,val headers:Map<String,String>,val body:String)
        val writes=mutableListOf<Write>()
        var lose=true
        var afterWrite:()->Unit={}
        var writeResponse: ((String)->String)? = null
        var direct=false
        var draftPhotoHeaders:Map<String,String>? = null
        override suspend fun plantDraftImage(draftId:Long,token:String,headers:Map<String,String>):ByteArray {
            assertEquals(7L,draftId)
            draftPhotoHeaders=headers
            return byteArrayOf(1,2,3)
        }
        override suspend fun post(path:String,token:String,headers:Map<String,String>,body:String)=error("Unexpected generic write")
        override suspend fun plantRead(path:String,token:String,headers:Map<String,String>,query:Map<String,String>):BackendApiResponse {
            assertEquals("2",headers["X-Expected-Actor-Id"]);assertEquals("3",headers["X-Expected-Branch-Id"])
            if(path.endsWith("/inventory/permissions"))return BackendApiResponse(200,"""{"permissions":{"can_apply_count":$direct}}""")
            return BackendApiResponse(200,"""{"items":[],"next_after_id":null}""")
        }
        override suspend fun plantWrite(path:String,method:String,token:String,headers:Map<String,String>,body:ByteArray):BackendApiResponse {
            writes+=Write(path,method,headers,body.toString(Charsets.UTF_8));afterWrite()
            if(lose)throw java.io.IOException("Lost response")
            writeResponse?.let { return BackendApiResponse(200,it(path)) }
            return BackendApiResponse(200,if(path.contains("/products/"))"""{"revision":3}""" else """{"id":7,"original_name":"Ficus","fields":{},"revision":2,"status":"DRAFT","has_image":false}""")
        }
    }
    private val permissions=setOf("MANAGE_PRODUCTS","MANAGE_PRICES","PUBLISH_CATALOGING")
    @Test fun directRegistrationIncludesExplicitZeroWithoutASecondInventoryWrite()=runTest {
        val store=SessionStore();activeBackend(store,permissions=permissions+"MANAGE_INVENTORY")
        val api=Api().apply {lose=false;direct=true}
        repo(api,store,Memory()).save(PlantWork(id=7,name="Ficus",presentation="Maceta",categoryId=1,price="25.50",quantity="0"),true)
        assertEquals(listOf("/api/v1/plants/confirm"),api.writes.map{it.path})
        assertTrue(api.writes.single().body.contains("\"initial_quantity\":0"))
    }
    @Test fun lostDirectCountRecoversExactBaselineAndKey()=runTest {
        val store=SessionStore();activeBackend(store,permissions=permissions+"MANAGE_INVENTORY")
        val api=Api();val memory=Memory();val repository=repo(api,store,memory)
        try {repository.count(PlantWork(id=9,product=true,quantity="5.000",baselineMovementId=12),"4","Conteo físico");fail()}catch(_:java.io.IOException){}
        assertTrue(repository.hasPending())
        api.lose=false;api.writeResponse={"""{"status":"APPLIED","observed_quantity":"4.000"}"""}
        assertEquals("4.000",repo(api,store,memory).recover()?.quantity)
        assertEquals(api.writes[0],api.writes[1])
        assertTrue(api.writes.last().body.contains("\"baseline_movement_id\":12"))
        assertFalse(repository.hasPending())
    }
    private fun repo(api:Api,store:SessionStore,memory:Memory):PlantRepository {
        val database=mock(ViveroDatabase::class.java)
        `when`(database.plantWorkDao()).thenReturn(memory)
        return PlantRepository(api,store,database)
    }
    @Test fun lostCreateSurvivesRepositoryRecreationAndUsesSameKeyAndBody()=runTest {
        val store=SessionStore();activeBackend(store,permissions=permissions)
        val api=Api();val memory=Memory();val first=repo(api,store,memory)
        try{first.save(PlantWork(name="Ficus"),false);fail()}catch(_:java.io.IOException){}
        assertTrue(first.hasPending())
        val reopened=repo(api,store,memory);api.lose=false
        assertEquals(7L,reopened.recover()?.id)
        assertEquals(api.writes[0],api.writes[1]);assertFalse(reopened.hasPending())
    }
    @Test fun productCompletionNeverWritesInventoryAndCarriesRevision()=runTest {
        val store=SessionStore();activeBackend(store,permissions=permissions)
        val api=Api().apply{lose=false};val repository=repo(api,store,Memory())
        repository.save(PlantWork(id=9,product=true,name="Ficus",price="25.50",quantity="6.000",revision=2),false)
        assertEquals(listOf("/api/v1/products/9"),api.writes.map{it.path})
        assertEquals("\"2\"",api.writes.single().headers["If-Match"])
        assertFalse(api.writes.single().body.contains("quantity"))
    }
    @Test fun changedSessionCannotConsumeOrReplayOriginalAttempt()=runTest {
        val store=SessionStore();activeBackend(store,permissions=permissions)
        val api=Api().apply{lose=false;afterWrite={activeBackend(store,actor=8,permissions=permissions)}}
        val memory=Memory();val repository=repo(api,store,memory)
        try{repository.save(PlantWork(name="Ficus"),false);fail()}catch(_:IllegalStateException){}
        assertFalse(repository.hasPending());assertNull(repository.recover())
        activeBackend(store,permissions=permissions);assertTrue(repository.hasPending())
        api.afterWrite={};assertEquals(7L,repository.recover()?.id)
    }
    @Test fun listSendsActorAndBranchForScopedCatalogingReads()=runTest {
        val store=SessionStore();activeBackend(store,permissions=permissions)
        assertTrue(repo(Api(),store,Memory()).list().plants.isEmpty())
    }
    @Test fun moneyAndCountsNeverRoundOrInventValues() {
        assertNull(plantCents(""));assertNull(plantQuantity(""))
        assertEquals(1234L,plantCents("12.34"));assertEquals(0L,plantQuantity("0"))
        for(value in listOf("-1","1.234","NaN","1,000"))try{plantCents(value);fail(value)}catch(_:IllegalArgumentException){}
        for(value in listOf("1.5","-1","1000001"))try{plantQuantity(value);fail(value)}catch(_:IllegalArgumentException){}
    }
    @Test fun publishingPhotoKeepsUnsavedFieldsAndSelectedCatalogMatch()=runTest {
        val store=SessionStore();activeBackend(store,permissions=permissions)
        val photo=java.io.File.createTempFile("plant-test-", ".jpg")
        try {
            photo.writeBytes(byteArrayOf(1,2,3))
            val api=Api().apply {
                lose=false
                writeResponse={ path ->
                    if(path.endsWith("/photo")) """{"id":7,"original_name":"Anterior","fields":{},"revision":3,"status":"DRAFT","has_image":true}"""
                    else """{"id":7,"original_name":"Ficus","fields":{},"revision":4,"status":"LINKED","has_image":true,"publication":{"ready":false,"needs_initial_count":true}}"""
                }
            }
            val saved=repo(api,store,Memory()).save(PlantWork(id=7,name="Ficus editado",presentation="Maceta",categoryId=2,price="25.50",description="Cuidados nuevos",linkProductId=9,photoPath=photo.absolutePath),true)
            val confirmation=kotlinx.serialization.json.Json.parseToJsonElement(api.writes.last().body).toString()
            assertTrue(confirmation.contains("\"approved_name\":\"Ficus editado\""))
            assertTrue(confirmation.contains("\"description\":\"Cuidados nuevos\""))
            assertTrue(confirmation.contains("\"product_id\":9"))
            assertTrue(confirmation.contains("\"revision\":3"))
            assertEquals("LINKED",saved.status)
            assertTrue(saved.needsInitialCount)
            assertNull(saved.photoPath)
        } finally { photo.delete() }
    }
    @Test fun creatingDraftPreservesSelectedCatalogMatchForConfirmation()=runTest {
        val store=SessionStore();activeBackend(store,permissions=permissions)
        val api=Api().apply {
            lose=false
            writeResponse={ """{"id":7,"original_name":"Ficus","fields":{"presentation":"Maceta","category_id":2},"revision":2,"status":"DRAFT","has_image":false}""" }
        }
        repo(api,store,Memory()).save(PlantWork(name="Ficus",presentation="Maceta",categoryId=2,linkProductId=9),true)
        assertTrue(api.writes.last().body.contains("\"product_id\":9"))
    }
    @Test fun savedDraftPhotoIsReadWithActorAndBranchScope()=runTest {
        val store=SessionStore();activeBackend(store,permissions=permissions)
        val api=Api()
        val repository=repo(api,store,Memory())
        assertArrayEquals(byteArrayOf(1,2,3),repository.preview(PlantWork(id=7,hasImage=true)))
        assertEquals(mapOf("X-Expected-Actor-Id" to "2","X-Expected-Branch-Id" to "3"),api.draftPhotoHeaders)
    }
}
