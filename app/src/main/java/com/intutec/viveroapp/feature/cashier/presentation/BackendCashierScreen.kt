package com.intutec.viveroapp.feature.cashier.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.network.BackendApiTransport
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.authorizedSession
import com.intutec.viveroapp.core.designsystem.*
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
fun BackendCashierScreen(
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onDetail: (Long) -> Unit,
    viewModel: BackendCashierViewModel = hiltViewModel(),
    patioViewModel: BackendCashierPatioViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val patio by patioViewModel.state.collectAsStateWithLifecycle()
    var sale by remember { mutableStateOf<BackendCashierSale?>(null) }
    var more by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf("CASH") }
    var amount by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var retryId by remember { mutableStateOf<Long?>(null) }
    var retireId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.enabled) {
        if (!state.enabled) {
            sale = null
            retryId = null
            retireId = null
            patioViewModel.clear()
        }
    }

    Scaffold(
        topBar = {
            ViveroTopAppBar(
                title = "Caja",
                onBack = onBack,
                eyebrow = "SUCURSAL · COBROS Y COMPROBANTES"
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ViveroSectionIntro(
                    title = "Caja de tu sucursal",
                    subtitle = "Selecciona un pedido pendiente de comanda y procesa el cobro.",
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (state.loading || state.working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                state.error?.let {
                    SalesNotice(it, critical = true)
                }

                state.message?.let {
                    SalesNotice(it)
                }

                state.receipt?.let { receipt ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        "Pago confirmado",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Folio: ${receipt.folio}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total cobrado:")
                                Text(receipt.dueCents.asMxn(), fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Recibido:")
                                Text(receipt.receivedCents.asMxn(), fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cambio entregado:", fontWeight = FontWeight.Bold)
                                Text(receipt.changeCents.asMxn(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                if (state.pending.isNotEmpty()) {
                    SalesNotice(
                        "Hay un intento guardado. Consulta su resultado antes de iniciar otro cobro.",
                        critical = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { more = !more }) {
                        Icon(
                            if (more) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (more) "Ocultar opciones" else "Más opciones de caja")
                    }
                    if (more) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.refresh() },
                                enabled = !state.loading && !state.working
                            ) {
                                Text("Actualizar")
                            }
                            TextButton(
                                onClick = onHistory,
                                enabled = state.enabled && !state.working
                            ) {
                                Text("Mis comprobantes")
                            }
                        }
                    }
                }
            }

            // Pending recovery items
            items(state.pending, key = { it.id }) { pending ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Pago pendiente por confirmar · Venta ID ${pending.saleId}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = { viewModel.recover(pending.id) },
                            enabled = !state.working && !state.loading,
                            shape = CircleShape
                        ) {
                            Text("Consultar resultado")
                        }
                        if (more) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { retryId = pending.id },
                                    enabled = state.enabled && !state.working && !state.loading
                                ) {
                                    Text("Reintentar cobro")
                                }
                                TextButton(
                                    onClick = { retireId = pending.id },
                                    enabled = state.enabled && !state.working && !state.loading
                                ) {
                                    Text("Cerrar intento")
                                }
                            }
                        }
                    }
                }
            }

            // Sales list cards
            items(state.sales, key = { it.id }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    row.folio,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                            Text(
                                row.totalCents.asMxn(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    sale = row
                                    amount = ""
                                    reference = ""
                                    method = "CASH"
                                    patioViewModel.open(row)
                                },
                                enabled = state.enabled && !state.working && !state.loading && state.pending.isEmpty(),
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp)
                            ) {
                                Icon(Icons.Outlined.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Procesar cobro", fontWeight = FontWeight.Bold)
                            }

                            if (more) {
                                OutlinedIconButton(
                                    onClick = { onDetail(row.id) },
                                    enabled = !state.working,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = "Ver comanda")
                                }
                            }
                        }
                    }
                }
            }

            item {
                if (state.sales.isEmpty() && !state.loading) {
                    SalesEmpty(
                        title = "Sin comandas pendientes",
                        message = "No hay pedidos pendientes de cobro en esta sucursal."
                    )
                }
                if (state.next != null) {
                    OutlinedButton(
                        onClick = { viewModel.refresh(true) },
                        enabled = !state.working && !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Text("Cargar más pedidos")
                    }
                }
            }
        }
    }

    retireId?.let { id ->
        AlertDialog(
            onDismissRequest = { retireId = null },
            title = { Text("Cerrar intento de cobro") },
            text = { Text("Si el pago ya existe, se conservará su comprobante. Si no existe, el servidor bloqueará la clave para impedir un cobro tardío. No cancela la venta ni devuelve dinero. Un error deja el intento pendiente.") },
            confirmButton = {
                TextButton(
                    onClick = { retireId = null; viewModel.retire(id) },
                    enabled = state.enabled && !state.working && !state.loading
                ) {
                    Text("Confirmar cierre")
                }
            },
            dismissButton = {
                TextButton(onClick = { retireId = null }) {
                    Text("Conservar pendiente")
                }
            }
        )
    }

    retryId?.let { id ->
        AlertDialog(
            onDismissRequest = { retryId = null },
            title = { Text("Reintentar intento guardado") },
            text = { Text("Primero se consultará el resultado. Si no existe, se enviará exactamente el mismo cobro. Si la reserva venció, el intento seguirá guardado para revisión; no inicies otro cobro de esta venta.") },
            confirmButton = {
                TextButton(
                    onClick = { retryId = null; viewModel.retry(id) },
                    enabled = state.enabled && !state.working && !state.loading
                ) {
                    Text("Reintentar")
                }
            },
            dismissButton = {
                TextButton(onClick = { retryId = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    sale?.let { selected ->
        AlertDialog(
            onDismissRequest = { sale = null; patioViewModel.clear() },
            title = { Text("Cobro de comanda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Folio: ${selected.folio}", fontWeight = FontWeight.Bold)
                            Text("Total a cobrar: ${selected.totalCents.asMxn()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }

                    when {
                        patio.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        patio.error -> Text("No se pudo leer la referencia de patio. Vuelve a consultar el pedido.", color = MaterialTheme.colorScheme.error)
                        patio.saleId == selected.id -> Text("Referencia de patio: ${patio.note ?: "Sin referencia"}", style = MaterialTheme.typography.bodyMedium)
                    }

                    Text("Método de pago:", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("CASH" to "Efectivo", "CARD" to "Tarjeta", "TRANSFER" to "Transferencia").forEach { (code, label) ->
                            FilterChip(
                                selected = method == code,
                                onClick = { method = code },
                                label = { Text(label) }
                            )
                        }
                    }

                    if (method == "CASH") {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { if (it.length <= 18) amount = it },
                            label = { Text("Efectivo recibido (MXN)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = reference,
                            onValueChange = { if (it.length <= 120) reference = it },
                            label = { Text("Referencia de operación; no datos de tarjeta") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sale = null
                        patioViewModel.clear()
                        viewModel.pay(selected.id, method, amount, reference)
                    },
                    shape = CircleShape,
                    enabled = !state.working && state.pending.isEmpty() && patio.saleId == selected.id && !patio.loading && !patio.error && (method != "CASH" || (amount.parseMxnCents() ?: 0) >= selected.totalCents) && (method != "TRANSFER" || reference.isNotBlank())
                ) {
                    Text("Confirmar y cobrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { sale = null; patioViewModel.clear() }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
