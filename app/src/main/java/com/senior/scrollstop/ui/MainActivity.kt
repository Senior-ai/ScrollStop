package com.senior.scrollstop.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.senior.scrollstop.data.AppInfo
import com.senior.scrollstop.data.BlockerStore
import com.senior.scrollstop.data.InstalledApps
import com.senior.scrollstop.data.PauseRules
import com.senior.scrollstop.service.BlockerAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Setup and settings: permission status, today's stats, pause settings and the app picker. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = BlockerStore(this)
        setContent { ScrollStopTheme { MainScreen(store) } }
    }
}

@Composable
private fun MainScreen(store: BlockerStore) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(false) }
    var totals by remember { mutableStateOf(store.todayTotals()) }
    var guarded by remember { mutableStateOf(store.guardedPackages) }
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
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            Text("ScrollStop", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "A breath before the scroll.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!serviceEnabled) item { PermissionCard() }
        item { StatsCard(totals, guarded.isEmpty()) }
        item { SettingsCard(store) }
        item {
            Text("Apps to guard", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        if (apps == null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        items(shown, key = { it.packageName }) { app ->
            AppRow(app, checked = app.packageName in guarded) { on ->
                store.setGuarded(app.packageName, on)
                guarded = store.guardedPackages
            }
        }
    }
}

@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Turn on ScrollStop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "ScrollStop needs Accessibility access to notice when you open a guarded app. " +
                    "It only looks at which app is open, never at what's on screen.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { context.startActivity(BlockerAccessibilityService.settingsIntent()) }) {
                Text("Open Accessibility settings")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    "Greyed out? Android blocks this for sideloaded apps until you open App info, " +
                        "tap ⋮ in the top corner and choose \"Allow restricted settings\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Open App info") }
            }
        }
    }
}

@Composable
private fun StatsCard(totals: BlockerStore.DailyTotals, nothingGuarded: Boolean) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("${totals.attempts}", "pauses today")
            Stat("${totals.resisted}", "times you walked away")
        }
        if (nothingGuarded) {
            Text(
                "Pick a few apps below to get started.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsCard(store: BlockerStore) {
    var pause by remember { mutableFloatStateOf(store.basePauseSeconds.toFloat()) }
    var progressive by remember { mutableStateOf(store.progressivePause) }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Pause length: ${pause.toInt()} s", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = pause,
                onValueChange = { pause = it },
                onValueChangeFinished = { store.basePauseSeconds = pause.toInt() },
                valueRange = PauseRules.MIN_PAUSE_SECONDS.toFloat()..20f,
                steps = 20 - PauseRules.MIN_PAUSE_SECONDS - 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Longer pause each time", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "+${PauseRules.PROGRESSIVE_STEP_SECONDS} s for every repeat open of the same app today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = progressive, onCheckedChange = {
                    progressive = it
                    store.progressivePause = it
                })
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val icon = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
