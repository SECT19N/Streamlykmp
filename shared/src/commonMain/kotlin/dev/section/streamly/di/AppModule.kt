package dev.section.streamly.di

import dev.section.streamly.livetv.LiveTvViewModel
import dev.section.streamly.livetv.data.BundledChannelSource
import dev.section.streamly.livetv.data.ChannelRepository
import dev.section.streamly.livetv.data.ChannelSource
import dev.section.streamly.livetv.data.DefaultChannelRepository
import dev.section.streamly.player.PlayerViewModel
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule: Module = module {
    single { Json { ignoreUnknownKeys = true } }

    // Live TV. To load the list from a URL, bind a remote ChannelSource here instead.
    single<ChannelSource> { BundledChannelSource(get()) }
    single<ChannelRepository> { DefaultChannelRepository(get()) }
    viewModel { LiveTvViewModel(get(), get()) }

    // Player: the channel id comes from the navigation route.
    viewModel { (channelId: String) -> PlayerViewModel(channelId, get(), get()) }
}
