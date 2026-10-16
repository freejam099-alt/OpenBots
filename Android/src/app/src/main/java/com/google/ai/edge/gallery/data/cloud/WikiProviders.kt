/*
 * Wiki — Cloud provider definitions + penyimpanan pengaturan provider.
 * OpenAI SDK compatible: OpenRouter, Groq, Gemini, DeepSeek, MiniMax + Custom endpoint.
 */
package com.google.ai.edge.gallery.data.cloud

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class WikiProviderId(val title: String, val icon: ImageVector) {
  OPEN_ROUTER("OpenRouter", Icons.Rounded.Cloud),
  GROQ("Groq", Icons.Rounded.Bolt),
  GEMINI("Gemini", Icons.Rounded.AutoAwesome),
  DEEPSEEK("DeepSeek", Icons.Rounded.Psychology),
  MINIMAX("MiniMax", Icons.Rounded.SmartToy),
  CUSTOM("Custom OpenAI", Icons.Rounded.Tune),
}

@Serializable
data class WikiProviderConfig(
  val id: WikiProviderId,
  val baseUrl: String,
  val apiKey: String = "",
  val modelId: String = "",
  val streaming: Boolean = true,
)

object WikiProviders {
  const val WIKI_APP_NAME = "Wiki"
  // Wiki default MCP: dipakai sebagai MCP bawaan di Agent Skills.
  const val WIKI_DEFAULT_MCP_URL = "https://mapstools.googleapis.com/mcp"
  const val WIKI_DEFAULT_MCP_NAME = "Wiki Maps MCP"

  fun defaults(): List<WikiProviderConfig> =
    listOf(
      WikiProviderConfig(WikiProviderId.OPEN_ROUTER, "https://openrouter.ai/api/v1"),
      WikiProviderConfig(WikiProviderId.GROQ, "https://api.groq.com/openai/v1"),
      WikiProviderConfig(
        WikiProviderId.GEMINI,
        "https://generativelanguage.googleapis.com/v1beta/openai",
      ),
      WikiProviderConfig(WikiProviderId.DEEPSEEK, "https://api.deepseek.com/v1"),
      WikiProviderConfig(WikiProviderId.MINIMAX, "https://api.minimax.io/v1"),
      WikiProviderConfig(WikiProviderId.CUSTOM, "https://api.openai.com/v1"),
    )
}

/**
 * Penyimpanan pengaturan provider cloud (SharedPreferences + kotlinx.serialization).
 *
 * Dipakai supaya Base URL / API key / model hasil Fetch tidak hilang saat app ditutup.
 */
object WikiCloudSettingsStore {
  private const val PREFS_NAME = "wiki_cloud_settings"
  private const val KEY_CONFIGS = "configs"
  private const val KEY_ACTIVE = "active_provider"
  private val json = Json { ignoreUnknownKeys = true }
  private val serializer = ListSerializer(WikiProviderConfig.serializer())

  fun load(context: Context): Pair<List<WikiProviderConfig>, WikiProviderId>? {
    return try {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val raw = prefs.getString(KEY_CONFIGS, null) ?: return null
      val configs = json.decodeFromString(serializer, raw)
      if (configs.isEmpty()) return null
      val active =
        prefs
          .getString(KEY_ACTIVE, null)
          ?.let { runCatching { WikiProviderId.valueOf(it) }.getOrNull() }
          ?: WikiProviderId.OPEN_ROUTER
      configs to active
    } catch (_: Exception) {
      null
    }
  }

  fun save(context: Context, configs: List<WikiProviderConfig>, activeProviderId: WikiProviderId) {
    try {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      prefs
        .edit()
        .putString(KEY_CONFIGS, json.encodeToString(serializer, configs))
        .putString(KEY_ACTIVE, activeProviderId.name)
        .apply()
    } catch (_: Exception) {
      // Abaikan kegagalan penyimpanan: pengaturan tetap dipakai in-memory.
    }
  }
}

/**
 * Wiki cloud runtime: memegang konfigurasi provider aktif yang dipakai oleh
 * [com.google.ai.edge.gallery.runtime.cloud.WikiCloudLlmHelper] saat chat berjalan.
 *
 * [version] naik setiap pengaturan berubah, supaya UI (kartu model & layar pengaturan) ikut
 * menyegarkan diri.
 */
object WikiCloudRuntime {
  @Volatile
  var configs: List<WikiProviderConfig> = WikiProviders.defaults()
    private set

  @Volatile
  var activeProviderId: WikiProviderId = WikiProviderId.OPEN_ROUTER
    private set

  private val _version = MutableStateFlow(0)
  val version: StateFlow<Int> = _version.asStateFlow()

  fun activeConfig(): WikiProviderConfig? =
    configs.find { it.id == activeProviderId } ?: configs.firstOrNull()

  /** True jika provider aktif sudah punya API key + model (siap dipakai chat). */
  fun isConfigured(): Boolean {
    val cfg = activeConfig() ?: return false
    return cfg.apiKey.isNotBlank() && cfg.modelId.isNotBlank()
  }

  fun isActive(id: WikiProviderId): Boolean = activeProviderId == id

  /** Muat pengaturan tersimpan (dipanggil sekali saat app start). */
  fun load(context: Context) {
    WikiCloudSettingsStore.load(context)?.let { (saved, active) ->
      configs = ensureAllProviders(saved)
      activeProviderId = active
      _version.value = _version.value + 1
    }
  }

  /** Simpan pengaturan ke disk. */
  fun persist(context: Context) = WikiCloudSettingsStore.save(context, configs, activeProviderId)

  fun set(newConfigs: List<WikiProviderConfig>) {
    configs = ensureAllProviders(newConfigs)
    _version.value = _version.value + 1
  }

  fun update(cfg: WikiProviderConfig) {
    set(configs.map { if (it.id == cfg.id) cfg else it })
  }

  fun select(id: WikiProviderId) {
    activeProviderId = id
    _version.value = _version.value + 1
  }

  private fun ensureAllProviders(list: List<WikiProviderConfig>): List<WikiProviderConfig> =
    WikiProviders.defaults().map { def -> list.find { it.id == def.id } ?: def }
}

/** Penanda instance untuk model cloud (bukan engine lokal). */
object WikiCloudInstance

/** Nama model cloud khusus Wiki. */
const val WIKI_CLOUD_MODEL_NAME = "WikiCloud"

/**
 * Membuat model cloud Wiki (OpenAI SDK compatible). Tidak diunduh, tidak butuh native engine.
 */
fun createWikiCloudModel(): com.google.ai.edge.gallery.data.Model =
  com.google.ai.edge.gallery.data.Model(
    name = WIKI_CLOUD_MODEL_NAME,
    displayName = "Wiki Cloud",
    info = "Cloud chat via provider OpenAI-compatible: OpenRouter, Groq, Gemini, DeepSeek, MiniMax, Custom.",
    backendSpec =
      com.google.ai.edge.gallery.data.BackendSpec(
        runtimeType = com.google.ai.edge.gallery.data.RuntimeType.CLOUD,
        accelerators = listOf(com.google.ai.edge.gallery.data.Accelerator.CPU),
      ),
    llmProfile = com.google.ai.edge.gallery.data.LlmProfile(maxTokens = 8192),
    bestForTaskIds =
      listOf(com.google.ai.edge.gallery.data.BuiltInTaskId.LLM_AGENT_CHAT),
  )
