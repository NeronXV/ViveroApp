package com.intutec.viveroapp.feature.inventory

import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.activeBackend
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.inventory.domain.repository.*
import com.intutec.viveroapp.feature.inventory.presentation.BackendInventoryViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackendInventoryViewModelTest {
    @get:Rule val main=MainDispatcherRule()
    private class Remote:BackendInventoryGateway {
        var reads=0;var gate:CompletableDeferred<Unit>?=null
        override suspend fun dashboard(token:String,identity:BackendSaleIdentity,after:Long?):BackendInventoryPage {
            reads++;val captured=gate;captured?.let {withContext(NonCancellable){it.await()}}
            return BackendInventoryPage(listOf(BackendInventoryItem(identity.userId,"Demo","DEMO","pieza",1000,0,false,null)),null)
        }
        override suspend fun history(token:String,identity:BackendSaleIdentity,product:Long,before:Long?)=BackendInventoryHistory(emptyList(),null)
        override suspend fun submit(token:String,attempt:BackendInventoryAttempt):BackendInventoryReceipt=error("Not a VM mutation")
        override suspend fun result(token:String,attempt:BackendInventoryAttempt):BackendInventoryReceipt=error("Not a VM mutation")
    }
    private class Operations:BackendInventoryOperations {
        var writes=0;var rows=emptyList<BackendInventoryPending>()
        override suspend fun pending()=rows
        override suspend fun start(action:BackendInventoryAction,product:Long,quantity:Long,notes:String?):BackendInventoryReceipt {writes++;return BackendInventoryReceipt(action,5,product,quantity*1000,quantity*1000)}
        override suspend fun recover(id:Long,retryMissing:Boolean):BackendInventoryReceipt=error("Not requested")
    }
    @Test fun lateOldAccountDashboardCannotReplaceNewAccountAndDialogIdentityChanges()=runTest {
        val sessions=SessionStore();activeBackend(sessions,permissions=setOf("MANAGE_INVENTORY"))
        val remote=Remote();val operations=Operations();val vm=BackendInventoryViewModel(sessions,remote,operations)
        testScheduler.runCurrent();val gate=CompletableDeferred<Unit>();remote.gate=gate
        vm.refresh();testScheduler.runCurrent();remote.gate=null
        activeBackend(sessions,actor=9,permissions=setOf("MANAGE_INVENTORY"));testScheduler.runCurrent()
        gate.complete(Unit);testScheduler.runCurrent()
        assertEquals(9L,vm.state.value.identity?.userId);assertEquals(9L,vm.state.value.products.single().id)
    }
    @Test fun readOnlyPermissionAndLostCapabilityBlockMutations()=runTest {
        val sessions=SessionStore();activeBackend(sessions,permissions=setOf("VIEW_INVENTORY_ALERTS"))
        val remote=Remote();val operations=Operations();val vm=BackendInventoryViewModel(sessions,remote,operations)
        testScheduler.runCurrent();assertTrue(vm.state.value.enabled);assertFalse(vm.state.value.canWrite)
        vm.start(BackendInventoryAction.RECEPTION,4,"2","");testScheduler.runCurrent();assertEquals(0,operations.writes)
        activeBackend(sessions,permissions=emptySet());testScheduler.runCurrent();vm.refresh();testScheduler.runCurrent()
        assertFalse(vm.state.value.enabled);assertTrue(vm.state.value.products.isEmpty());assertEquals(1,remote.reads)
    }
    @Test fun nativeWritesStayDisabledAndSavedAttemptsArePreserved()=runTest {
        val sessions=SessionStore();activeBackend(sessions,permissions=setOf("MANAGE_INVENTORY"))
        val remote=Remote();val operations=Operations();val vm=BackendInventoryViewModel(sessions,remote,operations)
        testScheduler.runCurrent();vm.start(BackendInventoryAction.COUNT,4,"0.5","Demo");testScheduler.runCurrent()
        assertEquals(0,operations.writes);assertFalse(vm.state.value.canWrite)
        operations.rows=listOf(BackendInventoryPending(1,4,BackendInventoryAction.COUNT,0,"UNCERTAIN"));vm.refresh();testScheduler.runCurrent()
        vm.start(BackendInventoryAction.RECEPTION,4,"2","");testScheduler.runCurrent();assertEquals(0,operations.writes)
        assertEquals(operations.rows,vm.state.value.pending)
    }
}
