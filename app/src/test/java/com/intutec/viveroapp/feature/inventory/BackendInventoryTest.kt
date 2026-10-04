package com.intutec.viveroapp.feature.inventory

import com.intutec.viveroapp.core.network.*
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.activeBackend
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.inventory.data.local.*
import com.intutec.viveroapp.feature.inventory.data.remote.BackendInventoryRemoteDataSource
import com.intutec.viveroapp.feature.inventory.data.repository.BackendRoomInventoryOperations
import com.intutec.viveroapp.feature.inventory.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackendInventoryTest {
    private val who=BackendSaleIdentity(2,3)
    private val token="a".repeat(43)
    private class Transport(var body:String):BackendApiTransport {
        var status=200;var path="";var sent="";var headers=emptyMap<String,String>()
        override suspend fun get(path:String,token:String,query:Map<String,String>):BackendApiResponse {this.path=path;return BackendApiResponse(status,body)}
        override suspend fun post(path:String,token:String,headers:Map<String,String>,body:String):BackendApiResponse {this.path=path;this.headers=headers;sent=body;return BackendApiResponse(status,this.body)}
    }
    private val item="""{"product_id":4,"product_name":"Demo","product_code":"DEMO","product_unit":"pieza","total_quantity":"0.125","minimum_stock":"1.000","is_low_stock":true,"balance_updated_at":null}"""
    private fun page(item:String=this.item,branch:Int=3)="""{"schema_version":1,"branch_id":$branch,"items":[$item],"has_more":false,"next_product_id":null}"""
    private fun attempt(action:BackendInventoryAction=BackendInventoryAction.COUNT,qty:Long=0)=BackendInventoryAttempt(who,action,4,qty,"Demo","b".repeat(64))
    @Test fun dashboardKeepsExactFractionalBalances()=runTest {
        val source=BackendInventoryRemoteDataSource(Transport(page()))
        assertEquals(125L,source.dashboard(token,who).items.single().quantityMilli)
    }
    @Test fun rejectsWrongBranchInvalidDecimalsAndLowStockFlags()=runTest {
        for(body in listOf(page(branch=9),page(item.replace("0.125","0.12")),page(item.replace("0.125","-0.125")),page(item.replace("true","false")),page(item.replace("\"product_id\":4","\"product_id\":\"4\"")))) {
            try {BackendInventoryRemoteDataSource(Transport(body)).dashboard(token,who);fail("Invalid dashboard")}catch(_:IllegalStateException){}
        }
    }
    @Test fun zeroCountAcceptsSignedAdjustmentAndRecoversOriginalBodyAndHeaders()=runTest {
        val transport=Transport("""{"schema_version":1,"idempotent_replay":true,"count_id":5,"product_id":4,"previous_quantity":"2.125","counted_quantity":"0.000","adjustment_quantity":"-2.125","total_quantity":"0.000"}""")
        val source=BackendInventoryRemoteDataSource(transport);val a=attempt()
        assertEquals(-2125L,source.submit(token,a).adjustmentMilli)
        val original=transport.sent;source.result(token,a)
        assertEquals(original,transport.sent);assertEquals("/api/v1/inventory/counts/result",transport.path)
        assertEquals(a.key,transport.headers["Idempotency-Key"]);assertEquals("2",transport.headers["X-Expected-Actor-Id"]);assertEquals("3",transport.headers["X-Expected-Branch-Id"])
        transport.body=transport.body.replace("-2.125","-2.000")
        try {source.result(token,a);fail("Broken arithmetic accepted")}catch(_:IllegalStateException){}
    }
    @Test fun onlyExactMissingResultPermitsRetry()=runTest {
        val transport=Transport("""{"error":"INVENTORY_OPERATION_NOT_FOUND"}""");transport.status=404
        try {BackendInventoryRemoteDataSource(transport).result(token,attempt());fail("Missing result")}catch(_:BackendInventoryResultMissing){}
        transport.body="""{"error":"NOT_FOUND"}"""
        try {BackendInventoryRemoteDataSource(transport).result(token,attempt());fail("Generic error")}catch(error:IllegalStateException){assertFalse(error is BackendInventoryResultMissing)}
    }
    @Test fun historyPreservesNegativeSaleAndRejectsWrongProduct()=runTest {
        val body="""{"schema_version":1,"branch_id":3,"items":[{"id":6,"product_id":4,"product_name":"Demo","product_code":"DEMO","movement_type":"SALE","quantity":"-2.000","notes":null,"created_at":"2026-10-02T12:00:00Z","created_by_label":null}],"has_more":false,"next_before_id":null}"""
        assertEquals(-2000L,BackendInventoryRemoteDataSource(Transport(body)).history(token,who,4).items.single().quantityMilli)
        try {BackendInventoryRemoteDataSource(Transport(body)).history(token,who,5);fail("Wrong product")}catch(_:IllegalStateException){}
    }
    @Test fun historyAcceptsHistoricalTransfersWithTheirSign()=runTest {
        val body="""{"schema_version":1,"branch_id":3,"items":[{"id":6,"product_id":4,"product_name":"Demo","product_code":"DEMO","movement_type":"TRANSFER_OUT","quantity":"-2.000","notes":null,"created_at":"2026-10-02T12:00:00Z","created_by_label":null}],"has_more":false,"next_before_id":null}"""
        assertEquals(-2000L,BackendInventoryRemoteDataSource(Transport(body)).history(token,who,4).items.single().quantityMilli)
        assertEquals(2000L,BackendInventoryRemoteDataSource(Transport(body.replace("TRANSFER_OUT","TRANSFER_IN").replace("-2.000","2.000"))).history(token,who,4).items.single().quantityMilli)
        try {BackendInventoryRemoteDataSource(Transport(body.replace("-2.000","2.000"))).history(token,who,4);fail("Wrong transfer sign")}catch(_:IllegalStateException){}
    }
    private class Dao:BackendInventoryAttemptDao() {
        val rows=mutableMapOf<Long,BackendInventoryAttemptEntity>()
        override suspend fun insert(row:BackendInventoryAttemptEntity):Long {val id=rows.size.toLong()+1;rows[id]=row.copy(id=id);return id}
        override suspend fun load(id:Long)=rows[id]
        override suspend fun pending(actor:Long,branch:Long)=rows.values.filter {it.actorId==actor && it.branchId==branch && it.state!="SUCCEEDED"}
        override suspend fun claim(id:Long):Int {val row=rows.getValue(id);if(row.state !in setOf("PENDING","UNCERTAIN"))return 0;rows[id]=row.copy(state="SYNCING");return 1}
        override suspend fun uncertain(id:Long):Int {val row=rows.getValue(id);if(row.state!="SYNCING")return 0;rows[id]=row.copy(state="UNCERTAIN");return 1}
        override suspend fun complete(id:Long,server:Long):Int {val row=rows.getValue(id);if(row.state!="SYNCING")return 0;rows[id]=row.copy(state="SUCCEEDED",serverId=server);return 1}
    }
    private class Remote(val dao:Dao):BackendInventoryGateway {
        var posts=0;var reads=0;var error:Exception?=null;var recoveryError:Exception?=null;var original:BackendInventoryAttempt?=null
        override suspend fun dashboard(token:String,identity:BackendSaleIdentity,after:Long?)=BackendInventoryPage(emptyList(),null)
        override suspend fun history(token:String,identity:BackendSaleIdentity,product:Long,before:Long?)=BackendInventoryHistory(emptyList(),null)
        private fun receipt(a:BackendInventoryAttempt)=BackendInventoryReceipt(a.action,5,a.productId,a.quantity*1000,a.quantity*1000)
        override suspend fun submit(token:String,attempt:BackendInventoryAttempt):BackendInventoryReceipt {
            assertTrue(dao.rows.values.any {it.key==attempt.key && it.quantity==attempt.quantity && it.notes==attempt.notes});posts++;original=attempt;error?.let {throw it};return receipt(attempt)
        }
        override suspend fun result(token:String,attempt:BackendInventoryAttempt):BackendInventoryReceipt {
            reads++;assertEquals(original?.key,attempt.key);assertEquals(original?.quantity,attempt.quantity);assertEquals(original?.notes,attempt.notes);recoveryError?.let {throw it};return receipt(attempt)
        }
    }
    private fun sessions()=SessionStore().also {activeBackend(it,permissions=setOf("MANAGE_INVENTORY"))}
    @Test fun lostReplyIsDurableBlocksNewMovementAndRecoversWithoutAnotherPost()=runTest {
        val sessions=sessions();val dao=Dao();val remote=Remote(dao);remote.error=IllegalStateException()
        val service=BackendRoomInventoryOperations(sessions,dao,remote)
        try {service.start(BackendInventoryAction.RECEPTION,4,2," Demo ")}catch(_:IllegalStateException){}
        assertEquals("UNCERTAIN",dao.rows.getValue(1).state)
        try {service.start(BackendInventoryAction.COUNT,4,0,"Demo");fail("Second operation")}catch(_:IllegalStateException){}
        assertEquals(1,remote.posts)
        assertEquals(5L,BackendRoomInventoryOperations(sessions,dao,remote).recover(1).id)
        assertEquals(1,remote.posts);assertEquals("SUCCEEDED",dao.rows.getValue(1).state)
    }
    @Test fun explicitRetryRequiresMissingObservationAndKeepsOriginalKey()=runTest {
        val dao=Dao();val remote=Remote(dao);remote.error=IllegalStateException();val service=BackendRoomInventoryOperations(sessions(),dao,remote)
        try {service.start(BackendInventoryAction.COUNT,4,0,"Demo")}catch(_:IllegalStateException){}
        val key=dao.rows.getValue(1).key;remote.error=null;remote.recoveryError=IllegalStateException("network")
        try {service.recover(1,true);fail("Ambiguous result")}catch(_:IllegalStateException){}
        assertEquals(1,remote.posts)
        remote.recoveryError=BackendInventoryResultMissing()
        try {service.recover(1);fail("Must not submit without explicit retry")}catch(_:IllegalStateException){}
        assertEquals(1,remote.posts)
        service.recover(1,true);assertEquals(2,remote.posts);assertEquals(key,remote.original?.key);assertEquals(1,dao.rows.size)
    }
    @Test fun otherActorAndReadOnlyPermissionCannotRecoverOrWrite()=runTest {
        val sessions=sessions();val dao=Dao();val remote=Remote(dao);remote.error=IllegalStateException();val service=BackendRoomInventoryOperations(sessions,dao,remote)
        try {service.start(BackendInventoryAction.COUNT,4,0,"Demo")}catch(_:IllegalStateException){}
        activeBackend(sessions,actor=9,permissions=setOf("MANAGE_INVENTORY"))
        try {service.recover(1);fail("Other actor")}catch(_:IllegalStateException){}
        assertTrue(service.pending().isEmpty());assertEquals(0,remote.reads)
        activeBackend(sessions,permissions=setOf("VIEW_INVENTORY_ALERTS"))
        assertEquals(1,service.pending().size)
        try {service.recover(1);fail("Read only")}catch(_:IllegalStateException){}
        assertEquals(0,remote.reads)
    }
    @Test fun cancellationKeepsSavedAttemptAndPropagates()=runTest {
        val dao=Dao();val remote=Remote(dao);remote.error=CancellationException()
        try {BackendRoomInventoryOperations(sessions(),dao,remote).start(BackendInventoryAction.RECEPTION,4,2,null);fail("Cancellation swallowed")}catch(_:CancellationException){}
        assertEquals("UNCERTAIN",dao.rows.getValue(1).state)
    }
    @Test fun invalidQuantityOrCountReasonNeverPersistsOrPosts()=runTest {
        val dao=Dao();val remote=Remote(dao);val service=BackendRoomInventoryOperations(sessions(),dao,remote)
        for(action in listOf<suspend ()->Unit>({service.start(BackendInventoryAction.RECEPTION,4,0,null)},{service.start(BackendInventoryAction.COUNT,4,-1,"Demo")},{service.start(BackendInventoryAction.COUNT,4,0,"ab")},{service.start(BackendInventoryAction.COUNT,4,0,"bad\nreason")})) {
            try {action();fail("Invalid operation")}catch(_:IllegalArgumentException){}
        }
        assertTrue(dao.rows.isEmpty());assertEquals(0,remote.posts)
    }
}
