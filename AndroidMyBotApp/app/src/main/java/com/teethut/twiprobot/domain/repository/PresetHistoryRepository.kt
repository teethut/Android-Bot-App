package com.teethut.twiprobot.domain.repository

import com.teethut.twiprobot.data.local.entity.PidPresetEntity
import com.teethut.twiprobot.data.local.entity.TelemetrySessionEntity
import kotlinx.coroutines.flow.Flow

interface PresetHistoryRepository {
    val presetsFlow: Flow<List<PidPresetEntity>>
    val sessionsFlow: Flow<List<TelemetrySessionEntity>>

    suspend fun savePreset(preset: PidPresetEntity): Long
    suspend fun updatePreset(preset: PidPresetEntity): Boolean
    suspend fun deletePreset(id: Long): Boolean
    suspend fun saveSession(session: TelemetrySessionEntity): Long
    suspend fun deleteSession(id: Long): Boolean
}

