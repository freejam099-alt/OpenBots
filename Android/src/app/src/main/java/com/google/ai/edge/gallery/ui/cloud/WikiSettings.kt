/*
 * Wiki — Settings container Material3: Gen Card per provider (rounded icon),
 * custom Base URL + API key + fetch model + streaming toggle.
 */
package com.google.ai.edge.gallery.ui.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.cloud.WikiProviderConfig
import com.google.ai.edge.gallery.data.cloud.WikiProviders
import com.google.ai.edge.gallery.runtime.cloud.WikiCloudLlmHelper
import kotlinx.coroutines.launch

@Composable
fun WikiProviderGenCard(
  config: WikiProviderConfig,
  models: List<String>,
  loading: Boolean,
  onConfigChange: (WikiProviderConfig) -> Unit,
  onFetch: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
          modifier = Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Icon(config.id.icon, contentDescription = config.id.title, modifier = Modifier.padding(10.dp))
        }
        Column(Modifier.weight(1f)) {
          Text(config.id.title, style = MaterialTheme.typography.titleMedium)
          Text(config.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp)) else IconButton(onClick = onFetch) {
          Icon(Icons.Rounded.Download, contentDescription = "Fetch model")
        }
      }
      OutlinedTextField(config.baseUrl, { onConfigChange(config.copy(baseUrl = it)) }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
      OutlinedTextField(config.apiKey, { onConfigChange(config.copy(apiKey = it)) }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
      OutlinedTextField(config.modelId, { onConfigChange(config.copy(modelId = it)) }, label = { Text("Model (atau hasil Fetch)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
      if (models.isNotEmpty()) Text(models.take(8).joinToString(", "), style = MaterialTheme.typography.bodySmall)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Streaming", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(config.streaming, { onConfigChange(config.copy(streaming = it)) })
      }
    }
  }
}

@Composable
fun WikiSettingsContainer(
  helper: WikiCloudLlmHelper? = null,
  initial: List<WikiProviderConfig> = WikiProviders.defaults(),
  onConfigsChange: (List<WikiProviderConfig>) -> Unit = {},
) {
  var configs by remember { mutableStateOf(initial) }
  var loadingId by remember { mutableStateOf<String?>(null) }
  var cache by remember { mutableStateOf(mapOf<String, List<String>>()) }
  val scope = rememberCoroutineScope()
  // Column (bukan Lazy) agar aman di dalam SettingsDialog yang sudah scrollable.
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    configs.forEach { cfg ->
      WikiProviderGenCard(
        config = cfg,
        models = cache[cfg.id.name].orEmpty(),
        loading = loadingId == cfg.id.name,
        onConfigChange = { next ->
          configs = configs.map { if (it.id == cfg.id) next else it }
          onConfigsChange(configs)
        },
        onFetch = {
          loadingId = cfg.id.name
          scope.launch {
            val list = try { helper?.fetchModels(cfg).orEmpty() } catch (_: Exception) { emptyList() }
            cache = cache + (cfg.id.name to list)
            if (list.isNotEmpty() && cfg.modelId.isBlank()) {
              val next = cfg.copy(modelId = list.first())
              configs = configs.map { if (it.id == cfg.id) next else it }
              onConfigsChange(configs)
            }
            loadingId = null
          }
        },
      )
    }
  }
}
