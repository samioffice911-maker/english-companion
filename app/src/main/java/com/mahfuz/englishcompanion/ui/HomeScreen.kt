package com.mahfuz.englishcompanion.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahfuz.englishcompanion.Bus
import com.mahfuz.englishcompanion.Engine
import com.mahfuz.englishcompanion.Modes
import com.mahfuz.englishcompanion.R
import com.mahfuz.englishcompanion.Screen
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(onStart: (String) -> Unit, onSettings: () -> Unit, onOpen: (Screen) -> Unit) {
    val prefs = Engine.prefs
    @Suppress("UNUSED_VARIABLE") val v = Bus.statsVersion   // re-read when stats change
    val today = prefs.stats(1)
    val streak = prefs.streak()
    val goal = prefs.dailyGoalMin
    val minutes = today.seconds / 60f
    var pro by remember { mutableStateOf(prefs.professional) }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val name = prefs.userName

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (name.isBlank()) "$greet 👋" else "$greet,", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (name.isNotBlank()) Text("$name 🌱", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Small steps. Big progress.", color = C.Muted, fontSize = 13.sp)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(C.Card),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.IconButton(onClick = onSettings) { Ico(R.drawable.ic_settings, C.Muted) }
            }
        }

        // Live status banner if running
        if (Bus.running) {
            GlassCard(Modifier.fillMaxWidth(), border = C.Green, onClick = { onOpen(Screen.TALK) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(C.Green))
                    Spacer(Modifier.width(10.dp))
                    Text("Companion is on · ${Bus.status}", color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Ico(R.drawable.ic_chevron, C.Muted, 18.dp)
                }
            }
        }

        // Today's speaking time
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today's Speaking Time", color = C.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(formatMinutes(minutes), color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Goal: $goal min", color = C.Green, fontSize = 12.sp)
                }
                Box(contentAlignment = Alignment.Center) {
                    ProgressRing(minutes / goal, 70.dp)
                    Text("${(minutes * 100 / goal).toInt().coerceAtMost(999)}%", color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(R.drawable.ic_mic, C.Cyan, "Words Spoken", "${today.words}", Modifier.weight(1f))
            StatTile(R.drawable.ic_check, C.Red, "Corrections", "${today.corrections}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(R.drawable.ic_book, C.Purple, "New Vocabulary", "${today.newWords}", Modifier.weight(1f))
            EmojiTile("🔥", "Streak", "$streak ${if (streak == 1) "day" else "days"}", Modifier.weight(1f))
        }

        SectionTitle("Quick Modes")
        val icons = listOf(R.drawable.ic_wave, R.drawable.ic_check, R.drawable.ic_headphones)
        val colors = listOf(C.Blue, C.Purple, C.Green)
        Modes.ALL.forEachIndexed { i, m ->
            ModeCard(icons[i], colors[i], Modes.TITLES[i], Modes.SUBTITLES[i], prefs.mode == m) { onStart(m) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💼", fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Professional English", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Meetings, presentations, emails", color = C.Muted, fontSize = 12.sp)
                }
                Switch(
                    checked = pro,
                    onCheckedChange = { pro = it; prefs.professional = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Blue, checkedThumbColor = androidx.compose.ui.graphics.Color.White)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

fun formatMinutes(m: Float): String {
    return if (m >= 60) String.format(Locale.US, "%dh %dm", (m / 60).toInt(), (m % 60).toInt())
    else String.format(Locale.US, "%d min", m.toInt())
}
