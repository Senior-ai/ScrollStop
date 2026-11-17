package com.senior.scrollstop.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlockerStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = BlockerStore(context)

    @Test
    fun quizSettingsDefaultToBreathingAndMaths() {
        assertEquals(PauseMode.BREATHE, store.pauseMode)
        assertEquals(QuizTopic.MATH, store.quizTopic)
    }

    @Test
    fun quizSettingsAreSaved() {
        store.pauseMode = PauseMode.QUIZ
        store.quizTopic = QuizTopic.CAPITALS
        assertEquals(PauseMode.QUIZ, store.pauseMode)
        assertEquals(QuizTopic.CAPITALS, store.quizTopic)
    }

    @Test
    fun importedDeckIsSavedAndBecomesTheQuizDeck() {
        val spanish = store.decks.add("Spanish", listOf(MyCard("hola", "hello"), MyCard("perro", "dog")))
        store.decks.add("Pharmacology", listOf(MyCard("Aspirin class", "NSAID")))
        val reopened = BlockerStore(context).decks
        assertEquals(listOf("Spanish", "Pharmacology"), reopened.decks.map { it.name })
        assertEquals("Pharmacology", reopened.selectedDeck?.name)

        reopened.selectedDeckId = spanish.id
        assertEquals(spanish.cards, reopened.selectedDeck?.cards)
    }

    @Test
    fun deletingTheQuizDeckFallsBackToAnother() {
        val first = store.decks.add("A", listOf(MyCard("q", "a")))
        val second = store.decks.add("B", listOf(MyCard("q", "b")))
        store.decks.delete(second.id)
        assertEquals(first.id, store.decks.selectedDeck?.id)
        store.decks.delete(first.id)
        assertNull(store.decks.selectedDeck)
    }

    @Test
    fun addingACardWithNoDecksCreatesMyCards() {
        store.decks.addCard(MyCard("hola", "hello"))
        assertEquals("My cards", store.decks.selectedDeck?.name)
        store.decks.addCard(MyCard("adiós", "goodbye"))
        assertEquals(2, store.decks.selectedDeck?.cards?.size)
    }

    @Test
    fun cardsFromVersion17MoveIntoADeck() {
        context.getSharedPreferences("scrollstop", Context.MODE_PRIVATE).edit()
            .putString("my_cards", "hola\thello\nperro\tdog").commit()
        val deck = BlockerStore(context).decks.selectedDeck
        assertEquals("My cards", deck?.name)
        assertEquals(listOf(MyCard("hola", "hello"), MyCard("perro", "dog")), deck?.cards)
    }
}
