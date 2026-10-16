/*
 * Wiki — Pengaturan Cloud: kartu per provider (gaya kartu aplikasi), Fetch daftar model,
 * pilih model dari hasil Fetch, lalu Simpan (persisten ke disk).
 */
package com.google.ai.edge.gallery.ui.cloud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.cloud.WikiCloudRuntime
import com.google.ai.edge.gallery.data.cloud.WikiProviderConfig
import com.google.ai.edge.gallery.data.cloud.WikiProviderId
import com.google.ai.edge.gallery.data.cloud.WikiProviders
import com.google.ai.edge.gallery.runtime.cloud.WikiCloudLlmHelper
import com.google.ai.edge.gallery.ui.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WikiSettingsContainer(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val runtimeVersion by WikiCloudRuntime.version.collectAsState()

  // Draft per provider: perubahan baru tersimpan saat tombol Simpan ditekan.
  var drafts by remember(runtimeVersion) {
    mutableStateOf(WikiCloudRuntime.configs.associateBy { it.id })
  }
  var expandedId by remember(runtimeVersion) {
    mutableStateOf<WikiProviderId?>(WikiCloudRuntime.activeProviderId)
  }
  var fetchingId by remember { mutableStateOf<WikiProviderId?>(null) }
  var fetchErrors by remember { mutableStateOf(mapOf<WikiProviderId, String>()) }
  var fetchedModels by remember { mutableStateOf(mapOf<WikiProviderId, List<String>>()) }
  var savedFlashId by remember { mutableStateOf<WikiProviderId?>(null) }
  var pickerForId by remember { mutableStateOf<WikiProviderId?>(null) }

  LaunchedEffect(savedFlashId) {
    if (savedFlashId != null) {
      delay(2000)
      savedFlashId = null
    }
  }

  fun draftOf(id: WikiProviderId): WikiProviderConfig =
    drafts[id] ?: WikiProviders.defaults().first { it.id == id }

  fun updateDraft(next: WikiProviderConfig) {
    drafts = drafts + (next.id to next)
  }

  fun fetchModelsFor(cfg: WikiProviderConfig) {
    fetchingId = cfg.id
    fetchErrors = fetchErrors - cfg.id
    scope.launch {
      WikiCloudLlmHelper.fetchModels(cfg)
        .onSuccess { list ->
          fetchedModels = fetchedModels + (cfg.id to list)
          if (list.isEmpty()) {
            fetchErrors =
              fetchErrors +
                (cfg.id to "Fetch berhasil, tapi daftar model kosong. Isi kolom Model manual.")
          } else {
            pickerForId = cfg.id
          }
        }
        .onFailure { e ->
          fetchErrors = fetchErrors + (cfg.id to "Gagal fetch: ${e.message ?: "tidak diketahui"}")
        }
      fetchingId = null
    }
  }

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    WikiProviders.defaults().forEachIndexed { index, def ->
      val draft = draftOf(def.id)
      WikiProviderCard(
        providerDefault = def,
        draft = draft,
        index = index,
        isActive = WikiCloudRuntime.isActive(def.id),
        expanded = expandedId == def.id,
        fetching = fetchingId == def.id,
        fetchError = fetchErrors[def.id],
        fetchedCount = fetchedModels[def.id]?.size ?: 0,
        justSaved = savedFlashId == def.id,
        onToggleExpand = { expandedId = if (expandedId == def.id) null else def.id },
        onDraftChange = { updateDraft(it) },
        onFetch = { fetchModelsFor(draft) },
        onOpenPicker = { pickerForId = def.id },
        onSave = {
          WikiCloudRuntime.set(
            WikiCloudRuntime.configs.map { if (it.id == draft.id) draft else it }
          )
          WikiCloudRuntime.select(draft.id)
          WikiCloudRuntime.persist(context)
          savedFlashId = draft.id
        },
      )
    }
    Text(
      "Provider yang disimpan menjadi provider aktif untuk chat Wiki Cloud. Pengaturan tersimpan di perangkat.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  val pickerId = pickerForId
  if (pickerId != null) {
    val models = fetchedModels[pickerId].orEmpty()
    if (models.isNotEmpty()) {
      ModelPickerDialog(
        providerTitle = pickerId.title,
        models = models,
        onSelect = { picked ->
          updateDraft(draftOf(pickerId).copy(modelId = picked))
          pickerForId = null
        },
        onDismiss = { pickerForId = null },
      )
    }
  }
}

@Composable
private fun WikiProviderCard(
  providerDefault: WikiProviderConfig,
  draft: WikiProviderConfig,
  index: Int,
  isActive: Boolean,
  expanded: Boolean,
  fetching: Boolean,
  fetchError: String?,
  fetchedCount: Int,
  justSaved: Boolean,
  onToggleExpand: () -> Unit,
  onDraftChange: (WikiProviderConfig) -> Unit,
  onFetch: () -> Unit,
  onOpenPicker: () -> Unit,
  onSave: () -> Unit,
) {
  val gradients = MaterialTheme.customColors.taskBgGradientColors
  val gradient = gradients[index % gradients.size]
  val configured = draft.apiKey.isNotBlank() && draft.modelId.isNotBlank()

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(
      modifier = Modifier.padding(16.dp).animateContentSize(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(44.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(
                Brush.linearGradient(colors = listOf(gradient.first(), gradient.last()))
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = providerDefault.id.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(providerDefault.id.title, style = MaterialTheme.typography.titleMedium)
            if (isActive) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Provider aktif",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
            }
          }
          Text(
            when {
              isActive && configured -> "Aktif · ${draft.modelId}"
              isActive -> "Aktif · belum lengkap"
              configured -> "Siap dipakai"
              else -> "Belum diatur"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Icon(
          imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
          contentDescription = if (expanded) "Tutup" else "Buka",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = draft.baseUrl,
            onValueChange = { onDraftChange(draft.copy(baseUrl = it)) },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
          )
          var keyVisible by remember { mutableStateOf(false) }
          OutlinedTextField(
            value = draft.apiKey,
            onValueChange = { onDraftChange(draft.copy(apiKey = it)) },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            visualTransformation =
              if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { keyVisible = !keyVisible }) {
                Icon(
                  imageVector =
                    if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                  contentDescription = if (keyVisible) "Sembunyikan API key" else "Lihat API key",
                )
              }
            },
          )
          OutlinedTextField(
            value = draft.modelId,
            onValueChange = { onDraftChange(draft.copy(modelId = it)) },
            label = { Text("Model (hasil Fetch / manual)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            trailingIcon = {
              if (fetchedCount > 0) {
                IconButton(onClick = onOpenPicker) {
                  Icon(
                    imageVector = Icons.Rounded.UnfoldMore,
                    contentDescription = "Pilih dari hasil Fetch ($fetchedCount model)",
                  )
                }
              }
            },
          )
          if (fetchError != null) {
            Text(
              fetchError,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              "Streaming",
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
              checked = draft.streaming,
              onCheckedChange = { onDraftChange(draft.copy(streaming = it)) },
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onFetch, enabled = !fetching) {
              if (fetching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              } else {
                Icon(
                  imageVector = Icons.Rounded.Refresh,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text("Fetch Model")
            }
            Button(onClick = onSave) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Simpan")
            }
          }
          if (justSaved) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                "Tersimpan & jadi provider aktif",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ModelPickerDialog(
  providerTitle: String,
  models: List<String>,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filtered =
    remember(models, query) {
      if (query.isBlank()) models else models.filter { it.contains(query, ignoreCase = true) }
    }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Pilih model — $providerTitle") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          label = { Text("Cari model") },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          singleLine = true,
        )
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
          items(filtered) { modelId ->
            Text(
              modelId,
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { onSelect(modelId) }
                  .padding(vertical = 12.dp, horizontal = 4.dp),
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (filtered.isEmpty()) {
            item {
              Text(
                "Tidak ada model yang cocok. Ketik manual di kolom Model.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
  )
}
