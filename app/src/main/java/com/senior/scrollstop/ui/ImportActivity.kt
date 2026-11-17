package com.senior.scrollstop.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.senior.scrollstop.data.BlockerStore
import com.senior.scrollstop.data.DeckParser
import com.senior.scrollstop.data.PauseMode
import com.senior.scrollstop.data.QuizBank
import com.senior.scrollstop.data.QuizTopic

/**
 * Imports a quiz deck from pasted text, a chosen file, or text/files shared from another app.
 * Nothing is saved until the user taps Import, so content shared in from elsewhere only ever
 * fills the preview.
 */
class ImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared = sharedContent(intent)
        val store = BlockerStore(this)
        setContent {
            ScrollStopTheme {
                ImportScreen(
                    initial = shared,
                    readFile = { uri -> readText(this, uri) },
                    onImport = { name, cards ->
                        store.decks.add(name, cards)
                        store.quizTopic = QuizTopic.MY_CARDS
                        val message = if (store.pauseMode == PauseMode.QUIZ) {
                            "Imported ${cards.size} cards into “$name”"
                        } else {
                            "Imported ${cards.size} cards. Switch the pause to Quiz to use them."
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }

    /** Text or a file handed over with Share → ScrollStop. */
    private fun sharedContent(intent: Intent): SourceText? {
        if (intent.action != Intent.ACTION_SEND) return null
        val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (stream != null) return readText(this, stream)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return SourceText(text.take(MAX_CHARS), name = intent.getStringExtra(Intent.EXTRA_SUBJECT), isFile = false)
    }

    companion object {
        private const val MAX_CHARS = 2_000_000

        fun intent(context: Context) = Intent(context, ImportActivity::class.java)

        /** Reads a text file chosen or shared by the user, up to a size limit. */
        fun readText(context: Context, uri: Uri): SourceText? = runCatching {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().use { reader ->
                    val buffer = CharArray(MAX_CHARS)
                    val read = reader.read(buffer).coerceAtLeast(0)
                    String(buffer, 0, read)
                }
            } ?: return null
            SourceText(text.removePrefix("\uFEFF"), name, isFile = true)
        }.getOrNull()
    }
}

/** Text to import, with the file or subject name it came with, if any. */
data class SourceText(val text: String, val name: String?, val isFile: Boolean)

@Composable
private fun ImportScreen(
    initial: SourceText?,
    readFile: (Uri) -> SourceText?,
    onImport: (name: String, cards: List<com.senior.scrollstop.data.MyCard>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    // Anything shared or chosen is shown as one block rather than dumped into the paste box.
    var file by remember { mutableStateOf(initial) }
    var pasted by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(deckName(initial?.name)) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val source = readFile(uri)
        if (source == null) {
            Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
        } else {
            file = source
            if (name.isBlank() || name == "My deck") name = deckName(source.name)
        }
    }

    val text = file?.text ?: pasted
    val result = remember(text, file) { DeckParser.parse(text, file?.name?.takeIf { file?.isFile == true }) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Import a deck", style = MaterialTheme.typography.headlineLarge)
        Text(
            "One card per line: the question, then a tab or a spaced dash ( - ), then the answer. " +
                "CSV files work too.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(name, { name = it }, label = { Text("Deck name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        val chosen = file
        if (chosen != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (chosen.isFile) chosen.name ?: "Chosen file" else "Shared text",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { file = null }) { Text("Remove") }
            }
        } else {
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text("Paste cards here") },
                placeholder = { Text("hola - hello\nperro - dog") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = {
                pick.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "text/tab-separated-values"))
            }) { Text("Choose a file", color = MaterialTheme.colorScheme.onSurface) }
        }

        if (text.isBlank()) {
            Tips()
        } else {
            Preview(result)
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { onImport(name, result.cards) },
            enabled = result.cards.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(if (result.cards.isEmpty()) "Import" else "Import ${result.cards.size} cards") }
        TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Preview(result: DeckParser.Result) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (result.cards.size) {
                0 -> "No cards found yet."
                1 -> "1 card found."
                else -> "${result.cards.size} cards found."
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        result.cards.take(3).forEach { card ->
            Column {
                Text(card.question, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(card.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (result.skippedLines.isNotEmpty()) {
            val shown = result.skippedLines.take(8).joinToString(", ")
            val more = if (result.skippedLines.size > 8) " and ${result.skippedLines.size - 8} more" else ""
            val lines = if (result.skippedLines.size == 1) "1 line" else "${result.skippedLines.size} lines"
            Quiet("Skipped $lines without a question and an answer: line $shown$more.")
        }
        val answers = result.cards.map { it.answer.lowercase() }.toSet().size
        if (result.cards.isNotEmpty() && answers < QuizBank.MIN_CARDS) {
            Quiet("Quizzes need at least ${QuizBank.MIN_CARDS} different answers. Until then you'll get mental maths.")
        }
    }
}

@Composable
private fun Tips() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Where to get cards", style = MaterialTheme.typography.titleLarge)
        Quiet("Quizlet: open a set, tap ⋯ → Export, then copy the text and paste it here.")
        Quiet("Anki: File → Export → Notes in Plain Text, then choose the file.")
        Quiet("Any chatbot: ask “Make 40 flashcards about <your topic>, one per line, as question - answer”, then paste the reply.")
        Quiet("Or share text or a file from another app straight to ScrollStop.")
    }
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "Spanish verbs.csv" → "Spanish verbs". */
private fun deckName(source: String?): String =
    source?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() } ?: "My deck"
