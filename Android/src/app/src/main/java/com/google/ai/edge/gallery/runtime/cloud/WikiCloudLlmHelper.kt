/*
 * Wiki — OpenAI-compatible cloud runtime (SSE streaming, fetch models).
 * Tanpa dependensi baru: HttpURLConnection + kotlinx.serialization + Coroutines.
 */
package com.google.ai.edge.gallery.runtime.cloud

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.cloud.WikiCloudRuntime
import com.google.ai.edge.gallery.data.cloud.WikiProviderConfig
import com.google.ai.edge.gallery.data.cloud.WikiProviderId
import com.google.ai.edge.gallery.runtime.CleanUpListener
import com.google.ai.edge.gallery.runtime.LlmModelHelper
import com.google.ai.edge.gallery.runtime.ResultListener
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolProvider
import java.io.BufferedReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable data class WikiChatMessage(val role: String, val content: String)
@Serializable
data class WikiChatRequest(
  val model: String,
  val messages: List<WikiChatMessage>,
  val stream: Boolean = true,
  val temperature: Double = 0.7,
)

/**
 * Wiki cloud runtime singleton, dipakai melalui `Model.runtimeHelper`.
 *
 * Menyimpan system prompt (dari Agent Skills: skills + MCP + system instruction) dan meneruskannya
 * sebagai pesan `system` pada setiap request OpenAI-compatible.
 */
object WikiCloudLlmHelper : LlmModelHelper {
  private val impl = WikiCloudLlmHelperImpl()

  @Volatile private var systemPrompt: String = ""

  fun setSystemPrompt(prompt: String) {
    systemPrompt = prompt
  }

  fun getSystemPrompt(): String = systemPrompt

  override fun initialize(
    context: Context,
    model: Model,
    taskId: String,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
    initialMessages: List<Message>,
    coroutineScope: CoroutineScope?,
    enableResponseFormat: Boolean,
  ) {
    systemInstruction?.toString()?.let { if (it.isNotBlank()) setSystemPrompt(it) }
    onDone("")
  }

  override fun resetConversation(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
    initialMessages: List<Message>,
    enableResponseFormat: Boolean,
  ) {
    systemInstruction?.toString()?.let { if (it.isNotBlank()) setSystemPrompt(it) }
    impl.resetConversation(
      model = model,
      supportImage = supportImage,
      supportAudio = supportAudio,
      systemInstruction = systemInstruction,
      tools = tools,
      enableConversationConstrainedDecoding = enableConversationConstrainedDecoding,
      initialMessages = initialMessages,
      enableResponseFormat = enableResponseFormat,
    )
  }

  override fun cleanUp(model: Model, onDone: () -> Unit) = impl.cleanUp(model, onDone)

  override fun stopResponse(model: Model) = impl.stopResponse(model)

  override fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit,
    images: List<Bitmap>,
    audioClips: List<ByteArray>,
    coroutineScope: CoroutineScope?,
    extraContext: Map<String, String>,
    responseJsonSchema: String?,
  ) {
    impl.setSystemPrompt(systemPrompt)
    impl.runInference(
      model = model,
      input = input,
      resultListener = resultListener,
      cleanUpListener = cleanUpListener,
      onError = onError,
      images = images,
      audioClips = audioClips,
      coroutineScope = coroutineScope,
      extraContext = extraContext,
      responseJsonSchema = responseJsonSchema,
    )
  }

  suspend fun fetchModels(cfg: WikiProviderConfig): Result<List<String>> = impl.fetchModels(cfg)
}

class WikiCloudLlmHelperImpl(
  private var configProvider: () -> WikiProviderConfig? = { WikiCloudRuntime.activeConfig() }
) : LlmModelHelper {
  private val history = mutableListOf<WikiChatMessage>()
  private var job: Job? = null
  @Volatile private var activeConn: HttpURLConnection? = null
  @Volatile private var systemPrompt: String = ""
  private val json = Json {
    ignoreUnknownKeys = true
    // Penting: encodeDefaults=true supaya "stream": true benar-benar terkirim ke provider.
    // Tanpa ini, provider membalas non-stream dan UI tidak pernah menerima token.
    encodeDefaults = true
  }

  /** System prompt yang dipakai sebagai pesan `system` (dari Agent Skills: skills + MCP). */
  fun setSystemPrompt(prompt: String) {
    systemPrompt = prompt
  }

  override fun initialize(
    context: Context,
    model: Model,
    taskId: String,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
    initialMessages: List<Message>,
    coroutineScope: CoroutineScope?,
    enableResponseFormat: Boolean,
  ) {
    history.clear()
    onDone("")
  }

  override fun resetConversation(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
    initialMessages: List<Message>,
    enableResponseFormat: Boolean,
  ) {
    history.clear()
  }

  override fun cleanUp(model: Model, onDone: () -> Unit) {
    job?.cancel()
    try { activeConn?.disconnect() } catch (_: Exception) {}
    activeConn = null
    history.clear()
    onDone()
  }

  override fun stopResponse(model: Model) {
    job?.cancel()
    try { activeConn?.disconnect() } catch (_: Exception) {}
  }

  override fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (String) -> Unit,
    images: List<Bitmap>,
    audioClips: List<ByteArray>,
    coroutineScope: CoroutineScope?,
    extraContext: Map<String, String>,
    responseJsonSchema: String?,
  ) {
    val scope = coroutineScope ?: CoroutineScope(Dispatchers.IO)
    job?.cancel()
    job = scope.launch(Dispatchers.IO) {
      try {
        val cfg = configProvider() ?: run {
          withContext(Dispatchers.Main) { onError("Wiki: provider belum dikonfigurasi (API key / model kosong).") }
          return@launch
        }
        if (cfg.apiKey.isBlank() || cfg.modelId.isBlank()) {
          withContext(Dispatchers.Main) { onError("Wiki: isi API key dan pilih model dulu di Settings.") }
          return@launch
        }
        history.add(WikiChatMessage("user", input))
        val msgs =
          buildList {
            if (systemPrompt.isNotBlank()) add(WikiChatMessage("system", systemPrompt))
            addAll(history)
          }
        val req = WikiChatRequest(model = cfg.modelId, messages = msgs, stream = cfg.streaming)
        val body = json.encodeToString(WikiChatRequest.serializer(), req)
        val url = URL(cfg.baseUrl.trimEnd('/') + "/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 30_000
          readTimeout = 120_000
          doOutput = true
          setRequestProperty("Content-Type", "application/json")
          setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
          setRequestProperty("Accept", "text/event-stream, application/json")
          setRequestProperty("User-Agent", "Wiki-Android/1.0")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        activeConn = conn
        try {
        val code = conn.responseCode
        if (code !in 200..299) {
          val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
          rollbackLastUserMessage(input)
          withContext(Dispatchers.Main) { onError("Wiki cloud error: $err") }
          return@launch
        }
        val full = StringBuilder()
        val contentType = conn.contentType.orEmpty()
        val sseStream =
          cfg.streaming && contentType.contains("text/event-stream", ignoreCase = true)
        if (!sseStream) {
          // Non-stream, atau server mengabaikan "stream": baca seluruh body.
          val text = conn.inputStream.bufferedReader().readText()
          val parsed = parseNonStreamResponse(text)
          if (parsed != null) {
            val (content, reasoning) = parsed
            if (reasoning.isNotEmpty()) {
              withContext(Dispatchers.Main) { resultListener("", false, reasoning) }
            }
            if (content.isNotEmpty()) {
              full.append(content)
              withContext(Dispatchers.Main) { resultListener(content, false, null) }
            }
          } else if (text.contains("data:")) {
            // Server mengirim SSE walau Content-Type tidak menyebutkannya.
            consumeSseStream(BufferedReader(StringReader(text)), full, resultListener)
          } else {
            rollbackLastUserMessage(input)
            withContext(Dispatchers.Main) { onError("Wiki cloud error: respons non-stream tak terbaca") }
            return@launch
          }
        } else {
          val rawFallback = consumeSseStream(conn.inputStream.bufferedReader(), full, resultListener)
          if (full.isEmpty()) {
            // Sebagian server membalas JSON biasa walau diminta stream.
            parseNonStreamResponse(rawFallback)?.let { (content, reasoning) ->
              if (reasoning.isNotEmpty()) {
                withContext(Dispatchers.Main) { resultListener("", false, reasoning) }
              }
              if (content.isNotEmpty()) {
                full.append(content)
                withContext(Dispatchers.Main) { resultListener(content, false, null) }
              }
            }
          }
        }
        history.add(WikiChatMessage("assistant", full.toString()))
        withContext(Dispatchers.Main) { resultListener("", true, null) }
        } finally {
          try { conn.disconnect() } catch (_: Exception) {}
          if (activeConn === conn) activeConn = null
        }
      } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) return@launch
        rollbackLastUserMessage(input)
        withContext(Dispatchers.Main) { onError(e.message ?: "Wiki cloud error") }
      }
    }
  }

  /** Buang pesan user terakhir setelah kegagalan, supaya percakapan tidak menyimpan giliran gagal. */
  private fun rollbackLastUserMessage(input: String) {
    val last = history.lastOrNull()
    if (last != null && last.role == "user" && last.content == input) {
      history.removeAt(history.lastIndex)
    }
  }

  /** Parse respons JSON biasa (non-stream): pasangan (content, reasoning), atau null bila rusak. */
  private fun parseNonStreamResponse(text: String): Pair<String, String>? =
    try {
      val obj = json.parseToJsonElement(text).jsonObject
      val message = obj["choices"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("message")?.jsonObject
      val content = message?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
      val reasoning =
        message?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
          ?: message?.get("reasoning")?.jsonPrimitive?.contentOrNull
          ?: ""
      if (content.isEmpty() && reasoning.isEmpty()) null else content to reasoning
    } catch (_: Exception) {
      null
    }

  /**
   * Baca aliran SSE: teruskan konten & reasoning (CoT) ke [resultListener]. Mengembalikan
   * baris non-SSE (kalau ada) sebagai fallback untuk respons JSON biasa.
   */
  private suspend fun consumeSseStream(
    reader: BufferedReader,
    full: StringBuilder,
    resultListener: ResultListener,
  ): String {
    val rawFallback = StringBuilder()
    reader.use { r ->
      var line: String?
      while (r.readLine().also { line = it } != null) {
        val l = line!!.trim()
        if (l.isEmpty()) continue
        if (!l.startsWith("data:")) {
          rawFallback.append(l)
          continue
        }
        val data = l.removePrefix("data:").trim()
        if (data == "[DONE]") break
        try {
          val obj = json.parseToJsonElement(data).jsonObject
          val choice = obj["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
          val chunk = choice?.get("delta")?.jsonObject ?: choice?.get("message")?.jsonObject
          val reasoning =
            chunk?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
              ?: chunk?.get("reasoning")?.jsonPrimitive?.contentOrNull
              ?: ""
          val delta = chunk?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
          if (reasoning.isNotEmpty()) {
            withContext(Dispatchers.Main) { resultListener("", false, reasoning) }
          }
          if (delta.isNotEmpty()) {
            full.append(delta)
            withContext(Dispatchers.Main) { resultListener(delta, false, null) }
          }
        } catch (_: Exception) { /* abaikan chunk non-JSON */ }
      }
    }
    return rawFallback.toString()
  }

  /**
   * Fetch model list. OpenAI-compatible: GET {baseUrl}/models.
   *
   * Khusus Gemini memakai endpoint native /v1beta/models karena daftar model lewat basis
   * OpenAI-compat tidak selalu tersedia.
   */
  suspend fun fetchModels(cfg: WikiProviderConfig): Result<List<String>> =
    withContext(Dispatchers.IO) {
      try {
        val base = cfg.baseUrl.trim().trimEnd('/')
        val geminiNative = cfg.id == WikiProviderId.GEMINI && cfg.apiKey.isNotBlank()
        val url =
          if (geminiNative) {
            URL("https://generativelanguage.googleapis.com/v1beta/models?key=${cfg.apiKey}")
          } else {
            URL("$base/models")
          }
        val conn =
          (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            if (!geminiNative && cfg.apiKey.isNotBlank()) {
              setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            }
          }
        val code = conn.responseCode
        if (code !in 200..299) {
          val err = conn.errorStream?.bufferedReader()?.readText()?.take(260)?.trim().orEmpty()
          return@withContext Result.failure(RuntimeException("HTTP $code ${err}".trim()))
        }
        val text = conn.inputStream.bufferedReader().readText()
        val root = json.parseToJsonElement(text).jsonObject
        val ids = mutableListOf<String>()
        root["data"]?.jsonArray?.forEach { el ->
          el.jsonObject["id"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) ids.add(it) }
        }
        if (ids.isEmpty()) {
          // Gemini native: { "models": [{ "name": "models/gemini-2.5-flash" }] }
          root["models"]?.jsonArray?.forEach { el ->
            el.jsonObject["name"]?.jsonPrimitive?.content?.let { name ->
              val id = name.removePrefix("models/")
              if (id.isNotBlank() && !id.contains("embedding") && !id.contains("aqa")) {
                ids.add(id)
              }
            }
          }
        }
        Result.success(ids.distinct().sorted())
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
}
