package com.senior.scrollstop.data

import android.content.Context
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Deck(val id: String, val name: String, val cards: List<MyCard>)

/**
 * The user's quiz decks, kept in one JSON file in app-private storage (excluded from backups,
 * like everything else). Also remembers which deck the quiz uses.
 */
class DeckStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "decks.json")
    private val prefs = context.applicationContext.getSharedPreferences("scrollstop", Context.MODE_PRIVATE)

    val decks: List<Deck>
        get() = load()

    var selectedDeckId: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    /** The deck the quiz uses: the chosen one, or the first if that was deleted. */
    val selectedDeck: Deck?
        get() = decks.let { all -> all.firstOrNull { it.id == selectedDeckId } ?: all.firstOrNull() }

    /** Saves a new deck, makes it the quiz deck and returns it. */
    fun add(name: String, cards: List<MyCard>): Deck {
        val deck = Deck(UUID.randomUUID().toString(), name.trim().ifEmpty { "My deck" }, cards)
        save(decks + deck)
        selectedDeckId = deck.id
        return deck
    }

    fun delete(id: String) {
        save(decks.filterNot { it.id == id })
        if (selectedDeckId == id) selectedDeckId = null
    }

    /** Adds a card to the quiz deck, creating "My cards" if there are no decks yet. */
    fun addCard(card: MyCard) {
        val target = selectedDeck ?: run {
            add("My cards", listOf(card))
            return
        }
        save(decks.map { if (it.id == target.id) it.copy(cards = it.cards + card) else it })
    }

    private fun load(): List<Deck> {
        if (!file.exists()) return migrateOldCards()
        return runCatching {
            val array = JSONObject(file.readText()).getJSONArray("decks")
            (0 until array.length()).map { i ->
                val deck = array.getJSONObject(i)
                val cards = deck.getJSONArray("cards")
                Deck(
                    id = deck.getString("id"),
                    name = deck.getString("name"),
                    cards = (0 until cards.length()).map { j ->
                        cards.getJSONArray(j).let { MyCard(it.getString(0), it.getString(1)) }
                    },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(decks: List<Deck>) {
        val json = JSONObject().put(
            "decks",
            JSONArray(decks.map { deck ->
                JSONObject()
                    .put("id", deck.id)
                    .put("name", deck.name)
                    .put("cards", JSONArray(deck.cards.map { JSONArray(listOf(it.question, it.answer)) }))
            }),
        )
        // Write then rename, so a crash mid-write can't leave a half-written file.
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    /** Version 1.7 kept a single card list in settings; turn it into a deck once. */
    private fun migrateOldCards(): List<Deck> {
        val old = prefs.getString(KEY_OLD_CARDS, null).orEmpty().lines()
            .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { MyCard(it[0], it[1]) } }
        prefs.edit().remove(KEY_OLD_CARDS).apply()
        if (old.isEmpty()) return emptyList()
        val deck = Deck(UUID.randomUUID().toString(), "My cards", old)
        save(listOf(deck))
        return listOf(deck)
    }

    companion object {
        private const val KEY_SELECTED = "quiz_deck"
        private const val KEY_OLD_CARDS = "my_cards"
    }
}
