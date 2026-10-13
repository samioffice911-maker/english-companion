package com.mahfuz.englishcompanion.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahfuz.englishcompanion.Bus
import com.mahfuz.englishcompanion.Engine
import com.mahfuz.englishcompanion.Kind
import com.mahfuz.englishcompanion.LogEntry
import com.mahfuz.englishcompanion.MicControl
import com.mahfuz.englishcompanion.Modes
import com.mahfuz.englishcompanion.R
import java.util.Locale

@Composable
fun TalkScreen(onStart: () -> Unit, onStop: () -> Unit) {
    val prefs = Engine.prefs
    var mode by remember { mutableStateOf(prefs.mode) }
    var menu by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val running = Bus.running
    val log = Bus.log
    val listState = rememberLazyListState()

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Top bar: mode chooser
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable { menu = true }.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(Modes.title(mode), color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (prefs.professional) Text("  💼", fontSize = 14.sp)
                Text("  ▾", color = C.Muted, fontSize = 15.sp)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Modes.ALL.forEachIndexed { i, m ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(Modes.TITLES[i], fontWeight = FontWeight.SemiBold)
                                Text(Modes.SUBTITLES[i], fontSize = 12.sp, color = C.Muted)
                            }
                        },
                        onClick = { mode = m; prefs.mode = m; menu = false }
                    )
                }
            }
        }

        // Status line
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(
                when {
                    !running -> C.Muted
                    Bus.capturing -> C.Orange
                    else -> C.Green
                }
            ))
            Spacer(Modifier.width(6.dp))
            Text(if (running) Bus.status else "Not listening", color = C.Muted, fontSize = 13.sp)
        }

        // Orb
        val empty = log.none { it.kind == Kind.USER || it.kind == Kind.AI }
        val orbSize = if (empty) 190.dp else 120.dp
        Column(
            Modifier.fillMaxWidth().animateContentSize().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VoiceOrb(orbSize, running, Bus.level) { if (running) tapMic() else onStart() }
            if (empty) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (running) "I'm listening…" else "Tap the orb to start",
                    color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Talk naturally in English, Bangla or both.\nTap the mic when you finish a sentence for a faster reply.",
                    color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        // Conversation
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(log, key = { it.id }) { Bubble(it) }
        }

        // Typing box
        if (typing) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("Type Bangla or English…", color = C.Muted) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.Blue, unfocusedBorderColor = C.Border,
                        focusedTextColor = C.Text, unfocusedTextColor = C.Text,
                        focusedContainerColor = C.Card, unfocusedContainerColor = C.Card
                    )
                )
                Spacer(Modifier.width(8.dp))
                RoundButton(R.drawable.ic_send, C.Blue, 48) {
                    val t = text.trim()
                    if (t.isNotEmpty()) { Engine.submitText(t); text = "" }
                }
            }
        }

        // Bottom controls
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundButton(R.drawable.ic_keyboard, if (typing) C.Blue else C.Card, 48) { typing = !typing }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MicButton(running, Bus.capturing) { if (running) tapMic() else onStart() }
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        !running -> "Tap to start"
                        Bus.capturing -> "Tap when done"
                        else -> "Tap to speak"
                    },
                    color = C.Muted, fontSize = 12.sp
                )
            }
            RoundButton(R.drawable.ic_stop, if (running) C.Red.copy(alpha = 0.85f) else C.Card, 48) {
                if (running) onStop()
            }
        }
    }
}

private fun tapMic() {
    if (Bus.capturing) MicControl.forceEnd = true else MicControl.forceStart = true
}

@Composable
private fun MicButton(running: Boolean, capturing: Boolean, onClick: () -> Unit) {
    val ring = if (capturing) C.Orange else C.Cyan
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(C.Blue, C.BlueDeep)))
            .border(3.dp, if (running) ring else C.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Ico(if (capturing) R.drawable.ic_send else R.drawable.ic_mic, Color.White, 32.dp)
    }
}

@Composable
fun RoundButton(icon: Int, bg: Color, sizeDp: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(bg).border(1.dp, C.Border, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Ico(icon, C.Text, (sizeDp * 0.45f).dp) }
}

@Composable
fun Bubble(e: LogEntry) {
    when (e.kind) {
        Kind.USER -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Box(
                Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .background(C.UserBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) { Text(e.text, color = C.Text, fontSize = 16.sp) }
            val info = buildList {
                if (e.meta.isNotBlank()) add(e.meta)
                if (e.seconds > 0f) add(String.format(Locale.US, "⏱ %.1fs", e.seconds))
            }.joinToString("  ·  ")
            if (info.isNotBlank()) Text(info, color = if (e.meta.startsWith("Bangla")) C.Cyan else C.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, end = 6.dp))
        }

        Kind.AI -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(C.Blue),
                contentAlignment = Alignment.Center
            ) { Ico(R.drawable.ic_wave, Color.White, 16.dp) }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.widthIn(max = 290.dp)
                    .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                    .background(C.CardHi)
                    .border(1.dp, C.Border, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                    .clickable { Engine.speaker.speak(e.text) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) { Text(e.text, color = C.Text, fontSize = 16.sp) }
        }

        Kind.CORRECTION -> Box(Modifier.fillMaxWidth().padding(start = 36.dp)) {
            Column(
                Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.GreenBg)
                    .border(1.dp, C.Green.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable { Engine.speaker.speak(e.text) }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico(R.drawable.ic_check, C.Green, 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(e.meta.substringBefore(" · "), color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Ico(R.drawable.ic_volume, C.Green, 14.dp)
                }
                Spacer(Modifier.height(4.dp))
                Text(e.text, color = Color(0xFFB9F5CF), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                val note = e.meta.substringAfter(" · ", "")
                if (note.isNotBlank()) Text(note, color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Kind.WORD -> Box(Modifier.fillMaxWidth().padding(start = 36.dp)) {
            Column(
                Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1F1A4A))
                    .border(1.dp, C.Purple.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Text("📘 New word", color = C.Purple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(e.text, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (e.meta.isNotBlank()) Text(e.meta, color = C.Muted, fontSize = 13.sp)
            }
        }

        Kind.INFO -> Text(
            e.text, color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        )

        Kind.ERROR -> Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF3A1420))
                .border(1.dp, C.Red.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) { Text("⚠ " + e.text, color = Color(0xFFFFB3B3), fontSize = 13.sp) }
    }
}
