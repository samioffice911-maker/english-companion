package com.mahfuz.englishcompanion

/** Sample data so automatic screenshot tests show realistic screens. Only loaded via a test command. */
object TestData {
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        val p = Engine.prefs
        if (p.userName.isBlank()) p.userName = "Mahfuz"
        p.addTurn("english", 12, 40f, true, false)
        p.addTurn("bangla", 6, 25f, false, true)
        p.addTurn("mixed", 9, 30f, true, false)
        p.addTurn("english", 15, 50f, false, false)
        p.recordMistake("tense", "said 'I go' for past -> 'I went'")
        p.recordMistake("tense", "said 'she come' -> 'she came'")
        p.recordMistake("article", "missing 'a' before 'meeting'")
        p.addWord("exhausted | very tired | I'm exhausted after the long lab day.")
        p.addWord("opportunity | a good chance | This is a great opportunity for you.")

        Bus.post(Kind.INFO, "Companion started. Talk naturally — English, Bangla, or both.")
        Bus.post(Kind.USER, "আজকে মনে হচ্ছে বৃষ্টি হবে", "Bangla detected", 1.8f)
        Bus.post(Kind.CORRECTION, "I think it's going to rain today.", "In English")
        Bus.post(Kind.AI, "In English: I think it's going to rain today.")
        Bus.post(Kind.USER, "Yesterday I go to the market with my friend", "English", 2.3f)
        Bus.post(Kind.CORRECTION, "Yesterday I went to the market with my friend.", "More natural way · said 'I go' for past -> 'I went'")
        Bus.post(Kind.AI, "Better: Yesterday I went to the market with my friend.")
        Bus.post(Kind.WORD, "exhausted", "very tired — I'm exhausted after the long lab day.")
        Bus.post(Kind.INFO, "📶 Slow network — that reply took 7s. Tapping the mic when you finish helps.")
        Bus.statsChanged()
    }
}
