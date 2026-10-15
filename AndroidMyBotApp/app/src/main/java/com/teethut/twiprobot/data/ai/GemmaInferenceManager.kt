package com.teethut.twiprobot.data.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages on-device Gemma 4 LLM inference using Google LiteRT-LM.
 * Directly supports .litertlm model files with Adreno GPU acceleration on Galaxy S24 Ultra.
 */
class GemmaInferenceManager(private val context: Context) {

    companion object {
        @Volatile
        private var instance: GemmaInferenceManager? = null

        fun getInstance(context: Context): GemmaInferenceManager {
            return instance ?: synchronized(this) {
                instance ?: GemmaInferenceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val initMutex = Mutex()
    private val inferenceMutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var savedConversationConfig: ConversationConfig? = null

    @Volatile
    var isModelLoaded: Boolean = false
        private set

    var activeBackend: String = "GPU"
        private set

    /**
     * Resets the conversation context to a clean, fresh state without reloading model weights.
     * Uses a fresh random seed each time so the robot never loops on identical sentences.
     */
    fun resetConversation() {
        synchronized(this) {
            val eng = engine ?: return
            val cfg = savedConversationConfig ?: return
            try {
                conversation?.close()
            } catch (_: Exception) {}
            try {
                val freshSeed = kotlin.random.Random.nextInt(1, 1000000)
                val updatedConfig = cfg.copy(
                    samplerConfig = cfg.samplerConfig?.copy(seed = freshSeed)
                )
                conversation = eng.createConversation(updatedConfig)
                android.util.Log.d("GemmaInference", "Reset conversation context with dynamic seed: $freshSeed")
            } catch (e: Exception) {
                android.util.Log.w("GemmaInference", "Failed to reset conversation: ${e.message}", e)
            }
        }
    }

    /**
     * Finds standard model candidates in external storage, downloads, or app directories.
     * Dynamically searches for any .litertlm, .tflite, or .bin model file.
     */
    fun findModelFile(): File? {
        val searchDirs = listOfNotNull(
            File("/storage/emulated/0/Download"),
            File("/sdcard/Download"),
            context.getExternalFilesDir(null),
            context.filesDir,
            File("/sdcard/TWIP/models")
        )

        // 1. First look for smaller/faster models (2B, 1B, SmolLM) or standard Gemma 4
        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val matched = dir.listFiles { f ->
                val name = f.name.lowercase()
                f.isFile && (name.endsWith(".litertlm") || name.endsWith(".bin") || name.endsWith(".tflite"))
            } ?: emptyArray()

            // Sort by preference: 1B/2B first, then 4B, then largest
            val best = matched.sortedWith(
                compareBy<File> { file ->
                    val n = file.name.lowercase()
                    when {
                        n.contains("1b") || n.contains("2b") || n.contains("smol") -> 0
                        n.contains("gemma") -> 1
                        else -> 2
                    }
                }.thenByDescending { it.length() }
            ).firstOrNull()

            if (best != null && best.length() > 50L * 1024 * 1024) {
                android.util.Log.i("GemmaInference", "Discovered LLM model: ${best.name} (${best.length() / (1024 * 1024)} MB)")
                return best
            }
        }
        return null
    }

    /**
     * Initializes the Gemma 4 engine using a local .litertlm model file.
     * Must be called on a background dispatcher.
     */
    suspend fun initModel(
        modelPath: String,
        maxTokens: Int = 200
    ): Result<Unit> = withContext(Dispatchers.IO) {
        initMutex.withLock {
            runCatching {
                if (isModelLoaded && engine != null && conversation != null) {
                    android.util.Log.i("GemmaInference", "Gemma 4 model already loaded and ready ($activeBackend)")
                    return@runCatching
                }

                val file = File(modelPath)
                if (!file.exists()) {
                    throw IllegalArgumentException("Gemma 4 model file not found at: $modelPath")
                }

                closeInternal() // release any previous instance under lock

                // Try GPU backend first (S24 Ultra Adreno), fallback to CPU if OpenCL vendor library is restricted
                val newEngine = try {
                    val gpuConfig = EngineConfig(
                        modelPath = file.absolutePath,
                        backend = Backend.GPU()
                    )
                    Engine(gpuConfig).also {
                        it.initialize()
                        activeBackend = "GPU (Adreno)"
                    }
                } catch (gpuError: Throwable) {
                    android.util.Log.w("GemmaInference", "GPU init failed, falling back to CPU", gpuError)
                    val cpuConfig = EngineConfig(
                        modelPath = file.absolutePath,
                        backend = Backend.CPU()
                    )
                    Engine(cpuConfig).also {
                        it.initialize()
                        activeBackend = "CPU (Fallback)"
                    }
                }

                // Focused sampling prevents out-of-distribution non-ASCII tokens while keeping variety
                val initialSeed = kotlin.random.Random.nextInt(1, 1000000)
                val samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
                    topK = 20,
                    topP = 0.85,
                    temperature = 0.50,
                    seed = initialSeed
                )

                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(
                        """
                        You are Twippy, an affectionate and clever cyber-cat robot companion on two wheels.
                        Language: Speak strictly in clear, charming English using standard Latin alphabet letters.
                        Never output non-Latin script, CJK, emojis, or raw markdown.
                        Keep responses concise, cheerful, direct, and playful so they sound great when spoken aloud.
                        """.trimIndent()
                    ),
                    samplerConfig = samplerConfig,
                    maxOutputToken = maxTokens
                )

                synchronized(this@GemmaInferenceManager) {
                    savedConversationConfig = conversationConfig
                    conversation = newEngine.createConversation(conversationConfig)
                    engine = newEngine
                    isModelLoaded = true
                }
                android.util.Log.i("GemmaInference", "Gemma 4 model successfully initialized on $activeBackend")
            }
        }
    }

    /**
     * Synchronously generates a full response text for a given prompt.
     */
    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        inferenceMutex.withLock {
            runCatching {
                val conv = conversation ?: throw IllegalStateException("Gemma 4 model is not initialized")
                val sb = StringBuilder()
                conv.sendMessageAsync(prompt).collect { message ->
                    for (content in message.contents.contents) {
                        if (content is Content.Text) {
                            sb.append(content.text)
                        }
                    }
                }
                sb.toString()
            }
        }
    }

    /**
     * Asynchronously streams generated tokens as they are produced by the Gemma 4 engine.
     */
    fun streamResponse(prompt: String): Flow<String> = flow {
        inferenceMutex.withLock {
            val conv = conversation ?: throw IllegalStateException("Gemma 4 model is not initialized")
            conv.sendMessageAsync(prompt).collect { message ->
                val text = buildString {
                    for (content in message.contents.contents) {
                        if (content is Content.Text) {
                            append(content.text)
                        }
                    }
                }
                if (text.isNotEmpty()) {
                    emit(text)
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Releases native memory and model weights allocated by LiteRT-LM.
     */
    fun close() {
        synchronized(this) {
            closeInternal()
        }
    }

    private fun closeInternal() {
        try {
            conversation?.close()
            engine?.close()
        } catch (_: Exception) {
        } finally {
            conversation = null
            savedConversationConfig = null
            engine = null
            isModelLoaded = false
        }
    }
}
