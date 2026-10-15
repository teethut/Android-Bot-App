package com.teethut.twiprobot.data.repository

import android.content.Context
import com.teethut.twiprobot.data.local.database.RobotDatabaseHelper
import com.teethut.twiprobot.data.local.entity.PidPresetEntity
import com.teethut.twiprobot.data.local.entity.TelemetrySessionEntity
import com.teethut.twiprobot.domain.repository.PresetHistoryRepository
import kotlinx.coroutines.flow.Flow

class PresetHistoryRepositoryImpl(
    private val dbHelper: RobotDatabaseHelper
) : PresetHistoryRepository {

    override val presetsFlow: Flow<List<PidPresetEntity>> = dbHelper.getAllPresetsFlow()
    override val sessionsFlow: Flow<List<TelemetrySessionEntity>> = dbHelper.getAllSessionsFlow()

    override suspend fun savePreset(preset: PidPresetEntity): Long {
        return dbHelper.insertPreset(preset)
    }

    override suspend fun updatePreset(preset: PidPresetEntity): Boolean {
        return dbHelper.updatePreset(preset) > 0
    }

    override suspend fun deletePreset(id: Long): Boolean {
        return dbHelper.deletePreset(id) > 0
    }

    override suspend fun saveSession(session: TelemetrySessionEntity): Long {
        return dbHelper.insertSession(session)
    }

    override suspend fun deleteSession(id: Long): Boolean {
        return dbHelper.deleteSession(id) > 0
    }

    companion object {
        @Volatile
        private var instance: PresetHistoryRepository? = null

        fun getInstance(context: Context): PresetHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: PresetHistoryRepositoryImpl(
                    RobotDatabaseHelper(context.applicationContext)
                ).also { instance = it }
            }
        }
    }
}

