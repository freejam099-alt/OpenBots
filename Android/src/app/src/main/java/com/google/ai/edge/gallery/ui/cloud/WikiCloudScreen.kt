/*
 * Wiki — Layar pengaturan Cloud (full-screen): kartu provider, Fetch model, pilih, Simpan.
 */
package com.google.ai.edge.gallery.ui.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WikiCloudScreen(onNavigateUp: () -> Unit) {
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onNavigateUp) {
          Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali")
        }
        Text("Wiki Cloud", style = MaterialTheme.typography.titleLarge)
      }
      Column(
        modifier =
          Modifier.weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          "Hubungkan Wiki ke provider OpenAI-compatible. Isi API key, tekan Fetch Model untuk mengambil daftar model, pilih modelnya, lalu Simpan.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WikiSettingsContainer()
        Spacer(modifier = Modifier.height(4.dp))
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
      ) {
        Button(onClick = onNavigateUp) {
          Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Selesai")
        }
      }
    }
  }
}
