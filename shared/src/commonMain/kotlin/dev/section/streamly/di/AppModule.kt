package dev.section.streamly.di

import org.koin.core.module.Module
import org.koin.dsl.module

val appModule: Module = module {
    // Bindings are added per feature (network client, repositories, ViewModels).
}
