package com.poziomnica.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val mainValue: Float,
    val xValue: Float?,
    val yValue: Float?,
    val unit: String,
    val tolerance: Float,
    val targetReached: Boolean,
    val createdAt: Long,
    val note: String,
    val photoUri: String?,
    val calibrationProfile: String,
    val targetValue: Float?
)
