@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.poziomnica.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.poziomnica.database.MeasurementEntity
import com.poziomnica.domain.AngleUnit
import com.poziomnica.domain.SoundMode
import com.poziomnica.export.ExportManager
import com.poziomnica.domain.IndicatorStyle
import com.poziomnica.domain.MeasurementMath
import com.poziomnica.domain.MeasurementType
import com.poziomnica.domain.SmoothingLevel
import com.poziomnica.ui.components.CameraOverlay
import com.poziomnica.viewmodel.*
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.atan2

@Composable
fun CameraLevelScreen(nav: NavHostController, vm: CameraLevelViewModel) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var exposureLocked by remember { mutableStateOf(false) }
    var cameraMessage by remember { mutableStateOf<String?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    var imageLineStart by remember { mutableStateOf<Offset?>(null) }
    var imageLineEnd by remember { mutableStateOf<Offset?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    DisposableEffect(Unit) { vm.start(); onDispose { vm.stop() } }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Black) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        if (imageLineStart == null || imageLineEnd != null) {
                            imageLineStart = tap
                            imageLineEnd = null
                        } else {
                            imageLineEnd = tap
                        }
                    }
                }
        ) {
            if (!hasPermission) {
                Column(
                    Modifier.align(Alignment.Center).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Tryb aparatu wymaga uprawnienia CAMERA. Pozostałe moduły działają bez aparatu.", color = androidx.compose.ui.graphics.Color.White)
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Udziel dostępu do aparatu") }
                    FilledTonalIconButton(onClick = { nav.navigateHome() }) { Icon(Icons.Default.ArrowBack, "Wyjdź") }
                }
            } else {
                CameraPreview(
                    modifier = Modifier.matchParentSize(),
                    onReady = { capture, boundCamera, view ->
                        imageCapture = capture
                        camera = boundCamera
                        previewView = view
                    },
                    onError = { cameraMessage = it }
                )
                CameraOverlay(
                    roll = state.reading.roll,
                    pitch = state.reading.pitch,
                    grid = state.settings.cameraGrid,
                    vertical = state.settings.cameraVerticalLine,
                    horizontal = state.settings.cameraHorizontalLine,
                    modifier = Modifier.matchParentSize(),
                    alpha = state.settings.overlayAlpha,
                    lineWidth = state.settings.lineWidth,
                    tolerance = state.settings.defaultTolerance
                )
                if (imageLineStart != null) {
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val start = imageLineStart!!
                        val end = imageLineEnd ?: start
                        drawCircle(androidx.compose.ui.graphics.Color(0xFFFFC857), 7f, start)
                        if (imageLineEnd != null) {
                            drawLine(
                                androidx.compose.ui.graphics.Color(0xFFFFC857),
                                start,
                                end,
                                strokeWidth = 5f,
                                cap = StrokeCap.Round
                            )
                            drawCircle(androidx.compose.ui.graphics.Color(0xFFFFC857), 7f, end)
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.TopStart).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = { nav.navigateHome() }) { Icon(Icons.Default.ArrowBack, "Wyjdź") }
                    AssistChip(onClick = {}, label = { Text("Przechył ${"%.1f".format(state.reading.roll)}°") })
                    AssistChip(onClick = {}, label = { Text("Poziom ${"%.1f".format(state.reading.pitch)}°") })
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        cameraMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            imageLineEnd?.let { end ->
                                val angle = imageLineAngle(imageLineStart!!, end)
                                "Linia z obrazu: ${"%.1f".format(angle)}° od poziomu, ${"%.1f".format(90f - kotlin.math.abs(angle))}° od pionu. Perspektywa może zniekształcić ocenę."
                            } ?: "Dotknij dwa punkty na obrazie, aby pomocniczo ocenić linię. Perspektywa może zniekształcić ocenę.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CameraControlButtons(
                            soundEnabled = state.settings.soundEnabled,
                            vibrationEnabled = state.settings.vibrationEnabled,
                            grid = state.settings.cameraGrid,
                            vertical = state.settings.cameraVerticalLine,
                            horizontal = state.settings.cameraHorizontalLine,
                            onSave = { confirmSave = true },
                            onSound = { vm.setSound(!state.settings.soundEnabled) },
                            onVibration = { vm.setVibration(!state.settings.vibrationEnabled) },
                            onGrid = { vm.setGrid(!state.settings.cameraGrid) },
                            onVertical = { vm.setVertical(!state.settings.cameraVerticalLine) },
                            onHorizontal = { vm.setHorizontal(!state.settings.cameraHorizontalLine) },
                            torchEnabled = torchEnabled,
                            exposureLocked = exposureLocked,
                            onTorch = {
                                torchEnabled = !torchEnabled
                                camera?.cameraControl?.enableTorch(torchEnabled)
                            },
                            onExposureLock = {
                                val lock = !exposureLocked
                                setCenterExposureAndFocusLock(
                                    camera = camera,
                                    previewView = previewView,
                                    lock = lock,
                                    onState = {
                                        exposureLocked = it
                                        cameraMessage = if (it) {
                                            "Ekspozycja i ostrość zablokowane na środku kadru"
                                        } else {
                                            "Ekspozycja i ostrość automatyczna"
                                        }
                                    },
                                    onError = { cameraMessage = it }
                                )
                            },
                            onPhoto = {
                                takeCameraPhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    overlay = false,
                                    state = state,
                                    onSaved = { uri ->
                                        vm.savePhotoMeasurement(uri.toString(), "Zdjęcie bez nakładki")
                                        cameraMessage = "Zapisano zdjęcie w Galerii"
                                    },
                                    onError = { cameraMessage = it }
                                )
                            },
                            onPhotoOverlay = {
                                takeCameraPhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    overlay = true,
                                    state = state,
                                    onSaved = { uri ->
                                        vm.savePhotoMeasurement(uri.toString(), "Zdjęcie z nakładką")
                                        cameraMessage = "Zapisano zdjęcie z nakładką w Galerii"
                                    },
                                    onError = { cameraMessage = it }
                                )
                            }
                        )
                        TextButton(onClick = { imageLineStart = null; imageLineEnd = null }) { Text("Wyczyść linię") }
                    }
                }
            }
        }
    }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Zapisać pomiar z aparatu?") },
            text = { Text("Do historii trafi sam wynik orientacji telefonu, bez zdjęcia.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSave = false
                    vm.save("Pomiar z aparatu")
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Anuluj") } }
        )
    }
}

fun imageLineAngle(start: Offset, end: Offset): Float {
    val radians = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    var degrees = Math.toDegrees(radians).toFloat()
    while (degrees > 90f) degrees -= 180f
    while (degrees < -90f) degrees += 180f
    return degrees
}

fun setCenterExposureAndFocusLock(
    camera: Camera?,
    previewView: PreviewView?,
    lock: Boolean,
    onState: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    val currentCamera = camera ?: run {
        onError("Aparat nie jest jeszcze gotowy")
        return
    }
    val view = previewView ?: run {
        onError("Podgląd aparatu nie jest jeszcze gotowy")
        return
    }
    if (!lock) {
        currentCamera.cameraControl.cancelFocusAndMetering()
        onState(false)
        return
    }
    val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
    val action = FocusMeteringAction.Builder(
        point,
        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
    ).disableAutoCancel().build()
    val future = currentCamera.cameraControl.startFocusAndMetering(action)
    future.addListener(
        {
            runCatching { future.get() }
                .onSuccess { result ->
                    if (result.isFocusSuccessful) {
                        onState(true)
                    } else {
                        onState(true)
                        onError("Telefon zablokował ekspozycję, ale nie potwierdził ostrości")
                    }
                }
                .onFailure { onError("Nie udało się zablokować ekspozycji: ${it.message ?: "brak obsługi przez aparat"}") }
        },
        ContextCompat.getMainExecutor(view.context)
    )
}

@Composable
fun CameraControlButtons(
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    grid: Boolean,
    vertical: Boolean,
    horizontal: Boolean,
    onSave: () -> Unit,
    onSound: () -> Unit,
    onVibration: () -> Unit,
    onGrid: () -> Unit,
    onVertical: () -> Unit,
    onHorizontal: () -> Unit,
    torchEnabled: Boolean,
    exposureLocked: Boolean,
    onTorch: () -> Unit,
    onExposureLock: () -> Unit,
    onPhoto: () -> Unit,
    onPhotoOverlay: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onPhoto, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.PhotoCamera, null)
                Spacer(Modifier.width(6.dp))
                Text("Zdjęcie", maxLines = 1)
            }
            Button(onClick = onPhotoOverlay, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.AddPhotoAlternate, null)
                Spacer(Modifier.width(6.dp))
                Text("Z nakładką", maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = onSave, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Pomiar", maxLines = 1)
            }
            FilledTonalButton(onClick = onSound, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (soundEnabled) "Dźwięk" else "Wycisz", maxLines = 1)
            }
            FilledTonalButton(onClick = onVibration, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Default.Vibration, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (vibrationEnabled) "Wibr." else "Bez wibr.", maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = grid, onClick = onGrid, label = { Text("Siatka") }, leadingIcon = if (grid) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
            FilterChip(selected = vertical, onClick = onVertical, label = { Text("Pion") }, leadingIcon = if (vertical) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
            FilterChip(selected = horizontal, onClick = onHorizontal, label = { Text("Poziom") }, leadingIcon = if (horizontal) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
            FilterChip(selected = torchEnabled, onClick = onTorch, label = { Text("Latarka") }, leadingIcon = if (torchEnabled) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
            FilterChip(selected = exposureLocked, onClick = onExposureLock, label = { Text("Ekspozycja") }, leadingIcon = if (exposureLocked) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onReady: (ImageCapture, Camera, PreviewView) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val rotation = LocalView.current.display?.rotation ?: android.view.Surface.ROTATION_0
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = PreviewView(ctx)
            view.scaleType = PreviewView.ScaleType.FILL_CENTER
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().setTargetRotation(rotation).build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val imageCapture = ImageCapture.Builder().setTargetRotation(rotation).build()
                provider.unbindAll()
                runCatching {
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }.onSuccess { boundCamera ->
                    onReady(imageCapture, boundCamera, view)
                }.onFailure {
                    onError("Aparat jest niedostępny: ${it.message ?: "błąd uruchomienia"}")
                }
            }, ContextCompat.getMainExecutor(context))
            view
        }
    )
}

fun takeCameraPhoto(
    context: android.content.Context,
    imageCapture: ImageCapture?,
    overlay: Boolean,
    state: MeasurementUiState,
    onSaved: (Uri) -> Unit,
    onError: (String) -> Unit
) {
    val capture = imageCapture ?: run {
        onError("Aparat nie jest jeszcze gotowy")
        return
    }
    val name = "poziomnica_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Poziomnica")
    if (!directory.exists() && !directory.mkdirs()) {
        onError("Nie można utworzyć katalogu zdjęć aplikacji")
        return
    }
    val file = File(directory, name)
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    capture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val fileUri = Uri.fromFile(file)
                runCatching { normalizeCapturedPhoto(file) }
                if (overlay) runCatching { drawCameraOverlayOnImage(context, fileUri, state) }
                    .onFailure { onError("Zdjęcie zapisane, ale nie udało się dodać nakładki") }
                runCatching {
                    publishPhotoToGallery(context, file, name)
                }.onSuccess { galleryUri ->
                    file.delete()
                    onSaved(galleryUri)
                }.onFailure {
                    onError("Zdjęcie zapisano w aplikacji, ale nie udało się dodać go do Galerii: ${it.message ?: "błąd zapisu"}")
                    onSaved(fileUri)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError("Błąd zapisu zdjęcia: ${exception.message ?: "nieznany błąd"}")
            }
        }
    )
}

fun normalizeCapturedPhoto(file: File) {
    val orientation = ExifInterface(file.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (rotation == 0f) return
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
    FileOutputStream(file).use { rotated.compress(Bitmap.CompressFormat.JPEG, 94, it) }
    bitmap.recycle()
    rotated.recycle()
}

fun publishPhotoToGallery(context: android.content.Context, file: File, displayName: String): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Poziomnica")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Nie można utworzyć wpisu zdjęcia w Galerii")
    try {
        resolver.openOutputStream(uri)?.use { output ->
            FileInputStream(file).use { input -> input.copyTo(output) }
        } ?: error("Nie można otworzyć pliku w Galerii")
        val finished = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(uri, finished, null, null)
        return uri
    } catch (t: Throwable) {
        resolver.delete(uri, null, null)
        throw t
    }
}

fun drawCameraOverlayOnImage(context: android.content.Context, uri: Uri, state: MeasurementUiState) {
    val resolver = context.contentResolver
    val bitmap = if (uri.scheme == "file") {
        BitmapFactory.decodeFile(uri.path)
    } else {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } ?: return
    val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutable)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 12, 18, 20)
        textSize = (mutable.width * 0.045f).coerceAtLeast(34f)
    }
    val pad = mutable.width * 0.04f
    val boxHeight = paint.textSize * 3.2f
    canvas.drawRect(0f, 0f, mutable.width.toFloat(), boxHeight, paint)
    paint.color = Color.WHITE
    paint.isFakeBoldText = true
    canvas.drawText("Poziomnica", pad, paint.textSize * 1.15f, paint)
    paint.isFakeBoldText = false
    canvas.drawText("Przechył ${"%.2f".format(state.reading.roll)}°   Poziom ${"%.2f".format(state.reading.pitch)}°", pad, paint.textSize * 2.35f, paint)
    paint.color = Color.argb(230, 24, 160, 88)
    paint.strokeWidth = (mutable.width * 0.006f).coerceAtLeast(5f)
    val centerX = mutable.width / 2f
    val centerY = mutable.height / 2f
    canvas.drawLine(pad, centerY, mutable.width - pad, centerY, paint)
    canvas.drawLine(centerX, boxHeight + pad, centerX, mutable.height - pad, paint)
    paint.style = Paint.Style.STROKE
    canvas.drawCircle(centerX, centerY, mutable.width.coerceAtMost(mutable.height) * 0.035f, paint)
    paint.style = Paint.Style.FILL
    if (uri.scheme == "file" && uri.path != null) {
        FileOutputStream(uri.path!!).use { mutable.compress(Bitmap.CompressFormat.JPEG, 94, it) }
    } else {
        resolver.openOutputStream(uri, "w")?.use { mutable.compress(Bitmap.CompressFormat.JPEG, 94, it) }
    }
}

@Composable
fun HistoryScreen(nav: NavHostController, vm: HistoryViewModel) {
    val context = LocalContext.current
    val list by vm.visible.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var exportKind by remember { mutableStateOf("csv") }
    var showExportDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MeasurementEntity?>(null) }
    var fullPhoto by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<MeasurementEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<MeasurementEntity?>(null) }
    var singleExportKind by remember { mutableStateOf("png") }
    var singleExportTarget by remember { mutableStateOf<MeasurementEntity?>(null) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf("date_desc") }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var deleteSelectedConfirm by remember { mutableStateOf(false) }
    val shownList = remember(list, typeFilter, sortMode) {
        val filtered = typeFilter?.let { type -> list.filter { it.type == type } } ?: list
        when (sortMode) {
            "date_asc" -> filtered.sortedBy { it.createdAt }
            "value_desc" -> filtered.sortedByDescending { it.mainValue }
            "value_asc" -> filtered.sortedBy { it.mainValue }
            "type" -> filtered.sortedBy { measurementTypeLabel(it.type) }
            else -> filtered.sortedByDescending { it.createdAt }
        }
    }
    val selectedMeasurements = remember(shownList, selectedIds) { shownList.filter { it.id in selectedIds } }
    val exportList = if (selectedMeasurements.isNotEmpty()) selectedMeasurements else shownList
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let {
            val exporter = ExportManager(context)
            when (exportKind) {
                "pdf" -> exporter.writePdf(it, exportList)
                "txt" -> exporter.writeText(it, exportList)
                else -> exporter.writeCsv(it, exportList)
            }
        }
    }
    val singleExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val measurement = singleExportTarget
        if (uri != null && measurement != null) {
            val exporter = ExportManager(context)
            when (singleExportKind) {
                "pdf" -> exporter.writePdf(uri, listOf(measurement))
                "txt" -> exporter.writeText(uri, listOf(measurement))
                else -> exporter.writeBitmap(uri, measurement)
            }
        }
        singleExportTarget = null
    }
    val shareText = remember(shownList) {
        shownList.joinToString("\n") { "${it.name}: ${formatMeasurementValue(it)}" }
    }
    Scaffold(topBar = { CompactMeasurementTopBar(nav, "Historia pomiarów") }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query, { query = it; vm.setQuery(it) }, label = { Text("Szukaj") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { showExportDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.IosShare, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Eksport historii")
                }
            }
            HistoryFilterControls(
                selectedType = typeFilter,
                sortMode = sortMode,
                onType = { typeFilter = it },
                onSort = { sortMode = it }
            )
            if (selectedIds.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("Zaznaczone: ${selectedIds.size}") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) })
                    FilledTonalButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.IosShare, null); Spacer(Modifier.width(6.dp)); Text("Eksport zazn.") }
                    FilledTonalButton(onClick = { deleteSelectedConfirm = true }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Usuń zazn.") }
                    TextButton(onClick = { selectedIds = emptySet() }) { Text("Wyczyść") }
                }
            }
            if (shownList.isEmpty()) Text("Historia jest pusta.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shownList, key = { it.id }) { m ->
                    ElevatedCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = m.id in selectedIds,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + m.id else selectedIds - m.id
                                    }
                                )
                                MeasurementThumbnail(m.photoUri, Modifier.size(58.dp), onClick = { m.photoUri?.let { fullPhoto = it } })
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(m.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${measurementTypeLabel(m.type)}  ${formatMeasurementValue(m)}", style = MaterialTheme.typography.bodyMedium)
                                    Text("${formatTolerance(m)}  ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(m.createdAt))}", style = MaterialTheme.typography.bodySmall)
                                    if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodySmall)
                                    if (m.photoUri != null && !photoAvailable(context, m.photoUri)) {
                                        Text("Zdjęcie niedostępne lub usunięte z Galerii", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { selected = m }) { Icon(Icons.Default.Visibility, null); Text("Pokaż") }
                                TextButton(onClick = { shareMeasurement(context, m) }) { Icon(Icons.Default.Share, null); Text("Udostępnij") }
                                TextButton(onClick = { deleteTarget = m }) { Icon(Icons.Default.Delete, null); Text("Usuń") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Eksport historii") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PDF tworzy raport do wydruku. CSV nadaje się do Excela. Tekst jest najlepszy do szybkiego wysłania lub wklejenia.")
                    Text(if (selectedMeasurements.isNotEmpty()) "Eksport obejmie zaznaczone pomiary." else "Eksport obejmie aktualnie widoczną listę, czyli wynik wyszukiwania, filtr i sortowanie.")
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { exportKind = "pdf"; showExportDialog = false; exportLauncher.launch("poziomnica-historia.pdf") }) { Text("PDF") }
                    TextButton(onClick = { exportKind = "csv"; showExportDialog = false; exportLauncher.launch("poziomnica-historia.csv") }) { Text("CSV") }
                    TextButton(onClick = { exportKind = "txt"; showExportDialog = false; exportLauncher.launch("poziomnica-historia.txt") }) { Text("TXT") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Udostępnij pomiary"))
                }) { Text("Udostępnij tekst") }
            }
        )
    }
    selected?.let { measurement ->
        MeasurementPreviewDialog(
            measurement = measurement,
            onDismiss = { selected = null },
            onEdit = { editTarget = measurement },
            onShare = { shareMeasurement(context, measurement) },
            onOpenPhoto = { measurement.photoUri?.let { openPhoto(context, it) } },
            onFullPhoto = { measurement.photoUri?.let { fullPhoto = it } },
            onExportPdf = {
                singleExportKind = "pdf"
                singleExportTarget = measurement
                singleExportLauncher.launch("${measurement.name.ifBlank { "pomiar" }}.pdf")
            },
            onExportPng = {
                singleExportKind = "png"
                singleExportTarget = measurement
                singleExportLauncher.launch("${measurement.name.ifBlank { "pomiar" }}.png")
            },
            onExportText = {
                singleExportKind = "txt"
                singleExportTarget = measurement
                singleExportLauncher.launch("${measurement.name.ifBlank { "pomiar" }}.txt")
            },
            onDelete = {
                deleteTarget = measurement
            }
        )
    }
    editTarget?.let { measurement ->
        EditMeasurementDialog(
            measurement = measurement,
            onDismiss = { editTarget = null },
            onSave = { name, note ->
                vm.update(measurement, name, note)
                editTarget = null
                selected = measurement.copy(name = name.ifBlank { measurement.name }, note = note)
            }
        )
    }
    deleteTarget?.let { measurement ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Usunąć pomiar?") },
            text = { Text("Pomiar „${measurement.name}” zostanie trwale usunięty z historii.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteMeasurementAndPhoto(context, vm, measurement)
                    if (selected?.id == measurement.id) selected = null
                    deleteTarget = null
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Anuluj") } }
        )
    }
    if (deleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { deleteSelectedConfirm = false },
            title = { Text("Usunąć zaznaczone pomiary?") },
            text = { Text("Zostanie usuniętych ${selectedMeasurements.size} rekordów. Zdjęcia powiązane z tymi pomiarami też zostaną usunięte z Galerii, jeżeli są dostępne.") },
            confirmButton = {
                TextButton(onClick = {
                    selectedMeasurements.forEach { deleteMeasurementAndPhoto(context, vm, it) }
                    selectedIds = emptySet()
                    deleteSelectedConfirm = false
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { deleteSelectedConfirm = false }) { Text("Anuluj") } }
        )
    }
    fullPhoto?.let { uri ->
        FullPhotoDialog(photoUri = uri, onDismiss = { fullPhoto = null }, onOpenGallery = { openPhoto(context, uri) })
    }
}

@Composable
fun HistoryFilterControls(
    selectedType: String?,
    sortMode: String,
    onType: (String?) -> Unit,
    onSort: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selectedType == null, onClick = { onType(null) }, label = { Text("Wszystkie") })
        MeasurementType.entries.forEach { type ->
            FilterChip(selected = selectedType == type.name, onClick = { onType(type.name) }, label = { Text(type.label, maxLines = 1) })
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "date_desc" to "Najnowsze",
            "date_asc" to "Najstarsze",
            "value_desc" to "Wynik ↓",
            "value_asc" to "Wynik ↑",
            "type" to "Typ"
        ).forEach { (key, label) ->
            FilterChip(selected = sortMode == key, onClick = { onSort(key) }, label = { Text(label) })
        }
    }
}

@Composable
fun MeasurementThumbnail(photoUri: String?, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, onClick = onClick) {
        if (photoUri == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.ImageNotSupported, null) }
        } else {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageURI(Uri.parse(photoUri))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun MeasurementPreviewDialog(
    measurement: MeasurementEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onOpenPhoto: () -> Unit,
    onFullPhoto: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPng: () -> Unit,
    onExportText: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(measurement.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                measurement.photoUri?.let { uri ->
                    AndroidView(
                        factory = { context ->
                            ImageView(context).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setImageURI(Uri.parse(uri))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onFullPhoto) { Text("Pełny podgląd") }
                        TextButton(onClick = onOpenPhoto) { Text("Otwórz w Galerii") }
                    }
                }
                Text(measurementTypeLabel(measurement.type), style = MaterialTheme.typography.titleMedium)
                Text("Wynik: ${formatMeasurementValue(measurement)}")
                measurement.xValue?.let { Text("Oś X: ${"%.3f".format(it)}") }
                measurement.yValue?.let { Text("Oś Y: ${"%.3f".format(it)}") }
                Text(formatTolerance(measurement))
                measurement.targetValue?.let { Text("Cel: ${"%.3f".format(it)}") }
                Text("Data: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(measurement.createdAt))}")
                Text("Wartość osiągnięta: ${if (measurement.targetReached) "tak" else "nie"}")
                if (measurement.note.isNotBlank()) Text("Notatka: ${measurement.note}")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onEdit) { Text("Edytuj") }
                TextButton(onClick = onShare) { Text("Udostępnij") }
            }
        },
        dismissButton = {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = onExportPdf) { Text("PDF") }
                TextButton(onClick = onExportPng) { Text("PNG") }
                TextButton(onClick = onExportText) { Text("TXT") }
                TextButton(onClick = onDelete) { Text("Usuń") }
                TextButton(onClick = onDismiss) { Text("Zamknij") }
            }
        }
    )
}

@Composable
fun FullPhotoDialog(photoUri: String, onDismiss: () -> Unit, onOpenGallery: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = androidx.compose.ui.graphics.Color.Black, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageURI(Uri.parse(photoUri))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 620.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onOpenGallery) { Text("Galeria") }
                    TextButton(onClick = onDismiss) { Text("Zamknij") }
                }
            }
        }
    }
}

@Composable
fun EditMeasurementDialog(
    measurement: MeasurementEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(measurement.id) { mutableStateOf(measurement.name) }
    var note by remember(measurement.id) { mutableStateOf(measurement.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edytuj pomiar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notatka") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, note) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

fun shareMeasurement(context: android.content.Context, measurement: MeasurementEntity) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (measurement.photoUri != null) "image/jpeg" else "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "${measurement.name}\n${measurementTypeLabel(measurement.type)}\nWynik: ${formatMeasurementValue(measurement)}\n${formatTolerance(measurement)}"
        )
        measurement.photoUri?.let { putExtra(Intent.EXTRA_STREAM, Uri.parse(it)) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Udostępnij pomiar"))
}

fun openPhoto(context: android.content.Context, photoUri: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(photoUri), "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

fun photoAvailable(context: android.content.Context, photoUri: String): Boolean =
    runCatching { context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { true } == true }.getOrDefault(false)

fun deleteMeasurementAndPhoto(context: android.content.Context, vm: HistoryViewModel, measurement: MeasurementEntity) {
    measurement.photoUri?.let {
        runCatching { context.contentResolver.delete(Uri.parse(it), null, null) }
    }
    vm.delete(measurement)
}

fun measurementTypeLabel(type: String): String =
    runCatching { MeasurementType.valueOf(type).label }.getOrDefault(type)

fun formatMeasurementValue(measurement: MeasurementEntity): String {
    val unit = runCatching { AngleUnit.valueOf(measurement.unit) }.getOrNull()
    return when (unit) {
        AngleUnit.LUX -> "%.1f lx".format(measurement.mainValue)
        AngleUnit.DEGREES, AngleUnit.PERCENT, AngleUnit.MM_PER_M, AngleUnit.CM_PER_M, AngleUnit.RATIO -> MeasurementMath.formatByUnit(measurement.mainValue, unit)
        null -> "%.3f %s".format(measurement.mainValue, measurement.unit)
    }
}

fun formatTolerance(measurement: MeasurementEntity): String =
    if (measurement.tolerance <= 0f) "Bez tolerancji" else "Tolerancja: ±${measurement.tolerance}°"

@Composable
fun CalibrationScreen(nav: NavHostController, vm: CalibrationViewModel) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var newProfileDialog by remember { mutableStateOf(false) }
    var renameProfile by remember { mutableStateOf<com.poziomnica.domain.CalibrationProfile?>(null) }
    var deleteProfile by remember { mutableStateOf<com.poziomnica.domain.CalibrationProfile?>(null) }
    var restoreConfirm by remember { mutableStateOf(false) }
    Scaffold(topBar = { CompactMeasurementTopBar(nav, "Kalibracja") }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSection("Tylna obudowa 4x90°")
            AssistChip(onClick = {}, label = { Text("Do telefonu leżącego plecami na płaskiej powierzchni") }, leadingIcon = { Icon(Icons.Default.Smartphone, null) })
            Text("Zapisz 4 pozycje na tej samej powierzchni, obracając telefon co 90°. Korekta kompensuje wystający aparat, etui i nierówności obudowy.", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { state.planeSamples.size / 4f }, modifier = Modifier.fillMaxWidth())
            Text("Krok ${state.nextStep.coerceAtMost(4)}/4", style = MaterialTheme.typography.titleMedium)
            Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Aktualnie: X ${"%.2f".format(state.reading.surfaceX)}°   Y ${"%.2f".format(state.reading.surfaceY)}°   ${state.reading.supportEdge}", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::capturePlanePoint, enabled = !state.complete, modifier = Modifier.weight(1f)) { Text("Zapisz punkt") }
                OutlinedButton(onClick = vm::resetPlaneCalibrationFlow, modifier = Modifier.weight(1f)) { Text("Od nowa") }
            }
            Button(onClick = vm::savePlaneCalibration, enabled = state.complete, modifier = Modifier.fillMaxWidth()) { Text("Zatwierdź tylną obudowę") }
            HorizontalDivider()
            SettingsSection("Krawędzie 2x180°")
            AssistChip(onClick = {}, label = { Text("Do lewej/prawej lub górnej/dolnej krawędzi") }, leadingIcon = { Icon(Icons.Default.Straighten, null) })
            Text("Postaw telefon na kalibrowanej krawędzi, zapisz punkt, obróć telefon o 180° na tej samej krawędzi i zapisz drugi punkt.", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { state.edgeSamples.size / 2f }, modifier = Modifier.fillMaxWidth())
            Text("Krok ${state.edgeNextStep.coerceAtMost(2)}/2", style = MaterialTheme.typography.titleMedium)
            Text("Wykryta krawędź: ${state.reading.supportEdge}", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::captureEdgePoint, enabled = !state.edgeComplete, modifier = Modifier.weight(1f)) { Text("Zapisz punkt") }
                OutlinedButton(onClick = vm::resetEdgeCalibrationFlow, modifier = Modifier.weight(1f)) { Text("Od nowa") }
            }
            Button(onClick = vm::saveEdgeCalibration, enabled = state.edgeComplete, modifier = Modifier.fillMaxWidth()) { Text("Zatwierdź krawędź") }
            HorizontalDivider()
            SettingsSection("Szybkie zero")
            Text("Ustawia aktualne położenie jako zero dla aktywnego profilu.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { vm.quick() }, modifier = Modifier.fillMaxWidth()) { Text("Ustaw aktualne jako zero") }
            SettingsSection("Profile")
            OutlinedButton(onClick = { newProfileDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Dodaj profil z aktualnych ustawień")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = {
                            Text(
                                "Tył pitch ${"%.2f".format(p.offsetPitch)}°, roll ${"%.2f".format(p.offsetRoll)}°; " +
                                    "długa ${"%.2f".format(p.longEdgeOffset)}°, krótka ${"%.2f".format(p.shortEdgeOffset)}°; " +
                                    "ostatnia: ${if (p.lastCalibratedAt == 0L) "brak" else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(p.lastCalibratedAt))}"
                            )
                        },
                        leadingContent = { Icon(if (p.isActive) Icons.Default.CheckCircle else Icons.Default.Tune, null) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { vm.activate(p.id) }) { Icon(Icons.Default.RadioButtonChecked, "Aktywuj") }
                                IconButton(onClick = { vm.setDefault(p.id) }) { Icon(Icons.Default.PushPin, "Domyślny") }
                                if (!p.isDefault) IconButton(onClick = { renameProfile = p }) { Icon(Icons.Default.Edit, "Zmień nazwę") }
                                if (!p.isDefault) IconButton(onClick = { deleteProfile = p }) { Icon(Icons.Default.Delete, "Usuń") }
                            }
                        }
                    )
                }
            }
            OutlinedButton(onClick = { restoreConfirm = true }) { Text("Przywróć kalibrację fabryczną") }
        }
    }
    if (newProfileDialog) {
        ProfileNameDialog(
            title = "Nowy profil",
            initialName = "Profil własny",
            onDismiss = { newProfileDialog = false },
            onSave = {
                vm.createProfile(it)
                newProfileDialog = false
            }
        )
    }
    renameProfile?.let { profile ->
        ProfileNameDialog(
            title = "Zmień nazwę profilu",
            initialName = profile.name,
            onDismiss = { renameProfile = null },
            onSave = {
                vm.rename(profile.id, it)
                renameProfile = null
            }
        )
    }
    deleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteProfile = null },
            title = { Text("Usunąć profil?") },
            text = { Text("Profil „${profile.name}” zostanie usunięty. Zapisane pomiary w historii pozostaną.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(profile.id)
                    deleteProfile = null
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { deleteProfile = null }) { Text("Anuluj") } }
        )
    }
    if (restoreConfirm) {
        AlertDialog(
            onDismissRequest = { restoreConfirm = false },
            title = { Text("Przywrócić kalibrację fabryczną?") },
            text = { Text("Wszystkie profile kalibracji zostaną usunięte, a aktywny będzie profil fabryczny.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.restore()
                    restoreConfirm = false
                }) { Text("Przywróć") }
            },
            dismissButton = { TextButton(onClick = { restoreConfirm = false }) { Text("Anuluj") } }
        )
    }
}

@Composable
fun ProfileNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa profilu") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
fun SettingsScreen(nav: NavHostController, vm: SettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var resetSection by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf("Pomiar") }
    Scaffold(topBar = { CompactMeasurementTopBar(nav, "Ustawienia") }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Wygląd", "Pomiar", "Dźwięk", "Aparat", "Uprawnienia").forEach {
                    FilterChip(selected = section == it, onClick = { section = it }, label = { Text(it) }, leadingIcon = if (section == it) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
                }
            }
            when (section) {
                "Wygląd" -> {
                    SettingsSection("Wygląd")
                    SwitchRow("Ciemny motyw", settings.darkTheme, vm::darkTheme)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(settings.indicatorStyle == IndicatorStyle.REALISTIC, onClick = { vm.style(IndicatorStyle.REALISTIC) }, label = { Text("Realistyczny") })
                        FilterChip(settings.indicatorStyle == IndicatorStyle.MINIMAL, onClick = { vm.style(IndicatorStyle.MINIMAL) }, label = { Text("Minimalistyczny") })
                    }
                    OutlinedButton(onClick = { resetSection = "Wygląd" }, modifier = Modifier.fillMaxWidth()) { Text("Resetuj wygląd do domyślnych") }
                }
                "Pomiar" -> {
                    SettingsSection("Pomiar")
                    SliderRow("Tolerancja ±${"%.1f".format(settings.defaultTolerance)}°", settings.defaultTolerance, 0.1f..1.0f, vm::tolerance)
                    Text("Wygładzanie odczytu", style = MaterialTheme.typography.titleMedium)
                    Text("Szybkie reaguje natychmiast, bardzo stabilne mocniej tłumi drgania.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmoothingLevel.entries.forEach {
                            FilterChip(
                                selected = settings.smoothingLevel == it,
                                onClick = { vm.smoothing(it) },
                                label = { Text(it.label, maxLines = 1) },
                                leadingIcon = if (settings.smoothingLevel == it) ({ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }) else null
                            )
                        }
                    }
                    SwitchRow("Blokada wygaszania ekranu", settings.keepScreenOn, vm::keepOn)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(0, 1, 2, 3, 5).forEach { AssistChip(onClick = { vm.autoHold(it) }, label = { Text(if (it == 0) "Auto HOLD wył." else "${it}s") }) }
                    }
                    OutlinedButton(onClick = { resetSection = "Pomiar" }, modifier = Modifier.fillMaxWidth()) { Text("Resetuj pomiar do domyślnych") }
                }
                "Dźwięk" -> {
                    SettingsSection("Dźwięk i wibracje")
                    SwitchRow("Dźwięk", settings.soundEnabled, vm::sound)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoundMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.soundMode == mode,
                                onClick = { vm.soundMode(mode) },
                                label = { Text(mode.label, maxLines = 1) },
                                leadingIcon = if (settings.soundMode == mode) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
                            )
                        }
                    }
                    SliderRow("Głośność ${"%.0f".format(settings.volume * 100)}%", settings.volume, 0f..1f, vm::volume)
                    SliderRow("Ton ${settings.toneHz} Hz", settings.toneHz.toFloat(), 220f..2200f, vm::tone)
                    Button(onClick = vm::testSound) { Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(8.dp)); Text("Sprawdź dźwięk") }
                    SwitchRow("Wibracje", settings.vibrationEnabled, vm::vibration)
                    SliderRow("Siła wibracji ${settings.vibrationStrength}", settings.vibrationStrength.toFloat(), 1f..255f, vm::vibrationStrength)
                    SliderRow("Czas wibracji ${settings.vibrationDurationMs} ms", settings.vibrationDurationMs.toFloat(), 20f..500f, vm::vibrationDuration)
                    Button(onClick = vm::testVibration) { Icon(Icons.Default.Vibration, null); Spacer(Modifier.width(8.dp)); Text("Sprawdź wibrację") }
                    OutlinedButton(onClick = { resetSection = "Dźwięk" }, modifier = Modifier.fillMaxWidth()) { Text("Resetuj dźwięk i wibracje do domyślnych") }
                }
                "Aparat" -> {
                    SettingsSection("Aparat")
                    Text(
                        "Ekspozycja w trybie aparatu blokuje jasność i ostrość na środku kadru. Przydaje się, gdy aparat sam rozjaśnia lub przyciemnia obraz podczas przesuwania telefonu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SwitchRow("Siatka domyślnie", settings.cameraGrid, vm::cameraGrid)
                    SwitchRow("Linia pionowa", settings.cameraVerticalLine, vm::cameraVertical)
                    SwitchRow("Linia pozioma", settings.cameraHorizontalLine, vm::cameraHorizontal)
                    SliderRow("Przejrzystość nakładki ${"%.0f".format(settings.overlayAlpha * 100)}%", settings.overlayAlpha, 0.15f..1f, vm::overlayAlpha)
                    SliderRow("Grubość linii ${"%.1f".format(settings.lineWidth)}", settings.lineWidth, 1f..8f, vm::lineWidth)
                    OutlinedButton(onClick = { resetSection = "Aparat" }, modifier = Modifier.fillMaxWidth()) { Text("Resetuj aparat do domyślnych") }
                }
                else -> {
                    SettingsSection("Uprawnienia")
                    Text("Aplikacja wymaga uprawnienia aparatu tylko w trybie Aparat. Pozostałe moduły działają bez aparatu, internetu, lokalizacji i konta użytkownika.", style = MaterialTheme.typography.bodyMedium)
                    AssistChip(onClick = {}, label = { Text("Aparat: wymagany tylko dla trybu aparatu") }, leadingIcon = { Icon(Icons.Default.PhotoCamera, null) })
                    AssistChip(onClick = {}, label = { Text("Lokalizacja: niewymagana") }, leadingIcon = { Icon(Icons.Default.LocationOff, null) })
                    AssistChip(onClick = {}, label = { Text("Internet: niewymagany") }, leadingIcon = { Icon(Icons.Default.CloudOff, null) })
                    OutlinedButton(onClick = { resetSection = "Wszystko" }, modifier = Modifier.fillMaxWidth()) { Text("Reset wszystkich ustawień") }
                }
            }
        }
    }
    resetSection?.let { target ->
        AlertDialog(
            onDismissRequest = { resetSection = null },
            title = { Text("Reset ustawień") },
            text = { Text("Czy na pewno przywrócić ustawienia sekcji „$target” do wartości domyślnych?") },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        "Wygląd" -> vm.resetAppearance()
                        "Pomiar" -> vm.resetMeasurement()
                        "Dźwięk" -> vm.resetFeedback()
                        "Aparat" -> vm.resetCamera()
                        else -> vm.reset()
                    }
                    resetSection = null
                }) { Text("Resetuj") }
            },
            dismissButton = {
                TextButton(onClick = { resetSection = null }) { Text("Anuluj") }
            }
        )
    }
}

@Composable
fun FaqScreen(nav: NavHostController) {
    Scaffold(topBar = { CompactMeasurementTopBar(nav, "FAQ") }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(faqItems()) { item ->
                var expanded by remember(item.title) { mutableStateOf(false) }
                ElevatedCard(onClick = { expanded = !expanded }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                        }
                        if (expanded) {
                            Text(item.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

data class FaqItem(val title: String, val body: String)

fun faqItems(): List<FaqItem> = listOf(
    FaqItem(
        "Poziomnica",
        "Tryb służy do ustawiania poziomu na długiej albo krótkiej krawędzi telefonu. Najlepiej oprzeć telefon stabilnie o mierzony element i obserwować bąbelek oraz wartość kąta. Cel pozwala ustawić inny kąt niż 0°, na przykład 5°. Tolerancja decyduje, kiedy wskaźnik robi się zielony oraz kiedy działa dźwięk i wibracja."
    ),
    FaqItem(
        "Pion",
        "Tryb pionu mierzy odchylenie od pionu po przyłożeniu telefonu krawędzią do ściany, słupka, framugi albo profilu. Telefon sam pokazuje wykrytą krawędź. Wynik bliski 0° oznacza pion. Przed precyzyjnym użyciem warto skalibrować krawędź."
    ),
    FaqItem(
        "Poziomowanie powierzchni",
        "Ten tryb działa, gdy telefon leży tylną obudową na stole, pralce, maszynie albo innej płaskiej powierzchni. Bąbelek pokazuje kierunek regulacji w dwóch osiach. Zielony środek oznacza, że powierzchnia mieści się w tolerancji. Zerowanie ustawia aktualne położenie jako punkt odniesienia."
    ),
    FaqItem(
        "Spadek",
        "Tryb spadku służy do rur, rynien, odpływów, parapetów, tarasów i posadzek. Cel można ustawić w stopniach, procentach, mm/m albo cm/m. Aplikacja przelicza jednostki i pokazuje, czy spadek jest za mały, za duży albo prawidłowy."
    ),
    FaqItem(
        "Kątomierz",
        "Najpierw ustaw telefon na pierwszej powierzchni i naciśnij Baza. Następnie przechyl albo przełóż telefon na drugą powierzchnię i naciśnij Nachylenie. Wynik pokazuje kąt między bazą i drugim położeniem. Reset rozpoczyna nowy pomiar."
    ),
    FaqItem(
        "Aparat",
        "Tryb aparatu pokazuje linie poziomu, pionu, siatkę i cyfrowe wartości orientacji telefonu. Zdjęcie bez nakładki zapisuje sam obraz, a zdjęcie z nakładką zapisuje obraz z liniami pomocniczymi i wynikiem. Przycisk Ekspozycja blokuje automatyczną jasność i ostrość na środku kadru, żeby obraz nie zmieniał jasności podczas mierzenia. Wyłączenie przywraca automatykę aparatu. Dotknięcie dwóch punktów na obrazie pozwala pomocniczo ocenić linię widoczną w kadrze. Taki pomiar może być zniekształcony przez perspektywę, jeżeli telefon nie jest równolegle do obiektu."
    ),
    FaqItem(
        "Historia",
        "Historia przechowuje lokalne pomiary bez konta i bez internetu. Pomiar można podejrzeć, udostępnić, wyeksportować albo usunąć. Przy wielu zaznaczonych pomiarach eksport obejmuje tylko zaznaczenie."
    ),
    FaqItem(
        "Kalibracja",
        "Kalibracja kompensuje etui, wystający aparat i nierówności obudowy. Dla tylnej obudowy wykonaj cztery pomiary na tej samej powierzchni, obracając telefon co 90°. Dla krawędzi wykonaj dwa pomiary na tej samej krawędzi po obrocie telefonu o 180°. Po zmianie etui warto utworzyć osobny profil kalibracji."
    ),
    FaqItem(
        "Ustawienia",
        "Wygląd zmienia motyw i styl wskaźników. Pomiar ustawia tolerancję, jednostkę, wygładzanie i automatyczny HOLD. Dźwięk i wibracje ustawiają sposób sygnalizacji celu. Aparat ustawia domyślne linie, siatkę, grubość i przezroczystość nakładki."
    ),
    FaqItem(
        "Luksomierz",
        "Luksomierz korzysta z czujnika światła telefonu. Czujnik zwykle znajduje się przy kamerze przedniej lub głośniku, dlatego nie należy zasłaniać górnej części ekranu. Wynik jest orientacyjny i zależy od konkretnego telefonu."
    )
)

@Composable
fun SettingsSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked, onChange)
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label)
        Slider(value, onValueChange = onChange, valueRange = range)
    }
}
