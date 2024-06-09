package com.example.advanceddrivingassistant.utils

import kotlin.random.Random

fun generateRandomDouble(min: Double, max: Double): Double {
    require(min < max) { "Minimum value must be less than maximum value." }

    val random = Random.Default
    val randomDouble = min + (max - min) * random.nextDouble()

    return randomDouble
}