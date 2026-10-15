package com.teethut.twiprobot.data.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Types of procedural robot vocalizations inspired by Anki Vector/Cozmo, Wall-E, BMO, and R2-D2.
 */
enum class RobotSynthSound(val label: String) {
    HAPPY_CHIRP("Happy Chirp 🎶"),
    LOVE_PURR("Happy Purr & Coo 💖"),
    CURIOUS_BEEP("Curious Question ❓"),
    PROUD_FANFARE("Victory Chime ✨"),
    SURPRISED_GASP("Surprised Eep! 😮"),
    DIZZY_WARBLE("Dizzy Fall Warble 😵"),
    LOCK_BEEP("Target Locked 🎯"),
    SLEEP_SNORE_PURR("Sleepy Snore Purr 💤"),
    WOBBLE_WARBLE("Nervous Wobble Warble 💦"),
    POUT_WHIMPER("Pouty Whimper 😤"),
    CAT_MEOW_RISE("Realistic Meow Rise 🐱"),
    CAT_NOSE_SNEEZE("Cat Sneeze / Boop 🤧"),
    CAT_HISS_STARTLE("Startled Hiss 😾")
}

/**
 * Real-time procedural 16-bit PCM audio synthesizer for expressive robot chirps, trills, and purrs.
 * Generates waveforms mathematically in memory with zero external asset dependencies.
 */
class ProceduralRobotSynth {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastPlayTimeMs: Long = 0L

    /**
     * Plays an expressive procedural robot sound asynchronously on a background AudioTrack thread.
     */
    fun playSynthSound(
        sound: RobotSynthSound,
        volume: Float = 0.85f,
        isManual: Boolean = false
    ): Boolean {
        val now = System.currentTimeMillis()
        if (!isManual && now - lastPlayTimeMs < 900L) {
            return false
        }
        lastPlayTimeMs = now

        scope.launch {
            try {
                val pcmData = synthesizePcm(sound, SAMPLE_RATE)
                playPcmBuffer(pcmData, volume.coerceIn(0f, 1f))
            } catch (e: Exception) {
                Log.e("ProceduralRobotSynth", "Failed to play synth sound $sound: ${e.message}", e)
            }
        }
        return true
    }

    private fun playPcmBuffer(pcmData: ShortArray, volume: Float) {
        if (pcmData.isEmpty()) return

        val byteCount = pcmData.size * 2
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(byteCount)

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(pcmData, 0, pcmData.size)
            track.setVolume(volume)
            track.play()

            val durationMs = (pcmData.size * 1000L) / SAMPLE_RATE
            Thread.sleep(durationMs + 80L)
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 44100

        /**
         * Pure function that synthesizes a 16-bit mono PCM buffer for the requested [RobotSynthSound].
         */
        fun synthesizePcm(sound: RobotSynthSound, sampleRate: Int = SAMPLE_RATE): ShortArray {
            return when (sound) {
                RobotSynthSound.HAPPY_CHIRP -> generateHappyChirp(sampleRate)
                RobotSynthSound.LOVE_PURR -> generateLovePurr(sampleRate)
                RobotSynthSound.CURIOUS_BEEP -> generateCuriousBeep(sampleRate)
                RobotSynthSound.PROUD_FANFARE -> generateProudFanfare(sampleRate)
                RobotSynthSound.SURPRISED_GASP -> generateSurprisedGasp(sampleRate)
                RobotSynthSound.DIZZY_WARBLE -> generateDizzyWarble(sampleRate)
                RobotSynthSound.LOCK_BEEP -> generateLockBeep(sampleRate)
                RobotSynthSound.SLEEP_SNORE_PURR -> generateSleepSnorePurr(sampleRate)
                RobotSynthSound.WOBBLE_WARBLE -> generateWobbleWarble(sampleRate)
                RobotSynthSound.POUT_WHIMPER -> generatePoutWhimper(sampleRate)
                RobotSynthSound.CAT_MEOW_RISE -> generateCatMeowRise(sampleRate)
                RobotSynthSound.CAT_NOSE_SNEEZE -> generateCatNoseSneeze(sampleRate)
                RobotSynthSound.CAT_HISS_STARTLE -> generateCatHissStartle(sampleRate)
            }
        }

        /**
         * Joyful rising Cozmo/BMO trill: two quick staccato chirps followed by a bubbly upward glide with vibrato.
         */
        private fun generateHappyChirp(sampleRate: Int): ShortArray {
            val seg1 = glideTone(sampleRate, durationSec = 0.085, startHz = 780.0, endHz = 1180.0, vibratoHz = 18.0, vibratoDepth = 25.0)
            val gap1 = silence(sampleRate, 0.025)
            val seg2 = glideTone(sampleRate, durationSec = 0.085, startHz = 980.0, endHz = 1480.0, vibratoHz = 20.0, vibratoDepth = 30.0)
            val gap2 = silence(sampleRate, 0.025)
            val seg3 = glideTone(sampleRate, durationSec = 0.19, startHz = 1100.0, endHz = 1960.0, vibratoHz = 28.0, vibratoDepth = 65.0)
            return concat(seg1, gap1, seg2, gap2, seg3)
        }

        /**
         * Warm rhythmic cat-bot purr (~26 Hz AM modulation) layered with a sweet high cooing pitch glide.
         */
        private fun generateLovePurr(sampleRate: Int): ShortArray {
            val durationSec = 0.65
            val totalSamples = (sampleRate * durationSec).toInt()
            val out = ShortArray(totalSamples)

            var cooPhase = 0.0
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val progress = t / durationSec

                // Envelope
                val env = adsrEnvelope(progress, attack = 0.12, decay = 0.15, sustainLevel = 0.85, releaseStart = 0.78)

                // 1. Warm Purr layer: 26 Hz pulse modulating 130 Hz + 260 Hz warm hum
                val purrMod = 0.55 + 0.45 * sin(2.0 * PI * 26.0 * t)
                val purrCarrier = 0.65 * sin(2.0 * PI * 135.0 * t) + 0.35 * sin(2.0 * PI * 270.0 * t)
                val purrSignal = purrMod * purrCarrier * 0.48

                // 2. Cute Coo layer: gentle arch from 660 Hz -> 990 Hz -> 880 Hz with soft vibrato
                val arch = sin(PI * progress)
                val cooFreq = 680.0 + 340.0 * arch + 18.0 * sin(2.0 * PI * 14.0 * t)
                cooPhase += (2.0 * PI * cooFreq) / sampleRate
                val cooSignal = sin(cooPhase) * 0.52

                val sample = (purrSignal + cooSignal) * env
                out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.85).toInt().toShort()
            }
            return out
        }

        /**
         * Inquisitive upward pitch bend ("bwee-ooo?").
         */
        private fun generateCuriousBeep(sampleRate: Int): ShortArray {
            val seg1 = glideTone(sampleRate, durationSec = 0.11, startHz = 620.0, endHz = 580.0, vibratoHz = 0.0, vibratoDepth = 0.0)
            val gap = silence(sampleRate, 0.03)
            val seg2 = glideTone(sampleRate, durationSec = 0.24, startHz = 640.0, endHz = 1420.0, vibratoHz = 16.0, vibratoDepth = 22.0, curveExp = 1.8)
            return concat(seg1, gap, seg2)
        }

        /**
         * Cute 4-note major arpeggio chime (C6 - E6 - G6 - C7) with soft bell harmonics.
         */
        private fun generateProudFanfare(sampleRate: Int): ShortArray {
            val n1 = glideTone(sampleRate, durationSec = 0.075, startHz = 1046.5, endHz = 1046.5, overtoneMix = 0.25)
            val n2 = glideTone(sampleRate, durationSec = 0.075, startHz = 1318.5, endHz = 1318.5, overtoneMix = 0.25)
            val n3 = glideTone(sampleRate, durationSec = 0.075, startHz = 1568.0, endHz = 1568.0, overtoneMix = 0.25)
            val n4 = glideTone(sampleRate, durationSec = 0.22, startHz = 2093.0, endHz = 2093.0, vibratoHz = 12.0, vibratoDepth = 18.0, overtoneMix = 0.30)
            return concat(n1, silence(sampleRate, 0.015), n2, silence(sampleRate, 0.015), n3, silence(sampleRate, 0.015), n4)
        }

        /**
         * Quick startled "Eep!" upward jump when Stop palm is shown.
         */
        private fun generateSurprisedGasp(sampleRate: Int): ShortArray {
            val seg1 = glideTone(sampleRate, durationSec = 0.14, startHz = 820.0, endHz = 1850.0, vibratoHz = 32.0, vibratoDepth = 45.0, curveExp = 0.6)
            val gap = silence(sampleRate, 0.025)
            val seg2 = glideTone(sampleRate, durationSec = 0.09, startHz = 1350.0, endHz = 1100.0)
            return concat(seg1, gap, seg2)
        }

        /**
         * Sad/dizzy descending warble when the robot loses balance or tips over.
         */
        private fun generateDizzyWarble(sampleRate: Int): ShortArray {
            return glideTone(
                sampleRate = sampleRate,
                durationSec = 0.52,
                startHz = 1150.0,
                endHz = 340.0,
                vibratoHz = 14.0,
                vibratoDepth = 95.0,
                overtoneMix = 0.20
            )
        }

        /**
         * Crisp double confirmation chirp when target locks on.
         */
        private fun generateLockBeep(sampleRate: Int): ShortArray {
            val b1 = glideTone(sampleRate, durationSec = 0.055, startHz = 1174.6, endHz = 1174.6)
            val gap = silence(sampleRate, 0.02)
            val b2 = glideTone(sampleRate, durationSec = 0.09, startHz = 1568.0, endHz = 1760.0)
            return concat(b1, gap, b2)
        }

        /**
         * Anime cartoon sleeping snore (Kirby / Pikachu style):
         * High sweet whistle inhale (620 Hz -> 840 Hz) with gentle purr-flutter,
         * brief resting turnaround pause, followed by a soft relaxing exhale sigh (560 Hz -> 350 Hz).
         */
        private fun generateSleepSnorePurr(sampleRate: Int): ShortArray {
            // 1. Inhale whistle: 0.42s swell from 620 Hz to 840 Hz
            val inhaleSec = 0.42
            val inhaleSamples = (sampleRate * inhaleSec).toInt()
            val inhale = ShortArray(inhaleSamples)
            var phase1 = 0.0
            for (i in 0 until inhaleSamples) {
                val t = i.toDouble() / sampleRate
                val progress = t / inhaleSec
                // Exponential curve up
                val baseFreq = 620.0 + (840.0 - 620.0) * progress.pow(1.3)
                val vibrato = 14.0 * sin(2.0 * PI * 13.0 * t)
                val instFreq = baseFreq + vibrato
                phase1 += (2.0 * PI * instFreq) / sampleRate

                // Gentle swell envelope: sine half-arch swell
                val env = sin(PI * progress * 0.5).pow(1.6)
                // Pure round flute-like whistle with slight harmonic
                val sample = (0.90 * sin(phase1) + 0.10 * sin(2.0 * phase1)) * env * 0.72
                inhale[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }

            // 2. Breath turnaround gap: 0.065s
            val gap = silence(sampleRate, 0.065)

            // 3. Exhale sigh / puff: 0.52s gentle falling relaxation from 560 Hz down to 350 Hz
            val exhaleSec = 0.52
            val exhaleSamples = (sampleRate * exhaleSec).toInt()
            val exhale = ShortArray(exhaleSamples)
            var phase2 = 0.0
            for (i in 0 until exhaleSamples) {
                val t = i.toDouble() / sampleRate
                val progress = t / exhaleSec
                val baseFreq = 560.0 - (560.0 - 350.0) * progress.pow(0.85)
                val flutter = 8.0 * sin(2.0 * PI * 8.0 * t)
                val instFreq = baseFreq + flutter
                phase2 += (2.0 * PI * instFreq) / sampleRate

                // Gentle puff envelope: quick soft onset, long peaceful fade out
                val attack = (progress / 0.12).coerceAtMost(1.0)
                val fade = (1.0 - progress).pow(1.3)
                val env = attack * fade
                val sample = (0.88 * sin(phase2) + 0.12 * sin(2.0 * phase2)) * env * 0.65
                exhale[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }

            return concat(inhale, gap, exhale)
        }

        /**
         * High-energy rapid flutter warble whistle during sharp pitch tilt or nervous wobble.
         */
        private fun generateWobbleWarble(sampleRate: Int): ShortArray {
            val seg1 = glideTone(
                sampleRate = sampleRate,
                durationSec = 0.22,
                startHz = 620.0,
                endHz = 1380.0,
                vibratoHz = 34.0,
                vibratoDepth = 85.0,
                overtoneMix = 0.25,
                curveExp = 1.5
            )
            val seg2 = glideTone(
                sampleRate = sampleRate,
                durationSec = 0.16,
                startHz = 1380.0,
                endHz = 840.0,
                vibratoHz = 28.0,
                vibratoDepth = 55.0,
                overtoneMix = 0.20
            )
            return concat(seg1, seg2)
        }

        /**
         * Grumpy cute whimper / pout chirp when target is lost or user shows thumb down.
         */
        private fun generatePoutWhimper(sampleRate: Int): ShortArray {
            val note1 = glideTone(
                sampleRate = sampleRate,
                durationSec = 0.13,
                startHz = 740.0,
                endHz = 520.0,
                vibratoHz = 8.0,
                vibratoDepth = 15.0,
                overtoneMix = 0.18
            )
            val gap = silence(sampleRate, 0.035)
            val note2 = glideTone(
                sampleRate = sampleRate,
                durationSec = 0.19,
                startHz = 560.0,
                endHz = 360.0,
                vibratoHz = 12.0,
                vibratoDepth = 25.0,
                overtoneMix = 0.22,
                curveExp = 0.8
            )
            return concat(note1, gap, note2)
        }

        /**
         * Realistic kitten "miau" formant sweep: rising and falling glide tone with feline formant vibrato.
         * Sweeps smoothly: 450 Hz -> 1200 Hz -> 700 Hz.
         */
        private fun generateCatMeowRise(sampleRate: Int): ShortArray {
            val durationSec = 0.48
            val totalSamples = (sampleRate * durationSec).toInt()
            val out = ShortArray(totalSamples)
            var phase = 0.0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val progress = t / durationSec

                // Frequency contour: rise to 1200 Hz at 42%, then glide down to 700 Hz
                val baseFreq = if (progress < 0.42) {
                    val p = progress / 0.42
                    450.0 + (1200.0 - 450.0) * sin(p * PI * 0.5)
                } else {
                    val p = (progress - 0.42) / 0.58
                    1200.0 - (1200.0 - 700.0) * sin(p * PI * 0.5)
                }

                // Feline throat vibrato active in middle and end
                val vibratoDepth = if (progress > 0.25) 24.0 * sin((progress - 0.25) / 0.75 * PI) else 0.0
                val vibrato = vibratoDepth * sin(2.0 * PI * 18.0 * t)
                val instFreq = (baseFreq + vibrato).coerceAtLeast(100.0)

                phase += (2.0 * PI * instFreq) / sampleRate

                // Formant harmonics for realistic feline vocal tract
                val fundamental = sin(phase)
                val harmonic2 = sin(2.0 * phase)
                val harmonic3 = sin(3.0 * phase)
                val formantTone = 0.62 * fundamental + 0.26 * harmonic2 + 0.12 * harmonic3

                // ADSR-like envelope: soft rise, held meow, gentle fade
                val env = adsrEnvelope(progress, attack = 0.14, decay = 0.12, sustainLevel = 0.88, releaseStart = 0.68)
                val sample = formantTone * env * 0.85
                out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            return out
        }

        /**
         * Cute high-frequency sneeze chirp when nose is booped:
         * Quick high tick followed by an 1800 Hz sneeze burst + breath noise.
         */
        private fun generateCatNoseSneeze(sampleRate: Int): ShortArray {
            // 1. Pre-sneeze tick chirp: 0.045s glide from 1200 Hz to 2400 Hz
            val tick = glideTone(sampleRate, durationSec = 0.045, startHz = 1200.0, endHz = 2400.0, overtoneMix = 0.3)
            val gap = silence(sampleRate, 0.015)

            // 2. Sneeze burst ("A-tchoo!"): 0.18s burst mixing high sine sweep (1800 Hz -> 500 Hz) with filtered breath noise
            val sneezeSec = 0.18
            val sneezeSamples = (sampleRate * sneezeSec).toInt()
            val sneeze = ShortArray(sneezeSamples)
            var phase = 0.0
            val rng = Random(42)

            for (i in 0 until sneezeSamples) {
                val t = i.toDouble() / sampleRate
                val progress = t / sneezeSec
                val baseFreq = 1800.0 - (1800.0 - 520.0) * progress.pow(0.7)
                phase += (2.0 * PI * baseFreq) / sampleRate

                // Sneeze impulse envelope: explosive fast attack (0.015s) and sharp exponential fall
                val env = if (progress < 0.08) {
                    progress / 0.08
                } else {
                    exp(-4.5 * (progress - 0.08))
                }

                val tone = sin(phase) * 0.60
                val breath = (rng.nextDouble() * 2.0 - 1.0) * 0.40
                val sample = (tone + breath) * env * 0.90
                sneeze[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }

            return concat(tick, gap, sneeze)
        }

        /**
         * Filtered noise burst hiss with fast attack for startled reactions.
         */
        private fun generateCatHissStartle(sampleRate: Int): ShortArray {
            val durationSec = 0.34
            val totalSamples = (sampleRate * durationSec).toInt()
            val out = ShortArray(totalSamples)
            val rng = Random(1337)
            var prevNoise = 0.0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val progress = t / durationSec

                // Fast attack (0.02s) and gentle decay envelope
                val env = if (progress < 0.06) {
                    progress / 0.06
                } else {
                    exp(-3.2 * (progress - 0.06))
                }

                // High-pass filtered noise + 38 Hz fluttering hiss modulation
                val rawWhite = rng.nextDouble() * 2.0 - 1.0
                val highPassed = rawWhite - 0.75 * prevNoise
                prevNoise = rawWhite

                val throatFlutter = 0.85 + 0.15 * sin(2.0 * PI * 38.0 * t)
                val sample = highPassed * throatFlutter * env * 0.65
                out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            return out
        }

        private fun glideTone(
            sampleRate: Int,
            durationSec: Double,
            startHz: Double,
            endHz: Double,
            vibratoHz: Double = 0.0,
            vibratoDepth: Double = 0.0,
            overtoneMix: Double = 0.15,
            curveExp: Double = 1.0
        ): ShortArray {
            val count = (sampleRate * durationSec).toInt().coerceAtLeast(1)
            val out = ShortArray(count)
            var phase = 0.0
            var phase2 = 0.0

            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val progress = (i.toDouble() / count).pow(curveExp)
                val baseFreq = startHz + (endHz - startHz) * progress
                val vibrato = if (vibratoHz > 0.0) vibratoDepth * sin(2.0 * PI * vibratoHz * t) else 0.0
                val instFreq = (baseFreq + vibrato).coerceAtLeast(40.0)

                phase += (2.0 * PI * instFreq) / sampleRate
                phase2 += (2.0 * PI * instFreq * 2.0) / sampleRate

                val env = adsrEnvelope(i.toDouble() / count, attack = 0.08, decay = 0.15, sustainLevel = 0.85, releaseStart = 0.75)
                val raw = (1.0 - overtoneMix) * sin(phase) + overtoneMix * sin(phase2)
                val sample = raw * env * 0.85
                out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            return out
        }

        private fun adsrEnvelope(
            progress: Double,
            attack: Double,
            decay: Double,
            sustainLevel: Double,
            releaseStart: Double
        ): Double {
            return when {
                progress < attack -> (progress / attack).coerceIn(0.0, 1.0)
                progress < attack + decay -> {
                    val decayProg = (progress - attack) / decay
                    1.0 - (1.0 - sustainLevel) * decayProg
                }
                progress < releaseStart -> sustainLevel
                else -> {
                    val relProg = ((progress - releaseStart) / (1.0 - releaseStart)).coerceIn(0.0, 1.0)
                    sustainLevel * exp(-3.5 * relProg) * (1.0 - relProg)
                }
            }
        }

        private fun silence(sampleRate: Int, durationSec: Double): ShortArray {
            return ShortArray((sampleRate * durationSec).toInt().coerceAtLeast(0))
        }

        private fun concat(vararg arrays: ShortArray): ShortArray {
            val totalLen = arrays.sumOf { it.size }
            val result = ShortArray(totalLen)
            var offset = 0
            for (arr in arrays) {
                System.arraycopy(arr, 0, result, offset, arr.size)
                offset += arr.size
            }
            return result
        }
    }
}

