/*
 * Wiki — OpenAI-compatible cloud runtime (SSE streaming, fetch models).
 * Tanpa dependensi baru: HttpURLConnection + kotlinx.serialization + Coroutines.
 */
package com.google.ai.edge.gallery.runtime.cloud

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.cloud.WikiProviderConfig
import com.google.ai.edge.gallery.runtime.CleanUpListener
import com.google.ai.edge.gallery.runtime.LlmModelHelper
import com.google.ai.edge.gallery.runtime.ResultListener
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolProvider
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

  suspend fun fetchModels(cfg: WikiProviderConfig): List<String> = impl.fetchModels(cfg)
}

class WikiCloudLlmHelperImpl(
  private var configProvider: () -> WikiProviderConfig? = { WikiCloudRuntime.activeConfig() }
) : LlmModelHelper {
  private val history = mutableListOf<WikiChatMessage>()
  private var job: Job? = null
  @Volatile private var activeConn: HttpURLConnection? = null
  @Volatile private var systemPrompt: String = ""
  private val json = Json { ignoreUnknownKeys = true }

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
          setRequestProperty("Accept", "text/event-stream")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        activeConn = conn
        try {
        if (conn.responseCode !in 200..299) {
          val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
          withContext(Dispatchers.Main) { onError("Wiki cloud error: $err") }
          return@launch
        }
        val full = StringBuilder()
        if (!cfg.streaming) {
          val text = conn.inputStream.bufferedReader().readText()
          try {
            val obj = json.parseToJsonElement(text).jsonObject
            val content = obj["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
              ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
            if (content.isNotEmpty()) {
              full.append(content)
              withContext(Dispatchers.Main) { resultListener(content, false, null) }
            }
          } catch (_: Exception) {
            withContext(Dispatchers.Main) { onError("Wiki cloud error: respons non-stream tak terbaca") }
            return@launch
          }
          history.add(WikiChatMessage("assistant", full.toString()))
          withContext(Dispatchers.Main) { resultListener("", true, null) }
          return@launch
        }
        BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
          var line: String?
          while (reader.readLine().also { line = it } != null) {
            val l = line!!.trim()
            if (l.isEmpty()) continue
            if (!l.startsWith("data:")) continue
            val data = l.removePrefix("data:").trim()
            if (data == "[DONE]") break
            try {
              val obj = json.parseToJsonElement(data).jsonObject
              val delta = obj["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: obj["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                  ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: ""
              if (delta.isNotEmpty()) {
                full.append(delta)
                withContext(Dispatchers.Main) { resultListener(delta, false, null) }
              }
            } catch (_: Exception) { /* abaikan chunk non-JSON */ }
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
        withContext(Dispatchers.Main) { onError(e.message ?: "Wiki cloud error") }
      }
    }
  }

  /** Fetch model list: GET {baseUrl}/models -> List<String>. OpenAI SDK compatible. */
  suspend fun fetchModels(cfg: WikiProviderConfig): List<String> = withContext(Dispatchers.IO) {
    if (cfg.apiKey.isBlank()) return@withContext emptyList()
    val url = URL(cfg.baseUrl.trimEnd('/') + "/models")
    val conn = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 15_000
      readTimeout = 30_000
      setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
    }
    if (conn.responseCode !in 200..299) return@withContext emptyList()
    val text = conn.inputStream.bufferedReader().readText()
    try {
      val arr = json.parseToJsonElement(text).jsonObject["data"]?.jsonArray ?: return@withContext emptyList()
      arr.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
    } catch (_: Exception) { emptyList() }
  }
}
