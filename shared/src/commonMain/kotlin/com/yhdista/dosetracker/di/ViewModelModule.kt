package com.yhdista.dosetracker.di

import com.yhdista.dosetracker.ui.catalog.MedicationCatalogViewModel
import com.yhdista.dosetracker.ui.confirm.ConfirmDoseViewModel
import com.yhdista.dosetracker.ui.cycle.CreateCycleViewModel
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
    viewModel { MedicationCatalogViewModel(get()) }
    viewModel { AddDoseViewModel(get(), get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { TodayViewModel(get(), get()) }
    viewModel { MedicationDetailViewModel(get(), get(), get(), get()) }
    viewModel { ConfirmDoseViewModel(get(), get()) }
    viewModel { ReportViewModel(get()) }
    viewModel { MedicationReportViewModel(get(), get()) }
    viewModel { CreateCycleViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { DebugViewModel(get(), get(), get()) }
}
