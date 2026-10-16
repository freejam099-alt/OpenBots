/*
 * Wiki — Panel aksi model cloud di daftar model: tanpa tombol unduh,
 * hanya status konfigurasi + tombol Chat / Atur Provider.
 */
package com.google.ai.edge.gallery.ui.common.modelitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.cloud.WikiCloudRuntime

/** Panel aksi model cloud: status konfigurasi + tombol Chat/Atur Provider (tanpa unduhan). */
@Composable
fun WikiCloudModelPanel(model: Model, onAction: () -> Unit, modifier: Modifier = Modifier) {
  val version by WikiCloudRuntime.version.collectAsState()
  val cfg = remember(version) { WikiCloudRuntime.activeConfig() }
  val configured = cfg != null && cfg.apiKey.isNotBlank() && cfg.modelId.isNotBlank()
  Column(
    modifier = modifier.fillMaxWidth().padding(top = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = if (configured) Icons.Rounded.CheckCircle else Icons.Rounded.Cloud,
        contentDescription = null,
        tint =
          if (configured) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (configured) "Siap dipakai" else "Belum diatur",
          style = MaterialTheme.typography.labelLarge,
          color =
            if (configured) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text =
            if (configured && cfg != null) "${cfg.id.title} · ${cfg.modelId}"
            else "Tekan Atur Provider: isi API key, Fetch Model, lalu Simpan.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
      Icon(
        imageVector = if (configured) Icons.Rounded.SmartToy else Icons.Rounded.Tune,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(if (configured) "Chat" else "Atur Provider")
    }
  }
}
