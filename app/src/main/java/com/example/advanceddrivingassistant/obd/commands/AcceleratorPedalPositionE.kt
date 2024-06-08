package com.example.advanceddrivingassistant.obd.commands

import com.github.pires.obd.commands.ObdCommand

class AcceleratorPedalPositionE: ObdCommand("01 4A") {
    private var percentAcceleratorPedalPosition: Double = -1.0

    override fun performCalculations() {
        // Ignore the first two bytes [41 61] of the response
        // The third byte is the accelerator pedal position D
        percentAcceleratorPedalPosition = buffer[2] * 100.0 / 255.0
    }

    override fun getFormattedResult(): String {
        return "$percentAcceleratorPedalPosition${this.resultUnit}"
    }

    override fun getCalculatedResult(): String {
        return percentAcceleratorPedalPosition.toString()
    }

    override fun getName(): String {
        return "Accelerator Pedal Position E"
    }

    override fun getResultUnit(): String {
        return "%"
    }
}