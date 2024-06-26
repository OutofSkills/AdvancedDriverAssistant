package com.example.advanceddrivingassistant.obd

import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.advanceddrivingassistant.obd.commands.AcceleratorPedalPositionD
import com.example.advanceddrivingassistant.obd.commands.AcceleratorPedalPositionE
import com.example.advanceddrivingassistant.obd.commands.ActualEnginePercentTorqueCommand
import com.example.advanceddrivingassistant.obd.commands.DriversDemandEnginePercentTorqueCommand
import com.example.advanceddrivingassistant.obd.commands.RelativeThrottlePositionCommand
import com.example.advanceddrivingassistant.utils.logToFile
import com.github.pires.obd.commands.SpeedCommand
import com.github.pires.obd.commands.control.VinCommand
import com.github.pires.obd.commands.engine.LoadCommand
import com.github.pires.obd.commands.engine.MassAirFlowCommand
import com.github.pires.obd.commands.engine.RPMCommand
import com.github.pires.obd.commands.engine.ThrottlePositionCommand
import com.github.pires.obd.commands.fuel.ConsumptionRateCommand
import com.github.pires.obd.commands.fuel.FuelLevelCommand
import com.github.pires.obd.commands.pressure.BarometricPressureCommand
import com.github.pires.obd.commands.protocol.EchoOffCommand
import com.github.pires.obd.commands.protocol.LineFeedOffCommand
import com.github.pires.obd.commands.protocol.ObdResetCommand
import com.github.pires.obd.commands.protocol.SelectProtocolCommand
import com.github.pires.obd.commands.protocol.TimeoutCommand
import com.github.pires.obd.commands.temperature.AmbientAirTemperatureCommand
import com.github.pires.obd.enums.ObdProtocols
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

data class ObdCommandResult(var name: String, var value: String, var unit: String)

class ObdCommandManager(
    private val context: Context,
    private val socket: BluetoothSocket?,
    private val onDataReceived: (ObdCommandResult) -> Unit,
    private val onVinIdentifierReceived: (ObdCommandResult) -> Unit
) {

    private val loggingTag = "ObdCommandManager"
    private val coroutineScope = CoroutineScope(Dispatchers.IO);

    private val initialConfigCommands
        get() = listOf(
            ObdResetCommand(),
            EchoOffCommand(),
            LineFeedOffCommand(),
            TimeoutCommand(42),
            SelectProtocolCommand(ObdProtocols.AUTO),
            AmbientAirTemperatureCommand()
        )

    private val commandList
        get() = listOf(
            ThrottlePositionCommand(),
            MassAirFlowCommand(),
            DriversDemandEnginePercentTorqueCommand(),
            ActualEnginePercentTorqueCommand(),
            AmbientAirTemperatureCommand(),
            AcceleratorPedalPositionD(),
            AcceleratorPedalPositionE(),
            RelativeThrottlePositionCommand(),
            ConsumptionRateCommand(),
            BarometricPressureCommand(),
            LoadCommand(),
            SpeedCommand(),
            RPMCommand(),
            FuelLevelCommand(),
        )

    private var dataCollectingJob: Job? = null

    private suspend fun configObdDevice() {
        logToFile(context, loggingTag, "[configObdDevice] Starting obd command flow")

        try {
            initialConfigCommands.forEach {
                it.run(socket?.inputStream, socket?.outputStream)
                if (it is ObdResetCommand) {
                    delay(500)
                }
            }
        } catch (e: Exception) {
            logToFile(
                context,
                loggingTag,
                "[configObdDevice] Failed to run initial commands: ${e.message}"
            )
        }
    }

    private fun startObdCommandFlow() = flow {
        while (socket?.isConnected == true) { // indefinite loop to keep running commands
            commandList.forEach {
                try {
                    it.run(
                        socket.inputStream,
                        socket.outputStream
                    ) // blocking call
                    emit(it) // read complete, emit value
                } catch (e: Exception) {
                    logToFile(
                        context,
                        loggingTag,
                        "[startObdCommandFlow] Failed to run command: ${e.message}"
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun getCarVinIdentifier() = flow {
        if (socket?.isConnected == true) {
            try {
                val vinCommand = VinCommand()
                vinCommand.run(
                    socket.inputStream,
                    socket.outputStream
                ) // blocking call
                emit(vinCommand) // read complete, emit value
            } catch (e: Exception) {
                logToFile(
                    context,
                    loggingTag,
                    "[getCarIdentifier] Failed to run command: ${e.message}"
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    fun startCollectingData() {
        stopCollectingData()

        dataCollectingJob = coroutineScope.launch(Dispatchers.IO) {
            configObdDevice().also {
                getCarVinIdentifier().collect {
                    val vinIdentifier = ObdCommandResult("VIN", it.calculatedResult, "")
                    onVinIdentifierReceived(vinIdentifier)
                }

                startObdCommandFlow().collect {
                    val result = ObdCommandResult(it.name, it.calculatedResult, it.resultUnit)
                    Log.d(loggingTag, "${result.name}: ${result.value} ${result.unit}")

                    onDataReceived(result)
                }
            }
        }
    }

    fun stopCollectingData() {
        dataCollectingJob?.cancel()
        dataCollectingJob = null
    }
}