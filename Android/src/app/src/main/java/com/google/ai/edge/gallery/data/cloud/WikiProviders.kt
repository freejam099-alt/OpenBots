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
