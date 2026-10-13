package com.mahfuz.englishcompanion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahfuz.englishcompanion.Bus
import com.mahfuz.englishcompanion.Engine
import com.mahfuz.englishcompanion.R

@Composable
fun ProgressScreen() {
    val prefs = Engine.prefs
    @Suppress("UNUSED_VARIABLE") val v = Bus.statsVersion
    var range by remember { mutableIntStateOf(0) }
    val days = when (range) { 0 -> 7; 1 -> 30; else -> 0 }
    val s = prefs.stats(days)
    val mistakes = prefs.mistakeCounts()
    val chartDays = if (range == 1) 30 else 7
    val mins = prefs.dailyMinutes(chartDays)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Progress", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Segmented(listOf("7 days", "30 days", "All time"), range, { range = it }, Modifier.fillMaxWidth())

        // Daily minutes bar chart
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Text("Speaking minutes per day", color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                val max = (mins.maxOrNull() ?: 0f).coerceAtLeast(1f)
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val n = mins.size
                    val gap = size.width / n
                    val bw = gap * 0.6f
                    mins.forEachIndexed { i, m ->
                        val h = (m / max) * size.height
                        drawRoundRect(
                            C.Border, Offset(i * gap + (gap - bw) / 2, 0f), Size(bw, size.height),
                            CornerRadius(bw / 2, bw / 2), alpha = 0.35f
                        )
                        if (h > 0f) drawRoundRect(
                            Brush.verticalGradient(listOf(C.Cyan, C.Blue), startY = size.height - h, endY = size.height),
                            Offset(i * gap + (gap - bw) / 2, size.height - h), Size(bw, h),
                            CornerRadius(bw / 2, bw / 2)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(if (chartDays == 7) "7 days ago" else "30 days ago", color = C.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("Today", color = C.Muted, fontSize = 11.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(R.drawable.ic_mic, C.Cyan, "Speaking Time", formatMinutes(s.seconds / 60f), Modifier.weight(1f))
            StatTile(R.drawable.ic_wave, C.Green, "Words Spoken", "${s.words}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(R.drawable.ic_headphones, C.Blue, "English Speaking", "${s.englishPct}%", Modifier.weight(1f))
            StatTile(R.drawable.ic_home, C.Orange, "Bangla Usage", "${s.banglaPct}%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(R.drawable.ic_check, C.Red, "Corrections", "${s.corrections}", Modifier.weight(1f))
            StatTile(R.drawable.ic_book, C.Purple, "New Words", "${s.newWords}", Modifier.weight(1f))
        }
        EmojiTile("🔥", "Streak", "${prefs.streak()} days", Modifier.fillMaxWidth())

        SectionTitle("Repeated mistakes")
        GlassCard(Modifier.fillMaxWidth()) {
            if (mistakes.isEmpty()) {
                Text("No repeated mistakes yet. Keep talking!", color = C.Muted, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val top = mistakes.first().second.toFloat()
                    mistakes.take(8).forEach { (type, count) ->
                        Column {
                            Row {
                                Text(type.replace('_', ' ').replaceFirstChar { it.uppercase() }, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("$count", color = C.Muted, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(C.Border)) {
                                Box(
                                    Modifier.fillMaxWidth(count / top).height(6.dp).clip(RoundedCornerShape(50))
                                        .background(Brush.horizontalGradient(listOf(C.Orange, C.Red)))
                                )
                            }
                        }
                    }
                }
            }
        }
        val notes = prefs.recentMistakeNotes(5)
        if (notes.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recent", color = C.Muted, fontSize = 13.sp)
                    notes.reversed().forEach {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("•", color = C.Orange)
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = C.Text, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
