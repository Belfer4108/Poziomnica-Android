package com.poziomnica

import com.poziomnica.domain.LevelReading
import com.poziomnica.domain.MeasurementMath
import com.poziomnica.sensors.SmoothingFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementMathTest {
    @Test fun degreesToPercentUsesTangent() {
        assertEquals(1.7455f, MeasurementMath.degreesToPercent(1f), 0.001f)
    }

    @Test fun degreesToMmPerMeterUsesTangent() {
        assertEquals(17.455f, MeasurementMath.degreesToMmPerMeter(1f), 0.01f)
    }

    @Test fun mmPerMeterToRatioIsProtectedAgainstZero() {
        assertTrue(MeasurementMath.mmPerMeterToRatio(0f).isInfinite())
        assertEquals(50f, MeasurementMath.mmPerMeterToRatio(20f), 0.001f)
    }

    @Test fun toleranceCheckUsesAbsoluteDistance() {
        assertTrue(MeasurementMath.isWithinTolerance(0.18f, 0f, 0.2f))
        assertTrue(!MeasurementMath.isWithinTolerance(0.21f, 0f, 0.2f))
    }

    @Test fun angleBetweenSurfacesIsAbsoluteAndClamped() {
        assertEquals(45f, MeasurementMath.angleBetweenSurfaces(-15f, 30f), 0.001f)
        assertEquals(180f, MeasurementMath.angleBetweenSurfaces(-100f, 120f), 0.001f)
    }

    @Test fun twoSidedCalibrationReturnsAverageError() {
        val correction = MeasurementMath.twoSidedCalibrationError(LevelReading(pitch = 0.4f, roll = 0.2f), LevelReading(pitch = -0.2f, roll = 0.0f))
        assertEquals(0.1f, correction.first, 0.001f)
        assertEquals(0.1f, correction.second, 0.001f)
    }

    @Test fun smoothingFilterMovesTowardNewReadingWithoutJumping() {
        val filter = SmoothingFilter(com.poziomnica.domain.SmoothingLevel.STANDARD)
        filter.filter(LevelReading(linearAngle = 0f))
        val out = filter.filter(LevelReading(linearAngle = 10f))
        assertTrue(out.linearAngle > 0f)
        assertTrue(out.linearAngle < 10f)
    }
}
