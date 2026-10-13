package com.mahfuz.englishcompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahfuz.englishcompanion.Bus
import com.mahfuz.englishcompanion.Engine
import com.mahfuz.englishcompanion.R

@Composable
fun WordsScreen() {
    val prefs = Engine.prefs
    @Suppress("UNUSED_VARIABLE") val v = Bus.statsVersion
    var refresh by remember { mutableIntStateOf(0) }
    val words = remember(v, refresh) { prefs.vocabulary() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("My Vocabulary", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("${words.size} words learned while talking", color = C.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        if (words.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "No words yet. When the companion teaches you a better word, it will appear here so you can review it.",
                    color = C.Muted, fontSize = 14.sp
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(words, key = { it.word + it.time }) { w ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(w.word, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                Engine.speaker.speak(w.word + ". " + w.example)
                            }) { Ico(R.drawable.ic_volume, C.Cyan, 20.dp) }
                            IconButton(onClick = { prefs.deleteWord(w.word); refresh++ }) {
                                Ico(R.drawable.ic_delete, C.Muted, 20.dp)
                            }
                        }
                        if (w.meaning.isNotBlank()) Text(w.meaning, color = C.Text, fontSize = 14.sp)
                        if (w.example.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("“${w.example}”", color = C.Cyan, fontSize = 14.sp, fontStyle = FontStyle.Italic)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
