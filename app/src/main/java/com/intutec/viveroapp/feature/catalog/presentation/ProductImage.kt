package com.intutec.viveroapp.feature.catalog.presentation

import androidx.annotation.DrawableRes
import com.intutec.viveroapp.R

@DrawableRes
fun productImageResource(imageKey: String): Int = when (imageKey) {
    "lavender" -> R.drawable.plant_lavender
    "echeveria" -> R.drawable.plant_echeveria
    else -> R.drawable.plant_monstera
}
