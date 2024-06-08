package com.example.advanceddrivingassistant.obd.commands

import com.github.pires.obd.commands.ObdCommand

class RelativeThrottlePositionCommand : ObdCommand("01 45") {

    private var relativeThrottlePosition: Double = -1.0

    override fun performCalculations() {
        // Ignore the first two bytes [41 45] of the response
        // The third byte is the relative throttle position
        relativeThrottlePosition = buffer[2] * 100.0 / 255.0
    }

    override fun getFormattedResult(): String {
        return "$relativeThrottlePosition${this.resultUnit}"
    }

    override fun getCalculatedResult(): String {
        return relativeThrottlePosition.toString()
    }

    override fun getName(): String {
        return "Relative Throttle Position"
    }

    override fun getResultUnit(): String {
        return "%"
    }
}