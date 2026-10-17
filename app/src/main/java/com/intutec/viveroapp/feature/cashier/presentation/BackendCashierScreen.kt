package com.intutec.viveroapp.feature.cashier.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.authorizedSession
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar
import com.intutec.viveroapp.core.designsystem.ViveroCard
import com.intutec.viveroapp.core.designsystem.ViveroSectionIntro
import com.intutec.viveroapp.feature.cashier.domain.repository.BackendCashierSale
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class PatioNoteState(val saleId: Long? = null, val loading: Boolean = false, val note: String? = null, val error: Boolean = false)

@HiltViewModel
class BackendCashierPatioViewModel @Inject constructor(
    private val sessions: SessionStore, private val transport: BackendApiTransport,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PatioNoteState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun open(sale: BackendCashierSale) {
        request?.cancel()
        val current = sessions.backend.value
        val session = current.authorizedSession("OPERATE_CASHIER", true) ?: run { clear(); return }
        val branchId = current.context?.branch?.id ?: run { clear(); return }
        mutableState.value = PatioNoteState(saleId = sale.id, loading = true)
        request = viewModelScope.launch {
            try {
                val response = transport.get("/api/v1/cashier/sales/${sale.id}", session.token, mapOf("view" to "patio"))
                check(response.status == 200)
                val body = Json.parseToJsonElement(response.body).jsonObject
                val detail = body["sale"]?.jsonObject
                check(body["schema_version"]?.jsonPrimitive?.intOrNull == 1 &&
                    detail?.get("id")?.jsonPrimitive?.longOrNull == sale.id &&
                    detail?.get("branch_id")?.jsonPrimitive?.longOrNull == branchId &&
                    detail?.get("folio")?.jsonPrimitive?.content == sale.folio &&
                    detail?.get("total_cents")?.jsonPrimitive?.longOrNull == sale.totalCents &&
                    detail?.get("status")?.jsonPrimitive?.content == "SENT_TO_CASHIER" && body.containsKey("patio_note"))
                val note = body["patio_note"]?.let { if (it.toString() == "null") null else it.jsonPrimitive.content }
                check(note == null || (note.codePointCount(0, note.length) <= 120 && note.none { it.code < 32 || it.code in 127..159 }))
                val latest = sessions.backend.value
                check(latest.authorizedSession("OPERATE_CASHIER", true)?.token == session.token && latest.context?.branch?.id == branchId)
                mutableState.value = PatioNoteState(saleId = sale.id, note = note)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = PatioNoteState(saleId = sale.id, error = true) }
        }
    }
    fun clear() { request?.cancel(); mutableState.value = PatioNoteState() }
}

@Composable
fun BackendCashierScreen(onBack: () -> Unit, onHistory: () -> Unit, onDetail: (Long) -> Unit, viewModel: BackendCashierViewModel = hiltViewModel(), patioViewModel: BackendCashierPatioViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val patio by patioViewModel.state.collectAsStateWithLifecycle()
    var sale by remember { mutableStateOf<BackendCashierSale?>(null) }
    var more by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf("CASH") }
    var amount by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var retryId by remember { mutableStateOf<Long?>(null) }
    var retireId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.enabled) { if (!state.enabled) { sale = null; retryId = null; retireId = null; patioViewModel.clear() } }
    Scaffold(topBar = { ViveroTopAppBar(title = "Caja", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ViveroSectionIntro("Caja de tu sucursal", "Abre un pedido pendiente y cobra.", modifier = Modifier.padding(bottom = 16.dp))
                if (state.loading || state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it) }
                state.receipt?.let { Text("Pago confirmado · ${it.folio}\nTotal: ${it.dueCents.asMxn()} · Recibido: ${it.receivedCents.asMxn()} · Cambio: ${it.changeCents.asMxn()}") }
                if (state.pending.isNotEmpty()) Text("Hay un intento guardado. Consulta su resultado antes de iniciar otro cobro.")
                TextButton({ more = !more }) { Text(if (more) "Ocultar más opciones" else "Más opciones") }
                if (more) {
                    OutlinedButton({ viewModel.refresh() }, enabled = !state.loading && !state.working) { Text("Actualizar") }
                    TextButton(onHistory, enabled = state.enabled && !state.working) { Text("Mis comprobantes") }
                }
            }
            items(state.pending, key = { it.id }) { pending ->
                ViveroCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Text("Pago pendiente de confirmar · Venta ${pending.saleId}")
                    Button({ viewModel.recover(pending.id) }, enabled = !state.working && !state.loading) { Text("Consultar resultado") }
                    if (more) {
                        TextButton({ retryId = pending.id }, enabled = state.enabled && !state.working && !state.loading) { Text("Reintentar el mismo cobro") }
                        TextButton({ retireId = pending.id }, enabled = state.enabled && !state.working && !state.loading) { Text("Cerrar intento") }
                    }
                } }
            }
            items(state.sales, key = { it.id }) { row ->
                ViveroCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Text(row.folio, style = MaterialTheme.typography.titleMedium); Text("Total: ${row.totalCents.asMxn()}")
                    Button({ sale = row; amount = ""; reference = ""; method = "CASH"; patioViewModel.open(row) }, enabled = state.enabled && !state.working && !state.loading && state.pending.isEmpty()) { Text("Abrir pedido") }
                    if (more) TextButton({ onDetail(row.id) }, enabled = !state.working) { Text("Ver comanda") }
                } }
            }
            item {
                if (state.sales.isEmpty() && !state.loading) Text("No hay comandas por cobrar.")
                if (state.next != null) TextButton({ viewModel.refresh(true) }, enabled = !state.working && !state.loading) { Text("Cargar más") }
            }
        }
    }
    retireId?.let { id ->
        AlertDialog(onDismissRequest = { retireId = null }, title = { Text("Cerrar intento de cobro") },
            text = { Text("Si el pago ya existe, se conservará su comprobante. Si no existe, el servidor bloqueará la clave para impedir un cobro tardío. No cancela la venta ni devuelve dinero. Un error deja el intento pendiente.") },
            confirmButton = { TextButton({ retireId = null; viewModel.retire(id) }, enabled = state.enabled && !state.working && !state.loading) { Text("Confirmar cierre") } },
            dismissButton = { TextButton({ retireId = null }) { Text("Conservar pendiente") } })
    }
    retryId?.let { id ->
        AlertDialog(onDismissRequest = { retryId = null }, title = { Text("Reintentar intento guardado") },
            text = { Text("Primero se consultará el resultado. Si no existe, se enviará exactamente el mismo cobro. Si la reserva venció, el intento seguirá guardado para revisión; no inicies otro cobro de esta venta.") },
            confirmButton = { TextButton({ retryId = null; viewModel.retry(id) }, enabled = state.enabled && !state.working && !state.loading) { Text("Reintentar") } },
            dismissButton = { TextButton({ retryId = null }) { Text("Cancelar") } })
    }
    sale?.let { selected ->
        AlertDialog(onDismissRequest = { sale = null; patioViewModel.clear() }, title = { Text("Pedido pendiente") }, text = { Column {
            Text("${selected.folio}\nTotal: ${selected.totalCents.asMxn()}")
            when {
                patio.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                patio.error -> Text("No se pudo leer la referencia de patio. Vuelve a consultar el pedido.", color = MaterialTheme.colorScheme.error)
                patio.saleId == selected.id -> Text("Referencia de patio: ${patio.note ?: "Sin referencia"}")
            }
            Row { listOf("CASH" to "Efectivo", "CARD" to "Tarjeta", "TRANSFER" to "Transferencia").forEach { (code, label) ->
                FilterChip(method == code, { method = code }, label = { Text(label) })
            } }
            if (method == "CASH") OutlinedTextField(amount, { if (it.length <= 18) amount = it }, label = { Text("Efectivo recibido (MXN)") }, singleLine = true)
            else OutlinedTextField(reference, { if (it.length <= 120) reference = it }, label = { Text("Referencia de operación; no datos de tarjeta") }, singleLine = true)
        } }, confirmButton = { Button({ sale = null; patioViewModel.clear(); viewModel.pay(selected.id, method, amount, reference) },
            enabled = !state.working && state.pending.isEmpty() && patio.saleId == selected.id && !patio.loading && !patio.error && (method != "CASH" || (amount.parseMxnCents() ?: 0) >= selected.totalCents) && (method != "TRANSFER" || reference.isNotBlank())) { Text("Cobrar") } },
            dismissButton = { TextButton({ sale = null; patioViewModel.clear() }) { Text("Cancelar") } })
    }
}
