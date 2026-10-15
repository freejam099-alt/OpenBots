/*
 * Wiki — Cloud provider definitions.
 * OpenAI SDK compatible: OpenRouter, Groq, Gemini, DeepSeek, MiniMax + Custom endpoint.
 */
package com.google.ai.edge.gallery.data.cloud

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class WikiProviderId(val title: String, val icon: ImageVector) {
  OPEN_ROUTER("OpenRouter", Icons.Rounded.Cloud),
  GROQ("Groq", Icons.Rounded.Bolt),
  GEMINI("Gemini", Icons.Rounded.AutoAwesome),
  DEEPSEEK("DeepSeek", Icons.Rounded.Psychology),
  MINIMAX("MiniMax", Icons.Rounded.SmartToy),
  CUSTOM("Custom OpenAI", Icons.Rounded.Tune),
}

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

  fun defaults(): List<WikiProviderConfig> = listOf(
    WikiProviderConfig(WikiProviderId.OPEN_ROUTER, "https://openrouter.ai/api/v1"),
    WikiProviderConfig(WikiProviderId.GROQ, "https://api.groq.com/openai/v1"),
    WikiProviderConfig(WikiProviderId.GEMINI, "https://generativelanguage.googleapis.com/v1beta/openai"),
    WikiProviderConfig(WikiProviderId.DEEPSEEK, "https://api.deepseek.com/v1"),
    WikiProviderConfig(WikiProviderId.MINIMAX, "https://api.minimax.io/v1"),
    WikiProviderConfig(WikiProviderId.CUSTOM, "https://api.openai.com/v1"),
  )
}

/**
 * Wiki cloud runtime: memegang konfigurasi provider aktif (in-memory) yang dipakai oleh
 * [com.google.ai.edge.gallery.runtime.cloud.WikiCloudLlmHelper] saat chat berjalan.
 */
object WikiCloudRuntime {
  @Volatile var configs: List<WikiProviderConfig> = WikiProviders.defaults()
  @Volatile var activeProviderId: WikiProviderId = WikiProviderId.OPEN_ROUTER

  fun activeConfig(): WikiProviderConfig? =
    configs.find { it.id == activeProviderId } ?: configs.firstOrNull()

  fun update(cfg: WikiProviderConfig) {
    configs = configs.map { if (it.id == cfg.id) cfg else it }
  }

  fun select(id: WikiProviderId) {
    activeProviderId = id
  }
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

