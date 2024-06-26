package com.example.advanceddrivingassistant.utils

import android.icu.text.DecimalFormat

fun formatDouble(value: Double): String {
    val decimalFormat = DecimalFormat("#.##")
    return decimalFormat.format(value)
}