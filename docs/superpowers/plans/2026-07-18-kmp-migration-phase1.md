# DoseTracker KMP Migration Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract a `:shared` Kotlin Multiplatform module (`androidTarget()` only) containing domain, data (Room), UI (Compose), and DI (Koin), so the app keeps working identically on Android while being ready for iOS/Desktop targets in a later phase.

**Architecture:** Single `:shared` KMP module (mirrors the low-granularity philosophy already used by the sibling VLACHOVI_CZ project's own `:shared` module) holding everything platform-agnostic; `:app` becomes a thin Android shell (MainActivity, Application, WorkManager/NotificationManager-based reminder implementation, manifest). Hilt is replaced by Koin throughout (Hilt cannot run outside Android/JVM, and DI must work in future iOS/Desktop targets). Room 2.8.4's official multiplatform support (`@ConstructedBy` + KSP-generated per-target constructor) keeps the database class in commonMain.

**Tech Stack:** Kotlin 2.4.10, AGP 9.3.0, Room 2.8.4 (multiplatform mode), Koin 4.1.0 (core + androidx-compose + androidx-workmanager), kotlinx-datetime 0.8.0, Compose Multiplatform (org.jetbrains.compose) matching the existing Compose BOM 2026.06.01 generation.

## Global Constraints

- Package names do NOT change when files move to `:shared` — e.g. `com.yhdista.dosetracker.domain.model.Dose` stays `com.yhdista.dosetracker.domain.model.Dose`, just physically relocated to `shared/src/commonMain/kotlin/...`. Only the Gradle module changes, not the Kotlin package.
- `:shared` Android namespace (AGP R-class only, no bearing on Kotlin package names): `com.yhdista.dosetracker.shared`.
- `:app` keeps `applicationId`/namespace `com.yhdista.dosetracker` — unchanged, no `.android` suffix.
- Every `java.time.*` type crossing into `:shared` commonMain is replaced with its `kotlinx-datetime` equivalent: `java.time.Instant` → `kotlinx.datetime.Instant`, `java.time.LocalDate` → `kotlinx.datetime.LocalDate`, `java.time.LocalDateTime` → `kotlinx.datetime.LocalDateTime`. `:app`'s Android-only reminder code (`ReminderScheduler`, `NotificationHelper`) may keep using `java.time.*` internally since that module stays JVM/Android-only forever — it just converts at the boundary where it talks to the shared `DoseReminderScheduler` interface.
- Every existing Hilt annotation (`@HiltViewModel`, `@Inject`, `@Module`, `@InstallIn`, `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltWorker`/`@AssistedInject`) is removed and replaced with the Koin equivalent described in each task. No Hilt dependency remains in the final `app/build.gradle.kts`.
- The `distribute` Gradle task and `firebaseAppDistribution` block (already present in `app/build.gradle.kts`) are not touched by any task in this plan.
- After every task, `./gradlew :app:assembleDebug` must succeed before moving to the next task.

---

## File Structure Overview

New module:
```
shared/
  build.gradle.kts
  src/
    commonMain/kotlin/com/yhdista/dosetracker/
      core/Data.kt, core/Resettable.kt
      domain/model/Dose.kt, domain/model/Medication.kt
      domain/repository/MedicationRepository.kt
      data/local/AppDatabase.kt, data/local/Converters.kt, data/local/DatabaseBuilder.kt
      data/local/dao/DoseDao.kt, data/local/dao/MedicationDao.kt
      data/local/entity/DoseEntity.kt, data/local/entity/MedicationEntity.kt
      data/mapper/DoseMapper.kt, data/mapper/MedicationMapper.kt
      data/repository/MedicationRepositoryImpl.kt
      reminder/DoseReminderScheduler.kt
      ui/theme/Color.kt, ui/theme/Type.kt, ui/theme/Theme.kt (expect)
      ui/navigation/Destinations.kt
      ui/catalog/MedicationCatalogScreen.kt, ui/catalog/MedicationCatalogViewModel.kt
      ui/dose/AddDoseScreen.kt, ui/dose/AddDoseViewModel.kt
      ui/history/HistoryScreen.kt, ui/history/HistoryViewModel.kt
      ui/today/TodayScreen.kt, ui/today/TodayViewModel.kt
      ui/app/DoseTrackerAppMain.kt
    androidMain/kotlin/com/yhdista/dosetracker/
      data/local/DatabaseBuilder.android.kt
      di/DataModule.kt
      ui/theme/Theme.android.kt (actual)
      ui/app/NotificationPermission.android.kt
    commonMain/kotlin/com/yhdista/dosetracker/ui/app/NotificationPermission.kt (expect)
    androidHostTest/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt
```

Modified `:app`:
```
app/
  build.gradle.kts            (drop Hilt/Room/Moshi deps, add :shared, keep firebase-appdistribution)
  src/main/java/com/yhdista/dosetracker/
    MainActivity.kt            (slimmed down, calls into :shared)
    DoseTrackerApp.kt          (startKoin instead of Hilt)
    reminder/ReminderScheduler.kt   (implements shared DoseReminderScheduler)
    reminder/NotificationHelper.kt  (unchanged, still Android-only)
    reminder/ReminderReceiver.kt    (Koin instead of Hilt injection)
    reminder/RescheduleReceiver.kt  (unchanged)
    reminder/RescheduleWorker.kt    (Koin worker instead of HiltWorker)
    di/                         (deleted entirely — moved to :shared/di/DataModule.kt + app-side Koin modules)
```

---

### Task 1: Scaffold the `:shared` KMP module

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Create: `shared/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/placeholder/Placeholder.kt` (deleted again in Task 2 — just proves the module compiles)

**Interfaces:**
- Produces: a `:shared` Gradle module other tasks add files to, with `kotlin.multiplatform`, `com.android.kotlin.multiplatform.library`, `org.jetbrains.compose`, `org.jetbrains.kotlin.plugin.compose`, `com.google.devtools.ksp` plugins applied, and an Android target configured via the `kotlin { android { } }` block.

**Note on plugin choice (discovered during implementation, not in the original design):** AGP 9.0+ no longer allows `com.android.library` to be applied together with `org.jetbrains.kotlin.multiplatform` in the same module — it throws `IllegalStateException: The 'com.android.library' ... plugin is not compatible with the 'org.jetbrains.kotlin.multiplatform' plugin since AGP 9.0`, recommending the dedicated `com.android.kotlin.multiplatform.library` plugin instead. That plugin requires Compose Multiplatform 1.9.3+ (this plan uses 1.11.1, the current stable release compatible with Kotlin 2.4.10) and has a different DSL: Android configuration moves from a separate top-level `android { }` block into an `android { }` block *nested inside* `kotlin { }`, there is no `androidTarget { }` call (the nested `android { }` block both declares and configures the target), build types/product flavors are not supported (single-variant only — fine for this project, which will only ever ship one Android variant of `:shared`), and the JVM unit test source set is named `androidHostTest` (not `androidUnitTest`) and must be explicitly opted into via `withHostTestBuilder {}.configure {}`. The steps below reflect this corrected setup.

- [ ] **Step 1: Add version catalog entries**

In `gradle/libs.versions.toml`, add to `[versions]` (after `hiltWork = "1.4.0"`):

```toml
kotlinxDatetime = "0.8.0"
koin = "4.1.0"
composeMultiplatform = "1.11.1"
```

Add to `[libraries]` (after `hilt-compiler = ...`):

```toml
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }
koin-core = { group = "io.insert-koin", name = "koin-core", version.ref = "koin" }
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-androidx-workmanager = { group = "io.insert-koin", name = "koin-androidx-workmanager", version.ref = "koin" }
koin-compose = { group = "io.insert-koin", name = "koin-compose", version.ref = "koin" }
koin-compose-viewmodel = { group = "io.insert-koin", name = "koin-compose-viewmodel", version.ref = "koin" }
```

(No `koin-bom` — Koin's BOM can't be applied inside the Kotlin Multiplatform `sourceSets { commonMain.dependencies { } }` DSL, since `platform()` isn't available on that receiver. Pinning each Koin artifact directly to the same `koin` version is simpler and equivalent here.)

Add to `[plugins]` (after `hilt = ...`):

```toml
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
android-kotlin-multiplatform-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
jetbrains-compose = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
```

- [ ] **Step 2: Register the new plugins in the root build file**

In `build.gradle.kts` (root), add to the `plugins { }` block:

```kotlin
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.jetbrains.compose) apply false
```

- [ ] **Step 3: Include the module**

In `settings.gradle.kts`, change:

```kotlin
rootProject.name = "DoseTracker"
include(":app")
```

to:

```kotlin
rootProject.name = "DoseTracker"
include(":app")
include(":shared")
```

- [ ] **Step 4: Create `shared/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.yhdista.dosetracker.shared"
        compileSdk = 37
        minSdk = 28

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }

        androidResources {
            enable = true
        }

        withHostTestBuilder {}.configure {}
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            implementation(libs.androidx.room.runtime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.accompanist.permissions)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockito.kotlin)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
}
```

Note: `compose.runtime`/`compose.foundation`/`compose.material3`/`compose.materialIconsExtended`/`compose.ui`/`compose.components.resources` print Gradle deprecation warnings on this Compose Multiplatform version ("Specify dependency directly") — they still work and are not build errors; leave them as-is unless a later Compose Multiplatform bump forces a change.

- [ ] **Step 5: Create a placeholder file so the module has source to compile**

`shared/src/commonMain/kotlin/com/yhdista/dosetracker/placeholder/Placeholder.kt`:

```kotlin
package com.yhdista.dosetracker.placeholder

internal const val PLACEHOLDER = true
```

- [ ] **Step 6: Verify the module builds**

Run: `./gradlew :shared:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml build.gradle.kts shared/build.gradle.kts shared/src
git commit -m "feat: scaffold :shared KMP module (androidTarget only)"
```

---

### Task 2: Move `core` package and wire `:app` → `:shared`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/core/Data.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/core/Resettable.kt`
- Delete: `app/src/main/java/com/yhdista/dosetracker/core/Data.kt`
- Delete: `app/src/main/java/com/yhdista/dosetracker/core/Resettable.kt`
- Delete: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/placeholder/Placeholder.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `com.yhdista.dosetracker.core.Data` (sealed class: `Success<T>`, `Error`, `Loading`), `com.yhdista.dosetracker.core.Resettable` — now available to both `:shared` and `:app`.

- [ ] **Step 1: Move the two files verbatim**

Move `app/src/main/java/com/yhdista/dosetracker/core/Data.kt` to `shared/src/commonMain/kotlin/com/yhdista/dosetracker/core/Data.kt` with identical content:

```kotlin
package com.yhdista.dosetracker.core

/**
 * A generic class that holds a value with its loading status.
 * @param <T>
 */
sealed class Data<out T> {
    data class Success<out T>(val data: T) : Data<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Data<Nothing>()
    object Loading : Data<Nothing>()

    val isSuccess get() = this is Success
    val isError get() = this is Error
    val isLoading get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data
}
```

Move `app/src/main/java/com/yhdista/dosetracker/core/Resettable.kt` to `shared/src/commonMain/kotlin/com/yhdista/dosetracker/core/Resettable.kt` with identical content:

```kotlin
package com.yhdista.dosetracker.core

/**
 * Interface for classes that hold user state and need to be reset.
 */
interface Resettable {
    fun reset()
}
```

Delete the two original files from `app/` and delete `shared/src/commonMain/kotlin/com/yhdista/dosetracker/placeholder/Placeholder.kt`.

- [ ] **Step 2: Add the `:shared` dependency to `:app`**

In `app/build.gradle.kts`, add inside `dependencies { }` (top of the block):

```kotlin
    implementation(project(":shared"))
```

- [ ] **Step 3: Verify `:app` still resolves `Data`/`Resettable` through `:shared`**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` — every existing `import com.yhdista.dosetracker.core.Data` in `:app` now resolves through `:shared`'s compiled artifact.

- [ ] **Step 4: Commit**

```bash
git add shared/src app/src app/build.gradle.kts
git commit -m "refactor: move core package to :shared, wire :app dependency"
```

---

### Task 3: Domain layer + kotlinx-datetime conversion

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/Dose.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/model/Medication.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt`
- Delete: `app/src/main/java/com/yhdista/dosetracker/domain/model/Dose.kt`
- Delete: `app/src/main/java/com/yhdista/dosetracker/domain/model/Medication.kt`
- Delete: `app/src/main/java/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt`

**Interfaces:**
- Consumes: `com.yhdista.dosetracker.core.Data` (Task 2).
- Produces: `Dose(id: Long, medicationId: Long, medicationName: String, timestamp: kotlinx.datetime.Instant, amount: Double?, unit: String?, status: DoseStatus)`, `DoseStatus` enum, `Medication(id, name, dosage, unit, frequency, reminderTime)`, `MedicationRepository` interface with `getDosesForDate(date: kotlinx.datetime.LocalDate)`.

- [ ] **Step 1: Create `Dose.kt` with `kotlinx.datetime.Instant`**

```kotlin
package com.yhdista.dosetracker.domain.model

import kotlinx.datetime.Instant

data class Dose(
    val id: Long = 0,
    val medicationId: Long,
    val medicationName: String = "", // Added medication name
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

- [ ] **Step 2: Create `Medication.kt` (unchanged, no date fields)**

```kotlin
package com.yhdista.dosetracker.domain.model

data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: Double,
    val unit: String,
    val frequency: String, // e.g., "Daily", "Twice a day"
    val reminderTime: String? = null // e.g., "08:00"
)
```

- [ ] **Step 3: Create `MedicationRepository.kt` with `kotlinx.datetime.LocalDate`**

```kotlin
package com.yhdista.dosetracker.domain.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface MedicationRepository {
    fun getMedications(): Flow<Data<List<Medication>>>
    fun getMedicationById(id: Long): Flow<Data<Medication>>
    suspend fun insertMedication(medication: Medication): Data<Long>
    suspend fun updateMedication(medication: Medication): Data<Unit>
    suspend fun deleteMedication(medication: Medication): Data<Unit>

    fun getDosesForMedication(medicationId: Long): Flow<Data<List<Dose>>>
    fun getDosesForDate(date: LocalDate): Flow<Data<List<Dose>>>
    fun getAllDoses(): Flow<Data<List<Dose>>>
    suspend fun insertDose(dose: Dose): Data<Long>
    suspend fun updateDose(dose: Dose): Data<Unit>

    fun searchMedications(query: String): Flow<Data<List<Medication>>>
}
```

- [ ] **Step 4: Delete the three original `:app` files, verify build**

Delete `app/src/main/java/com/yhdista/dosetracker/domain/model/Dose.kt`, `Medication.kt`, `app/src/main/java/com/yhdista/dosetracker/domain/repository/MedicationRepository.kt`.

Run: `./gradlew :shared:assemble`
Expected: `BUILD SUCCESSFUL` (nothing in `:shared` references these yet outside itself, so this just validates the new files compile standalone).

- [ ] **Step 5: Commit**

```bash
git add shared/src app/src
git commit -m "refactor: move domain layer to :shared, switch Dose/repository dates to kotlinx-datetime"
```

---

### Task 4: Room data layer (multiplatform) + mappers

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/DoseEntity.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/entity/MedicationEntity.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/DoseDao.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/dao/MedicationDao.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/Converters.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/AppDatabase.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/DatabaseBuilder.kt`
- Create: `shared/src/androidMain/kotlin/com/yhdista/dosetracker/data/local/DatabaseBuilder.android.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/DoseMapper.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/MedicationMapper.kt`
- Delete: the six equivalent files under `app/src/main/java/com/yhdista/dosetracker/data/local/` and `data/mapper/`

**Interfaces:**
- Consumes: `Dose`, `Medication`, `DoseStatus` (Task 3).
- Produces: `getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase` (commonMain), `getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase>` (androidMain, used by `:app`'s Koin module in Task 6), `DoseDao`, `MedicationDao`, `DoseWithMedication`, `DoseEntity.toDomain()`, `Dose.toEntity()`, `MedicationEntity.toDomain()`, `Medication.toEntity()`.

- [ ] **Step 1: Create `DoseEntity.kt` with `kotlinx.datetime.Instant`**

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
    indices = [Index(value = ["medicationId"])]
)
data class DoseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val timestamp: Instant,
    val amount: Double?,
    val unit: String?,
    val status: DoseStatus
)
```

- [ ] **Step 2: Create `MedicationEntity.kt` (unchanged)**

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
    val unit: String,
    val frequency: String,
    val reminderTime: String? = null
)
```

- [ ] **Step 3: Create `DoseDao.kt` (unchanged queries, same package)**

```kotlin
package com.yhdista.dosetracker.data.local.dao

import androidx.room.*
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import kotlinx.coroutines.flow.Flow

data class DoseWithMedication(
    @Embedded val dose: DoseEntity,
    @ColumnInfo(name = "medicationName") val medicationName: String
)

@Dao
interface DoseDao {
    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName 
        FROM doses 
        INNER JOIN medications ON doses.medicationId = medications.id 
        WHERE medicationId = :medicationId 
        ORDER BY timestamp DESC
    """)
    fun getDosesForMedication(medicationId: Long): Flow<List<DoseWithMedication>>

    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName 
        FROM doses 
        INNER JOIN medications ON doses.medicationId = medications.id 
        WHERE timestamp >= :startTime AND timestamp <= :endTime 
        ORDER BY timestamp ASC
    """)
    fun getDosesInTimeRange(startTime: Long, endTime: Long): Flow<List<DoseWithMedication>>

    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName 
        FROM doses 
        INNER JOIN medications ON doses.medicationId = medications.id 
        ORDER BY timestamp DESC
    """)
    fun getAllDoses(): Flow<List<DoseWithMedication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDose(dose: DoseEntity): Long

    @Update
    suspend fun updateDose(dose: DoseEntity)

    @Delete
    suspend fun deleteDose(dose: DoseEntity)
}
```

Note: `getDosesInTimeRange` still takes `Long` epoch-millis bounds — the caller (`MedicationRepositoryImpl`, Task 5) computes those from a `kotlinx.datetime.LocalDate`.

- [ ] **Step 4: Create `MedicationDao.kt` (unchanged)**

```kotlin
package com.yhdista.dosetracker.data.local.dao

import androidx.room.*
import com.yhdista.dosetracker.data.local.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications")
    fun getAllMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    fun getMedicationById(id: Long): Flow<MedicationEntity?>

    @Query("SELECT * FROM medications WHERE name LIKE '%' || :query || '%'")
    fun searchMedications(query: String): Flow<List<MedicationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: MedicationEntity): Long

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Delete
    suspend fun deleteMedication(medication: MedicationEntity)
}
```

- [ ] **Step 5: Create `Converters.kt` with `kotlinx.datetime.Instant`**

```kotlin
package com.yhdista.dosetracker.data.local

import androidx.room.TypeConverter
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
}
```

- [ ] **Step 6: Create `AppDatabase.kt` using Room's multiplatform constructor**

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
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import com.yhdista.dosetracker.data.local.entity.MedicationEntity

@Database(
    entities = [MedicationEntity::class, DoseEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseDao(): DoseDao

    companion object {
        const val DATABASE_NAME = "dosetracker_db"

        val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                connection.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Paracetamol', 500.0, 'mg', 'Every 6 hours', '08:00')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Ibuprofen', 400.0, 'mg', 'Twice a day', '09:00')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Amoxicillin', 500.0, 'mg', 'Three times a day', '07:00')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Vitamin C', 1000.0, 'mg', 'Daily', '10:00')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Lisinopril', 10.0, 'mg', 'Daily', '08:30')")
            }
        }
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
```

Note: the KSP Room compiler processor generates the `actual` for `AppDatabaseConstructor` automatically for each configured target — Task 1 already wired `kspAndroid` in `shared/build.gradle.kts`, so this only requires the Android target for now.

- [ ] **Step 7: Create the commonMain database builder finisher**

`shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/local/DatabaseBuilder.kt`:

```kotlin
package com.yhdista.dosetracker.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .addCallback(AppDatabase.seedCallback)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
```

- [ ] **Step 8: Create the androidMain database builder factory**

`shared/src/androidMain/kotlin/com/yhdista/dosetracker/data/local/DatabaseBuilder.android.kt`:

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
    )
}
```

- [ ] **Step 9: Add the `androidx.sqlite` bundled driver dependency**

In `shared/build.gradle.kts`, add to `commonMain.dependencies`:

```kotlin
            implementation("androidx.sqlite:sqlite-bundled:2.7.0")
```

- [ ] **Step 10: Move the mappers (unchanged logic, same package)**

`shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/DoseMapper.kt`:

```kotlin
package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.dao.DoseWithMedication
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import com.yhdista.dosetracker.domain.model.Dose

fun DoseEntity.toDomain(medicationName: String = ""): Dose {
    return Dose(
        id = id,
        medicationId = medicationId,
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
        timestamp = timestamp,
        amount = amount,
        unit = unit,
        status = status
    )
}
```

`shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/mapper/MedicationMapper.kt`:

```kotlin
package com.yhdista.dosetracker.data.mapper

import com.yhdista.dosetracker.data.local.entity.MedicationEntity
import com.yhdista.dosetracker.domain.model.Medication

fun MedicationEntity.toDomain(): Medication {
    return Medication(
        id = id,
        name = name,
        dosage = dosage,
        unit = unit,
        frequency = frequency,
        reminderTime = reminderTime
    )
}

fun Medication.toEntity(): MedicationEntity {
    return MedicationEntity(
        id = id,
        name = name,
        dosage = dosage,
        unit = unit,
        frequency = frequency,
        reminderTime = reminderTime
    )
}
```

- [ ] **Step 11: Delete the original `:app` files**

Delete `app/src/main/java/com/yhdista/dosetracker/data/local/AppDatabase.kt`, `Converters.kt`, `data/local/dao/DoseDao.kt`, `data/local/dao/MedicationDao.kt`, `data/local/entity/DoseEntity.kt`, `data/local/entity/MedicationEntity.kt`, `data/mapper/DoseMapper.kt`, `data/mapper/MedicationMapper.kt`.

- [ ] **Step 12: Verify the Room KSP setup compiles**

Run: `./gradlew :shared:assemble`
Expected: `BUILD SUCCESSFUL`. This is the highest-risk step in the whole plan (Room's multiplatform constructor/driver API). If it fails, the compiler error will point at the exact mismatched signature (most likely `SQLiteConnection.execSQL` or `Room.databaseBuilder<AppDatabase>` overload) — fix the signature to match the installed Room 2.8.4 API and re-run.

- [ ] **Step 13: Commit**

```bash
git add shared/build.gradle.kts shared/src app/src
git commit -m "refactor: move Room data layer to :shared using Room multiplatform constructor"
```

---

### Task 5: `DoseReminderScheduler` interface + repository move

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/reminder/DoseReminderScheduler.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt`
- Delete: `app/src/main/java/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/reminder/ReminderScheduler.kt`

**Interfaces:**
- Consumes: `MedicationRepository`, `Dose`, `Medication`, `DoseDao`, `MedicationDao`, mappers (earlier tasks).
- Produces: `interface DoseReminderScheduler { fun scheduleReminder(medication: Medication); fun cancelReminder(medicationId: Long) }` — implemented by `:app`'s `ReminderScheduler` (Task 11 wires it into Koin).

- [ ] **Step 1: Create the common `DoseReminderScheduler` interface**

```kotlin
package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.domain.model.Medication

interface DoseReminderScheduler {
    fun scheduleReminder(medication: Medication)
    fun cancelReminder(medicationId: Long)
}
```

- [ ] **Step 2: Move `MedicationRepositoryImpl`, drop `@Inject`, depend on the interface**

```kotlin
package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.data.local.dao.DoseDao
import com.yhdista.dosetracker.data.local.dao.MedicationDao
import com.yhdista.dosetracker.data.mapper.toDomain
import com.yhdista.dosetracker.data.mapper.toEntity
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.reminder.DoseReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

class MedicationRepositoryImpl(
    private val medicationDao: MedicationDao,
    private val doseDao: DoseDao,
    private val reminderScheduler: DoseReminderScheduler
) : MedicationRepository {

    override fun getMedications(): Flow<Data<List<Medication>>> {
        return medicationDao.getAllMedications()
            .map { entities ->
                val medications = entities.map { it.toDomain() }
                Data.Success(medications) as Data<List<Medication>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch medications", e)) }
    }

    override fun getMedicationById(id: Long): Flow<Data<Medication>> {
        return medicationDao.getMedicationById(id)
            .map { entity ->
                if (entity != null) {
                    Data.Success(entity.toDomain()) as Data<Medication>
                } else {
                    Data.Error("Medication not found")
                }
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch medication", e)) }
    }

    override suspend fun insertMedication(medication: Medication): Data<Long> {
        return try {
            val id = medicationDao.insertMedication(medication.toEntity())
            if (medication.reminderTime != null) {
                reminderScheduler.scheduleReminder(medication.copy(id = id))
            }
            Data.Success(id)
        } catch (e: Exception) {
            Data.Error("Failed to insert medication", e)
        }
    }

    override suspend fun updateMedication(medication: Medication): Data<Unit> {
        return try {
            medicationDao.updateMedication(medication.toEntity())
            if (medication.reminderTime != null) {
                reminderScheduler.scheduleReminder(medication)
            } else {
                reminderScheduler.cancelReminder(medication.id)
            }
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to update medication", e)
        }
    }

    override suspend fun deleteMedication(medication: Medication): Data<Unit> {
        return try {
            medicationDao.deleteMedication(medication.toEntity())
            reminderScheduler.cancelReminder(medication.id)
            Data.Success(Unit)
        } catch (e: Exception) {
            Data.Error("Failed to delete medication", e)
        }
    }

    override fun getDosesForMedication(medicationId: Long): Flow<Data<List<Dose>>> {
        return doseDao.getDosesForMedication(medicationId)
            .map { entities ->
                val doses = entities.map { it.toDomain() }
                Data.Success(doses) as Data<List<Dose>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch doses", e)) }
    }

    override fun getDosesForDate(date: LocalDate): Flow<Data<List<Dose>>> {
        val zone = TimeZone.currentSystemDefault()
        val startOfDay = date.atStartOfDayIn(zone).toEpochMilliseconds()
        val endOfDay = date.atTime(LocalTime(23, 59, 59, 999_000_000)).toInstant(zone).toEpochMilliseconds()

        return doseDao.getDosesInTimeRange(startOfDay, endOfDay)
            .map { entities ->
                val doses = entities.map { it.toDomain() }
                Data.Success(doses) as Data<List<Dose>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch doses for date", e)) }
    }

    override fun getAllDoses(): Flow<Data<List<Dose>>> {
        return doseDao.getAllDoses()
            .map { entities ->
                val doses = entities.map { it.toDomain() }
                Data.Success(doses) as Data<List<Dose>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to fetch all doses", e)) }
    }

    override suspend fun insertDose(dose: Dose): Data<Long> {
        return try {
            val id = doseDao.insertDose(dose.toEntity())
            Data.Success(id)
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

    override fun searchMedications(query: String): Flow<Data<List<Medication>>> {
        return medicationDao.searchMedications(query)
            .map { entities ->
                val medications = entities.map { it.toDomain() }
                Data.Success(medications) as Data<List<Medication>>
            }
            .onStart { emit(Data.Loading) }
            .catch { e -> emit(Data.Error("Failed to search medications", e)) }
    }
}
```

- [ ] **Step 3: Make `:app`'s `ReminderScheduler` implement the shared interface**

In `app/src/main/java/com/yhdista/dosetracker/reminder/ReminderScheduler.kt`, change the class declaration and imports:

```kotlin
package com.yhdista.dosetracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yhdista.dosetracker.domain.model.Medication
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduler(
    private val context: Context
) : DoseReminderScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleReminder(medication: Medication) {
        val reminderTimeStr = medication.reminderTime ?: return
        val time = try {
            LocalTime.parse(reminderTimeStr)
        } catch (_: Exception) {
            return
        }

        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var scheduledTime = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)

        if (scheduledTime.isBefore(now)) {
            scheduledTime = scheduledTime.plusDays(1)
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("medicationId", medication.id)
            putExtra("medicationName", medication.name)
            putExtra("dosage", "${medication.dosage} ${medication.unit}")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledTime.toInstant().toEpochMilli(),
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledTime.toInstant().toEpochMilli(),
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledTime.toInstant().toEpochMilli(),
                pendingIntent
            )
        }
    }

    override fun cancelReminder(medicationId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
```

(Only the class header changed: dropped `@Singleton`/`@Inject`/`@ApplicationContext`, now implements `DoseReminderScheduler`, plain constructor. Koin wires the `Context` in Task 6.)

- [ ] **Step 4: Delete the original `:app` repository file**

Delete `app/src/main/java/com/yhdista/dosetracker/data/repository/MedicationRepositoryImpl.kt`.

- [ ] **Step 5: Verify `:shared` compiles**

Run: `./gradlew :shared:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add shared/src app/src
git commit -m "refactor: introduce DoseReminderScheduler interface, move MedicationRepositoryImpl to :shared"
```

---

### Task 6: Koin modules (data layer) + delete Hilt DI files

**Files:**
- Create: `shared/src/androidMain/kotlin/com/yhdista/dosetracker/di/DataModule.kt` (androidMain, not commonMain — it imports `android.content.Context` and the androidMain-only `getDatabaseBuilder`, neither of which commonMain can see)
- Delete: `app/src/main/java/com/yhdista/dosetracker/di/DatabaseModule.kt`
- Delete: `app/src/main/java/com/yhdista/dosetracker/di/RepositoryModule.kt`

**Interfaces:**
- Consumes: `getDatabaseBuilder` (androidMain, Task 4), `getRoomDatabase` (commonMain, Task 4), `AppDatabase`, `MedicationRepositoryImpl`, `MedicationRepository`.
- Produces: `val dataModule: Module` — a Koin module providing `AppDatabase`, `MedicationDao`, `DoseDao`, `MedicationRepository`. `:app` includes this module in Task 11's `startKoin { }` call, alongside an app-side module providing `DoseReminderScheduler`.

- [ ] **Step 1: Create the Koin data module**

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
    single<MedicationRepository> {
        MedicationRepositoryImpl(
            medicationDao = get(),
            doseDao = get(),
            reminderScheduler = get()
        )
    }
}
```

Note: `get<Context>()` resolves the Android `Context` that `:app`'s `startKoin { androidContext(...) }` call (Task 11) registers automatically — this is Koin's standard `androidContext()` mechanism, available because `:shared`'s `androidMain` source set depends on `koin-android`.

- [ ] **Step 2: Delete the Hilt DI files**

Delete `app/src/main/java/com/yhdista/dosetracker/di/DatabaseModule.kt` and `app/src/main/java/com/yhdista/dosetracker/di/RepositoryModule.kt`. The `di/` directory in `:app` is now empty; leave it for Task 11 to add the app-side Koin module.

- [ ] **Step 3: Verify `:shared` compiles**

Run: `./gradlew :shared:assemble`
Expected: `BUILD SUCCESSFUL` (Koin's `module { }` DSL type-checks even though nothing calls `startKoin` yet).

- [ ] **Step 4: Commit**

```bash
git add shared/src app/src
git commit -m "feat: add Koin data module in :shared, remove Hilt DI modules"
```

---

### Task 7: Theme (common tokens + Android-specific dynamic color)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/theme/Color.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/theme/Type.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/theme/Theme.kt` (`expect fun DoseTrackerTheme`)
- Create: `shared/src/androidMain/kotlin/com/yhdista/dosetracker/ui/theme/Theme.android.kt` (`actual fun DoseTrackerTheme`)
- Delete: the three equivalent files under `app/src/main/java/com/yhdista/dosetracker/ui/theme/`

**Interfaces:**
- Produces: `Purple80`/`PurpleGrey80`/`Pink80`/`Purple40`/`PurpleGrey40`/`Pink40` (`Color`), `Typography` (Material3 `Typography`), `@Composable fun DoseTrackerTheme(darkTheme: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit)`.

**Note (retroactively corrected — discovered during Task 10, not the original Task 7 design):** the original design kept `Theme.kt` entirely in `androidMain`, reasoning that dynamic color has no cross-platform equivalent yet and there was nothing to make `expect` against. That turned out to be wrong: Task 10's `TodayScreen.kt` needs `DoseTrackerTheme` from its commonMain `@Preview` function, and commonMain cannot see an androidMain-only declaration. The fix is an `expect`/`actual` split — dynamic color logic still lives only in the androidMain `actual`, nothing about the runtime behavior changes, but the function *signature* (with its default parameter values) must be declared as `expect` in commonMain so commonMain callers can resolve it. `actual` declarations cannot repeat default parameter values (a Kotlin restriction), so the defaults live only on the `expect` side.

- [ ] **Step 1: Move `Color.kt` (unchanged, pure Compose Multiplatform)**

```kotlin
package com.yhdista.dosetracker.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

- [ ] **Step 2: Move `Type.kt` (unchanged)**

```kotlin
package com.yhdista.dosetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
```

- [ ] **Step 3: Create the commonMain `expect` declaration**

`shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/theme/Theme.kt`:

```kotlin
package com.yhdista.dosetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
expect fun DoseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
)
```

- [ ] **Step 3b: Create the androidMain `actual` implementation**

`shared/src/androidMain/kotlin/com/yhdista/dosetracker/ui/theme/Theme.android.kt`:

```kotlin
package com.yhdista.dosetracker.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
actual fun DoseTrackerTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 4: Delete the original `:app` theme files**

Delete `app/src/main/java/com/yhdista/dosetracker/ui/theme/Color.kt`, `Theme.kt`, `Type.kt`.

- [ ] **Step 5: Verify `:shared` compiles**

Run: `./gradlew :shared:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add shared/src app/src
git commit -m "refactor: move theme to :shared (tokens common, dynamic color android-only)"
```

---

### Task 8: Navigation destinations

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/navigation/Destinations.kt`
- Delete: `app/src/main/java/com/yhdista/dosetracker/ui/navigation/Destinations.kt`

**Interfaces:**
- Produces: `sealed interface Destination : NavKey` with `Today`, `Medications`, `History`, `MedicationDetail(id: Long)`, `AddDose(medicationId: Long)`.

- [ ] **Step 1: Move the file verbatim (already pure Kotlin + kotlinx.serialization, both KMP-safe)**

```kotlin
package com.yhdista.dosetracker.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Destination : NavKey {
    @Serializable
    data object Today : Destination

    @Serializable
    data object Medications : Destination

    @Serializable
    data object History : Destination

    @Serializable
    data class MedicationDetail(val id: Long) : Destination

    @Serializable
    data class AddDose(val medicationId: Long) : Destination
}
```

- [ ] **Step 2: Delete the original file, verify build**

Delete `app/src/main/java/com/yhdista/dosetracker/ui/navigation/Destinations.kt`.

Run: `./gradlew :shared:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add shared/src app/src
git commit -m "refactor: move navigation destinations to :shared"
```

---

### Task 9: ViewModels — move + Hilt→Koin + kotlinx-datetime

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/catalog/MedicationCatalogViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/dose/AddDoseViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/history/HistoryViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/di/ViewModelModule.kt`
- Delete: the four equivalent files under `app/src/main/java/com/yhdista/dosetracker/ui/*/`

**Interfaces:**
- Consumes: `MedicationRepository` (Task 5), `Data`, `Dose`, `Medication`, `DoseStatus`.
- Produces: same four `ViewModel` classes and their `State`/`Event` types, now plain classes (no `@HiltViewModel`), plus `val viewModelModule: Module` that Task 11 includes in `startKoin { }`.

**Note on `Clock` (discovered during implementation):** `kotlinx-datetime` 0.8.0 removed `kotlinx.datetime.Clock` in favor of `kotlin.time.Clock` (the Kotlin 2.1+ stdlib's own consolidated Clock, same ecosystem move that made `kotlinx.datetime.Instant` a deprecated typealias for `kotlin.time.Instant`). `import kotlinx.datetime.Clock` does not expose a usable `System` member and fails with "Unresolved reference 'System'" — every `Clock.System` usage below (and in Tasks 10 and 13) imports `kotlin.time.Clock` instead. `LocalDateTime`/`LocalDate`/`TimeZone`/`toLocalDateTime`/`toInstant`/`todayIn` are unaffected and stay as `kotlinx.datetime.*` — they already target `kotlin.time.Instant` under the hood, so they work the same with `kotlin.time.Clock.System` as a receiver.

- [ ] **Step 1: Move `MedicationCatalogViewModel.kt`, drop Hilt**

```kotlin
package com.yhdista.dosetracker.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CatalogState(
    val medications: Data<List<Medication>> = Data.Loading,
    val searchQuery: String = ""
)

sealed interface CatalogEvent {
    data class Search(val query: String) : CatalogEvent
    data class AddMedication(
        val name: String,
        val dosage: String,
        val unit: String,
        val frequency: String
    ) : CatalogEvent
}

class MedicationCatalogViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<CatalogState> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                repository.getMedications()
            } else {
                repository.searchMedications(query)
            }
        }
        .map { result ->
            CatalogState(medications = result, searchQuery = _searchQuery.value)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CatalogState()
        )

    fun onEvent(event: CatalogEvent) {
        when (event) {
            is CatalogEvent.Search -> _searchQuery.value = event.query
            is CatalogEvent.AddMedication -> addMedication(event)
        }
    }

    private fun addMedication(event: CatalogEvent.AddMedication) {
        viewModelScope.launch {
            val dosageValue = event.dosage.toDoubleOrNull() ?: 0.0
            repository.insertMedication(
                Medication(
                    name = event.name,
                    dosage = dosageValue,
                    unit = event.unit,
                    frequency = event.frequency
                )
            )
        }
    }
}
```

- [ ] **Step 2: Move `AddDoseViewModel.kt`, drop Hilt, switch to `kotlinx.datetime.LocalDateTime`**

```kotlin
package com.yhdista.dosetracker.ui.dose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class AddDoseState(
    val medication: Data<Medication> = Data.Loading,
    val amount: String = "",
    val time: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val isSuccess: Boolean = false,
    val error: String? = null
)

sealed interface AddDoseEvent {
    data class UpdateAmount(val amount: String) : AddDoseEvent
    data class UpdateTime(val time: LocalDateTime) : AddDoseEvent
    data object SaveDose : AddDoseEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddDoseViewModel(
    private val repository: MedicationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val medicationIdFlow = savedStateHandle.getStateFlow<Long?>("medicationId", null)

    private val _state = MutableStateFlow(AddDoseState())
    val state = _state.asStateFlow()

    init {
        medicationIdFlow
            .filterNotNull()
            .flatMapLatest { id -> repository.getMedicationById(id) }
            .onEach { result ->
                _state.update { state ->
                    state.copy(
                        medication = result,
                        amount = if (result is Data.Success) result.data.dosage.toString() else state.amount
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun setMedicationId(id: Long) {
        if (savedStateHandle.get<Long>("medicationId") == null) {
            savedStateHandle["medicationId"] = id
        }
    }

    fun onEvent(event: AddDoseEvent) {
        when (event) {
            is AddDoseEvent.UpdateAmount -> _state.update { it.copy(amount = event.amount) }
            is AddDoseEvent.UpdateTime -> _state.update { it.copy(time = event.time) }
            is AddDoseEvent.SaveDose -> saveDose()
        }
    }

    private fun saveDose() {
        val currentState = _state.value
        val medication = (currentState.medication as? Data.Success)?.data ?: return

        viewModelScope.launch {
            val dose = Dose(
                medicationId = medication.id,
                timestamp = currentState.time.toInstant(TimeZone.currentSystemDefault()),
                amount = currentState.amount.toDoubleOrNull(),
                unit = medication.unit,
                status = DoseStatus.TAKEN
            )
            when (val result = repository.insertDose(dose)) {
                is Data.Success -> _state.update { it.copy(isSuccess = true) }
                is Data.Error -> _state.update { it.copy(error = result.message) }
                else -> Unit
            }
        }
    }
}
```

- [ ] **Step 3: Move `HistoryViewModel.kt`, drop Hilt (no date types here)**

```kotlin
package com.yhdista.dosetracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.*

data class HistoryState(
    val dosesWithMeds: Data<List<DoseWithMedication>> = Data.Loading
)

data class DoseWithMedication(
    val dose: Dose,
    val medication: Medication?
)

class HistoryViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    val uiState: StateFlow<HistoryState> = combine(
        repository.getAllDoses(),
        repository.getMedications()
    ) { dosesResult, medsResult ->
        if (dosesResult is Data.Success && medsResult is Data.Success) {
            val medsMap = medsResult.data.associateBy { it.id }
            val combined = dosesResult.data.map { dose ->
                DoseWithMedication(dose, medsMap[dose.medicationId])
            }
            HistoryState(Data.Success(combined))
        } else if (dosesResult is Data.Error) {
            HistoryState(Data.Error(dosesResult.message))
        } else if (medsResult is Data.Error) {
            HistoryState(Data.Error(medsResult.message))
        } else {
            HistoryState(Data.Loading)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryState()
    )
}
```

- [ ] **Step 4: Move `TodayViewModel.kt`, drop Hilt, switch to `kotlinx.datetime.LocalDate`**

```kotlin
package com.yhdista.dosetracker.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
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
        _selectedDoseId
    ) { doses, selectedId ->
        TodayState(
            doses = doses,
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

- [ ] **Step 5: Create the Koin ViewModel module**

```kotlin
package com.yhdista.dosetracker.di

import com.yhdista.dosetracker.ui.catalog.MedicationCatalogViewModel
import com.yhdista.dosetracker.ui.dose.AddDoseViewModel
import com.yhdista.dosetracker.ui.history.HistoryViewModel
import com.yhdista.dosetracker.ui.today.TodayViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { MedicationCatalogViewModel(get()) }
    viewModel { AddDoseViewModel(get(), get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { TodayViewModel(get(), get()) }
}
```

Note: Koin resolves the `SavedStateHandle` parameter automatically for `viewModel { }` definitions when the ViewModel is created through `koinViewModel()` in Compose (Task 10) — this is Koin's standard androidx ViewModel + `SavedStateHandle` integration, no manual wiring needed.

- [ ] **Step 6: Delete the four original `:app` ViewModel files**

Delete `app/src/main/java/com/yhdista/dosetracker/ui/catalog/MedicationCatalogViewModel.kt`, `ui/dose/AddDoseViewModel.kt`, `ui/history/HistoryViewModel.kt`, `ui/today/TodayViewModel.kt`.

- [ ] **Step 7: Verify `:shared` compiles**

Run: `./gradlew :shared:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add shared/src app/src
git commit -m "refactor: move ViewModels to :shared, replace Hilt with Koin, switch to kotlinx-datetime"
```

---

### Task 10: Screens — move + kotlinx-datetime formatting + shared app entry composable

**Files:**
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/catalog/MedicationCatalogScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/dose/AddDoseScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/history/HistoryScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/today/TodayScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/DoseTrackerAppMain.kt`
- Create: `shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/NotificationPermission.kt` (expect)
- Create: `shared/src/androidMain/kotlin/com/yhdista/dosetracker/ui/app/NotificationPermission.android.kt` (actual)
- Delete: the four equivalent screen files and `MainActivity.kt`'s `DoseTrackerAppMain`/`MedicationDetailPlaceholder` composables (Task 11 slims `MainActivity.kt` down)

**Interfaces:**
- Consumes: the four ViewModels (Task 9), `Destination` (Task 8), `DoseTrackerTheme` (Task 7).
- Produces: `@Composable fun DoseTrackerAppMain()` — the full navigation host, now callable from `:app`'s `MainActivity` (Task 11) with zero Android-specific code inline.

- [ ] **Step 1: Move `MedicationCatalogScreen.kt` (no date formatting, unchanged)**

```kotlin
package com.yhdista.dosetracker.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Medication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationCatalogScreen(
    viewModel: MedicationCatalogViewModel,
    onMedicationClick: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Medication Catalog") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Medication")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.onEvent(CatalogEvent.Search(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            if (showAddDialog) {
                AddMedicationDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { name, dosage, unit, frequency ->
                        viewModel.onEvent(CatalogEvent.AddMedication(name, dosage, unit, frequency))
                        showAddDialog = false
                    }
                )
            }

            when (val result = state.medications) {
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
                    val medications = result.data
                    if (medications.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No medications found")
                        }
                    } else {
                        LazyColumn {
                            items(medications) { medication ->
                                MedicationItem(
                                    medication = medication,
                                    onClick = { onMedicationClick(medication.id) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddMedicationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("mg") }
    var frequency by remember { mutableStateOf("Daily") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Medication") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (e.g., mg, ml)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it },
                    label = { Text("Frequency (e.g., Daily)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, dosage, unit, frequency) },
                enabled = name.isNotBlank() && dosage.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search medications...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun MedicationItem(
    medication: Medication,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(medication.name) },
        supportingContent = { Text("${medication.dosage} ${medication.unit} - ${medication.frequency}") },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
```

- [ ] **Step 2: Move `AddDoseScreen.kt`, replace `DateTimeFormatter` with `kotlinx.datetime` formatting**

```kotlin
package com.yhdista.dosetracker.ui.dose

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
fun AddDoseScreen(
    medicationId: Long,
    viewModel: AddDoseViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(medicationId) {
        viewModel.setMedicationId(medicationId)
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Dose") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val result = state.medication) {
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
                val medication = result.data
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Medication: ${medication.name}",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = { viewModel.onEvent(AddDoseEvent.UpdateAmount(it)) },
                        label = { Text("Amount (${medication.unit})") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Time: ${state.time.format(dateTimeDisplayFormat)}",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Button(
                        onClick = { viewModel.onEvent(AddDoseEvent.SaveDose) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Log Dose")
                    }

                    if (state.error != null) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

private val dateTimeDisplayFormat = kotlinx.datetime.LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); dayOfMonth()
    char(' ')
    hour(); char(':'); minute()
}
```

- [ ] **Step 3: Move `HistoryScreen.kt`, replace `ZoneId`/`DateTimeFormatter` with `kotlinx.datetime` formatting**

```kotlin
package com.yhdista.dosetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Dose History") })
        }
    ) { padding ->
        when (val result = state.dosesWithMeds) {
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
                val items = result.data
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No history found")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        items(items) { item ->
                            HistoryItem(item)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private val historyTimeFormat = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); dayOfMonth()
    char(' ')
    hour(); char(':'); minute()
}

@Composable
fun HistoryItem(item: DoseWithMedication) {
    val timeStr = item.dose.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).format(historyTimeFormat)

    ListItem(
        headlineContent = { Text(item.medication?.name ?: "Unknown Medication") },
        supportingContent = {
            val amountStr = item.dose.amount?.toString() ?: item.medication?.dosage?.toString() ?: ""
            val unitStr = item.dose.unit ?: item.medication?.unit ?: ""
            Text("$amountStr $unitStr at $timeStr")
        },
        trailingContent = {
            Text(
                text = item.dose.status.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.dose.status.name == "TAKEN") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    )
}
```

- [ ] **Step 4: Move `TodayScreen.kt`, replace `Instant`/`ZoneId`/`DateTimeFormatter` with `kotlinx.datetime`, swap `@Preview` import**

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
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.ui.theme.DoseTrackerTheme
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onNavigateToDetail: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TodayContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateToDetail = onNavigateToDetail
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayContent(
    state: TodayState,
    onEvent: (TodayEvent) -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
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
                if (doses.data.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No doses scheduled for today")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(doses.data, key = { it.id }) { dose ->
                            DoseItem(
                                dose = dose,
                                onClick = { onNavigateToDetail(dose.medicationId) },
                                onToggleStatus = { onEvent(TodayEvent.ToggleDoseStatus(dose)) }
                            )
                        }
                    }
                }
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
                ))
            ),
            onEvent = {},
            onNavigateToDetail = {}
        )
    }
}
```

Note: `@Preview(showBackground = true)` becomes plain `@Preview` from `androidx.compose.ui.tooling.preview.Preview` — as of Compose Multiplatform 1.10+ this is the single unified preview annotation usable directly in commonMain (the old `org.jetbrains.compose.ui.tooling.preview.Preview` is deprecated); it does not accept `showBackground` in this phase, drop the parameter.

- [ ] **Step 5: Create the `NotificationPermission` expect/actual pair**

`shared/src/commonMain/kotlin/com/yhdista/dosetracker/ui/app/NotificationPermission.kt`:

```kotlin
package com.yhdista.dosetracker.ui.app

import androidx.compose.runtime.Composable

@Composable
expect fun RequestNotificationPermissionEffect()
```

`shared/src/androidMain/kotlin/com/yhdista/dosetracker/ui/app/NotificationPermission.android.kt`:

```kotlin
package com.yhdista.dosetracker.ui.app

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun RequestNotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT >= 33) {
        val notificationPermissionState = rememberPermissionState(
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        LaunchedEffect(Unit) {
            if (!notificationPermissionState.status.isGranted) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }
}
```

- [ ] **Step 6: Create `DoseTrackerAppMain.kt` — the shared navigation host**

```kotlin
package com.yhdista.dosetracker.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.yhdista.dosetracker.ui.catalog.MedicationCatalogScreen
import com.yhdista.dosetracker.ui.catalog.MedicationCatalogViewModel
import com.yhdista.dosetracker.ui.dose.AddDoseScreen
import com.yhdista.dosetracker.ui.dose.AddDoseViewModel
import com.yhdista.dosetracker.ui.history.HistoryScreen
import com.yhdista.dosetracker.ui.history.HistoryViewModel
import com.yhdista.dosetracker.ui.navigation.Destination
import com.yhdista.dosetracker.ui.today.TodayScreen
import com.yhdista.dosetracker.ui.today.TodayViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun DoseTrackerAppMain() {
    RequestNotificationPermissionEffect()

    val backstack = rememberNavBackStack(Destination.Today)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = backstack.last() is Destination.Today,
                onClick = {
                    if (backstack.last() !is Destination.Today) {
                        backstack.clear()
                        backstack.add(Destination.Today)
                    }
                },
                icon = { Icon(Icons.Rounded.Today, contentDescription = "Today") },
                label = { Text("Today") }
            )
            item(
                selected = backstack.last() is Destination.Medications,
                onClick = {
                    if (backstack.last() !is Destination.Medications) {
                        backstack.clear()
                        backstack.add(Destination.Medications)
                    }
                },
                icon = { Icon(Icons.Rounded.Medication, contentDescription = "Medications") },
                label = { Text("Meds") }
            )
            item(
                selected = backstack.last() is Destination.History,
                onClick = {
                    if (backstack.last() !is Destination.History) {
                        backstack.clear()
                        backstack.add(Destination.History)
                    }
                },
                icon = { Icon(Icons.Rounded.History, contentDescription = "History") },
                label = { Text("History") }
            )
        }
    ) {
        NavDisplay(
            backStack = backstack,
            modifier = Modifier.fillMaxSize(),
            onBack = { backstack.removeLastOrNull() },
            sceneStrategies = listOf(listDetailStrategy),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            )
        ) { key ->
            val destination = key as Destination
            when (destination) {
                is Destination.Today -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        TodayScreen(
                            viewModel = koinViewModel<TodayViewModel>(),
                            onNavigateToDetail = { id ->
                                backstack.add(Destination.MedicationDetail(id))
                            }
                        )
                    }
                }
                is Destination.Medications -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        MedicationCatalogScreen(
                            viewModel = koinViewModel<MedicationCatalogViewModel>(),
                            onMedicationClick = { id ->
                                backstack.add(Destination.AddDose(id))
                            }
                        )
                    }
                }
                is Destination.History -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.listPane()
                    ) {
                        HistoryScreen(
                            viewModel = koinViewModel<HistoryViewModel>()
                        )
                    }
                }
                is Destination.AddDose -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        AddDoseScreen(
                            medicationId = destination.medicationId,
                            viewModel = koinViewModel<AddDoseViewModel>(),
                            onBack = { backstack.removeLastOrNull() }
                        )
                    }
                }
                is Destination.MedicationDetail -> {
                    NavEntry(
                        key = destination,
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) {
                        MedicationDetailPlaceholder(destination.id) {
                            backstack.removeLastOrNull()
                        }
                    }
                }
                else -> {
                    NavEntry(key = destination) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Screen for $destination")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailPlaceholder(id: Long, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medication $id") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Details for Medication $id")
        }
    }
}
```

- [ ] **Step 7: Delete the original `:app` screen files**

Delete `app/src/main/java/com/yhdista/dosetracker/ui/catalog/MedicationCatalogScreen.kt`, `ui/dose/AddDoseScreen.kt`, `ui/history/HistoryScreen.kt`, `ui/today/TodayScreen.kt`.

- [ ] **Step 8: Add the Compose Multiplatform preview and adaptive-navigation dependencies**

In `shared/build.gradle.kts`, add to `commonMain.dependencies`:

```kotlin
            implementation(compose.preview)
            implementation(libs.androidx.compose.adaptive)
            implementation(libs.androidx.compose.adaptive.layout)
            implementation(libs.androidx.compose.adaptive.navigation3)
            implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
```

(The four `adaptive` entries were missing from the original design — `DoseTrackerAppMain.kt`'s `NavigationSuiteScaffold`/`ListDetailSceneStrategy` need them and commonMain doesn't inherit them from anywhere else. Found during Task 10 implementation via a real "unresolved reference" compiler error; these four catalog entries already existed and were already used the same way by `:app`'s old `MainActivity.kt`, just never added to `:shared`.)

- [ ] **Step 9: Verify `:shared` compiles**

Run: `./gradlew :shared:assemble`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add shared/build.gradle.kts shared/src app/src
git commit -m "refactor: move screens and app navigation host to :shared, kotlinx-datetime formatting"
```

---

### Task 11: `:app` shell rewrite — Koin startup, slim MainActivity, Koin WorkManager

**Files:**
- Modify: `app/src/main/java/com/yhdista/dosetracker/DoseTrackerApp.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/MainActivity.kt`
- Create: `app/src/main/java/com/yhdista/dosetracker/di/AppModule.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/reminder/ReminderReceiver.kt`
- Modify: `app/src/main/java/com/yhdista/dosetracker/reminder/RescheduleWorker.kt`

**Interfaces:**
- Consumes: `dataModule`, `viewModelModule` (`:shared`, Tasks 6 and 9), `DoseTrackerAppMain`, `DoseTrackerTheme` (`:shared`, Task 10/7), `ReminderScheduler`, `NotificationHelper`.
- Produces: a working Koin dependency graph reachable from `MainActivity`, `ReminderReceiver`, and `RescheduleWorker`.

- [ ] **Step 1: Create the app-side Koin module (binds `DoseReminderScheduler` + `NotificationHelper`)**

```kotlin
package com.yhdista.dosetracker.di

import com.yhdista.dosetracker.reminder.DoseReminderScheduler
import com.yhdista.dosetracker.reminder.NotificationHelper
import com.yhdista.dosetracker.reminder.ReminderScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single<DoseReminderScheduler> { ReminderScheduler(androidContext()) }
    single { NotificationHelper(androidContext()) }
}
```

- [ ] **Step 2: Rewrite `DoseTrackerApp.kt` — `startKoin` instead of Hilt, Koin WorkManager factory**

```kotlin
package com.yhdista.dosetracker

import android.app.Application
import androidx.work.Configuration
import com.yhdista.dosetracker.di.appModule
import com.yhdista.dosetracker.di.dataModule
import com.yhdista.dosetracker.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.workmanager.factory.KoinWorkerFactory
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
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(KoinWorkerFactory())
            .build()
}
```

- [ ] **Step 3: Rewrite `MainActivity.kt` — slim shell calling into `:shared`**

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
        setContent {
            DoseTrackerTheme {
                DoseTrackerAppMain()
            }
        }
    }
}
```

- [ ] **Step 4: Rewrite `ReminderReceiver.kt` — Koin injection instead of Hilt**

`BroadcastReceiver`s are instantiated by the OS, so constructor injection is not possible — resolve `NotificationHelper` from Koin's started global instance instead (equivalent to what Hilt's `@AndroidEntryPoint` did via field injection):

```kotlin
package com.yhdista.dosetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationName = intent.getStringExtra("medicationName") ?: return
        val dosage = intent.getStringExtra("dosage") ?: ""
        val medicationId = intent.getLongOf("medicationId", -1L)

        if (medicationId != -1L) {
            val notificationHelper = GlobalContext.get().get<NotificationHelper>()
            notificationHelper.showNotification(medicationName, dosage, medicationId)
        }
    }

    private fun Intent.getLongOf(key: String, defaultValue: Long): Long {
        return if (hasExtra(key)) getLongExtra(key, defaultValue) else defaultValue
    }
}
```

`BroadcastReceiver`s are instantiated by the OS (no constructor injection possible), so resolving from `GlobalContext` (Koin's started global instance, set up in `DoseTrackerApp.onCreate`) is the standard Koin pattern here — equivalent to what Hilt's `@AndroidEntryPoint` did via field injection.

- [ ] **Step 5: Rewrite `RescheduleWorker.kt` — plain `CoroutineWorker`, resolved via Koin's `KoinWorkerFactory`**

```kotlin
package com.yhdista.dosetracker.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

class RescheduleWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: MedicationRepository,
    private val scheduler: DoseReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val medicationsResult = repository.getMedications()
            .filter { it is Data.Success }
            .first()

        if (medicationsResult is Data.Success) {
            medicationsResult.data.forEach { medication ->
                if (medication.reminderTime != null) {
                    scheduler.scheduleReminder(medication)
                }
            }
        }

        return Result.success()
    }
}
```

- [ ] **Step 6: Register the worker with Koin**

In `app/src/main/java/com/yhdista/dosetracker/di/AppModule.kt` (Step 1's file), add:

```kotlin
import androidx.work.WorkerParameters
import org.koin.androidx.workmanager.dsl.worker
```

and inside the `module { }` block, add:

```kotlin
    worker { params ->
        com.yhdista.dosetracker.reminder.RescheduleWorker(
            androidContext(),
            params.get<WorkerParameters>(),
            get(),
            get()
        )
    }
```

- [ ] **Step 7: Verify the full app builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. This is the second highest-risk step (Koin's WorkManager DSL — `koin-androidx-workmanager` package/import names have shifted across Koin versions; if `worker { }` or `KoinWorkerFactory` aren't found at the paths above, check the installed `koin-androidx-workmanager` artifact's actual package via `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep koin` and adjust the imports).

- [ ] **Step 8: Commit**

```bash
git add app/src
git commit -m "refactor: rewrite :app shell to use Koin instead of Hilt"
```

---

### Task 12: Remove Hilt from `app/build.gradle.kts`

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)

**Interfaces:**
- Consumes: nothing new — this is pure removal now that Tasks 1–11 replaced every Hilt usage.

- [ ] **Step 1: Remove the Hilt plugin from `app/build.gradle.kts`**

Remove this line from the `plugins { }` block:

```kotlin
    alias(libs.plugins.hilt)
```

- [ ] **Step 2: Remove Hilt/Room/Moshi dependencies now provided by `:shared`**

In `app/build.gradle.kts`'s `dependencies { }` block, remove:

```kotlin
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.hilt.android)
    implementation(libs.converter.moshi)
    implementation(libs.moshi.kotlin)
```
and:
```kotlin
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
    "ksp"(libs.hilt.compiler)
    "ksp"(libs.androidx.hilt.work.compiler)
```

Add, alongside the existing `implementation(project(":shared"))`:

```kotlin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
```

Keep `libs.retrofit`, `libs.okhttp`, `libs.logging.interceptor`, `libs.play.services.location`, the camera libs, and `libs.accompanist.permissions` (still used by `:shared`'s androidMain, Task 10) — none of these are Hilt/Room/Moshi-related.

- [ ] **Step 3: Add the `koin-androidx-workmanager` catalog entry** (if not already present from Task 1 — confirm it's there)

Verify `gradle/libs.versions.toml` has `koin-androidx-workmanager = { group = "io.insert-koin", name = "koin-androidx-workmanager" }` under `[libraries]` (added in Task 1 Step 1).

- [ ] **Step 4: Remove the Hilt plugin declaration from the root `build.gradle.kts`**

Remove:

```kotlin
    alias(libs.plugins.hilt) apply false
```

- [ ] **Step 5: Remove now-unused Hilt catalog entries**

In `gradle/libs.versions.toml`, remove the `hilt` version entry, `hiltWork` version entry, `hilt-android`/`hilt-compiler`/`androidx-hilt-work`/`androidx-hilt-work-compiler` library entries, and the `hilt` plugin entry — but only after confirming no file anywhere in the repo still references them:

Run: `command grep -rn "libs.hilt\|libs.androidx.hilt\|libs.plugins.hilt" app/build.gradle.kts shared/build.gradle.kts build.gradle.kts`
Expected: no output.

- [ ] **Step 6: Full clean build**

Run: `./gradlew clean :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, zero references to `dagger`/`hilt` remain anywhere under `app/src` or `shared/src` (confirm with `command grep -rln "dagger\|hilt" app/src shared/src` returning no output).

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts gradle/libs.versions.toml build.gradle.kts
git commit -m "chore: remove Hilt entirely, now fully on Koin"
```

---

### Task 13: Tests + full verification pass

**Files:**
- Create: `shared/src/androidHostTest/kotlin/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt`
- Delete: `app/src/test/java/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt`
- Keep unchanged: `app/src/test/java/com/yhdista/dosetracker/ExampleUnitTest.kt`, `app/src/androidTest/java/com/yhdista/dosetracker/ExampleInstrumentedTest.kt`

**Interfaces:**
- Consumes: `TodayViewModel` (Task 9).

- [ ] **Step 1: Move `TodayViewModelTest.kt`, switch to `kotlinx.datetime.Clock`**

```kotlin
package com.yhdista.dosetracker.ui.today

import androidx.lifecycle.SavedStateHandle
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
import kotlin.time.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

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
    fun `uiState emits success when repository returns data`() = runTest {
        val doses = listOf(
            Dose(id = 1, medicationId = 1, timestamp = Clock.System.now(), status = DoseStatus.PENDING)
        )
        whenever(repository.getDosesForDate(org.mockito.kotlin.any())).thenReturn(flowOf(Data.Success(doses)))

        val viewModel = TodayViewModel(repository, SavedStateHandle())

        val job = launch { viewModel.uiState.collect {} }

        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assert(finalState.doses is Data.Success)
        assertEquals(doses, (finalState.doses as Data.Success).data)

        job.cancel()
    }
}
```

Delete `app/src/test/java/com/yhdista/dosetracker/ui/today/TodayViewModelTest.kt`.

- [ ] **Step 2: Run the moved test**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.yhdista.dosetracker.ui.today.TodayViewModelTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 3: Run the remaining `:app` tests**

Run: `./gradlew :app:testDebugUnitTest :app:connectedAndroidTest`
Expected: `BUILD SUCCESSFUL` (the instrumented test requires a connected device/emulator — if none is available, run `./gradlew :app:testDebugUnitTest` only and note the instrumented test was not exercised).

- [ ] **Step 4: Full assemble + manual smoke test**

Run: `./gradlew clean :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

Install and manually verify on a device/emulator:
- `./gradlew :app:installDebug`
- Launch DoseTracker, confirm the notification permission prompt appears (Android 13+) and the Today/Meds/History bottom navigation renders with the five seeded medications reachable from the Meds tab.
- Add a dose from a medication's detail screen, confirm it appears in History with the correct time.
- Toggle a dose's status on the Today screen, confirm the card visually updates.
- Confirm a previously-scheduled reminder still triggers a notification (or, more practically, that scheduling one via adding a medication with a `reminderTime` doesn't crash — real-time verification of the alarm firing is optional given `RescheduleWorker`/`ReminderScheduler` logic was not behaviorally changed, only its DI wiring).

- [ ] **Step 5: Commit**

```bash
git add shared/src app/src
git commit -m "test: move TodayViewModelTest to :shared, switch to kotlinx-datetime Clock"
```

---

## Self-Review Notes

- **Spec coverage:** Every section of `docs/superpowers/specs/2026-07-18-kmp-migration-phase1-design.md` is covered — module split (Tasks 1–2), domain/data move (Tasks 3–6), Hilt→Koin (Tasks 6, 9, 11, 12), `DoseReminderScheduler` abstraction (Task 5), Room KMP (Task 4), UI move (Tasks 7–10), Firebase/App Distribution untouched (explicitly called out, not modified by any task), verification plan (Task 13).
- **kotlinx-datetime scope correction:** the approved spec only mentioned domain/data date types; this plan additionally converts every UI-layer `java.time` usage (discovered during file-by-file planning) per the user's explicit follow-up decision to do the full conversion now rather than deferring UI to Phase 2.
- **Highest-risk steps flagged inline:** Task 4 Step 12 (Room multiplatform driver/callback API) and Task 11 Step 7 (Koin WorkManager DSL) are the two areas most likely to need a signature adjustment against the actually-resolved library versions — both have a concrete verification command and guidance on what to check if it fails.
- **Type consistency check:** `DoseReminderScheduler.scheduleReminder(medication: Medication)`/`cancelReminder(medicationId: Long)` (Task 5) match exactly what `MedicationRepositoryImpl` calls (Task 5) and what `:app`'s `ReminderScheduler` implements (Task 5) and what `RescheduleWorker` calls (Task 11). `MedicationRepository.getDosesForDate(date: LocalDate)` (Task 3) matches the `TodayViewModel` call site (Task 9) and the `MedicationRepositoryImpl` override (Task 5).
