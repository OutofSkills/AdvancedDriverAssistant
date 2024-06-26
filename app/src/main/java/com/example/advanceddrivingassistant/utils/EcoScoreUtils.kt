package com.example.advanceddrivingassistant.utils

object EcoScoreUtils {
    private const val IDEAL_SPEED_MIN = 40.0
    private const val IDEAL_SPEED_MAX = 80.0
    private const val MAX_FUEL_CONSUMPTION_RATE = 5.0 // L/100km for full score

    /**
     * Calculates the eco score based on speed and fuel consumption rate.
     *
     * @param speed The speed of the vehicle in kilometers per hour (km/h).
     * @param fuelConsumptionRate The fuel consumption rate in liters per 100 kilometers (L/100km).
     * @return The eco score between 0 and 100.
     */
    fun calculateEcoScore(speed: Double, fuelConsumptionRate: Double): Double {
        val speedFactor = calculateSpeedFactor(speed)
        val fuelConsumptionFactor = calculateFuelConsumptionFactor(fuelConsumptionRate)

        // Weights for the factors
        val w1 = 0.5
        val w2 = 0.5

        return (w1 * speedFactor) + (w2 * fuelConsumptionFactor)
    }

    private fun calculateSpeedFactor(speed: Double): Double {
        return when {
            speed in IDEAL_SPEED_MIN..IDEAL_SPEED_MAX -> 100.0
            speed < IDEAL_SPEED_MIN -> (speed / IDEAL_SPEED_MIN) * 100
            else -> (IDEAL_SPEED_MAX / speed) * 100
        }.coerceIn(0.0, 100.0)
    }

    private fun calculateFuelConsumptionFactor(fuelConsumptionRate: Double): Double {
        return (MAX_FUEL_CONSUMPTION_RATE / fuelConsumptionRate * 100).coerceIn(0.0, 100.0)
    }
}
