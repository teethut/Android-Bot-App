package com.teethut.twiprobot.data.vision

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.hypot

data class GestureDetectionResult(
    val isWaveTriggered: Boolean = false,
    val isStopGestureActive: Boolean = false
)

/**
 * Detects deliberate hand gestures from skeletal pose landmarks:
 * 1. Hand Waving: Elevated hand with horizontal side-to-side stroke reversals.
 * 2. Stop Gesture: Elevated hand with open, upright palm held steady towards the camera.
 *
 * Specifically tuned for self-balancing robots to cancel out chassis balancing wobble
 * using body-relative coordinates.
 */
class HandWaveDetector(
    private val cooldownMs: Long = 2500L,
    private val windowDurationMs: Long = 1300L,
    private val minReversals: Int = 3,
    private val strokeThresholdFraction: Float = 0.060f, // ~6.0% of frame width
    private val minTotalTravelFraction: Float = 0.20f     // ~20% of frame width
) {

    data class WristSample(val relX: Float, val y: Float, val timestampMs: Long)

    class WristTracker {
        private val samples = ArrayDeque<WristSample>()
        private var lastDirection = 0 // +1 right, -1 left
        private var lastExtremeRelX: Float? = null
        private var lastExtremeTimeMs: Long = 0L
        var reversals = 0
            private set
        private var lastReversalTimestamp = 0L

        fun addSample(
            relX: Float,
            y: Float,
            now: Long,
            windowMs: Long,
            minStrokeWidth: Float,
            minTotalTravel: Float,
            requiredReversals: Int
        ): Boolean {
            while (samples.isNotEmpty() && (now - samples.first().timestampMs > windowMs)) {
                samples.removeFirst()
            }

            samples.addLast(WristSample(relX, y, now))

            if (samples.size < 4) return false

            if (now - lastReversalTimestamp > 600L) {
                reversals = 0
            }

            val prev = samples[samples.size - 2]
            val dx = relX - prev.relX

            if (abs(dx) > 3.0f) {
                val currentDirection = if (dx > 0) 1 else -1

                if (lastDirection != 0 && currentDirection != lastDirection) {
                    val strokeDistance = lastExtremeRelX?.let { abs(relX - it) } ?: 0f
                    val strokeDuration = if (lastExtremeTimeMs > 0L) now - lastExtremeTimeMs else 250L

                    if (strokeDistance >= minStrokeWidth && strokeDuration in 80L..550L) {
                        reversals++
                        lastReversalTimestamp = now
                        lastExtremeRelX = relX
                        lastExtremeTimeMs = now
                        lastDirection = currentDirection
                    }
                } else if (lastDirection == 0) {
                    lastDirection = currentDirection
                    lastExtremeRelX = relX
                    lastExtremeTimeMs = now
                } else {
                    if (currentDirection == 1 && (lastExtremeRelX == null || relX > lastExtremeRelX!!)) {
                        lastExtremeRelX = relX
                        lastExtremeTimeMs = now
                    } else if (currentDirection == -1 && (lastExtremeRelX == null || relX < lastExtremeRelX!!)) {
                        lastExtremeRelX = relX
                        lastExtremeTimeMs = now
                    }
                }
            }

            var totalTravel = 0f
            for (i in 1 until samples.size) {
                totalTravel += abs(samples[i].relX - samples[i - 1].relX)
            }

            if (reversals >= requiredReversals && totalTravel >= minTotalTravel) {
                reset()
                return true
            }

            return false
        }

        fun reset() {
            samples.clear()
            lastDirection = 0
            lastExtremeRelX = null
            lastExtremeTimeMs = 0L
            reversals = 0
            lastReversalTimestamp = 0L
        }
    }

    class StopGestureTracker(
        private val minHoldDurationMs: Long = 300L,
        private val maxMovementPx: Float = 55f
    ) {
        private var holdStartTimeMs: Long = 0L
        private var lastSeenTimeMs: Long = 0L
        private var anchorX: Float = 0f
        private var anchorY: Float = 0f
        var isActive: Boolean = false
            private set

        fun update(isStopPosture: Boolean, x: Float, y: Float, now: Long): Boolean {
            if (isStopPosture) {
                lastSeenTimeMs = now
                if (holdStartTimeMs == 0L) {
                    holdStartTimeMs = now
                    anchorX = x
                    anchorY = y
                } else {
                    val dist = hypot(x - anchorX, y - anchorY)
                    if (dist > maxMovementPx) {
                        // Hand moved excessively, reset anchor
                        anchorX = x
                        anchorY = y
                        holdStartTimeMs = now
                    } else if (now - holdStartTimeMs >= minHoldDurationMs) {
                        isActive = true
                    }
                }
            } else {
                // Hysteresis: keep active across brief frame drops (up to 200ms)
                if (now - lastSeenTimeMs > 200L) {
                    holdStartTimeMs = 0L
                    isActive = false
                }
            }
            return isActive
        }

        fun reset() {
            holdStartTimeMs = 0L
            lastSeenTimeMs = 0L
            isActive = false
        }
    }

    private val leftWristTracker = WristTracker()
    private val rightWristTracker = WristTracker()
    private val leftStopTracker = StopGestureTracker()
    private val rightStopTracker = StopGestureTracker()

    private var lastWaveTriggerTimeMs = 0L

    /**
     * Processes skeletal landmarks from a frame to evaluate wave and stop gestures.
     */
    fun processPose(
        pose: Pose,
        frameWidth: Int,
        frameHeight: Int,
        isFrontCamera: Boolean = true,
        currentTimeMs: Long = System.currentTimeMillis()
    ): GestureDetectionResult {
        val minStrokeWidth = frameWidth * strokeThresholdFraction
        val minTotalTravel = frameWidth * minTotalTravelFraction

        // --- Left Arm Evaluation ---
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
        val leftPinky = pose.getPoseLandmark(PoseLandmark.LEFT_PINKY)

        val isLeftArmElevated = isHandElevated(leftWrist, leftElbow, leftShoulder, frameHeight)
        var leftWaved = false

        if (isLeftArmElevated && leftWrist != null && leftShoulder != null) {
            val relX = leftWrist.position.x - leftShoulder.position.x
            if (currentTimeMs - lastWaveTriggerTimeMs >= cooldownMs) {
                leftWaved = leftWristTracker.addSample(
                    relX = if (isFrontCamera) -relX else relX,
                    y = leftWrist.position.y,
                    now = currentTimeMs,
                    windowMs = windowDurationMs,
                    minStrokeWidth = minStrokeWidth,
                    minTotalTravel = minTotalTravel,
                    requiredReversals = minReversals
                )
            }
        } else {
            leftWristTracker.reset()
        }

        val isLeftStopPosture = isLeftArmElevated &&
                leftWristTracker.reversals == 0 &&
                isStopHandPosture(leftWrist, leftIndex, leftPinky, frameHeight)

        val leftStopActive = leftStopTracker.update(
            isStopPosture = isLeftStopPosture,
            x = leftWrist?.position?.x ?: 0f,
            y = leftWrist?.position?.y ?: 0f,
            now = currentTimeMs
        )

        // --- Right Arm Evaluation ---
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)
        val rightPinky = pose.getPoseLandmark(PoseLandmark.RIGHT_PINKY)

        val isRightArmElevated = isHandElevated(rightWrist, rightElbow, rightShoulder, frameHeight)
        var rightWaved = false

        if (isRightArmElevated && rightWrist != null && rightShoulder != null) {
            val relX = rightWrist.position.x - rightShoulder.position.x
            if (currentTimeMs - lastWaveTriggerTimeMs >= cooldownMs) {
                rightWaved = rightWristTracker.addSample(
                    relX = if (isFrontCamera) -relX else relX,
                    y = rightWrist.position.y,
                    now = currentTimeMs,
                    windowMs = windowDurationMs,
                    minStrokeWidth = minStrokeWidth,
                    minTotalTravel = minTotalTravel,
                    requiredReversals = minReversals
                )
            }
        } else {
            rightWristTracker.reset()
        }

        val isRightStopPosture = isRightArmElevated &&
                rightWristTracker.reversals == 0 &&
                isStopHandPosture(rightWrist, rightIndex, rightPinky, frameHeight)

        val rightStopActive = rightStopTracker.update(
            isStopPosture = isRightStopPosture,
            x = rightWrist?.position?.x ?: 0f,
            y = rightWrist?.position?.y ?: 0f,
            now = currentTimeMs
        )

        val waveTriggered = (leftWaved || rightWaved) && !leftStopActive && !rightStopActive
        if (waveTriggered) {
            lastWaveTriggerTimeMs = currentTimeMs
            leftWristTracker.reset()
            rightWristTracker.reset()
        }

        val stopActive = leftStopActive || rightStopActive

        return GestureDetectionResult(
            isWaveTriggered = waveTriggered,
            isStopGestureActive = stopActive
        )
    }

    /**
     * Checks if hand elevation satisfies the deliberate gesture posture.
     */
    private fun isHandElevated(
        wrist: PoseLandmark?,
        elbow: PoseLandmark?,
        shoulder: PoseLandmark?,
        frameHeight: Int
    ): Boolean {
        if (wrist == null || wrist.inFrameLikelihood < 0.65f) return false
        if (elbow == null || elbow.inFrameLikelihood < 0.55f) return false
        if (shoulder == null || shoulder.inFrameLikelihood < 0.55f) return false

        val wy = wrist.position.y
        val ey = elbow.position.y
        val sy = shoulder.position.y

        val isAboveElbow = wy < (ey - frameHeight * 0.08f)
        val isNearOrAboveShoulder = wy < (sy + frameHeight * 0.04f)

        return isAboveElbow && isNearOrAboveShoulder
    }

    /**
     * Checks if fingers are upright relative to wrist (characteristic of open stop palm).
     */
    private fun isStopHandPosture(
        wrist: PoseLandmark?,
        indexFinger: PoseLandmark?,
        pinkyFinger: PoseLandmark?,
        frameHeight: Int
    ): Boolean {
        if (wrist == null) return false

        // If finger landmarks are confident, verify fingers extend upward above wrist
        val hasIndex = indexFinger != null && indexFinger.inFrameLikelihood >= 0.45f
        val hasPinky = pinkyFinger != null && pinkyFinger.inFrameLikelihood >= 0.45f

        if (hasIndex && hasPinky) {
            val fingersAboveWrist = indexFinger.position.y < (wrist.position.y - frameHeight * 0.02f) ||
                    pinkyFinger.position.y < (wrist.position.y - frameHeight * 0.02f)
            return fingersAboveWrist
        } else if (hasIndex) {
            return indexFinger.position.y < (wrist.position.y - frameHeight * 0.02f)
        }

        // If fingers obscured, hand elevated near chest/shoulder held upright qualifies
        return true
    }

    fun reset() {
        leftWristTracker.reset()
        rightWristTracker.reset()
        leftStopTracker.reset()
        rightStopTracker.reset()
    }
}
