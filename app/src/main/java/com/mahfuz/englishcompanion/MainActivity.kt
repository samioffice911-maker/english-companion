package com.mahfuz.englishcompanion

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var startBtn: Button
    private lateinit var logContainer: LinearLayout
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Engine.init(this)

        status = findViewById(R.id.txtStatus)
        stats = findViewById(R.id.txtStats)
        startBtn = findViewById(R.id.btnStart)
        logContainer = findViewById(R.id.logContainer)
        scroll = findViewById(R.id.scroll)

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupModeSpinner()

        startBtn.setOnClickListener {
            if (Bus.running) stopCompanion() else startCompanion()
        }

        val edit = findViewById<EditText>(R.id.editText)
        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val t = edit.text.toString().trim()
            if (t.isNotEmpty()) {
                Engine.submitText(t)
                edit.setText("")
            }
        }

        if (Engine.prefs.apiKey.isBlank()) {
            addBubble(LogEntry(Kind.INFO,
                "Welcome! First tap ⚙ Settings and paste your free Gemini API key."))
        }
    }

    override fun onResume() {
        super.onResume()
        logContainer.removeAllViews()
        Bus.log.forEach { addBubble(it) }
        Bus.onEntry = { addBubble(it); refreshStats() }
        Bus.onStatus = { status.text = it }
        Bus.onRunning = { updateButton(it) }
        status.text = Bus.status
        updateButton(Bus.running)
        refreshStats()
    }

    override fun onPause() {
        super.onPause()
        Bus.onEntry = null
        Bus.onStatus = null
        Bus.onRunning = null
    }

    private fun setupModeSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerMode)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, Modes.LABELS) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.parseColor("#E8EEF3")); v.textSize = 15f
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(Color.parseColor("#E8EEF3"))
                v.setBackgroundColor(Color.parseColor("#1A2532"))
                v.setPadding(24, 28, 24, 28)
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(Modes.ALL.indexOf(Engine.prefs.mode).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Engine.prefs.mode = Modes.ALL[pos]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun startCompanion() {
        if (Engine.prefs.apiKey.isBlank()) {
            Toast.makeText(this, "Add your Gemini API key in Settings first", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val needed = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), REQ_PERMS)
            return
        }
        startForegroundService(Intent(this, CompanionService::class.java))
    }

    private fun stopCompanion() {
        startService(Intent(this, CompanionService::class.java).setAction(CompanionService.ACTION_STOP))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startForegroundService(Intent(this, CompanionService::class.java))
        } else {
            Toast.makeText(this, "Microphone permission is needed to listen to you.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateButton(running: Boolean) {
        startBtn.text = if (running) "■  Stop Companion" else "▶  Start Companion"
        startBtn.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (running) "#E5534B" else "#2BB673"))
    }

    private fun refreshStats() {
        stats.text = Engine.prefs.statsText()
    }

    private fun addBubble(e: LogEntry) {
        val tv = TextView(this)
        tv.text = when (e.kind) {
            Kind.USER -> "🗣 ${e.text}"
            Kind.AI -> "🤖 ${e.text}"
            else -> e.text
        }
        tv.textSize = if (e.kind == Kind.INFO || e.kind == Kind.ERROR) 13f else 16f
        tv.setTextColor(Color.parseColor(when (e.kind) {
            Kind.ERROR -> "#FF8A80"
            Kind.INFO -> "#93A1AF"
            Kind.CORRECTION -> "#9BE7B8"
            else -> "#E8EEF3"
        }))
        val bg = GradientDrawable()
        bg.cornerRadius = 28f
        bg.setColor(Color.parseColor(when (e.kind) {
            Kind.USER -> "#24364A"
            Kind.AI -> "#173B2C"
            Kind.CORRECTION -> "#12301F"
            else -> "#00000000"
        }))
        tv.background = bg
        tv.setPadding(28, 18, 28, 18)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 12
        if (e.kind == Kind.USER) lp.marginStart = 80 else if (e.kind == Kind.AI || e.kind == Kind.CORRECTION) lp.marginEnd = 80
        logContainer.addView(tv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    companion object {
        const val REQ_PERMS = 11
    }
}
