package com.example.advanceddrivingassistant.utils

fun calculateConsumptionPer100km(speed: Double, consumptionInLitersPerHour: Double): Double {
    val consumptionPer100km = (consumptionInLitersPerHour * 100.0) / speed
    return consumptionPer100km
}