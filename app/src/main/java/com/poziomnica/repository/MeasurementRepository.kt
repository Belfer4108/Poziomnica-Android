package com.poziomnica.repository

import com.poziomnica.database.MeasurementDao
import com.poziomnica.database.MeasurementEntity
import com.poziomnica.domain.MeasurementDraft
import kotlinx.coroutines.flow.Flow

class MeasurementRepository(private val dao: MeasurementDao) {
    val measurements: Flow<List<MeasurementEntity>> = dao.observeAll()

    fun observe(id: Long): Flow<MeasurementEntity?> = dao.observeById(id)

    suspend fun save(draft: MeasurementDraft): Long = dao.insert(
        MeasurementEntity(
            name = draft.name,
            type = draft.type.name,
            mainValue = draft.mainValue,
            xValue = draft.xValue,
            yValue = draft.yValue,
            unit = draft.unit.name,
            tolerance = draft.tolerance,
            targetReached = draft.targetReached,
            createdAt = System.currentTimeMillis(),
            note = draft.note,
            photoUri = draft.photoUri,
            calibrationProfile = draft.calibrationProfile,
            targetValue = draft.targetValue
        )
    )

    suspend fun update(entity: MeasurementEntity) = dao.update(entity)
    suspend fun delete(entity: MeasurementEntity) = dao.delete(entity)
    suspend fun deleteMany(ids: List<Long>) = dao.deleteByIds(ids)
    suspend fun duplicate(entity: MeasurementEntity) = dao.insert(entity.copy(id = 0, name = entity.name + " kopia", createdAt = System.currentTimeMillis()))
}
