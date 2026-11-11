package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ThemeMode
import com.example.data.Wallpaper
import com.example.data.WallpaperScreen
import com.example.data.WallpaperStatus
import com.example.ui.theme.resolveDark
import com.example.util.WallpaperManagerHelper
import com.example.web.Libraries
import com.example.web.WebViewSetup

/**
 * The app has three pages. Home shows only the preview and the Library; Code and Settings are their
 * own pages, reached from the header.
 */
@Composable
fun MainScreen(vm: MainViewModel) {
    val context = LocalContext.current

    LaunchedEffect(vm.message) {
        val text = vm.message
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            vm.message = null
        }
    }

    // System Back returns to the Home page first and only then leaves the app.
    BackHandler(enabled = vm.screen != AppScreen.HOME) { vm.navigate(AppScreen.HOME) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // key(): every page starts at the top instead of inheriting the previous page's scroll position.
        key(vm.screen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    when (vm.screen) {
                        AppScreen.HOME -> HomePage(vm)
                        AppScreen.CODE -> CodePage(vm)
                        AppScreen.SETTINGS -> SettingsPage(vm)
                    }
                }
            }
        }
    }

    if (vm.fullscreen) FullscreenPreview(vm)
    if (vm.showDestination) DestinationDialog(vm)
}

@Composable
private fun HomePage(vm: MainViewModel) {
    HomeHeader(vm)
    PreviewSection(vm)
    LibrarySection(vm)
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun CodePage(vm: MainViewModel) {
    SubHeader("Code") { vm.navigate(AppScreen.HOME) }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PreviewBox(vm, widthFraction = 0.4f, maxWidth = 190.dp)
        PreviewActions(vm)
    }
    EditorSection(vm)
    DeleteSection(vm)
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun SettingsPage(vm: MainViewModel) {
    SubHeader("Settings") { vm.navigate(AppScreen.HOME) }
    SettingsSection(vm)
    LiveWallpaperSection(vm)
    Footer()
}

// ----------------------------------------------------------------------------------------------
// Header
// ----------------------------------------------------------------------------------------------

@Composable
private fun HomeHeader(vm: MainViewModel) {
    val dark = resolveDark(vm.themeMode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "HTML Fun",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        NavText("Code") { vm.navigate(AppScreen.CODE) }
        NavText("Settings") { vm.navigate(AppScreen.SETTINGS) }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .semantics { contentDescription = "Switch light or dark theme" }
                .clickable { vm.updateTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
            contentAlignment = Alignment.Center
        ) {
            ThemeGlyph(MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun NavText(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    )
}

/** Header of the Code and Settings pages: a back arrow and the page title. */
@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .semantics { contentDescription = "Back" }
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            BackGlyph(MaterialTheme.colorScheme.onBackground)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}

/** A thin chevron, drawn so no icon set is needed. */
@Composable
private fun BackGlyph(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.62f, h * 0.14f), Offset(w * 0.30f, h * 0.50f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.30f, h * 0.50f), Offset(w * 0.62f, h * 0.86f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** A circle that is half filled: the classic "light / dark" symbol, drawn so it needs no icon set. */
@Composable
private fun ThemeGlyph(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.6.dp.toPx()
        val diameter = size.minDimension - stroke
        drawCircle(color = color, radius = diameter / 2f, style = Stroke(width = stroke))
        drawArc(
            color = color,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(diameter, diameter)
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Preview
// ----------------------------------------------------------------------------------------------

/** The phone-shaped preview. Used on the Home page and, smaller, on the Code page. */
@Composable
private fun PreviewBox(vm: MainViewModel, widthFraction: Float, maxWidth: Dp) {
    val colors = MaterialTheme.colorScheme
    val config = LocalConfiguration.current

    // The preview has the same proportions as this device's screen.
    val ratio = remember(config.screenWidthDp, config.screenHeightDp) {
        val long = maxOf(config.screenWidthDp, config.screenHeightDp).toFloat()
        val short = minOf(config.screenWidthDp, config.screenHeightDp).toFloat()
        (long / short).coerceIn(1.3f, 2.4f)
    }
    val previewWidth = minOf(maxWidth, (config.screenWidthDp * widthFraction).dp)

    Box(
        modifier = Modifier
            .width(previewWidth)
            .aspectRatio(1f / ratio)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(22.dp))
            .background(Color.Black)
    ) {
        if (!vm.fullscreen && vm.previewUrl.isNotEmpty()) {
            WebPreview(
                url = vm.previewUrl,
                server = vm.server,
                touchEnabled = vm.touchEnabled,
                onConsole = vm::log,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PreviewActions(vm: MainViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = vm::reload) { Text("Reload") }
        TextButton(onClick = { vm.fullscreen = true }) { Text("Full screen") }
    }
}

@Composable
private fun PreviewSection(vm: MainViewModel) {
    val colors = MaterialTheme.colorScheme
    val wallpapers by vm.wallpapers.collectAsState()
    val selected = wallpapers.firstOrNull { it.id == vm.selectedId }

    val kind = if (selected?.isPreset == true) "Preset" else "Custom"
    val screens = vm.statusOf(vm.selectedId)
    val status = if (screens == WallpaperStatus.NEITHER) kind else kind + " \u00B7 " + screens.label

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PreviewBox(vm, widthFraction = 0.56f, maxWidth = 260.dp)

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = selected?.title ?: "",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Opens the Home / Lock chooser every time, so where a wallpaper goes is always the user's choice.
        Button(
            onClick = { vm.showDestination = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Set as wallpaper", style = MaterialTheme.typography.labelLarge)
        }
        PreviewActions(vm)
    }
}

@Composable
private fun FullscreenPreview(vm: MainViewModel) {
    Dialog(
        onDismissRequest = { vm.fullscreen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)) {
            WebPreview(
                url = vm.previewUrl,
                server = vm.server,
                touchEnabled = vm.touchEnabled,
                onConsole = vm::log,
                modifier = Modifier.fillMaxSize()
            )
            TextButton(
                onClick = { vm.fullscreen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            ) {
                Text("Close", color = Color.White)
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Library
// ----------------------------------------------------------------------------------------------

@Composable
private fun LibrarySection(vm: MainViewModel) {
    val colors = MaterialTheme.colorScheme
    val wallpapers by vm.wallpapers.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importUri(uri)
    }

    SectionLabel("Library")
    wallpapers.forEach { item ->
        key(item.id) {
            LibraryRow(
                item = item,
                selected = item.id == vm.selectedId,
                status = vm.statusOf(item.id),
                onSelect = { vm.select(item.id) }
            )
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = vm::create,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("New") }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("text/html", "application/zip", "*/*")) },
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Import HTML / ZIP") }
    }
}

@Composable
private fun LibraryRow(
    item: Wallpaper,
    selected: Boolean,
    status: WallpaperStatus,
    onSelect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioDot(selected)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.isPreset) "Preset" else "Custom",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        StatusPill(status)
    }
}

/** Home Screen / Lock Screen / Both / Neither. Active states get an outline, "Neither" stays quiet. */
@Composable
private fun StatusPill(status: WallpaperStatus) {
    val colors = MaterialTheme.colorScheme
    val active = status != WallpaperStatus.NEITHER
    Text(
        text = status.label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (active) colors.onSurface else colors.onSurfaceVariant,
        modifier = Modifier
            .then(if (active) Modifier.border(1.dp, colors.onSurface, RoundedCornerShape(50)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun RadioDot(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(18.dp)
            .border(1.5.dp, if (selected) colors.onSurface else colors.outline, CircleShape)
            .padding(4.dp)
    ) {
        if (selected) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(colors.onSurface, CircleShape))
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Editor
// ----------------------------------------------------------------------------------------------

@Composable
private fun EditorSection(vm: MainViewModel) {
    val colors = MaterialTheme.colorScheme
    val wallpapers by vm.wallpapers.collectAsState()
    val isPreset = wallpapers.firstOrNull { it.id == vm.selectedId }?.isPreset == true

    Spacer(modifier = Modifier.height(16.dp))

    BasicTextField(
        value = vm.title,
        onValueChange = { vm.title = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.onSurface),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Column {
                Box(modifier = Modifier.padding(vertical = 10.dp)) {
                    if (vm.title.isEmpty()) {
                        Text(
                            text = "Title",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant
                        )
                    }
                    inner()
                }
                HorizontalDivider(color = colors.outline)
            }
        }
    )

    Spacer(modifier = Modifier.height(14.dp))
    BasicTextField(
        value = vm.code,
        onValueChange = { vm.code = it },
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.onSurface
        ),
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Ascii
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp)
    )

    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Libraries.all.forEach { library ->
            Chip(label = library.name, onClick = { vm.addLibrary(library) })
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = vm::runDraft,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Run") }
        Button(
            onClick = vm::save,
            enabled = isPreset || vm.isDirty,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                when {
                    isPreset -> "Save as copy"
                    vm.isDirty -> "Save"
                    else -> "Saved"
                }
            )
        }
    }

    if (vm.console.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CONSOLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { vm.console.clear() }
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            vm.console.takeLast(8).forEach { line ->
                Text(
                    text = line,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
                    color = if (line.startsWith("error")) colors.error else colors.onSurface
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, colors.outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

// ----------------------------------------------------------------------------------------------
// Settings
// ----------------------------------------------------------------------------------------------

@Composable
private fun SettingsSection(vm: MainViewModel) {
    SettingRow("Frame rate") {
        Segmented(
            options = listOf("30" to 30, "60" to 60, "Max" to 0),
            selected = vm.fps,
            onSelect = vm::updateFps
        )
    }
    ToggleRow(
        title = "Touch input",
        subtitle = "Let the wallpaper react to your touches",
        checked = vm.touchEnabled,
        onChange = vm::updateTouch
    )
    ToggleRow(
        title = "GPU rendering",
        subtitle = "Hardware accelerated, supports WebGL. Turn off if the wallpaper stays blank.",
        checked = vm.gpuRendering,
        onChange = vm::updateGpu
    )
    SettingRow("Appearance") {
        Segmented(
            options = listOf(
                "System" to ThemeMode.SYSTEM,
                "Light" to ThemeMode.LIGHT,
                "Dark" to ThemeMode.DARK
            ),
            selected = vm.themeMode,
            onSelect = vm::updateTheme
        )
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                checkedBorderColor = colors.primary,
                uncheckedThumbColor = colors.outline,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = colors.outline
            )
        )
    }
}

/** A plain segmented control: one thin outline, the selected segment filled with ink. */
@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .border(1.dp, colors.outline, shape)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.second == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) colors.primary else Color.Transparent)
                    .clickable { onSelect(option.second) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.first,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.onPrimary else colors.onSurface
                )
            }
            if (index < options.lastIndex) {
                Box(modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.outline))
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Shared bits
// ----------------------------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 32.dp, bottom = 10.dp)
    )
}

@Composable
private fun Footer() {
    val colors = MaterialTheme.colorScheme
    val engine = remember { WebViewSetup.engineVersion() }
    Spacer(modifier = Modifier.height(32.dp))
    HorizontalDivider(color = colors.outlineVariant)
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = "Engine: $engine",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant
    )
    Text(
        text = "Wallpapers run on the system WebView (Chromium). Updating it through Google Play brings the newest HTML, CSS and JavaScript.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    Spacer(modifier = Modifier.height(32.dp))
}

// ----------------------------------------------------------------------------------------------
// Delete (Code page), live wallpaper status (Settings page), Home / Lock chooser
// ----------------------------------------------------------------------------------------------

@Composable
private fun DeleteSection(vm: MainViewModel) {
    val colors = MaterialTheme.colorScheme
    val wallpapers by vm.wallpapers.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val selected = wallpapers.firstOrNull { it.id == vm.selectedId }
    if (selected == null || selected.isPreset) return

    Spacer(modifier = Modifier.height(28.dp))
    HorizontalDivider(color = colors.outlineVariant)
    TextButton(onClick = { confirm = true }) {
        Text("Delete this wallpaper", color = colors.error)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete wallpaper?") },
            text = { Text("\"" + selected.title + "\" and its files will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(selected.id)
                    confirm = false
                    vm.navigate(AppScreen.HOME)
                }) { Text("Delete", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LiveWallpaperSection(vm: MainViewModel) {
    val colors = MaterialTheme.colorScheme
    val wallpapers by vm.wallpapers.collectAsState()
    fun titleOf(id: String?): String = wallpapers.firstOrNull { it.id == id }?.title ?: "Not set"

    SectionLabel("Live wallpaper")
    StatusLine("Home screen", titleOf(vm.shownOn(WallpaperScreen.HOME)))
    StatusLine("Lock screen", titleOf(vm.shownOn(WallpaperScreen.LOCK)))
    Text(
        text = "The Home and Lock screens are separate. Pick a wallpaper in the Library, tap Set as wallpaper " +
            "and choose a screen. In the system dialog pick \"HTML Fun - Home screen\" for the Home screen and " +
            "\"HTML Fun - Lock screen\" for the Lock screen. If your phone offers one live wallpaper for both " +
            "screens, they share it.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun StatusLine(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Asked every time a wallpaper is set: which screen should it go to? */
@Composable
private fun DestinationDialog(vm: MainViewModel) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val wallpapers by vm.wallpapers.collectAsState()
    val selected = wallpapers.firstOrNull { it.id == vm.selectedId }
    fun titleOf(id: String?): String = wallpapers.firstOrNull { it.id == id }?.title ?: "not set"
    val openChooser: (WallpaperScreen) -> Unit = { screen ->
        WallpaperManagerHelper.launchLiveWallpaperChooser(context, screen)
    }

    Dialog(onDismissRequest = { vm.showDestination = false }) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "SET ON",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = selected?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(18.dp))
                DestinationOption(
                    title = "Home screen",
                    current = "Now: " + titleOf(vm.shownOn(WallpaperScreen.HOME))
                ) { vm.applyTo(WallpaperScreen.HOME, openChooser) }
                Spacer(modifier = Modifier.height(10.dp))
                DestinationOption(
                    title = "Lock screen",
                    current = "Now: " + titleOf(vm.shownOn(WallpaperScreen.LOCK))
                ) { vm.applyTo(WallpaperScreen.LOCK, openChooser) }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.showDestination = false }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun DestinationOption(title: String, current: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = current,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
