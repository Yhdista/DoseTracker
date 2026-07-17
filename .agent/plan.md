# Project Plan

DoseTracker: A multiplatform medication assistant app primarily for tracking steroid supplements and other medications from a catalog. It should track dose amount and timing, and provide notifications for scheduled doses. The architecture must follow the "mzdravi-architecture" (Clean Architecture, strict type safety, Hilt for DI, Room for data, MVVM/MVI). Name: DoseTracker.

## Project Brief

# Project Brief: DoseTracker

DoseTracker is a modern Android application designed to help users manage and track their medication schedules, with a specific focus on steroid supplements and cataloged medications. The app ensures users never miss a dose through intelligent scheduling and notifications.

## Features
- **Medication Catalog & Logging**: Easily search and add medications from a catalog, logging specific dose amounts and exact timing.
- **Automated Reminders**: Push notifications for upcoming and scheduled doses to ensure strict adherence to medication protocols.
- **Dose History Log**: A comprehensive view of past doses, allowing users to track their consistency over time.
- **Adaptive Dashboard**: A "Today" view that highlights immediate tasks and provides a quick-entry method for logging doses.

## High-Level Technical Stack
- **Kotlin**: The core programming language for modern Android development.
- **Jetpack Compose**: A declarative UI toolkit used for building the entire user interface.
- **Jetpack Navigation 3**: A state-driven navigation system to manage app transitions and deep linking.
- **Compose Material Adaptive**: Provides a responsive layout system that ensures the app looks great on phones, tablets, and foldables.
- **Hilt**: Dependency injection framework to support modularity and the "mzdravi-architecture" requirements.
- **Room**: A robust persistence library for local data storage, essential for tracking history and schedules offline.
- **Coroutines & Flow**: Used for efficient, non-blocking asynchronous operations and reactive data handling.
- **Clean Architecture (MVVM/MVI)**: A structured architectural approach ensuring type safety, testability, and clear separation of concerns.

---
> [!NOTE]
> The UI Design Image section is omitted due to tool unavailability.

## Implementation Steps
**Total Duration:** 28m 28s

### Task_1_Domain_Data_DI: Establish the Core Foundation: Define Medication and Dose entities, configure Room database, and set up Hilt for Dependency Injection following 'mzdravi-architecture'.
- **Status:** COMPLETED
- **Updates:** Completed Task_1_Domain_Data_DI.
- **Acceptance Criteria:**
  - Medication and Dose entities defined with strict type safety
  - Room Database and DAOs implemented
  - Repository layer with Hilt DI modules configured
- **Duration:** 3m 18s

### Task_2_UI_Architecture_TodayView: Build UI Infrastructure: Set up Jetpack Navigation 3 and implement the 'Today' adaptive dashboard using Compose Material Adaptive.
- **Status:** COMPLETED
- **Updates:** Completed Task_2_UI_Architecture_TodayView.
- **Acceptance Criteria:**
  - Navigation 3 graph managing app state
  - Dashboard displays today's medication schedule
  - UI adapts correctly to phone and tablet layouts
- **Duration:** 5m 50s

### Task_3_Medication_Logging_History: Feature Implementation: Develop the medication catalog search, dose logging flow, and history view using MVI/MVVM patterns.
- **Status:** COMPLETED
- **Updates:** Completed Task_3_Medication_Logging_History.
- **Acceptance Criteria:**
  - Users can search and select medications from catalog
  - Dose amounts and timing are correctly logged and persisted
  - Dose History Log displays previous entries accurately
- **Duration:** 4m 41s

### Task_4_Notifications_Scheduling: Reliability & Reminders: Implement automated dose reminders and scheduling notifications.
- **Status:** COMPLETED
- **Updates:** Completed Task_4_Notifications_Scheduling.
- **Acceptance Criteria:**
  - Dose reminders triggered via notifications at scheduled times
  - Background workers handle schedule updates and triggers
- **Duration:** 3m 42s

### Task_5_Run_and_Verify: Final Validation: Perform a complete application build, run stability tests, and verify alignment with user requirements.
- **Status:** COMPLETED
- **Updates:** Final verification successful.
- Fixed WorkManager + Hilt integration for RescheduleWorker.
- Refined Today screen to show medication names.
- Added 'Add Medication' feature.
- Verified stability and adaptive UI on tablet and phone.
- Clean Architecture and 'mzdravi-architecture' guidelines followed.
- **Acceptance Criteria:**
  - Build pass
  - App does not crash
  - make sure all existing tests pass
  - critic_agent verifies application stability and UI requirements
- **Duration:** 10m 57s

