package com.teethut.twiprobot

import com.teethut.twiprobot.data.sound.ProceduralRobotSynth
import com.teethut.twiprobot.data.sound.RobotSynthSound
import com.teethut.twiprobot.domain.model.RobotHandGesture
import com.teethut.twiprobot.domain.model.TargetLockStatus
import com.teethut.twiprobot.presentation.tracker.components.CatEmotionEngine
import com.teethut.twiprobot.presentation.tracker.components.CatEyeShape
import com.teethut.twiprobot.presentation.tracker.components.CatMouthStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CatEmotionEngineTest {

    @Test
    fun `when robot is not balancing, engine resolves dizzy cross eyes and sad wavy mouth`() {
        val emotion = CatEmotionEngine.resolveEmotion(
            isBalancing = false,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.SEARCHING,
            pitchAngle = 0f,
            idleDurationMs = 0L
        )

        assertEquals(CatEyeShape.DIZZY_CROSS, emotion.eyeShape)
        assertEquals(CatMouthStyle.WAVY_SAD, emotion.mouthStyle)
        assertTrue(emotion.statusBannerText?.contains("DIZZY") == true)
        assertEquals(15f, emotion.earDroopDeg, 0.01f)
    }

    @Test
    fun `when robot pitch exceeds 16_0 degrees, engine activates nervous wobble and sweat drop`() {
        // Forward tilt
        val fwdEmotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOCKED,
            pitchAngle = 17.5f,
            idleDurationMs = 0L
        )

        assertEquals(CatEyeShape.NERVOUS_WOBBLE, fwdEmotion.eyeShape)
        assertEquals(CatMouthStyle.SURPRISED_O, fwdEmotion.mouthStyle)
        assertTrue("Sweat drop should be active during sharp pitch wobble", fwdEmotion.hasSweatDrop)
        assertEquals(1.0f, fwdEmotion.sweatDropIntensity, 0.01f)
        assertTrue(fwdEmotion.earFlattenBack)
        assertTrue(fwdEmotion.statusBannerText?.contains("WOBBLE") == true)

        // Backward tilt
        val bwdEmotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOCKED,
            pitchAngle = -17.2f,
            idleDurationMs = 0L
        )

        assertEquals(CatEyeShape.NERVOUS_WOBBLE, bwdEmotion.eyeShape)
        assertTrue(bwdEmotion.hasSweatDrop)
    }

    @Test
    fun `when user gives thumb down, engine activates grumpy pout and puffed cheeks`() {
        val emotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.THUMB_DOWN,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOCKED,
            pitchAngle = 0f,
            idleDurationMs = 0L
        )

        assertEquals(CatEyeShape.POUTY_HALF, emotion.eyeShape)
        assertEquals(CatMouthStyle.POUT_PUFFED, emotion.mouthStyle)
        assertTrue("Puffed cheeks scale should be greater than 1.2", emotion.puffedCheeksScale > 1.2f)
        assertTrue(emotion.statusBannerText?.contains("HMPH") == true)
    }

    @Test
    fun `when target is lost, engine activates cute pout with puffed cheeks`() {
        val emotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOST,
            pitchAngle = 0f,
            idleDurationMs = 0L
        )

        assertEquals(CatEyeShape.POUTY_HALF, emotion.eyeShape)
        assertEquals(CatMouthStyle.POUT_PUFFED, emotion.mouthStyle)
        assertTrue(emotion.puffedCheeksScale > 1.1f)
        assertTrue(emotion.statusBannerText?.contains("Where did you go") == true)
    }

    @Test
    fun `when idle duration reaches 15 seconds, engine triggers sleepy napping mode with zZz`() {
        // Less than 15s -> Not napping
        val activeEmotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.SEARCHING,
            pitchAngle = 0f,
            idleDurationMs = 14000L
        )
        assertFalse(activeEmotion.isNapping)
        assertEquals(CatEyeShape.NORMAL_ROUNDED, activeEmotion.eyeShape)

        // 15s or more without locked target -> Enters napping mode
        val nappingEmotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.SEARCHING,
            pitchAngle = 0f,
            idleDurationMs = 15000L
        )
        assertTrue("Should be in napping mode after 15s idle", nappingEmotion.isNapping)
        assertEquals(CatEyeShape.SLEEPY_CLOSED, nappingEmotion.eyeShape)
        assertEquals(CatMouthStyle.NEUTRAL_W, nappingEmotion.mouthStyle)
        assertTrue(nappingEmotion.statusBannerText?.contains("NAPPING") == true)
        assertTrue(nappingEmotion.statusBannerText?.contains("zZz") == true)

        // If target is locked, robot does NOT nap even after 15s
        val lockedEmotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOCKED,
            pitchAngle = 0f,
            idleDurationMs = 20000L
        )
        assertFalse("Should not nap when target is actively locked", lockedEmotion.isNapping)
        assertEquals(CatEyeShape.NORMAL_ROUNDED, lockedEmotion.eyeShape)
    }

    @Test
    fun `wave gesture triggers joyful squint and open laughing mouth with star sparkles`() {
        val emotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.WAVE,
            isWaveTriggered = true,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOCKED,
            pitchAngle = 0f,
            idleDurationMs = 0L
        )

        assertEquals(CatEyeShape.HAPPY_SQUINT, emotion.eyeShape)
        assertEquals(CatMouthStyle.OPEN_LAUGH, emotion.mouthStyle)
        assertTrue(emotion.hasStarSparkles)
        assertTrue(emotion.statusBannerText?.contains("MEOW") == true)
    }

    @Test
    fun `procedural synth vocalizations produce valid non-clipping audio buffers`() {
        val sleepPcm = ProceduralRobotSynth.synthesizePcm(RobotSynthSound.SLEEP_SNORE_PURR)
        val wobblePcm = ProceduralRobotSynth.synthesizePcm(RobotSynthSound.WOBBLE_WARBLE)
        val poutPcm = ProceduralRobotSynth.synthesizePcm(RobotSynthSound.POUT_WHIMPER)

        // Verify buffer size and duration
        assertTrue("Sleep snore should be at least 1s (~44100 samples)", sleepPcm.size > 40000)
        assertTrue("Wobble warble should be ~0.3s-0.5s (>14000 samples)", wobblePcm.size > 14000)
        assertTrue("Pout whimper should be ~0.3s (>12000 samples)", poutPcm.size > 12000)

        // Verify amplitudes are non-silent and non-zero
        val maxSleep = sleepPcm.maxOf { abs(it.toInt()) }
        val maxWobble = wobblePcm.maxOf { abs(it.toInt()) }
        val maxPout = poutPcm.maxOf { abs(it.toInt()) }

        assertTrue("Sleep purr must be audible (>5000)", maxSleep > 5000)
        assertTrue("Wobble warble must be audible (>10000)", maxWobble > 10000)
        assertTrue("Pout whimper must be audible (>10000)", maxPout > 10000)

        // Verify no PCM clipping overflow (Short.MAX_VALUE = 32767)
        assertTrue(maxSleep <= Short.MAX_VALUE)
        assertTrue(maxWobble <= Short.MAX_VALUE)
        assertTrue(maxPout <= Short.MAX_VALUE)
    }

    @Test
    fun `when target was lost but robot remains idle for 15s, engine transitions to sleepy napping mode`() {
        val emotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOST,
            pitchAngle = 0f,
            idleDurationMs = 15000L
        )

        assertTrue("Should enter napping mode after 15s idle even if target was lost", emotion.isNapping)
        assertEquals(CatEyeShape.SLEEPY_CLOSED, emotion.eyeShape)
        assertEquals(CatMouthStyle.NEUTRAL_W, emotion.mouthStyle)
        assertTrue(emotion.statusBannerText?.contains("NAPPING") == true)
    }

    @Test
    fun `when robot tilts past 16_0 degrees during napping idle, nervous wobble immediately overrides nap`() {
        val emotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.SEARCHING,
            pitchAngle = 17.0f,
            idleDurationMs = 20000L // Was idle for 20s
        )

        assertFalse("Napping mode must immediately abort on sudden tilt wobble", emotion.isNapping)
        assertEquals(CatEyeShape.NERVOUS_WOBBLE, emotion.eyeShape)
        assertEquals(CatMouthStyle.SURPRISED_O, emotion.mouthStyle)
        assertTrue(emotion.hasSweatDrop)
        assertEquals(1.0f, emotion.sweatDropIntensity, 0.01f)
    }

    @Test
    fun `custom earTuftColorHex in CatEmotionParameters is preserved correctly`() {
        val defaultEmotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.SEARCHING,
            pitchAngle = 0f,
            idleDurationMs = 0L
        )
        assertEquals(0xFFFFFFFFL, defaultEmotion.earTuftColorHex)

        val customEmotion = defaultEmotion.copy(earTuftColorHex = 0xFFFFC0CBL)
        assertEquals(0xFFFFC0CBL, customEmotion.earTuftColorHex)
    }

    @Test
    fun `procedural synthesizer generates valid non-empty PCM buffers for feline sounds`() {
        val sounds = listOf(
            RobotSynthSound.CAT_MEOW_RISE,
            RobotSynthSound.CAT_NOSE_SNEEZE,
            RobotSynthSound.CAT_HISS_STARTLE
        )

        for (sound in sounds) {
            val pcm = ProceduralRobotSynth.synthesizePcm(sound)
            assertTrue("Sound $sound must generate non-empty PCM buffer", pcm.isNotEmpty())
            assertTrue("Sound $sound must have at least 2000 samples", pcm.size > 2000)

            var nonZeroFound = false
            for (sample in pcm) {
                if (sample.toInt() != 0) {
                    nonZeroFound = true
                    break
                }
            }
            assertTrue("Sound $sound must contain audio signal", nonZeroFound)
        }
    }

    @Test
    fun `feline drives evolve correctly across petting, wobble, and idle events`() {
        val initial = com.teethut.twiprobot.domain.model.FelineDrives(
            affection = 0.5f,
            energy = 0.5f,
            curiosity = 0.5f
        )

        val engine = com.teethut.twiprobot.data.ai.CatPersonalityEngine(
            com.teethut.twiprobot.data.ai.GemmaInferenceManager(
                android.content.ContextWrapper(null)
            )
        )

        // Petting increases affection
        val petted = engine.calculateNextDrives(initial, "PET_EAR")
        assertTrue("Petting must boost affection", petted.affection > initial.affection)

        // Booping nose boosts curiosity & affection
        val booped = engine.calculateNextDrives(initial, "NOSE_BOOP")
        assertTrue("Nose boop must boost curiosity", booped.curiosity > initial.curiosity)

        // Wobble reduces energy
        val wobbled = engine.calculateNextDrives(initial, "ROBOT_WOBBLE")
        assertTrue("Wobble must consume energy", wobbled.energy < initial.energy)

        // Idle decreases energy and curiosity
        val idled = engine.calculateNextDrives(initial, "BORED_IDLE")
        assertTrue("Idle must decrease energy", idled.energy < initial.energy)
        assertTrue("Idle must decrease curiosity", idled.curiosity < initial.curiosity)
    }

    @Test
    fun `feline directive dynamically modulates anime face expressions and moods`() {
        // 1. Happy mood
        val happyDirective = com.teethut.twiprobot.domain.model.CatPersonalityDirective(
            speech = "I love you!",
            mood = com.teethut.twiprobot.domain.model.FelineMood.HAPPY,
            pupilDilation = 0.8f,
            earDroop = 0.1f,
            blushAlpha = 0.6f
        )
        val happyEmotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOCKED,
            felineDirective = happyDirective
        )
        assertEquals(CatEyeShape.HAPPY_SQUINT, happyEmotion.eyeShape)
        assertEquals(CatMouthStyle.OPEN_LAUGH, happyEmotion.mouthStyle)
        assertTrue(happyEmotion.hasStarSparkles)
        assertEquals("I love you!", happyEmotion.statusBannerText)

        // 2. Affectionate mood -> Heart eyes
        val loveDirective = com.teethut.twiprobot.domain.model.CatPersonalityDirective(
            mood = com.teethut.twiprobot.domain.model.FelineMood.AFFECTIONATE,
            pupilDilation = 0.9f
        )
        val loveEmotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOCKED,
            felineDirective = loveDirective
        )
        assertEquals(CatEyeShape.HEART_EYES, loveEmotion.eyeShape)
        assertTrue(loveEmotion.hasStarSparkles)

        // 3. Startled mood -> Surprised wide eyes with sweat drop
        val startledDirective = com.teethut.twiprobot.domain.model.CatPersonalityDirective(
            mood = com.teethut.twiprobot.domain.model.FelineMood.STARTLED
        )
        val startledEmotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOCKED,
            felineDirective = startledDirective
        )
        assertEquals(CatEyeShape.SURPRISED_WIDE, startledEmotion.eyeShape)
        assertEquals(CatMouthStyle.SURPRISED_O, startledEmotion.mouthStyle)
        assertTrue(startledEmotion.hasSweatDrop)

        // 4. Sleepy mood -> Sleepy closed eyes and napping flag
        val sleepyDirective = com.teethut.twiprobot.domain.model.CatPersonalityDirective(
            mood = com.teethut.twiprobot.domain.model.FelineMood.SLEEPY
        )
        val sleepyEmotion = CatEmotionEngine.resolveEmotion(
            isBalancing = true,
            gesture = RobotHandGesture.NONE,
            isWaveTriggered = false,
            isStopGestureActive = false,
            lockStatus = TargetLockStatus.LOCKED,
            felineDirective = sleepyDirective
        )
        assertEquals(CatEyeShape.SLEEPY_CLOSED, sleepyEmotion.eyeShape)
        assertTrue(sleepyEmotion.isNapping)
    }
}



