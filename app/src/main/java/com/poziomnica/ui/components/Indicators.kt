package com.poziomnica.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.poziomnica.domain.IndicatorStyle
import kotlin.math.abs

@Composable
fun LinearVial(
    angle: Float,
    tolerance: Float,
    style: IndicatorStyle,
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
    scaleDegrees: Float = 12f
) {
    val reached = abs(angle) <= tolerance
    val statusColor = when {
        reached -> Color(0xFF18A058)
        abs(angle) < tolerance * 4 -> Color(0xFFFFA726)
        else -> Color(0xFFE53935)
    }
    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(if (vertical) 0.38f else 4.2f)) {
        val tube = if (vertical) Size(size.width * 0.45f, size.height * 0.86f) else Size(size.width * 0.92f, size.height * 0.46f)
        val topLeft = Offset((size.width - tube.width) / 2f, (size.height - tube.height) / 2f)
        drawRoundRect(
            color = if (style == IndicatorStyle.REALISTIC) Color(0xFF263238) else Color(0xFF0F1518),
            topLeft = topLeft - Offset(10f, 10f),
            size = Size(tube.width + 20f, tube.height + 20f),
            cornerRadius = CornerRadius(38f, 38f)
        )
        drawRoundRect(Color(0xAAE2F5AA), topLeft, tube, CornerRadius(32f, 32f))
        drawRoundRect(statusColor.copy(alpha = if (reached) 0.32f else 0.12f), topLeft, tube, CornerRadius(32f, 32f))
        repeat(11) { i ->
            val f = i / 10f
            if (vertical) {
                val y = topLeft.y + tube.height * f
                drawLine(Color(0xFF1D2A2D), Offset(size.width / 2f - 35f, y), Offset(size.width / 2f + 35f, y), 2f)
            } else {
                val x = topLeft.x + tube.width * f
                drawLine(Color(0xFF1D2A2D), Offset(x, size.height / 2f - 35f), Offset(x, size.height / 2f + 35f), 2f)
            }
        }
        if (vertical) {
            drawLine(statusColor, Offset(size.width / 2f, topLeft.y + 22f), Offset(size.width / 2f, topLeft.y + tube.height - 22f), 5f, StrokeCap.Round)
        } else {
            drawLine(statusColor, Offset(topLeft.x + 22f, size.height / 2f), Offset(topLeft.x + tube.width - 22f, size.height / 2f), 5f, StrokeCap.Round)
        }
        val displacement = (angle / scaleDegrees.coerceAtLeast(1f)).coerceIn(-1f, 1f)
        val bubbleRadius = if (vertical) tube.width * 0.27f else tube.height * 0.34f
        val center = if (vertical) Offset(size.width / 2f, size.height / 2f + displacement * tube.height * 0.38f) else Offset(size.width / 2f + displacement * tube.width * 0.42f, size.height / 2f)
        drawCircle(Color.White.copy(alpha = if (reached) 0.56f else 0.44f), bubbleRadius, center)
        drawCircle(Color(0xFF6E8C63).copy(alpha = 0.72f), bubbleRadius, center, style = Stroke(3f))
    }
}

@Composable
fun SurfaceBullseye(x: Float, y: Float, tolerance: Float, modifier: Modifier = Modifier) {
    val reached = kotlin.math.hypot(x.toDouble(), y.toDouble()) <= tolerance
    Canvas(modifier = modifier.size(310.dp)) {
        val radius = size.minDimension / 2f * 0.92f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF101719), radius, center)
        drawCircle(Color(0xFFE8F5E9), radius * 0.96f, center)
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { drawCircle(Color(0xFF607D8B), radius * it, center, style = Stroke(2f)) }
        drawLine(Color(0xFF455A64), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 2f)
        drawLine(Color(0xFF455A64), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 2f)
        val centerZoneRadius = (tolerance / 5f).coerceIn(0.05f, 0.24f) * radius
        drawCircle((if (reached) Color(0xFF18A058) else Color(0xFFFFA726)).copy(alpha = 0.28f), centerZoneRadius, center)
        drawCircle(if (reached) Color(0xFF18A058) else Color(0xFFFFA726), centerZoneRadius, center, style = Stroke(6f))
        val bubble = Offset(center.x + (x / 10f).coerceIn(-1f, 1f) * radius * 0.78f, center.y + (y / 10f).coerceIn(-1f, 1f) * radius * 0.78f)
        val bubbleColor = if (reached) Color(0xFF18A058) else Color.White
        drawCircle(bubbleColor.copy(alpha = if (reached) 0.58f else 0.42f), radius * 0.12f, bubble)
        drawCircle(if (reached) Color(0xFF0B7D3B) else Color(0xFF1B5E20), radius * 0.12f, bubble, style = Stroke(3f))
        drawCircle(Color(0xFF263238), radius * 0.024f, center)
        drawCircle(Color.White.copy(alpha = 0.86f), radius * 0.011f, center)
    }
}

@Composable
fun CameraOverlay(
    roll: Float,
    pitch: Float,
    grid: Boolean,
    vertical: Boolean,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    alpha: Float = 0.85f,
    lineWidth: Float = 2.5f,
    tolerance: Float = 0.3f
) {
    Canvas(modifier = modifier) {
        val green = Color(0xFF18A058)
        val red = Color(0xFFE53935)
        val center = Offset(size.width / 2f, size.height / 2f)
        val a = alpha.coerceIn(0.15f, 1f)
        val stroke = lineWidth.coerceIn(1f, 8f)
        if (grid) {
            repeat(3) { i ->
                val x = size.width * (i + 1) / 4f
                val y = size.height * (i + 1) / 4f
                drawLine(Color.White.copy(alpha = 0.32f * a), Offset(x, 0f), Offset(x, size.height), stroke * 0.55f)
                drawLine(Color.White.copy(alpha = 0.32f * a), Offset(0f, y), Offset(size.width, y), stroke * 0.55f)
            }
        }
        if (vertical) drawLine((if (abs(roll) <= tolerance) green else red).copy(alpha = a), Offset(center.x, 0f), Offset(center.x, size.height), stroke)
        if (horizontal) drawLine((if (abs(pitch) <= tolerance) green else red).copy(alpha = a), Offset(0f, center.y), Offset(size.width, center.y), stroke)
        drawCircle(Color.White.copy(alpha = a), stroke * 1.8f, center)
    }
}
