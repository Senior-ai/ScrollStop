package com.senior.scrollstop.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizBankTest {

    private fun assertWellFormed(q: QuizQuestion) {
        assertEquals("four options: ${q.prompt}", 4, q.options.size)
        assertEquals("options are distinct: ${q.options}", 4, q.options.map { it.lowercase() }.toSet().size)
        assertTrue(q.answerIndex in q.options.indices)
    }

    @Test
    fun everyTopicGivesWellFormedQuestions() {
        val random = Random(42)
        QuizTopic.entries.forEach { topic -> repeat(300) { assertWellFormed(QuizBank.next(topic, random)) } }
    }

    @Test
    fun mathAnswersAreCorrect() {
        val random = Random(7)
        repeat(500) {
            val q = QuizBank.next(QuizTopic.MATH, random)
            val (a, op, b) = Regex("""What's (\d+) (.) (\d+)\?""").find(q.prompt)!!.destructured
            val expected = when (op) {
                "+" -> a.toInt() + b.toInt()
                "−" -> a.toInt() - b.toInt()
                else -> a.toInt() * b.toInt()
            }
            assertEquals(q.prompt, expected.toString(), q.answer)
            assertTrue("wrong answers are positive: ${q.options}", q.options.all { it.toInt() > 0 })
        }
    }

    @Test
    fun capitalsQuestionsMatchTheirCountry() {
        val random = Random(3)
        repeat(200) {
            val q = QuizBank.next(QuizTopic.CAPITALS, random)
            val country = q.prompt.removePrefix("What's the capital of ").removeSuffix("?")
            assertEquals(QuizBank.CAPITALS.toMap()[country], q.answer)
        }
    }

    @Test
    fun answersAreUniqueSoOnlyOneOptionIsRight() {
        listOf(QuizBank.CAPITALS, QuizBank.VOCABULARY).forEach { pairs ->
            assertEquals(pairs.size, pairs.map { it.second.lowercase() }.toSet().size)
        }
        QuizBank.SCIENCE.forEach(::assertWellFormed)
    }

    @Test
    fun ownCardsAreUsedOnceThereAreEnough() {
        val cards = listOf("hola" to "hello", "adiós" to "goodbye", "gracias" to "thank you", "perro" to "dog")
            .map { MyCard(it.first, it.second) }
        val random = Random(1)
        repeat(50) {
            val q = QuizBank.next(QuizTopic.MY_CARDS, random, cards)
            assertWellFormed(q)
            assertEquals(cards.first { it.question == q.prompt }.answer, q.answer)
        }
    }

    @Test
    fun tooFewOwnCardsFallBackToMaths() {
        val q = QuizBank.next(QuizTopic.MY_CARDS, Random(1), listOf(MyCard("hola", "hello")))
        assertTrue(q.prompt, q.prompt.startsWith("What's "))
    }

    @Test
    fun progressiveQuizAsksMoreUpToTheCap() {
        assertEquals(1, PauseRules.questionsRequired(progressive = false, attemptsToday = 20))
        assertEquals(1, PauseRules.questionsRequired(progressive = true, attemptsToday = 3))
        assertEquals(2, PauseRules.questionsRequired(progressive = true, attemptsToday = 4))
        assertEquals(PauseRules.MAX_QUESTIONS, PauseRules.questionsRequired(progressive = true, attemptsToday = 50))
    }
}
