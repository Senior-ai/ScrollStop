package com.senior.scrollstop.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlockerStoreTest {

    private val store = BlockerStore(ApplicationProvider.getApplicationContext())

    @Test
    fun quizSettingsDefaultToBreathingAndMaths() {
        assertEquals(PauseMode.BREATHE, store.pauseMode)
        assertEquals(QuizTopic.MATH, store.quizTopic)
    }

    @Test
    fun cardsSurviveTabsAndNewlinesInTheirText() {
        store.myCards = listOf(MyCard("two\twords", "line\nbreak"), MyCard("hola", "hello"))
        assertEquals(listOf(MyCard("two words", "line break"), MyCard("hola", "hello")), store.myCards)
    }

    @Test
    fun quizSettingsAreSaved() {
        store.pauseMode = PauseMode.QUIZ
        store.quizTopic = QuizTopic.CAPITALS
        assertEquals(PauseMode.QUIZ, store.pauseMode)
        assertEquals(QuizTopic.CAPITALS, store.quizTopic)
    }
}
