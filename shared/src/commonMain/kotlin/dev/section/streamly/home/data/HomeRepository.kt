package dev.section.streamly.home.data

import dev.section.streamly.core.cache.SessionCache
import dev.section.streamly.home.domain.HomeRow
import dev.section.streamly.home.domain.HomeRowType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface HomeRepository {
    /**
     * Fails if any row can't be loaded. Rows with no usable items are dropped. A recent result is
     * reused unless [refresh] is set; if loading fails, an older result is returned instead.
     */
    suspend fun getRows(refresh: Boolean = false): Result<List<HomeRow>>

    /** The next page of [row], appended to its items. Fails without touching the row. */
    suspend fun loadMore(row: HomeRow): Result<HomeRow>
}

class DefaultHomeRepository(
    private val source: HomeSource,
    private val cache: SessionCache<String, List<HomeRow>> = SessionCache(),
) : HomeRepository {

    override suspend fun getRows(refresh: Boolean): Result<List<HomeRow>> {
        if (!refresh) cache.getFresh(CacheKey)?.let { return Result.success(it) }
        return try {
            coroutineScope {
                val trendingMovies = async { source.trendingMovies() }
                val trendingShows = async { source.trendingShows() }
                val popularMovies = async { source.popularMovies() }
                val popularShows = async { source.popularShows() }
                val rows = listOf(
                    row(HomeRowType.TrendingMovies, trendingMovies.await(), isMovie = true),
                    row(HomeRowType.TrendingShows, trendingShows.await(), isMovie = false),
                    row(HomeRowType.PopularMovies, popularMovies.await(), isMovie = true),
                    row(HomeRowType.PopularShows, popularShows.await(), isMovie = false),
                ).filter { it.items.isNotEmpty() }
                cache.put(CacheKey, rows)
                Result.success(rows)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cache.getStale(CacheKey)?.let { Result.success(it) } ?: Result.failure(e)
        }
    }

    override suspend fun loadMore(row: HomeRow): Result<HomeRow> = try {
        val next = row.page + 1
        val page = when (row.type) {
            HomeRowType.TrendingMovies -> source.trendingMovies(next)
            HomeRowType.TrendingShows -> source.trendingShows(next)
            HomeRowType.PopularMovies -> source.popularMovies(next)
            HomeRowType.PopularShows -> source.popularShows(next)
        }
        val more = page.results.mapNotNull { it.toDomain(row.type.isMovie) }
        val merged = row.copy(
            items = (row.items + more).distinctBy { it.id },
            page = next,
            totalPages = page.totalPages,
        )
        // Keep the cached first-load result in step so a revisit shows what was already scrolled.
        cache.getStale(CacheKey)?.let { rows ->
            cache.put(CacheKey, rows.map { if (it.type == row.type) merged else it })
        }
        Result.success(merged)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun row(type: HomeRowType, page: TmdbPageDto, isMovie: Boolean) = HomeRow(
        type = type,
        items = page.results.mapNotNull { it.toDomain(isMovie) }.distinctBy { it.id },
        page = page.page,
        totalPages = page.totalPages,
    )

    private companion object {
        const val CacheKey = "rows"
    }
}
