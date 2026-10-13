package com.mahfuz.englishcompanion.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahfuz.englishcompanion.Bus
import com.mahfuz.englishcompanion.Engine
import com.mahfuz.englishcompanion.Prefs
import com.mahfuz.englishcompanion.R
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val prefs = Engine.prefs
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(prefs.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(prefs.userName) }
    var model by remember { mutableStateOf(prefs.model) }
    var goal by remember { mutableFloatStateOf(prefs.dailyGoalMin.toFloat()) }
    var speed by remember { mutableFloatStateOf(prefs.speechRate) }
    var sens by remember { mutableIntStateOf(prefs.sensitivity) }
    val pauseOptions = listOf(700, 900, 1400)
    var pause by remember { mutableIntStateOf(pauseOptions.indexOf(prefs.pauseMs).let { if (it < 0) 1 else it }) }
    var saveHist by remember { mutableStateOf(prefs.saveHistory) }
    var useMem by remember { mutableStateOf(prefs.useMemory) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        prefs.apiKey = key
        prefs.userName = name
        prefs.model = model
        prefs.dailyGoalMin = goal.toInt()
        prefs.speechRate = speed
        Engine.speaker.setRate(speed)
        prefs.sensitivity = sens
        prefs.pauseMs = pauseOptions[pause]
        prefs.saveHistory = saveHist
        prefs.useMemory = useMem
        Bus.statsChanged()
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { save(); onBack() }) { Ico(R.drawable.ic_back) }
            Text("Settings", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        // API key
        SettingCard("🔑  Gemini API key", "Free from aistudio.google.com/apikey. Saved only on this phone.") {
            Field(key, { key = it }, "Paste your API key",
                if (showKey) VisualTransformation.None else PasswordVisualTransformation())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show key", color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Toggle(showKey) { showKey = it }
            }
        }

        SettingCard("👤  Your name", "Used for the greeting on Home.") {
            Field(name, { name = it }, "e.g. Sami")
        }

        SettingCard("🎯  Daily goal", "${goal.toInt()} minutes of speaking per day") {
            Slider(goal, { goal = it }, valueRange = 5f..60f, steps = 10, colors = sliderColors())
        }

        SettingCard("🔊  Voice speed", String.format(Locale.US, "%.2fx", speed)) {
            Slider(speed, { speed = it }, valueRange = 0.5f..1.6f, colors = sliderColors())
            TextButton(onClick = {
                Engine.speaker.setRate(speed)
                Engine.speaker.speak("Hi! This is how I sound. Let's practise English together.")
            }) { Text("▶  Test voice", color = C.Cyan) }
        }

        SettingCard("🎙  Microphone sensitivity", "Low for noisy places, High if it misses your voice.") {
            Segmented(listOf("Low", "Medium", "High"), sens, { sens = it }, Modifier.fillMaxWidth())
        }

        SettingCard("⏸  Wait after you stop talking", "Short = faster replies. Long = time to think mid-sentence. (You can also tap the mic to send instantly.)") {
            Segmented(listOf("Short", "Normal", "Long"), pause, { pause = it }, Modifier.fillMaxWidth())
        }

        SettingCard("🧠  AI model", "Change only if you see a 'model not found' or limit error. Free options: gemini-3.5-flash-lite (fastest), gemini-3.1-flash-lite, gemini-3.6-flash") {
            Field(model, { model = it }, Prefs.DEFAULT_MODEL)
        }

        SettingCard("🔒  Privacy", "Your voice is never saved. Audio is sent to Gemini, then thrown away.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Remember recent conversation", color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Toggle(saveHist) { saveHist = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Remember my repeated mistakes", color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Toggle(useMem) { useMem = it }
            }
        }

        Button(
            onClick = { save(); Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show(); onBack() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Blue)
        ) { Text("Save settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete all my data", color = C.Red)
        }

        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "" }
        Text("English Companion v$version", color = C.Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("This deletes conversation history, mistake memory, vocabulary and progress. Your API key is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.clearAllData()
                    Engine.clearHistory()
                    Bus.clearLog()
                    Bus.statsChanged()
                    confirmDelete = false
                    Toast.makeText(ctx, "All data deleted", Toast.LENGTH_SHORT).show()
                }) { Text("Delete", color = C.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            containerColor = C.Card
        )
    }
}

@Composable
private fun SettingCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = C.Muted, fontSize = 12.sp)
            Spacer(Modifier.width(1.dp))
            content()
        }
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, hint: String,
    transform: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(hint, color = C.Muted) },
        singleLine = true,
        visualTransformation = transform,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Blue, unfocusedBorderColor = C.Border,
            focusedTextColor = C.Text, unfocusedTextColor = C.Text,
            focusedContainerColor = C.Bg, unfocusedContainerColor = C.Bg
        )
    )
}

@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = C.Blue, checkedThumbColor = Color.White)
    )
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = C.Cyan, activeTrackColor = C.Blue, inactiveTrackColor = C.Border
)
