package com.senior.scrollstop.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.senior.scrollstop.data.BlockerStore
import com.senior.scrollstop.data.MyCard
import com.senior.scrollstop.data.QuizTopic
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportActivityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun share(text: String, subject: String? = null) = Intent(context, ImportActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .apply { if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject) }

    @Test
    fun sharedTextIsPreviewedAndImportedAsTheQuizDeck() {
        ActivityScenario.launch<ImportActivity>(share("hola\thello\nperro - dog\nnot a card\ngato,cat", subject = "Spanish")).use {
            compose.onNodeWithText("2 cards found.").assertExists()
            compose.onNodeWithText("Skipped 2 lines without a question and an answer: line 3, 4.").assertExists()
            compose.onNodeWithText("Import 2 cards").performScrollTo().performClick()
            compose.waitForIdle()
        }
        val store = BlockerStore(context)
        assertEquals("Spanish", store.decks.selectedDeck?.name)
        assertEquals(listOf(MyCard("hola", "hello"), MyCard("perro", "dog")), store.decks.selectedDeck?.cards)
        assertEquals(QuizTopic.MY_CARDS, store.quizTopic)
    }

    @Test
    fun nothingIsSavedWithoutTappingImport() {
        ActivityScenario.launch<ImportActivity>(share("a - b")).use {
            compose.onNodeWithText("1 card found.").assertExists()
        }
        assertEquals(emptyList<Any>(), BlockerStore(context).decks.decks)
    }

    @Test
    fun emptyImportIsDisabledAndShowsWhereToGetCards() {
        ActivityScenario.launch<ImportActivity>(Intent(context, ImportActivity::class.java)).use {
            compose.onNodeWithText("Where to get cards").assertExists()
            compose.onNodeWithText("Import").assertIsNotEnabled()
        }
    }
}
