@file:OptIn(ExperimentalMaterial3Api::class)

package com.gline

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent { GlineTheme { MainScreen(viewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Google Maps' "Share directions" arrives here as text containing the link. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        viewModel.onShared(text)
    }
}

private val StartColor = Color(0xFF2E7D32)
private val EndColor = Color(0xFFC62828)

@Composable
private fun GlineTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun MainScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun tell(message: String?) {
        if (message != null) scope.launch { snackbar.showSnackbar(message) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        vm.onLocationPermissionResult(grants.values.any { it })
    }
    val locationRequest = vm.locationRequest
    LaunchedEffect(locationRequest) {
        if (locationRequest != null) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("GLine", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Google Maps routes as GPX for Beeline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinkInput(
                vm = vm,
                onPaste = {
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (text.isNullOrBlank()) tell("The clipboard is empty.") else vm.onShared(text)
                },
            )

            ChoiceRow("Travel mode") {
                FilterChip(selected = vm.modeChoice == null, onClick = { vm.chooseMode(null) }, label = { Text("From link") })
                TravelMode.entries.forEach { mode ->
                    FilterChip(selected = vm.modeChoice == mode, onClick = { vm.chooseMode(mode) }, label = { Text(mode.label) })
                }
            }

            val hasKey = vm.googleKey.isNotBlank()
            val engine = if (vm.engine == RoutingEngine.GOOGLE && !hasKey) RoutingEngine.OSM else vm.engine
            ChoiceRow("Roads between stops from") {
                RoutingEngine.entries.forEach { choice ->
                    FilterChip(
                        selected = engine == choice,
                        enabled = choice != RoutingEngine.GOOGLE || hasKey,
                        onClick = { vm.chooseEngine(choice) },
                        label = { Text(if (choice == RoutingEngine.GOOGLE && !hasKey) "Google (needs key)" else choice.label) },
                    )
                }
            }

            vm.progress?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(message)
                }
            }

            vm.error?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            vm.result?.let { result ->
                ResultCard(
                    result = result,
                    onOpen = { tell(GpxFiles.openInBeeline(context, result)) },
                    onShare = { GpxFiles.share(context, result) },
                    onSave = {
                        tell(
                            try {
                                GpxFiles.saveToDownloads(context, result)
                            } catch (e: Exception) {
                                "Couldn't save: ${e.message}"
                            },
                        )
                    },
                )
            }

            if (vm.result == null && vm.progress == null) HowTo()

            GoogleSettings(vm)
        }
    }
}

@Composable
private fun LinkInput(vm: MainViewModel, onPaste: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = vm.input,
            onValueChange = { vm.input = it },
            label = { Text("Google Maps link") },
            placeholder = { Text("https://maps.app.goo.gl/…") },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.convert() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) { Text("Paste") }
            Button(
                onClick = { vm.convert() },
                enabled = vm.input.isNotBlank() && vm.progress == null,
                modifier = Modifier.weight(1f),
            ) { Text("Convert") }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, chips: @Composable RowScope.() -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = chips,
        )
    }
}

@Composable
private fun ResultCard(result: Conversion, onOpen: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(result.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOf(
                        formatDistance(result.path.distanceMeters),
                        formatDuration(result.path.durationSeconds),
                        result.mode.label,
                        "roads from ${result.path.engine}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            RoutePreview(
                result.path.points,
                result.stops,
                Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )

            StopList(result.stops)

            result.notes.forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open in Beeline") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share…") }
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

@Composable
private fun StopList(stops: List<Stop>) {
    val real = stops.filterNot { it.isVia }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        real.forEachIndexed { i, stop ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(stopColor(i, real.size), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (i < 26) "${'A' + i}" else "${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Text(stop.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
        val vias = stops.size - real.size
        if (vias > 0) {
            Text(
                "Also passes through $vias point${if (vias == 1) "" else "s"} the route was dragged to in Maps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun stopColor(index: Int, count: Int): Color = when (index) {
    0 -> StartColor
    count - 1 -> EndColor
    else -> MaterialTheme.colorScheme.tertiary
}

/** The route's shape, so it can be checked against Maps before importing. */
@Composable
private fun RoutePreview(points: List<LatLng>, stops: List<Stop>, modifier: Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val viaColor = MaterialTheme.colorScheme.outline
    val middleColor = MaterialTheme.colorScheme.tertiary
    val background = MaterialTheme.colorScheme.surfaceVariant
    val realStops = stops.filterNot { it.isVia }

    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background),
    ) {
        val all = points + stops.map { it.position }
        if (all.isEmpty()) return@Canvas

        // Equirectangular projection, squeezed by latitude so shapes aren't stretched.
        val xScale = cos(Math.toRadians(all.map { it.lat }.average()))
        val minX = all.minOf { it.lng * xScale }
        val maxX = all.maxOf { it.lng * xScale }
        val minY = all.minOf { it.lat }
        val maxY = all.maxOf { it.lat }
        val pad = 20.dp.toPx()
        val width = size.width - 2 * pad
        val height = size.height - 2 * pad
        val spanX = (maxX - minX).coerceAtLeast(1e-9)
        val spanY = (maxY - minY).coerceAtLeast(1e-9)
        val scale = min(width / spanX, height / spanY)
        val left = pad + (width - spanX * scale) / 2
        val top = pad + (height - spanY * scale) / 2
        fun project(p: LatLng) = Offset(
            (left + (p.lng * xScale - minX) * scale).toFloat(),
            (top + (maxY - p.lat) * scale).toFloat(),
        )

        val step = (points.size / 3000).coerceAtLeast(1)
        val line = Path()
        points.forEachIndexed { i, p ->
            if (i % step != 0 && i != points.lastIndex) return@forEachIndexed
            val o = project(p)
            if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y)
        }
        drawPath(line, lineColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        stops.filter { it.isVia }.forEach { drawCircle(viaColor, radius = 3.dp.toPx(), center = project(it.position)) }
        realStops.forEachIndexed { i, stop ->
            val color = when (i) {
                0 -> StartColor
                realStops.lastIndex -> EndColor
                else -> middleColor
            }
            drawCircle(Color.White, radius = 7.dp.toPx(), center = project(stop.position))
            drawCircle(color, radius = 5.dp.toPx(), center = project(stop.position))
        }
    }
}

@Composable
private fun HowTo() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How to use", style = MaterialTheme.typography.titleMedium)
            Text("1. In Google Maps, tap Directions and plan the route: add stops and pick driving, cycling or walking.")
            Text("2. Tap ⋮ (top right), then Share directions, and choose GLine.")
            Text("3. Check the preview and tap Open in Beeline, then pick an import mode in Beeline.")
            Text(
                "You can also paste a Google Maps directions link above, such as one copied from a browser.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoogleSettings(vm: MainViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide Google routing" else "Use Google's exact roads (optional)")
        }
        if (expanded) {
            Text(
                "GLine plans the roads between your stops with OpenStreetMap, which is free and needs no setup. " +
                    "It goes through the same stops as your Google route, but may pick different roads between them.\n\n" +
                    "For the roads Google Maps would use, paste a Google Maps Platform API key with the Routes API " +
                    "enabled. Google bills per request after a free monthly allowance. The key stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = vm.googleKey,
                onValueChange = vm::updateGoogleKey,
                label = { Text("Google API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// A US-English phone can be riding anywhere, so miles go alongside kilometres, never instead.
private val usesMiles = Locale.getDefault().country in setOf("US", "GB", "LR", "MM")

private fun formatDistance(meters: Double): String {
    val metric = if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1000)
    return if (usesMiles) "$metric (${String.format(Locale.getDefault(), "%.1f mi", meters / 1609.344)})" else metric
}

private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt()
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
