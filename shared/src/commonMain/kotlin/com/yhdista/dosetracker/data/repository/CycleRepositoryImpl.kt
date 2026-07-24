package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.AppLogger
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.data.local.dao.CycleDao
import com.yhdista.dosetracker.data.mapper.toDomain
import com.yhdista.dosetracker.data.mapper.toEntity
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleWeek
import com.yhdista.dosetracker.domain.repository.CycleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class CycleRepositoryImpl(
    private val cycleDao: CycleDao
) : CycleRepository {

    override fun getActiveCycle(): Flow<Data<Cycle?>> {
        return cycleDao.getActiveCycleFlow()
            .map { entity -> Data.Success(entity?.toDomain()) as Data<Cycle?> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch active cycle", e)
                emit(Data.Error("Failed to fetch active cycle", e))
            }
    }

    override suspend fun getActiveCycleOnce(): Cycle? {
        return cycleDao.getActiveCycleOnce()?.toDomain()
    }

    override suspend fun getStandardCycle(): Cycle? {
        return cycleDao.getStandardCycle()?.toDomain()
    }

    override suspend fun getCycleById(id: Long): Cycle? {
        return cycleDao.getCycleById(id)?.toDomain()
    }

    override fun getCompletedCycles(): Flow<Data<List<Cycle>>> {
        return cycleDao.getCompletedCycles()
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Cycle>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch completed cycles", e)
                emit(Data.Error("Failed to fetch completed cycles", e))
            }
    }

    override suspend fun createCycle(cycle: Cycle): Data<Long> {
        return try {
            val weekCount = cycle.totalWeeks ?: 1
            val cycleId = cycleDao.insertCycleWithWeeks(cycle.toEntity(), weekCount)
            AppLogger.i("Database", "Created cycle: id=$cycleId, name='${cycle.name}', weeks=$weekCount")
            Data.Success(cycleId)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to create cycle '${cycle.name}'", e)
            Data.Error("Failed to create cycle", e)
        }
    }

    override suspend fun updateCycle(cycle: Cycle): Data<Unit> {
        return try {
            cycleDao.updateCycle(cycle.toEntity())
            AppLogger.i("Database", "Updated cycle: id=${cycle.id}, name='${cycle.name}', status=${cycle.status}")
            Data.Success(Unit)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to update cycle id=${cycle.id}", e)
            Data.Error("Failed to update cycle", e)
        }
    }

    override suspend fun completeAndActivateCycle(completed: Cycle, activated: Cycle?): Data<Unit> {
        return try {
            cycleDao.completeAndActivate(completed.toEntity(), activated?.toEntity())
            AppLogger.i("Database", "Completed cycle id=${completed.id}, activated cycle id=${activated?.id}")
            Data.Success(Unit)
        } catch (e: Exception) {
            AppLogger.e("Database", "Failed to complete cycle id=${completed.id}", e)
            Data.Error("Failed to complete cycle", e)
        }
    }

    override fun getWeeksForCycle(cycleId: Long): Flow<Data<List<CycleWeek>>> {
        return cycleDao.getWeeksForCycle(cycleId)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<CycleWeek>> }
            .onStart { emit(Data.Loading) }
            .catch { e ->
                AppLogger.e("Database", "Failed to fetch cycle weeks for cycleId=$cycleId", e)
                emit(Data.Error("Failed to fetch cycle weeks", e))
            }
    }

    override suspend fun getCycleWeek(cycleId: Long, weekIndex: Int): CycleWeek? {
        return cycleDao.getCycleWeek(cycleId, weekIndex)?.toDomain()
    }
}
