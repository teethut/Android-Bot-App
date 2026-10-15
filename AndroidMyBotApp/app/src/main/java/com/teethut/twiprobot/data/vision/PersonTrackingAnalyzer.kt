package com.teethut.twiprobot.data.vision

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import android.content.Context
import com.teethut.twiprobot.domain.model.DetectedSceneObject
import com.teethut.twiprobot.domain.model.DetectedTarget
import com.teethut.twiprobot.domain.model.RobotHandGesture

class PersonTrackingAnalyzer(
    context: Context? = null,
    private val onTargetDetected: (target: DetectedTarget?, frameWidth: Int, frameHeight: Int, fps: Int) -> Unit,
    var onWaveDetected: (() -> Unit)? = null,
    var onStopGestureChanged: ((Boolean) -> Unit)? = null,
    var onGestureChanged: ((RobotHandGesture, Float) -> Unit)? = null,
    var onSceneObjectsDetected: ((List<DetectedSceneObject>) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
    )

    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    private val mediaPipeHelper: MediaPipeGestureRecognizerHelper? = if (context != null) {
        MediaPipeGestureRecognizerHelper(
            context = context,
            onGestureResult = { gesture, confidence ->
                handleGestureResult(gesture, confidence)
            }
        )
    } else {
        null
    }

    @Volatile
    var isGestureDetectionEnabled: Boolean = true
    @Volatile
    var isSceneCommentaryEnabled: Boolean = true
    @Volatile
    private var lastStopGestureState: Boolean = false
    @Volatile
    private var currentActiveGesture: RobotHandGesture = RobotHandGesture.NONE

    private var primaryTrackingId: Int? = null
    private var isFrontFacing = true
    private var targetFps: Int = 15 // Default battery saver rate

    // Scene commentary rate limiter
    private var lastSceneAnalysisTimestampMs: Long = 0L

    // FPS calculation & rate limiting
    private var lastAnalyzedTimestampMs = 0L
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = 0

    // Multi-factor target persistence & temporal smoothing
    private var lastTargetNormX: Float? = null
    private var lastTargetNormY: Float? = null
    private var lastTargetNormW: Float? = null
    private var lastTargetNormH: Float? = null
    private var preFistTargetNormH: Float? = null
    private var lastTargetSeenMs: Long = 0L
    private val targetLostGracePeriodMs = 450L // Retain track across brief head turns or blinks

    fun setCameraFacing(isFront: Boolean) {
        this.isFrontFacing = isFront
        resetTracking()
    }

    fun setTargetFps(fps: Int) {
        this.targetFps = fps.coerceIn(5, 30)
    }

    fun resetTracking() {
        primaryTrackingId = null
        lastTargetNormX = null
        lastTargetNormY = null
        lastTargetNormW = null
        lastTargetNormH = null
        preFistTargetNormH = null
        lastTargetSeenMs = 0L
        mediaPipeHelper?.reset()
        if (lastStopGestureState) {
            lastStopGestureState = false
            onStopGestureChanged?.invoke(false)
        }
        if (currentActiveGesture != RobotHandGesture.NONE) {
            currentActiveGesture = RobotHandGesture.NONE
            onGestureChanged?.invoke(RobotHandGesture.NONE, 0f)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        val minFrameIntervalMs = 1000L / targetFps

        // Battery saver: Drop frame early if within throttle interval to avoid ML Kit inference
        if (now - lastAnalyzedTimestampMs < minFrameIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestampMs = now

        // Measure actual processed FPS
        frameCount++
        if (now - lastFpsTimestamp >= 1000L) {
            currentFps = frameCount
            frameCount = 0
            lastFpsTimestamp = now
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val isTransposed = rotationDegrees == 90 || rotationDegrees == 270
        val uprightWidth = if (isTransposed) imageProxy.height else imageProxy.width
        val uprightHeight = if (isTransposed) imageProxy.width else imageProxy.height

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        if (isGestureDetectionEnabled && mediaPipeHelper != null) {
            try {
                mediaPipeHelper.processImageProxy(imageProxy, isFrontFacing)
            } catch (e: Exception) {
                android.util.Log.e("PersonTrackingAnalyzer", "MediaPipe processing error: ${e.message}", e)
            }
        }

        val shouldRunSceneLabeling = isSceneCommentaryEnabled && (now - lastSceneAnalysisTimestampMs >= 1800L)
        if (shouldRunSceneLabeling) {
            lastSceneAnalysisTimestampMs = now
            val faceTask = detector.process(inputImage)
            val labelTask = imageLabeler.process(inputImage)

            faceTask.addOnSuccessListener { faces ->
                handleFaces(faces, uprightWidth, uprightHeight, now)
            }.addOnFailureListener { e ->
                android.util.Log.e("PersonTrackingAnalyzer", "ML Kit Face Detection failure: ${e.message}", e)
                handleFaces(emptyList(), uprightWidth, uprightHeight, now)
            }

            labelTask.addOnSuccessListener { labels ->
                val interesting = labels
                    .filter { it.confidence >= 0.65f && isInterestingSceneLabel(it.text) }
                    .map { DetectedSceneObject(label = it.text, confidence = it.confidence, timestampMs = now) }
                if (interesting.isNotEmpty()) {
                    onSceneObjectsDetected?.invoke(interesting)
                }
            }.addOnFailureListener { e ->
                android.util.Log.e("PersonTrackingAnalyzer", "ML Kit Scene Labeling failure: ${e.message}", e)
            }

            Tasks.whenAllComplete(faceTask, labelTask).addOnCompleteListener {
                imageProxy.close()
            }
        } else {
            // Run ML Kit Face Tracking for user person following
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    handleFaces(faces, uprightWidth, uprightHeight, now)
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("PersonTrackingAnalyzer", "ML Kit Face Detection failure: ${e.message}", e)
                    handleFaces(emptyList(), uprightWidth, uprightHeight, now)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private val ignoredLabels = setOf(
        "mammal", "vertebrate", "carnivore", "organism", "skin", "flooring", "room", "jaw",
        "smile", "forehead", "cheek", "eyebrow", "sleeve", "comfort", "flash photography",
        "gesture", "selfie", "beard", "chin", "mouth", "neck", "facial hair", "font", "poster",
        "snapshot", "photography", "arm", "hand", "finger", "thumb", "eyewear", "hair"
    )

    private fun isInterestingSceneLabel(label: String): Boolean {
        val lower = label.trim().lowercase()
        return !ignoredLabels.contains(lower)
    }

    internal fun handleGestureResult(gesture: RobotHandGesture, confidence: Float) {
        if (!isGestureDetectionEnabled) return

        val isStop = gesture == RobotHandGesture.OPEN_PALM_STOP
        if (isStop != lastStopGestureState) {
            lastStopGestureState = isStop
            onStopGestureChanged?.invoke(isStop)
        }

        if (gesture != currentActiveGesture) {
            currentActiveGesture = gesture
            onGestureChanged?.invoke(gesture, confidence)
        }

        // Backward compatibility triggers for wave
        if (gesture == RobotHandGesture.WAVE) {
            onWaveDetected?.invoke()
        }
    }

    fun close() {
        mediaPipeHelper?.close()
        try {
            imageLabeler.close()
            detector.close()
        } catch (_: Exception) {}
    }

    internal fun handleFaces(faces: List<Face>, uprightWidth: Int, uprightHeight: Int, now: Long) {
        val selectedFace = selectTargetFace(faces, uprightWidth, uprightHeight)
        val isFistHeld = currentActiveGesture == RobotHandGesture.CLOSED_FIST

        if (selectedFace != null) {
            primaryTrackingId = selectedFace.trackingId
            lastTargetSeenMs = now

            val bbox = selectedFace.boundingBox
            val centerX = bbox.exactCenterX()
            val centerY = bbox.exactCenterY()
            val faceWidth = bbox.width().toFloat()
            val faceHeight = bbox.height().toFloat()

            // Normalized coordinates relative to frame center [-1.0, 1.0]
            var rawNormX = (centerX - (uprightWidth / 2f)) / (uprightWidth / 2f)
            val rawNormY = (centerY - (uprightHeight / 2f)) / (uprightHeight / 2f)

            // For front-facing camera, mirror horizontally so screen coordinates
            // match the physical left/right direction of the target person
            if (isFrontFacing) {
                rawNormX = -rawNormX
            }

            val rawNormW = (faceWidth / uprightWidth).coerceIn(0f, 1f)
            val rawNormH = (faceHeight / uprightHeight).coerceIn(0f, 1f)

            // Exponential Moving Average (EMA) Temporal Smoothing to eliminate jitter.
            // Compare against the pre-fist anchor height so progressive multi-frame chin occlusion
            // while raising a fist cannot gradually erode lastTargetNormH.
            val alpha = 0.65f
            val smoothedX = lastTargetNormX?.let { (alpha * rawNormX) + ((1f - alpha) * it) } ?: rawNormX
            val smoothedY = lastTargetNormY?.let { (alpha * rawNormY) + ((1f - alpha) * it) } ?: rawNormY
            val smoothedW = lastTargetNormW?.let { (alpha * rawNormW) + ((1f - alpha) * it) } ?: rawNormW
            val baselineH = preFistTargetNormH ?: lastTargetNormH
            val smoothedH = lastTargetNormH?.let { prevH ->
                if (isFistHeld && baselineH != null && rawNormH < baselineH * 0.85f) {
                    baselineH
                } else {
                    (alpha * rawNormH) + ((1f - alpha) * prevH)
                }
            } ?: rawNormH

            lastTargetNormX = smoothedX
            lastTargetNormY = smoothedY
            lastTargetNormW = smoothedW
            lastTargetNormH = smoothedH
            if (!isFistHeld) {
                preFistTargetNormH = smoothedH
            }

            val target = DetectedTarget(
                trackingId = selectedFace.trackingId,
                normalizedX = smoothedX.coerceIn(-1f, 1f),
                normalizedY = smoothedY.coerceIn(-1f, 1f),
                normalizedWidth = smoothedW,
                normalizedHeight = smoothedH,
                rawBoundingBox = bbox,
                timestampMs = now
            )

            onTargetDetected(target, uprightWidth, uprightHeight, currentFps)
        } else {
            // Check if we are within the target loss grace period (extended to 3000ms when user holds fist in front of face)
            val effectiveGracePeriodMs = if (isFistHeld) 3000L else targetLostGracePeriodMs
            val isWithinGrace = primaryTrackingId != null &&
                lastTargetNormX != null &&
                lastTargetSeenMs > 0L &&
                (now - lastTargetSeenMs <= effectiveGracePeriodMs)

            if (isWithinGrace) {
                // Bridge the temporary dropout using the last known smoothed position
                val target = DetectedTarget(
                    trackingId = primaryTrackingId,
                    normalizedX = lastTargetNormX!!.coerceIn(-1f, 1f),
                    normalizedY = lastTargetNormY!!.coerceIn(-1f, 1f),
                    normalizedWidth = lastTargetNormW ?: 0.18f,
                    normalizedHeight = (if (isFistHeld) preFistTargetNormH else null) ?: lastTargetNormH ?: 0.18f,
                    timestampMs = now
                )
                onTargetDetected(target, uprightWidth, uprightHeight, currentFps)
            } else {
                // Reset persistent state once grace expires
                if (lastTargetSeenMs == 0L || now - lastTargetSeenMs > effectiveGracePeriodMs) {
                    primaryTrackingId = null
                    lastTargetNormX = null
                    lastTargetNormY = null
                    lastTargetNormW = null
                    lastTargetNormH = null
                    preFistTargetNormH = null
                }
                onTargetDetected(null, uprightWidth, uprightHeight, currentFps)
            }
        }
    }

    /**
     * Enhanced Multi-Factor Target Face Selector:
     * 1. If an existing tracking ID matches, continue tracking it.
     * 2. If tracking ID is lost (e.g. ML Kit reassigned ID on head turn), find closest face to previous (x,y).
     * 3. Otherwise, select the most prominent candidate using a weighted score of face area (size/proximity)
     *    and centrality (faces closer to the center are prioritized over background pedestrians).
     */
    private fun selectTargetFace(faces: List<Face>, frameWidth: Int, frameHeight: Int): Face? {
        if (faces.isEmpty()) return null

        // 1. Exact ID lock match
        if (primaryTrackingId != null) {
            val exactMatch = faces.find { it.trackingId == primaryTrackingId }
            if (exactMatch != null) return exactMatch
        }

        // 2. Spatial proximity fallback: If tracking ID temporarily jumped/reassigned, find closest face
        if (lastTargetNormX != null && lastTargetNormY != null) {
            val closestCandidate = faces.minByOrNull { face ->
                val cx = face.boundingBox.exactCenterX()
                val cy = face.boundingBox.exactCenterY()
                var nx = (cx - (frameWidth / 2f)) / (frameWidth / 2f)
                val ny = (cy - (frameHeight / 2f)) / (frameHeight / 2f)
                if (isFrontFacing) nx = -nx

                val dx = nx - lastTargetNormX!!
                val dy = ny - lastTargetNormY!!
                dx * dx + dy * dy
            }
            // If the closest face is within reasonable proximity (< 0.45 normalized distance), lock onto it
            if (closestCandidate != null) {
                val cx = closestCandidate.boundingBox.exactCenterX()
                val cy = closestCandidate.boundingBox.exactCenterY()
                var nx = (cx - (frameWidth / 2f)) / (frameWidth / 2f)
                val ny = (cy - (frameHeight / 2f)) / (frameHeight / 2f)
                if (isFrontFacing) nx = -nx
                val distSq = (nx - lastTargetNormX!!).let { it * it } + (ny - lastTargetNormY!!).let { it * it }
                if (distSq < 0.25f) { // ~0.5 max normalized offset
                    return closestCandidate
                }
            }
        }

        // 3. New candidate selection: Weighted scoring combining Area + Center Prominence
        return faces.maxByOrNull { face ->
            val w = face.boundingBox.width().toFloat()
            val h = face.boundingBox.height().toFloat()
            val areaFraction = (w * h) / (frameWidth.toFloat() * frameHeight.toFloat())

            val cx = face.boundingBox.exactCenterX()
            val cy = face.boundingBox.exactCenterY()
            val normDistFromCenter = kotlin.math.hypot(
                (cx - frameWidth / 2f) / (frameWidth / 2f),
                (cy - frameHeight / 2f) / (frameHeight / 2f)
            ).coerceIn(0f, 1.5f)

            // Center bias factor: 1.0 at center down to 0.4 at edges
            val centerWeight = 1.0f - (0.4f * normDistFromCenter)
            areaFraction * centerWeight
        }
    }
}
