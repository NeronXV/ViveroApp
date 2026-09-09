package com.intutec.viveroapp.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.intutec.viveroapp.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

@Composable
fun BotanicalBackdrop(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF075E45),
                    Color(0xFF054634),
                    Color(0xFF032B20),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        drawCircle(Color.White.copy(alpha = 0.05f), radius = size.minDimension * .45f, center = Offset(size.width * .08f, size.height * .12f))
        drawCircle(secondary.copy(alpha = 0.12f), radius = size.minDimension * .35f, center = Offset(size.width * .92f, size.height * .86f))
        rotate(-28f, pivot = Offset(size.width * .78f, size.height * .18f)) {
            drawOval(
                color = Color.White.copy(alpha = .07f),
                topLeft = Offset(size.width * .61f, size.height * .03f),
                size = Size(size.width * .34f, size.height * .15f),
            )
        }
        rotate(38f, pivot = Offset(size.width * .17f, size.height * .78f)) {
            drawOval(
                color = primary.copy(alpha = .18f),
                topLeft = Offset(size.width * .01f, size.height * .67f),
                size = Size(size.width * .36f, size.height * .16f),
            )
        }
    }
}

@Composable
fun LightBotanicalBackdrop(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier) {
        drawRect(background)
        drawCircle(
            color = primary.copy(alpha = .055f),
            radius = size.minDimension * .32f,
            center = Offset(size.width * 1.04f, size.height * .15f),
        )
        drawCircle(
            color = secondary.copy(alpha = .055f),
            radius = size.minDimension * .24f,
            center = Offset(size.width * -.05f, size.height * .88f),
        )
        rotate(-30f, pivot = Offset(size.width * .88f, size.height * .28f)) {
            drawOval(
                color = primary.copy(alpha = .055f),
                topLeft = Offset(size.width * .73f, size.height * .20f),
                size = Size(size.width * .28f, size.height * .10f),
            )
        }
    }
}

@Composable
fun ViveroMark(markSize: Dp = 52.dp, dark: Boolean = false) {
    Surface(
        modifier = Modifier.size(markSize),
        shape = CircleShape,
        color = if (dark) Color.White.copy(alpha = .94f) else MaterialTheme.colorScheme.surface,
        shadowElevation = if (dark) 0.dp else 4.dp,
    ) {
        Image(
            painter = painterResource(R.drawable.isotipo_flor),
            contentDescription = "Isotipo de Vivero Dulcinea",
            modifier = Modifier.padding(markSize * .17f),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun DulcineaLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "Vivero Dulcinea",
) {
    Image(
        painter = painterResource(R.drawable.logo_completo),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun DulcineaWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = "Vivero Dulcinea",
) {
    Image(
        painter = painterResource(R.drawable.logotipo_texto),
        contentDescription = contentDescription,
        modifier = modifier.width(154.dp).height(54.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    borderColor: Color? = null,
) {
    val stroke = borderColor ?: contentColor.copy(alpha = 0.18f)
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        border = BorderStroke(1.dp, stroke),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(contentColor))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun WebHeroBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.secondary,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 6.dp,
        content = { Row(content = content) },
    )
}

@Composable
fun ViveroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViveroTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    eyebrow: String = "VIVERO DULCINEA",
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun ViveroSectionIntro(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (eyebrow != null) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
