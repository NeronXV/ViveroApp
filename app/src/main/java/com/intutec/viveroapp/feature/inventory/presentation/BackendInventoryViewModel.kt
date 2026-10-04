package com.intutec.viveroapp.feature.inventory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.domain.repository.BackendSaleIdentity
import com.intutec.viveroapp.feature.inventory.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackendInventoryUiState(val identity:BackendSaleIdentity?=null,val enabled:Boolean=false,val canWrite:Boolean=false,val loading:Boolean=false,val working:Boolean=false,
    val products:List<BackendInventoryItem> = emptyList(),val next:Long?=null,val pending:List<BackendInventoryPending> = emptyList(),
    val historyProduct:BackendInventoryItem?=null,val movements:List<BackendInventoryMovement> = emptyList(),val nextMovement:Long?=null,
    val receipt:BackendInventoryReceipt?=null,val error:String?=null)
@HiltViewModel
class BackendInventoryViewModel @Inject constructor(private val sessions:SessionStore,private val remote:BackendInventoryGateway,private val operations:BackendInventoryOperations):ViewModel() {
    private val _state=MutableStateFlow(BackendInventoryUiState());val state=_state.asStateFlow()
    private var generation=0L;private var operation:Job?=null
    private fun access():Pair<String,BackendSaleIdentity>? {
        val current=sessions.backend.value;val session=current.inventorySession() ?: return null
        return session.token to BackendSaleIdentity(session.userId,requireNotNull(current.context?.branch).id)
    }
    init {viewModelScope.launch {sessions.backend.collect {
        generation++;operation?.cancel();_state.value=BackendInventoryUiState(identity=access()?.second,enabled=access()!=null,canWrite=sessions.backend.value.inventorySession(true)!=null)
        if(_state.value.enabled)refresh()
    }}}
    fun refresh(more:Boolean=false) {
        val before=_state.value;if(more && before.next==null)return
        run {who,current->
            val pending=operations.pending();val page=remote.dashboard(who.first,who.second,if(more)before.next else null)
            if(current())_state.update {it.copy(products=(if(more)before.products else emptyList())+page.items,next=page.next,pending=pending)}
        }
    }
    fun history(product:BackendInventoryItem,more:Boolean=false) {
        val before=_state.value;if(more && before.nextMovement==null)return
        run {who,current->
            val page=remote.history(who.first,who.second,product.id,if(more)before.nextMovement else null)
            if(current())_state.update {it.copy(historyProduct=product,movements=(if(more)before.movements else emptyList())+page.items,nextMovement=page.next)}
        }
    }
    fun closeHistory(){operation?.cancel();generation++;_state.update {it.copy(historyProduct=null,movements=emptyList(),nextMovement=null,loading=false,working=false,error=null)}}
    fun start(action:BackendInventoryAction,product:Long,quantity:String,notes:String) {
        if(!_state.value.canWrite || _state.value.pending.isNotEmpty())return
        run(write=true) {who,current->
            require(Regex("^(?:0|[1-9][0-9]{0,10})$").matches(quantity)) {"Escribe unidades enteras."}
            val receipt=operations.start(action,product,quantity.toLong(),notes)
            afterReceipt(who,current,receipt)
        }
    }
    fun recover(id:Long,retry:Boolean=false) {
        if(!_state.value.canWrite)return
        run(write=true) {who,current->afterReceipt(who,current,operations.recover(id,retry))}
    }
    private suspend fun afterReceipt(who:Pair<String,BackendSaleIdentity>,current:()->Boolean,receipt:BackendInventoryReceipt) {
        if(current())_state.update {it.copy(receipt=receipt,historyProduct=null,movements=emptyList(),nextMovement=null)}
        val pending=operations.pending()
        if(current())_state.update {it.copy(pending=pending)}
        val page=remote.dashboard(who.first,who.second)
        if(current())_state.update {it.copy(products=page.items,next=page.next)}
    }
    fun retryRead(){val product=_state.value.historyProduct;if(product!=null)history(product) else refresh()}
    private fun run(write:Boolean=false,action:suspend (Pair<String,BackendSaleIdentity>,()->Boolean)->Unit) {
        val who=access() ?: return;if(operation?.isActive==true || (write && sessions.backend.value.inventorySession(true)==null))return
        val revision=generation;_state.update {it.copy(loading=!write,working=write,error=null,receipt=if(write)null else it.receipt)}
        operation=viewModelScope.launch {
            val job=kotlinx.coroutines.currentCoroutineContext()[Job]
            val current={revision==generation && job?.isActive==true && access()==who}
            try {action(who,current)}catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){if(current()) {
                _state.update {it.copy(error=if(write && it.receipt==null) "No se confirmó la operación. Consulta el intento guardado antes de registrar otro movimiento." else "No se pudo completar la consulta de inventario. Actualiza para consultar el saldo.")}
                try {val pending=operations.pending();if(current())_state.update {it.copy(pending=pending)}}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){}
            }}finally {if(revision==generation)_state.update {it.copy(loading=false,working=false)}}
        }
    }
}
