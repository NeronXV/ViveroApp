package com.intutec.viveroapp.core.common

fun Long.asMxn(): String {
    val absolute = kotlin.math.abs(this)
    val pesos = absolute / 100
    val cents = (absolute % 100).toString().padStart(2, '0')
    val sign = if (this < 0) "-" else ""
    return "$sign$$pesos.$cents"
}
