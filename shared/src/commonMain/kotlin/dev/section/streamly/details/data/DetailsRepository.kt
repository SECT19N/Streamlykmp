package dev.section.streamly.details.data

import dev.section.streamly.core.cache.SessionCache
import dev.section.streamly.details.domain.MediaDetails
import kotlinx.coroutines.CancellationException

interface DetailsRepository {
    /**
     * Fails if the request fails or the record has no usable id or title. A recent result is
     * reused unless [refresh] is set; if loading fails, an older result is returned instead.
     */
    suspend fun getDetails(id: Int, isMovie: Boolean, refresh: Boolean = false): Result<MediaDetails>
}

class DefaultDetailsRepository(
    private val source: DetailsSource,
    private val cache: SessionCache<Pair<Int, Boolean>, MediaDetails> = SessionCache(),
) : DetailsRepository {

    override suspend fun getDetails(id: Int, isMovie: Boolean, refresh: Boolean): Result<MediaDetails> {
        val key = id to isMovie
        if (!refresh) cache.getFresh(key)?.let { return Result.success(it) }
        return try {
            val dto = if (isMovie) source.movie(id) else source.show(id)
            val details = dto.toDomain(isMovie)
            if (details != null) {
                cache.put(key, details)
                Result.success(details)
            } else {
                staleOr(key, IllegalStateException("Incomplete details for $id"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            staleOr(key, e)
        }
    }

    private fun staleOr(key: Pair<Int, Boolean>, error: Exception): Result<MediaDetails> =
        cache.getStale(key)?.let { Result.success(it) } ?: Result.failure(error)
}
