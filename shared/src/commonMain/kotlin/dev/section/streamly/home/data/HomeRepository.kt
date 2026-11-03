package dev.section.streamly.home.data

import dev.section.streamly.home.domain.HomeRow
import dev.section.streamly.home.domain.HomeRowType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface HomeRepository {
    /** Fails if any row can't be loaded. Rows with no usable items are dropped. */
    suspend fun getRows(): Result<List<HomeRow>>
}

class DefaultHomeRepository(private val source: HomeSource) : HomeRepository {

    override suspend fun getRows(): Result<List<HomeRow>> = try {
        coroutineScope {
            val trendingMovies = async { source.trendingMovies() }
            val trendingShows = async { source.trendingShows() }
            val popularMovies = async { source.popularMovies() }
            val popularShows = async { source.popularShows() }
            Result.success(
                listOf(
                    row(HomeRowType.TrendingMovies, trendingMovies.await(), isMovie = true),
                    row(HomeRowType.TrendingShows, trendingShows.await(), isMovie = false),
                    row(HomeRowType.PopularMovies, popularMovies.await(), isMovie = true),
                    row(HomeRowType.PopularShows, popularShows.await(), isMovie = false),
                ).filter { it.items.isNotEmpty() },
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun row(type: HomeRowType, page: TmdbPageDto, isMovie: Boolean) =
        HomeRow(type, page.results.mapNotNull { it.toDomain(isMovie) }.distinctBy { it.id })
}
