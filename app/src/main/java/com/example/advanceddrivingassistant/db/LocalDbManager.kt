package com.example.advanceddrivingassistant.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getIntOrNull

class LocalDbManager(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "driving_assistant.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "driving_data"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
        private const val COLUMN_SPEED = "speed"
        private const val COLUMN_RPM = "rpm"
        private const val COLUMN_CONSUMPTION_RATE = "consumption_rate"
        private const val COLUMN_ECO_POINTS = "eco_points"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = "CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$COLUMN_LATITUDE REAL," +
                "$COLUMN_LONGITUDE REAL," +
                "$COLUMN_SPEED INTEGER," +
                "$COLUMN_RPM INTEGER," +
                "$COLUMN_CONSUMPTION_RATE REAL," +
                "$COLUMN_ECO_POINTS INTEGER," +
                "$COLUMN_TIMESTAMP INTEGER" +
                ")"
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle database upgrades if needed
    }

    fun saveData(data: DrivingData) {
        Log.d("DrivingData", "Saving data: ${data.rpm} ${data.speed}")
        val values = ContentValues().apply {
            put(COLUMN_LATITUDE, data.latitude ?: 0.0)
            put(COLUMN_LONGITUDE, data.longitude ?: 0.0)
            put(COLUMN_SPEED, data.speed?.toInt() ?: 0)
            put(COLUMN_RPM, data.rpm?.toInt() ?: 0)
            put(COLUMN_CONSUMPTION_RATE, data.consumptionRate?.toDouble() ?: 0.0)
            put(COLUMN_ECO_POINTS, data.ecoPoints ?: 0)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }

        val db = writableDatabase
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun getFuelLastConsumptionRatesRecords(numberOfRecords: Int): List<Double> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COLUMN_CONSUMPTION_RATE " +
                    "FROM $TABLE_NAME " +
                    "ORDER BY $COLUMN_TIMESTAMP DESC " +
                    "LIMIT ?",
            arrayOf(numberOfRecords.toString())
        )

        val dataList = mutableListOf<Double>()

        while (cursor.moveToNext()) {
            val consumptionRate = cursor.getDouble(
                cursor.getColumnIndexOrThrow(
                    COLUMN_CONSUMPTION_RATE
                )
            )
            dataList.add(consumptionRate)
        }

        cursor.close()
        db.close()

        return dataList
    }

    fun getAverageSpeedAndConsumptionRate(numberOfRecords: Int): List<Double> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COLUMN_CONSUMPTION_RATE, $COLUMN_SPEED " +
                    "FROM $TABLE_NAME " +
                    "ORDER BY $COLUMN_TIMESTAMP DESC " +
                    "LIMIT ?",
            arrayOf(numberOfRecords.toString())
        )

        var totalConsumptionRate = 0.0
        var totalSpeed = 0.0
        var count = 0

        while (cursor.moveToNext()) {
            val consumptionRate = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CONSUMPTION_RATE))
            val speed = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SPEED))
            totalConsumptionRate += consumptionRate
            totalSpeed += speed
            count++
        }

        cursor.close()
        db.close()

        val averageConsumptionRate = if (count > 0) totalConsumptionRate / count else 0.0
        val averageSpeed = if (count > 0) totalSpeed / count else 0.0

        return listOf(averageSpeed, averageConsumptionRate)
    }

    fun getFuelConsumptionRates(numberOfDays: Int): List<Double> {
        val currentTime = System.currentTimeMillis()
        val timeThreshold = currentTime - numberOfDays * 24 * 60 * 60 * 1000L

        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COLUMN_CONSUMPTION_RATE " +
                    "FROM $TABLE_NAME " +
                    "WHERE $COLUMN_TIMESTAMP >= ? ",
            arrayOf(timeThreshold.toString())
        )

        val dataList = mutableListOf<Double>()

        while (cursor.moveToNext()) {
            val consumptionRate =
                cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CONSUMPTION_RATE))
            dataList.add(consumptionRate)
        }

        cursor.close()
        db.close()

        return dataList
    }

    fun getLastRecordedData(numberOfRecords: Int): List<DrivingData> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COLUMN_SPEED, " +
                    "$COLUMN_RPM, " +
                    "$COLUMN_ECO_POINTS " +
                    "FROM (SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_TIMESTAMP DESC LIMIT ?) " +
                    "ORDER BY $COLUMN_TIMESTAMP ASC",
            arrayOf(numberOfRecords.toString())
        )

        val dataList = mutableListOf<DrivingData>()

        while (cursor.moveToNext()) {
            val speed = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SPEED))
            val rpm = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_RPM))
            val ecoPoints = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ECO_POINTS))

            val drivingData = DrivingData(
                null,
                null,
                null,
                speed.toInt().toString(),
                rpm.toInt().toString(),
                null,
                ecoPoints
            )
            dataList.add(drivingData)
        }

        cursor.close()
        db.close()

        return dataList
    }

    fun getAverageData(days: Int): DrivingData {
        val currentTime = System.currentTimeMillis()
        val timeThreshold = currentTime - days * 24 * 60 * 60 * 1000L

        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT AVG($COLUMN_SPEED) AS avg_speed, " +
                    "AVG($COLUMN_RPM) AS avg_rpm, " +
                    "AVG($COLUMN_ECO_POINTS) AS avg_eco_points " +
                    "FROM $TABLE_NAME " +
                    "WHERE $COLUMN_TIMESTAMP >= ?",
            arrayOf(timeThreshold.toString())
        )

        var avgSpeed: Double? = null
        var avgRpm: Double? = null
        var avgConsumptionRate: Double? = null
        var avgEcoPoints: Int? = null

        if (cursor.moveToFirst()) {
            avgSpeed = cursor.getDouble(cursor.getColumnIndexOrThrow("avg_speed"))
            avgRpm = cursor.getDouble(cursor.getColumnIndexOrThrow("avg_rpm"))
            avgEcoPoints = cursor.getInt(cursor.getColumnIndexOrThrow("avg_eco_points"))

            Log.d(
                "AverageData",
                "Avg Speed: $avgSpeed, Avg Rpm: $avgRpm, Avg Consumption Rate: $avgConsumptionRate, Avg Eco Points: $avgEcoPoints"
            )
        }

        cursor.close()
        db.close()

        return DrivingData(
            null,
            null,
            null,
            (avgSpeed?.toInt() ?: 0).toString(),
            (avgRpm?.toInt() ?: 0).toString(),
            null,
            avgEcoPoints ?: 0
        )
    }
}

data class DrivingData(
    val id: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val speed: String?,
    val rpm: String?,
    val consumptionRate: String?,
    val ecoPoints: Int?,
    val fuelConsumptionRate: Double? = null,
)