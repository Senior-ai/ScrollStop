package com.senior.scrollstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckParserTest {

    private fun cards(vararg pairs: Pair<String, String>) = pairs.map { MyCard(it.first, it.second) }

    @Test
    fun readsQuizletTabExport() {
        val result = DeckParser.parse("hola\thello\nadiós\tgoodbye\n")
        assertEquals(cards("hola" to "hello", "adiós" to "goodbye"), result.cards)
        assertEquals(emptyList<Int>(), result.skippedLines)
    }

    @Test
    fun readsAnkiPlainTextExport() {
        val anki = "#separator:tab\n#html:true\nWhat is ATP?\tThe cell's energy <b>currency</b>&nbsp;\tTag1\n" +
            "Krebs cycle location<br>(eukaryotes)\tMitochondrial matrix\n"
        assertEquals(
            cards("What is ATP?" to "The cell's energy currency", "Krebs cycle location (eukaryotes)" to "Mitochondrial matrix"),
            DeckParser.parse(anki).cards,
        )
    }

    @Test
    fun readsDashSeparatedLinesWithAnyDash() {
        val text = "perro - dog\ngato – cat\ncasa — house\nWhat is 2 - 1? - one"
        assertEquals(
            cards("perro" to "dog", "gato" to "cat", "casa" to "house", "What is 2 - 1?" to "one"),
            DeckParser.parse(text).cards,
        )
    }

    @Test
    fun readsCsvWithQuotesAndCommas() {
        val csv = "\"Capital of Spain, in Spanish\",\"Madrid\"\n\"He said \"\"hi\"\"\",greeting\nplain,field"
        assertEquals(
            cards("Capital of Spain, in Spanish" to "Madrid", "He said \"hi\"" to "greeting", "plain" to "field"),
            DeckParser.parse(csv).cards,
        )
    }

    @Test
    fun csvIsUsedForCsvFilesEvenWithoutQuotes() {
        assertEquals(cards("ser" to "to be"), DeckParser.parse("ser,to be", fileName = "verbs.CSV").cards)
    }

    @Test
    fun reportsLinesItCannotRead() {
        val result = DeckParser.parse("hola\thello\njust a sentence\n\n\tmissing question\nok - fine")
        assertEquals(listOf(2, 4), result.skippedLines)
        assertEquals(2, result.cards.size)
    }

    @Test
    fun dropsExactDuplicatesAndHandlesWindowsLineEnds() {
        val result = DeckParser.parse("a\tb\r\nA\tB\r\nc\td\r\n")
        assertEquals(cards("a" to "b", "c" to "d"), result.cards)
    }
}
