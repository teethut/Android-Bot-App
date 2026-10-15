package com.teethut.twiprobot

import com.teethut.twiprobot.data.sensor.PhoneOrientationSensor
import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.domain.model.PidSettings
import com.teethut.twiprobot.domain.model.RobotTelemetry
import com.teethut.twiprobot.domain.repository.RobotRepository
import com.teethut.twiprobot.presentation.drive.DriveViewModel
import com.teethut.twiprobot.presentation.telemetry.TelemetryViewModel
import com.teethut.twiprobot.presentation.tracker.TrackerViewModel
import com.teethut.twiprobot.presentation.tuning.TuningViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RobotAppUnitTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRobotRepository : RobotRepository {
        val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected("192.168.4.1", 80))
        override val connectionState: StateFlow<ConnectionState> = _connectionState

        val _telemetryFlow = MutableSharedFlow<RobotTelemetry>(replay = 1)
        override val telemetryFlow: Flow<RobotTelemetry> = _telemetryFlow

        val _currentTelemetry = MutableStateFlow(RobotTelemetry())
        override val currentTelemetry: StateFlow<RobotTelemetry> = _currentTelemetry

        var lastSentSpeed: Float? = null
        var lastSentSteer: Float? = null
        var pidToReturn = PidSettings()
        var lastUpdatedOffset: Float? = null

        override fun connect(host: String, port: Int) {
            _connectionState.value = ConnectionState.Connected(host, port)
        }

        override fun disconnect() {
            _connectionState.value = ConnectionState.Disconnected
        }

        override fun sendDriveCommand(speed: Float, steer: Float) {
            lastSentSpeed = speed
            lastSentSteer = steer
        }

        override suspend fun fetchPidSettings(): Result<PidSettings> {
            return Result.success(pidToReturn)
        }

        override suspend fun updatePidSettings(settings: PidSettings): Result<Unit> {
            pidToReturn = settings
            return Result.success(Unit)
        }

        override suspend fun updateAngleOffset(offset: Float): Result<Unit> {
            lastUpdatedOffset = offset
            return Result.success(Unit)
        }
    }

    @Test
    fun driveViewModel_calculatesSpeedAndSteerAccurately() {
        val repo = FakeRobotRepository()
        val viewModel = DriveViewModel(repo)

        // 40% drive sensitivity (default) & 100% steer sensitivity (default):
        // Throttle 1.0 -> 1.0^1.3 * 3000 * 0.4 = 1200
        // Steer 0.5 -> 0.5^1.05 * 2500 * 1.0 = 1207.4204
        val expectedSteer = (Math.pow(0.5, 1.05) * 2500.0 * 1.0).toFloat()
        viewModel.onJoystickMove(throttle = 1.0f, steer = 0.5f)

        val state = viewModel.uiState.value
        assertEquals(1200f, state.calculatedSpeed, 0.01f)
        assertEquals(expectedSteer, state.calculatedSteer, 0.05f)
        assertTrue(state.isDriving)

        // Release joystick -> Failsafe zeroes out speed and steer
        viewModel.onJoystickRelease()
        val releasedState = viewModel.uiState.value
        assertEquals(0f, releasedState.calculatedSpeed, 0.01f)
        assertEquals(0f, releasedState.calculatedSteer, 0.01f)
        assertFalse(releasedState.isDriving)
        assertEquals(0f, repo.lastSentSpeed ?: -1f, 0.01f)
        assertEquals(0f, repo.lastSentSteer ?: -1f, 0.01f)
    }

    @Test
    fun driveViewModel_sensitivityScalingAndExpoWork() {
        val repo = FakeRobotRepository()
        val viewModel = DriveViewModel(repo)

        // Set drive sensitivity to 100% and steer sensitivity to 150%
        viewModel.onDriveSensitivityChanged(100)
        viewModel.onSteerSensitivityChanged(150)
        viewModel.onJoystickMove(throttle = 1.0f, steer = 1.0f)

        val state = viewModel.uiState.value
        assertEquals(3000f, state.calculatedSpeed, 0.01f)
        assertEquals(3750f, state.calculatedSteer, 0.01f) // 2500 * 1.5 = 3750

        // Test non-linear expo smoothing near center
        viewModel.onJoystickMove(throttle = 0.5f, steer = 0.5f)
        val expectedSpeedHalf = (Math.pow(0.5, 1.3) * 3000f * 1.0f).toFloat()
        val expectedSteerHalf = (Math.pow(0.5, 1.05) * 2500f * 1.5f).toFloat()
        assertEquals(expectedSpeedHalf, viewModel.uiState.value.calculatedSpeed, 0.05f)
        assertEquals(expectedSteerHalf, viewModel.uiState.value.calculatedSteer, 0.05f)
    }

    @Test
    fun tuningViewModel_loadsAndAppliesPresets() = runTest {
        val repo = FakeRobotRepository()
        val viewModel = TuningViewModel(repo)

        viewModel.applyPreset("Aggressive")
        assertEquals("48.0", viewModel.uiState.value.kp1Text)
        assertEquals("0.15", viewModel.uiState.value.ki1Text)
        assertEquals("0.02", viewModel.uiState.value.kd1Text)
        assertEquals("3000.0", viewModel.uiState.value.maxSteerText)
        assertEquals("10000.0", viewModel.uiState.value.steerAccelText)

        viewModel.applySettings().join()
        assertEquals(48.0f, repo.pidToReturn.kp1, 0.01f)
        assertEquals(0.15f, repo.pidToReturn.ki1, 0.01f)
        assertEquals(0.02f, repo.pidToReturn.kd1, 0.01f)
        assertEquals(3000.0f, repo.pidToReturn.maxSteer, 0.01f)
        assertEquals(10000.0f, repo.pidToReturn.steerAccel, 0.01f)
    }

    @Test
    fun telemetryViewModel_pausesAndClears() {
        val repo = FakeRobotRepository()
        val viewModel = TelemetryViewModel(repo)

        assertFalse(viewModel.uiState.value.isPaused)
        viewModel.togglePause()
        assertTrue(viewModel.uiState.value.isPaused)

        viewModel.clearCharts()
        assertTrue(viewModel.uiState.value.pitchHistory.isEmpty())
        assertTrue(viewModel.uiState.value.gForceHistory.isEmpty())
    }

    @Test
    fun telemetryViewModel_recordsGForceHistory() = runTest {
        val repo = FakeRobotRepository()
        val viewModel = TelemetryViewModel(repo)

        repo._telemetryFlow.emit(RobotTelemetry(timestampMs = 100L, pitch = 1.0f, gForce = 1.08f))
        testScheduler.runCurrent()

        assertEquals(1, viewModel.uiState.value.gForceHistory.size)
        assertEquals(1.08f, viewModel.uiState.value.gForceHistory.last(), 0.001f)
    }

    @Test
    fun driveViewModel_switchesDriveModeAndTiltHoldWorks() {
        val repo = FakeRobotRepository()
        val viewModel = DriveViewModel(repo)

        assertEquals(com.teethut.twiprobot.domain.model.DriveMode.JOYSTICK, viewModel.uiState.value.driveMode)

        viewModel.setDriveMode(com.teethut.twiprobot.domain.model.DriveMode.PHONE_TILT)
        assertEquals(com.teethut.twiprobot.domain.model.DriveMode.PHONE_TILT, viewModel.uiState.value.driveMode)

        // Hold to drive
        viewModel.onTiltHoldStart()
        assertTrue(viewModel.uiState.value.isDriving)

        // Release
        viewModel.onDriveRelease()
        assertFalse(viewModel.uiState.value.isDriving)
        assertEquals(0f, viewModel.uiState.value.calculatedSpeed, 0.01f)
        assertEquals(0f, viewModel.uiState.value.calculatedSteer, 0.01f)
    }

    @Test
    fun phoneOrientationSensor_recentersCurrentAngleAsZeroReference() {
        val sensor = PhoneOrientationSensor()

        // Simulate natural phone holding angle: 45 degrees towards face, slight 5 deg roll
        sensor.updateOrientationDegrees(rawPitch = -45f, rawRoll = 5f)

        // Initial update auto-calibrates reference so relative angles start at 0
        assertEquals(0f, sensor.tiltData.value.pitchDeg, 0.01f)
        assertEquals(0f, sensor.tiltData.value.rollDeg, 0.01f)
        assertEquals(0f, sensor.tiltData.value.normThrottle, 0.01f)
        assertEquals(0f, sensor.tiltData.value.normSteer, 0.01f)

        // User shifts their posture to -30 degrees pitch and -10 degrees roll
        sensor.updateOrientationDegrees(rawPitch = -30f, rawRoll = -10f)
        // Before pressing recenter, it shows relative delta from old reference:
        assertTrue(sensor.tiltData.value.pitchDeg != 0f)

        // User presses "Hold to Drive", which invokes recenter()
        sensor.recenter()

        // At the moment of pressing, the current angle is the new zero reference:
        assertEquals(0f, sensor.tiltData.value.pitchDeg, 0.01f)
        assertEquals(0f, sensor.tiltData.value.rollDeg, 0.01f)
        assertEquals(0f, sensor.tiltData.value.normThrottle, 0.01f)
        assertEquals(0f, sensor.tiltData.value.normSteer, 0.01f)
        assertEquals(-30f, sensor.tiltData.value.referencePitchDeg, 0.01f)
        assertEquals(-10f, sensor.tiltData.value.referenceRollDeg, 0.01f)

        // Now tilting phone forward by 15° (rawPitch goes from -30 to -45)
        sensor.updateOrientationDegrees(rawPitch = -45f, rawRoll = -10f)
        assertEquals(-15f, sensor.tiltData.value.pitchDeg, 0.01f)
        assertEquals(0f, sensor.tiltData.value.rollDeg, 0.01f)
        // Forward tilt produces positive forward throttle:
        assertTrue(sensor.tiltData.value.normThrottle > 0f)
    }

    @Test
    fun driveViewModel_tiltHoldRecentersZeroAngle() = runTest {
        val repo = FakeRobotRepository()
        val sensor = PhoneOrientationSensor()
        val viewModel = DriveViewModel(repository = repo, phoneOrientationSensor = sensor)

        viewModel.setDriveMode(com.teethut.twiprobot.domain.model.DriveMode.PHONE_TILT)

        // Simulate holding phone at -50° pitch
        sensor.updateOrientationDegrees(rawPitch = -50f, rawRoll = 2f)

        // Shift posture to -35° pitch before driving
        sensor.updateOrientationDegrees(rawPitch = -35f, rawRoll = 8f)

        // User presses Hold-to-Drive:
        viewModel.onTiltHoldStart()

        // Must recenter zero: throttleNorm & steerNorm are reset to 0f
        assertEquals(0f, viewModel.uiState.value.throttleNorm, 0.01f)
        assertEquals(0f, viewModel.uiState.value.steerNorm, 0.01f)
        assertEquals(0f, viewModel.uiState.value.calculatedSpeed, 0.01f)
        assertEquals(0f, viewModel.uiState.value.calculatedSteer, 0.01f)
        assertTrue(viewModel.uiState.value.isDriving)

        viewModel.onDriveRelease()
        assertFalse(viewModel.uiState.value.isDriving)
    }

    @Test
    fun telemetryLogEntry_formatsCsvCorrectly() {
        val entry = com.teethut.twiprobot.domain.model.TelemetryLogEntry(
            timestampMs = 1000L,
            state = "BALANCING",
            pitch = 1.25f,
            setpoint = 0.0f,
            offset = 6.0f,
            gForce = 1.02f,
            motorOut = 50.0f,
            speed = 10.0f,
            targetSpeed = 0.0f,
            steer = 0.0f,
            encLeft = 120L,
            encRight = 122L,
            freeHeapBytes = 180000L
        )

        val csv = entry.toCsvRow()
        assertEquals("1000,BALANCING,1.25,0.0,6.0,1.02,50.0,10.0,0.0,0.0,120,122,180000", csv)
        assertTrue(com.teethut.twiprobot.domain.model.TelemetryLogEntry.CSV_HEADER.contains("pitch_deg"))
        assertTrue(com.teethut.twiprobot.domain.model.TelemetryLogEntry.CSV_HEADER.contains("g_force"))
    }

    @Test
    fun telemetryViewModel_sessionRecordingFlow() = runTest {
        val repo = FakeRobotRepository()
        val viewModel = TelemetryViewModel(repo)

        assertFalse(viewModel.uiState.value.isRecording)
        assertEquals(0, viewModel.uiState.value.recordedEntriesCount)

        viewModel.startRecording()
        assertTrue(viewModel.uiState.value.isRecording)

        // Emit telemetry item
        repo._telemetryFlow.emit(RobotTelemetry(timestampMs = 500L, pitch = 2.0f))
        testScheduler.runCurrent()
        assertEquals(1, viewModel.uiState.value.recordedEntriesCount)

        viewModel.stopRecording()
        assertFalse(viewModel.uiState.value.isRecording)

        viewModel.clearRecordedSession()
        assertEquals(0, viewModel.uiState.value.recordedEntriesCount)
    }

    private class FakePresetHistoryRepository : com.teethut.twiprobot.domain.repository.PresetHistoryRepository {
        val presetList = mutableListOf<com.teethut.twiprobot.data.local.entity.PidPresetEntity>()
        val sessionList = mutableListOf<com.teethut.twiprobot.data.local.entity.TelemetrySessionEntity>()

        val _presetsFlow = MutableStateFlow<List<com.teethut.twiprobot.data.local.entity.PidPresetEntity>>(emptyList())
        override val presetsFlow: Flow<List<com.teethut.twiprobot.data.local.entity.PidPresetEntity>> = _presetsFlow

        val _sessionsFlow = MutableStateFlow<List<com.teethut.twiprobot.data.local.entity.TelemetrySessionEntity>>(emptyList())
        override val sessionsFlow: Flow<List<com.teethut.twiprobot.data.local.entity.TelemetrySessionEntity>> = _sessionsFlow

        override suspend fun savePreset(preset: com.teethut.twiprobot.data.local.entity.PidPresetEntity): Long {
            val withId = if (preset.id == 0L) preset.copy(id = (presetList.size + 1).toLong()) else preset
            presetList.add(withId)
            _presetsFlow.value = presetList.toList()
            return withId.id
        }

        override suspend fun updatePreset(preset: com.teethut.twiprobot.data.local.entity.PidPresetEntity): Boolean {
            val idx = presetList.indexOfFirst { it.id == preset.id }
            return if (idx >= 0) {
                presetList[idx] = preset
                _presetsFlow.value = presetList.toList()
                true
            } else {
                false
            }
        }

        override suspend fun deletePreset(id: Long): Boolean {
            val removed = presetList.removeAll { it.id == id }
            _presetsFlow.value = presetList.toList()
            return removed
        }

        override suspend fun saveSession(session: com.teethut.twiprobot.data.local.entity.TelemetrySessionEntity): Long {
            val withId = if (session.id == 0L) session.copy(id = (sessionList.size + 1).toLong()) else session
            sessionList.add(withId)
            _sessionsFlow.value = sessionList.toList()
            return withId.id
        }

        override suspend fun deleteSession(id: Long): Boolean {
            val removed = sessionList.removeAll { it.id == id }
            _sessionsFlow.value = sessionList.toList()
            return removed
        }
    }

    @Test
    fun historyViewModel_savesAndDeletesPresets() = runTest {
        val historyRepo = FakePresetHistoryRepository()
        val robotRepo = FakeRobotRepository()
        val viewModel = com.teethut.twiprobot.presentation.history.HistoryViewModel(historyRepo, robotRepo)

        assertTrue(viewModel.presets.value.isEmpty())

        viewModel.saveNewPreset("Test Profile", "Carpet", PidSettings(kp1 = 42f)).join()
        testScheduler.runCurrent()

        assertEquals(1, viewModel.presets.value.size)
        assertEquals("Test Profile", viewModel.presets.value[0].name)
        assertEquals(42f, viewModel.presets.value[0].kp1, 0.01f)

        // Delete preset
        viewModel.deletePreset(viewModel.presets.value[0].id).join()
        testScheduler.runCurrent()
        assertTrue(viewModel.presets.value.isEmpty())
    }

    @Test
    fun historyViewModel_appliesPresetToRobotRestApi() = runTest {
        val historyRepo = FakePresetHistoryRepository()
        val robotRepo = FakeRobotRepository()
        val viewModel = com.teethut.twiprobot.presentation.history.HistoryViewModel(historyRepo, robotRepo)

        val preset = com.teethut.twiprobot.data.local.entity.PidPresetEntity(
            id = 1L,
            name = "Hardwood Profile",
            surfaceType = "Hardwood",
            kp1 = 35f, ki1 = 0.05f, kd1 = 0.01f,
            kp2 = -0.001f, ki2 = 0f, kd2 = 0f
        )

        viewModel.applyPresetToRobot(preset).join()
        testScheduler.runCurrent()

        assertEquals(35f, robotRepo.pidToReturn.kp1, 0.01f)
        assertEquals(0.05f, robotRepo.pidToReturn.ki1, 0.01f)
        assertEquals("Successfully applied 'Hardwood Profile' to robot!", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun historyViewModel_savesTelemetrySession() = runTest {
        val historyRepo = FakePresetHistoryRepository()
        val robotRepo = FakeRobotRepository()
        val viewModel = com.teethut.twiprobot.presentation.history.HistoryViewModel(historyRepo, robotRepo)

        viewModel.saveTelemetrySession("Session Run #1", durationSec = 30, sampleCount = 600, maxSpeed = 1200f, csvData = "t,s\n1,2").join()
        testScheduler.runCurrent()

        assertEquals(1, viewModel.sessions.value.size)
        assertEquals("Session Run #1", viewModel.sessions.value[0].sessionName)
        assertEquals(30, viewModel.sessions.value[0].durationSeconds)
        assertEquals(600, viewModel.sessions.value[0].sampleCount)
    }

    @Test
    fun pidPreset_containsOffsetAngleAndBothLoopsKpKiKd() {
        val settings = PidSettings(
            kp1 = 45.5f,
            ki1 = 0.25f,
            kd1 = 0.05f,
            kp2 = -0.0025f,
            ki2 = 0.0001f,
            kd2 = 0.00003f,
            angleOffset = 7.5f
        )

        val entity = com.teethut.twiprobot.data.local.entity.PidPresetEntity.fromPidSettings(
            name = "Custom Tuning",
            surfaceType = "Concrete",
            settings = settings,
            id = 10L
        )

        assertEquals(10L, entity.id)
        assertEquals("Custom Tuning", entity.name)
        assertEquals("Concrete", entity.surfaceType)
        assertEquals(7.5f, entity.angleOffset, 0.001f)
        assertEquals(45.5f, entity.kp1, 0.001f)
        assertEquals(0.25f, entity.ki1, 0.001f)
        assertEquals(0.05f, entity.kd1, 0.001f)
        assertEquals(-0.0025f, entity.kp2, 0.00001f)
        assertEquals(0.0001f, entity.ki2, 0.00001f)
        assertEquals(0.00003f, entity.kd2, 0.00001f)

        val restored = entity.toPidSettings()
        assertEquals(7.5f, restored.angleOffset, 0.001f)
        assertEquals(45.5f, restored.kp1, 0.001f)
        assertEquals(0.25f, restored.ki1, 0.001f)
        assertEquals(0.05f, restored.kd1, 0.001f)
        assertEquals(-0.0025f, restored.kp2, 0.00001f)
        assertEquals(0.0001f, restored.ki2, 0.00001f)
        assertEquals(0.00003f, restored.kd2, 0.00001f)
    }

    @Test
    fun historyViewModel_savesPresetWithOffsetAngleAndBothLoops() = runTest {
        val historyRepo = FakePresetHistoryRepository()
        val robotRepo = FakeRobotRepository()
        val viewModel = com.teethut.twiprobot.presentation.history.HistoryViewModel(historyRepo, robotRepo)

        val customSettings = PidSettings(
            kp1 = 38.0f,
            ki1 = 0.12f,
            kd1 = 0.015f,
            kp2 = -0.0018f,
            ki2 = 0.0002f,
            kd2 = 0.00004f,
            angleOffset = 5.2f
        )

        viewModel.saveNewPreset("Office Carpet", "Carpet", customSettings).join()
        testScheduler.runCurrent()

        assertEquals(1, viewModel.presets.value.size)
        val saved = viewModel.presets.value[0]
        assertEquals("Office Carpet", saved.name)
        assertEquals(5.2f, saved.angleOffset, 0.001f)
        assertEquals(38.0f, saved.kp1, 0.001f)
        assertEquals(0.12f, saved.ki1, 0.001f)
        assertEquals(0.015f, saved.kd1, 0.001f)
        assertEquals(-0.0018f, saved.kp2, 0.0001f)
        assertEquals(0.0002f, saved.ki2, 0.00001f)
        assertEquals(0.00004f, saved.kd2, 0.00001f)
    }

    @Test
    fun tuningViewModel_savesCurrentProfileWithOffsetAngleAndBothLoops() = runTest {
        val historyRepo = FakePresetHistoryRepository()
        val robotRepo = FakeRobotRepository()
        val viewModel = TuningViewModel(robotRepo, historyRepo)

        viewModel.onKp1Changed("48.5")
        viewModel.onKi1Changed("0.18")
        viewModel.onKd1Changed("0.025")
        viewModel.onKp2Changed("-0.0020")
        viewModel.onKi2Changed("0.00015")
        viewModel.onKd2Changed("0.000035")
        viewModel.onAngleOffsetChanged("6.8")

        viewModel.savePreset("Lab Bench", "Tile")?.join()
        testScheduler.runCurrent()

        assertEquals(1, historyRepo.presetList.size)
        val saved = historyRepo.presetList[0]
        assertEquals("Lab Bench", saved.name)
        assertEquals("Tile", saved.surfaceType)
        assertEquals(6.8f, saved.angleOffset, 0.001f)
        assertEquals(48.5f, saved.kp1, 0.001f)
        assertEquals(0.18f, saved.ki1, 0.001f)
        assertEquals(0.025f, saved.kd1, 0.001f)
        assertEquals(-0.0020f, saved.kp2, 0.0001f)
        assertEquals(0.00015f, saved.ki2, 0.00001f)
        assertEquals(0.000035f, saved.kd2, 0.000001f)
    }

    @Test
    fun proceduralRobotSynth_synthesizesNonEmptyWaveformsForAllEmotions() {
        val sounds = com.teethut.twiprobot.data.sound.RobotSynthSound.values()
        for (sound in sounds) {
            val pcm = com.teethut.twiprobot.data.sound.ProceduralRobotSynth.synthesizePcm(sound)
            assertTrue("Expected non-empty PCM for $sound", pcm.size > 1000)
            val maxAmplitude = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("Expected audible waveform amplitude for $sound", maxAmplitude > 5000)
        }
    }

    @Test
    fun cuteRobotVoiceManager_cleansMarkdownAndEmojisAndMapsGestures() {
        val raw = "**Hi there!** 👋 (=^・ω・^=) I am `Twippy` ✨ → ready to roll!"
        val cleaned = com.teethut.twiprobot.data.sound.CuteRobotVoiceManager.cleanTextForSpeech(raw)
        assertFalse(cleaned.contains("*"))
        assertFalse(cleaned.contains("`"))
        assertTrue(cleaned.contains("Hi there!"))
        assertTrue(cleaned.contains("meow"))
        assertTrue(cleaned.contains("Twippy"))

        for (gesture in com.teethut.twiprobot.domain.model.RobotHandGesture.values()) {
            val phrase = com.teethut.twiprobot.data.sound.CuteRobotVoiceManager.getCutePhraseForGesture(gesture)
            if (gesture == com.teethut.twiprobot.domain.model.RobotHandGesture.NONE) {
                assertEquals(null, phrase)
            } else {
                assertTrue("Expected non-blank phrase for $gesture", !phrase.isNullOrBlank())
            }
        }
    }

    @Test
    fun trackerViewModel_setAngleOffsetAndAdjustWorks() = runTest {
        val repo = FakeRobotRepository()
        repo.pidToReturn = PidSettings(angleOffset = 6.0f)
        val viewModel = TrackerViewModel(
            repository = repo,
            soundEffectManager = null,
            cuteVoiceManager = null,
            appContext = null
        )
        testScheduler.runCurrent()

        // Synchronized initial offset from PID settings
        assertEquals(6.0f, viewModel.uiState.value.currentAngleOffset, 0.001f)

        // Set offset to 6.8° (e.g. standard phone mount)
        viewModel.setAngleOffset(6.8f).join()
        testScheduler.runCurrent()

        assertEquals(6.8f, viewModel.uiState.value.currentAngleOffset, 0.001f)
        assertEquals(6.8f, repo.lastUpdatedOffset ?: 0f, 0.001f)

        // Adjust offset by +0.5°
        viewModel.adjustAngleOffset(0.5f).join()
        testScheduler.runCurrent()

        assertEquals(7.3f, viewModel.uiState.value.currentAngleOffset, 0.001f)
        assertEquals(7.3f, repo.lastUpdatedOffset ?: 0f, 0.001f)

        // Adjust offset by -0.1°
        viewModel.adjustAngleOffset(-0.1f).join()
        testScheduler.runCurrent()

        assertEquals(7.2f, viewModel.uiState.value.currentAngleOffset, 0.001f)

        // Test clamping (max 15.0°, min 0.0°)
        viewModel.setAngleOffset(25.0f).join()
        testScheduler.runCurrent()
        assertEquals(15.0f, viewModel.uiState.value.currentAngleOffset, 0.001f)

        viewModel.setAngleOffset(-5.0f).join()
        testScheduler.runCurrent()
        assertEquals(0.0f, viewModel.uiState.value.currentAngleOffset, 0.001f)
    }

    @Test
    fun trackerViewModel_telemetryOffsetSyncAndLandscapeLockWorks() = runTest {
        val repo = FakeRobotRepository()
        val viewModel = TrackerViewModel(
            repository = repo,
            soundEffectManager = null,
            cuteVoiceManager = null,
            appContext = null
        )
        testScheduler.runCurrent()

        // Landscape lock default is true
        assertTrue(viewModel.uiState.value.isLandscapeLocked)

        // Toggle landscape lock
        viewModel.toggleLandscapeLock()
        assertFalse(viewModel.uiState.value.isLandscapeLocked)

        viewModel.setLandscapeLocked(true)
        assertTrue(viewModel.uiState.value.isLandscapeLocked)

        // Emitting live telemetry with offset updates currentAngleOffset
        repo._telemetryFlow.emit(RobotTelemetry(offset = 7.15f))
        testScheduler.runCurrent()

        assertEquals(7.15f, viewModel.uiState.value.currentAngleOffset, 0.001f)
    }
}

