package com.poziomnica.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.poziomnica.camera.ArPlaneKind
import com.poziomnica.camera.ArTapeMeasureView
import com.poziomnica.camera.ArTapeProjectedCursor
import com.poziomnica.camera.ArTapeProjectedFeaturePoint
import com.poziomnica.camera.ArTapeProjectedPlane
import com.poziomnica.camera.ArTapeProjectedPoint
import com.poziomnica.viewmodel.ArPoint
import com.poziomnica.viewmodel.TapeMeasureUiState
import com.poziomnica.viewmodel.TapeMeasureUnit
import com.poziomnica.viewmodel.TapeMeasureViewModel
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun TapeMeasureScreen(nav: NavHostController, vm: TapeMeasureViewModel) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var arView by remember { mutableStateOf<ArTapeMeasureView?>(null) }
    var projectedPoints by remember { mutableStateOf(emptyList<ArTapeProjectedPoint>()) }
    var projectedPlane by remember { mutableStateOf<ArTapeProjectedPlane?>(null) }
    var projectedCursor by remember { mutableStateOf<ArTapeProjectedCursor?>(null) }
    var featurePoints by remember { mutableStateOf(emptyList<ArTapeProjectedFeaturePoint>()) }
    var centerPoint by remember { mutableStateOf<ArPoint?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    fun leave() {
        arView?.clearAnchors()
        projectedPoints = emptyList()
        projectedPlane = null
        projectedCursor = null
        featurePoints = emptyList()
        centerPoint = null
        vm.reset()
        nav.navigateHome()
    }

    BackHandler { leave() }

    DisposableEffect(hasPermission) {
        if (hasPermission) arView?.resumeSession()
        onDispose { arView?.pauseSession() }
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Black) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(androidx.compose.ui.graphics.Color.Black)) {
            if (!hasPermission) {
                Column(
                    Modifier.align(Alignment.Center).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Miarka AR wymaga aparatu. Pozostałe moduły aplikacji działają bez tego uprawnienia.", color = androidx.compose.ui.graphics.Color.White)
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Udziel dostępu do aparatu") }
                    FilledTonalIconButton(onClick = { nav.navigateHome() }) { Icon(Icons.Default.ArrowBack, "Wyjdź") }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ArTapeMeasureView(ctx).also { view ->
                            view.preferredPlaneKind = ArPlaneKind.UNKNOWN
                            view.onFrameState = {
                                projectedPoints = it.anchors
                                projectedPlane = it.plane
                                projectedCursor = it.cursor
                                featurePoints = it.featurePoints
                                centerPoint = it.centerPoint
                                vm.updateTracking(it.message, it.targetAvailable, it.centerPoint)
                            }
                            arView = view
                            view.resumeSession()
                        }
                    }
                )
                TapePlaneOverlay(projectedPlane, projectedCursor, featurePoints)
                TapeReticle(centerPoint, projectedCursor, state)
                TapeOverlay(state, projectedPoints, centerPoint, vm)
                if (projectedPlane == null) TapeScanGuide(Modifier.align(Alignment.Center))
                Row(
                    Modifier.align(Alignment.TopStart).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = { leave() }) { Icon(Icons.Default.ArrowBack, "Wróć") }
                    AssistChip(onClick = {}, label = { Text("Miarka AR") })
                    AssistChip(onClick = {}, label = { Text(surfaceLabel(projectedPlane)) })
                }
                TapeBottomPanel(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    state = state,
                    vm = vm,
                    message = message,
                    onMark = {
                        arView?.addCurrentAnchorAsync(2) { point -> vm.markPoint(point ?: centerPoint) } ?: vm.markPoint(null)
                    },
                    onReset = {
                        arView?.clearAnchors()
                        projectedPoints = emptyList()
                        centerPoint = null
                        vm.reset()
                        message = null
                    },
                    onSave = { vm.save() },
                    onPhoto = {
                        arView?.captureBitmap { bitmap ->
                            val uri = bitmap?.let { saveTapeImage(context, it, state, vm, projectedPoints, centerPoint) }
                            if (uri != null) {
                                vm.save(uri.toString())
                                message = "Zapisano zdjęcie miarki w Galerii."
                            } else {
                                message = "Nie udało się zapisać zdjęcia miarki."
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TapeScanGuide(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(22.dp),
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Szukam płaszczyzny", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Poruszaj telefonem powoli. Miarka musi złapać powierzchnię, do której przyklei taśmę.", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TapeBottomPanel(
    modifier: Modifier,
    state: TapeMeasureUiState,
    vm: TapeMeasureViewModel,
    message: String?,
    onMark: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onPhoto: () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(8.dp)) {
        val compact = maxHeight < 420.dp
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), tonalElevation = 3.dp, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(if (compact) 6.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Taśma: ${vm.formatDistance(state.measurementMeters ?: state.liveMeters)}",
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (state.targetAvailable) "Punkt gotowy" else "Szukam", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (!compact) Text(message ?: state.savedMessage ?: state.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = state.unit == TapeMeasureUnit.MILLIMETERS, onClick = { vm.setUnit(TapeMeasureUnit.MILLIMETERS) }, label = { Text("mm") })
                    FilterChip(selected = state.unit == TapeMeasureUnit.CENTIMETERS, onClick = { vm.setUnit(TapeMeasureUnit.CENTIMETERS) }, label = { Text("cm") })
                    FilterChip(selected = state.unit == TapeMeasureUnit.METERS, onClick = { vm.setUnit(TapeMeasureUnit.METERS) }, label = { Text("m") })
                    FilledTonalButton(onClick = onMark, enabled = state.targetAvailable, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(36.dp)) {
                        Icon(Icons.Default.Place, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.startPoint == null || state.endPoint != null) "Start" else "Koniec", maxLines = 1)
                    }
                    FilledTonalButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(36.dp)) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                        if (!compact) {
                            Spacer(Modifier.width(6.dp))
                            Text("Reset", maxLines = 1)
                        }
                    }
                    FilledTonalButton(onClick = onSave, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(36.dp)) {
                        Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                        if (!compact) {
                            Spacer(Modifier.width(6.dp))
                            Text("Zapisz", maxLines = 1)
                        }
                    }
                    FilledTonalButton(onClick = onPhoto, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(36.dp)) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                        if (!compact) {
                            Spacer(Modifier.width(6.dp))
                            Text("Zdjęcie", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TapePlaneOverlay(plane: ArTapeProjectedPlane?, cursor: ArTapeProjectedCursor?, featurePoints: List<ArTapeProjectedFeaturePoint>) {
    Canvas(Modifier.fillMaxSize()) {
        val scan = plane?.scanPoints.orEmpty()
        scan.forEach { point -> drawCircle(androidx.compose.ui.graphics.Color(0xFF70E0A0).copy(alpha = 0.24f), 2.5f, Offset(point.x, point.y)) }
        cursor?.ring?.let { ring ->
            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[(i + 1) % ring.size]
                drawLine(androidx.compose.ui.graphics.Color(0xFF70E0A0).copy(alpha = 0.58f), Offset(a.x, a.y), Offset(b.x, b.y), 4f, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun TapeReticle(centerPoint: ArPoint?, cursor: ArTapeProjectedCursor?, state: TapeMeasureUiState) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val point = centerPoint?.let { Offset(it.screenX ?: center.x, it.screenY ?: center.y) } ?: center
        val color = if (state.targetAvailable || cursor != null) androidx.compose.ui.graphics.Color(0xFF18A058) else androidx.compose.ui.graphics.Color(0xFFE53935)
        drawCircle(color.copy(alpha = 0.18f), 34f, point)
        drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f), 25f, point, style = Stroke(5f))
        drawCircle(color, 5f, point)
    }
}

@Composable
private fun TapeOverlay(
    state: TapeMeasureUiState,
    points: List<ArTapeProjectedPoint>,
    centerPoint: ArPoint?,
    vm: TapeMeasureViewModel
) {
    Canvas(Modifier.fillMaxSize()) {
        val visible = points.filter { it.tracking }.associateBy { it.index }
        val start = visible[0]?.let { Offset(it.x, it.y) } ?: state.startPoint?.screenOffset()
        val finalEnd = visible[1]?.let { Offset(it.x, it.y) } ?: state.endPoint?.screenOffset()
        val liveEnd = state.livePoint?.screenOffset() ?: centerPoint?.screenOffset()
        val end = finalEnd ?: liveEnd
        if (start != null && end != null) {
            drawTape(start, end, vm.formatDistance(state.measurementMeters ?: state.liveMeters))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTape(start: Offset, end: Offset, label: String) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = hypot(dx, dy).coerceAtLeast(1f)
    val nx = -dy / length
    val ny = dx / length
    val angle = atan2(dy, dx)
    val tapeColor = androidx.compose.ui.graphics.Color(0xFFFFC928)
    val edgeColor = androidx.compose.ui.graphics.Color(0xFF3B2D00)
    val half = 15f
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(start.x + nx * half, start.y + ny * half)
        lineTo(end.x + nx * half, end.y + ny * half)
        lineTo(end.x - nx * half, end.y - ny * half)
        lineTo(start.x - nx * half, start.y - ny * half)
        close()
    }
    drawPath(path, tapeColor.copy(alpha = 0.90f))
    drawLine(edgeColor, start, end, 3f, cap = StrokeCap.Round)
    drawCircle(tapeColor, 13f, start)
    drawCircle(tapeColor, 13f, end)
    val tickCount = (length / 34f).toInt().coerceIn(3, 40)
    for (i in 0..tickCount) {
        val t = i / tickCount.toFloat()
        val x = start.x + dx * t
        val y = start.y + dy * t
        val tick = if (i % 5 == 0) 13f else 8f
        drawLine(edgeColor.copy(alpha = 0.82f), Offset(x + nx * tick, y + ny * tick), Offset(x - nx * tick, y - ny * tick), 2.4f, cap = StrokeCap.Round)
    }
    drawTapeLabel(label, Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f), angle)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTapeLabel(text: String, center: Offset, angle: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
        isFakeBoldText = true
    }
    val bg = androidx.compose.ui.graphics.Color(0xFF2D2A22).copy(alpha = 0.92f)
    val padding = 18f
    val width = paint.measureText(text) + padding * 2f
    val height = 54f
    drawContext.canvas.save()
    drawContext.canvas.translate(center.x, center.y)
    drawContext.canvas.rotate((angle * 180f / Math.PI.toFloat()).coerceIn(-90f, 90f))
    drawRoundRect(bg, topLeft = Offset(-width / 2f, -height / 2f), size = androidx.compose.ui.geometry.Size(width, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f))
    drawContext.canvas.nativeCanvas.drawText(text, -width / 2f + padding, 14f, paint)
    drawContext.canvas.restore()
}

private fun ArPoint.screenOffset(): Offset? {
    val x = screenX ?: return null
    val y = screenY ?: return null
    return Offset(x, y)
}

private fun surfaceLabel(plane: ArTapeProjectedPlane?): String {
    return when (plane?.kind) {
        ArPlaneKind.FLOOR -> "Podłoga / blat"
        ArPlaneKind.WALL -> "Ściana"
        ArPlaneKind.CEILING -> "Sufit"
        null,
        ArPlaneKind.UNKNOWN -> "Punkt OK"
    }
}

private fun saveTapeImage(
    context: android.content.Context,
    bitmap: Bitmap,
    state: TapeMeasureUiState,
    vm: TapeMeasureViewModel,
    points: List<ArTapeProjectedPoint>,
    centerPoint: ArPoint?
): Uri? {
    val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(mutable)
    drawTapeOnBitmap(canvas, state, vm, points, centerPoint)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "poziomnica_miarka_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Poziomnica")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        if (uri.scheme == "file" && uri.path != null) {
            FileOutputStream(uri.path!!).use { mutable.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        } else {
            resolver.openOutputStream(uri, "w")?.use { mutable.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    } catch (t: Throwable) {
        resolver.delete(uri, null, null)
        null
    }
}

private fun drawTapeOnBitmap(canvas: AndroidCanvas, state: TapeMeasureUiState, vm: TapeMeasureViewModel, points: List<ArTapeProjectedPoint>, centerPoint: ArPoint?) {
    val visible = points.filter { it.tracking }.associateBy { it.index }
    val start = visible[0]?.let { BitmapPoint(it.x, it.y) } ?: state.startPoint?.bitmapPoint()
    val end = visible[1]?.let { BitmapPoint(it.x, it.y) } ?: state.endPoint?.bitmapPoint() ?: state.livePoint?.bitmapPoint() ?: centerPoint?.bitmapPoint()
    if (start == null || end == null) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 201, 40)
        strokeWidth = 28f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(start.x, start.y, end.x, end.y, paint)
    paint.color = Color.rgb(59, 45, 0)
    paint.strokeWidth = 4f
    canvas.drawLine(start.x, start.y, end.x, end.y, paint)
    val label = vm.formatDistance(state.measurementMeters ?: state.liveMeters)
    drawBitmapLabel(canvas, label, (start.x + end.x) / 2f + 16f, (start.y + end.y) / 2f - 18f)
}

private data class BitmapPoint(val x: Float, val y: Float)

private fun ArPoint.bitmapPoint(): BitmapPoint? {
    val x = screenX ?: return null
    val y = screenY ?: return null
    return BitmapPoint(x, y)
}

private fun drawBitmapLabel(canvas: AndroidCanvas, text: String, x: Float, y: Float) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
        isFakeBoldText = true
    }
    val pad = 14f
    val width = textPaint.measureText(text) + pad * 2f
    val height = 52f
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(238, 45, 42, 34) }
    canvas.drawRoundRect(x, y - height, x + width, y, 20f, 20f, bg)
    canvas.drawText(text, x + pad, y - 16f, textPaint)
}
