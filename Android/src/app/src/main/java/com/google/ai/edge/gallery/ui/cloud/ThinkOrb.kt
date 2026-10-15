/*
 * Wiki — Think Orb: bola-bola streaming melawan hukum fisika.
 * Murni Material3 + Canvas, tanpa lib baru. Anti-lag: particle count kecil,
 * remember + infiniteTransition, tanpa allocation per-frame.
 */
package com.google.ai.edge.gallery.ui.cloud

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ThinkOrb(modifier: Modifier = Modifier, orbCount: Int = 5, diameter: androidx.compose.ui.unit.Dp = 56.dp) {
  val c1 = MaterialTheme.colorScheme.primary
  val c2 = MaterialTheme.colorScheme.tertiary
  val transition = rememberInfiniteTransition(label = "wiki_orb")
  val t by transition.animateFloat(0f, 6.2831f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
  val seeds = remember(orbCount) { List(orbCount) { i -> i * 1.37f } }
  Canvas(modifier.size(diameter)) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 3.2f
    seeds.forEachIndexed { i, s ->
      // Gerak anti-fisika: orbit berlawanan + radius berdenyut.
      val dir = if (i % 2 == 0) 1f else -1f
      val ang = dir * t * (0.9f + i * 0.12f) + s
      val rad = r * (0.7f + 0.3f * sin(t * 1.7f + s))
      val o = Offset(cx + rad * cos(ang), cy + rad * sin(ang) * 0.8f)
      drawCircle(if (i % 2 == 0) c1.copy(alpha = 0.85f) else c2.copy(alpha = 0.7f), radius = size.minDimension / (9f + i), center = o)
    }
    drawCircle(Color.White.copy(alpha = 0.25f), radius = size.minDimension / 10f, center = Offset(cx, cy))
  }
}
