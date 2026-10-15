package com.teethut.twiprobot.data.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.teethut.twiprobot.domain.model.RobotHandGesture
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Encapsulates Google MediaPipe Tasks: Gesture Recognizer in LIVE_STREAM mode.
 *
 * Runs real-time 3D hand tracking and gesture classification on device:
 * - Maps Open_Palm with low motion variance to RobotHandGesture.OPEN_PALM_STOP.
 * - Detects hand lateral oscillations (stroke reversals) to trigger RobotHandGesture.WAVE.
 * - Recognizes standard MediaPipe gestures (Closed_Fist, Pointing_Up, Thumb_Up, Victory, etc.)
 *   augmented with 21-landmark geometric finger-curl verification so angled/dorsal fists hold reliably.
 */
class MediaPipeGestureRecognizerHelper(
    private val context: Context,
    private val onGestureResult: (gesture: RobotHandGesture, confidence: Float) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private val tag = "MediaPipeGesture"
    private var gestureRecognizer: GestureRecognizer? = null
    private val stabilizer = GestureHoldStabilizer(
        fistHoldConfirmMs = 200L,
        stopHoldConfirmMs = 300L,
        staticGestureConfirmMs = 300L,
        stopGracePeriodMs = 300L,
        fistHoldGracePeriodMs = 800L,
        fistOverrideConfirmMs = 400L,
        openHandReleaseConfirmMs = 220L
    )
    private var lastFrameTimestampMs: Long = 0L

    data class Point2D(
        val x: Float,
        val y: Float
    )

    data class HandGeometryAnalysis(
        val curledFingerCount: Int = -1,
        val extendedFingerCount: Int = -1,
        val isThumbExtended: Boolean = false,
        val isGeometricFist: Boolean = false,
        val palmScale: Float = 0f
    )

    data class HandCandidate(
        val categoryName: String,
        val score: Float,
        val wristX: Float = 0.5f,
        val wristY: Float = 0.5f,
        val curledFingerCount: Int = -1,
        val extendedFingerCount: Int = -1,
        val isGeometricFist: Boolean = false,
        val isThumbExtended: Boolean = false,
        val palmScale: Float = 0f
    )

    /**
     * Stabilizes MediaPipe hand gesture classifications across frames:
     * - Prioritizes command gestures (Closed_Fist, Open_Palm) across multi-hand detections.
     * - Uses 21-landmark finger curl geometry to maintain Closed_Fist hold even when the
     *   canned neural classifier outputs "None" or "Thumb_Up" from low floor-robot camera angles.
     * - Requires intentional sustained hold windows (200-300ms) before activating gestures.
     * - Provides hold hysteresis (800ms grace period) for Closed_Fist across motion-blur dropouts.
     */
    class GestureHoldStabilizer(
        private val fistHoldConfirmMs: Long = 180L,
        private val stopHoldConfirmMs: Long = 200L,
        private val staticGestureConfirmMs: Long = 0L,
        private val stopGracePeriodMs: Long = 250L,
        private val fistHoldGracePeriodMs: Long = 250L,
        private val fistOverrideConfirmMs: Long = 250L,
        private val openHandReleaseConfirmMs: Long = 180L
    ) {
        private data class HandPoint(val x: Float, val y: Float, val timeMs: Long)
        private val handHistory = ArrayDeque<HandPoint>()
        var reversals = 0
            private set
        private var lastDirection = 0 // +1 right, -1 left
        private var lastExtremeX: Float? = null
        private var lastExtremeTimeMs: Long = 0L
        private var lastReversalTimestamp: Long = 0L

        private var fistCandidateStartTimeMs: Long = 0L
        private var lastFistDetectedTimeMs: Long = 0L
        var isFistActive: Boolean = false
            private set

        private var stopCandidateStartTimeMs: Long = 0L
        private var lastStopDetectedTimeMs: Long = 0L
        var isStopActive: Boolean = false
            private set

        private var otherCandidateCategory: String? = null
        private var otherCandidateStartTimeMs: Long = 0L
        private var openHandReleaseStartTimeMs: Long = 0L
        private var lastGestureDetectedTimeMs: Long = 0L
        var activeGesture: RobotHandGesture = RobotHandGesture.NONE
            private set

        fun reset() {
            resetWaveMotion()
            fistCandidateStartTimeMs = 0L
            lastFistDetectedTimeMs = 0L
            isFistActive = false
            stopCandidateStartTimeMs = 0L
            lastStopDetectedTimeMs = 0L
            isStopActive = false
            otherCandidateCategory = null
            otherCandidateStartTimeMs = 0L
            openHandReleaseStartTimeMs = 0L
            lastGestureDetectedTimeMs = 0L
            activeGesture = RobotHandGesture.NONE
        }

        fun isCandidateFist(c: HandCandidate): Boolean {
            // Require genuine MediaPipe Closed_Fist classification with high confidence
            // and no more than 1 extended finger to prevent false positives.
            return c.categoryName == "Closed_Fist" && c.score >= 0.75f && c.extendedFingerCount <= 1
        }

        fun selectBestCandidate(candidates: List<HandCandidate>): HandCandidate? {
            if (candidates.isEmpty()) return null

            // 1. Prioritize Closed_Fist when genuinely recognized with high confidence
            val fistCandidate = candidates
                .filter { isCandidateFist(it) }
                .maxByOrNull { it.score }
            if (fistCandidate != null) return fistCandidate

            // 2. Prioritize Open_Palm command gesture (when fingers are not curled into a fist)
            val openPalmCandidate = candidates
                .filter {
                    it.categoryName == "Open_Palm" &&
                        it.score >= 0.70f &&
                        !it.isGeometricFist &&
                        it.curledFingerCount < 3
                }
                .maxByOrNull { it.score }
            if (openPalmCandidate != null) return openPalmCandidate

            // 3. Otherwise prioritize any recognized non-None gesture with highest confidence
            val recognizedCandidate = candidates
                .filter { it.categoryName != "None" && it.categoryName.isNotBlank() && it.score >= 0.65f }
                .maxByOrNull { it.score }
            if (recognizedCandidate != null) return recognizedCandidate

            return candidates.first()
        }

        fun processCandidates(
            candidates: List<HandCandidate>,
            now: Long
        ): Pair<RobotHandGesture, Float>? {
            val selected = selectBestCandidate(candidates)

            if (selected == null) {
                // No hands visible in frame
                fistCandidateStartTimeMs = 0L
                stopCandidateStartTimeMs = 0L
                otherCandidateStartTimeMs = 0L
                otherCandidateCategory = null
                openHandReleaseStartTimeMs = 0L

                if (isStopActive) {
                    if (now - lastStopDetectedTimeMs > stopGracePeriodMs) {
                        isStopActive = false
                        activeGesture = RobotHandGesture.NONE
                        return RobotHandGesture.NONE to 0f
                    }
                    return null
                }
                if (isFistActive) {
                    if (now - lastFistDetectedTimeMs > fistHoldGracePeriodMs) {
                        isFistActive = false
                        activeGesture = RobotHandGesture.NONE
                        return RobotHandGesture.NONE to 0f
                    }
                    return null
                }
                if (activeGesture != RobotHandGesture.NONE) {
                    if (now - lastGestureDetectedTimeMs > stopGracePeriodMs) {
                        activeGesture = RobotHandGesture.NONE
                        return RobotHandGesture.NONE to 0f
                    }
                    return null
                }
                return RobotHandGesture.NONE to 0f
            }

            val categoryName = selected.categoryName
            val score = selected.score
            val wristX = selected.wristX
            val wristY = selected.wristY

            // 1. Closed_Fist (neural or 21-landmark geometric) takes precedence as Halt / Hold Position
            if (isCandidateFist(selected)) {
                resetWaveMotion()
                isStopActive = false
                stopCandidateStartTimeMs = 0L
                otherCandidateStartTimeMs = 0L
                otherCandidateCategory = null
                openHandReleaseStartTimeMs = 0L

                if (isFistActive) {
                    // Fist is already engaged, maintain hold
                    lastFistDetectedTimeMs = now
                    lastGestureDetectedTimeMs = now
                    return RobotHandGesture.CLOSED_FIST to score
                } else {
                    // Candidate fist: require sustained hold before first engagement
                    if (fistCandidateStartTimeMs == 0L) {
                        fistCandidateStartTimeMs = now
                    }
                    if (now - fistCandidateStartTimeMs >= fistHoldConfirmMs) {
                        isFistActive = true
                        lastFistDetectedTimeMs = now
                        lastGestureDetectedTimeMs = now
                        activeGesture = RobotHandGesture.CLOSED_FIST
                        return RobotHandGesture.CLOSED_FIST to score
                    } else {
                        return null
                    }
                }
            } else {
                fistCandidateStartTimeMs = 0L
            }

            // 2. Evaluate hand lateral motion for Wave gesture (never override an active fist hold unless palm is open)
            val isWaving = updateHandMotion(wristX, wristY, now)
            val isOpenHandWave = categoryName == "Open_Palm" &&
                selected.curledFingerCount < 3 &&
                score >= 0.65f
            val allowWaveTrigger = (!isFistActive && selected.curledFingerCount < 3 && score >= 0.60f) || isOpenHandWave

            if (isWaving && allowWaveTrigger) {
                isStopActive = false
                isFistActive = false
                fistCandidateStartTimeMs = 0L
                stopCandidateStartTimeMs = 0L
                otherCandidateStartTimeMs = 0L
                otherCandidateCategory = null
                openHandReleaseStartTimeMs = 0L
                lastGestureDetectedTimeMs = now
                activeGesture = RobotHandGesture.WAVE
                return RobotHandGesture.WAVE to score.coerceAtLeast(0.85f)
            }

            // 3. Evaluate static gestures from MediaPipe neural classifier
            return when (categoryName) {
                "Open_Palm" -> {
                    otherCandidateStartTimeMs = 0L
                    otherCandidateCategory = null
                    openHandReleaseStartTimeMs = 0L
                    fistCandidateStartTimeMs = 0L

                    // Reject false Open_Palm if landmarks show 3+ fingers are still curled in a fist
                    if (selected.curledFingerCount >= 3) {
                        if (isFistActive) {
                            lastFistDetectedTimeMs = now
                        }
                        return null
                    }

                    // Require at least 0.65f confidence for Open Palm
                    if (score < 0.65f) {
                        stopCandidateStartTimeMs = 0L
                        return null
                    }

                    // Require higher confidence to break an active fist hold
                    if (isFistActive && score < 0.70f) {
                        return null
                    }

                    if (stopCandidateStartTimeMs == 0L) {
                        stopCandidateStartTimeMs = now
                    }
                    lastStopDetectedTimeMs = now

                    if (reversals == 0 && (now - stopCandidateStartTimeMs >= stopHoldConfirmMs)) {
                        isStopActive = true
                        isFistActive = false
                        lastGestureDetectedTimeMs = now
                        activeGesture = RobotHandGesture.OPEN_PALM_STOP
                        RobotHandGesture.OPEN_PALM_STOP to score
                    } else {
                        null
                    }
                }
                "Pointing_Up", "Thumb_Up", "Thumb_Down", "Victory", "ILoveYou" -> {
                    stopCandidateStartTimeMs = 0L
                    fistCandidateStartTimeMs = 0L
                    openHandReleaseStartTimeMs = 0L

                    // Require at least 0.65f confidence for any recognized static gesture
                    if (score < 0.65f) {
                        otherCandidateStartTimeMs = 0L
                        otherCandidateCategory = null
                        return null
                    }

                    val mapped = when (categoryName) {
                        "Pointing_Up" -> RobotHandGesture.POINTING_UP
                        "Thumb_Up" -> RobotHandGesture.THUMB_UP
                        "Thumb_Down" -> RobotHandGesture.THUMB_DOWN
                        "Victory" -> RobotHandGesture.VICTORY
                        "ILoveYou" -> RobotHandGesture.LOVE
                        else -> RobotHandGesture.NONE
                    }

                    // If Fist Hold is active, require sustained high-confidence classification
                    // before overriding so an angled-fist glitch does not break position hold
                    if (isFistActive) {
                        if (otherCandidateCategory != categoryName) {
                            otherCandidateCategory = categoryName
                            otherCandidateStartTimeMs = now
                        }
                        if (now - otherCandidateStartTimeMs >= fistOverrideConfirmMs && score >= 0.70f) {
                            isFistActive = false
                            isStopActive = false
                            otherCandidateStartTimeMs = 0L
                            otherCandidateCategory = null
                            lastGestureDetectedTimeMs = now
                            activeGesture = mapped
                            mapped to score
                        } else {
                            null
                        }
                    } else {
                        // Temporal confirmation: Must be held steadily for staticGestureConfirmMs
                        if (otherCandidateCategory != categoryName) {
                            otherCandidateCategory = categoryName
                            otherCandidateStartTimeMs = now
                        }
                        if (now - otherCandidateStartTimeMs >= staticGestureConfirmMs) {
                            isStopActive = false
                            isFistActive = false
                            lastGestureDetectedTimeMs = now
                            activeGesture = mapped
                            mapped to score
                        } else {
                            null
                        }
                    }
                }
                else -> {
                    // Unknown / "None" category while hand landmarks are still visible
                    stopCandidateStartTimeMs = 0L
                    fistCandidateStartTimeMs = 0L
                    otherCandidateStartTimeMs = 0L
                    otherCandidateCategory = null

                    // If user clearly opened/relaxed their fingers in frame while holding a fist, release faster
                    val isClearlyUncurledHand = selected.curledFingerCount in 0..1 &&
                        selected.extendedFingerCount >= 3

                    if (isFistActive && isClearlyUncurledHand) {
                        if (openHandReleaseStartTimeMs == 0L) {
                            openHandReleaseStartTimeMs = now
                        }
                        if (now - openHandReleaseStartTimeMs >= openHandReleaseConfirmMs) {
                            isFistActive = false
                            openHandReleaseStartTimeMs = 0L
                            activeGesture = RobotHandGesture.NONE
                            return RobotHandGesture.NONE to 0f
                        }
                    } else {
                        openHandReleaseStartTimeMs = 0L
                    }

                    when {
                        isStopActive && (now - lastStopDetectedTimeMs > stopGracePeriodMs) -> {
                            isStopActive = false
                            activeGesture = RobotHandGesture.NONE
                            RobotHandGesture.NONE to 0f
                        }
                        isFistActive && (now - lastFistDetectedTimeMs > fistHoldGracePeriodMs) -> {
                            isFistActive = false
                            activeGesture = RobotHandGesture.NONE
                            RobotHandGesture.NONE to 0f
                        }
                        !isStopActive && !isFistActive && activeGesture != RobotHandGesture.NONE &&
                            (now - lastGestureDetectedTimeMs > stopGracePeriodMs) -> {
                            activeGesture = RobotHandGesture.NONE
                            RobotHandGesture.NONE to 0f
                        }
                        else -> null
                    }
                }
            }
        }

        private fun resetWaveMotion() {
            handHistory.clear()
            reversals = 0
            lastDirection = 0
            lastExtremeX = null
            lastExtremeTimeMs = 0L
            lastReversalTimestamp = 0L
        }

        /**
         * Evaluates horizontal stroke oscillation (waving).
         * Returns true when >= 3 distinct reversals occur within 1.3 seconds.
         */
        private fun updateHandMotion(normX: Float, normY: Float, now: Long): Boolean {
            while (handHistory.isNotEmpty() && (now - handHistory.first().timeMs > 1300L)) {
                handHistory.removeFirst()
            }
            handHistory.addLast(HandPoint(normX, normY, now))

            if (handHistory.size < 4) return false

            if (now - lastReversalTimestamp > 600L) {
                reversals = 0
            }

            val prev = handHistory[handHistory.size - 2]
            val dx = normX - prev.x

            if (abs(dx) > 0.020f) { // ~2.0% frame width movement threshold
                val currentDir = if (dx > 0) 1 else -1
                if (lastDirection != 0 && currentDir != lastDirection) {
                    val strokeDistance = lastExtremeX?.let { abs(normX - it) } ?: 0f
                    val strokeDuration = if (lastExtremeTimeMs > 0L) now - lastExtremeTimeMs else 250L

                    if (strokeDistance >= 0.065f && strokeDuration in 80L..550L) {
                        reversals++
                        lastReversalTimestamp = now
                        lastExtremeX = normX
                        lastExtremeTimeMs = now
                        lastDirection = currentDir
                    }
                } else if (lastDirection == 0) {
                    lastDirection = currentDir
                    lastExtremeX = normX
                    lastExtremeTimeMs = now
                } else {
                    if (currentDir == 1 && (lastExtremeX == null || normX > lastExtremeX!!)) {
                        lastExtremeX = normX
                        lastExtremeTimeMs = now
                    } else if (currentDir == -1 && (lastExtremeX == null || normX < lastExtremeX!!)) {
                        lastExtremeX = normX
                        lastExtremeTimeMs = now
                    }
                }
            }

            var totalTravel = 0f
            for (i in 1 until handHistory.size) {
                totalTravel += abs(handHistory[i].x - handHistory[i - 1].x)
            }

            if (reversals >= 3 && totalTravel >= 0.20f) {
                reversals = 0
                handHistory.clear()
                return true
            }

            return false
        }
    }

    init {
        setupGestureRecognizer()
    }

    fun reset() {
        stabilizer.reset()
    }

    private fun setupGestureRecognizer() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("gesture_recognizer.task")
                .build()

            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinHandDetectionConfidence(0.65f)
                .setMinHandPresenceConfidence(0.65f)
                .setMinTrackingConfidence(0.60f)
                .setNumHands(2)
                .setResultListener { result, _ ->
                    processResult(result)
                }
                .setErrorListener { error ->
                    Log.e(tag, "MediaPipe error: ${error.message}", error)
                    onError(error.message ?: "Gesture recognizer error")
                }
                .build()

            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
            Log.i(tag, "MediaPipe GestureRecognizer initialized successfully from assets")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize MediaPipe GestureRecognizer: ${e.message}", e)
            onError(e.message ?: "Failed to initialize gesture recognizer")
        }
    }

    /**
     * Converts a CameraX ImageProxy to MPImage and dispatches to MediaPipe LIVE_STREAM.
     */
    @OptIn(ExperimentalGetImage::class)
    fun processImageProxy(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val recognizer = gestureRecognizer ?: return

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()

            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                if (isFrontCamera) {
                    // Mirror horizontally for selfie camera
                    postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )

            val mpImage: MPImage = BitmapImageBuilder(rotatedBitmap).build()
            val frameTimestampMs = maxOf(SystemClock.uptimeMillis(), lastFrameTimestampMs + 1L)
            lastFrameTimestampMs = frameTimestampMs

            recognizer.recognizeAsync(mpImage, frameTimestampMs)
        } catch (e: Exception) {
            Log.e(tag, "Error converting image for MediaPipe: ${e.message}")
        }
    }

    private fun processResult(result: GestureRecognizerResult) {
        val now = System.currentTimeMillis()
        val gestures = result.gestures()
        val landmarksList = result.landmarks()

        val candidates = if (gestures.isEmpty() || landmarksList.isEmpty()) {
            emptyList()
        } else {
            gestures.mapIndexedNotNull { idx, handCategories ->
                val topCategory = handCategories.firstOrNull() ?: return@mapIndexedNotNull null
                val handLandmarks = landmarksList.getOrNull(idx)
                val wrist = handLandmarks?.getOrNull(0)
                val geometry = if (handLandmarks != null && handLandmarks.size >= 21) {
                    analyzeHandLandmarks(handLandmarks.map { Point2D(it.x(), it.y()) })
                } else {
                    HandGeometryAnalysis()
                }

                // Reject micro-detections and distant background clutter (< 4.0% of frame dimensions)
                if (geometry.palmScale > 0f && geometry.palmScale < 0.040f) {
                    return@mapIndexedNotNull null
                }

                HandCandidate(
                    categoryName = topCategory.categoryName() ?: "None",
                    score = topCategory.score(),
                    wristX = wrist?.x() ?: 0.5f,
                    wristY = wrist?.y() ?: 0.5f,
                    curledFingerCount = geometry.curledFingerCount,
                    extendedFingerCount = geometry.extendedFingerCount,
                    isGeometricFist = geometry.isGeometricFist,
                    isThumbExtended = geometry.isThumbExtended,
                    palmScale = geometry.palmScale
                )
            }
        }

        val outcome = stabilizer.processCandidates(candidates, now)
        if (outcome != null) {
            onGestureResult(outcome.first, outcome.second)
        }
    }

    fun close() {
        try {
            gestureRecognizer?.close()
            gestureRecognizer = null
        } catch (e: Exception) {
            Log.e(tag, "Error closing GestureRecognizer: ${e.message}")
        }
    }

    companion object {
        private val FINGER_JOINTS = arrayOf(
            intArrayOf(5, 6, 7, 8),   // Index: MCP, PIP, DIP, TIP
            intArrayOf(9, 10, 11, 12), // Middle: MCP, PIP, DIP, TIP
            intArrayOf(13, 14, 15, 16), // Ring: MCP, PIP, DIP, TIP
            intArrayOf(17, 18, 19, 20)  // Pinky: MCP, PIP, DIP, TIP
        )

        /**
         * Evaluates 21 normalized hand landmarks to determine finger curl and compact fist geometry.
         * Works across frontal, dorsal (back-of-hand), vertical, and low floor-camera angles.
         */
        fun analyzeHandLandmarks(landmarks: List<Point2D>): HandGeometryAnalysis {
            if (landmarks.size < 21) return HandGeometryAnalysis()

            val wrist = landmarks[0]
            val palmScale = maxOf(
                hypot(landmarks[9].x - wrist.x, landmarks[9].y - wrist.y),
                hypot(landmarks[5].x - wrist.x, landmarks[5].y - wrist.y),
                hypot(landmarks[17].x - landmarks[5].x, landmarks[17].y - landmarks[5].y)
            )
            if (palmScale < 0.040f) return HandGeometryAnalysis(palmScale = palmScale)

            var curledCount = 0
            var extendedCount = 0

            for (joints in FINGER_JOINTS) {
                val mcp = landmarks[joints[0]]
                val pip = landmarks[joints[1]]
                val dip = landmarks[joints[2]]
                val tip = landmarks[joints[3]]

                val distTipWrist = hypot(tip.x - wrist.x, tip.y - wrist.y)
                val distPipWrist = hypot(pip.x - wrist.x, pip.y - wrist.y)
                val distDipWrist = hypot(dip.x - wrist.x, dip.y - wrist.y)
                val distTipMcp = hypot(tip.x - mcp.x, tip.y - mcp.y)

                val isCurled = (distTipWrist <= distPipWrist * 1.15f) ||
                    (distTipWrist <= distDipWrist * 1.05f) ||
                    (distTipMcp <= palmScale * 0.75f)

                if (isCurled) {
                    curledCount++
                } else if (distTipWrist > distPipWrist * 1.18f && distTipMcp > palmScale * 0.80f) {
                    extendedCount++
                }
            }

            val thumbIp = landmarks[3]
            val thumbTip = landmarks[4]
            val distThumbTipWrist = hypot(thumbTip.x - wrist.x, thumbTip.y - wrist.y)
            val distThumbIpWrist = hypot(thumbIp.x - wrist.x, thumbIp.y - wrist.y)

            val minThumbDistToIndexMiddle = minOf(
                hypot(thumbTip.x - landmarks[5].x, thumbTip.y - landmarks[5].y),
                hypot(thumbTip.x - landmarks[6].x, thumbTip.y - landmarks[6].y),
                hypot(thumbTip.x - landmarks[9].x, thumbTip.y - landmarks[9].y),
                hypot(thumbTip.x - landmarks[10].x, thumbTip.y - landmarks[10].y)
            )

            val isThumbExtended = (distThumbTipWrist > distThumbIpWrist * 1.10f) &&
                (minThumbDistToIndexMiddle > palmScale * 0.72f)

            val isGeometricFist = (curledCount == 4) && !isThumbExtended

            return HandGeometryAnalysis(
                curledFingerCount = curledCount,
                extendedFingerCount = extendedCount,
                isThumbExtended = isThumbExtended,
                isGeometricFist = isGeometricFist,
                palmScale = palmScale
            )
        }
    }
}


