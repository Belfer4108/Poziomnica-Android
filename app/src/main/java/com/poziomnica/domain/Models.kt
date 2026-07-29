package com.poziomnica.domain

enum class MeasurementType(val label: String) {
    LINEAR_LEVEL("Poziomnica"),
    SURFACE_LEVEL("Poziomowanie powierzchni"),
    PLUMB("Pion"),
    SLOPE("Spadek"),
    PROTRACTOR("Kątomierz"),
    CAMERA("Aparat"),
    LIGHT("Luksomierz"),
    TAPE_MEASURE("Miarka AR")
}

data class LightReading(
    val lux: Float = 0f,
    val minLux: Float = 0f,
    val maxLux: Float = 0f,
    val averageLux: Float = 0f,
    val available: Boolean = true,
    val timestampMillis: Long = System.currentTimeMillis()
)

enum class AngleUnit(val label: String) {
    DEGREES("stopnie"),
    PERCENT("procent"),
    MM_PER_M("mm/m"),
    CM_PER_M("cm/m"),
    RATIO("1:X"),
    LUX("lx"),
    METERS("m"),
    CENTIMETERS("cm"),
    SQUARE_METERS("m²"),
    CUBIC_METERS("m³")
}

enum class SmoothingLevel(val label: String, val alpha: Float) {
    FAST("szybkie", 0.48f),
    STANDARD("standardowe", 0.28f),
    STABLE("stabilne", 0.16f),
    VERY_STABLE("bardzo stabilne", 0.08f)
}

enum class IndicatorStyle(val label: String) {
    REALISTIC("realistyczny"),
    MINIMAL("minimalistyczny")
}

enum class SoundMode(val label: String) {
    SINGLE("pojedynczy"),
    CONTINUOUS("stały"),
    PROXIMITY("zbliżeniowy")
}

enum class SlopeDirection(val label: String) {
    LEFT("w lewo"),
    RIGHT("w prawo"),
    FORWARD("do przodu"),
    BACKWARD("do tyłu")
}

data class LevelReading(
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val azimuth: Float = 0f,
    val linearAngle: Float = 0f,
    val supportEdge: String = "połóż telefon na krawędzi",
    val surfaceX: Float = 0f,
    val surfaceY: Float = 0f,
    val accuracyDegrees: Float = 0.2f,
    val sensorSummary: String = "Oczekiwanie na czujnik",
    val limitedAccuracy: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val totalSurfaceDeviation: Float
        get() = kotlin.math.hypot(surfaceX.toDouble(), surfaceY.toDouble()).toFloat()
}

data class CalibrationProfile(
    val id: String,
    val name: String,
    val offsetPitch: Float = 0f,
    val offsetRoll: Float = 0f,
    val longEdgeOffset: Float = 0f,
    val shortEdgeOffset: Float = 0f,
    val isDefault: Boolean = false,
    val isActive: Boolean = false,
    val lastCalibratedAt: Long = 0L
)

data class MeasurementDraft(
    val name: String,
    val type: MeasurementType,
    val mainValue: Float,
    val xValue: Float? = null,
    val yValue: Float? = null,
    val unit: AngleUnit = AngleUnit.DEGREES,
    val tolerance: Float,
    val targetReached: Boolean,
    val note: String = "",
    val photoUri: String? = null,
    val calibrationProfile: String = "",
    val targetValue: Float? = null
)
