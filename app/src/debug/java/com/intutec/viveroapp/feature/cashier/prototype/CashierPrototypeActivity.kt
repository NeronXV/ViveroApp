package com.intutec.viveroapp.feature.cashier.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.intutec.viveroapp.core.common.asMxn
import com.intutec.viveroapp.core.designsystem.ViveroMark
import com.intutec.viveroapp.ui.theme.ViveroAppTheme

private val Forest = Color(0xFF234D3C)
private val Sage = Color(0xFF789B78)
private val PaleSage = Color(0xFFDDE9DB)
private val Cream = Color(0xFFF7F2E8)
private val Paper = Color(0xFFFFFCF6)
private val Terracotta = Color(0xFFC97754)
private val Ink = Color(0xFF24312B)
private val MutedInk = Color(0xFF637068)

class CashierPrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = CashierScenario.from(intent.getStringExtra("scenario"))
        setContent {
            ViveroAppTheme(darkTheme = false, dynamicColor = false) {
                CashierPrototype(scenario = scenario)
            }
        }
    }
}

private enum class CashierScenario {
    QUEUE, EMPTY, LOADING, ERROR, DETAIL, PAYMENT, CONFIRMATION;

    companion object {
        fun from(raw: String?): CashierScenario = entries.firstOrNull {
            it.name.equals(raw, ignoreCase = true)
        } ?: QUEUE
    }
}

private data class PrototypeOrder(
    val folio: String,
    val waiting: String,
    val itemKinds: Int,
    val units: Int,
    val totalCents: Long,
    val status: String = "Esperando en caja",
)

private val prototypeOrders = listOf(
    PrototypeOrder("VD-260822-A1B2C3", "Hace 18 min", 2, 3, 24_950),
    PrototypeOrder("VD-260822-D4E5F6", "Hace 7 min", 1, 1, 10_000),
)

@Composable
private fun CashierPrototype(scenario: CashierScenario) {
    Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
        Column(Modifier.fillMaxSize()) {
            PrototypeHeader()
            when (scenario) {
                CashierScenario.QUEUE -> QueueScreen()
                CashierScenario.EMPTY -> CenterState(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = "Caja al día",
                    message = "No hay comandas esperando cobro en esta sucursal.",
                    action = "Actualizar",
                )
                CashierScenario.LOADING -> LoadingState()
                CashierScenario.ERROR -> CenterState(
                    icon = Icons.Rounded.WifiOff,
                    title = "No pudimos actualizar la bandeja",
                    message = "Comprueba tu conexión e intenta nuevamente. Ninguna comanda fue modificada.",
                    action = "Reintentar",
                    isError = true,
                )
                CashierScenario.DETAIL -> OrderDetailScreen()
                CashierScenario.PAYMENT -> PaymentConceptScreen()
                CashierScenario.CONFIRMATION -> ConfirmationConceptScreen()
            }
        }
    }
}

@Composable
private fun PrototypeHeader() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Forest)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        BotanicalAccent(Modifier.matchParentSize())
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ViveroMark(markSize = 44.dp, dark = true)
            Column(Modifier.weight(1f)) {
                Text(
                    "Caja",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    "Sucursal Centro · turno activo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = .78f),
                )
            }
            Surface(shape = CircleShape, color = Color.White.copy(alpha = .12f)) {
                Icon(
                    Icons.Rounded.PointOfSale,
                    contentDescription = "Módulo Caja",
                    tint = Color.White,
                    modifier = Modifier.padding(11.dp).size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun BotanicalAccent(modifier: Modifier) {
    Canvas(modifier) {
        drawCircle(Color.White.copy(alpha = .05f), size.minDimension * .7f, Offset(size.width * .92f, 0f))
        drawOval(
            color = Sage.copy(alpha = .22f),
            topLeft = Offset(size.width * .72f, size.height * .12f),
            size = Size(size.width * .2f, size.height * .52f),
        )
    }
}

@Composable
private fun PrototypeNotice(modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = PaleSage, contentColor = Forest) {
        Text(
            "Prototipo visual · datos ficticios",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun QueueScreen() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 800.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                QueueContent(Modifier.weight(1.35f).fillMaxHeight())
                QueueAside(Modifier.weight(.65f).fillMaxHeight())
            }
        } else {
            QueueContent(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun QueueContent(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PrototypeNotice()
            Spacer(Modifier.height(18.dp))
            Text("Comandas por atender", style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text(
                "Primero aparece la que lleva más tiempo esperando.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedInk,
            )
            Spacer(Modifier.height(8.dp))
        }
        items(prototypeOrders) { order -> OrderCard(order) }
        item {
            Text(
                "Actualizado hace unos segundos",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MutedInk,
            )
        }
    }
}

@Composable
private fun OrderCard(order: PrototypeOrder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = PaleSage) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = Forest,
                        modifier = Modifier.padding(12.dp).size(25.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(order.folio, style = MaterialTheme.typography.titleMedium, color = Ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, tint = Terracotta, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(order.waiting, style = MaterialTheme.typography.labelMedium, color = Terracotta)
                    }
                }
                StatusLabel(order.status)
            }
            HorizontalDivider(color = Forest.copy(alpha = .10f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${order.itemKinds} productos · ${order.units} unidades",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedInk,
                    )
                    Text(order.totalCents.asMxn(), style = MaterialTheme.typography.headlineSmall, color = Forest)
                }
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = PaleSage, contentColor = Forest),
                ) {
                    Text("Ver comanda")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Surface(shape = CircleShape, color = Color(0xFFFFE4D6), contentColor = Color(0xFF7C321F)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QueueAside(modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Forest),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(Icons.Rounded.LocalFlorist, null, tint = PaleSage, modifier = Modifier.size(34.dp))
            Text("Turno tranquilo, atención cuidadosa", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(
                "Abre una comanda y confirma productos e importe antes de iniciar cualquier cobro.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = .78f),
            )
            HorizontalDivider(color = Color.White.copy(alpha = .15f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = PaleSage)
                Text("El total siempre lo confirma el servidor", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text("2 comandas pendientes", style = MaterialTheme.typography.titleMedium, color = PaleSage)
        }
    }
}

@Composable
private fun CenterState(
    icon: ImageVector,
    title: String,
    message: String,
    action: String,
    isError: Boolean = false,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Paper),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(.86f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PrototypeNotice()
                Surface(shape = CircleShape, color = if (isError) Color(0xFFFFE4D6) else PaleSage) {
                    Icon(icon, null, tint = if (isError) Color(0xFF8A3822) else Forest, modifier = Modifier.padding(18.dp).size(34.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center)
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MutedInk, textAlign = TextAlign.Center)
                OutlinedButton(onClick = {}, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            CircularProgressIndicator(color = Forest)
            Text("Actualizando comandas…", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("Prototipo visual", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Forest)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            }
        }
    }
}

@Composable
private fun OrderDetailScreen() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 800.dp
        val content: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PrototypeNotice()
                ScreenTitle("Detalle de comanda", "VD-260822-A1B2C3 · esperando 18 min")
                Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ProductLine("Helecho Boston", "VIV-HELECHO-014", 2, 7_500)
                        HorizontalDivider(color = Forest.copy(alpha = .1f))
                        ProductLine("Maceta barro 20 cm", "MAC-BARRO-020", 1, 9_950)
                    }
                }
                TotalCard(24_950)
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                ) {
                    Icon(Icons.Rounded.Payments, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar cobro")
                }
                Text(
                    "Acción conceptual: no cambia estados ni registra pagos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedInk,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(1.25f)) { content() }
                QueueAside(Modifier.weight(.75f).fillMaxHeight())
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) { item { content() } }
        }
    }
}

@Composable
private fun ProductLine(name: String, code: String, quantity: Int, unitPrice: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = PaleSage) {
            Icon(Icons.Rounded.LocalFlorist, null, tint = Forest, modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$code · $quantity × ${unitPrice.asMxn()}", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        }
        Text((quantity * unitPrice).asMxn(), style = MaterialTheme.typography.titleMedium, color = Forest)
    }
}

@Composable
private fun TotalCard(total: Long) {
    Surface(shape = RoundedCornerShape(18.dp), color = PaleSage) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Total confirmado por servidor", style = MaterialTheme.typography.labelMedium, color = Forest)
                Text("Sin descuentos", style = MaterialTheme.typography.bodySmall, color = MutedInk)
            }
            Text(total.asMxn(), style = MaterialTheme.typography.headlineSmall, color = Forest)
        }
    }
}

@Composable
private fun PaymentConceptScreen() {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Paper),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                PrototypeNotice()
                Text("Registrar cobro", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Text("VD-260822-A1B2C3", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                TotalCard(24_950)
                Text("Método de pago", style = MaterialTheme.typography.titleMedium, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaymentChoice(Icons.Rounded.Payments, "Efectivo", true, Modifier.weight(1f))
                    PaymentChoice(Icons.Rounded.CreditCard, "Tarjeta", false, Modifier.weight(1f))
                }
                Surface(shape = RoundedCornerShape(16.dp), color = Cream) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Importe recibido", style = MaterialTheme.typography.labelMedium, color = MutedInk)
                        Text("$300.00", style = MaterialTheme.typography.headlineSmall, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text("Cambio sugerido · $50.50", style = MaterialTheme.typography.titleMedium, color = Forest)
                    }
                }
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                ) { Text("Confirmar cobro · ${24_950L.asMxn()}") }
                Text(
                    "Concepto únicamente. El backend deberá confirmar el pago de forma atómica.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PaymentChoice(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier) {
    Surface(
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) PaleSage else Cream,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Forest) else null,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = Forest)
            Text(label, style = MaterialTheme.typography.labelLarge, color = Ink)
        }
    }
}

@Composable
private fun ConfirmationConceptScreen() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(30.dp)) {
            Column(
                Modifier.padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PrototypeNotice()
                Surface(shape = CircleShape, color = PaleSage) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Forest, modifier = Modifier.padding(20.dp).size(44.dp))
                }
                Text("Pago confirmado", style = MaterialTheme.typography.headlineMedium, color = Ink, textAlign = TextAlign.Center)
                Text("VD-260822-A1B2C3", style = MaterialTheme.typography.titleMedium, color = MutedInk)
                Text(24_950L.asMxn(), style = MaterialTheme.typography.headlineLarge, color = Forest)
                Text(
                    "La comanda saldría de la bandeja y quedaría lista para generar su ticket.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MutedInk,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Preparar ticket")
                }
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Volver a la bandeja") }
            }
        }
    }
}

@Preview(name = "Caja · teléfono", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun QueuePhonePreview() = ViveroAppTheme { CashierPrototype(CashierScenario.QUEUE) }

@Preview(name = "Caja · tablet vertical", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun QueueTabletPreview() = ViveroAppTheme { CashierPrototype(CashierScenario.QUEUE) }

@Preview(name = "Caja · tablet horizontal", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun DetailTabletPreview() = ViveroAppTheme { CashierPrototype(CashierScenario.DETAIL) }
