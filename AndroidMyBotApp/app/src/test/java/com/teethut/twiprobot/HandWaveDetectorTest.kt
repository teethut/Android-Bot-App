package com.teethut.twiprobot

import com.teethut.twiprobot.data.vision.HandWaveDetector
import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.domain.model.PidSettings
import com.teethut.twiprobot.domain.model.RobotTelemetry
import com.teethut.twiprobot.domain.repository.RobotRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HandWaveDetectorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var tracker: HandWaveDetector.WristTracker

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
        override suspend fun fetchPidSettings(): Result<PidSettings> = Result.success(PidSettings())
        override suspend fun updatePidSettings(settings: PidSettings): Result<Unit> = Result.success(Unit)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tracker = HandWaveDetector.WristTracker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stationaryHand_doesNotTriggerWave() {
        var detected = false
        val startTime = 1000L
        for (i in 0 until 10) {
            val res = tracker.addSample(
                relX = 150f,
                y = 200f,
                now = startTime + (i * 50L),
                windowMs = 1300L,
                minStrokeWidth = 36f,
                minTotalTravel = 120f,
                requiredReversals = 3
            )
            if (res) detected = true
        }
        assertFalse("Stationary hand should never trigger wave gesture", detected)
    }

    @Test
    fun unidirectionalMovement_doesNotTriggerWave() {
        var detected = false
        val startTime = 1000L
        // Moving continuously right from 50 to 450 without reversing
        for (i in 0 until 10) {
            val res = tracker.addSample(
                relX = 50f + (i * 40f),
                y = 200f,
                now = startTime + (i * 50L),
                windowMs = 1300L,
                minStrokeWidth = 36f,
                minTotalTravel = 120f,
                requiredReversals = 3
            )
            if (res) detected = true
        }
        assertFalse("One-way linear movement should not trigger wave gesture", detected)
    }

    @Test
    fun robotTiltWobble_doesNotTriggerWave() {
        // When robot balances/wobbles, body and arm move together, keeping relative X near 0
        var detected = false
        val startTime = 1000L
        var time = startTime
        val bodyOffsets = listOf(0f, 15f, 30f, 15f, -10f, -25f, -10f, 15f)
        for (offset in bodyOffsets) {
            time += 80L
            // arm moves along with shoulder, so relX = 0f + slight jitter
            val relX = 0f + (offset * 0.05f)
            val res = tracker.addSample(
                relX = relX,
                y = 200f,
                now = time,
                windowMs = 1300L,
                minStrokeWidth = 36f,
                minTotalTravel = 120f,
                requiredReversals = 3
            )
            if (res) detected = true
        }
        assertFalse("Robot balancing wobble cancelling should prevent false wave detection", detected)
    }

    @Test
    fun deliberateWavingMotion_triggersWave() {
        var detected = false
        val startTime = 1000L
        var time = startTime

        // Simulate a 4-stroke wave (3 reversals):
        // Stroke 1: Right from 100 to 160 (time ~150ms)
        val stroke1 = listOf(100f, 120f, 140f, 160f)
        for (x in stroke1) {
            time += 40L
            if (tracker.addSample(x, 200f, time, 1300L, 36f, 120f, 3)) detected = true
        }

        // Stroke 2: Left from 160 down to 90 (Reversal 1, time ~160ms)
        val stroke2 = listOf(140f, 120f, 100f, 90f)
        for (x in stroke2) {
            time += 40L
            if (tracker.addSample(x, 200f, time, 1300L, 36f, 120f, 3)) detected = true
        }

        // Stroke 3: Right from 90 to 160 (Reversal 2, time ~160ms)
        val stroke3 = listOf(110f, 130f, 150f, 160f)
        for (x in stroke3) {
            time += 40L
            if (tracker.addSample(x, 200f, time, 1300L, 36f, 120f, 3)) detected = true
        }

        // Stroke 4: Left from 160 down to 95 (Reversal 3 - Wave Confirmed!)
        val stroke4 = listOf(140f, 120f, 105f, 95f)
        for (x in stroke4) {
            time += 40L
            if (tracker.addSample(x, 200f, time, 1300L, 36f, 120f, 3)) detected = true
        }

        assertTrue("Deliberate 3-reversal waving motion should trigger wave greeting", detected)
    }

    @Test
    fun smallJitter_doesNotTriggerWave() {
        var detected = false
        val startTime = 1000L
        var time = startTime

        // Jittering by tiny amounts (1px - 2px)
        val jitter = listOf(100f, 102f, 100f, 101f, 100f, 102f, 100f)
        for (x in jitter) {
            time += 40L
            if (tracker.addSample(x, 200f, time, 1300L, 36f, 120f, 3)) detected = true
        }

        assertFalse("Tiny camera landmark jitter should be filtered out", detected)
    }

    @Test
    fun stopGestureTracker_steadyHand_activatesAfterHoldDuration() {
        val stopTracker = HandWaveDetector.StopGestureTracker(minHoldDurationMs = 300L, maxMovementPx = 55f)
        val startTime = 1000L

        // Initial frame
        assertFalse(stopTracker.update(isStopPosture = true, x = 100f, y = 200f, now = startTime))

        // After 150ms: still holding, not yet 300ms
        assertFalse(stopTracker.update(isStopPosture = true, x = 102f, y = 198f, now = startTime + 150L))

        // After 350ms: held steadily >= 300ms -> active!
        assertTrue(stopTracker.update(isStopPosture = true, x = 103f, y = 201f, now = startTime + 350L))
        assertTrue(stopTracker.isActive)

        // Drop hand for 250ms (beyond 200ms grace period) -> should deactivate
        assertFalse(stopTracker.update(isStopPosture = false, x = 103f, y = 201f, now = startTime + 600L))
        assertFalse(stopTracker.isActive)
    }

    @Test
    fun stopGestureTracker_excessiveMovement_resetsTimer() {
        val stopTracker = HandWaveDetector.StopGestureTracker(minHoldDurationMs = 300L, maxMovementPx = 55f)
        val startTime = 1000L

        // Hold starts at (100, 200)
        stopTracker.update(isStopPosture = true, x = 100f, y = 200f, now = startTime)

        // Large sudden hand jerk to (200, 200) (>55px) at 200ms
        stopTracker.update(isStopPosture = true, x = 200f, y = 200f, now = startTime + 200L)

        // At 350ms from start (but only 150ms from jerk) -> not yet active
        assertFalse(stopTracker.update(isStopPosture = true, x = 201f, y = 200f, now = startTime + 350L))

        // At 550ms (350ms after jerk) -> now active
        assertTrue(stopTracker.update(isStopPosture = true, x = 202f, y = 201f, now = startTime + 550L))
    }

    @Test
    fun trackerViewModel_onStopGestureChanged_commandsNegativeSpeed() = runTest {
        val fakeRepo = FakeRobotRepository()
        val vm = TrackerViewModel(repository = fakeRepo)

        assertFalse(vm.uiState.value.isStopGestureActive)
        // Activate stop gesture
        vm.onStopGestureChanged(true)

        assertTrue(vm.uiState.value.isStopGestureActive)
        assertTrue(vm.uiState.value.calculatedSpeed < 0f)
        assertTrue(vm.uiState.value.statusMessage.contains("Backing Up"))

        // Release stop gesture
        vm.onStopGestureChanged(false)
        assertFalse(vm.uiState.value.isStopGestureActive)
        assertTrue(vm.uiState.value.calculatedSpeed == 0f)
    }

    @Test
    fun trackerViewModel_stopGestureDisabled_doesNotBackUp() = runTest {
        val fakeRepo = FakeRobotRepository()
        val vm = TrackerViewModel(repository = fakeRepo)
        vm.updateSettings(vm.uiState.value.settings.copy(stopGestureEnabled = false))

        vm.onStopGestureChanged(true)
        assertTrue(vm.uiState.value.isStopGestureActive)
        assertTrue(vm.uiState.value.calculatedSpeed == 0f)
    }

    @Test
    fun trackerViewModel_invertBackwardDirection_invertsSpeedSign() = runTest {
        val fakeRepo = FakeRobotRepository()
        val vm = TrackerViewModel(repository = fakeRepo)

        // Default: negative speed for backward
        vm.onStopGestureChanged(true)
        val defaultSpeed = vm.uiState.value.calculatedSpeed
        assertTrue("Default backward speed should be negative", defaultSpeed < 0f)

        // Invert backward direction toggle flipped
        vm.toggleInvertBackwardDirection()
        vm.onStopGestureChanged(true)
        val invertedSpeed = vm.uiState.value.calculatedSpeed
        assertTrue("Inverted backward speed should be positive", invertedSpeed > 0f)
        assertTrue("Magnitudes should match", kotlin.math.abs(defaultSpeed) == kotlin.math.abs(invertedSpeed))
    }

    @Test
    fun trackerViewModel_onGestureChanged_handlesExtendedGestures() = runTest {
        val fakeRepo = FakeRobotRepository()
        val vm = TrackerViewModel(repository = fakeRepo)

        // Test Victory sign
        vm.onGestureChanged(com.teethut.twiprobot.domain.model.RobotHandGesture.VICTORY, 0.95f)
        assertTrue(vm.uiState.value.activeGesture == com.teethut.twiprobot.domain.model.RobotHandGesture.VICTORY)
        assertTrue(vm.uiState.value.statusMessage.contains("Peace / Victory"))
        assertFalse(vm.uiState.value.isStopGestureActive)

        // Test Open Palm Stop
        vm.onGestureChanged(com.teethut.twiprobot.domain.model.RobotHandGesture.OPEN_PALM_STOP, 0.98f)
        assertTrue(vm.uiState.value.isStopGestureActive)
        assertTrue(vm.uiState.value.calculatedSpeed < 0f)

        // Test Gesture Release
        vm.onGestureChanged(com.teethut.twiprobot.domain.model.RobotHandGesture.NONE, 0f)
        assertFalse(vm.uiState.value.isStopGestureActive)
        assertTrue(vm.uiState.value.calculatedSpeed == 0f)
    }

    @Test
    fun trackerViewModel_closedFist_holdsPositionZeroingSpeedAndSteerEvenWhenTargetFarAndOffCenter() = runTest {
        val fakeRepo = FakeRobotRepository()
        val vm = TrackerViewModel(repository = fakeRepo)

        // 1. Target is far away and off-center -> normal tracking commands positive speed and steer
        val farOffCenterTarget = com.teethut.twiprobot.domain.model.DetectedTarget(
            trackingId = 1,
            normalizedX = 0.55f,
            normalizedHeight = 0.08f
        )
        vm.onTargetDetected(farOffCenterTarget, fps = 20)
        assertTrue("Expected positive follow speed before fist", vm.uiState.value.calculatedSpeed > 0f)
        assertTrue("Expected positive steer before fist", vm.uiState.value.calculatedSteer > 0f)

        // 2. User shows Closed Fist -> must immediately zero both speed and steer and send (0, 0)
        vm.onGestureChanged(com.teethut.twiprobot.domain.model.RobotHandGesture.CLOSED_FIST, 0.92f)
        assertTrue(vm.uiState.value.isFistHoldActive)
        assertTrue(vm.uiState.value.calculatedSpeed == 0f)
        assertTrue(vm.uiState.value.calculatedSteer == 0f)
        assertTrue(fakeRepo.lastSentSpeed == 0f)
        assertTrue(fakeRepo.lastSentSteer == 0f)
        assertTrue(vm.uiState.value.statusMessage.contains("Holding Position"))

        // 3. Subsequent face detection frames while Closed Fist is held must NOT override zero speed/steer
        vm.onTargetDetected(farOffCenterTarget, fps = 20)
        assertTrue("Speed must remain 0 while fist hold is active", vm.uiState.value.calculatedSpeed == 0f)
        assertTrue("Steer must remain 0 while fist hold is active", vm.uiState.value.calculatedSteer == 0f)
        assertTrue(vm.uiState.value.statusMessage.contains("Holding Position"))

        // 4. Releasing fist resumes normal tracking on next frame
        vm.onGestureChanged(com.teethut.twiprobot.domain.model.RobotHandGesture.NONE, 0f)
        assertFalse(vm.uiState.value.isFistHoldActive)
        vm.onTargetDetected(farOffCenterTarget, fps = 20)
        assertTrue("Speed should resume after fist is released", vm.uiState.value.calculatedSpeed > 0f)
        assertTrue("Steer should resume after fist is released", vm.uiState.value.calculatedSteer > 0f)
    }

    @Test
    fun gestureHoldStabilizer_closedFist_retainsHoldAcrossDropoutsAndMisclassifications() {
        val stabilizer = com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.GestureHoldStabilizer(
            fistHoldConfirmMs = 0L,
            fistHoldGracePeriodMs = 800L,
            fistOverrideConfirmMs = 350L
        )
        val t0 = 1000L

        // 1. Multi-hand frame: Hand 0 is idle ("None"), Hand 1 is "Closed_Fist"
        val res1 = stabilizer.processCandidates(
            listOf(
                com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("None", 0.90f, 0.2f, 0.8f),
                com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("Closed_Fist", 0.82f, 0.5f, 0.4f)
            ),
            now = t0
        )
        assertTrue(res1?.first == com.teethut.twiprobot.domain.model.RobotHandGesture.CLOSED_FIST)
        assertTrue(stabilizer.isFistActive)

        // 2. Camera motion blur during robot braking: 400ms of empty hand detections -> must keep holding!
        val resDropout = stabilizer.processCandidates(emptyList(), now = t0 + 400L)
        assertTrue("Expected null (no state change) during 800ms fist hold grace window", resDropout == null)
        assertTrue("Fist hold must remain active across 400ms frame dropout", stabilizer.isFistActive)

        // 3. Hand still in frame but unclassified ("None") at 650ms -> must still keep holding!
        val resNoneInGrace = stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("None", 0.5f)),
            now = t0 + 650L
        )
        assertTrue(resNoneInGrace == null)
        assertTrue(stabilizer.isFistActive)

        // 4. Re-detect Closed_Fist at 700ms -> refreshes hold timer
        val resRefresh = stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("Closed_Fist", 0.88f)),
            now = t0 + 700L
        )
        assertTrue(resRefresh?.first == com.teethut.twiprobot.domain.model.RobotHandGesture.CLOSED_FIST)

        // 5. Transient 1-frame low-angle misclassification as Thumb_Up at 800ms (100ms after fist) -> ignored!
        val resGlitch = stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("Thumb_Up", 0.72f)),
            now = t0 + 800L
        )
        assertTrue("Transient 1-frame Thumb_Up glitch should not break fist hold", resGlitch == null)
        assertTrue(stabilizer.isFistActive)

        // 6. Hand lowered for > 800ms after last fist (t0 + 700L + 850L = t0 + 1550L) -> cleanly releases to NONE
        val resRelease = stabilizer.processCandidates(emptyList(), now = t0 + 1550L)
        assertTrue(resRelease?.first == com.teethut.twiprobot.domain.model.RobotHandGesture.NONE)
        assertFalse(stabilizer.isFistActive)
    }

    @Test
    fun trackerViewModel_transitionsCleanlyBetweenOpenPalmStopAndClosedFist() = runTest {
        val fakeRepo = FakeRobotRepository()
        val vm = TrackerViewModel(repository = fakeRepo)

        // Start with Open Palm Stop (reverse)
        vm.onGestureChanged(com.teethut.twiprobot.domain.model.RobotHandGesture.OPEN_PALM_STOP, 0.95f)
        assertTrue(vm.uiState.value.isStopGestureActive)
        assertFalse(vm.uiState.value.isFistHoldActive)
        assertTrue(vm.uiState.value.calculatedSpeed < 0f)

        // Transition directly to Closed Fist (halt / hold position)
        vm.onGestureChanged(com.teethut.twiprobot.domain.model.RobotHandGesture.CLOSED_FIST, 0.94f)
        assertFalse(vm.uiState.value.isStopGestureActive)
        assertTrue(vm.uiState.value.isFistHoldActive)
        assertTrue(vm.uiState.value.calculatedSpeed == 0f)
        assertTrue(vm.uiState.value.calculatedSteer == 0f)
        assertTrue(fakeRepo.lastSentSpeed == 0f)
        assertTrue(fakeRepo.lastSentSteer == 0f)
        assertTrue(vm.uiState.value.statusMessage.contains("Holding Position"))

        // Transition back to Open Palm Stop (reverse)
        vm.onGestureChanged(com.teethut.twiprobot.domain.model.RobotHandGesture.OPEN_PALM_STOP, 0.96f)
        assertTrue(vm.uiState.value.isStopGestureActive)
        assertFalse(vm.uiState.value.isFistHoldActive)
        assertTrue(vm.uiState.value.calculatedSpeed < 0f)
    }

    @Test
    fun fistPositionHoldController_brakesForwardMomentumAndCounterSteersWheelDrift() {
        val holdController = com.teethut.twiprobot.data.vision.FistPositionHoldController()
        val settings = com.teethut.twiprobot.domain.model.TrackerSettings()

        // 1. Engage while robot is still rolling forward at 600 steps/s
        val movingTelemetry = RobotTelemetry(
            timestampMs = 1000L,
            state = "BALANCING",
            isBalancing = true,
            speed = 600f,
            encLeft = 1000L,
            encRight = 1000L
        )
        holdController.engage(movingTelemetry, nowMs = 1000L)
        val brakingCmd = holdController.computeHoldCommand(movingTelemetry, settings, nowMs = 1000L)
        assertTrue("Expected negative active braking command when rolling forward", brakingCmd.speed < -100f)

        // 2. Robot decelerates to near-stop at enc = 1150 -> controller locks stationary anchor at 1150
        val settledTelemetry = RobotTelemetry(
            timestampMs = 1250L,
            state = "BALANCING",
            isBalancing = true,
            speed = 15f,
            encLeft = 1150L,
            encRight = 1150L
        )
        val settledCmd = holdController.computeHoldCommand(settledTelemetry, settings, nowMs = 1250L)
        assertTrue("Expected zero command right at settled anchor", settledCmd.speed == 0f)

        // 3. Robot drifts forward (enc = 1280) and twists right (encLeft = 1320, encRight = 1240)
        val driftedTelemetry = RobotTelemetry(
            timestampMs = 1500L,
            state = "BALANCING",
            isBalancing = true,
            speed = 45f,
            encLeft = 1320L,
            encRight = 1240L
        )
        val correctingCmd = holdController.computeHoldCommand(driftedTelemetry, settings, nowMs = 1500L)
        assertTrue("Expected negative restoring speed when drifted forward past anchor", correctingCmd.speed < -100f)
        assertTrue("Expected negative restoring steer when twisted right", correctingCmd.steer < -50f)

        // 4. Verify polarity inversion settings flip restoring command signs properly
        val invertedSettings = settings.copy(invertThrottle = true, invertSteer = true)
        val invertedCmd = holdController.computeHoldCommand(driftedTelemetry, invertedSettings, nowMs = 1550L)
        assertTrue("Inverted throttle must flip restoring speed positive", invertedCmd.speed > 100f)
        assertTrue("Inverted steering must flip restoring steer positive", invertedCmd.steer > 50f)
    }

    @Test
    fun trackerViewModel_closedFist_maintainsZeroSpeedAndSteerAcrossTelemetryUpdatesWithoutFightingRobot() = runTest {
        val fakeRepo = FakeRobotRepository()
        val initialTelemetry = RobotTelemetry(
            timestampMs = 1000L,
            state = "BALANCING",
            isBalancing = true,
            speed = 0f,
            encLeft = 500L,
            encRight = 500L
        )
        fakeRepo._currentTelemetry.value = initialTelemetry
        val vm = TrackerViewModel(repository = fakeRepo)

        // Engage Fist Hold at enc = 500
        vm.onGestureChanged(com.teethut.twiprobot.domain.model.RobotHandGesture.CLOSED_FIST, 0.95f)
        assertTrue(vm.uiState.value.isFistHoldActive)
        assertTrue(fakeRepo.lastSentSpeed == 0f)
        assertTrue(fakeRepo.lastSentSteer == 0f)

        // Robot receives telemetry frames while balancing -> must keep speed at 0f and steer at 0f
        val drifted = RobotTelemetry(
            timestampMs = 1200L,
            state = "BALANCING",
            isBalancing = true,
            speed = 50f,
            encLeft = 660L,
            encRight = 620L
        )
        fakeRepo._currentTelemetry.value = drifted
        fakeRepo._telemetryFlow.emit(drifted)

        assertTrue(vm.uiState.value.calculatedSpeed == 0f)
        assertTrue(vm.uiState.value.calculatedSteer == 0f)
        assertTrue(vm.uiState.value.statusMessage.contains("Holding Position"))
    }

    @Test
    fun gestureHoldStabilizer_rejectsUnclassifiedPosturesAndRequiresGenuineHighConfidenceClosedFist() {
        val stabilizer = com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.GestureHoldStabilizer(
            fistHoldConfirmMs = 180L,
            fistHoldGracePeriodMs = 250L
        )
        val t0 = 1000L

        // 1. Hand candidate with category "None" even with curled fingers is rejected to prevent false positives
        val resNone = stabilizer.processCandidates(
            listOf(
                com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate(
                    categoryName = "None",
                    score = 0.5f,
                    curledFingerCount = 4,
                    extendedFingerCount = 0
                )
            ),
            now = t0
        )
        assertTrue("Category 'None' must never trigger a fist hold", resNone == null)
        assertFalse(stabilizer.isFistActive)

        // 2. Genuine Closed_Fist below confidence 0.75 is also rejected
        val resLowConf = stabilizer.processCandidates(
            listOf(
                com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate(
                    categoryName = "Closed_Fist",
                    score = 0.60f
                )
            ),
            now = t0 + 50L
        )
        assertTrue("Low-confidence Closed_Fist must be rejected", resLowConf == null)
        assertFalse(stabilizer.isFistActive)

        // 3. Genuine Closed_Fist with high confidence (>= 0.75) held for >= 180ms activates cleanly
        stabilizer.processCandidates(
            listOf(
                com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate(
                    categoryName = "Closed_Fist",
                    score = 0.85f
                )
            ),
            now = t0 + 100L
        )
        val resConfirmed = stabilizer.processCandidates(
            listOf(
                com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate(
                    categoryName = "Closed_Fist",
                    score = 0.88f
                )
            ),
            now = t0 + 300L
        )
        assertTrue(resConfirmed?.first == com.teethut.twiprobot.domain.model.RobotHandGesture.CLOSED_FIST)
        assertTrue(stabilizer.isFistActive)
    }

    @Test
    fun trackerViewModel_fistHoldCancelsWaveBannerAndTakesPriorityOverWave() = runTest {
        val fakeRepo = FakeRobotRepository()
        val vm = TrackerViewModel(repository = fakeRepo)

        // 1. Trigger Wave -> sets isWaveDetected = true
        vm.onWaveDetected()
        assertTrue(vm.uiState.value.isWaveDetected)
        assertTrue(vm.uiState.value.statusMessage.contains("Wave Detected"))

        // 2. User immediately shows Closed Fist within the 1600ms wave window
        vm.onGestureChanged(com.teethut.twiprobot.domain.model.RobotHandGesture.CLOSED_FIST, 0.93f)
        assertTrue(vm.uiState.value.isFistHoldActive)
        assertFalse("Fist hold must cancel active wave state", vm.uiState.value.isWaveDetected)
        assertTrue(vm.uiState.value.statusMessage.contains("Holding Position"))

        // 3. Subsequent target frame must keep "Holding Position" status, not wave status
        val target = com.teethut.twiprobot.domain.model.DetectedTarget(1, 0.5f, 0.2f)
        vm.onTargetDetected(target, fps = 30)
        assertTrue(vm.uiState.value.statusMessage.contains("Holding Position"))

        // 4. Stray pose-based onWaveDetected() while holding a fist must be ignored
        vm.onWaveDetected()
        assertFalse("Pose wave detector must not interrupt active fist hold", vm.uiState.value.isWaveDetected)
        assertTrue(vm.uiState.value.statusMessage.contains("Holding Position"))

        // 5. CatEmotionEngine must also prioritize isFistHoldActive over isWaveTriggered
        val emotion = com.teethut.twiprobot.presentation.tracker.components.CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = com.teethut.twiprobot.domain.model.RobotHandGesture.NONE,
            isWaveTriggered = true,
            isStopGestureActive = false,
            lockStatus = com.teethut.twiprobot.domain.model.TargetLockStatus.LOCKED,
            isFistHoldActive = true
        )
        assertTrue(emotion.statusBannerText?.contains("HALT") == true)
    }

    @Test
    fun gestureHoldStabilizer_rejectsSubThresholdAndTransientGlitches() {
        val stabilizer = com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.GestureHoldStabilizer(
            fistHoldConfirmMs = 200L,
            staticGestureConfirmMs = 300L
        )
        val t0 = 1000L

        // 1. Low confidence candidate (< 0.65f) is rejected and returns null
        val lowConfRes = stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("Victory", 0.50f)),
            now = t0
        )
        assertTrue("Sub-threshold 0.50f Victory should be rejected", lowConfRes == null)
        assertTrue(stabilizer.activeGesture == com.teethut.twiprobot.domain.model.RobotHandGesture.NONE)

        // 2. High confidence Victory at t0 starts confirmation window, returns null
        val startVictory = stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("Victory", 0.85f)),
            now = t0
        )
        assertTrue("Frame 1 of Victory should return null while confirming hold", startVictory == null)

        // 3. Frame at 150ms (< 300ms staticGestureConfirmMs) still returns null
        val midVictory = stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("Victory", 0.85f)),
            now = t0 + 150L
        )
        assertTrue("At 150ms, Victory should still be confirming", midVictory == null)

        // 4. Frame at 310ms (>= 300ms) successfully activates VICTORY
        val confirmedVictory = stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("Victory", 0.85f)),
            now = t0 + 310L
        )
        assertTrue(confirmedVictory?.first == com.teethut.twiprobot.domain.model.RobotHandGesture.VICTORY)
        assertTrue(stabilizer.activeGesture == com.teethut.twiprobot.domain.model.RobotHandGesture.VICTORY)
    }

    @Test
    fun gestureHoldStabilizer_fistRequiresHoldConfirmationBeforeActivating() {
        val stabilizer = com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.GestureHoldStabilizer(
            fistHoldConfirmMs = 200L,
            staticGestureConfirmMs = 300L
        )
        val t0 = 2000L

        // 1. Single-frame fist glitch at t0 -> returns null during hold confirmation
        val glitchFist = stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("Closed_Fist", 0.85f)),
            now = t0
        )
        assertTrue("Frame 1 of fist should return null during 200ms hold confirm", glitchFist == null)
        assertFalse("Fist hold should not be active on frame 1", stabilizer.isFistActive)

        // 2. Glitch ends on next frame at t0 + 50ms with unclassified hand -> cancels confirmation
        stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("None", 0.50f)),
            now = t0 + 50L
        )
        assertFalse(stabilizer.isFistActive)

        // 3. Deliberate fist sustained for full 200ms
        stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("Closed_Fist", 0.88f)),
            now = t0 + 200L
        )
        val activeFist = stabilizer.processCandidates(
            listOf(com.teethut.twiprobot.data.vision.MediaPipeGestureRecognizerHelper.HandCandidate("Closed_Fist", 0.88f)),
            now = t0 + 410L
        )
        assertTrue(activeFist?.first == com.teethut.twiprobot.domain.model.RobotHandGesture.CLOSED_FIST)
        assertTrue(stabilizer.isFistActive)
    }
}

