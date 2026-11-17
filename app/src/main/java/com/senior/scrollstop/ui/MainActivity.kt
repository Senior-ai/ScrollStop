package com.senior.scrollstop.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.senior.scrollstop.CrashLog
import com.senior.scrollstop.data.AppInfo
import com.senior.scrollstop.data.BlockerStore
import com.senior.scrollstop.data.InstalledApps
import com.senior.scrollstop.data.Deck
import com.senior.scrollstop.data.MyCard
import com.senior.scrollstop.data.PauseMode
import com.senior.scrollstop.data.PauseRules
import com.senior.scrollstop.data.QuizBank
import com.senior.scrollstop.data.QuizTopic
import com.senior.scrollstop.data.ShortFormFeatures
import com.senior.scrollstop.service.BlockerAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Setup and settings: permission status, today's stats, pause settings and the app picker. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crash = CrashLog.read(this)
        if (crash != null) {
            setContentView(crashReportView(this, crash, onDismiss = ::showMain))
        } else {
            showMain()
        }
    }

    private fun showMain() {
        val store = BlockerStore(this)
        setContent { ScrollStopTheme { MainScreen(store) } }
    }
}

/** Rounding for the few raised elements (setup block, search). Rows themselves are flat. */
private val Raised = RoundedCornerShape(28.dp)
private val Gutter = 24.dp

@Composable
private fun MainScreen(store: BlockerStore) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(false) }
    var totals by remember { mutableStateOf(store.todayTotals()) }
    var guarded by remember { mutableStateOf(store.guardedPackages) }
    var features by remember { mutableStateOf(store.enabledFeatures) }
    var query by remember { mutableStateOf("") }

    // Permission and stats change while we're in the background (Settings, the pause screen).
    LifecycleResumeEffect(Unit) {
        serviceEnabled = BlockerAccessibilityService.isEnabled(context)
        totals = store.todayTotals()
        onPauseOrDispose {}
    }

    val apps by produceState<List<AppInfo>?>(null) {
        value = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }

    val shown = remember(apps, guarded, query) {
        apps.orEmpty()
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .sortedWith(
                compareByDescending<AppInfo> { it.packageName in guarded }
                    .thenByDescending { it.packageName in BlockerStore.SUGGESTED_PACKAGES }
                    .thenBy { it.label.lowercase() },
            )
    }

    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(top = 32.dp, bottom = 40.dp),
    ) {
        item { Header(totals, serviceEnabled) }
        if (!serviceEnabled) item { SetupBlock() }

        section(
            "Reels & Shorts",
            "Pauses only the video feed. Messages, search and everything else keep working.",
        )
        val featureList = ShortFormFeatures.ALL
        itemsIndexed(featureList, key = { _, f -> f.key }) { index, feature ->
            SwitchRow(feature.label, null, feature.key in features, divider = index < featureList.lastIndex) { on ->
                store.setFeatureEnabled(feature.key, on)
                features = store.enabledFeatures
            }
        }

        section("The pause", null)
        item { PauseSettings(store) }

        section("Whole apps", "Pauses before the app opens at all. Best for apps that are all feed, like TikTok.")
        item {
            SearchField(query, Modifier.padding(horizontal = Gutter - 8.dp, vertical = 4.dp)) { query = it }
        }
        if (apps == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
            AppRow(app, checked = app.packageName in guarded, divider = index < shown.lastIndex) { on ->
                store.setGuarded(app.packageName, on)
                guarded = store.guardedPackages
            }
        }
    }
}

/** The app name, then today in one sentence instead of a dashboard. */
@Composable
private fun Header(totals: BlockerStore.DailyTotals, serviceEnabled: Boolean) {
    Column(Modifier.padding(horizontal = Gutter)) {
        Text("ScrollStop", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))
        Text(todaySentence(totals), style = MaterialTheme.typography.headlineSmall)
        if (serviceEnabled) {
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Text(
                    "On and watching for Reels and Shorts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

private fun todaySentence(t: BlockerStore.DailyTotals): String = when {
    t.attempts == 0 -> "No pauses yet today."
    t.resisted == 0 -> "Today you paused ${times(t.attempts)}."
    else -> "Today you paused ${times(t.attempts)} and walked away ${times(t.resisted)}."
}

private fun times(n: Int) = if (n == 1) "once" else "$n times"

private fun LazyListScope.section(title: String, description: String?) {
    item {
        Column(Modifier.padding(start = Gutter, end = Gutter, top = 36.dp, bottom = 6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Hairline() {
    HorizontalDivider(Modifier.padding(horizontal = Gutter), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, divider: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = Gutter, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    if (divider) Hairline()
}

/** The one raised block on the screen: setup is a real sequence, so the steps are numbered. */
@Composable
private fun SetupBlock() {
    val context = LocalContext.current
    val needsRestrictedStep = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    Column(
        Modifier
            .padding(horizontal = Gutter - 8.dp)
            .fillMaxWidth()
            .clip(Raised)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text("Two steps to switch it on", style = MaterialTheme.typography.titleLarge)
            Text(
                "ScrollStop needs Accessibility to notice Reels and Shorts. It never reads what you type or see, " +
                    "and it has no internet access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        var step = 1
        if (needsRestrictedStep) {
            SetupStep(
                number = step++,
                title = "Allow restricted settings",
                detail = "In App info, tap ⋮ at the top right, then Allow restricted settings. This applies to ScrollStop only.",
                action = "Open App info",
                primary = false,
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            }
        }
        SetupStep(
            number = step,
            title = "Turn on ScrollStop pause",
            detail = "In Accessibility, open Installed apps, then ScrollStop pause.",
            action = "Open Accessibility",
            primary = true,
        ) { context.startActivity(BlockerAccessibilityService.settingsIntent()) }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, detail: String, action: String, primary: Boolean, onClick: () -> Unit) {
    Row {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Fraunces),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (primary) {
                Button(onClick = onClick) { Text(action) }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) { Text(action, color = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

@Composable
private fun PauseSettings(store: BlockerStore) {
    var mode by remember { mutableStateOf(store.pauseMode) }
    var topic by remember { mutableStateOf(store.quizTopic) }
    var pause by remember { mutableFloatStateOf(store.basePauseSeconds.toFloat()) }
    var progressive by remember { mutableStateOf(store.progressivePause) }

    ChoiceRow("Breathe", "A slow countdown while the tide rises and falls", mode == PauseMode.BREATHE) {
        mode = PauseMode.BREATHE
        store.pauseMode = mode
    }
    ChoiceRow("Quiz", "Answer a question before you can carry on", mode == PauseMode.QUIZ) {
        mode = PauseMode.QUIZ
        store.pauseMode = mode
    }
    Hairline()

    if (mode == PauseMode.BREATHE) {
        Column(Modifier.padding(start = Gutter, end = Gutter, top = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Breathe for", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${pause.toInt()} s", style = MaterialTheme.typography.headlineSmall)
            }
            Slider(
                value = pause,
                onValueChange = { pause = it },
                onValueChangeFinished = { store.basePauseSeconds = pause.toInt() },
                valueRange = PauseRules.MIN_PAUSE_SECONDS.toFloat()..20f,
                steps = 20 - PauseRules.MIN_PAUSE_SECONDS - 1,
            )
        }
    } else {
        Text(
            "Quiz topic",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Gutter, end = Gutter, top = 16.dp, bottom = 4.dp),
        )
        QuizTopic.entries.forEach { option ->
            ChoiceRow(option.label, option.description, topic == option) {
                topic = option
                store.quizTopic = option
            }
        }
        if (topic == QuizTopic.MY_CARDS) DecksEditor(store)
    }
    Hairline()

    SwitchRow(
        title = "Harder each time",
        subtitle = if (mode == PauseMode.BREATHE) {
            "${PauseRules.PROGRESSIVE_STEP_SECONDS} more seconds each time you reopen the same thing today"
        } else {
            "One more question every ${PauseRules.OPENS_PER_EXTRA_QUESTION} reopens today, up to ${PauseRules.MAX_QUESTIONS}"
        },
        checked = progressive,
        divider = false,
    ) {
        progressive = it
        store.progressivePause = it
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = Gutter - 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The user's quiz decks for the "My decks" topic: pick one, import more, or add a card by hand. */
@Composable
private fun DecksEditor(store: BlockerStore) {
    val context = LocalContext.current
    var decks by remember { mutableStateOf(store.decks.decks) }
    var selected by remember { mutableStateOf(store.decks.selectedDeck?.id) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Deck?>(null) }

    fun refresh() {
        decks = store.decks.decks
        selected = store.decks.selectedDeck?.id
    }
    // Imports happen in their own screen; pick them up when we come back.
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose {}
    }

    val current = decks.firstOrNull { it.id == selected }
    Column(Modifier.padding(start = Gutter - 12.dp, end = Gutter - 12.dp, top = 4.dp, bottom = 8.dp)) {
        if (decks.isEmpty()) {
            Text(
                "No decks yet. Import one from Quizlet, Anki, a file or a chatbot, or add cards by hand.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        decks.forEach { deck ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        store.decks.selectedDeckId = deck.id
                        refresh()
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = deck.id == selected, onClick = {
                    store.decks.selectedDeckId = deck.id
                    refresh()
                })
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(deck.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (deck.cards.size == 1) "1 card" else "${deck.cards.size} cards",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { deleting = deck }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${deck.name}")
                }
            }
        }
        if (current != null && current.cards.map { it.answer.lowercase() }.toSet().size < QuizBank.MIN_CARDS) {
            Text(
                "“${current.name}” needs at least ${QuizBank.MIN_CARDS} different answers for a quiz. " +
                    "Until then you'll get mental maths.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Row(Modifier.padding(start = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { context.startActivity(ImportActivity.intent(context)) }) { Text("Import a deck") }
            OutlinedButton(
                onClick = { adding = true },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) { Text("Add a card", color = MaterialTheme.colorScheme.onSurface) }
        }
    }

    if (adding) {
        AddCardDialog(
            deckName = current?.name ?: "My cards",
            onDismiss = { adding = false },
            onAdd = { card ->
                store.decks.addCard(card)
                refresh()
                adding = false
            },
        )
    }
    deleting?.let { deck ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${deck.name}”?") },
            text = { Text("Its ${deck.cards.size} cards will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    store.decks.delete(deck.id)
                    refresh()
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddCardDialog(deckName: String, onDismiss: () -> Unit, onAdd: (MyCard) -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a card to “$deckName”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(question, { question = it }, label = { Text("Question") }, placeholder = { Text("Hola") })
                OutlinedTextField(answer, { answer = it }, label = { Text("Answer") }, placeholder = { Text("Hello") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(MyCard(question.trim(), answer.trim())) },
                enabled = question.isNotBlank() && answer.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SearchField(query: String, modifier: Modifier, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = Raised,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, divider: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val icon = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    if (divider) Hairline()
}
