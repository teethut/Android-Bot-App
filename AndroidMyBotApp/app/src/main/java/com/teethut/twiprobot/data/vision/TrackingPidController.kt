package com.teethut.twiprobot.data.vision

import com.teethut.twiprobot.domain.model.DetectedTarget
import com.teethut.twiprobot.domain.model.DistanceCategory
import com.teethut.twiprobot.domain.model.TargetLockStatus
import com.teethut.twiprobot.domain.model.TrackerSettings
import com.teethut.twiprobot.domain.model.TrackingMode
import kotlin.math.abs
import kotlin.math.sign

data class TrackingOutput(
    val speed: Float,
    val steer: Float,
    val lockStatus: TargetLockStatus,
    val distanceCategory: DistanceCategory
)

class TrackingPidController(
    private val lostTargetTimeoutMs: Long = 500L
) {
    private var lastTargetSeenMs: Long = 0L
    private var lastTarget: DetectedTarget? = null
    private var lastSteerError: Float = 0f
    private var lastDistError: Float = 0f
    private var lastComputeTimeMs: Long = 0L

    fun reset() {
        lastTargetSeenMs = 0L
        lastTarget = null
        clearErrors()
    }

    /**
     * Clears derivative error history while preserving target lock status.
     */
    fun clearErrors() {
        lastSteerError = 0f
        lastDistError = 0f
        lastComputeTimeMs = 0L
    }

    fun computeDriveCommand(
        target: DetectedTarget?,
        settings: TrackerSettings,
        trackingMode: TrackingMode,
        currentTimeMs: Long = System.currentTimeMillis()
    ): TrackingOutput {
        if (target != null) {
            lastTargetSeenMs = currentTimeMs
            lastTarget = target
        }

        val timeSinceLastSeen = currentTimeMs - lastTargetSeenMs
        val isTargetActive = target != null
        val isWithinGracePeriod = lastTarget != null && timeSinceLastSeen <= lostTargetTimeoutMs

        if (!isTargetActive && !isWithinGracePeriod) {
            // Target is completely lost
            lastSteerError = 0f
            lastDistError = 0f
            lastComputeTimeMs = currentTimeMs
            return TrackingOutput(
                speed = 0f,
                steer = 0f,
                lockStatus = if (lastTargetSeenMs == 0L) TargetLockStatus.SEARCHING else TargetLockStatus.LOST,
                distanceCategory = DistanceCategory.UNKNOWN
            )
        }

        val activeTarget = target ?: lastTarget!!
        val lockStatus = if (isTargetActive) TargetLockStatus.LOCKED else TargetLockStatus.LOST

        val isFirstSample = lastComputeTimeMs == 0L
        val dt = if (!isFirstSample) {
            ((currentTimeMs - lastComputeTimeMs) / 1000f).coerceIn(0.02f, 0.5f)
        } else {
            0.05f
        }
        lastComputeTimeMs = currentTimeMs

        // 1. Steering calculation (Horizontal Center Error) with D-term damping
        val rawEx = activeTarget.normalizedX.coerceIn(-1f, 1f)
        val steerSignMultiplier = if (settings.invertSteer) -1f else 1f

        val calculatedSteer: Float = if (abs(rawEx) <= settings.deadbandX) {
            lastSteerError = 0f
            0f
        } else {
            val sgn = sign(rawEx)
            val normalizedMag = (abs(rawEx) - settings.deadbandX) / (1f - settings.deadbandX)
            
            // Derivative rate of error change for damping overshoot (only on subsequent samples)
            val dTerm = if (!isFirstSample) {
                val dError = (rawEx - lastSteerError) / dt
                (dError * 0.08f * settings.maxSteer).coerceIn(-settings.maxSteer * 0.4f, settings.maxSteer * 0.4f)
            } else {
                0f
            }
            lastSteerError = rawEx

            val pTerm = sgn * normalizedMag.coerceIn(0f, 1f) * settings.maxSteer * settings.kpSteer
            val rawSteer = (pTerm + dTerm).coerceIn(-settings.maxSteer, settings.maxSteer)

            (rawSteer * steerSignMultiplier).coerceIn(-settings.maxSteer, settings.maxSteer)
        }

        // 2. Throttle / Speed calculation (Distance / Height Error)
        val h = activeTarget.normalizedHeight.coerceIn(0.01f, 1.0f)
        val targetH = settings.targetDistanceFraction.coerceIn(0.05f, 0.6f)
        val distError = targetH - h // > 0 means person is farther than target -> drive forward

        val distanceCategory = when {
            distError > settings.deadbandDist -> DistanceCategory.TOO_FAR
            distError < -settings.deadbandDist -> DistanceCategory.TOO_CLOSE
            else -> DistanceCategory.OPTIMAL
        }

        val baseSpeed: Float = when {
            trackingMode == TrackingMode.PAN_ONLY -> 0f
            abs(distError) <= settings.deadbandDist -> {
                lastDistError = 0f
                0f
            }
            distError > 0 -> {
                // Target is too far: advance forward with D damping
                val dSpeed = if (!isFirstSample) {
                    val dDist = (distError - lastDistError) / dt
                    (dDist * 0.05f * settings.maxSpeed).coerceIn(-settings.maxSpeed * 0.3f, settings.maxSpeed * 0.3f)
                } else {
                    0f
                }
                lastDistError = distError

                val mag = (distError - settings.deadbandDist) / targetH
                val pSpeed = mag.coerceIn(0f, 1f) * settings.maxSpeed * settings.kpSpeed

                // Steer Coordination: If robot is sharply turning, reduce forward speed to avoid tipping TWIP pendulum
                val steerReductionFactor = (1.0f - (0.5f * (abs(rawEx) / 1.0f))).coerceIn(0.5f, 1.0f)
                ((pSpeed + dSpeed) * steerReductionFactor).coerceIn(0f, settings.maxSpeed)
            }
            else -> {
                // Target is too close
                lastDistError = distError
                if (settings.allowReverse) {
                    val mag = (-distError - settings.deadbandDist) / targetH
                    // Reverse speed limit is capped at 60% of max forward speed for safety
                    val reverseCap = settings.maxSpeed * 0.6f
                    (-mag.coerceIn(0f, 1f) * reverseCap * settings.kpSpeed).coerceIn(-reverseCap, 0f)
                } else {
                    0f // Reverse inhibited for safety
                }
            }
        }

        val throttleSignMultiplier = if (settings.invertThrottle) -1f else 1f
        val calculatedSpeed = baseSpeed * throttleSignMultiplier

        return TrackingOutput(
            speed = calculatedSpeed,
            steer = calculatedSteer,
            lockStatus = lockStatus,
            distanceCategory = distanceCategory
        )
    }
}

data class HoldCommand(
    val speed: Float = 0f,
    val steer: Float = 0f,
    val isLockedOnAnchor: Boolean = false,
    val posErrorSteps: Float = 0f,
    val yawErrorSteps: Float = 0f
)

/**
 * Closed-loop wheel encoder position & heading hold controller for CLOSED_FIST halt mode.
 *
 * Compensates for initial robot momentum and steady-state center-of-gravity pitch offsets
 * (where open-loop V:0, S:0 would otherwise drift continuously because firmware spdKi = 0).
 */
class FistPositionHoldController(
    private val kpPos: Float = 1.25f,
    private val kiPos: Float = 0.35f,
    private val kdVel: Float = 0.35f,
    private val kdBrake: Float = 0.45f,
    private val kpYaw: Float = 1.6f,
    private val posDeadbandSteps: Float = 6f,
    private val yawDeadbandSteps: Float = 4f,
    private val velDeadbandStepsPerSec: Float = 25f,
    private val lockSpeedThreshold: Float = 150f,
    private val maxBrakingDurationMs: Long = 550L,
    private val maxPosErrorSteps: Float = 750f,
    private val maxIntegralSpeed: Float = 550f,
    private val maxHoldSpeed: Float = 850f,
    private val maxHoldSteer: Float = 450f
) {
    var isEngaged: Boolean = false
        private set
    var hasAnchor: Boolean = false
        private set
    var isBrakingPhase: Boolean = false
        private set
    var anchorPosSteps: Float = 0f
        private set
    var anchorYawSteps: Float = 0f
        private set

    private var initialSpeedSign: Float = 0f
    private var engageTimeMs: Long = 0L
    private var lastUpdateMs: Long = 0L
    private var posIntegral: Float = 0f

    fun isActive(): Boolean = isEngaged

    fun release() {
        reset()
    }

    fun reset() {
        isEngaged = false
        hasAnchor = false
        isBrakingPhase = false
        anchorPosSteps = 0f
        anchorYawSteps = 0f
        initialSpeedSign = 0f
        engageTimeMs = 0L
        lastUpdateMs = 0L
        posIntegral = 0f
    }

    private fun isTelemetryActive(telemetry: com.teethut.twiprobot.domain.model.RobotTelemetry): Boolean {
        return telemetry.isBalancing && (
            telemetry.timestampMs > 0L ||
                telemetry.encLeft != 0L ||
                telemetry.encRight != 0L ||
                abs(telemetry.speed) > 0.01f
            )
    }

    fun engage(
        telemetry: com.teethut.twiprobot.domain.model.RobotTelemetry,
        nowMs: Long = System.currentTimeMillis()
    ) {
        isEngaged = true
        posIntegral = 0f
        engageTimeMs = nowMs
        lastUpdateMs = nowMs

        if (isTelemetryActive(telemetry)) {
            hasAnchor = true
            anchorPosSteps = (telemetry.encLeft + telemetry.encRight) * 0.5f
            anchorYawSteps = (telemetry.encLeft - telemetry.encRight) * 0.5f
            isBrakingPhase = abs(telemetry.speed) > lockSpeedThreshold
            initialSpeedSign = sign(telemetry.speed)
        } else {
            hasAnchor = false
            isBrakingPhase = false
            anchorPosSteps = 0f
            anchorYawSteps = 0f
            initialSpeedSign = 0f
        }
    }

    fun computeHoldCommand(
        telemetry: com.teethut.twiprobot.domain.model.RobotTelemetry,
        settings: TrackerSettings = TrackerSettings(),
        nowMs: Long = System.currentTimeMillis()
    ): HoldCommand {
        if (!isEngaged || !isTelemetryActive(telemetry)) {
            return HoldCommand(0f, 0f, isLockedOnAnchor = false)
        }

        val throttleSign = if (settings.invertThrottle) -1f else 1f
        val steerSign = if (settings.invertSteer) -1f else 1f

        val currentPos = (telemetry.encLeft + telemetry.encRight) * 0.5f
        val currentYaw = (telemetry.encLeft - telemetry.encRight) * 0.5f

        if (!hasAnchor) {
            hasAnchor = true
            anchorPosSteps = currentPos
            anchorYawSteps = currentYaw
            engageTimeMs = nowMs
            lastUpdateMs = nowMs
            isBrakingPhase = abs(telemetry.speed) > lockSpeedThreshold
            initialSpeedSign = sign(telemetry.speed)
        }

        if (isBrakingPhase) {
            val crossedZero = initialSpeedSign != 0f &&
                sign(telemetry.speed) != 0f &&
                sign(telemetry.speed) != initialSpeedSign
            val settledSpeed = abs(telemetry.speed) <= lockSpeedThreshold
            val timedOut = (nowMs - engageTimeMs) >= maxBrakingDurationMs

            if (settledSpeed || crossedZero || timedOut) {
                isBrakingPhase = false
                anchorPosSteps = currentPos
                posIntegral = 0f
                lastUpdateMs = nowMs
            } else {
                anchorPosSteps = currentPos
                lastUpdateMs = nowMs
                val brakeSpeed = (-kdBrake * telemetry.speed * throttleSign).coerceIn(-maxHoldSpeed, maxHoldSpeed)
                val rawYawErr = (anchorYawSteps - currentYaw).coerceIn(-maxPosErrorSteps, maxPosErrorSteps)
                val effectiveYawErr = if (abs(rawYawErr) <= yawDeadbandSteps) {
                    0f
                } else {
                    rawYawErr - (sign(rawYawErr) * yawDeadbandSteps)
                }
                val steerCmd = (kpYaw * effectiveYawErr * steerSign).coerceIn(-maxHoldSteer, maxHoldSteer)
                return HoldCommand(
                    speed = brakeSpeed,
                    steer = steerCmd,
                    isLockedOnAnchor = false,
                    posErrorSteps = 0f,
                    yawErrorSteps = rawYawErr
                )
            }
        }

        val dt = if (lastUpdateMs > 0L && nowMs > lastUpdateMs) {
            ((nowMs - lastUpdateMs) / 1000f).coerceIn(0.01f, 0.25f)
        } else {
            0.05f
        }
        lastUpdateMs = nowMs

        val rawPosError = (anchorPosSteps - currentPos).coerceIn(-maxPosErrorSteps, maxPosErrorSteps)
        val rawYawError = (anchorYawSteps - currentYaw).coerceIn(-maxPosErrorSteps, maxPosErrorSteps)

        val effectivePosError = if (abs(rawPosError) <= posDeadbandSteps) {
            0f
        } else {
            rawPosError - (sign(rawPosError) * posDeadbandSteps)
        }

        val effectiveVel = if (abs(telemetry.speed) <= velDeadbandStepsPerSec && effectivePosError == 0f) {
            0f
        } else {
            telemetry.speed
        }

        if (effectivePosError != 0f) {
            posIntegral = (posIntegral + effectivePosError * kiPos * dt)
                .coerceIn(-maxIntegralSpeed, maxIntegralSpeed)
        } else if (abs(telemetry.speed) <= velDeadbandStepsPerSec) {
            posIntegral *= 0.95f
        }

        val pTerm = kpPos * effectivePosError
        val dTerm = -kdVel * effectiveVel
        val rawSpeedCmd = (pTerm + posIntegral + dTerm) * throttleSign

        val speedCmd = if (effectivePosError == 0f && effectiveVel == 0f && abs(posIntegral) < 5f) {
            0f
        } else {
            rawSpeedCmd.coerceIn(-maxHoldSpeed, maxHoldSpeed)
        }

        val effectiveYawError = if (abs(rawYawError) <= yawDeadbandSteps) {
            0f
        } else {
            rawYawError - (sign(rawYawError) * yawDeadbandSteps)
        }
        val steerCmd = if (effectiveYawError == 0f) {
            0f
        } else {
            (kpYaw * effectiveYawError * steerSign).coerceIn(-maxHoldSteer, maxHoldSteer)
        }

        return HoldCommand(
            speed = speedCmd,
            steer = steerCmd,
            isLockedOnAnchor = true,
            posErrorSteps = rawPosError,
            yawErrorSteps = rawYawError
        )
    }
}


