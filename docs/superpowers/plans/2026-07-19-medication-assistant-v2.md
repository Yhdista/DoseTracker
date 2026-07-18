# Medication Assistant v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a medication have multiple recurring reminders (several times a day, chosen weekdays), let the user confirm/skip/snooze a dose straight from the notification or adjust it in-app, and show a weekly per-medication adherence report.

**Architecture:** A new `ReminderSchedule` Room entity (many per `Medication`) replaces the old single `reminderTime`/`frequency` fields. A pure-Kotlin `DoseGenerator` in `:shared` materializes each day's scheduled occurrences into real `Dose` rows (`PENDING`) ahead of time and arms one exact `AlarmManager` alarm per dose via the existing `DoseReminderScheduler` abstraction — the notification, Today screen, and weekly report all then just read/write ordinary `Dose` rows. Notification actions and the missed-dose timeout are separate `BroadcastReceiver`s in `:app` that update a `Dose` by id.

**Tech Stack:** Kotlin Multiplatform (`androidTarget()` only), Jetpack Compose Multiplatform, Room 2.8.4, Koin 4.1.0 (incl. `koin-androidx-workmanager`), WorkManager, kotlinx-datetime 0.8.0, kotlinx-coroutines.

## Global Constraints

- No new Gradle dependencies — everything needed (Room, WorkManager, Koin, kotlinx-datetime, Compose Material3) is already in `gradle/libs.versions.toml`.
- `AppDatabase` schema changes are shipped with `fallbackToDestructiveMigration(dropAllTables = true)` — no `Migration` classes, no data preserved across the upgrade (confirmed acceptable: no real user data exists in the app yet).
- Tests follow the codebase's existing convention exactly (see `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt`): JUnit4 (`org.junit.Test`/`Before`/`After`), `mockito-kotlin` (`mock<>()`, `whenever`, `verify`), `kotlinx-coroutines-test` `StandardTestDispatcher`. **Not** JUnit5, not Turbine, not AssertK — those aren't set up in this project.
- New tests live under `shared/src/androidHostTest/kotlin/...`, mirroring where `TodayViewModelTest` already lives.
- UI copy is hardcoded inline in Compose (`Text("...")`), matching every existing screen — no `strings.xml` usage for screen text.
- ViewModels that need an id passed from navigation use the existing manual pattern (see `AddDoseViewModel`/`AddDoseScreen`): a `set<X>Id(id)` method backed by `SavedStateHandle.getStateFlow`, called from a `LaunchedEffect(id)` in the screen composable. Nav3 does **not** auto-populate `SavedStateHandle` in this project.
- `minSdk = 28`, `compileSdk = 37`, Kotlin `2.4.10`, JVM target 17.
- Build/compile verification command for every task: `./gradlew :app:assembleDebug`.
- Test verification command: `./gradlew :shared:testAndroidHostTest --tests "<FQCN>"`.

---

### Task 1: `WeekDays` bitmask utility

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/WeekDays.kt`
- Test: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/reminder/WeekDaysTest.kt`

**Interfaces:**
- Produces: `WeekDays.toBitmask(days: Set<DayOfWeek>): Int`, `WeekDays.fromBitmask(mask: Int): Set<DayOfWeek>`, `WeekDays.contains(mask: Int, day: DayOfWeek): Boolean`, `WeekDays.ALL_DAYS: Int` — used by `ReminderSchedule` (Task 2), `DoseGenerator` (Task 3), `MedicationDetailViewModel` (Task 5).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.yhdista.dosetracker.reminder

import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekDaysTest {

    @Test
    fun `toBitmask sets one bit per day, Monday as bit 0`() {
        assertEquals(0b0000001, WeekDays.toBitmask(setOf(DayOfWeek.MONDAY)))
        assertEquals(0b1000000, WeekDays.toBitmask(setOf(DayOfWeek.SUNDAY)))
        assertEquals(0b0000101, WeekDays.toBitmask(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
    }

    @Test
    fun `fromBitmask is the inverse of toBitmask`() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        assertEquals(days, WeekDays.fromBitmask(WeekDays.toBitmask(days)))
    }

    @Test
    fun `contains reports whether a day's bit is set`() {
        val mask = WeekDays.toBitmask(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertTrue(WeekDays.contains(mask, DayOfWeek.MONDAY))
        assertTrue(WeekDays.contains(mask, DayOfWeek.FRIDAY))
        assertFalse(WeekDays.contains(mask, DayOfWeek.SUNDAY))
    }

    @Test
    fun `ALL_DAYS contains every day`() {
        DayOfWeek.entries.forEach { day ->
            assertTrue(WeekDays.contains(WeekDays.ALL_DAYS, day))
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.reminder.WeekDaysTest"`
Expected: FAIL (compile error — `WeekDays` does not exist yet)

- [ ] **Step 3: Write the implementation**

```kotlin
package com.yhdista.dosetracker.reminder

import kotlinx.datetime.DayOfWeek

object WeekDays {
    private val ORDER = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    )

    const val ALL_DAYS: Int = 0b1111111

    fun toBitmask(days: Set<DayOfWeek>): Int =
        days.fold(0) { mask, day -> mask or (1 shl ORDER.indexOf(day)) }

    fun fromBitmask(mask: Int): Set<DayOfWeek> =
        ORDER.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()

    fun contains(mask: Int, day: DayOfWeek): Boolean =
        mask and (1 shl ORDER.indexOf(day)) != 0
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.reminder.WeekDaysTest"`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/WeekDays.kt \
        shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/reminder/WeekDaysTest.kt
git commit -m "feat: add WeekDays bitmask utility for reminder schedules"
```

---

### Task 2: `ReminderSchedule` data model, `Medication`/`Dose` migration, DB v2

This is the foundational schema change. It touches every file that referenced `Medication.reminderTime`/`Medication.frequency` or the old per-medication `DoseReminderScheduler`, so all of them move together in one task to keep the build green.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/ReminderSchedule.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/ReminderScheduleEntity.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/ReminderScheduleDao.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/ReminderScheduleMapper.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/Medication.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/Dose.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/MedicationEntity.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/DoseEntity.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/MedicationMapper.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/DoseMapper.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/DoseDao.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/AppDatabase.kt`
- Modify: `shared/src/androidMain/kotlin/com/yhdista/dosetracker/data/local/DatabaseBuilder.android.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseReminderScheduler.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/reminder/ReminderScheduler.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/reminder/ReminderReceiver.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/reminder/RescheduleWorker.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/di/AppModule.kt`
- Modify: `shared/src/androidMain/kotlin/com/yhdista/dosetracker/di/DataModule.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/catalog/MedicationCatalogScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/catalog/MedicationCatalogViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt`

**Interfaces:**
- Produces: `ReminderSchedule(id, medicationId, minutesOfDay, daysOfWeek, enabled)`; `MedicationRepository.getMedicationOnce(id): Medication?`, `.getDoseOnce(id): Dose?`, `.getDoseForSchedule(scheduleId, timestamp): Dose?`, `.getSchedulesForMedication(medicationId): Flow<Data<List<ReminderSchedule>>>`, `.getEnabledSchedules(): Data<List<ReminderSchedule>>`, `.insertSchedule(schedule): Data<Long>`, `.deleteSchedule(schedule): Data<Unit>`; `DoseReminderScheduler.scheduleReminder(doseId: Long, at: Instant)`, `.cancelReminder(doseId: Long)` — consumed by `DoseGenerator` (Task 3) and everything after.

- [ ] **Step 1: `ReminderSchedule` domain model**

```kotlin
package com.yhdista.dosetracker.domain.model

data class ReminderSchedule(
    val id: Long = 0,
    val medicationId: Long,
    val minutesOfDay: Int, // 0..1439, local time of day
    val daysOfWeek: Int,   // bitmask, see WeekDays
    val enabled: Boolean = true
)
```

- [ ] **Step 2: `ReminderScheduleEntity`**

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
        )
    ],
    indices = [Index(value = ["medicationId"])]
)
data class ReminderScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val minutesOfDay: Int,
    val daysOfWeek: Int,
    val enabled: Boolean = true
)
```

- [ ] **Step 3: `ReminderScheduleDao`**

```kotlin
package com.yhdista.dosetracker.data.local.dao

import androidx.room.*
import com.yhdista.dosetracker.data.local.entity.ReminderScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderScheduleDao {
    @Query("SELECT * FROM reminder_schedules WHERE medicationId = :medicationId")
    fun getSchedulesForMedication(medicationId: Long): Flow<List<ReminderScheduleEntity>>

    @Query("SELECT * FROM reminder_schedules WHERE enabled = 1")
    suspend fun getAllEnabledSchedules(): List<ReminderScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ReminderScheduleEntity): Long

    @Delete
    suspend fun deleteSchedule(schedule: ReminderScheduleEntity)
}
```

- [ ] **Step 4: `ReminderScheduleMapper`**

```kotlin
package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.entity.ReminderScheduleEntity
import com.yhdista.dosetracker.domain.model.ReminderSchedule

fun ReminderScheduleEntity.toDomain(): ReminderSchedule = ReminderSchedule(
    id = id,
    medicationId = medicationId,
    minutesOfDay = minutesOfDay,
    daysOfWeek = daysOfWeek,
    enabled = enabled
)

fun ReminderSchedule.toEntity(): ReminderScheduleEntity = ReminderScheduleEntity(
    id = id,
    medicationId = medicationId,
    minutesOfDay = minutesOfDay,
    daysOfWeek = daysOfWeek,
    enabled = enabled
)
```

- [ ] **Step 5: Simplify `Medication`**

Replace the whole file:

```kotlin
package com.yhdista.dosetracker.domain.model

data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: Double,
    val unit: String
)
```

- [ ] **Step 6: Add `scheduleId` to `Dose`**

Replace the whole file:

```kotlin
package com.yhdista.dosetracker.domain.model

import kotlinx.datetime.Instant

data class Dose(
    val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long? = null,
    val medicationName: String = "",
    val timestamp: Instant,
    val amount: Double? = null,
    val unit: String? = null,
    val status: DoseStatus = DoseStatus.PENDING
)

enum class DoseStatus {
    TAKEN,
    MISSED,
    SKIPPED,
    PENDING
}
```

- [ ] **Step 7: `MedicationEntity` drops `frequency`/`reminderTime`**

```kotlin
package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: Double,
    val unit: String
)
```

- [ ] **Step 8: `DoseEntity` gains `scheduleId`**

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
    val status: DoseStatus
)
```

- [ ] **Step 9: Update `MedicationMapper` and `DoseMapper`**

`MedicationMapper.kt` — replace the whole file:

```kotlin
package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.entity.MedicationEntity
import com.yhdista.dosetracker.domain.model.Medication

fun MedicationEntity.toDomain(): Medication {
    return Medication(
        id = id,
        name = name,
        dosage = dosage,
        unit = unit
    )
}

fun Medication.toEntity(): MedicationEntity {
    return MedicationEntity(
        id = id,
        name = name,
        dosage = dosage,
        unit = unit
    )
}
```

`DoseMapper.kt` — replace the whole file:

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
        timestamp = timestamp,
        amount = amount,
        unit = unit,
        status = status
    )
}
```

- [ ] **Step 10: `DoseDao` gains two one-shot lookups**

Add to `DoseDao.kt` (keep everything else as-is), and add `import kotlinx.datetime.Instant` at the top:

```kotlin
    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName
        FROM doses
        INNER JOIN medications ON doses.medicationId = medications.id
        WHERE doses.id = :id
    """)
    suspend fun getDoseWithMedicationById(id: Long): DoseWithMedication?

    @Query("SELECT * FROM doses WHERE scheduleId = :scheduleId AND timestamp = :timestamp LIMIT 1")
    suspend fun getDoseForSchedule(scheduleId: Long, timestamp: Instant): DoseEntity?
```

- [ ] **Step 11: `AppDatabase` v2 with the new table and reseeded data**

Replace the whole file:

```kotlin
package com.yhdista.dosetracker.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.yhdista.dosetracker.data.local.dao.DoseDao
import com.yhdista.dosetracker.data.local.dao.MedicationDao
import com.yhdista.dosetracker.data.local.dao.ReminderScheduleDao
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import com.yhdista.dosetracker.data.local.entity.MedicationEntity
import com.yhdista.dosetracker.data.local.entity.ReminderScheduleEntity

@Database(
    entities = [MedicationEntity::class, DoseEntity::class, ReminderScheduleEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseDao(): DoseDao
    abstract fun reminderScheduleDao(): ReminderScheduleDao

    companion object {
        const val DATABASE_NAME = "dosetracker_db"

        val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                connection.execSQL("INSERT INTO medications (name, dosage, unit) VALUES ('Paracetamol', 500.0, 'mg')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit) VALUES ('Ibuprofen', 400.0, 'mg')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit) VALUES ('Amoxicillin', 500.0, 'mg')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit) VALUES ('Vitamin C', 1000.0, 'mg')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit) VALUES ('Lisinopril', 10.0, 'mg')")
                connection.execSQL("INSERT INTO reminder_schedules (medicationId, minutesOfDay, daysOfWeek, enabled) VALUES (1, 480, 127, 1)")
                connection.execSQL("INSERT INTO reminder_schedules (medicationId, minutesOfDay, daysOfWeek, enabled) VALUES (2, 540, 127, 1)")
                connection.execSQL("INSERT INTO reminder_schedules (medicationId, minutesOfDay, daysOfWeek, enabled) VALUES (3, 420, 127, 1)")
                connection.execSQL("INSERT INTO reminder_schedules (medicationId, minutesOfDay, daysOfWeek, enabled) VALUES (4, 600, 127, 1)")
                connection.execSQL("INSERT INTO reminder_schedules (medicationId, minutesOfDay, daysOfWeek, enabled) VALUES (5, 510, 127, 1)")
            }
        }
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
```

- [ ] **Step 12: Destructive migration in the Android builder**

Modify `DatabaseBuilder.android.kt`:

```kotlin
package com.yhdista.dosetracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
    return Room.databaseBuilder<AppDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath
    ).fallbackToDestructiveMigration(dropAllTables = true)
}
```

- [ ] **Step 13: `MedicationRepository` interface — drop nothing, add what Tasks 2–5 need**

Replace the whole file:

```kotlin
package com.yhdista.dosetracker.domain.repository

import com.yhdista.dosetracker.core.Data
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
    fun getAllDoses(): Flow<Data<List<Dose>>>
    suspend fun getDoseOnce(id: Long): Dose?
    suspend fun getDoseForSchedule(scheduleId: Long, timestamp: Instant): Dose?
    suspend fun insertDose(dose: Dose): Data<Long>
    suspend fun updateDose(dose: Dose): Data<Unit>

    fun getSchedulesForMedication(medicationId: Long): Flow<Data<List<ReminderSchedule>>>
    suspend fun getEnabledSchedules(): Data<List<ReminderSchedule>>
    suspend fun insertSchedule(schedule: ReminderSchedule): Data<Long>
    suspend fun deleteSchedule(schedule: ReminderSchedule): Data<Unit>
}
```

- [ ] **Step 14: `MedicationRepositoryImpl`**

Replace the whole file:

```kotlin
package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.data.local.dao.DoseDao
import com.yhdista.dosetracker.data.local.dao.MedicationDao
import com.yhdista.dosetracker.data.local.dao.ReminderScheduleDao
import com.yhdista.dosetracker.data.mapper.toDomain
import com.yhdista.dosetracker.data.mapper.toEntity
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

class MedicationRepositoryImpl(
    private val medicationDao: MedicationDao,
    private val doseDao: DoseDao,
    private val scheduleDao: ReminderScheduleDao
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

    override fun getAllDoses(): Flow<Data<List<Dose>>> {
        return doseDao.getAllDoses()
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch all doses", e)) }
    }

    override suspend fun getDoseOnce(id: Long): Dose? {
        return doseDao.getDoseWithMedicationById(id)?.toDomain()
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

    override suspend fun getEnabledSchedules(): Data<List<ReminderSchedule>> {
        return try {
            Data.Success(scheduleDao.getAllEnabledSchedules().map { it.toDomain() })
        } catch (e: Exception) {
            Data.Error("Failed to fetch schedules", e)
        }
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
}
```

- [ ] **Step 15: `DoseReminderScheduler` becomes occurrence-based**

Replace the whole file:

```kotlin
package com.yhdista.dosetracker.reminder

import kotlinx.datetime.Instant

interface DoseReminderScheduler {
    fun scheduleReminder(doseId: Long, at: Instant)
    fun cancelReminder(doseId: Long)
}
```

- [ ] **Step 16: `ReminderScheduler` (`:app`) implements the new interface**

Replace the whole file:

```kotlin
package com.yhdista.dosetracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.datetime.Instant

class ReminderScheduler(
    private val context: Context
) : DoseReminderScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleReminder(doseId: Long, at: Instant) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("doseId", doseId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtMillis = at.toEpochMilliseconds()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    override fun cancelReminder(doseId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
```

- [ ] **Step 17: `ReminderReceiver` resolves the dose by id**

Replace the whole file:

```kotlin
package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra("doseId", -1L)
        if (doseId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.get()
                val repository = koin.get<MedicationRepository>()
                val dose = repository.getDoseOnce(doseId) ?: return@launch
                val medication = repository.getMedicationOnce(dose.medicationId) ?: return@launch

                koin.get<NotificationHelper>().showNotification(
                    doseId = doseId,
                    medicationName = medication.name,
                    dosage = "${dose.amount ?: medication.dosage} ${dose.unit ?: medication.unit}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 18: `NotificationHelper.showNotification` keys off `doseId`**

Modify `NotificationHelper.kt` — replace the `showNotification` function (lines 42-65) with:

```kotlin
    fun showNotification(doseId: Long, medicationName: String, dosage: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("doseId", doseId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            doseId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Medication Reminder")
            .setContentText("It's time to take your $medicationName ($dosage)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(doseId.toInt(), notification)
    }
```

- [ ] **Step 19: `RescheduleWorker` re-arms today's still-PENDING doses**

Replace the whole file (this is superseded by `DoseGenerator` in Task 4, but must compile and behave sensibly on its own until then):

```kotlin
package com.yhdista.dosetracker.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class RescheduleWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: MedicationRepository,
    private val scheduler: DoseReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val dosesResult = repository.getDosesForDate(today)
            .filter { it !is Data.Loading }
            .first()

        if (dosesResult is Data.Success) {
            val now = Clock.System.now()
            dosesResult.data
                .filter { it.status == DoseStatus.PENDING && it.timestamp > now }
                .forEach { dose -> scheduler.scheduleReminder(dose.id, dose.timestamp) }
        }

        return Result.success()
    }
}
```

- [ ] **Step 20: DI wiring**

Modify `shared/src/androidMain/kotlin/com/yhdista/dosetracker/di/DataModule.kt` — replace the whole file:

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
    single<MedicationRepository> {
        MedicationRepositoryImpl(
            medicationDao = get(),
            doseDao = get(),
            scheduleDao = get()
        )
    }
}
```

`app/src/main/java/com/yhdista/dosetracker/di/AppModule.kt` is unaffected by this task (still wires `DoseReminderScheduler`/`NotificationHelper`/`RescheduleWorker` — the `RescheduleWorker` constructor shape hasn't changed count-of-params, so `worker { params -> RescheduleWorker(params.get(), params.get(), get(), get()) }` still resolves correctly). No change needed here in this task.

- [ ] **Step 21: Catalog UI drops the frequency field**

Modify `MedicationCatalogViewModel.kt` — replace `CatalogEvent.AddMedication` and `addMedication`:

```kotlin
sealed interface CatalogEvent {
    data class Search(val query: String) : CatalogEvent
    data class AddMedication(
        val name: String,
        val dosage: String,
        val unit: String
    ) : CatalogEvent
}
```

```kotlin
    private fun addMedication(event: CatalogEvent.AddMedication) {
        viewModelScope.launch {
            val dosageValue = event.dosage.toDoubleOrNull() ?: 0.0
            repository.insertMedication(
                Medication(
                    name = event.name,
                    dosage = dosageValue,
                    unit = event.unit
                )
            )
        }
    }
```

Modify `MedicationCatalogScreen.kt`:

- `AddMedicationDialog`'s `onConfirm` parameter becomes `(String, String, String) -> Unit`, the `frequency` local `var` and its `OutlinedTextField` are removed, and its call becomes `onConfirm(name, dosage, unit)`.
- The call site inside `MedicationCatalogScreen` becomes:

```kotlin
            if (showAddDialog) {
                AddMedicationDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { name, dosage, unit ->
                        viewModel.onEvent(CatalogEvent.AddMedication(name, dosage, unit))
                        showAddDialog = false
                    }
                )
            }
```

- `MedicationItem` drops `frequency` from its supporting text and gains a "manage reminders" trailing action:

```kotlin
@Composable
fun MedicationItem(
    medication: Medication,
    onClick: () -> Unit,
    onManageReminders: () -> Unit
) {
    ListItem(
        headlineContent = { Text(medication.name) },
        supportingContent = { Text("${medication.dosage} ${medication.unit}") },
        trailingContent = {
            IconButton(onClick = onManageReminders) {
                Icon(Icons.Rounded.Schedule, contentDescription = "Manage reminders")
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
```

Add `import androidx.compose.material.icons.rounded.Schedule` to `MedicationCatalogScreen.kt`. The `fun MedicationCatalogScreen(...)` signature gains a new parameter, and its `LazyColumn` call site passes it through:

```kotlin
@Composable
fun MedicationCatalogScreen(
    viewModel: MedicationCatalogViewModel,
    onMedicationClick: (Long) -> Unit,
    onManageRemindersClick: (Long) -> Unit
) {
```

```kotlin
                            items(medications) { medication ->
                                MedicationItem(
                                    medication = medication,
                                    onClick = { onMedicationClick(medication.id) },
                                    onManageReminders = { onManageRemindersClick(medication.id) }
                                )
                                HorizontalDivider()
                            }
```

- [ ] **Step 22: Wire the new callback in `DoseTrackerAppMain`**

Modify `DoseTrackerAppMain.kt` — the `Destination.Medications` branch's `MedicationCatalogScreen(...)` call gains the new callback:

```kotlin
                is Destination.Medications -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        MedicationCatalogScreen(
                            viewModel = koinViewModel<MedicationCatalogViewModel>(),
                            onMedicationClick = { id ->
                                backstack.add(Destination.AddDose(id))
                            },
                            onManageRemindersClick = { id ->
                                backstack.add(Destination.MedicationDetail(id))
                            }
                        )
                    }
                }
```

(`Destination.MedicationDetail` still routes to `MedicationDetailPlaceholder` at this point — that's replaced in Task 5.)

- [ ] **Step 23: Verify the whole app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 24: Manual smoke test**

Install and launch the app (fresh DB, since schema version bumped): confirm the seeded medications show in the catalog without a "frequency" label, confirm "Add Medication" still works with just name/dosage/unit, confirm the app doesn't crash on any screen. No reminder notifications are expected yet — nothing generates `ReminderSchedule` rows through the UI until Task 5.

- [ ] **Step 25: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/ReminderSchedule.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/ReminderScheduleEntity.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/ReminderScheduleDao.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/ReminderScheduleMapper.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/Medication.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/Dose.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/MedicationEntity.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/DoseEntity.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/MedicationMapper.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/DoseMapper.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/DoseDao.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/AppDatabase.kt \
        shared/src/androidMain/kotlin/com/yhdista/dosetracker/data/local/DatabaseBuilder.android.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseReminderScheduler.kt \
        app/src/main/java/com/yhdista/dosetracker/reminder/ReminderScheduler.kt \
        app/src/main/java/com/yhdista/dosetracker/reminder/ReminderReceiver.kt \
        app/src/main/java/com/yhdista/dosetracker/reminder/NotificationHelper.kt \
        app/src/main/java/com/yhdista/dosetracker/reminder/RescheduleWorker.kt \
        shared/src/androidMain/kotlin/com/yhdista/dosetracker/di/DataModule.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/catalog/MedicationCatalogScreen.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/catalog/MedicationCatalogViewModel.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt
git commit -m "feat: replace flat medication reminder fields with ReminderSchedule (DB v2)"
```

---

### Task 3: `DoseGenerator`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseGenerator.kt`
- Test: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/reminder/DoseGeneratorTest.kt`

**Interfaces:**
- Consumes: `MedicationRepository.getEnabledSchedules()`, `.getDoseForSchedule()`, `.getMedicationOnce()`, `.insertDose()` (Task 2); `DoseReminderScheduler.scheduleReminder(doseId, at)` (Task 2); `WeekDays.contains()` (Task 1).
- Produces: `DoseGenerator(repository, scheduler).runForToday()`, `.runForDate(date: LocalDate)` — consumed by `RescheduleWorker` (Task 4) and `MedicationDetailViewModel` (Task 5).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DoseGeneratorTest {

    private val repository = mock<MedicationRepository>()
    private val scheduler = mock<DoseReminderScheduler>()
    private val generator = DoseGenerator(repository, scheduler)
    private val testDispatcher = StandardTestDispatcher()

    private val today = LocalDate(2026, 7, 20)
    private val farFutureDate = LocalDate(2099, 1, 5)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `creates a PENDING dose for a schedule matching today's weekday`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek))
        )
        val expectedInstant = today.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getDoseForSchedule(1, expectedInstant)).thenReturn(null)
        whenever(repository.getMedicationOnce(10)).thenReturn(Medication(id = 10, name = "Aspirin", dosage = 100.0, unit = "mg"))
        whenever(repository.insertDose(any())).thenReturn(Data.Success(99L))

        generator.runForDate(today)

        verify(repository).insertDose(
            Dose(medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        )
    }

    @Test
    fun `skips a schedule that does not include today's weekday`() = runTest {
        val otherDay = DayOfWeek.entries.first { it != today.dayOfWeek }
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(otherDay))
        )
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))

        generator.runForDate(today)

        verify(repository, never()).insertDose(any())
    }

    @Test
    fun `does not duplicate a dose that already exists for this schedule and time`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(today.dayOfWeek))
        )
        val expectedInstant = today.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        val existing = Dose(id = 5, medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.TAKEN)
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getDoseForSchedule(1, expectedInstant)).thenReturn(existing)

        generator.runForDate(today)

        verify(repository, never()).insertDose(any())
    }

    @Test
    fun `re-arms the reminder alarm for an already-existing PENDING dose in the future`() = runTest {
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(farFutureDate.dayOfWeek))
        )
        val expectedInstant = farFutureDate.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        val existing = Dose(id = 5, medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getDoseForSchedule(1, expectedInstant)).thenReturn(existing)

        generator.runForDate(farFutureDate)

        verify(scheduler).scheduleReminder(5, expectedInstant)
    }

    @Test
    fun `does not re-arm a PENDING dose already in the past`() = runTest {
        val pastDate = LocalDate(2020, 1, 1)
        val schedule = ReminderSchedule(
            id = 1, medicationId = 10, minutesOfDay = 480,
            daysOfWeek = WeekDays.toBitmask(setOf(pastDate.dayOfWeek))
        )
        val expectedInstant = pastDate.atTime(8, 0).toInstant(TimeZone.currentSystemDefault())
        val existing = Dose(id = 5, medicationId = 10, scheduleId = 1, timestamp = expectedInstant, amount = 100.0, unit = "mg", status = DoseStatus.PENDING)
        whenever(repository.getEnabledSchedules()).thenReturn(Data.Success(listOf(schedule)))
        whenever(repository.getDoseForSchedule(1, expectedInstant)).thenReturn(existing)

        generator.runForDate(pastDate)

        verify(scheduler, never()).scheduleReminder(any(), any())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.reminder.DoseGeneratorTest"`
Expected: FAIL (compile error — `DoseGenerator` does not exist yet)

- [ ] **Step 3: Write the implementation**

```kotlin
package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant

class DoseGenerator(
    private val repository: MedicationRepository,
    private val scheduler: DoseReminderScheduler
) {
    suspend fun runForToday() {
        runForDate(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }

    suspend fun runForDate(date: LocalDate) {
        val schedules = (repository.getEnabledSchedules() as? Data.Success)?.data ?: return
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()

        for (schedule in schedules) {
            if (!WeekDays.contains(schedule.daysOfWeek, date.dayOfWeek)) continue

            val hour = schedule.minutesOfDay / 60
            val minute = schedule.minutesOfDay % 60
            val scheduledInstant = date.atTime(hour, minute).toInstant(zone)

            val dose = repository.getDoseForSchedule(schedule.id, scheduledInstant)
                ?: createDose(schedule.medicationId, schedule.id, scheduledInstant)
                ?: continue

            if (dose.status == DoseStatus.PENDING && scheduledInstant > now) {
                scheduler.scheduleReminder(dose.id, scheduledInstant)
            }
        }
    }

    private suspend fun createDose(medicationId: Long, scheduleId: Long, at: Instant): Dose? {
        val medication = repository.getMedicationOnce(medicationId) ?: return null
        val dose = Dose(
            medicationId = medicationId,
            scheduleId = scheduleId,
            timestamp = at,
            amount = medication.dosage,
            unit = medication.unit,
            status = DoseStatus.PENDING
        )
        val id = (repository.insertDose(dose) as? Data.Success)?.data ?: return null
        return dose.copy(id = id)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.reminder.DoseGeneratorTest"`
Expected: BUILD SUCCESSFUL, 5 tests passed

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseGenerator.kt \
        shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/reminder/DoseGeneratorTest.kt
git commit -m "feat: add DoseGenerator to materialize schedule occurrences into Dose rows"
```

---

### Task 4: Wire `DoseGenerator` into boot, daily-midnight, and manual triggers

**Files:**
- Modify: `app/src/main/java/com/yhdista/dosetracker/reminder/RescheduleWorker.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/di/AppModule.kt`
- Modify: `shared/src/androidMain/kotlin/com/yhdista/dosetracker/di/DataModule.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/DoseTrackerApp.kt`

**Interfaces:**
- Consumes: `DoseGenerator.runForToday()` (Task 3).

- [ ] **Step 1: `RescheduleWorker` now just delegates to `DoseGenerator`**

Replace the whole file:

```kotlin
package com.yhdista.dosetracker.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters

class RescheduleWorker(
    context: Context,
    params: WorkerParameters,
    private val doseGenerator: DoseGenerator
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        doseGenerator.runForToday()
        return Result.success()
    }
}
```

- [ ] **Step 2: Register `DoseGenerator` in Koin and update the `worker { }` wiring**

Modify `shared/src/androidMain/kotlin/com/yhdista/dosetracker/di/DataModule.kt` — add one line inside the `module { ... }` block (after the `single<MedicationRepository> { ... }` block):

```kotlin
    single { com.yhdista.dosetracker.reminder.DoseGenerator(get(), get()) }
```

Modify `app/src/main/java/com/yhdista/dosetracker/di/AppModule.kt` — update the `worker { }` block (now 3 constructor args instead of 4):

```kotlin
    worker { params ->
        com.yhdista.dosetracker.reminder.RescheduleWorker(
            params.get(),
            params.get(),
            get()
        )
    }
```

- [ ] **Step 3: Enqueue a daily midnight run alongside the existing boot trigger**

Modify `DoseTrackerApp.kt` — replace the whole file:

```kotlin
package com.yhdista.dosetracker

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yhdista.dosetracker.di.appModule
import com.yhdista.dosetracker.di.dataModule
import com.yhdista.dosetracker.di.viewModelModule
import com.yhdista.dosetracker.reminder.RescheduleWorker
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class DoseTrackerApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DoseTrackerApp)
            workManagerFactory()
            modules(appModule, dataModule, viewModelModule)
        }
        scheduleDailyDoseGeneration(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(KoinWorkerFactory())
            .build()
}

private fun scheduleDailyDoseGeneration(context: Context) {
    val request = PeriodicWorkRequestBuilder<RescheduleWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(millisUntilNextMidnight(), TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "daily-dose-generation",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

private fun millisUntilNextMidnight(): Long {
    val zone = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(zone).date
    val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    return (nextMidnight - now).inWholeMilliseconds
}
```

- [ ] **Step 4: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yhdista/dosetracker/reminder/RescheduleWorker.kt \
        app/src/main/java/com/yhdista/dosetracker/di/AppModule.kt \
        shared/src/androidMain/kotlin/com/yhdista/dosetracker/di/DataModule.kt \
        app/src/main/java/com/yhdista/dosetracker/DoseTrackerApp.kt
git commit -m "feat: run DoseGenerator on boot, app upgrade, and daily at midnight"
```

---

### Task 5: Medication detail screen — reminder schedule add/delete

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/medicationdetail/MedicationDetailViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/medicationdetail/MedicationDetailScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt`

**Interfaces:**
- Consumes: `MedicationRepository.getSchedulesForMedication()`, `.insertSchedule()`, `.deleteSchedule()`, `.getDosesForMedication()` (Task 2); `DoseReminderScheduler.cancelReminder()` (Task 2); `DoseGenerator.runForToday()` (Task 3); `WeekDays.toBitmask()`/`fromBitmask()` (Task 1).

- [ ] **Step 1: `MedicationDetailViewModel`**

```kotlin
package com.yhdista.dosetracker.ui.medicationdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import com.yhdista.dosetracker.reminder.DoseReminderScheduler
import com.yhdista.dosetracker.reminder.WeekDays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

data class MedicationDetailState(
    val medication: Data<Medication> = Data.Loading,
    val schedules: Data<List<ReminderSchedule>> = Data.Loading
)

sealed interface MedicationDetailEvent {
    data class AddSchedule(val minutesOfDay: Int, val daysOfWeek: Set<DayOfWeek>) : MedicationDetailEvent
    data class DeleteSchedule(val schedule: ReminderSchedule) : MedicationDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationDetailViewModel(
    private val repository: MedicationRepository,
    private val scheduler: DoseReminderScheduler,
    private val doseGenerator: DoseGenerator,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val medicationIdFlow = savedStateHandle.getStateFlow<Long?>("medicationId", null)

    val uiState: StateFlow<MedicationDetailState> = medicationIdFlow
        .filterNotNull()
        .flatMapLatest { id ->
            combine(
                repository.getMedicationById(id),
                repository.getSchedulesForMedication(id)
            ) { medication, schedules -> MedicationDetailState(medication, schedules) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MedicationDetailState()
        )

    fun setMedicationId(id: Long) {
        if (savedStateHandle.get<Long>("medicationId") == null) {
            savedStateHandle["medicationId"] = id
        }
    }

    fun onEvent(event: MedicationDetailEvent) {
        when (event) {
            is MedicationDetailEvent.AddSchedule -> addSchedule(event)
            is MedicationDetailEvent.DeleteSchedule -> deleteSchedule(event.schedule)
        }
    }

    private fun addSchedule(event: MedicationDetailEvent.AddSchedule) {
        val medicationId = savedStateHandle.get<Long>("medicationId") ?: return
        viewModelScope.launch {
            repository.insertSchedule(
                ReminderSchedule(
                    medicationId = medicationId,
                    minutesOfDay = event.minutesOfDay,
                    daysOfWeek = WeekDays.toBitmask(event.daysOfWeek)
                )
            )
            doseGenerator.runForToday()
        }
    }

    private fun deleteSchedule(schedule: ReminderSchedule) {
        viewModelScope.launch {
            val doses = repository.getDosesForMedication(schedule.medicationId).first { it !is Data.Loading }
            if (doses is Data.Success) {
                doses.data
                    .filter { it.scheduleId == schedule.id && it.status == DoseStatus.PENDING }
                    .forEach { scheduler.cancelReminder(it.id) }
            }
            repository.deleteSchedule(schedule)
        }
    }
}
```

- [ ] **Step 2: `MedicationDetailScreen`**

```kotlin
package com.yhdista.dosetracker.ui.medicationdetail

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
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.reminder.WeekDays
import kotlinx.datetime.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MedicationDetailScreen(
    medicationId: Long,
    viewModel: MedicationDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(medicationId) {
        viewModel.setMedicationId(medicationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state.medication as? Data.Success)?.data?.name ?: "Medication") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add reminder")
            }
        }
    ) { padding ->
        if (showAddDialog) {
            AddScheduleDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { minutesOfDay, days ->
                    viewModel.onEvent(MedicationDetailEvent.AddSchedule(minutesOfDay, days))
                    showAddDialog = false
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
                    Text("Error: ${result.message}")
                }
            }
            is Data.Success -> {
                if (result.data.isEmpty()) {
                    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No reminders yet")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(result.data, key = { it.id }) { schedule ->
                            ScheduleRow(
                                schedule = schedule,
                                onDelete = { viewModel.onEvent(MedicationDetailEvent.DeleteSchedule(schedule)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(schedule: ReminderSchedule, onDelete: () -> Unit) {
    val hour = schedule.minutesOfDay / 60
    val minute = schedule.minutesOfDay % 60
    val days = WeekDays.fromBitmask(schedule.daysOfWeek)
    val daysLabel = if (days.size == 7) "Every day" else days.joinToString { it.name.take(3) }

    ListItem(
        headlineContent = { Text("%02d:%02d".format(hour, minute)) },
        supportingContent = { Text(daysLabel) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete reminder")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (minutesOfDay: Int, days: Set<DayOfWeek>) -> Unit
) {
    val timePickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
    var selectedDays by remember { mutableStateOf(DayOfWeek.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimePicker(state = timePickerState)
                Text("Days", style = MaterialTheme.typography.labelLarge)
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(timePickerState.hour * 60 + timePickerState.minute, selectedDays) },
                enabled = selectedDays.isNotEmpty()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

- [ ] **Step 3: Wire the real screen into `DoseTrackerAppMain`, remove the placeholder**

Modify `DoseTrackerAppMain.kt`:

- Remove the `MedicationDetailPlaceholder` function entirely.
- Remove the now-unused imports for it (`Icons.AutoMirrored.Rounded.ArrowBack`, `TopAppBar`, `IconButton` stay — they're still used by the real screen's own file — but the `Box`/`Alignment` imports in `DoseTrackerAppMain.kt` may become unused; leave them only if still referenced elsewhere in the file, remove otherwise).
- Add `import com.yhdista.dosetracker.ui.medicationdetail.MedicationDetailScreen` and `import com.yhdista.dosetracker.ui.medicationdetail.MedicationDetailViewModel`.
- Replace the `Destination.MedicationDetail` branch:

```kotlin
                is Destination.MedicationDetail -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        MedicationDetailScreen(
                            medicationId = destination.id,
                            viewModel = koinViewModel<MedicationDetailViewModel>(),
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
```

Add `viewModel { MedicationDetailViewModel(get(), get(), get(), get()) }` to `shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt`.

- [ ] **Step 4: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Manual smoke test**

Launch the app, open a medication from the catalog's "manage reminders" icon, add a reminder for a couple of minutes from now covering today's weekday, confirm it appears in the list, confirm it shows up as a `PENDING` entry on the Today screen, wait for the reminder time and confirm the (still plain, no actions yet) notification fires. Delete the reminder and confirm no further notification fires for it.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/medicationdetail/ \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt
git commit -m "feat: add medication detail screen for reminder schedule add/delete"
```

---

### Task 6: Auto-missed timeout

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseReminderScheduler.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseGenerator.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/reminder/ReminderScheduler.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/reminder/NotificationHelper.kt`
- Create: `app/src/main/java/com/yhdista/dosetracker/reminder/MissedDoseReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `DoseReminderScheduler.scheduleMissedTimeout(doseId, at)`, `.cancelMissedTimeout(doseId)`, `DoseReminderScheduler.MISSED_TIMEOUT_MINUTES` — consumed by `DoseActionReceiver` (Task 7).

- [ ] **Step 1: Extend `DoseReminderScheduler`**

Replace the whole file:

```kotlin
package com.yhdista.dosetracker.reminder

import kotlinx.datetime.Instant

interface DoseReminderScheduler {
    fun scheduleReminder(doseId: Long, at: Instant)
    fun cancelReminder(doseId: Long)
    fun scheduleMissedTimeout(doseId: Long, at: Instant)
    fun cancelMissedTimeout(doseId: Long)

    companion object {
        const val MISSED_TIMEOUT_MINUTES = 120
    }
}
```

- [ ] **Step 2: `DoseGenerator` also arms the timeout alarm**

Modify `DoseGenerator.kt` — add `import kotlin.time.Duration.Companion.minutes` and change the arming block inside `runForDate`:

```kotlin
            if (dose.status == DoseStatus.PENDING && scheduledInstant > now) {
                scheduler.scheduleReminder(dose.id, scheduledInstant)
                scheduler.scheduleMissedTimeout(
                    dose.id,
                    scheduledInstant + DoseReminderScheduler.MISSED_TIMEOUT_MINUTES.minutes
                )
            }
```

- [ ] **Step 3: `ReminderScheduler` implements the two new methods**

Modify `ReminderScheduler.kt` — add after `cancelReminder`:

```kotlin
    override fun scheduleMissedTimeout(doseId: Long, at: Instant) {
        val intent = Intent(context, MissedDoseReceiver::class.java).apply {
            putExtra("doseId", doseId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), pendingIntent)
    }

    override fun cancelMissedTimeout(doseId: Long) {
        val intent = Intent(context, MissedDoseReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
```

(Using `MissedDoseReceiver::class.java` as the target — rather than `ReminderReceiver::class.java` — is what keeps this alarm's `PendingIntent` distinct from the reminder alarm even though both use `doseId.toInt()` as the request code.)

- [ ] **Step 4: `NotificationHelper` gains `cancelNotification`**

Modify `NotificationHelper.kt` — add after `showNotification`:

```kotlin
    fun cancelNotification(doseId: Long) {
        notificationManager.cancel(doseId.toInt())
    }
```

- [ ] **Step 5: `MissedDoseReceiver`**

```kotlin
package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class MissedDoseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra("doseId", -1L)
        if (doseId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.get()
                val repository = koin.get<MedicationRepository>()
                val dose = repository.getDoseOnce(doseId)
                if (dose != null && dose.status == DoseStatus.PENDING) {
                    repository.updateDose(dose.copy(status = DoseStatus.MISSED))
                    koin.get<NotificationHelper>().cancelNotification(doseId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 6: Register the receiver**

Modify `AndroidManifest.xml` — add after the existing `ReminderReceiver` `<receiver>` entry:

```xml
        <receiver
            android:name=".reminder.MissedDoseReceiver"
            android:exported="false" />
```

- [ ] **Step 7: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Manual verification**

Since exact-alarm timing isn't practically unit testable, verify manually: temporarily change `MISSED_TIMEOUT_MINUTES` to `1`, add a reminder a couple of minutes out, let it fire and don't touch the notification, wait ~1 more minute, confirm the dose flips to `MISSED` in `TodayScreen`/`HistoryScreen` and the notification is dismissed. Revert the constant back to `120` afterward.

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseReminderScheduler.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseGenerator.kt \
        app/src/main/java/com/yhdista/dosetracker/reminder/ReminderScheduler.kt \
        app/src/main/java/com/yhdista/dosetracker/reminder/NotificationHelper.kt \
        app/src/main/java/com/yhdista/dosetracker/reminder/MissedDoseReceiver.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat: auto-mark a dose MISSED 2h after its scheduled time"
```

---

### Task 7: Notification actions — Taken / Skip / Snooze

**Files:**
- Create: `app/src/main/java/com/yhdista/dosetracker/reminder/DoseActionReceiver.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/reminder/NotificationHelper.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `DoseReminderScheduler.scheduleReminder()`, `.cancelMissedTimeout()` (Tasks 2, 6); `MedicationRepository.getDoseOnce()`, `.updateDose()` (Task 2).

- [ ] **Step 1: `DoseActionReceiver`**

```kotlin
package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class DoseActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TAKEN = "com.yhdista.dosetracker.ACTION_TAKEN"
        const val ACTION_SKIPPED = "com.yhdista.dosetracker.ACTION_SKIPPED"
        const val ACTION_SNOOZE = "com.yhdista.dosetracker.ACTION_SNOOZE"
        const val SNOOZE_MINUTES = 15
    }

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra("doseId", -1L)
        if (doseId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.get()
                val repository = koin.get<MedicationRepository>()
                val scheduler = koin.get<DoseReminderScheduler>()
                val notificationHelper = koin.get<NotificationHelper>()
                val dose = repository.getDoseOnce(doseId) ?: return@launch

                when (intent.action) {
                    ACTION_TAKEN -> {
                        repository.updateDose(dose.copy(status = DoseStatus.TAKEN))
                        scheduler.cancelMissedTimeout(doseId)
                        notificationHelper.cancelNotification(doseId)
                    }
                    ACTION_SKIPPED -> {
                        repository.updateDose(dose.copy(status = DoseStatus.SKIPPED))
                        scheduler.cancelMissedTimeout(doseId)
                        notificationHelper.cancelNotification(doseId)
                    }
                    ACTION_SNOOZE -> {
                        scheduler.scheduleReminder(doseId, Clock.System.now() + SNOOZE_MINUTES.minutes)
                        notificationHelper.cancelNotification(doseId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 2: `NotificationHelper` adds the three actions**

Replace `showNotification` (added in Task 2 Step 18) and add a helper:

```kotlin
    fun showNotification(doseId: Long, medicationName: String, dosage: String) {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("doseId", doseId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            doseId.toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Medication Reminder")
            .setContentText("It's time to take your $medicationName ($dosage)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Taken", doseActionIntent(doseId, DoseActionReceiver.ACTION_TAKEN))
            .addAction(0, "Skip", doseActionIntent(doseId, DoseActionReceiver.ACTION_SKIPPED))
            .addAction(0, "Snooze", doseActionIntent(doseId, DoseActionReceiver.ACTION_SNOOZE))
            .build()

        notificationManager.notify(doseId.toInt(), notification)
    }

    private fun doseActionIntent(doseId: Long, action: String): PendingIntent {
        val intent = Intent(context, DoseActionReceiver::class.java).apply {
            this.action = action
            putExtra("doseId", doseId)
        }
        return PendingIntent.getBroadcast(
            context,
            (doseId.toString() + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
```

- [ ] **Step 3: Register the receiver**

Modify `AndroidManifest.xml` — add after the `MissedDoseReceiver` entry from Task 6:

```xml
        <receiver
            android:name=".reminder.DoseActionReceiver"
            android:exported="false" />
```

- [ ] **Step 4: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Manual verification**

Add a reminder a couple of minutes out. When the notification fires, confirm it shows three actions. Test each on separate reminders: **Taken** — dose flips to TAKEN in `TodayScreen`, notification dismissed. **Skip** — dose flips to SKIPPED, notification dismissed. **Snooze** — notification dismissed, a new notification for the same dose appears ~15 minutes later, dose still PENDING in between.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/yhdista/dosetracker/reminder/DoseActionReceiver.kt \
        app/src/main/java/com/yhdista/dosetracker/reminder/NotificationHelper.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add Taken/Skip/Snooze actions to the reminder notification"
```

---

### Task 8: `ConfirmDoseScreen` — adjust amount/time in-app

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/DoseDao.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/confirm/ConfirmDoseViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/confirm/ConfirmDoseScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/MainActivity.kt`

**Interfaces:**
- Produces: `MedicationRepository.getDoseById(id): Flow<Data<Dose>>`.

- [ ] **Step 1: `DoseDao` gains a reactive by-id query**

Add to `DoseDao.kt`:

```kotlin
    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName
        FROM doses
        INNER JOIN medications ON doses.medicationId = medications.id
        WHERE doses.id = :id
    """)
    fun getDoseWithMedicationByIdFlow(id: Long): Flow<DoseWithMedication?>
```

- [ ] **Step 2: Repository interface + impl**

Add to `MedicationRepository.kt` (inside the interface, near the other `Dose` methods):

```kotlin
    fun getDoseById(id: Long): Flow<Data<Dose>>
```

Add to `MedicationRepositoryImpl.kt`:

```kotlin
    override fun getDoseById(id: Long): Flow<Data<Dose>> {
        return doseDao.getDoseWithMedicationByIdFlow(id)
            .map { entity ->
                if (entity != null) Data.Success(entity.toDomain()) as Data<Dose>
                else Data.Error("Dose not found")
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch dose", e)) }
    }
```

- [ ] **Step 3: `ConfirmDoseViewModel`**

```kotlin
package com.yhdista.dosetracker.ui.confirm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class ConfirmDoseState(
    val dose: Data<Dose> = Data.Loading,
    val amount: String = "",
    val time: LocalDateTime? = null,
    val isSuccess: Boolean = false,
    val error: String? = null
)

sealed interface ConfirmDoseEvent {
    data class UpdateAmount(val amount: String) : ConfirmDoseEvent
    data object Save : ConfirmDoseEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmDoseViewModel(
    private val repository: MedicationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val doseIdFlow = savedStateHandle.getStateFlow<Long?>("doseId", null)

    private val _state = MutableStateFlow(ConfirmDoseState())
    val state = _state.asStateFlow()

    init {
        doseIdFlow
            .filterNotNull()
            .flatMapLatest { id -> repository.getDoseById(id) }
            .onEach { result ->
                _state.update { state ->
                    state.copy(
                        dose = result,
                        amount = if (result is Data.Success && state.amount.isEmpty())
                            result.data.amount?.toString() ?: state.amount
                        else state.amount,
                        time = if (result is Data.Success && state.time == null)
                            result.data.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                        else state.time
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun setDoseId(id: Long) {
        if (savedStateHandle.get<Long>("doseId") == null) {
            savedStateHandle["doseId"] = id
        }
    }

    fun onEvent(event: ConfirmDoseEvent) {
        when (event) {
            is ConfirmDoseEvent.UpdateAmount -> _state.update { it.copy(amount = event.amount) }
            is ConfirmDoseEvent.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        val dose = (current.dose as? Data.Success)?.data ?: return
        val time = current.time ?: return

        viewModelScope.launch {
            val result = repository.updateDose(
                dose.copy(
                    amount = current.amount.toDoubleOrNull(),
                    timestamp = time.toInstant(TimeZone.currentSystemDefault()),
                    status = DoseStatus.TAKEN
                )
            )
            when (result) {
                is Data.Success -> _state.update { it.copy(isSuccess = true) }
                is Data.Error -> _state.update { it.copy(error = result.message) }
                else -> Unit
            }
        }
    }
}
```

- [ ] **Step 4: `ConfirmDoseScreen`**

Mirrors `AddDoseScreen.kt`'s structure exactly (same display-only time text — no time picker widget exists in this codebase yet, matching that existing screen's own limitation):

```kotlin
package com.yhdista.dosetracker.ui.confirm

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import kotlinx.datetime.format
import kotlinx.datetime.format.char

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDoseScreen(
    doseId: Long,
    viewModel: ConfirmDoseViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(doseId) {
        viewModel.setDoseId(doseId)
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm Dose") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val result = state.dose) {
            is Data.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Data.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${result.message}")
                }
            }
            is Data.Success -> {
                Column(
                    modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Medication: ${result.data.medicationName}",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = { viewModel.onEvent(ConfirmDoseEvent.UpdateAmount(it)) },
                        label = { Text("Amount (${result.data.unit ?: ""})") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.time?.let { time ->
                        Text(
                            text = "Time: ${time.format(confirmTimeFormat)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Button(
                        onClick = { viewModel.onEvent(ConfirmDoseEvent.Save) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save")
                    }
                    if (state.error != null) {
                        Text(text = state.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private val confirmTimeFormat = kotlinx.datetime.LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char(' ')
    hour(); char(':'); minute()
}
```

- [ ] **Step 5: New destination**

Modify `Destinations.kt` — add:

```kotlin
    @Serializable
    data class ConfirmDose(val doseId: Long) : Destination
```

- [ ] **Step 6: `TodayScreen` tap opens `ConfirmDoseScreen`**

Modify `TodayScreen.kt`:

- Rename the `onNavigateToDetail: (Long) -> Unit` parameter of `TodayScreen`/`TodayContent` to `onNavigateToConfirm: (Long) -> Unit`.
- In the `items(doses.data, key = { it.id }) { dose -> DoseItem(...) }` block, change `onClick = { onNavigateToDetail(dose.medicationId) }` to `onClick = { onNavigateToConfirm(dose.id) }`.
- Update the `TodayContentPreview` call site's parameter name from `onNavigateToDetail` to `onNavigateToConfirm` accordingly.

- [ ] **Step 7: Wire it all up in `DoseTrackerAppMain`**

Modify `DoseTrackerAppMain.kt`:

- The `Destination.Today` branch's `TodayScreen(...)` call: rename `onNavigateToDetail` to `onNavigateToConfirm`, body becomes `backstack.add(Destination.ConfirmDose(doseId))`.
- Add a new branch:

```kotlin
                is Destination.ConfirmDose -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        ConfirmDoseScreen(
                            doseId = destination.doseId,
                            viewModel = koinViewModel<ConfirmDoseViewModel>(),
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
```

- Add `import com.yhdista.dosetracker.ui.confirm.ConfirmDoseScreen` and `import com.yhdista.dosetracker.ui.confirm.ConfirmDoseViewModel`.
- The `DoseTrackerAppMain` composable gains a parameter and a deep-link effect:

```kotlin
@Composable
fun DoseTrackerAppMain(initialConfirmDoseId: Long? = null) {
    RequestNotificationPermissionEffect()

    val backstack = rememberNavBackStack(Destination.Today)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    LaunchedEffect(initialConfirmDoseId) {
        initialConfirmDoseId?.let { backstack.add(Destination.ConfirmDose(it)) }
    }
```

(Add `import androidx.compose.runtime.LaunchedEffect` if not already present.)

- [ ] **Step 8: DI**

Add `viewModel { ConfirmDoseViewModel(get(), get()) }` to `ViewModelModule.kt`.

- [ ] **Step 9: Notification tap deep-links into `ConfirmDoseScreen`**

Modify `MainActivity.kt` — replace the whole file:

```kotlin
package com.yhdista.dosetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yhdista.dosetracker.ui.app.DoseTrackerAppMain
import com.yhdista.dosetracker.ui.theme.DoseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val doseId = intent.getLongExtra("doseId", -1L).takeIf { it != -1L }
        setContent {
            DoseTrackerTheme {
                DoseTrackerAppMain(initialConfirmDoseId = doseId)
            }
        }
    }
}
```

(`FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` on the notification's `PendingIntent`, set back in Task 2 Step 18 and kept in Task 7, guarantees a fresh `onCreate` on every notification tap — no `onNewIntent` handling needed.)

- [ ] **Step 10: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Manual verification**

Tap a `PENDING` card on `TodayScreen` → `ConfirmDoseScreen` opens pre-filled with the medication's default amount and the scheduled time; edit the amount and hit Save → back on `TodayScreen` the dose shows TAKEN with the edited amount. Separately, let a reminder notification fire and tap its body (not an action button) → app opens directly into `ConfirmDoseScreen` for that dose.

- [ ] **Step 12: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/DoseDao.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/confirm/ \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayScreen.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt \
        app/src/main/java/com/yhdista/dosetracker/MainActivity.kt
git commit -m "feat: add ConfirmDoseScreen for adjusting amount/time, wire notification tap deep link"
```

---

### Task 9: `ReportViewModel` — weekly aggregation

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModel.kt`
- Test: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModelTest.kt`

**Interfaces:**
- Produces: `MedicationRepository.getDosesInWeek(weekStart: LocalDate): Flow<Data<List<Dose>>>`; `ReportViewModel.uiState: StateFlow<ReportState>`, `.onEvent(ReportEvent)` — consumed by `ReportScreen` (Task 10).

- [ ] **Step 1: Repository — week-range query**

Add to `MedicationRepository.kt` (near `getDosesForDate`):

```kotlin
    fun getDosesInWeek(weekStart: LocalDate): Flow<Data<List<Dose>>>
```

Add to `MedicationRepositoryImpl.kt` (needs `import kotlinx.datetime.DateTimeUnit` and `import kotlinx.datetime.plus` added to the file's imports):

```kotlin
    override fun getDosesInWeek(weekStart: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startOfWeek = weekStart.atStartOfDayIn(zone).toEpochMilliseconds()
        val endOfWeek = weekStart.plus(7, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()

        return doseDao.getDosesInTimeRange(startOfWeek, endOfWeek)
            .map { entities -> Data.Success(entities.map { it.toDomain() }) as Data<List<Dose>> }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch doses for week", e)) }
    }
```

- [ ] **Step 2: Write the failing tests for `ReportViewModel`**

```kotlin
package com.yhdista.dosetracker.ui.report

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private val repository = mock<MedicationRepository>()
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
    fun `summarizes counts per medication for the current week`() = runTest {
        val instant = Clock.System.now()
        val doses = listOf(
            Dose(id = 1, medicationId = 1, medicationName = "Aspirin", timestamp = instant, status = DoseStatus.TAKEN),
            Dose(id = 2, medicationId = 1, medicationName = "Aspirin", timestamp = instant, status = DoseStatus.MISSED),
            Dose(id = 3, medicationId = 1, medicationName = "Aspirin", timestamp = instant, status = DoseStatus.TAKEN),
            Dose(id = 4, medicationId = 2, medicationName = "Ibuprofen", timestamp = instant, status = DoseStatus.SKIPPED),
            Dose(id = 5, medicationId = 2, medicationName = "Ibuprofen", timestamp = instant, status = DoseStatus.PENDING)
        )
        whenever(repository.getDosesInWeek(any())).thenReturn(flowOf(Data.Success(doses)))

        val viewModel = ReportViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val summaries = (viewModel.uiState.value.summaries as Data.Success).data
        val aspirin = summaries.first { it.medicationName == "Aspirin" }
        val ibuprofen = summaries.first { it.medicationName == "Ibuprofen" }

        assertEquals(2, aspirin.taken)
        assertEquals(1, aspirin.missed)
        assertEquals(0, aspirin.skipped)
        assertEquals(1, ibuprofen.skipped)
        assertEquals(1, ibuprofen.upcoming)

        job.cancel()
    }

    @Test
    fun `PreviousWeek and NextWeek shift the queried week by 7 days`() = runTest {
        whenever(repository.getDosesInWeek(any())).thenReturn(flowOf(Data.Success(emptyList())))

        val viewModel = ReportViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val initialWeekStart = viewModel.uiState.value.weekStart

        viewModel.onEvent(ReportEvent.NextWeek)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(initialWeekStart.plus(kotlinx.datetime.DatePeriod(days = 7)), viewModel.uiState.value.weekStart)

        viewModel.onEvent(ReportEvent.PreviousWeek)
        viewModel.onEvent(ReportEvent.PreviousWeek)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(initialWeekStart.plus(kotlinx.datetime.DatePeriod(days = -7)), viewModel.uiState.value.weekStart)

        job.cancel()
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.report.ReportViewModelTest"`
Expected: FAIL (compile error — `ReportViewModel` does not exist yet)

- [ ] **Step 4: Write the implementation**

```kotlin
package com.yhdista.dosetracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class MedicationWeekSummary(
    val medicationName: String,
    val taken: Int,
    val missed: Int,
    val skipped: Int,
    val upcoming: Int
)

data class ReportState(
    val weekStart: LocalDate = currentWeekStart(),
    val summaries: Data<List<MedicationWeekSummary>> = Data.Loading
)

sealed interface ReportEvent {
    data object PreviousWeek : ReportEvent
    data object NextWeek : ReportEvent
}

private fun currentWeekStart(): LocalDate {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val daysSinceMonday = today.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber
    return today.minus(daysSinceMonday, DateTimeUnit.DAY)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _weekStart = MutableStateFlow(currentWeekStart())

    val uiState: StateFlow<ReportState> = _weekStart
        .flatMapLatest { weekStart ->
            repository.getDosesInWeek(weekStart).map { result -> weekStart to summarize(result) }
        }
        .map { (weekStart, summaries) -> ReportState(weekStart = weekStart, summaries = summaries) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReportState()
        )

    fun onEvent(event: ReportEvent) {
        when (event) {
            is ReportEvent.PreviousWeek -> _weekStart.update { it.minus(7, DateTimeUnit.DAY) }
            is ReportEvent.NextWeek -> _weekStart.update { it.plus(7, DateTimeUnit.DAY) }
        }
    }

    private fun summarize(result: Data<List<Dose>>): Data<List<MedicationWeekSummary>> {
        return when (result) {
            is Data.Success -> Data.Success(
                result.data
                    .groupBy { it.medicationName }
                    .map { (name, doses) ->
                        MedicationWeekSummary(
                            medicationName = name,
                            taken = doses.count { it.status == DoseStatus.TAKEN },
                            missed = doses.count { it.status == DoseStatus.MISSED },
                            skipped = doses.count { it.status == DoseStatus.SKIPPED },
                            upcoming = doses.count { it.status == DoseStatus.PENDING }
                        )
                    }
            )
            is Data.Error -> Data.Error(result.message)
            is Data.Loading -> Data.Loading
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.report.ReportViewModelTest"`
Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 6: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModel.kt \
        shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/report/ReportViewModelTest.kt
git commit -m "feat: add ReportViewModel with per-medication weekly adherence counts"
```

---

### Task 10: `ReportScreen` + navigation entry

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt`
- Modify: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt`

**Interfaces:**
- Consumes: `ReportViewModel.uiState`, `.onEvent()` (Task 9).

- [ ] **Step 1: `ReportScreen`**

```kotlin
package com.yhdista.dosetracker.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Weekly Report", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.onEvent(ReportEvent.PreviousWeek) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous week")
                }
                Text(
                    text = "${state.weekStart.format(weekLabelFormat)} - " +
                        "${state.weekStart.plus(6, DateTimeUnit.DAY).format(weekLabelFormat)}",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { viewModel.onEvent(ReportEvent.NextWeek) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next week")
                }
            }

            when (val result = state.summaries) {
                is Data.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is Data.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${result.message}")
                    }
                }
                is Data.Success -> {
                    if (result.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No doses this week")
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(result.data) { summary ->
                                MedicationSummaryCard(summary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationSummaryCard(summary: MedicationWeekSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(summary.medicationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Taken: ${summary.taken}  Missed: ${summary.missed}  Skipped: ${summary.skipped}")
            if (summary.upcoming > 0) {
                Text(
                    "Upcoming: ${summary.upcoming}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val weekLabelFormat = LocalDate.Format {
    monthNumber(); char('/'); day()
}
```

- [ ] **Step 2: New destination**

Modify `Destinations.kt` — add:

```kotlin
    @Serializable
    data object Report : Destination
```

- [ ] **Step 3: Add the fourth nav bar item and its `NavEntry`**

Modify `DoseTrackerAppMain.kt`:

- Add `import androidx.compose.material.icons.rounded.BarChart`, `import com.yhdista.dosetracker.ui.report.ReportScreen`, `import com.yhdista.dosetracker.ui.report.ReportViewModel`.
- Add a fourth `item(...)` inside `navigationSuiteItems = { ... }`, after the existing History item:

```kotlin
            item(
                selected = backstack.last() is Destination.Report,
                onClick = {
                    if (backstack.last() !is Destination.Report) {
                        backstack.clear()
                        backstack.add(Destination.Report)
                    }
                },
                icon = { Icon(Icons.Rounded.BarChart, contentDescription = "Report") },
                label = { Text("Report") }
            )
```

- Add a matching `when` branch:

```kotlin
                is Destination.Report -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        ReportScreen(viewModel = koinViewModel<ReportViewModel>())
                    }
                }
```

- [ ] **Step 4: DI**

Add `viewModel { ReportViewModel(get()) }` to `ViewModelModule.kt`.

- [ ] **Step 5: Verify the app compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Manual verification — full loop**

Add a medication with two daily reminder times covering today, on weekdays only. Confirm both show as PENDING on `TodayScreen`. Confirm one via the notification's Taken action, adjust the other via `ConfirmDoseScreen`. Open the Report tab and confirm the current week shows the medication with the right Taken/Missed/Skipped counts. Navigate to the previous/next week with the arrows and confirm the displayed date range and data both update.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/report/ReportScreen.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt \
        shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt
git commit -m "feat: add weekly Report screen with previous/next week navigation"
```
