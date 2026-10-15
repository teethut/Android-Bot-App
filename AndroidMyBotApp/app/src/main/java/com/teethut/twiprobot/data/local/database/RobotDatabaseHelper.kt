package com.teethut.twiprobot.data.local.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.teethut.twiprobot.data.local.entity.PidPresetEntity
import com.teethut.twiprobot.data.local.entity.TelemetrySessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class RobotDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    private val presetChangeTrigger = MutableSharedFlow<Unit>(replay = 1)
    private val sessionChangeTrigger = MutableSharedFlow<Unit>(replay = 1)

    init {
        presetChangeTrigger.tryEmit(Unit)
        sessionChangeTrigger.tryEmit(Unit)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PRESETS (
                $COL_PRESET_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PRESET_NAME TEXT NOT NULL,
                $COL_PRESET_SURFACE TEXT NOT NULL,
                $COL_KP1 REAL NOT NULL,
                $COL_KI1 REAL NOT NULL,
                $COL_KD1 REAL NOT NULL,
                $COL_KP2 REAL NOT NULL,
                $COL_KI2 REAL NOT NULL,
                $COL_KD2 REAL NOT NULL,
                $COL_ANGLE_OFFSET REAL NOT NULL,
                $COL_MAX_ACCEL REAL NOT NULL,
                $COL_MAX_STEPS REAL NOT NULL,
                $COL_MAX_STEER REAL NOT NULL DEFAULT 2500.0,
                $COL_STEER_ACCEL REAL NOT NULL DEFAULT 8000.0,
                $COL_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SESSIONS (
                $COL_SESSION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SESSION_NAME TEXT NOT NULL,
                $COL_STARTED_AT INTEGER NOT NULL,
                $COL_DURATION INTEGER NOT NULL,
                $COL_SAMPLE_COUNT INTEGER NOT NULL,
                $COL_MAX_SPEED REAL NOT NULL,
                $COL_CSV_DATA TEXT NOT NULL
            )
            """.trimIndent()
        )

        // Pre-populate Default Profiles as required by course guidelines
        insertInitialPreset(db, "Carpet - High Traction", "Carpet", 44.0f, 0.12f, 0.01f, -0.0018f, 0.0f, 0.00002f, 6.0f, 42000f, 22000f, 2500f, 8000f)
        insertInitialPreset(db, "Hardwood - Smooth", "Hardwood", 36.0f, 0.08f, 0.00f, -0.0012f, 0.0f, 0.00001f, 6.0f, 28000f, 15000f, 2000f, 6000f)
        insertInitialPreset(db, "Balanced Default", "General", 40.0f, 0.10f, 0.00f, -0.0015f, 0.0f, 0.00002f, 6.0f, 40000f, 20000f, 2500f, 8000f)
    }

    private fun insertInitialPreset(
        db: SQLiteDatabase,
        name: String,
        surface: String,
        kp1: Float, ki1: Float, kd1: Float,
        kp2: Float, ki2: Float, kd2: Float,
        offset: Float, accel: Float, msps: Float,
        maxSteer: Float = 2500f, steerAccel: Float = 8000f
    ) {
        val cv = ContentValues().apply {
            put(COL_PRESET_NAME, name)
            put(COL_PRESET_SURFACE, surface)
            put(COL_KP1, kp1)
            put(COL_KI1, ki1)
            put(COL_KD1, kd1)
            put(COL_KP2, kp2)
            put(COL_KI2, ki2)
            put(COL_KD2, kd2)
            put(COL_ANGLE_OFFSET, offset)
            put(COL_MAX_ACCEL, accel)
            put(COL_MAX_STEPS, msps)
            put(COL_MAX_STEER, maxSteer)
            put(COL_STEER_ACCEL, steerAccel)
            put(COL_CREATED_AT, System.currentTimeMillis())
        }
        db.insert(TABLE_PRESETS, null, cv)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_PRESETS ADD COLUMN $COL_MAX_STEER REAL NOT NULL DEFAULT 2500.0")
                db.execSQL("ALTER TABLE $TABLE_PRESETS ADD COLUMN $COL_STEER_ACCEL REAL NOT NULL DEFAULT 8000.0")
            } catch (e: Exception) {
                db.execSQL("DROP TABLE IF EXISTS $TABLE_PRESETS")
                db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
                onCreate(db)
            }
        }
    }

    // --- Reactive Flow Queries ---

    fun getAllPresetsFlow(): Flow<List<PidPresetEntity>> = flow {
        presetChangeTrigger.collect {
            emit(queryAllPresets())
        }
    }.flowOn(Dispatchers.IO)

    fun getAllSessionsFlow(): Flow<List<TelemetrySessionEntity>> = flow {
        sessionChangeTrigger.collect {
            emit(queryAllSessions())
        }
    }.flowOn(Dispatchers.IO)

    // --- Direct CRUD Operations ---

    private fun queryAllPresets(): List<PidPresetEntity> = synchronized(this) {
        val list = mutableListOf<PidPresetEntity>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_PRESETS, null, null, null, null, null, "$COL_CREATED_AT DESC")
            cursor.use {
                while (it.moveToNext()) {
                    val maxSteerIdx = it.getColumnIndex(COL_MAX_STEER)
                    val steerAccelIdx = it.getColumnIndex(COL_STEER_ACCEL)
                    list.add(
                        PidPresetEntity(
                            id = it.getLong(it.getColumnIndexOrThrow(COL_PRESET_ID)),
                            name = it.getString(it.getColumnIndexOrThrow(COL_PRESET_NAME)),
                            surfaceType = it.getString(it.getColumnIndexOrThrow(COL_PRESET_SURFACE)),
                            kp1 = it.getFloat(it.getColumnIndexOrThrow(COL_KP1)),
                            ki1 = it.getFloat(it.getColumnIndexOrThrow(COL_KI1)),
                            kd1 = it.getFloat(it.getColumnIndexOrThrow(COL_KD1)),
                            kp2 = it.getFloat(it.getColumnIndexOrThrow(COL_KP2)),
                            ki2 = it.getFloat(it.getColumnIndexOrThrow(COL_KI2)),
                            kd2 = it.getFloat(it.getColumnIndexOrThrow(COL_KD2)),
                            angleOffset = it.getFloat(it.getColumnIndexOrThrow(COL_ANGLE_OFFSET)),
                            maxAccel = it.getFloat(it.getColumnIndexOrThrow(COL_MAX_ACCEL)),
                            maxStepsPerSec = it.getFloat(it.getColumnIndexOrThrow(COL_MAX_STEPS)),
                            maxSteer = if (maxSteerIdx >= 0 && !it.isNull(maxSteerIdx)) it.getFloat(maxSteerIdx) else 2500.0f,
                            steerAccel = if (steerAccelIdx >= 0 && !it.isNull(steerAccelIdx)) it.getFloat(steerAccelIdx) else 8000.0f,
                            createdAt = it.getLong(it.getColumnIndexOrThrow(COL_CREATED_AT))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RobotDatabaseHelper", "Error querying presets: ${e.message}", e)
        }
        list
    }

    suspend fun insertPreset(preset: PidPresetEntity): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put(COL_PRESET_NAME, preset.name)
            put(COL_PRESET_SURFACE, preset.surfaceType)
            put(COL_KP1, preset.kp1)
            put(COL_KI1, preset.ki1)
            put(COL_KD1, preset.kd1)
            put(COL_KP2, preset.kp2)
            put(COL_KI2, preset.ki2)
            put(COL_KD2, preset.kd2)
            put(COL_ANGLE_OFFSET, preset.angleOffset)
            put(COL_MAX_ACCEL, preset.maxAccel)
            put(COL_MAX_STEPS, preset.maxStepsPerSec)
            put(COL_MAX_STEER, preset.maxSteer)
            put(COL_STEER_ACCEL, preset.steerAccel)
            put(COL_CREATED_AT, preset.createdAt)
        }
        val id = writableDatabase.insert(TABLE_PRESETS, null, cv)
        presetChangeTrigger.tryEmit(Unit)
        id
    }

    suspend fun updatePreset(preset: PidPresetEntity): Int = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put(COL_PRESET_NAME, preset.name)
            put(COL_PRESET_SURFACE, preset.surfaceType)
            put(COL_KP1, preset.kp1)
            put(COL_KI1, preset.ki1)
            put(COL_KD1, preset.kd1)
            put(COL_KP2, preset.kp2)
            put(COL_KI2, preset.ki2)
            put(COL_KD2, preset.kd2)
            put(COL_ANGLE_OFFSET, preset.angleOffset)
            put(COL_MAX_ACCEL, preset.maxAccel)
            put(COL_MAX_STEPS, preset.maxStepsPerSec)
            put(COL_MAX_STEER, preset.maxSteer)
            put(COL_STEER_ACCEL, preset.steerAccel)
        }
        val rows = writableDatabase.update(TABLE_PRESETS, cv, "$COL_PRESET_ID = ?", arrayOf(preset.id.toString()))
        presetChangeTrigger.tryEmit(Unit)
        rows
    }

    suspend fun deletePreset(id: Long): Int = withContext(Dispatchers.IO) {
        val rows = writableDatabase.delete(TABLE_PRESETS, "$COL_PRESET_ID = ?", arrayOf(id.toString()))
        presetChangeTrigger.tryEmit(Unit)
        rows
    }

    private fun queryAllSessions(): List<TelemetrySessionEntity> = synchronized(this) {
        val list = mutableListOf<TelemetrySessionEntity>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_SESSIONS, null, null, null, null, null, "$COL_STARTED_AT DESC")
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        TelemetrySessionEntity(
                            id = it.getLong(it.getColumnIndexOrThrow(COL_SESSION_ID)),
                            sessionName = it.getString(it.getColumnIndexOrThrow(COL_SESSION_NAME)),
                            startedAtMs = it.getLong(it.getColumnIndexOrThrow(COL_STARTED_AT)),
                            durationSeconds = it.getInt(it.getColumnIndexOrThrow(COL_DURATION)),
                            sampleCount = it.getInt(it.getColumnIndexOrThrow(COL_SAMPLE_COUNT)),
                            maxSpeed = it.getFloat(it.getColumnIndexOrThrow(COL_MAX_SPEED)),
                            csvData = it.getString(it.getColumnIndexOrThrow(COL_CSV_DATA))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RobotDatabaseHelper", "Error querying sessions: ${e.message}", e)
        }
        list
    }

    suspend fun insertSession(session: TelemetrySessionEntity): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put(COL_SESSION_NAME, session.sessionName)
            put(COL_STARTED_AT, session.startedAtMs)
            put(COL_DURATION, session.durationSeconds)
            put(COL_SAMPLE_COUNT, session.sampleCount)
            put(COL_MAX_SPEED, session.maxSpeed)
            put(COL_CSV_DATA, session.csvData)
        }
        val id = writableDatabase.insert(TABLE_SESSIONS, null, cv)
        sessionChangeTrigger.tryEmit(Unit)
        id
    }

    suspend fun deleteSession(id: Long): Int = withContext(Dispatchers.IO) {
        val rows = writableDatabase.delete(TABLE_SESSIONS, "$COL_SESSION_ID = ?", arrayOf(id.toString()))
        sessionChangeTrigger.tryEmit(Unit)
        rows
    }

    companion object {
        const val DATABASE_NAME = "twip_robot.db"
        const val DATABASE_VERSION = 2

        const val TABLE_PRESETS = "pid_presets"
        const val COL_PRESET_ID = "id"
        const val COL_PRESET_NAME = "name"
        const val COL_PRESET_SURFACE = "surface_type"
        const val COL_KP1 = "kp1"
        const val COL_KI1 = "ki1"
        const val COL_KD1 = "kd1"
        const val COL_KP2 = "kp2"
        const val COL_KI2 = "ki2"
        const val COL_KD2 = "kd2"
        const val COL_ANGLE_OFFSET = "angle_offset"
        const val COL_MAX_ACCEL = "max_accel"
        const val COL_MAX_STEPS = "max_steps_per_sec"
        const val COL_MAX_STEER = "max_steer"
        const val COL_STEER_ACCEL = "steer_accel"
        const val COL_CREATED_AT = "created_at"

        const val TABLE_SESSIONS = "telemetry_sessions"
        const val COL_SESSION_ID = "id"
        const val COL_SESSION_NAME = "session_name"
        const val COL_STARTED_AT = "started_at_ms"
        const val COL_DURATION = "duration_seconds"
        const val COL_SAMPLE_COUNT = "sample_count"
        const val COL_MAX_SPEED = "max_speed"
        const val COL_CSV_DATA = "csv_data"
    }
}

