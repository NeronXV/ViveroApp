package com.intutec.viveroapp.feature.catalog.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.intutec.viveroapp.R
import com.intutec.viveroapp.feature.catalog.domain.model.Product

@DrawableRes
fun productImageResource(imageKey: String): Int = when (imageKey) {
    "lavender" -> R.drawable.plant_lavender
    "echeveria" -> R.drawable.plant_echeveria
    else -> R.drawable.plant_monstera
}

@Composable
fun CatalogProductImage(
    product: Product,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (product.imageKey.isNotBlank()) {
        Image(
            painter = painterResource(productImageResource(product.imageKey)),
            contentDescription = "Fotografía de ${product.commonName}",
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalFlorist,
                contentDescription = product.primaryImage?.altText ?: "Producto sin imagen disponible",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
