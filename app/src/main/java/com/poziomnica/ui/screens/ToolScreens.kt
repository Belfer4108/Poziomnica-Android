@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.poziomnica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.poziomnica.domain.AngleUnit
import com.poziomnica.domain.MeasurementMath
import com.poziomnica.viewmodel.LightMeterViewModel
import kotlin.math.min

@Composable
fun LightMeterScreen(nav: NavHostController, vm: LightMeterViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val reading = state.heldReading ?: state.reading
    var confirmSave by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { vm.start(); onDispose { vm.stop() } }
    ToolScaffold(nav, "Luksomierz") {
        BigSensorValue("%.1f lx".format(reading.lux))
        LinearProgressIndicator(progress = { min(reading.lux / 1000f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Min", "%.1f lx".format(reading.minLux), Modifier.weight(1f))
            MetricCard("Średnia", "%.1f lx".format(reading.averageLux), Modifier.weight(1f))
            MetricCard("Max", "%.1f lx".format(reading.maxLux), Modifier.weight(1f))
        }
        Text(lightWorkHint(reading.lux), style = MaterialTheme.typography.titleMedium)
        Text("Czujnik światła jest zwykle przy głośniku lub kamerze przedniej. Nie zasłaniaj go dłonią.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = vm::toggleHold, modifier = Modifier.weight(1f).height(44.dp)) {
                Icon(if (state.isHeld) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                Spacer(Modifier.width(6.dp))
                Text(if (state.isHeld) "Wznów" else "HOLD", maxLines = 1)
            }
            FilledTonalButton(onClick = vm::resetStats, modifier = Modifier.weight(1f).height(44.dp)) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Reset", maxLines = 1)
            }
        }
        Button(onClick = { confirmSave = true }, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = reading.available) {
            Icon(Icons.Default.Save, null)
            Spacer(Modifier.width(8.dp))
            Text("Zapisz pomiar")
        }
        state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Zapisać pomiar światła?") },
            text = { Text("Pomiar luksomierza zostanie dodany do historii.") },
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

fun lightWorkHint(lux: Float): String = when {
    lux < 50f -> "Za ciemno do dokładnej pracy."
    lux < 150f -> "Wystarczy do orientacji, ale nie do precyzyjnych prac."
    lux < 300f -> "Typowe oświetlenie domowe."
    lux < 750f -> "Dobry zakres do pracy technicznej."
    lux < 1500f -> "Bardzo dobre oświetlenie stanowiska."
    else -> "Bardzo jasno, możliwe światło bezpośrednie."
}

@Composable
fun ToolScaffold(nav: NavHostController, title: String, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = { CompactMeasurementTopBar(nav, title) }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun BigSensorValue(text: String) {
    Text(text, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CalculatorsScreen(nav: NavHostController) {
    ToolScaffold(nav, "Przeliczniki") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SlopeCalculatorCard()
            LengthCalculatorCard()
            AngleSlopeCalculatorCard()
        }
    }
}

@Composable
fun SlopeCalculatorCard() {
    var value by remember { mutableStateOf("5") }
    var unit by remember { mutableStateOf(AngleUnit.MM_PER_M) }
    var length by remember { mutableStateOf("2.5") }
    val slopeDegrees = value.replace(',', '.').toFloatOrNull()?.let { MeasurementMath.valueToDegrees(it, unit) } ?: 0f
    val lengthMeters = length.replace(',', '.').toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
    val heightMm = MeasurementMath.heightDifferenceMm(slopeDegrees, lengthMeters)
    ElevatedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Spadek na całym odcinku", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = it }, label = { Text("Spadek") }, suffix = { Text(unitSuffix(unit)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(length, { length = it }, label = { Text("Długość") }, suffix = { Text("m") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AngleUnit.DEGREES, AngleUnit.PERCENT, AngleUnit.MM_PER_M, AngleUnit.CM_PER_M, AngleUnit.RATIO).forEach {
                    FilterChip(
                        selected = unit == it,
                        onClick = { unit = it },
                        label = { Text(it.label) },
                        leadingIcon = if (unit == it) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConverterResult("Różnica wysokości", formatHeight(heightMm), Modifier.weight(1f))
                ConverterResult("Kąt", "%+.3f°".format(slopeDegrees), Modifier.weight(1f))
            }
            Text("Jeżeli początek odcinka przyjmiesz jako 0, drugi koniec powinien różnić się o ${formatHeight(heightMm)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LengthCalculatorCard() {
    var input by remember { mutableStateOf("12") }
    var from by remember { mutableStateOf(LengthUnit.FOOT) }
    var to by remember { mutableStateOf(LengthUnit.METER) }
    val meters = (input.replace(',', '.').toDoubleOrNull() ?: 0.0) * from.toMeters
    val result = meters / to.toMeters
    ElevatedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Długości", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(input, { input = it }, label = { Text("Wartość") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            UnitChipRow("Z", LengthUnit.entries, from) { from = it }
            UnitChipRow("Na", LengthUnit.entries, to) { to = it }
            ConverterResult("Wynik", "%.4f ${to.label}".format(result), Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun AngleSlopeCalculatorCard() {
    var angle by remember { mutableStateOf("1.5") }
    val degrees = angle.replace(',', '.').toFloatOrNull() ?: 0f
    ElevatedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Kąt na spadek", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(angle, { angle = it }, label = { Text("Kąt") }, suffix = { Text("°") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConverterResult("Procent", "%.3f%%".format(MeasurementMath.degreesToPercent(degrees)), Modifier.weight(1f))
                ConverterResult("mm/m", "%.2f".format(MeasurementMath.degreesToMmPerMeter(degrees)), Modifier.weight(1f))
                ConverterResult("cm/m", "%.3f".format(MeasurementMath.degreesToCmPerMeter(degrees)), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ConverterResult(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun UnitChipRow(label: String, units: List<LengthUnit>, selected: LengthUnit, onSelected: (LengthUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            units.forEach {
                FilterChip(
                    selected = selected == it,
                    onClick = { onSelected(it) },
                    label = { Text(it.label) },
                    leadingIcon = if (selected == it) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
                )
            }
        }
    }
}

enum class LengthUnit(val label: String, val toMeters: Double) {
    MILLIMETER("mm", 0.001),
    CENTIMETER("cm", 0.01),
    METER("m", 1.0),
    KILOMETER("km", 1000.0),
    INCH("cale", 0.0254),
    FOOT("stopy", 0.3048)
}

fun formatHeight(mm: Float): String =
    if (kotlin.math.abs(mm) >= 100f) "%+.2f cm".format(mm / 10f) else "%+.1f mm".format(mm)
