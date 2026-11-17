package com.senior.scrollstop.data

/**
 * Turns text from a paste, a file or another app's Share into quiz cards. Understands:
 *
 * - `question<TAB>answer` — Quizlet's export and Anki's "Notes in Plain Text" (extra columns are
 *   ignored, `#` header lines are skipped, simple HTML formatting is removed)
 * - `question - answer` (also with – or —), the easy format to type or ask a chatbot for; split
 *   at the last dash
 * - CSV with optional "quotes", used when the file is .csv or the text starts with a quote
 *
 * Pure Kotlin so it can be unit tested.
 */
object DeckParser {

    const val MAX_CARDS = 5_000

    data class Result(val cards: List<MyCard>, val skippedLines: List<Int>)

    private val dashes = listOf(" – ", " — ", " - ")

    fun parse(text: String, fileName: String? = null): Result {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val firstContent = lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trimStart()
        val csv = fileName?.endsWith(".csv", ignoreCase = true) == true || firstContent?.startsWith("\"") == true

        val cards = mutableListOf<MyCard>()
        val seen = mutableSetOf<String>()
        val skipped = mutableListOf<Int>()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val fields = when {
                '\t' in line -> line.split('\t')
                csv -> splitCsv(line)
                else -> splitAtLastDash(line)
            }
            val question = fields?.getOrNull(0)?.let(::clean).orEmpty()
            val answer = fields?.getOrNull(1)?.let(::clean).orEmpty()
            if (question.isEmpty() || answer.isEmpty()) {
                skipped += index + 1
                return@forEachIndexed
            }
            if (cards.size < MAX_CARDS && seen.add(question.lowercase() + "\u0000" + answer.lowercase())) {
                cards += MyCard(question, answer)
            }
        }
        return Result(cards, skipped)
    }

    /** Splits at the last spaced dash: questions contain dashes more often than short answers. */
    private fun splitAtLastDash(line: String): List<String>? {
        val (at, dash) = dashes.map { line.lastIndexOf(it) to it }.maxByOrNull { it.first } ?: return null
        if (at < 0) return null
        return listOf(line.substring(0, at), line.substring(at + dash.length))
    }

    /** One CSV line: commas separate fields, "quoted" fields may contain commas and "" for a quote. */
    private fun splitCsv(line: String): List<String> {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { fields += field.toString(); field.clear() }
                else -> field.append(c)
            }
            i++
        }
        fields += field.toString()
        return fields
    }

    private val tag = Regex("<[^>]+>")
    private val spaces = Regex("\\s+")

    /** Removes Anki-style HTML and stray quotes, and collapses whitespace. */
    private fun clean(text: String): String = text
        .replace(Regex("(?i)<br\\s*/?>"), " ")
        .replace(tag, "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        .replace(spaces, " ")
        .trim()
        .removeSurrounding("\"")
        .trim()
}
