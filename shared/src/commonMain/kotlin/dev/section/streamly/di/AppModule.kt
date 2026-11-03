package dev.section.streamly.di

import dev.section.streamly.core.network.createTmdbClient
import dev.section.streamly.details.DetailsViewModel
import dev.section.streamly.details.data.DefaultDetailsRepository
import dev.section.streamly.details.data.DetailsRepository
import dev.section.streamly.details.data.DetailsSource
import dev.section.streamly.details.data.TmdbDetailsSource
import dev.section.streamly.home.HomeViewModel
import dev.section.streamly.home.data.DefaultHomeRepository
import dev.section.streamly.home.data.HomeRepository
import dev.section.streamly.home.data.HomeSource
import dev.section.streamly.home.data.TmdbHomeSource
import dev.section.streamly.livetv.LiveTvViewModel
import dev.section.streamly.livetv.data.BundledChannelSource
import dev.section.streamly.livetv.data.ChannelRepository
import dev.section.streamly.livetv.data.ChannelSource
import dev.section.streamly.livetv.data.DefaultChannelRepository
import dev.section.streamly.player.PlayerViewModel
import dev.section.streamly.search.SearchViewModel
import dev.section.streamly.search.data.DefaultSearchRepository
import dev.section.streamly.search.data.SearchRepository
import dev.section.streamly.search.data.SearchSource
import dev.section.streamly.search.data.TmdbSearchSource
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

    // Home (TMDB)
    single { createTmdbClient(get()) }
    single<HomeSource> { TmdbHomeSource(get()) }
    single<HomeRepository> { DefaultHomeRepository(get()) }
    viewModel { HomeViewModel(get(), get()) }

    // Details (TMDB): the id and type come from the navigation route.
    single<DetailsSource> { TmdbDetailsSource(get()) }
    single<DetailsRepository> { DefaultDetailsRepository(get()) }
    viewModel { (id: Int, isMovie: Boolean) -> DetailsViewModel(id, isMovie, get(), get()) }

    // Search (TMDB)
    single<SearchSource> { TmdbSearchSource(get()) }
    single<SearchRepository> { DefaultSearchRepository(get()) }
    viewModel { SearchViewModel(get(), get()) }

    // Player: the channel id comes from the navigation route.
    viewModel { (channelId: String) -> PlayerViewModel(channelId, get(), get()) }
}
