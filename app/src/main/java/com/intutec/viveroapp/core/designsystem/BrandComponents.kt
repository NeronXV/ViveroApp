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

@Composable
fun BotanicalBackdrop(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF006B4F),
                    Color(0xFF064E3B),
                    Color(0xFF032E23),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        drawCircle(Color.White.copy(alpha = 0.06f), radius = size.minDimension * .42f, center = Offset(size.width * .08f, size.height * .12f))
        drawCircle(secondary.copy(alpha = 0.12f), radius = size.minDimension * .34f, center = Offset(size.width * .92f, size.height * .86f))
        rotate(-28f, pivot = Offset(size.width * .78f, size.height * .18f)) {
            drawOval(
                color = Color.White.copy(alpha = .08f),
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
) {
    Surface(modifier = modifier, color = containerColor, contentColor = contentColor, shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(contentColor))
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
        tonalElevation = 2.dp,
        shadowElevation = 14.dp,
        content = { Row(content = content) },
    )
}
