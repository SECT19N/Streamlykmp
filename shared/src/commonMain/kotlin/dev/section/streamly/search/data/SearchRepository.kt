package dev.section.streamly.search.data

import dev.section.streamly.core.cache.SessionCache
import dev.section.streamly.home.domain.MediaItem
import dev.section.streamly.search.domain.SearchPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface SearchRepository {
    /**
     * One page of movies and shows matching [query]; fails if either request fails. A recent
     * result is reused unless [refresh] is set; if loading fails, an older result is returned.
     */
    suspend fun search(query: String, page: Int = 1, refresh: Boolean = false): Result<SearchPage>
}

class DefaultSearchRepository(
    private val source: SearchSource,
    private val cache: SessionCache<Pair<String, Int>, SearchPage> = SessionCache(),
) : SearchRepository {

    override suspend fun search(query: String, page: Int, refresh: Boolean): Result<SearchPage> {
        val key = query.lowercase() to page
        if (!refresh) cache.getFresh(key)?.let { return Result.success(it) }
        return try {
            coroutineScope {
                val movies = async { source.searchMovies(query, page) }
                val shows = async { source.searchShows(query, page) }
                val moviePage = movies.await()
                val showPage = shows.await()
                val items = interleave(
                    moviePage.results.mapNotNull { it.toDomain(isMovie = true) },
                    showPage.results.mapNotNull { it.toDomain(isMovie = false) },
                ).distinctBy { it.isMovie to it.id }
                val result = SearchPage(items, page, maxOf(moviePage.totalPages, showPage.totalPages))
                cache.put(key, result)
                Result.success(result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cache.getStale(key)?.let { Result.success(it) } ?: Result.failure(e)
        }
    }

    /** TMDB already ranks each list by relevance, so alternate to keep the best of both near the top. */
    private fun interleave(a: List<MediaItem>, b: List<MediaItem>): List<MediaItem> = buildList {
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            a.getOrNull(i)?.let(::add)
            b.getOrNull(i)?.let(::add)
        }
    }
}
