package dev.section.streamly.search.data

import dev.section.streamly.home.domain.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface SearchRepository {
    /** Movies and shows matching [query]; fails if either request fails. */
    suspend fun search(query: String): Result<List<MediaItem>>
}

class DefaultSearchRepository(private val source: SearchSource) : SearchRepository {

    override suspend fun search(query: String): Result<List<MediaItem>> = try {
        coroutineScope {
            val movies = async { source.searchMovies(query) }
            val shows = async { source.searchShows(query) }
            val movieItems = movies.await().results.mapNotNull { it.toDomain(isMovie = true) }
            val showItems = shows.await().results.mapNotNull { it.toDomain(isMovie = false) }
            Result.success(interleave(movieItems, showItems).distinctBy { it.isMovie to it.id })
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
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
