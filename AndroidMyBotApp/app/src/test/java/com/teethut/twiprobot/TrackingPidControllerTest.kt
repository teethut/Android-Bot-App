package com.teethut.twiprobot

import com.teethut.twiprobot.data.vision.TrackingPidController
import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.domain.model.DetectedTarget
import com.teethut.twiprobot.domain.model.DistanceCategory
import com.teethut.twiprobot.domain.model.RobotTelemetry
import com.teethut.twiprobot.domain.model.TargetLockStatus
import com.teethut.twiprobot.domain.model.TrackerSettings
import com.teethut.twiprobot.domain.model.TrackingMode
import com.teethut.twiprobot.domain.repository.RobotRepository
import com.teethut.twiprobot.presentation.drive.DriveViewModel
import com.teethut.twiprobot.presentation.tracker.TrackerViewModel
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
class TrackingPidControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when target is dead center within deadband, steering is zero`() {
        val controller = TrackingPidController()
        val settings = TrackerSettings(deadbandX = 0.06f, maxSteer = 500f)
        val target = DetectedTarget(
            normalizedX = 0.02f, // within 0.06 deadband
            normalizedHeight = 0.18f
        )

        val output = controller.computeDriveCommand(
            target = target,
            settings = settings,
            trackingMode = TrackingMode.PAN_ONLY
        )

        assertEquals(0f, output.steer, 0.001f)
        assertEquals(TargetLockStatus.LOCKED, output.lockStatus)
    }

    @Test
    fun `when target is to the right, steering is positive`() {
        val controller = TrackingPidController()
        val settings = TrackerSettings(deadbandX = 0.06f, maxSteer = 500f, invertSteer = false)
        val target = DetectedTarget(
            normalizedX = 0.5f,
            normalizedHeight = 0.18f
        )

        val output = controller.computeDriveCommand(
            target = target,
            settings = settings,
            trackingMode = TrackingMode.PAN_ONLY
        )

        assertTrue("Steer should be positive when target is on the right", output.steer > 0f)
        assertTrue("Steer should be capped at maxSteer", output.steer <= 500f)
    }

    @Test
    fun `when target is to the left, steering is negative`() {
        val controller = TrackingPidController()
        val settings = TrackerSettings(deadbandX = 0.06f, maxSteer = 500f, invertSteer = false)
        val target = DetectedTarget(
            normalizedX = -0.5f,
            normalizedHeight = 0.18f
        )

        val output = controller.computeDriveCommand(
            target = target,
            settings = settings,
            trackingMode = TrackingMode.PAN_ONLY
        )

        assertTrue("Steer should be negative when target is on the left", output.steer < 0f)
        assertTrue("Steer should be capped at -maxSteer", output.steer >= -500f)
    }

    @Test
    fun `when invertSteer is true, steering polarity reverses`() {
        val controller = TrackingPidController()
        val settingsNormal = TrackerSettings(deadbandX = 0.06f, maxSteer = 500f, invertSteer = false)
        val settingsInverted = TrackerSettings(deadbandX = 0.06f, maxSteer = 500f, invertSteer = true)
        val target = DetectedTarget(normalizedX = 0.4f, normalizedHeight = 0.18f)

        val outNormal = controller.computeDriveCommand(target, settingsNormal, TrackingMode.PAN_ONLY)
        val outInverted = controller.computeDriveCommand(target, settingsInverted, TrackingMode.PAN_ONLY)

        assertEquals(outNormal.steer, -outInverted.steer, 0.001f)
    }

    @Test
    fun `in PAN_ONLY mode, speed is always zero regardless of target distance`() {
        val controller = TrackingPidController()
        val settings = TrackerSettings(targetDistanceFraction = 0.18f, maxSpeed = 700f)
        val farTarget = DetectedTarget(normalizedX = 0f, normalizedHeight = 0.05f) // far away

        val output = controller.computeDriveCommand(farTarget, settings, TrackingMode.PAN_ONLY)

        assertEquals(0f, output.speed, 0.001f)
    }

    @Test
    fun `in FOLLOW mode, target too far generates positive forward speed`() {
        val controller = TrackingPidController()
        val settings = TrackerSettings(targetDistanceFraction = 0.20f, deadbandDist = 0.04f, maxSpeed = 700f)
        val farTarget = DetectedTarget(normalizedX = 0f, normalizedHeight = 0.08f)

        val output = controller.computeDriveCommand(farTarget, settings, TrackingMode.FOLLOW)

        assertTrue("Forward speed should be positive", output.speed > 0f)
        assertEquals(DistanceCategory.TOO_FAR, output.distanceCategory)
    }

    @Test
    fun `in FOLLOW mode, target too close inhibits reverse by default`() {
        val controller = TrackingPidController()
        val settings = TrackerSettings(
            targetDistanceFraction = 0.18f,
            deadbandDist = 0.04f,
            allowReverse = false
        )
        val closeTarget = DetectedTarget(normalizedX = 0f, normalizedHeight = 0.35f) // very close

        val output = controller.computeDriveCommand(closeTarget, settings, TrackingMode.FOLLOW)

        assertEquals("Speed should be 0 when reverse is not allowed", 0f, output.speed, 0.001f)
        assertEquals(DistanceCategory.TOO_CLOSE, output.distanceCategory)
    }

    @Test
    fun `in FOLLOW mode with allowReverse true, target too close generates negative speed capped at safety limit`() {
        val controller = TrackingPidController()
        val settings = TrackerSettings(
            targetDistanceFraction = 0.18f,
            deadbandDist = 0.04f,
            maxSpeed = 800f,
            allowReverse = true
        )
        val closeTarget = DetectedTarget(normalizedX = 0f, normalizedHeight = 0.35f)

        val output = controller.computeDriveCommand(closeTarget, settings, TrackingMode.FOLLOW)

        assertTrue("Speed should be negative", output.speed < 0f)
        assertTrue("Reverse speed should not exceed 60% of max forward speed", output.speed >= -800f * 0.6f)
    }

    @Test
    fun `target lost timeout resets speed and steer to zero after 500ms`() {
        val controller = TrackingPidController(lostTargetTimeoutMs = 500L)
        val settings = TrackerSettings()
        val target = DetectedTarget(normalizedX = 0.5f, normalizedHeight = 0.18f)

        val t0 = 1000L
        controller.computeDriveCommand(target, settings, TrackingMode.PAN_ONLY, currentTimeMs = t0)

        // After 600ms without seeing target
        val timeoutOutput = controller.computeDriveCommand(null, settings, TrackingMode.PAN_ONLY, currentTimeMs = t0 + 600L)

        assertEquals(0f, timeoutOutput.speed, 0.001f)
        assertEquals(0f, timeoutOutput.steer, 0.001f)
        assertEquals(TargetLockStatus.LOST, timeoutOutput.lockStatus)
    }

    private class FakeRobotRepository : RobotRepository {
        val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected("192.168.4.1", 80))
        override val connectionState: StateFlow<ConnectionState> = _connectionState

        val _telemetryFlow = MutableSharedFlow<RobotTelemetry>(replay = 1)
        override val telemetryFlow: Flow<RobotTelemetry> = _telemetryFlow

        val _currentTelemetry = MutableStateFlow(RobotTelemetry(isBalancing = true, state = "BALANCING"))
        override val currentTelemetry: StateFlow<RobotTelemetry> = _currentTelemetry

        var lastSentSpeed: Float? = null
        var lastSentSteer: Float? = null

        override fun connect(host: String, port: Int) {}
        override fun disconnect() {}
        override fun sendDriveCommand(speed: Float, steer: Float) {
            lastSentSpeed = speed
            lastSentSteer = steer
        }
        override suspend fun fetchPidSettings(): Result<com.teethut.twiprobot.domain.model.PidSettings> =
            Result.success(com.teethut.twiprobot.domain.model.PidSettings())
        override suspend fun updatePidSettings(settings: com.teethut.twiprobot.domain.model.PidSettings): Result<Unit> =
            Result.success(Unit)
    }

    @Test
    fun `TrackerViewModel fall alarm halts tracking immediately when robot falls`() = runTest {
        val fakeRepo = FakeRobotRepository()
        val viewModel = TrackerViewModel(repository = fakeRepo)

        viewModel.startTracking()
        assertTrue(viewModel.uiState.value.isTrackingActive)

        // 1. Simulate balancing first
        fakeRepo._telemetryFlow.emit(
            RobotTelemetry(
                isBalancing = true,
                state = "BALANCING",
                pitch = 0.0f
            )
        )

        // 2. Simulate robot falling over
        fakeRepo._telemetryFlow.emit(
            RobotTelemetry(
                isBalancing = false,
                state = "FALLEN",
                pitch = 45.0f
            )
        )

        assertFalse("Tracking should automatically halt when robot falls", viewModel.uiState.value.isTrackingActive)
        assertEquals(0f, fakeRepo.lastSentSpeed ?: 0f, 0.001f)
        assertEquals(0f, fakeRepo.lastSentSteer ?: 0f, 0.001f)

        viewModel.stopTracking()
    }

    @Test
    fun `when invertThrottle is true in FOLLOW mode, target too far generates negative speed`() {
        val controller = TrackingPidController()
        val settingsNormal = TrackerSettings(targetDistanceFraction = 0.20f, deadbandDist = 0.04f, maxSpeed = 700f, invertThrottle = false)
        val settingsInverted = TrackerSettings(targetDistanceFraction = 0.20f, deadbandDist = 0.04f, maxSpeed = 700f, invertThrottle = true)
        val farTarget = DetectedTarget(normalizedX = 0f, normalizedHeight = 0.08f)

        val outNormal = controller.computeDriveCommand(farTarget, settingsNormal, TrackingMode.FOLLOW)
        val outInverted = controller.computeDriveCommand(farTarget, settingsInverted, TrackingMode.FOLLOW)

        assertTrue("Normal speed should be positive", outNormal.speed > 0f)
        assertEquals("Inverted speed should be negative of normal speed", -outNormal.speed, outInverted.speed, 0.001f)
    }

    @Test
    fun `when invertThrottle is true and allowReverse is true, target too close generates positive speed`() {
        val controller = TrackingPidController()
        val settingsNormal = TrackerSettings(targetDistanceFraction = 0.18f, deadbandDist = 0.04f, maxSpeed = 800f, allowReverse = true, invertThrottle = false)
        val settingsInverted = TrackerSettings(targetDistanceFraction = 0.18f, deadbandDist = 0.04f, maxSpeed = 800f, allowReverse = true, invertThrottle = true)
        val closeTarget = DetectedTarget(normalizedX = 0f, normalizedHeight = 0.35f)

        val outNormal = controller.computeDriveCommand(closeTarget, settingsNormal, TrackingMode.FOLLOW)
        val outInverted = controller.computeDriveCommand(closeTarget, settingsInverted, TrackingMode.FOLLOW)

        assertTrue("Normal reverse speed should be negative", outNormal.speed < 0f)
        assertEquals("Inverted reverse speed should be positive of normal speed", -outNormal.speed, outInverted.speed, 0.001f)
    }

    @Test
    fun `DriveViewModel setInvertDirection inverts calculated speed`() = runTest {
        val fakeRepo = FakeRobotRepository()
        val driveVm = DriveViewModel(repository = fakeRepo)

        driveVm.onJoystickMove(0.5f, 0f)
        val normalSpeed = driveVm.uiState.value.calculatedSpeed
        assertTrue("Forward joystick should produce positive speed", normalSpeed > 0f)

        driveVm.setInvertDirection(true)
        val invertedSpeed = driveVm.uiState.value.calculatedSpeed
        assertEquals("Inverted direction should negate speed", -normalSpeed, invertedSpeed, 0.001f)

        driveVm.onDriveRelease()
    }
}
