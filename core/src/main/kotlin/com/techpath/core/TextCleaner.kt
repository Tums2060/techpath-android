package com.techpath.core

/**
 * Kotlin port of `clean_text()` from the SemProject_MLEngine notebook (and
 * `pipeline/train_model.py`). Every text the model sees, at training time and
 * on the phone, goes through exactly these steps.
 */
object TextCleaner {
    // Python's re is Unicode-aware; spell out its \w and \s so the JVM and Android (ICU) agree.
    private const val W = """[\p{L}\p{N}_]"""
    private const val S = """[\s\p{Z}\u0085\u001C-\u001F]"""
    private const val NOT_S = """[^\s\p{Z}\u0085\u001C-\u001F]"""

    private val MD_LINK = Regex("""\[([^\]]*)\]\([^)]*\)""")
    private val URL = Regex("https?://$NOT_S+|www\\.$NOT_S+")
    private val TAG = Regex("@$W+@")
    private val MD_SYMBOLS = Regex("""[=#*_`>-]{2,}""")
    private val OTHER = Regex("""[^a-z0-9+#.\s\p{Z}\u0085\u001C-\u001F]""")
    private val SPACES = Regex("$S+")

    fun clean(text: String): String {
        var t = text.lowercase()                 // 1. lowercase so 'Git' and 'git' are the same word
        t = MD_LINK.replace(t, "$1")             // 2. markdown link [label](url) -> label
        t = URL.replace(t, " ")                  // 3. remove raw URLs
        t = TAG.replace(t, " ")                  // 4. remove roadmap.sh tags like @official@
        t = MD_SYMBOLS.replace(t, " ")           // 5. remove markdown heading/rule symbols
        t = OTHER.replace(t, " ")                // 6. keep letters, digits and tech symbols (c++, c#, .net)
        return SPACES.replace(t, " ").trim()     // 7. collapse repeated spaces
    }
}
