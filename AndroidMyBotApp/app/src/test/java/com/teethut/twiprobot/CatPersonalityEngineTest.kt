package com.teethut.twiprobot

import com.teethut.twiprobot.data.ai.CatPersonalityEngine
import com.teethut.twiprobot.data.ai.GemmaInferenceManager
import com.teethut.twiprobot.domain.model.FelineMood
import com.teethut.twiprobot.domain.model.FelineWheelAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatPersonalityEngineTest {

    private lateinit var engine: CatPersonalityEngine

    @Before
    fun setUp() {
        val mockManager = GemmaInferenceManager(android.content.ContextWrapper(null))
        engine = CatPersonalityEngine(mockManager)
    }

    @Test
    fun `isValidEnglishSpeech allows short cute words down to 2 letters`() {
        assertTrue("Yay! should be valid speech", engine.isValidEnglishSpeech("Yay!"))
        assertTrue("Hi! should be valid speech", engine.isValidEnglishSpeech("Hi!"))
        assertTrue("Purr! should be valid speech", engine.isValidEnglishSpeech("Purr!"))
        assertTrue("Meow! should be valid speech", engine.isValidEnglishSpeech("Meow!"))
        assertTrue("Aww! should be valid speech", engine.isValidEnglishSpeech("Aww!"))
        assertTrue("Ooh! should be valid speech", engine.isValidEnglishSpeech("Ooh!"))
    }

    @Test
    fun `isValidEnglishSpeech rejects non-Latin scripts and raw JSON`() {
        assertFalse("CJK characters must be rejected", engine.isValidEnglishSpeech("Meow! 猫咪可爱"))
        assertFalse("Cyrillic script must be rejected", engine.isValidEnglishSpeech("Meow!Привет"))
        assertFalse("Raw JSON keys must be rejected", engine.isValidEnglishSpeech("""{"speech":"hello"}"""))
        assertFalse("Repetitive loops must be rejected", engine.isValidEnglishSpeech("cat cat cat cat cat"))
    }

    @Test
    fun `parseFelineResponse correctly parses valid structured JSON`() {
        val json = """{"speech":"Purrrrr! I love ear scratches!","vocalization":"LOVE_PURR","mood":"AFFECTIONATE","wheel_action":"PURR_ROCK","pupil_dilation":0.85,"ear_droop":0.1,"blush_alpha":0.8}"""
        val directive = engine.parseFelineResponse(json, 120L, "PET_EAR")

        assertEquals("Purrrrr! I love ear scratches!", directive.speech)
        assertEquals("LOVE_PURR", directive.vocalization)
        assertEquals(FelineMood.AFFECTIONATE, directive.mood)
        assertEquals(FelineWheelAction.PURR_ROCK, directive.wheelAction)
        assertEquals(0.85f, directive.pupilDilation, 0.01f)
        assertEquals("GEMMA_4", directive.source)
    }

    @Test
    fun `parseFelineResponse handles markdown-wrapped JSON`() {
        val raw = """
        ```json
        {"speech":"Whoa, dizzy balancing!","vocalization":"WOBBLE_WARBLE","mood":"STARTLED","wheel_action":"RECOIL_BACK"}
        ```
        """.trimIndent()
        val directive = engine.parseFelineResponse(raw, 150L, "WOBBLE")

        assertEquals("Whoa, dizzy balancing!", directive.speech)
        assertEquals("WOBBLE_WARBLE", directive.vocalization)
        assertEquals(FelineMood.STARTLED, directive.mood)
        assertEquals("GEMMA_4", directive.source)
    }

    @Test
    fun `parseFelineResponse salvage plain text Gemma outputs when JSON format is omitted`() {
        val plainText = "Meow! I see a red ball in front of me! Can I pounce?"
        val directive = engine.parseFelineResponse(plainText, 200L, "SCENE_OBJECT_BALL")

        assertEquals("Meow! I see a red ball in front of me! Can I pounce?", directive.speech)
        assertEquals("GEMMA_4", directive.source)
        assertEquals(FelineMood.CURIOUS, directive.mood)
    }

    @Test
    fun `parseFelineResponse infers mood and vocalization when JSON fields are omitted`() {
        val partialJson = """{"speech":"Aww, I love you so much!"}"""
        val directive = engine.parseFelineResponse(partialJson, 100L, "PET")

        assertEquals("Aww, I love you so much!", directive.speech)
        assertEquals("LOVE_PURR", directive.vocalization)
        assertEquals(FelineMood.AFFECTIONATE, directive.mood)
        assertEquals("GEMMA_4", directive.source)
    }

    @Test
    fun `buildPrompt formats cyber-cat directive request cleanly`() {
        val prompt = engine.buildPrompt("SPOKE_VOICE", "Hello Twippy!")
        assertTrue(prompt.contains("Human said: \"Hello Twippy!\""))
        assertTrue(prompt.contains("Spoken English only"))
        assertTrue(prompt.contains("Return ONLY a single valid raw JSON object"))
    }

    @Test
    fun `parseFelineResponse sanitizes stray non-ASCII glitch tokens from Gemma output`() {
        val raw = """{"speech":"Helloஸில்! I am ready to explore whatever comes next!","vocalization":"HAPPY_CHIRP","mood":"HAPPY"}"""
        val directive = engine.parseFelineResponse(raw, 110L, "WAVE")

        assertTrue("Should contain cleaned greeting", directive.speech.startsWith("Hello!"))
        assertTrue("Should contain valid English words", directive.speech.contains("ready to explore"))
        assertFalse("Must not contain non-ASCII glitch characters", directive.speech.contains("ஸில்"))
        assertEquals("GEMMA_4", directive.source)
    }

    @Test
    fun `parseFelineResponse strips thought tags cleanly`() {
        val raw = """<thought>The user is waving, be cute and energetic</thought>{"speech":"Hi there human friend!","mood":"HAPPY"}"""
        val directive = engine.parseFelineResponse(raw, 90L, "WAVE")

        assertEquals("Hi there human friend!", directive.speech)
        assertEquals(FelineMood.HAPPY, directive.mood)
        assertEquals("GEMMA_4", directive.source)
    }

    @Test
    fun `Gemma-only mode never returns empty speech even if model fails`() {
        engine.updateSliders(com.teethut.twiprobot.domain.model.CatPersonalitySliders(allowCannedPresets = false))
        val emptyDirective = engine.parseFelineResponse("", 10L, "PET_EAR")

        assertTrue("Speech must not be empty in Gemma-only mode", emptyDirective.speech.isNotBlank())
        assertEquals("GEMMA_FALLBACK", emptyDirective.source)
    }
}
