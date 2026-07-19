# Medication Cycles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user define a multi-week "Cyklus" (name, week count, per-week medication schedules), show it as a dashboard on the Today screen (progress, elapsed/remaining), tag today's doses as cycle-vs-other, and automatically transition through Standardní cyklus / Post-cyklus / no-cycle when a timed cycle finishes.

**Architecture:** Two new Room entities (`CycleEntity`, `CycleWeekEntity`) plus a nullable `cycleWeekId` on the existing `ReminderScheduleEntity` and a nullable `cycleId` on `DoseEntity`. `DoseGenerator` gains a `CycleLifecycleManager` collaborator that advances cycle state before generating doses, and its matching logic is extended to only fire cycle-linked schedules during their own cycle week. Three new screens (`CreateCycleScreen`, `CycleWeekEditorScreen`, `CycleHistoryScreen`) reuse the existing `ScheduleDialog` (extracted from `MedicationDetailScreen.kt` into a shared file) for per-medication schedule entry.

**Tech Stack:** Kotlin Multiplatform (`:shared` commonMain/androidMain), Room (KMP), Koin, Compose Multiplatform + Material3, kotlinx-datetime.

## Global Constraints

- No new Gradle dependencies.
- Tests follow the codebase's existing convention exactly: JUnit4 (`org.junit.Test`/`Before`/`After`), `mockito-kotlin`, `kotlinx-coroutines-test` `StandardTestDispatcher`. Not JUnit5, not Turbine, not Compose UI testing (none exists in this codebase).
- Not every new ViewModel gets a dedicated test file. This codebase already only tests its real business-logic surfaces (`DoseGenerator`, `WeekDays`, `TodayViewModel`, `ReportViewModel`, `MedicationReportViewModel`) — plain CRUD ViewModels (`MedicationDetailViewModel`, `HistoryViewModel`, `MedicationCatalogViewModel`, `ConfirmDoseViewModel`, `AddDoseViewModel`) have none. New CRUD-shaped ViewModels here (`CycleWeekEditorViewModel`, `CycleHistoryViewModel`) follow that same precedent — verified by compile check + the manual pass in Task 10. `CreateCycleViewModel.save()` gets a dedicated test because its branching (no-active-cycle / edit-STANDARD-in-place / attach-POST) is real business logic, not passthrough CRUD, matching the bar set by `CycleLifecycleManager` and `DoseGenerator`.
- No Room migration is written. `fallbackToDestructiveMigration(dropAllTables = true)` stays as-is; bumping `AppDatabase.version` (4 → 5) wipes local data on upgrade, same as every prior schema change in this project. This was explicitly requested by the user.
- `CycleStatus` has **three** states — `DRAFT`, `ACTIVE`, `COMPLETED` — not just the two implied by the design spec's prose. `DRAFT` is needed for a `STANDARD` template that's been configured but never activated yet, and for a `POST` cycle queued as another cycle's `nextCycleId` but not yet running — neither belongs in `getCompletedCycles()` (a real finished run) nor `getActiveCycle()`. This is a planning-time refinement of the approved design, not a behavior change the user needs to re-approve.
- New Cycle-related UI copy is written in **Czech**, matching the feature's own Czech domain terms ("Cyklus" / "Standardní cyklus" / "Post-cyklus") as agreed in the design spec's Sekce 3. Every other existing screen keeps its current English copy unchanged — this is a deliberate, scoped exception, not a wholesale localization.
- Build verification command: `./gradlew :app:assembleDebug`
- Test verification command: `./gradlew :shared:testAndroidHostTest --tests "<FQCN>"`
- Design reference: `docs/superpowers/specs/2026-07-19-medication-cycles-design.md`

---

### Task 1: Cycle schema layer (entities, DAO, converters, `AppDatabase`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/Cycle.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/CycleEntity.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/CycleWeekEntity.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/CycleDao.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/Dose.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/ReminderSchedule.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/DoseEntity.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/ReminderScheduleEntity.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/ReminderScheduleDao.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/Converters.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/AppDatabase.kt`

**Interfaces:**
- Produces: `Cycle`, `CycleWeek`, `CycleType`, `CycleStatus`, `CycleCompleteAction` domain types; `CycleEntity`, `CycleWeekEntity` Room entities; `CycleDao` with `getActiveCycleFlow(): Flow<CycleEntity?>`, `getActiveCycleOnce(): CycleEntity?`, `getStandardCycle(): CycleEntity?`, `getCompletedCycles(): Flow<List<CycleEntity>>`, `getCycleById(id: Long): CycleEntity?`, `insertCycle(cycle: CycleEntity): Long`, `updateCycle(cycle: CycleEntity)`, `getWeeksForCycle(cycleId: Long): Flow<List<CycleWeekEntity>>`, `getCycleWeek(cycleId: Long, weekIndex: Int): CycleWeekEntity?`, `insertCycleWeek(week: CycleWeekEntity): Long`; `ReminderScheduleEntity.cycleWeekId: Long?`, `ReminderSchedule.cycleWeekId: Long?`, `DoseEntity.cycleId: Long?`, `Dose.cycleId: Long?`; `ReminderScheduleDao.getSchedulesForCycleWeek(cycleWeekId: Long): Flow<List<ReminderScheduleEntity>>`. Consumed by Task 2's mappers/repository.

- [ ] **Step 1: Create the `Cycle` domain model**

```kotlin
package com.yhdista.dosetracker.domain.model

import kotlinx.datetime.LocalDate

enum class CycleType { NORMAL, STANDARD, POST }

enum class CycleStatus { DRAFT, ACTIVE, COMPLETED }

enum class CycleCompleteAction { TO_STANDARD, TO_POST, TO_NONE }

data class Cycle(
    val id: Long = 0,
    val name: String,
    val type: CycleType,
    val totalWeeks: Int? = null,
    val startDate: LocalDate,
    val status: CycleStatus,
    val onCompleteAction: CycleCompleteAction,
    val nextCycleId: Long? = null
)

data class CycleWeek(
    val id: Long = 0,
    val cycleId: Long,
    val weekIndex: Int
)
```

- [ ] **Step 2: Add `cycleId`/`cycleWeekId` to the existing domain models**

In `Dose.kt`, replace the `data class Dose` block with:

```kotlin
data class Dose(
    val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long? = null,
    val cycleId: Long? = null,
    val medicationName: String = "",
    val timestamp: Instant,
    val amount: Double? = null,
    val unit: String? = null,
    val status: DoseStatus = DoseStatus.PENDING
)
```

In `ReminderSchedule.kt`, replace the `data class ReminderSchedule` block with:

```kotlin
data class ReminderSchedule(
    val id: Long = 0,
    val medicationId: Long,
    val minutesOfDay: Int, // 0..1439, local time of day
    val daysOfWeek: Int,   // bitmask, see WeekDays
    val enabled: Boolean = true,
    val scheduleType: String = "WEEKDAYS",
    val intervalDays: Int = 1,
    val startDate: LocalDate? = null,
    val timeType: String = "EXACT",
    val dayPeriod: String? = null,
    val cycleWeekId: Long? = null
)
```

- [ ] **Step 3: Create the `CycleEntity` and `CycleWeekEntity` Room entities**

```kotlin
// CycleEntity.kt
package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: CycleType,
    val totalWeeks: Int?,
    val startDate: String,
    val status: CycleStatus,
    val onCompleteAction: CycleCompleteAction,
    val nextCycleId: Long?
)
```

```kotlin
// CycleWeekEntity.kt
package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cycle_weeks",
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cycleId"]), Index(value = ["cycleId", "weekIndex"], unique = true)]
)
data class CycleWeekEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cycleId: Long,
    val weekIndex: Int
)
```

- [ ] **Step 4: Add `cycleId`/`cycleWeekId` to the existing Room entities**

Replace the whole `DoseEntity.kt` with:

```kotlin
package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yhdista.dosetracker.domain.model.DoseStatus
import kotlinx.datetime.Instant

@Entity(
    tableName = "doses",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId"]), Index(value = ["scheduleId", "timestamp"], unique = true)]
)
data class DoseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    // No FK to reminder_schedules: deleting a schedule must not cascade-delete dose history.
    val scheduleId: Long?,
    val timestamp: Instant,
    val amount: Double?,
    val unit: String?,
    val status: DoseStatus,
    // No FK to cycles either, for the same reason: dose history must survive cycle deletion.
    val cycleId: Long? = null
)
```

Replace the whole `ReminderScheduleEntity.kt` with:

```kotlin
package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CycleWeekEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleWeekId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId"]), Index(value = ["cycleWeekId"])]
)
data class ReminderScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val minutesOfDay: Int,
    val daysOfWeek: Int,
    val enabled: Boolean = true,
    val scheduleType: String = "WEEKDAYS",
    val intervalDays: Int = 1,
    val startDate: String? = null,
    val timeType: String = "EXACT",
    val dayPeriod: String? = null,
    val cycleWeekId: Long? = null
)
```

- [ ] **Step 5: Create `CycleDao`**

```kotlin
package com.yhdista.dosetracker.data.local.dao

import androidx.room.*
import com.yhdista.dosetracker.data.local.entity.CycleEntity
import com.yhdista.dosetracker.data.local.entity.CycleWeekEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycles WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveCycleFlow(): Flow<CycleEntity?>

    @Query("SELECT * FROM cycles WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveCycleOnce(): CycleEntity?

    @Query("SELECT * FROM cycles WHERE type = 'STANDARD' LIMIT 1")
    suspend fun getStandardCycle(): CycleEntity?

    @Query("SELECT * FROM cycles WHERE status = 'COMPLETED' ORDER BY startDate DESC")
    fun getCompletedCycles(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles WHERE id = :id")
    suspend fun getCycleById(id: Long): CycleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: CycleEntity): Long

    @Update
    suspend fun updateCycle(cycle: CycleEntity)

    @Query("SELECT * FROM cycle_weeks WHERE cycleId = :cycleId ORDER BY weekIndex ASC")
    fun getWeeksForCycle(cycleId: Long): Flow<List<CycleWeekEntity>>

    @Query("SELECT * FROM cycle_weeks WHERE cycleId = :cycleId AND weekIndex = :weekIndex LIMIT 1")
    suspend fun getCycleWeek(cycleId: Long, weekIndex: Int): CycleWeekEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycleWeek(week: CycleWeekEntity): Long
}
```

- [ ] **Step 6: Add the cycle-week schedule query to `ReminderScheduleDao`**

Add this method inside the existing `ReminderScheduleDao` interface (after `getSchedulesForMedication`):

```kotlin
    @Query("SELECT * FROM reminder_schedules WHERE cycleWeekId = :cycleWeekId")
    fun getSchedulesForCycleWeek(cycleWeekId: Long): Flow<List<ReminderScheduleEntity>>
```

- [ ] **Step 7: Add cycle enum `TypeConverter`s**

Replace the whole `Converters.kt` with:

```kotlin
package com.yhdista.dosetracker.data.local

import androidx.room.TypeConverter
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.DoseStatus
import kotlinx.datetime.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilliseconds()
    }

    @TypeConverter
    fun fromDoseStatus(status: DoseStatus): String {
        return status.name
    }

    @TypeConverter
    fun toDoseStatus(value: String): DoseStatus {
        return DoseStatus.valueOf(value)
    }

    @TypeConverter
    fun fromCycleType(value: CycleType): String = value.name

    @TypeConverter
    fun toCycleType(value: String): CycleType = CycleType.valueOf(value)

    @TypeConverter
    fun fromCycleStatus(value: CycleStatus): String = value.name

    @TypeConverter
    fun toCycleStatus(value: String): CycleStatus = CycleStatus.valueOf(value)

    @TypeConverter
    fun fromCycleCompleteAction(value: CycleCompleteAction): String = value.name

    @TypeConverter
    fun toCycleCompleteAction(value: String): CycleCompleteAction = CycleCompleteAction.valueOf(value)
}
```

- [ ] **Step 8: Wire the new entities/DAO into `AppDatabase` and bump the version**

Replace the imports block and the `@Database`/class declaration at the top of `AppDatabase.kt` (everything from `package` through `abstract fun periodTimeDao(): PeriodTimeDao`) with:

```kotlin
package com.yhdista.dosetracker.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.yhdista.dosetracker.data.local.dao.CycleDao
import com.yhdista.dosetracker.data.local.dao.DoseDao
import com.yhdista.dosetracker.data.local.dao.MedicationDao
import com.yhdista.dosetracker.data.local.dao.ReminderScheduleDao
import com.yhdista.dosetracker.data.local.dao.PeriodTimeDao
import com.yhdista.dosetracker.data.local.entity.CycleEntity
import com.yhdista.dosetracker.data.local.entity.CycleWeekEntity
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import com.yhdista.dosetracker.data.local.entity.MedicationEntity
import com.yhdista.dosetracker.data.local.entity.ReminderScheduleEntity
import com.yhdista.dosetracker.data.local.entity.PeriodTimeEntity

@Database(
    entities = [
        MedicationEntity::class, DoseEntity::class, ReminderScheduleEntity::class,
        PeriodTimeEntity::class, CycleEntity::class, CycleWeekEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseDao(): DoseDao
    abstract fun reminderScheduleDao(): ReminderScheduleDao
    abstract fun periodTimeDao(): PeriodTimeDao
    abstract fun cycleDao(): CycleDao
```

Leave the `companion object { ... }` block (seed data) and the `expect object AppDatabaseConstructor` at the end of the file untouched.

- [ ] **Step 9: Verify the project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/Cycle.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/Dose.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/ReminderSchedule.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/CycleEntity.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/CycleWeekEntity.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/DoseEntity.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/ReminderScheduleEntity.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/CycleDao.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/ReminderScheduleDao.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/Converters.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/AppDatabase.kt
git commit -m "feat: add cycle schema (entities, DAO, converters, db wiring)"
```

---

### Task 2: Cycle data layer (mappers, repository, DI)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/CycleMapper.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/ReminderScheduleMapper.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/DoseMapper.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt`
- Modify: `shared/src/androidMain/kotlin/com/yhdista/dosetracker/di/DataModule.kt`

**Interfaces:**
- Consumes: Task 1's `CycleEntity`, `CycleWeekEntity`, `CycleDao`, `Cycle`, `CycleWeek`, `ReminderScheduleEntity.cycleWeekId`, `DoseEntity.cycleId`.
- Produces: `MedicationRepository.getActiveCycle(): Flow<Data<Cycle?>>`, `getActiveCycleOnce(): Cycle?`, `getStandardCycle(): Cycle?`, `getCycleById(id: Long): Cycle?`, `getCompletedCycles(): Flow<Data<List<Cycle>>>`, `createCycle(cycle: Cycle): Data<Long>`, `updateCycle(cycle: Cycle): Data<Unit>`, `getWeeksForCycle(cycleId: Long): Flow<Data<List<CycleWeek>>>`, `getCycleWeek(cycleId: Long, weekIndex: Int): CycleWeek?`, `getSchedulesForCycleWeek(cycleWeekId: Long): Flow<Data<List<ReminderSchedule>>>` — consumed by Task 3–8.

- [ ] **Step 1: Create `CycleMapper.kt`**

```kotlin
package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.entity.CycleEntity
import com.yhdista.dosetracker.data.local.entity.CycleWeekEntity
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleWeek
import kotlinx.datetime.LocalDate

fun CycleEntity.toDomain(): Cycle = Cycle(
    id = id,
    name = name,
    type = type,
    totalWeeks = totalWeeks,
    startDate = LocalDate.parse(startDate),
    status = status,
    onCompleteAction = onCompleteAction,
    nextCycleId = nextCycleId
)

fun Cycle.toEntity(): CycleEntity = CycleEntity(
    id = id,
    name = name,
    type = type,
    totalWeeks = totalWeeks,
    startDate = startDate.toString(),
    status = status,
    onCompleteAction = onCompleteAction,
    nextCycleId = nextCycleId
)

fun CycleWeekEntity.toDomain(): CycleWeek = CycleWeek(id = id, cycleId = cycleId, weekIndex = weekIndex)

fun CycleWeek.toEntity(): CycleWeekEntity = CycleWeekEntity(id = id, cycleId = cycleId, weekIndex = weekIndex)
```

- [ ] **Step 2: Carry `cycleWeekId`/`cycleId` through the existing mappers**

Replace the whole `ReminderScheduleMapper.kt` with:

```kotlin
package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.entity.ReminderScheduleEntity
import com.yhdista.dosetracker.domain.model.ReminderSchedule

import kotlinx.datetime.LocalDate

fun ReminderScheduleEntity.toDomain(): ReminderSchedule = ReminderSchedule(
    id = id,
    medicationId = medicationId,
    minutesOfDay = minutesOfDay,
    daysOfWeek = daysOfWeek,
    enabled = enabled,
    scheduleType = scheduleType,
    intervalDays = intervalDays,
    startDate = startDate?.let { LocalDate.parse(it) },
    timeType = timeType,
    dayPeriod = dayPeriod,
    cycleWeekId = cycleWeekId
)

fun ReminderSchedule.toEntity(): ReminderScheduleEntity = ReminderScheduleEntity(
    id = id,
    medicationId = medicationId,
    minutesOfDay = minutesOfDay,
    daysOfWeek = daysOfWeek,
    enabled = enabled,
    scheduleType = scheduleType,
    intervalDays = intervalDays,
    startDate = startDate?.toString(),
    timeType = timeType,
    dayPeriod = dayPeriod,
    cycleWeekId = cycleWeekId
)
```

Replace the whole `DoseMapper.kt` with:

```kotlin
package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.dao.DoseWithMedication
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import com.yhdista.dosetracker.domain.model.Dose

fun DoseEntity.toDomain(medicationName: String = ""): Dose {
    return Dose(
        id = id,
        medicationId = medicationId,
        scheduleId = scheduleId,
        cycleId = cycleId,
        medicationName = medicationName,
        timestamp = timestamp,
        amount = amount,
        unit = unit,
        status = status
    )
}

fun DoseWithMedication.toDomain(): Dose {
    return dose.toDomain(medicationName)
}

fun Dose.toEntity(): DoseEntity {
    return DoseEntity(
        id = id,
        medicationId = medicationId,
        scheduleId = scheduleId,
        cycleId = cycleId,
        timestamp = timestamp,
        amount = amount,
        unit = unit,
        status = status
    )
}
```

- [ ] **Step 3: Add cycle methods to `MedicationRepository`**

Replace the whole `MedicationRepository.kt` with:

```kotlin
package com.yhdista.dosetracker.domain.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleWeek
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

interface MedicationRepository {
    fun getMedications(): Flow<Data<List<Medication>>>
    fun getMedicationById(id: Long): Flow<Data<Medication>>
    suspend fun getMedicationOnce(id: Long): Medication?
    suspend fun insertMedication(medication: Medication): Data<Long>
    suspend fun updateMedication(medication: Medication): Data<Unit>
    suspend fun deleteMedication(medication: Medication): Data<Unit>
    fun searchMedications(query: String): Flow<Data<List<Medication>>>

    fun getDosesForMedication(medicationId: Long): Flow<Data<List<Dose>>>
    fun getDosesForDate(date: LocalDate): Flow<Data<List<Dose>>>
    fun getDosesInWeek(weekStart: LocalDate): Flow<Data<List<Dose>>>
    fun getDosesForMedicationInRange(medicationId: Long, start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>>
    fun getAllDoses(): Flow<Data<List<Dose>>>
    suspend fun getDoseOnce(id: Long): Dose?
    fun getDoseById(id: Long): Flow<Data<Dose>>
    suspend fun getDoseForSchedule(scheduleId: Long, timestamp: Instant): Dose?
    suspend fun getDoseForScheduleOnDate(scheduleId: Long, date: LocalDate): Dose?
    suspend fun insertDose(dose: Dose): Data<Long>
    suspend fun updateDose(dose: Dose): Data<Unit>

    fun getSchedulesForMedication(medicationId: Long): Flow<Data<List<ReminderSchedule>>>
    fun getSchedulesForCycleWeek(cycleWeekId: Long): Flow<Data<List<ReminderSchedule>>>
    suspend fun getEnabledSchedules(): Data<List<ReminderSchedule>>
    fun getAllSchedules(): Flow<Data<List<ReminderSchedule>>>
    suspend fun insertSchedule(schedule: ReminderSchedule): Data<Long>
    suspend fun updateSchedule(schedule: ReminderSchedule): Data<Unit>
    suspend fun deleteSchedule(schedule: ReminderSchedule): Data<Unit>

    fun getPeriodTimes(): Flow<Data<Map<String, Int>>>
    suspend fun getPeriodTimesOnce(): Map<String, Int>
    suspend fun updatePeriodTime(period: String, minutesOfDay: Int): Data<Unit>

    fun getActiveCycle(): Flow<Data<Cycle?>>
    suspend fun getActiveCycleOnce(): Cycle?
    suspend fun getStandardCycle(): Cycle?
    suspend fun getCycleById(id: Long): Cycle?
    fun getCompletedCycles(): Flow<Data<List<Cycle>>>
    suspend fun createCycle(cycle: Cycle): Data<Long>
    suspend fun updateCycle(cycle: Cycle): Data<Unit>
    fun getWeeksForCycle(cycleId: Long): Flow<Data<List<CycleWeek>>>
    suspend fun getCycleWeek(cycleId: Long, weekIndex: Int): CycleWeek?
}
```

- [ ] **Step 4: Implement the cycle methods in `MedicationRepositoryImpl`**

Replace the whole `MedicationRepositoryImpl.kt` with:

```kotlin
package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.data.local.dao.CycleDao
import com.yhdista.dosetracker.data.local.dao.DoseDao
import com.yhdista.dosetracker.data.local.dao.MedicationDao
import com.yhdista.dosetracker.data.local.dao.ReminderScheduleDao
import com.yhdista.dosetracker.data.local.entity.CycleWeekEntity
import com.yhdista.dosetracker.data.mapper.toDomain
import com.yhdista.dosetracker.data.mapper.toEntity
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleWeek
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

import com.yhdista.dosetracker.data.local.dao.PeriodTimeDao
import com.yhdista.dosetracker.data.local.entity.PeriodTimeEntity

class MedicationRepositoryImpl(
    private val medicationDao: MedicationDao,
    private val doseDao: DoseDao,
    private val scheduleDao: ReminderScheduleDao,
    private val periodTimeDao: PeriodTimeDao,
    private val cycleDao: CycleDao
) : MedicationRepository {

    override fun getMedications(): Flow<Data<List<Medication>>> {
        return medicationDao.getAllMedications()
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Medication>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch medications", e)) }
    }

    override fun getMedicationById(id: Long): Flow<Data<Medication>> {
        return medicationDao.getMedicationById(id)
            .map { entity ->
                if (entity != null) Data.Success(entity.toDomain()) as Data<Medication>
                else Data.Error("Medication not found")
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch medication", e)) }
    }

    override suspend fun getMedicationOnce(id: Long): Medication? {
        return medicationDao.getMedicationById(id).first()?.toDomain()
    }

    override suspend fun insertMedication(medication: Medication): Data<Long> {
        return try {
            Data.Success(medicationDao.insertMedication(medication.toEntity()))
        } catch (e: Exception) {
            Data.Error("Failed to insert medication", e)
        }
    }

    override suspend fun updateMedication(medication: Medication): Data<Unit> {
        return try {
            medicationDao.updateMedication(medication.toEntity())
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to update medication", e)
        }
    }

    override suspend fun deleteMedication(medication: Medication): Data<Unit> {
        return try {
            medicationDao.deleteMedication(medication.toEntity())
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to delete medication", e)
        }
    }

    override fun searchMedications(query: String): Flow<Data<List<Medication>>> {
        return medicationDao.searchMedications(query)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Medication>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to search medications", e)) }
    }

    override fun getDosesForMedication(medicationId: Long): Flow<Data<List<Dose>>> {
        return doseDao.getDosesForMedication(medicationId)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch doses", e)) }
    }

    override fun getDosesForDate(date: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startOfDay = date.atStartOfDayIn(zone).toEpochMilliseconds()
        val endOfDay = date.atTime(LocalTime(23, 59, 59, 999_000_000)).toInstant(zone).toEpochMilliseconds()

        return doseDao.getDosesInTimeRange(startOfDay, endOfDay)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch doses for date", e)) }
    }

    override fun getDosesInWeek(weekStart: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startOfWeek = weekStart.atStartOfDayIn(zone).toEpochMilliseconds()
        val endOfWeek = weekStart.plus(7, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() - 1

        return doseDao.getDosesInTimeRange(startOfWeek, endOfWeek)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch doses for week", e)) }
    }

    override fun getDosesForMedicationInRange(medicationId: Long, start: LocalDate, endExclusive: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startMillis = start.atStartOfDayIn(zone).toEpochMilliseconds()
        val endMillis = endExclusive.atStartOfDayIn(zone).toEpochMilliseconds() - 1

        return doseDao.getDosesForMedicationInTimeRange(medicationId, startMillis, endMillis)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch doses for medication in range", e)) }
    }

    override fun getAllDoses(): Flow<Data<List<Dose>>> {
        return doseDao.getAllDoses()
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch all doses", e)) }
    }

    override suspend fun getDoseOnce(id: Long): Dose? {
        return doseDao.getDoseWithMedicationById(id)?.toDomain()
    }

    override fun getDoseById(id: Long): Flow<Data<Dose>> {
        return doseDao.getDoseWithMedicationByIdFlow(id)
            .map { entity ->
                if (entity != null) Data.Success(entity.toDomain()) as Data<Dose>
                else Data.Error("Dose not found")
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch dose", e)) }
    }

    override suspend fun getDoseForSchedule(scheduleId: Long, timestamp: Instant): Dose? {
        return doseDao.getDoseForSchedule(scheduleId, timestamp)?.toDomain()
    }

    override suspend fun insertDose(dose: Dose): Data<Long> {
        return try {
            Data.Success(doseDao.insertDose(dose.toEntity()))
        } catch (e: Exception) {
            Data.Error("Failed to insert dose", e)
        }
    }

    override suspend fun updateDose(dose: Dose): Data<Unit> {
        return try {
            doseDao.updateDose(dose.toEntity())
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to update dose", e)
        }
    }

    override fun getSchedulesForMedication(medicationId: Long): Flow<Data<List<ReminderSchedule>>> {
        return scheduleDao.getSchedulesForMedication(medicationId)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<ReminderSchedule>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch schedules", e)) }
    }

    override fun getSchedulesForCycleWeek(cycleWeekId: Long): Flow<Data<List<ReminderSchedule>>> {
        return scheduleDao.getSchedulesForCycleWeek(cycleWeekId)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<ReminderSchedule>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch schedules for cycle week", e)) }
    }

    override suspend fun getEnabledSchedules(): Data<List<ReminderSchedule>> {
        return try {
            Data.Success(scheduleDao.getAllEnabledSchedules().map { it.toDomain() })
        } catch (e: Exception) {
            Data.Error("Failed to fetch schedules", e)
        }
    }

    override fun getAllSchedules(): Flow<Data<List<ReminderSchedule>>> {
        return scheduleDao.getAllSchedulesFlow()
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<ReminderSchedule>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch all schedules", e)) }
    }

    override suspend fun insertSchedule(schedule: ReminderSchedule): Data<Long> {
        return try {
            Data.Success(scheduleDao.insertSchedule(schedule.toEntity()))
        } catch (e: Exception) {
            Data.Error("Failed to insert schedule", e)
        }
    }

    override suspend fun deleteSchedule(schedule: ReminderSchedule): Data<Unit> {
        return try {
            scheduleDao.deleteSchedule(schedule.toEntity())
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to delete schedule", e)
        }
    }

    override suspend fun getDoseForScheduleOnDate(scheduleId: Long, date: LocalDate): Dose? {
        val zone = TimeZone.currentSystemDefault()
        val startOfDay = date.atStartOfDayIn(zone)
        val endOfDay = date.atTime(LocalTime(23, 59, 59, 999_000_000)).toInstant(zone)
        return doseDao.getDoseForScheduleOnDate(scheduleId, startOfDay, endOfDay)?.toDomain()
    }

    override suspend fun updateSchedule(schedule: ReminderSchedule): Data<Unit> {
        return try {
            scheduleDao.updateSchedule(schedule.toEntity())
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to update schedule", e)
        }
    }

    override fun getPeriodTimes(): Flow<Data<Map<String, Int>>> {
        return periodTimeDao.getAllPeriodTimesFlow()
            .map { entities ->
                val map = entities.associate { it.period to it.minutesOfDay }
                Data.Success(map) as Data<Map<String, Int>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch period times", e)) }
     }

     override suspend fun getPeriodTimesOnce(): Map<String, Int> {
         return periodTimeDao.getAllPeriodTimes().associate { it.period to it.minutesOfDay }
     }

     override suspend fun updatePeriodTime(period: String, minutesOfDay: Int): Data<Unit> {
         return try {
             periodTimeDao.insertPeriodTime(PeriodTimeEntity(period, minutesOfDay))
             Data.Success(Unit)
         } catch (e: Exception) {
             Data.Error("Failed to update period time", e)
         }
     }

    override fun getActiveCycle(): Flow<Data<Cycle?>> {
        return cycleDao.getActiveCycleFlow()
            .map { entity -> Data.Success(entity?.toDomain()) as Data<Cycle?> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch active cycle", e)) }
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
            .catch { e -> emit(Data.Error("Failed to fetch completed cycles", e)) }
    }

    override suspend fun createCycle(cycle: Cycle): Data<Long> {
        return try {
            val cycleId = cycleDao.insertCycle(cycle.toEntity())
            val weekCount = cycle.totalWeeks ?: 1
            for (weekIndex in 0 until weekCount) {
                cycleDao.insertCycleWeek(CycleWeekEntity(cycleId = cycleId, weekIndex = weekIndex))
            }
            Data.Success(cycleId)
        } catch (e: Exception) {
            Data.Error("Failed to create cycle", e)
        }
    }

    override suspend fun updateCycle(cycle: Cycle): Data<Unit> {
        return try {
            cycleDao.updateCycle(cycle.toEntity())
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to update cycle", e)
        }
    }

    override fun getWeeksForCycle(cycleId: Long): Flow<Data<List<CycleWeek>>> {
        return cycleDao.getWeeksForCycle(cycleId)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<CycleWeek>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch cycle weeks", e)) }
    }

    override suspend fun getCycleWeek(cycleId: Long, weekIndex: Int): CycleWeek? {
        return cycleDao.getCycleWeek(cycleId, weekIndex)?.toDomain()
    }
}
```

- [ ] **Step 5: Wire `CycleDao` into `dataModule`**

Replace the whole `DataModule.kt` with:

```kotlin
package com.yhdista.dosetracker.di

import android.content.Context
import com.yhdista.dosetracker.data.local.getDatabaseBuilder
import com.yhdista.dosetracker.data.local.getRoomDatabase
import com.yhdista.dosetracker.data.repository.MedicationRepositoryImpl
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import org.koin.dsl.module

val dataModule = module {
    single {
        getRoomDatabase(getDatabaseBuilder(get<Context>()))
    }
    single { get<com.yhdista.dosetracker.data.local.AppDatabase>().medicationDao() }
    single { get<com.yhdista.dosetracker.data.local.AppDatabase>().doseDao() }
    single { get<com.yhdista.dosetracker.data.local.AppDatabase>().reminderScheduleDao() }
    single { get<com.yhdista.dosetracker.data.local.AppDatabase>().periodTimeDao() }
    single { get<com.yhdista.dosetracker.data.local.AppDatabase>().cycleDao() }
    single<MedicationRepository> {
        MedicationRepositoryImpl(
            medicationDao = get(),
            doseDao = get(),
            scheduleDao = get(),
            periodTimeDao = get(),
            cycleDao = get()
        )
    }
    single { com.yhdista.dosetracker.reminder.CycleLifecycleManager(get()) }
    single { com.yhdista.dosetracker.reminder.DoseGenerator(get(), get(), get()) }
}
```

(The `CycleLifecycleManager(get())` reference above is resolved once Task 3 creates that class; this task only needs the DI wiring in place — Task 3's Step will confirm it compiles.)

- [ ] **Step 6: Verify the project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: This will FAIL at this point (`CycleLifecycleManager` doesn't exist yet, and `DoseGenerator`'s constructor doesn't take 3 args yet) — that's expected and resolved by Task 3/4. Skip this check here; the real compile check happens at the end of Task 4.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/CycleMapper.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/ReminderScheduleMapper.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/DoseMapper.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt \
        shared/src/androidMain/kotlin/com/yhdista/dosetracker/di/DataModule.kt
git commit -m "feat: add cycle mappers, repository methods, and DI wiring"
```

---

### Task 3: `CycleLifecycleManager`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/CycleLifecycleManager.kt`
- Test: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/reminder/CycleLifecycleManagerTest.kt`

**Interfaces:**
- Consumes: `MedicationRepository.getActiveCycleOnce()`, `getStandardCycle()`, `getCycleById(id)`, `updateCycle(cycle)` (Task 2).
- Produces: `CycleLifecycleManager(repository: MedicationRepository)` with `suspend fun advance(today: LocalDate)` — consumed by Task 4's `DoseGenerator`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CycleLifecycleManagerTest {

    private val repository = mock<MedicationRepository>()
    private val manager = CycleLifecycleManager(repository)
    private val testDispatcher = StandardTestDispatcher()

    private val today = LocalDate(2026, 7, 20)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `does nothing when there is no active cycle`() = runTest {
        whenever(repository.getActiveCycleOnce()).thenReturn(null)

        manager.advance(today)

        verify(repository, never()).updateCycle(any())
    }

    @Test
    fun `does nothing while a STANDARD cycle is active`() = runTest {
        val standard = Cycle(
            id = 1, name = "Standardni cyklus", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2020, 1, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(standard)

        manager.advance(today)

        verify(repository, never()).updateCycle(any())
    }

    @Test
    fun `does nothing while the active cycle still has weeks remaining`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = today, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)

        manager.advance(today)

        verify(repository, never()).updateCycle(any())
    }

    @Test
    fun `completes a NORMAL cycle and activates the STANDARD cycle`() = runTest {
        val startDate = LocalDate(2026, 6, 22) // 4 full weeks (28 days) before today
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = startDate, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        val standard = Cycle(
            id = 2, name = "Standardni cyklus", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2020, 1, 1), status = CycleStatus.DRAFT, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(repository.getStandardCycle()).thenReturn(standard)

        manager.advance(today)

        verify(repository).updateCycle(cycle.copy(status = CycleStatus.COMPLETED))
        verify(repository).updateCycle(standard.copy(status = CycleStatus.ACTIVE, startDate = today))
    }

    @Test
    fun `completes a cycle and activates its attached POST cycle`() = runTest {
        val startDate = LocalDate(2026, 6, 22)
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = startDate, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_POST,
            nextCycleId = 5
        )
        val post = Cycle(
            id = 5, name = "Post-cyklus", type = CycleType.POST, totalWeeks = 2,
            startDate = LocalDate(2020, 1, 1), status = CycleStatus.DRAFT, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(repository.getCycleById(5)).thenReturn(post)

        manager.advance(today)

        verify(repository).updateCycle(cycle.copy(status = CycleStatus.COMPLETED))
        verify(repository).updateCycle(post.copy(status = CycleStatus.ACTIVE, startDate = today))
    }

    @Test
    fun `completes a POST cycle into no active cycle`() = runTest {
        val startDate = LocalDate(2026, 7, 6) // 2 full weeks (14 days) before today
        val post = Cycle(
            id = 5, name = "Post-cyklus", type = CycleType.POST, totalWeeks = 2,
            startDate = startDate, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(post)

        manager.advance(today)

        verify(repository).updateCycle(post.copy(status = CycleStatus.COMPLETED))
        verify(repository, never()).getStandardCycle()
        verify(repository, never()).getCycleById(any())
    }

    @Test
    fun `recomputes correctly after several idle days past the cycle boundary`() = runTest {
        val startDate = LocalDate(2026, 6, 12) // 38 days before today, well past a 4-week (28 day) cycle
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = startDate, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        val standard = Cycle(
            id = 2, name = "Standardni cyklus", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2020, 1, 1), status = CycleStatus.DRAFT, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(repository.getStandardCycle()).thenReturn(standard)

        manager.advance(today)

        verify(repository).updateCycle(standard.copy(status = CycleStatus.ACTIVE, startDate = today))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.reminder.CycleLifecycleManagerTest"`
Expected: FAIL with a compile error (`CycleLifecycleManager` is unresolved)

- [ ] **Step 3: Implement `CycleLifecycleManager`**

```kotlin
package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

class CycleLifecycleManager(
    private val repository: MedicationRepository
) {
    suspend fun advance(today: LocalDate) {
        val cycle = repository.getActiveCycleOnce() ?: return
        if (cycle.type == CycleType.STANDARD) return
        val totalWeeks = cycle.totalWeeks ?: return
        val weekIndex = cycle.startDate.daysUntil(today) / 7
        if (weekIndex < totalWeeks) return

        repository.updateCycle(cycle.copy(status = CycleStatus.COMPLETED))

        when (cycle.onCompleteAction) {
            CycleCompleteAction.TO_STANDARD -> {
                val standard = repository.getStandardCycle() ?: return
                repository.updateCycle(standard.copy(status = CycleStatus.ACTIVE, startDate = today))
            }
            CycleCompleteAction.TO_POST -> {
                val nextId = cycle.nextCycleId ?: return
                val next = repository.getCycleById(nextId) ?: return
                repository.updateCycle(next.copy(status = CycleStatus.ACTIVE, startDate = today))
            }
            CycleCompleteAction.TO_NONE -> Unit
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.reminder.CycleLifecycleManagerTest"`
Expected: BUILD SUCCESSFUL, 7 tests passed

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/CycleLifecycleManager.kt \
        shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/reminder/CycleLifecycleManagerTest.kt
git commit -m "feat: add CycleLifecycleManager to advance cycle state day-by-day"
```

---

### Task 4: `DoseGenerator` cycle-aware dose generation

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseGenerator.kt`
- Modify: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/reminder/DoseGeneratorTest.kt`

**Interfaces:**
- Consumes: `CycleLifecycleManager.advance(date)` (Task 3), `MedicationRepository.getActiveCycleOnce()`, `getCycleWeek(cycleId, weekIndex)` (Task 2).
- Produces: `DoseGenerator(repository, scheduler, cycleLifecycleManager)` (constructor now takes 3 args) — consumed by Task 6/7's ViewModels and the `dataModule`/`ViewModelModule` wiring already done in Task 2 / to be done in Task 9.

- [ ] **Step 1: Extend `DoseGeneratorTest` with cycle-aware tests**

Add these imports to the top of `DoseGeneratorTest.kt` (alongside the existing ones):

```kotlin
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.CycleWeek
```

Replace this line:

```kotlin
    private val generator = DoseGenerator(repository, scheduler)
```

with:

```kotlin
    private val cycleLifecycleManager = mock<CycleLifecycleManager>()
    private val generator = DoseGenerator(repository, scheduler, cycleLifecycleManager)
```

Add these four tests at the end of the class, right before the final closing `}`:

```kotlin
    @Test
    fun `advances the cycle lifecycle before generating doses`() = runTest {
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(emptyList()))
        whenever(repository.getPeriodTimesOnce()).thenReturn(emptyMap())

        generator.runForDate(today)

        verify(cycleLifecycleManager).advance(today)
    }

    @Test
    fun `generates a dose for a cycle-linked schedule when its week is the active cycle's current week`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = today, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        val week0 = CycleWeek(id = 100, cycleId = 1, weekIndex = 0)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek)),
            cycleWeekId = 100
        )
        val expectedInstant = today.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(repository.getCycleWeek(1, 0)).thenReturn(week0)
        whenever(repository.getDoseForScheduleOnDate(1, today)).thenReturn(null)
        whenever(repository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG))
        whenever(repository.insertDose(any())).thenReturn(Data.Success(99L))

        generator.runForDate(today)

        verify(repository).insertDose(
            Dose(medicationId = 10, scheduleId = 1, cycleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        )
    }

    @Test
    fun `skips a cycle-linked schedule whose week is not the active cycle's current week`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = today, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        val week0 = CycleWeek(id = 100, cycleId = 1, weekIndex = 0)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek)),
            cycleWeekId = 200
        )
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(repository.getCycleWeek(1, 0)).thenReturn(week0)

        generator.runForDate(today)

        verify(repository, never()).insertDose(any())
    }

    @Test
    fun `still generates a standalone schedule while a cycle is active`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = today, status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        val week0 = CycleWeek(id = 100, cycleId = 1, weekIndex = 0)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek))
        )
        val expectedInstant = today.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getPeriodTimesOnce()).thenReturn(emptyMap())
        whenever(repository.getActiveCycleOnce()).thenReturn(cycle)
        whenever(repository.getCycleWeek(1, 0)).thenReturn(week0)
        whenever(repository.getDoseForScheduleOnDate(1, today)).thenReturn(null)
        whenever(repository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = MedicationUnit.MG))
        whenever(repository.insertDose(any())).thenReturn(Data.Success(99L))

        generator.runForDate(today)

        verify(repository).insertDose(
            Dose(medicationId = 10, scheduleId = 1, cycleId = null, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        )
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.reminder.DoseGeneratorTest"`
Expected: FAIL — compile error (`DoseGenerator` doesn't take a third constructor argument yet, `Dose`/`ReminderSchedule` have no `cycleId`/`cycleWeekId` — wait, those exist from Task 1/2; the failure here is purely the `DoseGenerator` constructor arity)

- [ ] **Step 3: Update `DoseGenerator`**

Replace the whole `DoseGenerator.kt` with:

```kotlin
package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.daysUntil
import kotlin.time.Duration.Companion.minutes

class DoseGenerator(
    private val repository: MedicationRepository,
    private val scheduler: DoseReminderScheduler,
    private val cycleLifecycleManager: CycleLifecycleManager
) {
    suspend fun runForToday() {
        runForDate(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }

    suspend fun runForDate(date: LocalDate) {
        cycleLifecycleManager.advance(date)

        val schedules = (repository.getEnabledSchedules() as? Data.Success)?.data ?: return
        val periodTimes = repository.getPeriodTimesOnce()
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val activeCycle = repository.getActiveCycleOnce()
        val activeCycleWeekId = resolveActiveCycleWeekId(activeCycle, date)

        for (schedule in schedules) {
            if (!matchesDate(schedule, date, activeCycleWeekId)) continue

            val minutes = if (schedule.timeType == "PERIOD") {
                periodTimes[schedule.dayPeriod] ?: schedule.minutesOfDay
            } else {
                schedule.minutesOfDay
            }

            val hour = minutes / 60
            val minute = minutes % 60
            val scheduledInstant = date.atTime(hour, minute).toInstant(zone)
            val cycleId = if (schedule.cycleWeekId != null) activeCycle?.id else null

            // Look up if there's any dose for this schedule on this date
            val existingDose = repository.getDoseForScheduleOnDate(schedule.id, date)

            if (existingDose == null) {
                val newDose = createDose(schedule.medicationId, schedule.id, scheduledInstant, cycleId)
                if (newDose != null && newDose.status == DoseStatus.PENDING && scheduledInstant > now) {
                    scheduler.scheduleReminder(newDose.id, scheduledInstant)
                    scheduler.scheduleMissedTimeout(
                        newDose.id,
                        scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes
                    )
                }
            } else {
                if (existingDose.status == DoseStatus.PENDING) {
                    if (existingDose.timestamp != scheduledInstant) {
                        // Cancel old alarms
                        scheduler.cancelReminder(existingDose.id)
                        scheduler.cancelMissedTimeout(existingDose.id)

                        // Update dose
                        val updatedDose = existingDose.copy(timestamp = scheduledInstant)
                        repository.updateDose(updatedDose)

                        // Schedule new alarms
                        if (scheduledInstant > now) {
                            scheduler.scheduleReminder(existingDose.id, scheduledInstant)
                            scheduler.scheduleMissedTimeout(
                                existingDose.id,
                                scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes
                            )
                        }
                    } else if (scheduledInstant > now) {
                        // Just make sure it is scheduled (for boot or re-arm)
                        scheduler.scheduleReminder(existingDose.id, scheduledInstant)
                        scheduler.scheduleMissedTimeout(
                            existingDose.id,
                            scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes
                        )
                    }
                }
            }
        }
    }

    private suspend fun resolveActiveCycleWeekId(activeCycle: Cycle?, date: LocalDate): Long? {
        if (activeCycle == null) return null
        val weekIndex = if (activeCycle.type == CycleType.STANDARD) {
            0
        } else {
            activeCycle.startDate.daysUntil(date) / 7
        }
        return repository.getCycleWeek(activeCycle.id, weekIndex)?.id
    }

    private fun matchesDate(schedule: ReminderSchedule, date: LocalDate, activeCycleWeekId: Long?): Boolean {
        if (schedule.cycleWeekId != null && schedule.cycleWeekId != activeCycleWeekId) return false
        return when (schedule.scheduleType) {
            "WEEKDAYS" -> WeekDays.contains(schedule.daysOfWeek, date.dayOfWeek)
            "INTERVAL" -> {
                val start = schedule.startDate ?: return false
                val days = start.daysUntil(date)
                days >= 0 && days % schedule.intervalDays == 0
            }
            else -> false
        }
    }

    private suspend fun createDose(medicationId: Long, scheduleId: Long, at: Instant, cycleId: Long?): Dose? {
        val medication = repository.getMedicationOnce(medicationId) ?: return null
        val dose = Dose(
            medicationId = medicationId,
            scheduleId = scheduleId,
            cycleId = cycleId,
            timestamp = at,
            amount = medication.dosage,
            unit = medication.unit.symbol,
            status = DoseStatus.PENDING
        )
        val id = (repository.insertDose(dose) as? Data.Success)?.data ?: return null
        return dose.copy(id = id)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.reminder.DoseGeneratorTest"`
Expected: BUILD SUCCESSFUL, 11 tests passed (7 existing + 4 new)

- [ ] **Step 5: Verify the whole project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (this also confirms Task 2's `dataModule` wiring from Step 5 now resolves correctly)

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseGenerator.kt \
        shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/reminder/DoseGeneratorTest.kt
git commit -m "feat: make DoseGenerator cycle-aware (week-scoped schedules, cycle lifecycle advance)"
```

---

### Task 5: Today dashboard + dose grouping

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayScreen.kt`
- Modify: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `MedicationRepository.getActiveCycle()` (Task 2).
- Produces: `TodayState.activeCycle: Data<Cycle?>`; `TodayScreen(viewModel, onNavigateToConfirm, onNavigateToCreateCycle, onNavigateToCycleHistory)` — consumed by Task 9's `DoseTrackerAppMain`.

- [ ] **Step 1: Extend `TodayViewModelTest`**

Add this import to the top of the file:

```kotlin
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import kotlinx.datetime.LocalDate
```

Replace the existing test with a version that also stubs `getActiveCycle`, and add a second test:

```kotlin
    @Test
    fun `uiState emits success when repository returns data`() = runTest {
        val doses = listOf(
            Dose(id = 1, medicationId = 1, timestamp = Clock.System.now(), status = DoseStatus.PENDING)
        )
        whenever(repository.getDosesForDate(org.mockito.kotlin.any())).thenReturn(flowOf(Data.Success(doses)))
        whenever(repository.getActiveCycle()).thenReturn(flowOf(Data.Success(null)))

        val viewModel = TodayViewModel(repository, SavedStateHandle())

        val job = launch { viewModel.uiState.collect {} }

        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assert(finalState.doses is Data.Success)
        assertEquals(doses, (finalState.doses as Data.Success).data)

        job.cancel()
    }

    @Test
    fun `uiState includes the active cycle when one is running`() = runTest {
        val cycle = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = LocalDate(2026, 7, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getDosesForDate(org.mockito.kotlin.any())).thenReturn(flowOf(Data.Success(emptyList())))
        whenever(repository.getActiveCycle()).thenReturn(flowOf(Data.Success(cycle)))

        val viewModel = TodayViewModel(repository, SavedStateHandle())
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assert(finalState.activeCycle is Data.Success)
        assertEquals(cycle, (finalState.activeCycle as Data.Success).data)

        job.cancel()
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.today.TodayViewModelTest"`
Expected: FAIL — compile error (`TodayState` has no `activeCycle`, `MedicationRepository` mock has no `getActiveCycle()` stub target yet since it's added in Task 2 already — the actual failure here is `finalState.activeCycle` not existing on `TodayState`)

- [ ] **Step 3: Update `TodayViewModel`**

Replace the whole `TodayViewModel.kt` with:

```kotlin
package com.yhdista.dosetracker.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class TodayState(
    val doses: Data<List<Dose>> = Data.Loading,
    val activeCycle: Data<Cycle?> = Data.Loading,
    val selectedDoseId: Long? = null
)

sealed interface TodayEvent {
    data class ToggleDoseStatus(val dose: Dose) : TodayEvent
    data class SelectDose(val id: Long?) : TodayEvent
}

class TodayViewModel(
    private val repository: MedicationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _selectedDoseId = savedStateHandle.getStateFlow<Long?>("selectedDoseId", null)

    val uiState: StateFlow<TodayState> = combine(
        repository.getDosesForDate(Clock.System.todayIn(TimeZone.currentSystemDefault())),
        repository.getActiveCycle(),
        _selectedDoseId
    ) { doses, activeCycle, selectedId ->
        TodayState(
            doses = doses,
            activeCycle = activeCycle,
            selectedDoseId = selectedId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayState()
    )

    fun onEvent(event: TodayEvent) {
        when (event) {
            is TodayEvent.ToggleDoseStatus -> toggleDoseStatus(event.dose)
            is TodayEvent.SelectDose -> selectDose(event.id)
        }
    }

    private fun toggleDoseStatus(dose: Dose) {
        viewModelScope.launch {
            val newStatus = if (dose.status == DoseStatus.TAKEN) DoseStatus.PENDING else DoseStatus.TAKEN
            repository.updateDose(dose.copy(status = newStatus))
        }
    }

    private fun selectDose(id: Long?) {
        savedStateHandle["selectedDoseId"] = id
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.today.TodayViewModelTest"`
Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 5: Update `TodayScreen` with the dashboard header and cycle/other grouping**

Replace the whole `TodayScreen.kt` with:

```kotlin
package com.yhdista.dosetracker.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.ui.theme.DoseTrackerTheme
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlinx.datetime.format.char
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onNavigateToConfirm: (Long) -> Unit,
    onNavigateToCreateCycle: () -> Unit,
    onNavigateToCycleHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TodayContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateToConfirm = onNavigateToConfirm,
        onCreateCycle = onNavigateToCreateCycle,
        onOpenCycleHistory = onNavigateToCycleHistory
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayContent(
    state: TodayState,
    onEvent: (TodayEvent) -> Unit,
    onNavigateToConfirm: (Long) -> Unit,
    onCreateCycle: () -> Unit = {},
    onOpenCycleHistory: () -> Unit = {}
) {
    val activeCycle = (state.activeCycle as? Data.Success)?.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's Doses", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        when (val doses = state.doses) {
            is Data.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Data.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(doses.message)
                }
            }
            is Data.Success -> {
                val cycleDoses = if (activeCycle != null) doses.data.filter { it.cycleId == activeCycle.id } else emptyList()
                val otherDoses = if (activeCycle != null) doses.data.filter { it.cycleId != activeCycle.id } else doses.data

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        if (activeCycle != null) {
                            CycleDashboardHeader(cycle = activeCycle, onOpenHistory = onOpenCycleHistory)
                        } else {
                            NoCycleHeader(onCreateCycle = onCreateCycle)
                        }
                    }

                    if (doses.data.isEmpty()) {
                        item {
                            Text(
                                "No doses scheduled for today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (activeCycle != null && cycleDoses.isNotEmpty()) {
                        item {
                            Text(
                                "V ramci cyklu ${activeCycle.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(cycleDoses, key = { it.id }) { dose ->
                            DoseItem(
                                dose = dose,
                                onClick = { onNavigateToConfirm(dose.id) },
                                onToggleStatus = { onEvent(TodayEvent.ToggleDoseStatus(dose)) }
                            )
                        }
                    }

                    if (otherDoses.isNotEmpty()) {
                        if (activeCycle != null) {
                            item {
                                Text(
                                    "Ostatni",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        items(otherDoses, key = { it.id }) { dose ->
                            DoseItem(
                                dose = dose,
                                onClick = { onNavigateToConfirm(dose.id) },
                                onToggleStatus = { onEvent(TodayEvent.ToggleDoseStatus(dose)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleDashboardHeader(cycle: Cycle, onOpenHistory: () -> Unit) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val elapsedDays = cycle.startDate.daysUntil(today)
    val typeLabel = when (cycle.type) {
        CycleType.NORMAL -> "Cyklus"
        CycleType.STANDARD -> "Standardni cyklus"
        CycleType.POST -> "Post-cyklus"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(cycle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Zacatek: ${cycle.startDate}")
            Text("Bezi $elapsedDays dni")
            val totalWeeks = cycle.totalWeeks
            if (totalWeeks != null) {
                val totalDays = totalWeeks * 7
                val remainingDays = (totalDays - elapsedDays).coerceAtLeast(0)
                val endDate = cycle.startDate.plus(totalDays, DateTimeUnit.DAY)
                Text("Zbyva $remainingDays dni (konci $endDate)")
            } else {
                Text("Bezi neomezene")
            }
            TextButton(onClick = onOpenHistory) {
                Text("Historie cyklu")
            }
        }
    }
}

@Composable
private fun NoCycleHeader(onCreateCycle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Zadny aktivni cyklus", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onCreateCycle) {
                Text("+ Novy cyklus")
            }
        }
    }
}

private val timeOnlyFormat = kotlinx.datetime.LocalDateTime.Format {
    hour(); char(':'); minute()
}

@Composable
fun DoseItem(
    dose: Dose,
    onClick: () -> Unit,
    onToggleStatus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (dose.status == DoseStatus.TAKEN)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dose.medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Scheduled at ${dose.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).format(timeOnlyFormat)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleStatus) {
                Icon(
                    imageVector = if (dose.status == DoseStatus.TAKEN)
                        Icons.Rounded.CheckCircle
                    else
                        Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = "Toggle Status",
                    tint = if (dose.status == DoseStatus.TAKEN)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Preview
@Composable
fun TodayContentPreview() {
    DoseTrackerTheme {
        TodayContent(
            state = TodayState(
                doses = Data.Success(listOf(
                    Dose(id = 1, medicationId = 1, timestamp = Clock.System.now(), status = DoseStatus.PENDING),
                    Dose(id = 2, medicationId = 2, timestamp = Clock.System.now(), status = DoseStatus.TAKEN)
                )),
                activeCycle = Data.Success(null)
            ),
            onEvent = {},
            onNavigateToConfirm = {}
        )
    }
}
```

Note: the empty-state text ("No doses scheduled for today") is now rendered as a list item alongside the dashboard header (rather than replacing the whole screen), since the header must stay visible even on a day with zero doses.

Fix the Czech copy above to use proper diacritics when writing the file: "Začátek", "Běží ... dní", "Zbývá ... dní (končí ...)", "Běží neomezeně", "Žádný aktivní cyklus", "V rámci cyklu", "Ostatní", "Standardní cyklus".

- [ ] **Step 6: Verify the project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModel.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayScreen.kt \
        shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt
git commit -m "feat: show active cycle dashboard and cycle/other dose grouping on Today"
```

---

### Task 6: Cycle navigation destinations + `CreateCycleScreen`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CreateCycleViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CreateCycleScreen.kt`
- Test: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/cycle/CreateCycleViewModelTest.kt`

**Interfaces:**
- Consumes: `MedicationRepository.getActiveCycleOnce()`, `createCycle()`, `getStandardCycle()`, `updateCycle()` (Task 2); `DoseGenerator.runForToday()` (Task 4).
- Produces: `Destination.CreateCycle`, `Destination.CycleWeekEditor(cycleId, weekIndex)`, `Destination.CycleHistory`; `CreateCycleViewModel(repository, doseGenerator)`; `CreateCycleScreen(viewModel, onBack, onCreated)` — consumed by Task 9.

- [ ] **Step 1: Add the new destinations**

Add these three entries inside the `Destination` sealed interface in `Destinations.kt` (after `data class MedicationReport`):

```kotlin
    @Serializable
    data object CreateCycle : Destination

    @Serializable
    data class CycleWeekEditor(val cycleId: Long, val weekIndex: Int) : Destination

    @Serializable
    data object CycleHistory : Destination
```

- [ ] **Step 2: Write the failing `CreateCycleViewModel` tests**

```kotlin
package com.yhdista.dosetracker.ui.cycle

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CreateCycleViewModelTest {

    private val repository = mock<MedicationRepository>()
    private val doseGenerator = mock<DoseGenerator>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `creates and activates a NORMAL cycle immediately when there is no active cycle`() = runTest {
        whenever(repository.getActiveCycleOnce()).thenReturn(null)
        whenever(repository.createCycle(any())).thenReturn(Data.Success(1L))

        val viewModel = CreateCycleViewModel(repository, doseGenerator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CreateCycleEvent.NameChanged("Cyklus"))
        viewModel.onEvent(CreateCycleEvent.TotalWeeksChanged(4))
        viewModel.onEvent(CreateCycleEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).createCycle(
            argThat { name == "Cyklus" && type == CycleType.NORMAL && totalWeeks == 4 && status == CycleStatus.ACTIVE }
        )
        assert(viewModel.uiState.value.createdCycleId == 1L)
    }

    @Test
    fun `editing the STANDARD template in place updates the existing row instead of creating a new one`() = runTest {
        val existing = Cycle(
            id = 9, name = "Stary nazev", type = CycleType.STANDARD, totalWeeks = null,
            startDate = LocalDate(2025, 1, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_NONE
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(existing)
        whenever(repository.getStandardCycle()).thenReturn(existing)

        val viewModel = CreateCycleViewModel(repository, doseGenerator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CreateCycleEvent.NameChanged("Novy nazev"))
        viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.STANDARD))
        viewModel.onEvent(CreateCycleEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateCycle(existing.copy(name = "Novy nazev"))
        verify(repository, never()).createCycle(any())
    }

    @Test
    fun `creating a POST cycle attaches it as the active cycle's next cycle`() = runTest {
        val active = Cycle(
            id = 1, name = "Cyklus", type = CycleType.NORMAL, totalWeeks = 4,
            startDate = LocalDate(2026, 7, 1), status = CycleStatus.ACTIVE, onCompleteAction = CycleCompleteAction.TO_STANDARD
        )
        whenever(repository.getActiveCycleOnce()).thenReturn(active)
        whenever(repository.createCycle(any())).thenReturn(Data.Success(5L))

        val viewModel = CreateCycleViewModel(repository, doseGenerator)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CreateCycleEvent.NameChanged("Post-cyklus"))
        viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.POST))
        viewModel.onEvent(CreateCycleEvent.TotalWeeksChanged(2))
        viewModel.onEvent(CreateCycleEvent.Save)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).createCycle(
            argThat { name == "Post-cyklus" && type == CycleType.POST && totalWeeks == 2 && status == CycleStatus.DRAFT }
        )
        verify(repository).updateCycle(
            active.copy(nextCycleId = 5L, onCompleteAction = CycleCompleteAction.TO_POST)
        )
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.cycle.CreateCycleViewModelTest"`
Expected: FAIL — compile error (`CreateCycleViewModel`/`CreateCycleEvent` are unresolved)

- [ ] **Step 4: Implement `CreateCycleViewModel`**

```kotlin
package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class CreateCycleState(
    val hasActiveCycle: Boolean? = null, // null while unknown
    val name: String = "",
    val type: CycleType = CycleType.NORMAL,
    val totalWeeks: Int = 4,
    val onCompleteAction: CycleCompleteAction = CycleCompleteAction.TO_STANDARD,
    val createdCycleId: Long? = null,
    val createdWeekCount: Int = 0
)

sealed interface CreateCycleEvent {
    data class NameChanged(val name: String) : CreateCycleEvent
    data class TypeChanged(val type: CycleType) : CreateCycleEvent
    data class TotalWeeksChanged(val weeks: Int) : CreateCycleEvent
    data class OnCompleteActionChanged(val action: CycleCompleteAction) : CreateCycleEvent
    object Save : CreateCycleEvent
}

class CreateCycleViewModel(
    private val repository: MedicationRepository,
    private val doseGenerator: DoseGenerator
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCycleState())
    val uiState: StateFlow<CreateCycleState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val active = repository.getActiveCycleOnce()
            _state.value = _state.value.copy(
                hasActiveCycle = active != null,
                type = if (active != null) CycleType.POST else CycleType.NORMAL
            )
        }
    }

    fun onEvent(event: CreateCycleEvent) {
        when (event) {
            is CreateCycleEvent.NameChanged -> _state.value = _state.value.copy(name = event.name)
            is CreateCycleEvent.TypeChanged -> _state.value = _state.value.copy(type = event.type)
            is CreateCycleEvent.TotalWeeksChanged -> _state.value = _state.value.copy(totalWeeks = event.weeks)
            is CreateCycleEvent.OnCompleteActionChanged -> _state.value = _state.value.copy(onCompleteAction = event.action)
            is CreateCycleEvent.Save -> save()
        }
    }

    private fun save() {
        viewModelScope.launch {
            val current = _state.value
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val active = repository.getActiveCycleOnce()

            val result: Data<Long>? = when {
                active == null -> {
                    val cycle = Cycle(
                        name = current.name,
                        type = current.type,
                        totalWeeks = if (current.type == CycleType.STANDARD) null else current.totalWeeks,
                        startDate = today,
                        status = CycleStatus.ACTIVE,
                        onCompleteAction = current.onCompleteAction
                    )
                    repository.createCycle(cycle).also {
                        if (it is Data.Success) doseGenerator.runForToday()
                    }
                }
                current.type == CycleType.STANDARD -> {
                    val existing = repository.getStandardCycle()
                    if (existing != null) {
                        repository.updateCycle(existing.copy(name = current.name))
                        Data.Success(existing.id)
                    } else {
                        val cycle = Cycle(
                            name = current.name,
                            type = CycleType.STANDARD,
                            totalWeeks = null,
                            startDate = today,
                            status = CycleStatus.DRAFT,
                            onCompleteAction = CycleCompleteAction.TO_NONE
                        )
                        repository.createCycle(cycle)
                    }
                }
                current.type == CycleType.POST -> {
                    val cycle = Cycle(
                        name = current.name,
                        type = CycleType.POST,
                        totalWeeks = current.totalWeeks,
                        startDate = today,
                        status = CycleStatus.DRAFT,
                        onCompleteAction = CycleCompleteAction.TO_NONE
                    )
                    val created = repository.createCycle(cycle)
                    if (created is Data.Success) {
                        repository.updateCycle(
                            active.copy(nextCycleId = created.data, onCompleteAction = CycleCompleteAction.TO_POST)
                        )
                    }
                    created
                }
                else -> null // NORMAL type while a cycle is already active is not offered by the UI.
            }

            if (result is Data.Success) {
                val weekCount = if (current.type == CycleType.STANDARD) 1 else current.totalWeeks
                _state.value = _state.value.copy(createdCycleId = result.data, createdWeekCount = weekCount)
            }
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.cycle.CreateCycleViewModelTest"`
Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 6: Implement `CreateCycleScreen`**

```kotlin
package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCycleScreen(
    viewModel: CreateCycleViewModel,
    onBack: () -> Unit,
    onCreated: (cycleId: Long, weekCount: Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdCycleId) {
        state.createdCycleId?.let { onCreated(it, state.createdWeekCount) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novy cyklus", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.hasActiveCycle == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.onEvent(CreateCycleEvent.NameChanged(it)) },
                    label = { Text("Nazev") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Typ cyklu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.hasActiveCycle == false) {
                        FilterChip(
                            selected = state.type == CycleType.NORMAL,
                            onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.NORMAL)) },
                            label = { Text("Cyklus") }
                        )
                    }
                    FilterChip(
                        selected = state.type == CycleType.STANDARD,
                        onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.STANDARD)) },
                        label = { Text("Standardni cyklus") }
                    )
                    if (state.hasActiveCycle == true) {
                        FilterChip(
                            selected = state.type == CycleType.POST,
                            onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.POST)) },
                            label = { Text("Post-cyklus") }
                        )
                    }
                }

                if (state.type != CycleType.STANDARD) {
                    OutlinedTextField(
                        value = state.totalWeeks.toString(),
                        onValueChange = { newValue ->
                            newValue.toIntOrNull()?.let { viewModel.onEvent(CreateCycleEvent.TotalWeeksChanged(it)) }
                        },
                        label = { Text("Pocet tydnu") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.type != CycleType.STANDARD && state.hasActiveCycle == false) {
                    Text("Po skonceni prejde do", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.onCompleteAction == CycleCompleteAction.TO_STANDARD,
                            onClick = { viewModel.onEvent(CreateCycleEvent.OnCompleteActionChanged(CycleCompleteAction.TO_STANDARD)) },
                            label = { Text("Standardni cyklus") }
                        )
                        FilterChip(
                            selected = state.onCompleteAction == CycleCompleteAction.TO_POST,
                            onClick = { viewModel.onEvent(CreateCycleEvent.OnCompleteActionChanged(CycleCompleteAction.TO_POST)) },
                            label = { Text("Post-cyklus") }
                        )
                    }
                }

                val isValid = state.name.isNotBlank() && (state.type == CycleType.STANDARD || state.totalWeeks > 0)
                Button(
                    onClick = { viewModel.onEvent(CreateCycleEvent.Save) },
                    enabled = isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ulozit")
                }
            }
        }
    }
}
```

Fix the Czech copy above to use proper diacritics when writing the file: "Nový cyklus", "Název", "Standardní cyklus", "Počet týdnů", "Po skončení přejde do", "Uložit".

- [ ] **Step 7: Register `CreateCycleViewModel` in `viewModelModule`**

Add this line inside `viewModelModule` in `ViewModelModule.kt` (after the `DebugViewModel` line):

```kotlin
    viewModel { com.yhdista.dosetracker.ui.cycle.CreateCycleViewModel(get(), get()) }
```

- [ ] **Step 8: Verify the project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CreateCycleViewModel.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CreateCycleScreen.kt \
        shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/cycle/CreateCycleViewModelTest.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt
git commit -m "feat: add cycle destinations and CreateCycleScreen"
```

---

### Task 7: Shared `ScheduleDialog` extraction + `CycleWeekEditorScreen`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/schedule/ScheduleDialog.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/medicationdetail/MedicationDetailScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleWeekEditorViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleWeekEditorScreen.kt`

**Interfaces:**
- Consumes: `MedicationRepository.getCycleWeek`, `getSchedulesForCycleWeek`, `getMedications`, `getPeriodTimes`, `insertSchedule`, `updateSchedule`, `deleteSchedule` (Task 2); `DoseGenerator.runForToday()` (Task 4).
- Produces: `ScheduleDialog(...)`/`formatMinutes(...)` now public in `ui.schedule` package (reused by `MedicationDetailScreen` and `CycleWeekEditorScreen`); `CycleWeekEditorViewModel(repository, doseGenerator, savedStateHandle)`; `CycleWeekEditorScreen(cycleId, weekIndex, viewModel, onBack)` — consumed by Task 9.

- [ ] **Step 1: Extract `ScheduleDialog` and `formatMinutes` into a shared file**

Create `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/schedule/ScheduleDialog.kt` with this content (this is the exact body of the current `private fun ScheduleDialog` and `private fun formatMinutes` from `MedicationDetailScreen.kt`, made public and moved to their own file):

```kotlin
package com.yhdista.dosetracker.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.reminder.WeekDays
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScheduleDialog(
    schedule: ReminderSchedule? = null,
    periodTimes: Map<String, Int>,
    onDismiss: () -> Unit,
    onConfirm: (
        minutesOfDay: Int,
        days: Set<DayOfWeek>,
        scheduleType: String,
        intervalDays: Int,
        startDate: LocalDate?,
        timeType: String,
        dayPeriod: String?
    ) -> Unit
) {
    val isEdit = schedule != null

    var timeType by remember { mutableStateOf(schedule?.timeType ?: "EXACT") }
    val initialMinutes = schedule?.minutesOfDay ?: 480
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )
    var dayPeriod by remember { mutableStateOf(schedule?.dayPeriod ?: "MORNING") }

    var scheduleType by remember { mutableStateOf(schedule?.scheduleType ?: "WEEKDAYS") }
    var selectedDays by remember {
        mutableStateOf(
            if (schedule != null && schedule.scheduleType == "WEEKDAYS") {
                WeekDays.fromBitmask(schedule.daysOfWeek)
            } else {
                DayOfWeek.entries.toSet()
            }
        )
    }
    var intervalDaysStr by remember { mutableStateOf(schedule?.intervalDays?.toString() ?: "2") }
    var startDate by remember { mutableStateOf(schedule?.startDate ?: Clock.System.todayIn(TimeZone.currentSystemDefault())) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        startDate = Instant.fromEpochMilliseconds(millis)
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Reminder" else "Add Reminder") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Time Setting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = timeType == "EXACT",
                        onClick = { timeType = "EXACT" },
                        label = { Text("Exact Time") }
                    )
                    FilterChip(
                        selected = timeType == "PERIOD",
                        onClick = { timeType = "PERIOD" },
                        label = { Text("Day Period") }
                    )
                }

                if (timeType == "EXACT") {
                    TimePicker(state = timePickerState)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "MORNING" to "Morning (Ráno)",
                            "NOON" to "Noon (Poledne)",
                            "EVENING" to "Evening (Večer)",
                            "NIGHT" to "Night (Noc)"
                        ).forEach { (key, label) ->
                            val timeStr = periodTimes[key]?.let { formatMinutes(it) } ?: ""
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { dayPeriod = key }
                            ) {
                                RadioButton(
                                    selected = dayPeriod == key,
                                    onClick = { dayPeriod = key }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("$label - $timeStr")
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("Frequency Setting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = scheduleType == "WEEKDAYS",
                        onClick = { scheduleType = "WEEKDAYS" },
                        label = { Text("Specific Days") }
                    )
                    FilterChip(
                        selected = scheduleType == "INTERVAL",
                        onClick = { scheduleType = "INTERVAL" },
                        label = { Text("Interval") }
                    )
                }

                if (scheduleType == "WEEKDAYS") {
                    Text("Days of week", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in selectedDays,
                                onClick = {
                                    selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                                },
                                label = { Text(day.name.take(3)) }
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = intervalDaysStr,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    intervalDaysStr = newValue
                                }
                            },
                            label = { Text("Repeat every X days") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Date: $startDate", style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { showDatePicker = true }) {
                                Text("Choose")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val interval = intervalDaysStr.toIntOrNull() ?: 1
            val isValid = if (scheduleType == "WEEKDAYS") selectedDays.isNotEmpty() else interval > 0
            Button(
                onClick = {
                    val minutes = timePickerState.hour * 60 + timePickerState.minute
                    onConfirm(
                        minutes,
                        selectedDays,
                        scheduleType,
                        interval,
                        if (scheduleType == "INTERVAL") startDate else null,
                        timeType,
                        if (timeType == "PERIOD") dayPeriod else null
                    )
                },
                enabled = isValid
            ) {
                Text(if (isEdit) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val hStr = if (h < 10) "0$h" else "$h"
    val mStr = if (m < 10) "0$m" else "$m"
    return "$hStr:$mStr"
}
```

- [ ] **Step 2: Remove the old private definitions from `MedicationDetailScreen.kt` and import the shared ones**

In `MedicationDetailScreen.kt`, delete the entire `private fun ScheduleDialog(...)` block (the whole function, from its `@OptIn` annotation down to its closing `}` — this is the block starting at `@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)` right before `private fun ScheduleDialog`) and the trailing `private fun formatMinutes(minutes: Int): String { ... }` block at the end of the file.

Add these two imports near the top of the file (alongside the existing `com.yhdista.dosetracker.*` imports):

```kotlin
import com.yhdista.dosetracker.ui.schedule.ScheduleDialog
import com.yhdista.dosetracker.ui.schedule.formatMinutes
```

Leave every other existing import in `MedicationDetailScreen.kt` untouched, even ones that become unused after this deletion (e.g. `kotlin.time.Clock`, `kotlinx.datetime.DayOfWeek`) — unused imports are a harmless lint warning, not a build error, and precisely trimming them risks removing one still needed by `ScheduleRow`/`PeriodSettingsDialog`.

- [ ] **Step 3: Verify the project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Implement `CycleWeekEditorViewModel`**

```kotlin
package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import com.yhdista.dosetracker.reminder.WeekDays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

data class CycleWeekEditorState(
    val weekId: Long? = null,
    val schedules: Data<List<ReminderSchedule>> = Data.Loading,
    val medications: Data<List<Medication>> = Data.Loading,
    val periodTimes: Data<Map<String, Int>> = Data.Loading
)

sealed interface CycleWeekEditorEvent {
    data class AddSchedule(
        val medicationId: Long,
        val minutesOfDay: Int,
        val daysOfWeek: Set<DayOfWeek>,
        val scheduleType: String,
        val intervalDays: Int,
        val startDate: LocalDate?,
        val timeType: String,
        val dayPeriod: String?
    ) : CycleWeekEditorEvent

    data class UpdateSchedule(val schedule: ReminderSchedule) : CycleWeekEditorEvent
    data class DeleteSchedule(val schedule: ReminderSchedule) : CycleWeekEditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CycleWeekEditorViewModel(
    private val repository: MedicationRepository,
    private val doseGenerator: DoseGenerator,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cycleIdFlow = savedStateHandle.getStateFlow<Long?>("cycleId", null)
    private val weekIndexFlow = savedStateHandle.getStateFlow<Int?>("weekIndex", null)

    val uiState: StateFlow<CycleWeekEditorState> = combine(cycleIdFlow, weekIndexFlow) { cycleId, weekIndex ->
        cycleId to weekIndex
    }
        .filter { (cycleId, weekIndex) -> cycleId != null && weekIndex != null }
        .map { (cycleId, weekIndex) -> repository.getCycleWeek(cycleId!!, weekIndex!!)?.id }
        .filterNotNull()
        .flatMapLatest { weekId ->
            combine(
                repository.getSchedulesForCycleWeek(weekId),
                repository.getMedications(),
                repository.getPeriodTimes()
            ) { schedules, medications, periodTimes ->
                CycleWeekEditorState(weekId, schedules, medications, periodTimes)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CycleWeekEditorState()
        )

    fun setCycleWeek(cycleId: Long, weekIndex: Int) {
        if (savedStateHandle.get<Long>("cycleId") == null) {
            savedStateHandle["cycleId"] = cycleId
            savedStateHandle["weekIndex"] = weekIndex
        }
    }

    fun onEvent(event: CycleWeekEditorEvent) {
        when (event) {
            is CycleWeekEditorEvent.AddSchedule -> addSchedule(event)
            is CycleWeekEditorEvent.UpdateSchedule -> updateSchedule(event.schedule)
            is CycleWeekEditorEvent.DeleteSchedule -> deleteSchedule(event.schedule)
        }
    }

    private fun addSchedule(event: CycleWeekEditorEvent.AddSchedule) {
        viewModelScope.launch {
            val weekId = uiState.value.weekId ?: return@launch
            repository.insertSchedule(
                ReminderSchedule(
                    medicationId = event.medicationId,
                    minutesOfDay = event.minutesOfDay,
                    daysOfWeek = WeekDays.toBitmask(event.daysOfWeek),
                    scheduleType = event.scheduleType,
                    intervalDays = event.intervalDays,
                    startDate = event.startDate,
                    timeType = event.timeType,
                    dayPeriod = event.dayPeriod,
                    cycleWeekId = weekId
                )
            )
            doseGenerator.runForToday()
        }
    }

    private fun updateSchedule(schedule: ReminderSchedule) {
        viewModelScope.launch {
            repository.updateSchedule(schedule)
            doseGenerator.runForToday()
        }
    }

    private fun deleteSchedule(schedule: ReminderSchedule) {
        viewModelScope.launch {
            repository.deleteSchedule(schedule)
            doseGenerator.runForToday()
        }
    }
}
```

- [ ] **Step 5: Implement `CycleWeekEditorScreen`**

```kotlin
package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.reminder.WeekDays
import com.yhdista.dosetracker.ui.schedule.ScheduleDialog
import com.yhdista.dosetracker.ui.schedule.formatMinutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleWeekEditorScreen(
    cycleId: Long,
    weekIndex: Int,
    viewModel: CycleWeekEditorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingMedicationId by remember { mutableStateOf<Long?>(null) }
    var editingSchedule by remember { mutableStateOf<ReminderSchedule?>(null) }
    var pickingMedication by remember { mutableStateOf(false) }

    LaunchedEffect(cycleId, weekIndex) {
        viewModel.setCycleWeek(cycleId, weekIndex)
    }

    val periodTimes = (state.periodTimes as? Data.Success)?.data ?: emptyMap()
    val medications = (state.medications as? Data.Success)?.data ?: emptyList()
    val medicationNames = medications.associate { it.id to it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tyden ${weekIndex + 1}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickingMedication = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Pridat lek")
            }
        }
    ) { padding ->
        if (pickingMedication) {
            MedicationPickerDialog(
                medications = medications,
                onDismiss = { pickingMedication = false },
                onPick = { medicationId ->
                    pickingMedication = false
                    pendingMedicationId = medicationId
                }
            )
        }

        pendingMedicationId?.let { medicationId ->
            ScheduleDialog(
                periodTimes = periodTimes,
                onDismiss = { pendingMedicationId = null },
                onConfirm = { minutes, days, schedType, interval, start, tType, period ->
                    viewModel.onEvent(
                        CycleWeekEditorEvent.AddSchedule(
                            medicationId = medicationId,
                            minutesOfDay = minutes,
                            daysOfWeek = days,
                            scheduleType = schedType,
                            intervalDays = interval,
                            startDate = start,
                            timeType = tType,
                            dayPeriod = period
                        )
                    )
                    pendingMedicationId = null
                }
            )
        }

        editingSchedule?.let { schedule ->
            ScheduleDialog(
                schedule = schedule,
                periodTimes = periodTimes,
                onDismiss = { editingSchedule = null },
                onConfirm = { minutes, days, schedType, interval, start, tType, period ->
                    viewModel.onEvent(
                        CycleWeekEditorEvent.UpdateSchedule(
                            schedule.copy(
                                minutesOfDay = minutes,
                                daysOfWeek = WeekDays.toBitmask(days),
                                scheduleType = schedType,
                                intervalDays = interval,
                                startDate = start,
                                timeType = tType,
                                dayPeriod = period
                            )
                        )
                    )
                    editingSchedule = null
                }
            )
        }

        when (val result = state.schedules) {
            is Data.Loading -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Data.Error -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Chyba: ${result.message}")
                }
            }
            is Data.Success -> {
                if (result.data.isEmpty()) {
                    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Zadny lek pro tento tyden")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(result.data, key = { it.id }) { schedule ->
                            CycleScheduleRow(
                                schedule = schedule,
                                medicationName = medicationNames[schedule.medicationId] ?: "?",
                                periodTimes = periodTimes,
                                onClick = { editingSchedule = schedule },
                                onDelete = { viewModel.onEvent(CycleWeekEditorEvent.DeleteSchedule(schedule)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleScheduleRow(
    schedule: ReminderSchedule,
    medicationName: String,
    periodTimes: Map<String, Int>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeLabel = if (schedule.timeType == "PERIOD") {
        val periodName = schedule.dayPeriod?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
        val minutes = periodTimes[schedule.dayPeriod] ?: schedule.minutesOfDay
        "$periodName (${formatMinutes(minutes)})"
    } else {
        formatMinutes(schedule.minutesOfDay)
    }
    val freqLabel = if (schedule.scheduleType == "INTERVAL") {
        "Kazdych ${schedule.intervalDays} dni (od ${schedule.startDate})"
    } else {
        val days = WeekDays.fromBitmask(schedule.daysOfWeek)
        if (days.size == 7) "Kazdy den" else days.joinToString { it.name.take(3) }
    }

    ListItem(
        headlineContent = { Text(medicationName, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("$timeLabel - $freqLabel") },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Smazat")
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun MedicationPickerDialog(
    medications: List<Medication>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vyber lek") },
        text = {
            LazyColumn {
                items(medications, key = { it.id }) { medication ->
                    ListItem(
                        headlineContent = { Text(medication.name) },
                        modifier = Modifier.clickable { onPick(medication.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Zrusit") }
        }
    )
}
```

Fix the Czech copy above to use proper diacritics when writing the file: "Týden", "Přidat lék", "Vyber lék", "Žádný lék pro tento týden", "Zrušit", "Každých ... dní (od ...)", "Každý den".

- [ ] **Step 6: Register `CycleWeekEditorViewModel` in `viewModelModule`**

Add this line inside `viewModelModule` in `ViewModelModule.kt`:

```kotlin
    viewModel { com.yhdista.dosetracker.ui.cycle.CycleWeekEditorViewModel(get(), get(), get()) }
```

- [ ] **Step 7: Verify the project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/schedule/ScheduleDialog.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/medicationdetail/MedicationDetailScreen.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleWeekEditorViewModel.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleWeekEditorScreen.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt
git commit -m "feat: extract shared ScheduleDialog and add CycleWeekEditorScreen"
```

---

### Task 8: `CycleHistoryScreen`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleHistoryViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleHistoryScreen.kt`

**Interfaces:**
- Consumes: `MedicationRepository.getCompletedCycles()` (Task 2).
- Produces: `CycleHistoryViewModel(repository)`; `CycleHistoryScreen(viewModel, onBack)` — consumed by Task 9.

- [ ] **Step 1: Implement `CycleHistoryViewModel`**

```kotlin
package com.yhdista.dosetracker.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CycleHistoryViewModel(
    repository: MedicationRepository
) : ViewModel() {
    val uiState: StateFlow<Data<List<Cycle>>> = repository.getCompletedCycles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Data.Loading
        )
}
```

- [ ] **Step 2: Implement `CycleHistoryScreen`**

```kotlin
package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.CycleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleHistoryScreen(
    viewModel: CycleHistoryViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historie cyklu", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val result = state) {
            is Data.Loading -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Data.Error -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Chyba: ${result.message}")
                }
            }
            is Data.Success -> {
                if (result.data.isEmpty()) {
                    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Zadny dokonceny cyklus")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(result.data, key = { it.id }) { cycle ->
                            ListItem(
                                headlineContent = { Text(cycle.name) },
                                supportingContent = { Text("${cycleTypeLabel(cycle.type)} - zacatek ${cycle.startDate}") }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun cycleTypeLabel(type: CycleType): String = when (type) {
    CycleType.NORMAL -> "Cyklus"
    CycleType.STANDARD -> "Standardni cyklus"
    CycleType.POST -> "Post-cyklus"
}
```

Fix the Czech copy above to use proper diacritics when writing the file: "Historie cyklu", "Žádný dokončený cyklus", "začátek", "Standardní cyklus".

- [ ] **Step 3: Register `CycleHistoryViewModel` in `viewModelModule`**

Add this line inside `viewModelModule` in `ViewModelModule.kt`:

```kotlin
    viewModel { com.yhdista.dosetracker.ui.cycle.CycleHistoryViewModel(get()) }
```

- [ ] **Step 4: Verify the project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleHistoryViewModel.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/cycle/CycleHistoryScreen.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt
git commit -m "feat: add CycleHistoryScreen"
```

---

### Task 9: Wire everything into `DoseTrackerAppMain` navigation

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt`

**Interfaces:**
- Consumes: `Destination.CreateCycle`/`CycleWeekEditor`/`CycleHistory` (Task 6), `CreateCycleScreen`/`ViewModel` (Task 6), `CycleWeekEditorScreen`/`ViewModel` (Task 7), `CycleHistoryScreen`/`ViewModel` (Task 8), `TodayScreen`'s new params (Task 5).

- [ ] **Step 1: Add the cycle screen imports**

Add these imports to `DoseTrackerAppMain.kt` (alongside the existing `com.yhdista.dosetracker.ui.*` imports):

```kotlin
import com.yhdista.dosetracker.ui.cycle.CreateCycleScreen
import com.yhdista.dosetracker.ui.cycle.CreateCycleViewModel
import com.yhdista.dosetracker.ui.cycle.CycleHistoryScreen
import com.yhdista.dosetracker.ui.cycle.CycleHistoryViewModel
import com.yhdista.dosetracker.ui.cycle.CycleWeekEditorScreen
import com.yhdista.dosetracker.ui.cycle.CycleWeekEditorViewModel
```

- [ ] **Step 2: Update the `Destination.Today` branch to pass the two new callbacks**

Replace the `is Destination.Today -> { ... }` block with:

```kotlin
                is Destination.Today -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        TodayScreen(
                            viewModel = koinViewModel<TodayViewModel>(),
                            onNavigateToConfirm = { doseId ->
                                backstack.add(Destination.ConfirmDose(doseId))
                            },
                            onNavigateToCreateCycle = { backstack.add(Destination.CreateCycle) },
                            onNavigateToCycleHistory = { backstack.add(Destination.CycleHistory) }
                        )
                    }
                }
```

- [ ] **Step 3: Add the three new `when` branches**

Add these branches right after the `is Destination.MedicationReport -> { ... }` block (before `is Destination.Settings -> { ... }`):

```kotlin
                is Destination.CreateCycle -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        CreateCycleScreen(
                            viewModel = koinViewModel<CreateCycleViewModel>(),
                            onBack = { backstack.removeLastOrNull() },
                            onCreated = { cycleId, weekCount ->
                                backstack.removeLastOrNull()
                                if (weekCount > 0) {
                                    backstack.add(Destination.CycleWeekEditor(cycleId, 0))
                                }
                            }
                        )
                    }
                }
                is Destination.CycleWeekEditor -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        CycleWeekEditorScreen(
                            cycleId = destination.cycleId,
                            weekIndex = destination.weekIndex,
                            viewModel = koinViewModel<CycleWeekEditorViewModel>(),
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.CycleHistory -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        CycleHistoryScreen(
                            viewModel = koinViewModel<CycleHistoryViewModel>(),
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
```

- [ ] **Step 4: Verify the project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run the full shared test suite**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, all tests passed (existing + new cycle tests)

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt
git commit -m "feat: wire cycle screens into app navigation"
```

---

### Task 10: End-to-end manual verification

**Files:** none (verification only)

- [ ] **Step 1: Launch the app**

Use the `run` skill (or `./gradlew :app:installDebug` + launch manually) to install and start the app on a device/emulator.

- [ ] **Step 2: Create a cycle with no cycle currently active**

Open the Today tab. Confirm it shows a "Žádný aktivní cyklus" card with a "+ Nový cyklus" button (no dashboard header). Tap it, fill in a name (e.g. "Testovací cyklus"), leave type as "Cyklus", set "Počet týdnů" to 2, leave "Standardní cyklus" selected as what follows, tap "Uložit". Confirm it navigates straight to a "Týden 1" screen.

- [ ] **Step 3: Add a medication schedule to week 1**

On the "Týden 1" screen, tap the add (+) button, pick a medication from the list, fill in the schedule dialog (any exact time, any day-of-week selection including today's weekday) and confirm. Confirm the medication now appears in the week's list with its time/frequency summary.

- [ ] **Step 4: Confirm the Today dashboard shows the new cycle**

Navigate back to the Today tab. Confirm the dashboard header now shows the cycle's name, type ("Cyklus"), start date, "Běží 0 dní", and "Zbývá 14 dní (končí ...)" with a "Historie cyklu" link. If the schedule you added matches today's weekday, confirm a dose appears under a "V rámci cyklu Testovací cyklus" section (use the Debug tab's "Generate Doses for Today" button first if it doesn't show up immediately).

- [ ] **Step 5: Confirm standalone doses stay separate**

Go to an existing medication (or add a new one) via the Medications tab and give it its own reminder schedule for today, independent of the cycle. Return to Today and confirm this dose appears under "Ostatní", not under the cycle's section.

- [ ] **Step 6: Confirm the cycle history screen works**

From the Today dashboard, tap "Historie cyklu". Confirm it opens an empty ("Žádný dokončený cyklus") list, since no cycle has completed yet.

- [ ] **Step 7: Confirm a completed cycle transitions to the STANDARD cycle**

Using the Debug tab, reset the database (or manually adjust system date forward, if convenient) is not required — instead, sanity-check the transition logic already covered by `CycleLifecycleManagerTest` in Task 3. As a lighter manual check: go back into "+ Nový cyklus" while the test cycle is active, confirm the type picker only offers "Standardní cyklus" and "Post-cyklus" (not "Cyklus") when a cycle is already running — this confirms the one-active-cycle UI constraint from Task 6 is working.

- [ ] **Step 8: Confirm no regressions in the existing Today/Medication flows**

Toggle a dose's status (tap the checkmark icon) on both a cycle dose and a standalone dose — confirm both update correctly. Open the Medications tab and confirm `MedicationDetailScreen`'s existing "Add Reminder" dialog (now backed by the shared `ScheduleDialog`) still works exactly as before.
