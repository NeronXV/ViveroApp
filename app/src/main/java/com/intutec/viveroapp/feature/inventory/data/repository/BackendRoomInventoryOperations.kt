package com.intutec.viveroapp.feature.inventory.data.repository

import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.inventory.data.local.*
import com.intutec.viveroapp.feature.inventory.domain.repository.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

@Singleton
class BackendRoomInventoryOperations @Inject constructor(private val sessions:SessionStore,private val dao:BackendInventoryAttemptDao,private val remote:BackendInventoryGateway):BackendInventoryOperations {
    private val mutex=Mutex()
    private data class Access(val token:String,val identity:BackendSaleIdentity)
    private fun access(write:Boolean=true):Access {
        sessions.expireBackend(System.currentTimeMillis());val state=sessions.backend.value
        val session=checkNotNull(state.inventorySession(write)){"Actualiza los permisos de inventario."}
        return Access(session.token,BackendSaleIdentity(session.userId,requireNotNull(state.context?.branch).id))
    }
    override suspend fun pending():List<BackendInventoryPending> {
        val who=access(false);val rows=dao.pending(who.identity.userId,who.identity.branchId);check(access(false)==who)
        return rows.map { BackendInventoryPending(it.id,it.productId,BackendInventoryAction.valueOf(it.action),it.quantity,it.state) }
    }
    override suspend fun start(action:BackendInventoryAction,product:Long,quantity:Long,notes:String?):BackendInventoryReceipt=exclusive {
        val who=access();val attempt=BackendInventoryAttempt.create(who.identity,action,product,quantity,notes)
        check(access()==who)
        val id=withContext(NonCancellable){dao.enqueue(BackendInventoryAttemptEntity(actorId=who.identity.userId,branchId=who.identity.branchId,productId=product,action=action.name,quantity=quantity,notes=attempt.notes,key=attempt.key))}
        settle(id,who,recovery=false,retry=false)
    }
    override suspend fun recover(id:Long,retryMissing:Boolean):BackendInventoryReceipt=exclusive {settle(id,access(),true,retryMissing)}
    private suspend fun settle(id:Long,who:Access,recovery:Boolean,retry:Boolean):BackendInventoryReceipt {
        val row=checkNotNull(dao.load(id));check(row.actorId==who.identity.userId && row.branchId==who.identity.branchId)
        val attempt=BackendInventoryAttempt(who.identity,BackendInventoryAction.valueOf(row.action),row.productId,row.quantity,row.notes,row.key)
        check(dao.claim(id)==1){"La operación ya está siendo atendida."}
        return try {
            check(access()==who)
            val result=if(recovery) try {remote.result(who.token,attempt)} catch(missing:BackendInventoryResultMissing){
                if(!retry) throw missing
                check(access()==who);remote.submit(who.token,attempt)
            } else remote.submit(who.token,attempt)
            check(result.action==attempt.action && result.productId==attempt.productId && result.id in 1..4294967295L && result.quantityMilli==attempt.quantity*1000)
            check(dao.complete(id,result.id)==1);result
        }catch(cancelled:CancellationException){withContext(NonCancellable){dao.uncertain(id)};throw cancelled}
        catch(_:Exception){dao.uncertain(id);error("No se confirmó el movimiento. Conserva y consulta el intento antes de registrar otro.")}
    }
    private suspend fun <T> exclusive(action:suspend ()->T):T {
        check(mutex.tryLock()){"Hay una operación de inventario en curso."};return try{action()}finally{mutex.unlock()}
    }
}
