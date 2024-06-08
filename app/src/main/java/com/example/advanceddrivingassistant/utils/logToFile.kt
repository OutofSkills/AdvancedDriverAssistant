package com.example.advanceddrivingassistant.utils

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Date
import java.util.Locale

fun logToFile(context: Context, tag: String, message: String) {
    Log.d(tag, message)

    val logMessage = formatLogMessage(tag, message)
    val logFile = getLogFile(context)

    try {
        FileOutputStream(logFile, true).use { fos ->
            OutputStreamWriter(fos).use { osw ->
                osw.write(logMessage)
            }
        }
    } catch (e: Exception) {
        Log.e("LogFile", "Error writing to log file", e)
    }
}

private fun formatLogMessage(tag: String, message: String): String {
    val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    return "$timeStamp - $tag: $message\n"
}

private fun getLogFile(context: Context): File {
    val logDir: File =
        context.getExternalFilesDir(null)!!

    if (!logDir.exists()) {
        logDir.mkdirs()
    }

    return File(logDir, "app_logs.txt")
}