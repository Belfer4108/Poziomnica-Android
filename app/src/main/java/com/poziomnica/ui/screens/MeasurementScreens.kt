@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.poziomnica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.poziomnica.navigation.Routes
import com.poziomnica.domain.AngleUnit
import com.poziomnica.domain.MeasurementMath
import com.poziomnica.domain.SlopeDirection
import com.poziomnica.domain.SoundMode
import com.poziomnica.ui.components.LinearVial
import com.poziomnica.ui.components.SurfaceBullseye
import com.poziomnica.viewmodel.*

@Composable
fun LinearLevelScreen(nav: NavHostController, vm: LinearLevelViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var targetInput by remember { mutableStateOf("0") }
    DisposableEffect(Unit) { vm.start(); onDispose { vm.stop() } }
    val reading = state.heldReading ?: state.reading
    MeasurementScaffold(nav, "Poziomnica", compact = true) {
        BigValue(MeasurementMath.formatByUnit(reading.linearAngle, state.settings.defaultUnit), state.targetReached, compact = true)
        LinearVial(reading.linearAngle - state.target, state.settings.defaultTolerance, state.settings.indicatorStyle, modifier = Modifier.heightIn(max = 92.dp))
        Text("Pomiar: ${reading.supportEdge}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Cel: ${MeasurementMath.formatByUnit(state.target, state.settings.defaultUnit)}   Różnica: ${MeasurementMath.formatByUnit(reading.linearAngle - state.target, state.settings.defaultUnit)}",
            style = MaterialTheme.typography.bodyMedium
        )
        MeasurementInfo(state, compact = true)
        Controls(vm, compact = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = targetInput,
                onValueChange = { targetInput = it },
                label = { Text("Cel (${state.settings.defaultUnit.label})") },
                suffix = { Text(unitSuffix(state.settings.defaultUnit)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    targetInput.replace(',', '.').toFloatOrNull()?.let {
                        vm.setTarget(it, state.settings.defaultUnit)
                        vm.setSound(true)
                        vm.setSoundMode(SoundMode.CONTINUOUS)
                    }
                },
                modifier = Modifier.height(48.dp)
            ) { Text("Ustaw") }
        }
    }
}

@Composable
fun SurfaceLevelScreen(nav: NavHostController, vm: SurfaceLevelViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { vm.start(); onDispose { vm.stop() } }
    val r = state.heldReading ?: state.reading
    MeasurementScaffold(nav, "Powierzchnia", compact = true) {
        SurfaceBullseye(r.surfaceX, r.surfaceY, state.settings.defaultTolerance, Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("X", "%+.2f°".format(r.surfaceX), Modifier.weight(1f))
            MetricTile("Y", "%+.2f°".format(r.surfaceY), Modifier.weight(1f))
            MetricTile("Odchylenie", "%.2f°".format(r.totalSurfaceDeviation), Modifier.weight(1f))
        }
        Text("Tolerancja ±${state.settings.defaultTolerance}°", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AssistChip(
            onClick = {},
            label = { Text(if (state.targetReached) "Poziom osiągnięty" else "Reguluj nogi lub podkładki tak, aby bąbelek wszedł w centralny okrąg") },
            leadingIcon = { Icon(if (state.targetReached) Icons.Default.CheckCircle else Icons.Default.Adjust, null) }
        )
        SurfaceControls(vm)
    }
}

@Composable
fun PlumbScreen(nav: NavHostController, vm: PlumbViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { vm.start(); onDispose { vm.stop() } }
    val reading = state.heldReading ?: state.reading
    val deviation = 90f - kotlin.math.abs(reading.roll)
    MeasurementScaffold(nav, "Pion", compact = true) {
        BigValue("%+.2f°".format(deviation), state.targetReached, compact = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Krawędź", reading.supportEdge, Modifier.weight(1.35f))
            MetricTile("Tolerancja", "±${state.settings.defaultTolerance}°", Modifier.weight(0.8f))
        }
        LinearVial(deviation, state.settings.defaultTolerance, state.settings.indicatorStyle, vertical = true, modifier = Modifier.align(Alignment.CenterHorizontally).width(154.dp).heightIn(max = 260.dp))
        Text(state.status, color = if (state.targetReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
        PlumbControls(vm)
    }
}

@Composable
fun SlopeScreen(nav: NavHostController, vm: SlopeViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val direction by vm.selectedDirection.collectAsStateWithLifecycle()
    var custom by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(AngleUnit.PERCENT) }
    var useLength by remember { mutableStateOf(false) }
    var lengthInput by remember { mutableStateOf("2.5") }
    var lengthSlider by remember { mutableStateOf(2.5f) }
    DisposableEffect(Unit) { vm.start(); onDispose { vm.stop() } }
    val reading = state.heldReading ?: state.reading
    val difference = reading.linearAngle - state.target
    val lengthMeters = lengthInput.replace(',', '.').toFloatOrNull()?.coerceIn(0f, 500f) ?: lengthSlider
    MeasurementScaffold(nav, "Spadek", compact = true) {
        BigValue(MeasurementMath.formatByUnit(reading.linearAngle, unit), state.targetReached, compact = true)
        Text(
            "Cel ${MeasurementMath.formatByUnit(state.target, unit)}   Różnica ${MeasurementMath.formatByUnit(difference, unit)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            state.status,
            style = MaterialTheme.typography.titleSmall,
            color = if (state.targetReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        LinearVial(difference, state.settings.defaultTolerance, state.settings.indicatorStyle, modifier = Modifier.heightIn(max = 92.dp))
        SlopeControls(vm, unit)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                custom,
                { custom = it },
                label = { Text("Cel (${unit.label})") },
                suffix = { Text(unitSuffix(unit)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { custom.replace(',', '.').toFloatOrNull()?.let { vm.setTarget(it, unit) } },
                modifier = Modifier.height(48.dp)
            ) { Text("Ustaw") }
        }
        SlopeUnitChips(unit) { unit = it }
        SlopePresetChips(unit, vm)
        DirectionControls(direction, vm)
        SlopeLengthPanel(
            enabled = useLength,
            onEnabledChange = { useLength = it },
            lengthInput = lengthInput,
            onLengthInput = {
                lengthInput = it
                it.replace(',', '.').toFloatOrNull()?.let { parsed -> lengthSlider = parsed.coerceIn(0.1f, 20f) }
            },
            lengthSlider = lengthSlider,
            onLengthSlider = {
                lengthSlider = it
                lengthInput = "%.2f".format(it)
            },
            targetDegrees = state.target,
            currentDegrees = reading.linearAngle,
            direction = direction
        )
    }
}

@Composable
fun ProtractorScreen(nav: NavHostController, vm: ProtractorViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val base by vm.baseAngle.collectAsStateWithLifecycle()
    val captured by vm.capturedAngle.collectAsStateWithLifecycle()
    var targetInput by remember { mutableStateOf("") }
    DisposableEffect(Unit) { vm.start(); onDispose { vm.stop() } }
    val reading = state.heldReading ?: state.reading
    val signedLiveAngle = base?.let { normalizeProtractorDelta(reading.linearAngle - it) } ?: reading.linearAngle
    val displayedAngle = captured ?: base?.let { MeasurementMath.angleBetweenSurfaces(it, reading.linearAngle) } ?: reading.linearAngle
    val vialAngle = captured ?: signedLiveAngle
    MeasurementScaffold(nav, "Kątomierz", compact = true) {
        BigValue("%+.2f°".format(displayedAngle), state.targetReached, compact = true)
        LinearVial(vialAngle, state.settings.defaultTolerance, state.settings.indicatorStyle, modifier = Modifier.heightIn(max = 92.dp), scaleDegrees = 90f)
        Text(
            when {
                base == null -> "Najpierw ustaw telefon na powierzchni bazowej i naciśnij Baza."
                captured == null -> "Baza: ${"%+.2f".format(base)}°. Przechyl telefon i naciśnij Nachylenie."
                else -> "Wynik złapany: ${"%+.2f".format(captured)}°. Reset rozpoczyna kolejny pomiar."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ProtractorControls(vm)
        Text("Cel: ${"%+.2f".format(state.target)}°   Tolerancja: ±${state.settings.defaultTolerance}°", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = targetInput,
                onValueChange = { targetInput = it },
                label = { Text("Cel") },
                suffix = { Text("°") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { targetInput.replace(',', '.').toFloatOrNull()?.let(vm::setTarget) }, modifier = Modifier.height(48.dp)) { Text("Ustaw") }
        }
    }
}

@Composable
fun MeasurementScaffold(nav: NavHostController, title: String, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            CompactMeasurementTopBar(nav, title)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(if (compact) 8.dp else 16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
            content = content
        )
    }
}

@Composable
fun CompactMeasurementTopBar(nav: NavHostController, title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(onClick = { nav.navigateHome() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ArrowBack, "Wróć", Modifier.size(19.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1)
        AppMenu(nav)
    }
}

@Composable
fun AppMenu(nav: NavHostController) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.Menu, "Menu") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("FAQ") }, leadingIcon = { Icon(Icons.Default.Help, null) }, onClick = { expanded = false; nav.navigateRoot(Routes.FAQ) })
        DropdownMenuItem(text = { Text("Kalibracja") }, leadingIcon = { Icon(Icons.Default.Tune, null) }, onClick = { expanded = false; nav.navigateRoot(Routes.CALIBRATION) })
        DropdownMenuItem(text = { Text("Ustawienia") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { expanded = false; nav.navigateRoot(Routes.SETTINGS) })
        DropdownMenuItem(text = { Text("Historia") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { expanded = false; nav.navigateRoot(Routes.HISTORY) })
    }
}

@Composable
fun BigValue(text: String, reached: Boolean, compact: Boolean = false) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun MeasurementInfo(state: MeasurementUiState, compact: Boolean = false, showSensorDetails: Boolean = true) {
    Text(state.status, color = if (state.targetReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Dokładność: ±${"%.2f".format(state.reading.accuracyDegrees)}°   Tolerancja: ±${state.settings.defaultTolerance}°", style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
    if (!compact && showSensorDetails) Text(if (state.reading.limitedAccuracy) "Ograniczona dokładność: brak preferowanego czujnika" else state.reading.sensorSummary, style = MaterialTheme.typography.bodySmall)
    if (state.heldReading != null) AssistChip(onClick = {}, label = { Text("HOLD aktywny") }, leadingIcon = { Icon(Icons.Default.PauseCircle, null) })
}

@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun SurfaceControls(vm: SurfaceLevelViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var confirmSave by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompactControlButton("Ustaw środek", Icons.Default.CenterFocusStrong, vm::zero)
        CompactControlButton(if (state.heldReading == null) "HOLD" else "Wznów", if (state.heldReading == null) Icons.Default.Pause else Icons.Default.PlayArrow, vm::toggleHold)
        CompactControlButton(if (state.settings.soundEnabled) "Dźwięk" else "Wycisz", if (state.settings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff) { vm.setSound(!state.settings.soundEnabled) }
        CompactControlButton(if (state.settings.vibrationEnabled) "Wibr." else "Bez wibr.", Icons.Default.Vibration) { vm.setVibration(!state.settings.vibrationEnabled) }
        FilledTonalButton(onClick = { confirmSave = true }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
            Icon(Icons.Default.Save, null, Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("Zapisz", maxLines = 1)
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(0.1f, 0.2f, 0.3f, 0.5f, 1.0f).forEach { tolerance ->
            FilterChip(
                selected = kotlin.math.abs(state.settings.defaultTolerance - tolerance) < 0.001f,
                onClick = { vm.setTolerance(tolerance) },
                label = { Text("±${"%.1f".format(tolerance)}°", maxLines = 1) },
                modifier = Modifier.height(36.dp),
                leadingIcon = if (kotlin.math.abs(state.settings.defaultTolerance - tolerance) < 0.001f) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
            )
        }
    }
    state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Zapisać poziomowanie powierzchni?") },
            text = { Text("Do historii trafi odchylenie całkowite oraz wartości X/Y.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSave = false
                    vm.save("X ${"%.2f".format(state.reading.surfaceX)}°, Y ${"%.2f".format(state.reading.surfaceY)}°, odchylenie ${"%.2f".format(state.reading.totalSurfaceDeviation)}°")
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Anuluj") } }
        )
    }
}

@Composable
fun PlumbControls(vm: PlumbViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var confirmSave by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompactControlButton("Zero", Icons.Default.CenterFocusStrong, vm::zero)
        CompactControlButton(if (state.heldReading == null) "HOLD" else "Wznów", if (state.heldReading == null) Icons.Default.Pause else Icons.Default.PlayArrow, vm::toggleHold)
        CompactControlButton(if (state.settings.soundEnabled) "Dźwięk" else "Wycisz", if (state.settings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff) { vm.setSound(!state.settings.soundEnabled) }
        CompactControlButton(if (state.settings.vibrationEnabled) "Wibr." else "Bez wibr.", Icons.Default.Vibration) { vm.setVibration(!state.settings.vibrationEnabled) }
        FilledTonalButton(onClick = { confirmSave = true }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
            Icon(Icons.Default.Save, null, Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("Zapisz", maxLines = 1)
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(0.1f, 0.2f, 0.3f, 0.5f, 1.0f).forEach { tolerance ->
            FilterChip(
                selected = kotlin.math.abs(state.settings.defaultTolerance - tolerance) < 0.001f,
                onClick = { vm.setTolerance(tolerance) },
                label = { Text("±${"%.1f".format(tolerance)}°", maxLines = 1) },
                modifier = Modifier.height(36.dp),
                leadingIcon = if (kotlin.math.abs(state.settings.defaultTolerance - tolerance) < 0.001f) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
            )
        }
    }
    state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Zapisać pomiar pionu?") },
            text = { Text("Do historii trafi odchylenie od pionu oraz wykryta krawędź telefonu.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSave = false
                    vm.savePlumb()
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Anuluj") } }
        )
    }
}

@Composable
fun Controls(vm: LiveMeasurementViewModel, compact: Boolean = false, zeroLabel: String = "Zero", showUnit: Boolean = true) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var confirmSave by remember { mutableStateOf(false) }
    val buttonHeight = if (compact) 40.dp else 52.dp
    if (compact) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactControlButton(zeroLabel, Icons.Default.CenterFocusStrong, vm::zero)
            CompactControlButton(if (state.heldReading == null) "HOLD" else "Wznów", if (state.heldReading == null) Icons.Default.Pause else Icons.Default.PlayArrow, vm::toggleHold)
            CompactControlButton(if (state.settings.soundEnabled) "Dźwięk" else "Wycisz", if (state.settings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff) { vm.setSound(!state.settings.soundEnabled) }
            CompactControlButton(if (state.settings.vibrationEnabled) "Wibr." else "Bez wibr.", Icons.Default.Vibration) { vm.setVibration(!state.settings.vibrationEnabled) }
            if (showUnit) CompactControlButton("Jednostka", Icons.Default.SwapHoriz) { vm.setUnit(nextMeasurementUnit(state.settings.defaultUnit)) }
            FilledTonalButton(onClick = { confirmSave = true }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Zapisz", maxLines = 1)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton(zeroLabel, Icons.Default.CenterFocusStrong, vm::zero, Modifier.weight(1f), buttonHeight)
            ControlButton(if (state.heldReading == null) "HOLD" else "Wznów", if (state.heldReading == null) Icons.Default.Pause else Icons.Default.PlayArrow, vm::toggleHold, Modifier.weight(1f), buttonHeight)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton(if (state.settings.soundEnabled) "Dźwięk" else "Wyciszony", if (state.settings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff, { vm.setSound(!state.settings.soundEnabled) }, Modifier.weight(1f), buttonHeight)
            ControlButton(if (state.settings.vibrationEnabled) "Wibracja" else "Bez wibracji", Icons.Default.Vibration, { vm.setVibration(!state.settings.vibrationEnabled) }, Modifier.weight(1f), buttonHeight)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showUnit) {
                ControlButton("Jednostka", Icons.Default.SwapHoriz, { vm.setUnit(nextMeasurementUnit(state.settings.defaultUnit)) }, Modifier.weight(1f), buttonHeight)
            }
            Button(onClick = { confirmSave = true }, modifier = Modifier.weight(1f).height(buttonHeight)) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Zapisz")
            }
        }
    }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(0.1f, 0.2f, 0.3f, 0.5f, 1.0f).forEach { tolerance ->
            FilterChip(
                selected = kotlin.math.abs(state.settings.defaultTolerance - tolerance) < 0.001f,
                onClick = { vm.setTolerance(tolerance) },
                label = { Text("±${"%.1f".format(tolerance)}°", maxLines = 1) },
                modifier = Modifier.height(36.dp),
                leadingIcon = if (kotlin.math.abs(state.settings.defaultTolerance - tolerance) < 0.001f) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
            )
        }
    }
    state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Zapisać pomiar?") },
            text = { Text("Pomiar zostanie dodany do historii. Anuluj, jeżeli przycisk został naciśnięty przypadkowo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSave = false
                    vm.save()
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Anuluj") } }
        )
    }
}

@Composable
fun CompactControlButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
fun ProtractorControls(vm: ProtractorViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var confirmSave by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("Baza", Icons.Default.CenterFocusStrong, vm::rememberFirstSurface, Modifier.weight(1f), 40.dp)
            ControlButton("Nachylenie", Icons.Default.Flag, vm::captureInclination, Modifier.weight(1f), 40.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("Reset", Icons.Default.Refresh, vm::clearFirstSurface, Modifier.weight(1f), 40.dp)
            ControlButton(if (state.heldReading == null) "HOLD" else "Wznów", if (state.heldReading == null) Icons.Default.Pause else Icons.Default.PlayArrow, vm::toggleHold, Modifier.weight(1f), 40.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton(if (state.settings.soundEnabled) "Dźwięk" else "Wycisz", if (state.settings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff, { vm.setSound(!state.settings.soundEnabled) }, Modifier.weight(1f), 40.dp)
            ControlButton(if (state.settings.vibrationEnabled) "Wibracja" else "Bez wibr.", Icons.Default.Vibration, { vm.setVibration(!state.settings.vibrationEnabled) }, Modifier.weight(1f), 40.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("Cel z wyniku", Icons.Default.Adjust, vm::setTargetToCurrent, Modifier.weight(1f), 40.dp)
            Button(onClick = { confirmSave = true }, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Zapisz", maxLines = 1)
            }
        }
    }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Zapisać pomiar?") },
            text = { Text("Pomiar kątomierza zostanie dodany do historii.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSave = false
                    vm.save()
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Anuluj") } }
        )
    }
}

@Composable
fun ControlButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 52.dp) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(height), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
fun UnitDropdown(unit: AngleUnit, onChange: (AngleUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(unit.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AngleUnit.entries.filterNot { it == AngleUnit.LUX }.forEach { DropdownMenuItem(text = { Text(it.label) }, onClick = { onChange(it); expanded = false }) }
        }
    }
}

@Composable
fun SlopeControls(vm: SlopeViewModel, unit: AngleUnit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var confirmSave by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompactControlButton("Zero", Icons.Default.CenterFocusStrong, vm::zero)
        CompactControlButton(if (state.heldReading == null) "HOLD" else "Wznów", if (state.heldReading == null) Icons.Default.Pause else Icons.Default.PlayArrow, vm::toggleHold)
        CompactControlButton(if (state.settings.soundEnabled) "Dźwięk" else "Wycisz", if (state.settings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff) { vm.setSound(!state.settings.soundEnabled) }
        CompactControlButton(if (state.settings.vibrationEnabled) "Wibr." else "Bez wibr.", Icons.Default.Vibration) { vm.setVibration(!state.settings.vibrationEnabled) }
        FilledTonalButton(onClick = { confirmSave = true }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
            Icon(Icons.Default.Save, null, Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("Zapisz", maxLines = 1)
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(0.1f, 0.2f, 0.3f, 0.5f, 1.0f).forEach { tolerance ->
            FilterChip(
                selected = kotlin.math.abs(state.settings.defaultTolerance - tolerance) < 0.001f,
                onClick = { vm.setTolerance(tolerance) },
                label = { Text("±${"%.1f".format(tolerance)}°", maxLines = 1) },
                modifier = Modifier.height(36.dp),
                leadingIcon = if (kotlin.math.abs(state.settings.defaultTolerance - tolerance) < 0.001f) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
            )
        }
    }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Zapisać spadek?") },
            text = { Text("Pomiar spadku zostanie dodany do historii razem z celem, jednostką i kierunkiem.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSave = false
                    vm.saveSlope(unit)
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Anuluj") } }
        )
    }
}

@Composable
fun SlopeUnitChips(unit: AngleUnit, onChange: (AngleUnit) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(AngleUnit.DEGREES, AngleUnit.PERCENT, AngleUnit.MM_PER_M, AngleUnit.CM_PER_M, AngleUnit.RATIO).forEach {
            FilterChip(
                selected = unit == it,
                onClick = { onChange(it) },
                label = { Text(it.label, maxLines = 1) },
                leadingIcon = if (unit == it) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
            )
        }
    }
}

@Composable
fun SlopePresetChips(unit: AngleUnit, vm: SlopeViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val presets = when (unit) {
            AngleUnit.DEGREES -> listOf("0.5°" to 0.5f, "1°" to 1f, "1.5°" to 1.5f, "2°" to 2f)
            AngleUnit.PERCENT -> listOf("1%" to 1f, "2%" to 2f, "3%" to 3f, "5%" to 5f)
            AngleUnit.MM_PER_M -> listOf("5 mm/m" to 5f, "10 mm/m" to 10f, "20 mm/m" to 20f, "30 mm/m" to 30f)
            AngleUnit.CM_PER_M -> listOf("0.5 cm/m" to 0.5f, "1 cm/m" to 1f, "2 cm/m" to 2f, "3 cm/m" to 3f)
            AngleUnit.RATIO -> listOf("1:200" to 200f, "1:100" to 100f, "1:50" to 50f, "1:40" to 40f)
            AngleUnit.LUX -> emptyList()
        }
        presets.forEach { (label, value) ->
            FilledTonalButton(
                onClick = { vm.setTarget(value, unit) },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
fun DirectionControls(selected: SlopeDirection, vm: SlopeViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SlopeDirection.entries.forEach {
            FilterChip(
                selected = selected == it,
                onClick = { vm.setDirection(it) },
                label = { Text(it.label, maxLines = 1) },
                modifier = Modifier.height(36.dp),
                leadingIcon = if (selected == it) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
            )
        }
    }
}

@Composable
fun SlopeLengthPanel(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    lengthInput: String,
    onLengthInput: (String) -> Unit,
    lengthSlider: Float,
    onLengthSlider: (Float) -> Unit,
    targetDegrees: Float,
    currentDegrees: Float,
    direction: SlopeDirection
) {
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Długość odcinka", style = MaterialTheme.typography.titleSmall)
                    Text("Przelicza spadek na różnicę wysokości na całej trasie.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(enabled, onEnabledChange)
            }
            if (enabled) {
                OutlinedTextField(
                    value = lengthInput,
                    onValueChange = onLengthInput,
                    label = { Text("Długość całego odcinka") },
                    suffix = { Text("m") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Slider(value = lengthSlider.coerceIn(0.1f, 20f), onValueChange = onLengthSlider, valueRange = 0.1f..20f)
                val targetMm = MeasurementMath.heightDifferenceMm(targetDegrees, lengthInput.replace(',', '.').toFloatOrNull() ?: lengthSlider)
                val currentMm = MeasurementMath.heightDifferenceMm(currentDegrees, lengthInput.replace(',', '.').toFloatOrNull() ?: lengthSlider)
                val deltaMm = currentMm - targetMm
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Cel końca", formatHeight(targetMm), Modifier.weight(1f))
                    MetricTile("Aktualnie", formatHeight(currentMm), Modifier.weight(1f))
                    MetricTile("Różnica", formatHeight(deltaMm), Modifier.weight(1f))
                }
                Text("Przyjmując początek jako 0, koniec odcinka w kierunku „${direction.label}” powinien mieć ${formatHeight(targetMm)} różnicy wysokości.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun nextMeasurementUnit(unit: AngleUnit): AngleUnit {
    val units = listOf(AngleUnit.DEGREES, AngleUnit.PERCENT, AngleUnit.MM_PER_M, AngleUnit.CM_PER_M)
    val index = units.indexOf(unit).takeIf { it >= 0 } ?: 0
    return units[(index + 1) % units.size]
}

fun unitSuffix(unit: AngleUnit): String = when (unit) {
    AngleUnit.DEGREES -> "°"
    AngleUnit.PERCENT -> "%"
    AngleUnit.MM_PER_M -> "mm/m"
    AngleUnit.CM_PER_M -> "cm/m"
    AngleUnit.RATIO -> "1:X"
    AngleUnit.LUX -> "lx"
}

fun normalizeProtractorDelta(value: Float): Float {
    var v = value
    while (v > 180f) v -= 360f
    while (v < -180f) v += 360f
    return v
}

fun NavHostController.navigateHome() {
    navigate(Routes.HOME) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}
