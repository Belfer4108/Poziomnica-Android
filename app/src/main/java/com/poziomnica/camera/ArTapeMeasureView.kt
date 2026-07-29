package com.poziomnica.camera

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.poziomnica.viewmodel.ArPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class ArTapeMeasureFrameState(
    val message: String,
    val targetAvailable: Boolean,
    val centerPoint: ArPoint?,
    val phoneDistanceMeters: Float?,
    val anchors: List<ArTapeProjectedPoint> = emptyList(),
    val plane: ArTapeProjectedPlane? = null,
    val cursor: ArTapeProjectedCursor? = null,
    val featurePoints: List<ArTapeProjectedFeaturePoint> = emptyList(),
    val planeKind: ArPlaneKind = ArPlaneKind.UNKNOWN
)

data class ArTapeProjectedPoint(
    val index: Int,
    val x: Float,
    val y: Float,
    val tracking: Boolean
)

data class ArTapeProjectedPlane(
    val kind: ArPlaneKind,
    val points: List<ArTapeProjectedFeaturePoint>,
    val scanPoints: List<ArTapeProjectedFeaturePoint> = emptyList()
)

data class ArTapeProjectedCursor(
    val kind: ArPlaneKind,
    val centerX: Float,
    val centerY: Float,
    val ring: List<ArTapeProjectedFeaturePoint>
)

data class ArTapeProjectedFeaturePoint(
    val x: Float,
    val y: Float
)

enum class ArPlaneKind {
    FLOOR,
    CEILING,
    WALL,
    UNKNOWN
}

class ArTapeMeasureView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
    var onFrameState: ((ArTapeMeasureFrameState) -> Unit)? = null
    @Volatile
    var preferredPlaneKind: ArPlaneKind = ArPlaneKind.UNKNOWN
    @Volatile
    var allowVisiblePlanePointFallback: Boolean = false

    private var session: Session? = null
    private var installRequested = false
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var textureId = -1
    private var program = 0
    private var positionAttrib = 0
    private var texCoordAttrib = 0
    private var textureUniform = 0
    private var latestFrame: Frame? = null
    private var latestCenterPoint: ArPoint? = null
    private var latestPhoneDistance: Float? = null
    private val anchors = mutableListOf<Anchor>()
    private val quadCoords = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val texCoords = FloatArray(8)
    private val quadBuffer: FloatBuffer = directFloatBuffer(quadCoords)
    private val texBuffer: FloatBuffer = directFloatBuffer(FloatArray(8))

    init {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun resumeSession() {
        val activity = context as? Activity ?: return
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isTransient) {
            postDelayed({ resumeSession() }, 200)
            return
        }
        if (!availability.isSupported) {
            emitState(ArTapeMeasureFrameState("Ten telefon nie obsługuje ARCore.", false, null, null))
            return
        }
        try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    emitState(ArTapeMeasureFrameState("Zainstaluj lub zaktualizuj Usługi Google Play dla AR.", false, null, null))
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            if (session == null) {
                session = Session(context).also { configureSession(it) }
            }
            super.onResume()
            session?.resume()
        } catch (t: Throwable) {
            emitState(ArTapeMeasureFrameState("ARCore niedostępny: ${t.message ?: "błąd uruchomienia"}", false, null, null))
        }
    }

    fun pauseSession() {
        queueEvent { latestFrame = null }
        session?.pause()
        super.onPause()
    }

    fun currentPoint(): ArPoint? = latestCenterPoint

    fun addCurrentAnchor(maxAnchors: Int = 64): ArPoint? {
        val frame = latestFrame ?: return null
        val hit = bestPlaneHit(frame)
        if (hit == null && allowVisiblePlanePointFallback) {
            return bestVisiblePlane(frame)?.let { planeCenterPoint(frame, it) }
        }
        hit ?: return null
        val anchor = runCatching { hit.createAnchor() }.getOrNull() ?: return null
        if (anchors.size >= maxAnchors) {
            anchors.removeAt(0).detach()
        }
        anchors.add(anchor)
        val projected = projectWorld(frame, anchor.pose.tx(), anchor.pose.ty(), anchor.pose.tz())
        return anchor.pose.let {
            ArPoint(
                x = it.tx(),
                y = it.ty(),
                z = it.tz(),
                screenX = projected?.x ?: viewportWidth / 2f,
                screenY = projected?.y ?: viewportHeight / 2f,
                distanceFromCameraMeters = distanceFromCamera(frame.camera.pose, it),
                planeKind = planeKind(hit.trackable)
            )
        }
    }

    fun addCurrentAnchorAsync(maxAnchors: Int = 64, onPoint: (ArPoint?) -> Unit) {
        queueEvent {
            val frame = latestFrame
            val point = if (frame == null) {
                null
            } else {
                val hit = bestPlaneHit(frame)
                val anchor = hit?.let { runCatching { it.createAnchor() }.getOrNull() }
                if (anchor == null) {
                    if (allowVisiblePlanePointFallback) bestVisiblePlane(frame)?.let { planeCenterPoint(frame, it) } else null
                } else {
                    if (anchors.size >= maxAnchors) {
                        anchors.removeAt(0).detach()
                    }
                        anchors.add(anchor)
                    val projected = projectWorld(frame, anchor.pose.tx(), anchor.pose.ty(), anchor.pose.tz())
                    anchor.pose.let {
                        ArPoint(
                            x = it.tx(),
                            y = it.ty(),
                            z = it.tz(),
                            screenX = projected?.x ?: viewportWidth / 2f,
                            screenY = projected?.y ?: viewportHeight / 2f,
                            distanceFromCameraMeters = distanceFromCamera(frame.camera.pose, it),
                            planeKind = planeKind(hit.trackable)
                        )
                    }
                }
            }
            post { onPoint(point) }
        }
    }

    fun clearAnchors() {
        anchors.forEach { it.detach() }
        anchors.clear()
    }

    fun captureBitmap(onReady: (Bitmap?) -> Unit) {
        queueEvent {
            val width = viewportWidth
            val height = viewportHeight
            val buffer = IntArray(width * height)
            val intBuffer = IntBuffer.wrap(buffer)
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, intBuffer)
            val flipped = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = buffer[y * width + x]
                    val blue = pixel and 0x000000ff
                    val red = pixel and 0x00ff0000
                    val fixed = (pixel and -0x1000000) or (blue shl 16) or (pixel and 0x0000ff00) or (red shr 16)
                    flipped[(height - y - 1) * width + x] = fixed
                }
            }
            val bitmap = Bitmap.createBitmap(flipped, width, height, Bitmap.Config.ARGB_8888)
            post { onReady(bitmap) }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        textureId = createExternalTexture()
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "u_Texture")
        session?.setCameraTextureName(textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        session?.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val arSession = session ?: return
        if (textureId != -1) arSession.setCameraTextureName(textureId)
        val frame = runCatching { arSession.update() }.getOrNull() ?: return
        latestFrame = frame
        drawCamera(frame)
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            latestCenterPoint = null
            latestPhoneDistance = null
            emitState(
                ArTapeMeasureFrameState(
                    "Poruszaj telefonem powoli, aż ARCore złapie śledzenie.",
                    false,
                    null,
                    null,
                    projectedAnchors(frame),
                    featurePoints = projectedFeaturePoints(frame)
                )
            )
            return
        }
        val hit = bestPlaneHit(frame)
        val detectedPlane = (hit?.trackable as? Plane) ?: bestVisiblePlane(frame)
        val phoneDistance = hit?.hitPose?.let { distanceFromCamera(camera.pose, it) }
        val hitPoint = hit?.hitPose?.let {
            val projected = projectWorld(frame, it.tx(), it.ty(), it.tz())
            ArPoint(
                x = it.tx(),
                y = it.ty(),
                z = it.tz(),
                screenX = projected?.x ?: viewportWidth / 2f,
                screenY = projected?.y ?: viewportHeight / 2f,
                distanceFromCameraMeters = phoneDistance,
                planeKind = planeKind(hit.trackable)
            )
        }
        val fallbackPoint = if (hitPoint == null && allowVisiblePlanePointFallback && detectedPlane != null) {
            planeCenterPoint(frame, detectedPlane)
        } else {
            null
        }
        val measurementPoint = hitPoint ?: fallbackPoint
        latestCenterPoint = measurementPoint
        latestPhoneDistance = phoneDistance
        val cursor = when {
            hit != null && detectedPlane != null -> projectedCursor(frame, hit.hitPose, detectedPlane)
            detectedPlane != null -> projectedCursor(frame, detectedPlane.centerPose, detectedPlane)
            else -> null
        }
        emitState(
            ArTapeMeasureFrameState(
                message = if (detectedPlane == null) {
                    "Szukam płaszczyzny. Oddal lub przybliż aparat i poruszaj telefonem powoli."
                } else if (hitPoint == null && fallbackPoint != null) {
                    "Wykryto płaszczyznę: ${planeKindLabel(planeKind(detectedPlane))}. Tryb wysokości użyje jej poziomu."
                } else if (measurementPoint == null) {
                    "Płaszczyzna wykryta: ${planeKindLabel(planeKind(detectedPlane))}. Ustaw środek ekranu na powierzchni."
                } else {
                    "Celownik jest przyklejony do płaszczyzny: ${planeKindLabel(planeKind(hit?.trackable))}."
                },
                targetAvailable = measurementPoint != null,
                centerPoint = measurementPoint,
                phoneDistanceMeters = phoneDistance,
                anchors = projectedAnchors(frame),
                plane = detectedPlane?.let { projectedPlane(frame, it) },
                cursor = cursor,
                featurePoints = projectedFeaturePoints(frame),
                planeKind = planeKind(detectedPlane)
            )
        )
    }

    private fun emitState(state: ArTapeMeasureFrameState) {
        post { onFrameState?.invoke(state) }
    }

    private fun configureSession(arSession: Session) {
        val config = Config(arSession).apply {
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            focusMode = Config.FocusMode.AUTO
            if (arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                depthMode = Config.DepthMode.AUTOMATIC
            }
        }
        arSession.configure(config)
    }

    private fun drawCamera(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED,
                texCoords
            )
            texBuffer.clear()
            texBuffer.put(texCoords)
            texBuffer.position(0)
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureUniform, 0)
        quadBuffer.position(0)
        GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        texBuffer.position(0)
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glEnableVertexAttribArray(texCoordAttrib)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDepthMask(true)
    }

    private fun bestPlaneHit(frame: Frame): HitResult? {
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f
        val maxOffset = (viewportWidth.coerceAtMost(viewportHeight) * 0.24f).coerceIn(70f, 260f)
        val samples = listOf(
            0f to 0f,
            -maxOffset * 0.45f to 0f,
            maxOffset * 0.45f to 0f,
            0f to -maxOffset * 0.45f,
            0f to maxOffset * 0.45f,
            -maxOffset to 0f,
            maxOffset to 0f,
            0f to -maxOffset,
            0f to maxOffset,
            -maxOffset * 0.7f to -maxOffset * 0.7f,
            maxOffset * 0.7f to -maxOffset * 0.7f,
            -maxOffset * 0.7f to maxOffset * 0.7f,
            maxOffset * 0.7f to maxOffset * 0.7f
        )
        val aimedKind = aimedPlaneKind(frame)
        val preferred = preferredPlaneKind.takeIf { it != ArPlaneKind.UNKNOWN } ?: aimedKind
        val candidates = samples.flatMap { (dx, dy) ->
            val sampleX = (centerX + dx).coerceIn(0f, viewportWidth.toFloat())
            val sampleY = (centerY + dy).coerceIn(0f, viewportHeight.toFloat())
            frame.hitTest(sampleX, sampleY).mapNotNull { hit ->
                val trackable = hit.trackable as? Plane ?: return@mapNotNull null
                if (trackable.trackingState != TrackingState.TRACKING || !trackable.isPoseInPolygon(hit.hitPose)) return@mapNotNull null
                val kind = planeKind(trackable)
                val projected = projectWorld(frame, hit.hitPose.tx(), hit.hitPose.ty(), hit.hitPose.tz())
                val screenDistance = projected?.let {
                    val sx = it.x - centerX
                    val sy = it.y - centerY
                    sx * sx + sy * sy
                } ?: (dx * dx + dy * dy)
                val kindPenalty = if (preferred != ArPlaneKind.UNKNOWN && kind != preferred) {
                    viewportWidth.coerceAtMost(viewportHeight) * viewportWidth.coerceAtMost(viewportHeight) * 0.38f
                } else {
                    0f
                }
                val centerSamplePenalty = dx * dx + dy * dy
                hit to (screenDistance + centerSamplePenalty * 0.35f + kindPenalty)
            }
        }
        return candidates.minByOrNull { it.second }?.first
    }

    private fun bestVisiblePlane(frame: Frame): Plane? {
        val aimedKind = aimedPlaneKind(frame)
        val preferred = preferredPlaneKind.takeIf { it != ArPlaneKind.UNKNOWN } ?: aimedKind
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f
        return session?.getAllTrackables(Plane::class.java)
            ?.filter { plane ->
                plane.trackingState == TrackingState.TRACKING &&
                    plane.subsumedBy == null
            }
            ?.mapNotNull { plane ->
                val projected = projectedPlane(frame, plane) ?: return@mapNotNull null
                val nearestPoint = (projected.scanPoints.ifEmpty { projected.points }).minByOrNull { point ->
                    val dx = point.x - centerX
                    val dy = point.y - centerY
                    dx * dx + dy * dy
                } ?: return@mapNotNull null
                val dx = nearestPoint.x - centerX
                val dy = nearestPoint.y - centerY
                val kind = planeKind(plane)
                val kindPenalty = if (preferred != ArPlaneKind.UNKNOWN && kind != preferred) {
                    viewportWidth.coerceAtMost(viewportHeight) * viewportWidth.coerceAtMost(viewportHeight) * 0.42f
                } else {
                    0f
                }
                val distanceScore = dx * dx + dy * dy + kindPenalty
                plane to distanceScore
            }
            ?.minByOrNull { it.second }
            ?.first
    }

    private fun aimedPlaneKind(frame: Frame): ArPlaneKind {
        val forward = FloatArray(3)
        frame.camera.pose.getTransformedAxis(2, -1f, forward, 0)
        val verticalComponent = forward[1]
        return when {
            abs(verticalComponent) < 0.46f -> ArPlaneKind.WALL
            verticalComponent < 0f -> ArPlaneKind.FLOOR
            else -> ArPlaneKind.CEILING
        }
    }

    private fun planeKind(trackable: Any?): ArPlaneKind = when (trackable) {
        is Plane -> when (trackable.type) {
            Plane.Type.HORIZONTAL_UPWARD_FACING -> ArPlaneKind.FLOOR
            Plane.Type.HORIZONTAL_DOWNWARD_FACING -> ArPlaneKind.CEILING
            Plane.Type.VERTICAL -> ArPlaneKind.WALL
        }
        else -> ArPlaneKind.UNKNOWN
    }

    private fun planeKindLabel(kind: ArPlaneKind): String = when (kind) {
        ArPlaneKind.FLOOR -> "podłoga/blat"
        ArPlaneKind.WALL -> "ściana"
        ArPlaneKind.CEILING -> "sufit"
        ArPlaneKind.UNKNOWN -> "nieznana"
    }

    private fun planeCenterPoint(frame: Frame, plane: Plane): ArPoint {
        val pose = plane.centerPose
        val projected = projectWorld(frame, pose.tx(), pose.ty(), pose.tz())
        return ArPoint(
            x = pose.tx(),
            y = pose.ty(),
            z = pose.tz(),
            screenX = projected?.x ?: viewportWidth / 2f,
            screenY = projected?.y ?: viewportHeight / 2f,
            distanceFromCameraMeters = distanceFromCamera(frame.camera.pose, pose),
            planeKind = planeKind(plane)
        )
    }

    private fun distanceFromCamera(cameraPose: Pose, pose: Pose): Float {
        val dx = pose.tx() - cameraPose.tx()
        val dy = pose.ty() - cameraPose.ty()
        val dz = pose.tz() - cameraPose.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun projectedPlane(frame: Frame, plane: Plane): ArTapeProjectedPlane? {
        if (plane.trackingState != TrackingState.TRACKING) return null
        val polygon = plane.polygon ?: return null
        val localPoints = mutableListOf<Pair<Float, Float>>()
        val projectedBoundary = mutableListOf<ArTapeProjectedFeaturePoint>()
        while (polygon.hasRemaining()) {
            val x = polygon.get()
            val z = polygon.get()
            localPoints.add(x to z)
            val pose = plane.centerPose.compose(Pose.makeTranslation(x, 0f, z))
            projectWorld(frame, pose.tx(), pose.ty(), pose.tz())?.let { projectedBoundary.add(it) }
        }
        if (localPoints.size < 3 || projectedBoundary.size < 3) return null
        return ArTapeProjectedPlane(
            kind = planeKind(plane),
            points = projectedBoundary,
            scanPoints = projectedPlaneScanPoints(frame, plane, localPoints)
        )
    }

    private fun projectedPlaneScanPoints(
        frame: Frame,
        plane: Plane,
        polygon: List<Pair<Float, Float>>
    ): List<ArTapeProjectedFeaturePoint> {
        val minX = polygon.minOf { it.first }
        val maxX = polygon.maxOf { it.first }
        val minZ = polygon.minOf { it.second }
        val maxZ = polygon.maxOf { it.second }
        val extent = maxOf(maxX - minX, maxZ - minZ).coerceAtLeast(0.01f)
        val step = when {
            extent < 0.8f -> 0.055f
            extent < 1.8f -> 0.075f
            else -> 0.11f
        }
        val result = mutableListOf<ArTapeProjectedFeaturePoint>()
        var row = 0
        var z = minZ
        while (z <= maxZ && result.size < 420) {
            var x = minX + if (row % 2 == 0) 0f else step * 0.5f
            while (x <= maxX && result.size < 420) {
                if (pointInsidePolygon(x, z, polygon)) {
                    val pose = plane.centerPose.compose(Pose.makeTranslation(x, 0f, z))
                    projectWorld(frame, pose.tx(), pose.ty(), pose.tz())?.let { result.add(it) }
                }
                x += step
            }
            z += step
            row++
        }
        return result
    }

    private fun pointInsidePolygon(x: Float, z: Float, polygon: List<Pair<Float, Float>>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].first
            val zi = polygon[i].second
            val xj = polygon[j].first
            val zj = polygon[j].second
            val intersects = ((zi > z) != (zj > z)) &&
                (x < (xj - xi) * (z - zi) / ((zj - zi).takeIf { kotlin.math.abs(it) > 0.0001f } ?: 0.0001f) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private fun projectedCursor(frame: Frame, centerPose: Pose, plane: Plane): ArTapeProjectedCursor? {
        val center = projectWorld(frame, centerPose.tx(), centerPose.ty(), centerPose.tz()) ?: return null
        val ring = mutableListOf<ArTapeProjectedFeaturePoint>()
        val radiusMeters = 0.14f
        val samples = 72
        for (i in 0 until samples) {
            val angle = (i / samples.toDouble()) * PI * 2.0
            val pose = centerPose.compose(
                Pose.makeTranslation(
                    (cos(angle) * radiusMeters).toFloat(),
                    0f,
                    (sin(angle) * radiusMeters).toFloat()
                )
            )
            projectWorld(frame, pose.tx(), pose.ty(), pose.tz())?.let { ring.add(it) }
        }
        return if (ring.size >= 8) ArTapeProjectedCursor(planeKind(plane), center.x, center.y, ring) else null
    }

    private fun bestPlaneHitAt(frame: Frame, x: Float, y: Float): HitResult? {
        val preferred = preferredPlaneKind.takeIf { it != ArPlaneKind.UNKNOWN } ?: aimedPlaneKind(frame)
        val hits = frame.hitTest(x.coerceIn(0f, viewportWidth.toFloat()), y.coerceIn(0f, viewportHeight.toFloat()))
        val candidates = hits.mapNotNull { hit ->
            val plane = hit.trackable as? Plane ?: return@mapNotNull null
            if (plane.trackingState != TrackingState.TRACKING || !plane.isPoseInPolygon(hit.hitPose)) return@mapNotNull null
            val kindPenalty = if (preferred != ArPlaneKind.UNKNOWN && planeKind(plane) != preferred) 0.25f else 0f
            hit to (hit.distance + kindPenalty)
        }
        return candidates.minByOrNull { it.second }?.first
    }

    private fun distance(a: Pose, b: Pose): Float {
        return hypot(hypot(a.tx() - b.tx(), a.ty() - b.ty()), a.tz() - b.tz())
    }

    private fun projectedFeaturePoints(frame: Frame): List<ArTapeProjectedFeaturePoint> {
        val cloud = runCatching { frame.acquirePointCloud() }.getOrNull() ?: return emptyList()
        return try {
            val points = cloud.points
            val result = mutableListOf<ArTapeProjectedFeaturePoint>()
            var index = 0
            while (points.hasRemaining() && result.size < 90) {
                val x = points.get()
                val y = points.get()
                val z = points.get()
                points.get()
                if (index % 3 == 0) projectWorld(frame, x, y, z)?.let { result.add(it) }
                index++
            }
            result
        } finally {
            cloud.release()
        }
    }

    private fun projectWorld(frame: Frame, x: Float, y: Float, z: Float): ArTapeProjectedFeaturePoint? {
        val camera = frame.camera
        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        val viewProjection = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)
        val clip = FloatArray(4)
        Matrix.multiplyMV(clip, 0, viewProjection, 0, floatArrayOf(x, y, z, 1f), 0)
        if (clip[3] <= 0f) return null
        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        return ArTapeProjectedFeaturePoint(
            x = (ndcX + 1f) * 0.5f * viewportWidth,
            y = (1f - ndcY) * 0.5f * viewportHeight
        )
    }

    private fun projectedAnchors(frame: Frame): List<ArTapeProjectedPoint> {
        val camera = frame.camera
        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        val viewProjection = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)
        return anchors.mapIndexedNotNull { index, anchor ->
            val pose = anchor.pose
            val world = floatArrayOf(pose.tx(), pose.ty(), pose.tz(), 1f)
            val clip = FloatArray(4)
            Matrix.multiplyMV(clip, 0, viewProjection, 0, world, 0)
            if (clip[3] <= 0f) return@mapIndexedNotNull null
            val ndcX = clip[0] / clip[3]
            val ndcY = clip[1] / clip[3]
            ArTapeProjectedPoint(
                index = index,
                x = (ndcX + 1f) * 0.5f * viewportWidth,
                y = (1f - ndcY) * 0.5f * viewportHeight,
                tracking = anchor.trackingState == TrackingState.TRACKING
            )
        }
    }

    private fun displayRotation(): Int = if (android.os.Build.VERSION.SDK_INT >= 30) {
        display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (context as? Activity)?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertex)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragment)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    private fun loadShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also {
        GLES20.glShaderSource(it, source)
        GLES20.glCompileShader(it)
    }

    private fun directFloatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            position(0)
        }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_Texture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """
    }
}

