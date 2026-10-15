package com.teethut.twiprobot.data.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.teethut.twiprobot.R

/**
 * Manages low-latency audio playback for companion sound effects (such as cute cat meows)
 * triggered by hand gestures and user interactions.
 *
 * Employs a dual-tier playback strategy:
 * 1. Ultra low-latency SoundPool using USAGE_MEDIA (STREAM_MUSIC).
 * 2. Automatic MediaPlayer fallback if SoundPool is loading or fails on hardware.
 */
class SoundEffectManager(context: Context) {

    private val appContext = context.applicationContext
    private var soundPool: SoundPool? = null
    private val rawResList = listOf(
        R.raw.cute_meow_1,
        R.raw.cute_meow_2,
        R.raw.cute_meow_chirp
    )
    private val soundIdToResMap = mutableMapOf<Int, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    private var lastPlayTimeMs: Long = 0L

    init {
        try {
            // Crucial: Use USAGE_MEDIA and CONTENT_TYPE_MUSIC so playback routes through
            // the device's normal Media Volume stream (instead of muted system notification stream).
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build().apply {
                    rawResList.forEach { resId ->
                        val sampleId = load(appContext, resId, 1)
                        soundIdToResMap[sampleId] = resId
                    }

                    setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0) {
                            loadedSoundIds.add(sampleId)
                            Log.d("SoundEffectManager", "Sound sample $sampleId loaded successfully into SoundPool")
                        } else {
                            Log.w("SoundEffectManager", "Sound sample $sampleId failed to load with status $status")
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("SoundEffectManager", "Failed to initialize SoundPool: ${e.message}", e)
        }
    }

    /**
     * Plays a random cute kitten meow sound effect.
     * @param volume Playback volume between 0.0 and 1.0 (defaults to 1.0 full volume)
     * @param isManual If true, bypasses the automatic gesture debounce to ensure test buttons always fire
     */
    fun playCuteMeow(volume: Float = 1.0f, isManual: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!isManual && now - lastPlayTimeMs < 1100L) {
            return false
        }
        lastPlayTimeMs = now

        val targetRes = rawResList.random()

        // 1. Attempt SoundPool playback if loaded
        val pool = soundPool
        if (pool != null) {
            val matchingSampleId = soundIdToResMap.entries.firstOrNull { it.value == targetRes }?.key
            if (matchingSampleId != null && loadedSoundIds.contains(matchingSampleId)) {
                val streamId = pool.play(matchingSampleId, volume, volume, 1, 0, 1.0f)
                if (streamId > 0) {
                    Log.d("SoundEffectManager", "Played meow via SoundPool (sampleId=$matchingSampleId, streamId=$streamId)")
                    return true
                }
                Log.w("SoundEffectManager", "SoundPool.play returned 0, attempting MediaPlayer fallback")
            }
        }

        // 2. Fallback to MediaPlayer for 100% reliable hardware playback
        return playViaMediaPlayer(targetRes, volume)
    }

    private fun playViaMediaPlayer(resId: Int, volume: Float): Boolean {
        return try {
            val mp = MediaPlayer.create(appContext, resId) ?: run {
                Log.e("SoundEffectManager", "MediaPlayer.create returned null for resId=$resId")
                return false
            }
            mp.setVolume(volume, volume)
            mp.setOnCompletionListener { player ->
                player.release()
            }
            mp.start()
            Log.d("SoundEffectManager", "Played meow via MediaPlayer fallback (resId=$resId)")
            true
        } catch (e: Exception) {
            Log.e("SoundEffectManager", "MediaPlayer fallback failed: ${e.message}", e)
            false
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundIdToResMap.clear()
        loadedSoundIds.clear()
    }

    companion object {
        @Volatile
        private var instance: SoundEffectManager? = null

        fun getInstance(context: Context): SoundEffectManager {
            return instance ?: synchronized(this) {
                instance ?: SoundEffectManager(context).also { instance = it }
            }
        }
    }
}
