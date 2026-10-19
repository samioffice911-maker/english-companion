package com.mahfuz.englishcompanion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.mahfuz.englishcompanion.ui.AppTheme
import com.mahfuz.englishcompanion.ui.C
import com.mahfuz.englishcompanion.ui.HomeScreen
import com.mahfuz.englishcompanion.ui.Ico
import com.mahfuz.englishcompanion.ui.ProgressScreen
import com.mahfuz.englishcompanion.ui.SettingsScreen
import com.mahfuz.englishcompanion.ui.TalkScreen
import com.mahfuz.englishcompanion.ui.WordsScreen

enum class Screen { HOME, TALK, PROGRESS, WORDS, SETTINGS }

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(Screen.HOME)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startForegroundService(Intent(this, CompanionService::class.java))
            } else {
                Toast.makeText(this, "Microphone permission is needed to listen to you.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Engine.init(this)
        if (Engine.prefs.apiKey.isBlank()) screen = Screen.SETTINGS

        setContent {
            AppTheme {
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                Scaffold(
                    containerColor = C.Bg,
                    bottomBar = { if (screen != Screen.SETTINGS) BottomBar() }
                ) { pad ->
                    Box(Modifier.fillMaxSize().background(C.screen).padding(pad)) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                onStart = { mode -> Engine.prefs.mode = mode; screen = Screen.TALK; startCompanion() },
                                onPractice = {
                                    if (Engine.prefs.apiKey.isBlank()) startCompanion()
                                    else { screen = Screen.TALK; startCompanion(); Engine.startPractice() }
                                },
                                onSettings = { screen = Screen.SETTINGS },
                                onOpen = { screen = it }
                            )
                            Screen.TALK -> TalkScreen(
                                onStart = { startCompanion() },
                                onStop = { stopCompanion() }
                            )
                            Screen.PROGRESS -> ProgressScreen()
                            Screen.WORDS -> WordsScreen()
                            Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun BottomBar() {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = C.Cyan,
            indicatorColor = C.Blue,
            unselectedIconColor = C.Muted,
            unselectedTextColor = C.Muted,
        )
        NavigationBar(containerColor = Color(0xFF0A1229)) {
            listOf(
                Triple(Screen.HOME, R.drawable.ic_home, "Home"),
                Triple(Screen.TALK, R.drawable.ic_mic, "Talk"),
                Triple(Screen.PROGRESS, R.drawable.ic_chart, "Progress"),
                Triple(Screen.WORDS, R.drawable.ic_book, "Words"),
            ).forEach { (s, icon, label) ->
                NavigationBarItem(
                    selected = screen == s,
                    onClick = { screen = s },
                    icon = { Ico(icon, tint = if (screen == s) Color.White else C.Muted) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = colors
                )
            }
        }
    }

    private fun startCompanion() {
        if (Bus.isRunning) return
        if (Engine.prefs.apiKey.isBlank()) {
            Toast.makeText(this, "Add your Gemini API key first", Toast.LENGTH_LONG).show()
            screen = Screen.SETTINGS
            return
        }
        val needed = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
            return
        }
        startForegroundService(Intent(this, CompanionService::class.java))
    }

    private fun stopCompanion() {
        startService(Intent(this, CompanionService::class.java).setAction(CompanionService.ACTION_STOP))
    }
}
