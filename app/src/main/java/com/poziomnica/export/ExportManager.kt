package com.poziomnica.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.poziomnica.database.MeasurementEntity
import com.poziomnica.domain.AngleUnit
import com.poziomnica.domain.MeasurementMath
import com.poziomnica.domain.MeasurementType
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportManager(private val context: Context) {
    fun writeText(uri: Uri, measurements: List<MeasurementEntity>) {
        write(uri, measurements.joinToString("\n\n") { printable(it) })
    }

    fun writeCsv(uri: Uri, measurements: List<MeasurementEntity>) {
        val header = "name,type,value,x,y,unit,tolerance,targetReached,createdAt,note\n"
        val rows = measurements.joinToString("\n") { m ->
            listOf(m.name, measurementTypeLabel(m.type), m.mainValue, m.xValue ?: "", m.yValue ?: "", unitLabel(m.unit), m.tolerance, m.targetReached, m.createdAt, m.note).joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" }
        }
        write(uri, header + rows)
    }

    fun writePdf(uri: Uri, measurements: List<MeasurementEntity>) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 29, 32); textSize = 14f }
        measurements.forEachIndexed { index, m ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
            val c = page.canvas
            paint.textSize = 24f
            paint.isFakeBoldText = true
            c.drawText("Poziomnica", 48f, 62f, paint)
            paint.textSize = 16f
            paint.isFakeBoldText = false
            printable(m).lines().forEachIndexed { i, line -> c.drawText(line, 48f, 105f + i * 24f, paint) }
            m.photoUri?.let { rawUri ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(rawUri))?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()?.let { bitmap ->
                    val top = 430f
                    val maxW = 499f
                    val maxH = 330f
                    val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    c.drawBitmap(bitmap, null, RectF(48f, top, 48f + w, top + h), null)
                }
            }
            document.finishPage(page)
        }
        context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) } ?: throw IOException("Nie można otworzyć pliku PDF")
        document.close()
    }

    fun writeBitmap(uri: Uri, measurement: MeasurementEntity) {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(240, 244, 242))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 28, 32); textSize = 46f; isFakeBoldText = true }
        canvas.drawText("Poziomnica", 64f, 90f, paint)
        paint.textSize = 34f
        paint.isFakeBoldText = false
        printable(measurement).lines().forEachIndexed { i, line -> canvas.drawText(line, 64f, 160f + i * 52f, paint) }
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 96, it) } ?: throw IOException("Nie można zapisać obrazu")
    }

    private fun write(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IOException("Nie można zapisać pliku")
    }

    private fun printable(m: MeasurementEntity): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(m.createdAt))
        return """
            Nazwa pomiaru: ${m.name}
            Typ: ${measurementTypeLabel(m.type)}
            Data i godzina: $date
            Wynik: ${formatMeasurementValue(m)}
            Oś X: ${m.xValue?.let { "%.3f".format(it) } ?: "-"}
            Oś Y: ${m.yValue?.let { "%.3f".format(it) } ?: "-"}
            Tolerancja: ±${m.tolerance}°
            Wartość docelowa osiągnięta: ${if (m.targetReached) "tak" else "nie"}
            Kalibracja: ${m.calibrationProfile.ifBlank { "Fabryczna" }}
            Notatka: ${m.note.ifBlank { "-" }}
            Zdjęcie: ${m.photoUri ?: "-"}
        """.trimIndent()
    }

    private fun measurementTypeLabel(type: String): String =
        runCatching { MeasurementType.valueOf(type).label }.getOrDefault(type)

    private fun unitLabel(unit: String): String =
        runCatching { AngleUnit.valueOf(unit).label }.getOrDefault(unit)

    private fun formatMeasurementValue(m: MeasurementEntity): String {
        val unit = runCatching { AngleUnit.valueOf(m.unit) }.getOrNull()
        return when (unit) {
            AngleUnit.LUX -> "%.1f lx".format(m.mainValue)
            AngleUnit.DEGREES, AngleUnit.PERCENT, AngleUnit.MM_PER_M, AngleUnit.CM_PER_M, AngleUnit.RATIO -> MeasurementMath.formatByUnit(m.mainValue, unit)
            null -> "%.3f %s".format(m.mainValue, m.unit)
        }
    }
}
