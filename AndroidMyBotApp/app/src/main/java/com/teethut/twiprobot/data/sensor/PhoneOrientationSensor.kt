package com.teethut.twiprobot.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sign

data class PhoneTiltData(
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val rawPitchDeg: Float = 0f,
    val rawRollDeg: Float = 0f,
    val referencePitchDeg: Float = 0f,
    val referenceRollDeg: Float = 0f,
    val normThrottle: Float = 0f,
    val normSteer: Float = 0f,
    val hasReference: Boolean = false
)

class PhoneOrientationSensor(context: Context? = null) : SensorEventListener {

    private val sensorManager = try {
        context?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    } catch (e: Exception) {
        null
    }
    private val rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _tiltData = MutableStateFlow(PhoneTiltData())
    val tiltData: StateFlow<PhoneTiltData> = _tiltData.asStateFlow()

    private var isListening = false
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Configuration
    private val maxPitchAngle = 35f // Tilting phone forward/back up to 35 degrees = 100% throttle
    private val maxRollAngle = 35f  // Tilting phone left/right up to 35 degrees = 100% steer
    private val deadzoneAngle = 4f  // Ignore slight hand jitters under 4 degrees

    // Zero-angle reference calibration
    private var referencePitchDeg: Float = 0f
    private var referenceRollDeg: Float = 0f
    private var hasReference: Boolean = false
    private var latestRawPitchDeg: Float = 0f
    private var latestRawRollDeg: Float = 0f

    fun startListening() {
        if (isListening || sensorManager == null) return

        val targetSensor = rotationVectorSensor ?: accelerometer ?: return
        hasReference = false
        sensorManager.registerListener(this, targetSensor, SensorManager.SENSOR_DELAY_GAME)
        isListening = true
    }

    fun stopListening() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
        hasReference = false
        _tiltData.value = PhoneTiltData()
    }

    /**
     * Recenters current phone angle as reference zero.
     * When pressed, this orientation becomes the neutral 0° baseline.
     */
    fun recenter() {
        referencePitchDeg = latestRawPitchDeg
        referenceRollDeg = latestRawRollDeg
        hasReference = true
        processAngles(latestRawPitchDeg, latestRawRollDeg)
    }

    fun resetReference() {
        referencePitchDeg = 0f
        referenceRollDeg = 0f
        hasReference = false
        processAngles(latestRawPitchDeg, latestRawRollDeg)
    }

    /**
     * Updates orientation from raw sensor angles and computes relative tilt
     * against calibrated zero reference.
     */
    fun updateOrientationDegrees(rawPitch: Float, rawRoll: Float) {
        latestRawPitchDeg = rawPitch
        latestRawRollDeg = rawRoll
        if (!hasReference) {
            referencePitchDeg = rawPitch
            referenceRollDeg = rawRoll
            hasReference = true
        }
        processAngles(rawPitch, rawRoll)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Convert radians to degrees:
            // orientationAngles[1] = pitch (forward/back tilt)
            // orientationAngles[2] = roll (left/right tilt)
            val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

            updateOrientationDegrees(pitchDeg, rollDeg)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            // Approximate pitch and roll from gravity vector
            val pitchRad = kotlin.math.atan2(-ay.toDouble(), kotlin.math.sqrt((ax * ax + az * az).toDouble())).toFloat()
            val rollRad = kotlin.math.atan2(ax.toDouble(), az.toDouble()).toFloat()

            val pitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat()
            val rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat()

            updateOrientationDegrees(pitchDeg, rollDeg)
        }
    }

    private fun normalizeAngleDiff(angle: Float): Float {
        var diff = angle % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }

    private fun processAngles(rawPitch: Float, rawRoll: Float) {
        val deltaPitch = normalizeAngleDiff(rawPitch - referencePitchDeg)
        val deltaRoll = normalizeAngleDiff(rawRoll - referenceRollDeg)

        // Smooth deadzone calculation without sudden step
        val filteredPitch = if (abs(deltaPitch) < deadzoneAngle) {
            0f
        } else {
            sign(deltaPitch) * ((abs(deltaPitch) - deadzoneAngle) / (maxPitchAngle - deadzoneAngle) * maxPitchAngle)
        }

        val filteredRoll = if (abs(deltaRoll) < deadzoneAngle) {
            0f
        } else {
            sign(deltaRoll) * ((abs(deltaRoll) - deadzoneAngle) / (maxRollAngle - deadzoneAngle) * maxRollAngle)
        }

        // Tilting phone top away from user is negative pitch delta in Android standard -> forward throttle
        val normThrottle = (-filteredPitch / maxPitchAngle).coerceIn(-1f, 1f)
        // Tilting right is positive roll delta -> right steering
        val normSteer = (filteredRoll / maxRollAngle).coerceIn(-1f, 1f)

        _tiltData.value = PhoneTiltData(
            pitchDeg = deltaPitch,
            rollDeg = deltaRoll,
            rawPitchDeg = rawPitch,
            rawRollDeg = rawRoll,
            referencePitchDeg = referencePitchDeg,
            referenceRollDeg = referenceRollDeg,
            normThrottle = normThrottle,
            normSteer = normSteer,
            hasReference = hasReference
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

