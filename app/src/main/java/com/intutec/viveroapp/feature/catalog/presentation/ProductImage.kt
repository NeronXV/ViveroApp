package com.intutec.viveroapp.feature.catalog.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.intutec.viveroapp.BuildConfig
import com.intutec.viveroapp.R
import com.intutec.viveroapp.feature.catalog.domain.model.Product
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    val contentDescription = product.primaryImage?.altText ?: "Fotografía de ${product.commonName}"
    val remoteUrl = product.primaryImage?.storagePath?.let {
        catalogImageUrl(BuildConfig.SUPABASE_URL, it)
    }
    when {
        product.imageKey.isNotBlank() -> Image(
            painter = painterResource(productImageResource(product.imageKey)),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
        remoteUrl != null -> SubcomposeAsyncImage(
            model = remoteUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            loading = {
                ProductImagePlaceholder(
                    modifier = Modifier.matchParentSize(),
                    showProgress = true,
                )
            },
            error = {
                ProductImagePlaceholder(
                    modifier = Modifier.matchParentSize(),
                    contentDescription = "No se pudo cargar la fotografía de ${product.commonName}",
                )
            },
        )
        else -> ProductImagePlaceholder(
            modifier = modifier,
            contentDescription = "${product.commonName} no tiene fotografía disponible",
        )
    }
}

@Composable
private fun ProductImagePlaceholder(
    modifier: Modifier,
    contentDescription: String? = null,
    showProgress: Boolean = false,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = Icons.Outlined.LocalFlorist,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

internal fun catalogImageUrl(supabaseUrl: String, storagePath: String): String? {
    val base = supabaseUrl.trim().trimEnd('/')
    val pathSegments = storagePath.trim().split('/')
    val baseUri = runCatching { URI(base) }.getOrNull() ?: return null
    if (baseUri.scheme !in setOf("http", "https") || baseUri.host.isNullOrBlank()) return null
    if (pathSegments.isEmpty() || pathSegments.any { it.isBlank() || it == "." || it == ".." }) return null
    val encodedPath = pathSegments.joinToString("/") { segment ->
        URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }
    return "$base/storage/v1/object/public/catalog-images/$encodedPath"
}
