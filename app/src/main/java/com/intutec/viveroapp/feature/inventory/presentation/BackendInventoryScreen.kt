package com.intutec.viveroapp.feature.inventory.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intutec.viveroapp.core.designsystem.ViveroTopAppBar
import com.intutec.viveroapp.core.designsystem.ViveroCard
import com.intutec.viveroapp.core.designsystem.ViveroSectionIntro
import com.intutec.viveroapp.feature.inventory.domain.repository.*

internal fun inventoryQuantity(milli:Long):String = java.math.BigDecimal.valueOf(milli,3).stripTrailingZeros().toPlainString()
@Composable
fun BackendInventoryScreen(onBack:()->Unit,viewModel:BackendInventoryViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember {mutableStateOf<BackendInventoryItem?>(null)}
    var action by remember {mutableStateOf(BackendInventoryAction.RECEPTION)}
    var quantity by remember {mutableStateOf("")};var notes by remember {mutableStateOf("")}
    var retryId by remember {mutableStateOf<Long?>(null)}
    LaunchedEffect(state.identity,state.canWrite){selected=null;retryId=null}
    BackHandler(enabled=state.historyProduct!=null){viewModel.closeHistory()}
    Scaffold(topBar={ViveroTopAppBar(title="Inventario",onBack={if(state.historyProduct!=null)viewModel.closeHistory() else onBack()})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                ViveroSectionIntro("Existencias y movimientos", "Consulta tu sucursal y registra únicamente movimientos reales.", modifier = Modifier.padding(bottom = 16.dp))
                if(state.loading || state.working)LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                state.receipt?.let {r->Text(if(r.action==BackendInventoryAction.RECEPTION) "Recepción ${r.id} confirmada: ${inventoryQuantity(r.quantityMilli)} unidades." else "Conteo ${r.id} confirmado: ${inventoryQuantity(r.quantityMilli)} unidades registradas; diferencia ${inventoryQuantity(requireNotNull(r.adjustmentMilli))}.")}
                OutlinedButton(viewModel::retryRead,enabled=state.enabled && !state.loading && !state.working){Text("Actualizar")}
                if(!state.enabled)Text("Actualiza tus permisos para consultar el inventario.")
                if(state.enabled && !state.canWrite)Text("Tu acceso permite consultar existencias e historial.")
            }
            items(state.pending,key={"attempt-${it.id}"}){pending->ViveroCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){
                Text("Intento ${pending.id} sin confirmar · Producto ${pending.productId}")
                Text("${if(pending.action==BackendInventoryAction.RECEPTION) "Recepción" else "Conteo"}: ${pending.quantity} unidades")
                TextButton({viewModel.recover(pending.id)},enabled=state.canWrite && !state.loading && !state.working){Text("Consultar resultado")}
                TextButton({retryId=pending.id},enabled=state.canWrite && !state.loading && !state.working){Text("Reintentar operación original")}
            }}}
            val product=state.historyProduct
            if(product!=null) {
                item {Text("Historial · ${product.name}",style=MaterialTheme.typography.titleLarge)}
                items(state.movements,key={"movement-${it.id}"}){movement->ViveroCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){
                    val type=when(movement.type){"OPENING"->"Apertura";"RECEPTION"->"Recepción";"ADJUSTMENT_ADD"->"Ajuste positivo";"ADJUSTMENT_SUB"->"Ajuste negativo";"SALE"->"Venta";"REFUND"->"Devolución";else->movement.type}
                    Text("$type · ${inventoryQuantity(movement.quantityMilli)} unidades");Text(movement.createdAt)
                    movement.actorLabel?.let {Text(it)};movement.notes?.let {Text(it)}
                }}}
                item {
                    if(state.movements.isEmpty() && !state.loading && state.error==null)Text("No hay movimientos registrados.")
                    if(state.nextMovement!=null)TextButton({viewModel.history(product,true)},enabled=!state.loading && !state.working){Text("Más movimientos")}
                }
            } else {
                items(state.products,key={"product-${it.id}"}){row->ViveroCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){
                    Text(row.name,style=MaterialTheme.typography.titleMedium);Text(row.code)
                    Text("Existencia: ${inventoryQuantity(row.quantityMilli)} ${row.unit}\nMínimo: ${inventoryQuantity(row.minimumMilli)}")
                    if(row.low)Text("Inventario bajo",color=MaterialTheme.colorScheme.error)
                    row.updatedAt?.let {Text("Última actualización: $it")}
                    TextButton({viewModel.history(row)},enabled=!state.loading && !state.working){Text("Ver historial")}
                    if(state.canWrite)Row {
                        listOf(BackendInventoryAction.RECEPTION to "Recibir",BackendInventoryAction.COUNT to "Conteo físico").forEach {(kind,label)->
                            TextButton({selected=row;action=kind;quantity="";notes=""},enabled=!state.loading && !state.working && state.pending.isEmpty()){Text(label)}
                        }
                    }
                }}}
                item {
                    if(state.products.isEmpty() && !state.loading && state.error==null)Text("No hay productos disponibles.")
                    if(state.next!=null)TextButton({viewModel.refresh(true)},enabled=!state.loading && !state.working){Text("Más productos")}
                }
            }
        }
    }
    selected?.let {product->
        val count=quantity.toLongOrNull();val valid=Regex("^(?:0|[1-9][0-9]{0,10})$").matches(quantity) && count!=null && (action==BackendInventoryAction.COUNT || count>0) && (action!=BackendInventoryAction.COUNT || notes.trim().codePointCount(0,notes.trim().length)>=3)
        AlertDialog(onDismissRequest={selected=null},title={Text(if(action==BackendInventoryAction.RECEPTION) "Confirmar recepción" else "Confirmar conteo físico")},text={Column {
            Text(product.name)
            Text(if(action==BackendInventoryAction.RECEPTION) "La cantidad se sumará a la existencia de tu sucursal." else "Indica las unidades reales. El servidor registrará la diferencia, que puede disminuir la existencia.")
            OutlinedTextField(quantity,{if(it.length<=11)quantity=it},label={Text("Unidades enteras")},singleLine=true)
            OutlinedTextField(notes,{if(it.codePointCount(0,it.length)<=240)notes=it},label={Text(if(action==BackendInventoryAction.COUNT) "Motivo obligatorio" else "Notas opcionales")})
        }},confirmButton={TextButton({selected=null;viewModel.start(action,product.id,quantity,notes)},enabled=valid && state.canWrite && !state.working && !state.loading && state.pending.isEmpty()){Text("Confirmar")}},dismissButton={TextButton({selected=null}){Text("Cancelar")}})
    }
    retryId?.let {id->AlertDialog(onDismissRequest={retryId=null},title={Text("Reintentar intento guardado")},text={Text("Se consultará primero el resultado. Solo si no existe se enviarán la cantidad, motivo y clave originales. Un error conserva el intento; no registres otra operación para sustituirlo.")},
        confirmButton={TextButton({retryId=null;viewModel.recover(id,true)},enabled=state.canWrite && !state.working && !state.loading){Text("Reintentar")}},dismissButton={TextButton({retryId=null}){Text("Conservar")}})}
}
