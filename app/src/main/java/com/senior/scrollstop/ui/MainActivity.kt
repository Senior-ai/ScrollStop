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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Shape
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
import com.senior.scrollstop.data.PauseRules
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

private val GroupRadius = 24.dp

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
    ) {
        item {
            Text(
                "ScrollStop",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                "A breath before the scroll.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 24.dp),
            )
        }

        item { if (serviceEnabled) ActiveBanner() else SetupCard() }

        item { Spacer(Modifier.height(12.dp)) }
        item { StatsRow(totals) }

        section(
            "Reels & Shorts",
            "Pause only the endless video feed. Messages, search and the rest of the app keep working.",
        )
        val featureList = ShortFormFeatures.ALL
        itemsIndexed(featureList, key = { _, f -> f.key }) { index, feature ->
            GroupRow(index, featureList.size) {
                SwitchRow(
                    title = feature.label,
                    subtitle = "Say no and ScrollStop backs you out of the feed",
                    checked = feature.key in features,
                ) { on ->
                    store.setFeatureEnabled(feature.key, on)
                    features = store.enabledFeatures
                }
            }
        }

        section("Pause", null)
        item { PauseSettings(store) }

        section("Whole apps", "Pause before the app opens at all. Best for apps that are all feed, like TikTok.")
        item {
            SearchField(query) { query = it }
            Spacer(Modifier.height(8.dp))
        }
        if (apps == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
            GroupRow(index, shown.size) {
                AppRow(app, checked = app.packageName in guarded) { on ->
                    store.setGuarded(app.packageName, on)
                    guarded = store.guardedPackages
                }
            }
        }
    }
}

private fun LazyListScope.section(title: String, description: String?) {
    item {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** One row of a rounded group, One UI style: only the first and last rows get rounded corners. */
@Composable
private fun GroupRow(index: Int, count: Int, content: @Composable () -> Unit) {
    val top = if (index == 0) GroupRadius else 0.dp
    val bottom = if (index == count - 1) GroupRadius else 0.dp
    val shape: Shape = RoundedCornerShape(top, top, bottom, bottom)
    Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        content()
        if (index < count - 1) {
            HorizontalDivider(
                Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SetupCard() {
    val context = LocalContext.current
    val needsRestrictedStep = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupRadius))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        Text("Finish setup", style = MaterialTheme.typography.titleLarge, color = onContainer)
        Text(
            "ScrollStop uses Accessibility to notice when you open Reels, Shorts or a guarded app. " +
                "It never reads what you type or see, and it has no internet access.",
            color = onContainer,
        )
        var step = 1
        if (needsRestrictedStep) {
            SetupStep(
                number = step++,
                title = "Allow restricted settings",
                detail = "Android blocks Accessibility for apps installed outside the Play Store until you allow it. " +
                    "In App info, tap ⋮ (top right), then Allow restricted settings. This applies to ScrollStop only.",
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
            detail = "In Accessibility, open Installed apps → ScrollStop pause and switch it on.",
            action = "Open Accessibility",
            primary = true,
        ) { context.startActivity(BlockerAccessibilityService.settingsIntent()) }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, detail: String, action: String, primary: Boolean, onClick: () -> Unit) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Row {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(onContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = MaterialTheme.colorScheme.primaryContainer, style = MaterialTheme.typography.labelLarge)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = onContainer)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = onContainer.copy(alpha = 0.8f))
            Spacer(Modifier.height(8.dp))
            if (primary) {
                Button(onClick = onClick) { Text(action) }
            } else {
                OutlinedButton(onClick = onClick) { Text(action, color = onContainer) }
            }
        }
    }
}

@Composable
private fun ActiveBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupRadius))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            "ScrollStop is on",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun StatsRow(totals: BlockerStore.DailyTotals) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("${totals.attempts}", "pauses today", Modifier.weight(1f).fillMaxHeight())
        StatTile("${totals.resisted}", "times you said no", Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(GroupRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PauseSettings(store: BlockerStore) {
    var pause by remember { mutableFloatStateOf(store.basePauseSeconds.toFloat()) }
    var progressive by remember { mutableStateOf(store.progressivePause) }
    Group {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Breathing pause", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "${pause.toInt()} seconds",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = pause,
                onValueChange = { pause = it },
                onValueChangeFinished = { store.basePauseSeconds = pause.toInt() },
                valueRange = PauseRules.MIN_PAUSE_SECONDS.toFloat()..20f,
                steps = 20 - PauseRules.MIN_PAUSE_SECONDS - 1,
            )
        }
        HorizontalDivider(
            Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        SwitchRow(
            title = "Longer each time",
            subtitle = "+${PauseRules.PROGRESSIVE_STEP_SECONDS} s for every repeat open of the same app today",
            checked = progressive,
        ) {
            progressive = it
            store.progressivePause = it
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(GroupRadius),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val icon = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
