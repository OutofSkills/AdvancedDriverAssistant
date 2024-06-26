package com.example.advanceddrivingassistant.dto

import kotlin.random.Random

data class EcoDrivingInference(
    var consumptionRate: Float,
    var rpm: Float,
    var driversDemandEnginePercentTorque: Float,
    var acceleratorPedalPositionD: Float,
    var acceleratorPedalPositionE: Float,
    var load: Float,
    var relativeThrottlePosition: Float,
    var actualEnginePercentTorque: Float
) {
    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            consumptionRate,
            rpm,
            driversDemandEnginePercentTorque,
            acceleratorPedalPositionD,
            acceleratorPedalPositionE,
            load,
            relativeThrottlePosition,
            actualEnginePercentTorque
        )
    }

    companion object {
        fun randomInstance(): EcoDrivingInference {
            return EcoDrivingInference(
                consumptionRate = Random.nextFloat() * 20f, // Example range: 0.0 to 20.0
                rpm = Random.nextFloat() * 8000f, // Example range: 0.0 to 8000.0
                driversDemandEnginePercentTorque = Random.nextFloat() * 100f, // Example range: 0.0 to 100.0
                acceleratorPedalPositionD = Random.nextFloat() * 100f, // Example range: 0.0 to 100.0
                acceleratorPedalPositionE = Random.nextFloat() * 100f, // Example range: 0.0 to 100.0
                load = Random.nextFloat() * 100f, // Example range: 0.0 to 100.0
                relativeThrottlePosition = Random.nextFloat() * 100f, // Example range: 0.0 to 100.0
                actualEnginePercentTorque = Random.nextFloat() * 100f // Example range: 0.0 to 100.0
            )
        }
    }
}