package com.mahfuz.englishcompanion

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class SettingsActivity : Activity() {
    private val prefs get() = Engine.prefs
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Engine.init(this)

        val scroll = ScrollView(this)
        scroll.fitsSystemWindows = true
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(32))
        scroll.addView(root)
        setContentView(scroll)

        addTitle("Settings")

        // --- API key ---
        header("Gemini API key")
        note("Get it free at aistudio.google.com/apikey. It is saved only on this phone.")
        val keyEdit = edit(prefs.apiKey, "Paste your API key here")
        keyEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val show = CheckBox(this).apply {
            text = "Show key"; setTextColor(Color.LTGRAY)
            setOnCheckedChangeListener { _, c ->
                keyEdit.inputType = InputType.TYPE_CLASS_TEXT or
                    (if (c) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
                keyEdit.setSelection(keyEdit.text.length)
            }
        }
        root.addView(show)

        // --- Model ---
        header("AI model")
        note("Leave as is unless you see a 'model not found' or limit error. Other free options: gemini-3.1-flash-lite, gemini-3.8-flash")
        val modelEdit = edit(prefs.model, Prefs.DEFAULT_MODEL)

        // --- Voice speed ---
        header("Voice speed")
        val speedLabel = note("")
        val speed = SeekBar(this).apply {
            max = 110   // 0.5 .. 1.6
            progress = ((prefs.speechRate - 0.5f) * 100).toInt()
        }
        fun speedText(p: Int) = String.format(Locale.US, "%.2fx", 0.5f + p / 100f)
        speedLabel.text = speedText(speed.progress)
        speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { speedLabel.text = speedText(p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(speed)

        // --- Mic sensitivity ---
        header("Microphone sensitivity")
        note("Use Low in noisy places, High if it misses your voice.")
        val sens = radioGroup(listOf("Low", "Medium", "High"), prefs.sensitivity)

        // --- Pause length ---
        header("Wait after you stop talking")
        note("Short = faster replies. Long = lets you think mid-sentence.")
        val pauseOptions = listOf(900, 1300, 2000)
        val pause = radioGroup(listOf("Short (0.9s)", "Normal (1.3s)", "Long (2s)"),
            pauseOptions.indexOf(prefs.pauseMs).let { if (it < 0) 1 else it })

        // --- Privacy ---
        header("Privacy")
        note("Your voice recordings are never saved. Audio is sent to Gemini, then thrown away.")
        val saveHist = check("Remember recent conversation after closing the app", prefs.saveHistory)
        val useMem = check("Remember my repeated mistakes (to practise them)", prefs.useMemory)

        // --- Save ---
        val save = button("Save settings", "#2BB673")
        save.setOnClickListener {
            prefs.apiKey = keyEdit.text.toString()
            prefs.model = modelEdit.text.toString()
            prefs.speechRate = 0.5f + speed.progress / 100f
            Engine.speaker.setRate(prefs.speechRate)
            prefs.sensitivity = selectedIndex(sens)
            prefs.pauseMs = pauseOptions[selectedIndex(pause).coerceIn(0, 2)]
            prefs.saveHistory = saveHist.isChecked
            prefs.useMemory = useMem.isChecked
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        val test = button("🔊 Test voice", "#24364A")
        test.setOnClickListener {
            Engine.speaker.setRate(0.5f + speed.progress / 100f)
            Engine.speaker.speak("Hi! This is how I sound. Let's practise English together.")
        }

        // --- Mistake memory view ---
        header("My repeated mistakes")
        val counts = prefs.mistakeCounts()
        note(if (counts.isEmpty()) "None recorded yet."
             else counts.take(8).joinToString("\n") { "• ${it.first.replace('_', ' ')}: ${it.second}" })
        val recent = prefs.recentMistakeNotes(5)
        if (recent.isNotEmpty()) note("Recent:\n" + recent.joinToString("\n") { "– $it" })

        val del = button("Delete all my data", "#7A2E2A")
        del.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete all data?")
                .setMessage("This deletes conversation history, mistake memory and progress. Your API key is kept.")
                .setPositiveButton("Delete") { _, _ ->
                    prefs.clearAllData()
                    Engine.clearHistory()
                    Bus.log.clear()
                    Toast.makeText(this, "All data deleted", Toast.LENGTH_SHORT).show()
                    recreate()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        note("Version ${packageManager.getPackageInfo(packageName, 0).versionName}")
    }

    // ---------- tiny UI helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addTitle(t: String) = TextView(this).apply {
        text = t; textSize = 24f; setTextColor(Color.WHITE)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        root.addView(this)
    }

    private fun header(t: String) = TextView(this).apply {
        text = t; textSize = 17f; setTextColor(Color.parseColor("#2BB673"))
        setPadding(0, dp(20), 0, dp(4))
        root.addView(this)
    }

    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#93A1AF"))
        setPadding(0, 0, 0, dp(6))
        root.addView(this)
    }

    private fun edit(value: String, hint: String) = EditText(this).apply {
        setText(value); this.hint = hint
        setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        setBackgroundResource(R.drawable.rounded_card)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isSingleLine = true
        root.addView(this, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun check(t: String, value: Boolean) = CheckBox(this).apply {
        text = t; isChecked = value; setTextColor(Color.WHITE)
        root.addView(this)
    }

    private fun radioGroup(options: List<String>, selected: Int): RadioGroup {
        val g = RadioGroup(this)
        g.orientation = RadioGroup.HORIZONTAL
        options.forEachIndexed { i, o ->
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.text = o; rb.setTextColor(Color.WHITE); rb.tag = i
            g.addView(rb)
            if (i == selected) rb.isChecked = true
        }
        root.addView(g)
        return g
    }

    private fun selectedIndex(g: RadioGroup): Int {
        val rb = g.findViewById<RadioButton>(g.checkedRadioButtonId) ?: return 1
        return rb.tag as Int
    }

    private fun button(t: String, color: String) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(color))
        gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12)
        root.addView(this, lp)
    }
}
