package com.example.advanceddrivingassistant.tensorflow

import android.content.Context
import android.content.res.AssetFileDescriptor
import com.example.advanceddrivingassistant.utils.EcoDrivingClass
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class EcoDrivingClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null

    init {
        initializeInterpreter()
    }

    private fun initializeInterpreter() {
        val assetManager = context.assets
        val model = loadModelFile(assetManager, "model.tflite")
        interpreter = Interpreter(model)
    }

    @Throws(IOException::class)
    private fun loadModelFile(assetManager: android.content.res.AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset: Long = fileDescriptor.startOffset
        val declaredLength: Long = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classify(inputData: FloatArray): EcoDrivingClass {
        val inputArray = arrayOf(inputData)
        val outputMap = mutableMapOf<Int, Any>()
        val outputData = Array(1) { FloatArray(3) }
        outputMap[0] = outputData

        interpreter?.runForMultipleInputsOutputs(inputArray, outputMap)

        val predictedClass = argmax(outputData[0])
        return when (predictedClass) {
            0, 1 -> EcoDrivingClass.ECO
            2 -> EcoDrivingClass.NON_ECO
            else -> EcoDrivingClass.UNKNOWN
        }
    }

    private fun argmax(array: FloatArray): Int {
        var maxIndex = -1
        var maxValue = Float.MIN_VALUE
        for (i in array.indices) {
            if (array[i] > maxValue) {
                maxValue = array[i]
                maxIndex = i
            }
        }
        return maxIndex
    }
}