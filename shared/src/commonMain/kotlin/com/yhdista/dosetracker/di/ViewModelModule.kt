package com.yhdista.dosetracker.di

import com.yhdista.dosetracker.ui.catalog.MedicationCatalogViewModel
import com.yhdista.dosetracker.ui.confirm.ConfirmDoseViewModel
import com.yhdista.dosetracker.ui.cycle.CreateCycleViewModel
import com.yhdista.dosetracker.ui.cycle.CycleHistoryViewModel
import com.yhdista.dosetracker.ui.cycle.CycleSettingsViewModel
import com.yhdista.dosetracker.ui.cycle.CycleWeekEditorViewModel
import com.yhdista.dosetracker.ui.cycle.CycleWeekListViewModel
import com.yhdista.dosetracker.ui.dose.AddDoseViewModel
import com.yhdista.dosetracker.ui.history.HistoryViewModel
import com.yhdista.dosetracker.ui.medicationdetail.MedicationDetailViewModel
import com.yhdista.dosetracker.ui.report.MedicationReportViewModel
import com.yhdista.dosetracker.ui.report.ReportViewModel
import com.yhdista.dosetracker.ui.today.TodayViewModel
import com.yhdista.dosetracker.ui.settings.SettingsViewModel
import com.yhdista.dosetracker.ui.debug.DebugViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { MedicationCatalogViewModel(get(), get()) }
    viewModel { (medicationId: Long) -> AddDoseViewModel(get(), get(), medicationId, get()) }
    viewModel { HistoryViewModel(get(), get()) }
    viewModel { TodayViewModel(get(), get(), get()) }
    viewModel { (medicationId: Long) ->
        MedicationDetailViewModel(get(), get(), get(), get(), medicationId, get())
    }
    viewModel { (doseId: Long) -> ConfirmDoseViewModel(get(), doseId, get()) }
    viewModel { ReportViewModel(get()) }
    viewModel { (medicationId: Long) -> MedicationReportViewModel(get(), get(), medicationId, get()) }
    viewModel { CreateCycleViewModel(get(), get()) }
    viewModel { (cycleId: Long, weekIndex: Int) ->
        CycleWeekEditorViewModel(get(), get(), get(), get(), get(), cycleId, weekIndex, get())
    }
    viewModel { (cycleId: Long) -> CycleWeekListViewModel(get(), cycleId, get()) }
    viewModel { CycleHistoryViewModel(get()) }
    viewModel { CycleSettingsViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { DebugViewModel(get(), get(), get()) }
}
