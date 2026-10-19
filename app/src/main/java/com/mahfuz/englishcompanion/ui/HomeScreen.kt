package com.mahfuz.englishcompanion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
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
fun HomeScreen(
    onStart: (String) -> Unit,
    onPractice: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (Screen) -> Unit
) {
    val prefs = Engine.prefs
    val v = Bus.statsVersion   // re-read when stats change
    val today = remember(v) { prefs.stats(1) }
    val week = remember(v) { prefs.dailyMinutes(7) }
    val streak = remember(v) { prefs.streak() }
    val topMistake = remember(v) { prefs.mistakeCounts().firstOrNull() }
    val lastWord = remember(v) { prefs.vocabulary().firstOrNull() }
    val goal = prefs.dailyGoalMin
    val minutes = today.seconds / 60f
    var pro by remember { mutableStateOf(prefs.professional) }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val name = prefs.userName

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- Header ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(C.blueGlow),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.firstOrNull()?.uppercaseChar()?.toString() ?: "🙂",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(greet + if (name.isBlank()) "" else ",", color = C.Muted, fontSize = 14.sp)
                Text(if (name.isBlank()) "Let's speak English" else name, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(C.Card).border(1.dp, C.Border, CircleShape)
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) { Ico(R.drawable.ic_settings, C.Muted) }
        }

        // ---------- Hero: start talking ----------
        HeroCard(
            running = Bus.running,
            paused = Bus.paused,
            status = Bus.status,
            modeTitle = Modes.title(prefs.mode),
            onClick = { if (Bus.running) onOpen(Screen.TALK) else onStart(prefs.mode) }
        )

        // ---------- Today ----------
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today's speaking time", color = C.Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(formatMinutes(minutes), color = C.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        val left = (goal - minutes).coerceAtLeast(0f)
                        Text(
                            if (left <= 0f) "🎉 Daily goal reached!" else "${left.toInt() + 1} min left to reach $goal min",
                            color = if (left <= 0f) C.Green else C.Cyan, fontSize = 12.sp
                        )
                    }
                    Box(contentAlignment = Alignment.Center) {
                        ProgressRing(minutes / goal, 76.dp, 8.dp)
                        Text("${(minutes * 100 / goal).toInt().coerceAtMost(999)}%", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                // mini week chart
                WeekBars(week)
            }
        }

        // ---------- Stats ----------
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientStat("🗣", "Words", "${today.words}", C.Cyan, Modifier.weight(1f))
            GradientStat("✅", "Corrections", "${today.corrections}", C.Green, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientStat("📘", "New words", "${today.newWords}", C.Purple, Modifier.weight(1f))
            GradientStat("🔥", "Streak", "$streak ${if (streak == 1) "day" else "days"}", C.Orange, Modifier.weight(1f))
        }

        // ---------- Daily practice ----------
        GlassCard(
            Modifier.fillMaxWidth(), border = C.Purple, background = Color(0xFF17163F), onClick = onPractice
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(C.Purple, C.Blue))),
                    contentAlignment = Alignment.Center
                ) { Ico(R.drawable.ic_play, Color.White, 26.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Daily 5-min practice", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (topMistake != null) "A short chat that works on your ${prettyType(topMistake.first)}"
                        else "The AI picks a topic and asks you questions",
                        color = C.Muted, fontSize = 12.sp
                    )
                }
                Ico(R.drawable.ic_chevron, C.Muted, 20.dp)
            }
        }

        // ---------- Focus + word ----------
        if (topMistake != null || lastWord != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (topMistake != null) {
                    GlassCard(Modifier.weight(1f).height(130.dp), onClick = { onOpen(Screen.PROGRESS) }) {
                        Column {
                            Text("🎯 Focus area", color = C.Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(prettyType(topMistake.first).replaceFirstChar { it.uppercase() }, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(tipFor(topMistake.first), color = C.Muted, fontSize = 12.sp, maxLines = 3)
                        }
                    }
                }
                if (lastWord != null) {
                    GlassCard(Modifier.weight(1f).height(130.dp), onClick = { onOpen(Screen.WORDS) }) {
                        Column {
                            Text("📘 Word to review", color = C.Purple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(lastWord.word, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Text(lastWord.meaning, color = C.Muted, fontSize = 12.sp, maxLines = 2)
                            if (lastWord.example.isNotBlank())
                                Text("“${lastWord.example}”", color = C.Cyan, fontSize = 11.sp, fontStyle = FontStyle.Italic, maxLines = 1)
                        }
                    }
                }
            }
        }

        // ---------- Modes ----------
        SectionTitle("Choose a mode")
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
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Blue, checkedThumbColor = Color.White)
                )
            }
        }

        Text(
            "💡 " + dailyTip(),
            color = C.Muted, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroCard(running: Boolean, paused: Boolean, status: String, modeTitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1E4BD8), Color(0xFF3A2D9C), Color(0xFF0E1A45))))
            .border(1.dp, Color(0xFF4C74FF).copy(alpha = 0.6f), shape)
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        !running -> "Ready to talk?"
                        paused -> "Companion paused"
                        else -> "I'm listening…"
                    },
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (running) status else "Think in Bangla. Speak in English.\nGrow every day.",
                    color = Color(0xFFCAD6FF), fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        if (running) "Open conversation" else "▶  Start · $modeTitle",
                        color = Color(0xFF1E3FBF), fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            VoiceOrb(104.dp, running && !paused, Bus.level, onClick)
        }
    }
}

@Composable
private fun GradientStat(emoji: String, label: String, value: String, color: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.22f), C.Card, C.Card)))
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 15.sp) }
                Spacer(Modifier.width(8.dp))
                Text(label, color = C.Muted, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            Text(value, color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun WeekBars(mins: List<Float>) {
    val labels = run {
        val c = Calendar.getInstance()
        val names = listOf("S", "M", "T", "W", "T", "F", "S")
        (6 downTo 0).map { back ->
            val cc = c.clone() as Calendar
            cc.add(Calendar.DAY_OF_YEAR, -back)
            names[cc.get(Calendar.DAY_OF_WEEK) - 1]
        }
    }
    val max = (mins.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Column {
        Canvas(Modifier.fillMaxWidth().height(54.dp)) {
            val n = mins.size
            val gap = size.width / n
            val bw = gap * 0.42f
            mins.forEachIndexed { i, m ->
                val x = i * gap + (gap - bw) / 2
                drawRoundRect(C.Border, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2, bw / 2), alpha = 0.3f)
                val h = (m / max) * size.height
                if (h > 0f) drawRoundRect(
                    if (i == n - 1) Brush.verticalGradient(listOf(C.Green, C.Cyan)) else Brush.verticalGradient(listOf(C.Cyan, C.Blue)),
                    Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2, bw / 2)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, color = if (i == labels.size - 1) C.Text else C.Muted, fontSize = 11.sp,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private fun prettyType(t: String) = when (t) {
    "tense" -> "verb tenses"
    "article" -> "articles (a / an / the)"
    "preposition" -> "prepositions"
    "agreement" -> "subject–verb agreement"
    "word_choice" -> "word choice"
    "word_order" -> "word order"
    "structure" -> "sentence structure"
    "plural" -> "plurals"
    "pronoun" -> "pronouns (he / she)"
    "literal_translation" -> "natural phrasing"
    "vocabulary" -> "vocabulary"
    else -> t.replace('_', ' ')
}

private fun tipFor(t: String) = when (t) {
    "tense" -> "Talking about yesterday? Use the past: went, ate, saw."
    "article" -> "Use “a” for any one thing, “the” for a specific one."
    "preposition" -> "at 5 pm · on Monday · in October · at home."
    "agreement" -> "He/She/It + verb-s: “She works”, not “She work”."
    "word_order" -> "English order: Subject → Verb → Object."
    "pronoun" -> "Men = he/him, women = she/her. Slow down and check."
    "literal_translation" -> "Think of how a native would say it, not word-for-word."
    "plural" -> "More than one? Add -s: two books, many friends."
    else -> "Your companion will practise this with you more often."
}

private fun dailyTip(): String {
    val tips = listOf(
        "Narrate what you're doing in English: “I'm making tea now.”",
        "Stuck? Say it in Bangla — the companion will give you the English.",
        "Repeat the corrected sentence out loud once. It helps it stick.",
        "Tap the mic as soon as you finish a sentence for the fastest reply.",
        "Describe your day in 3 sentences before you sleep.",
        "Use one new word from your Words list today.",
        "Think in short sentences first. Long ones come later."
    )
    return tips[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % tips.size]
}

fun formatMinutes(m: Float): String {
    return if (m >= 60) String.format(Locale.US, "%dh %dm", (m / 60).toInt(), (m % 60).toInt())
    else String.format(Locale.US, "%d min", m.toInt())
}
