package com.example.advanceddrivingassistant.obd.commands

import com.github.pires.obd.commands.ObdCommand

class ActualEnginePercentTorqueCommand : ObdCommand("01 62") {

    private var percentTorque: Int = -1

    override fun performCalculations() {
        // Ignore the first two bytes [41 61] of the response
        // The third byte is the percent torque
        percentTorque = buffer[2] - 125  // The value is offset by 125
    }

    override fun getFormattedResult(): String {
        return "$percentTorque${this.resultUnit}"
    }

    override fun getCalculatedResult(): String {
        return percentTorque.toString()
    }

    override fun getName(): String {
        return "Actual Engine - Percent Torque"
    }

    override fun getResultUnit(): String {
        return "%"
    }
}