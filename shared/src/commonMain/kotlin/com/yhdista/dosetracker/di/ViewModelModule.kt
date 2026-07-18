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
