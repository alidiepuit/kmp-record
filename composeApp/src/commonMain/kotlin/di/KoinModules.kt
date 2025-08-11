package di

import home.HomeScreenModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun appModule(): Module = module {
    viewModel { HomeScreenModel() }
}
