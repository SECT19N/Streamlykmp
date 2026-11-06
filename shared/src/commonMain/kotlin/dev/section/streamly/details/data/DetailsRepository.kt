package dev.section.streamly.details.data

import dev.section.streamly.core.cache.SessionCache
import dev.section.streamly.details.domain.MediaCollection
import dev.section.streamly.details.domain.MediaDetails
import dev.section.streamly.home.domain.MediaItem
import kotlinx.coroutines.CancellationException

interface DetailsRepository {
    /**
     * Fails if the request fails or the record has no usable id or title. A recent result is
     * reused unless [refresh] is set; if loading fails, an older result is returned instead.
     */
    suspend fun getDetails(
        id: Int,
        isMovie: Boolean,
        refresh: Boolean = false
    ): Result<MediaDetails>
}

class DefaultDetailsRepository(
    private val source: DetailsSource,
    private val cache: SessionCache<Pair<Int, Boolean>, MediaDetails> = SessionCache(),
) : DetailsRepository {

    override suspend fun getDetails(
        id: Int,
        isMovie: Boolean,
        refresh: Boolean
    ): Result<MediaDetails> {
        val key = id to isMovie
        if (!refresh) cache.getFresh(key)?.let { return Result.success(it) }
        return try {
            val dto = if (isMovie) source.movie(id) else source.show(id)
            val details = dto.toDomain(isMovie)?.let { withCollection(it, dto) }
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

    /** The collection is a bonus: if it can't be loaded the details are still shown without it. */
    private suspend fun withCollection(details: MediaDetails, dto: TmdbDetailsDto): MediaDetails {
        val ref = dto.belongsToCollection ?: return details
        val collectionId = ref.id ?: return details
        val name = ref.name?.takeIf { it.isNotBlank() } ?: return details
        val parts = try {
            source.collection(collectionId).parts
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return details
        }
        val others = parts
            // Undated entries (announced films) go last.
            .sortedBy { it.releaseDate?.takeIf(String::isNotBlank) ?: "9999" }
            .mapNotNull { part ->
                val partId = part.id ?: return@mapNotNull null
                if (partId == details.id) return@mapNotNull null
                val title = part.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                MediaItem(
                    partId,
                    title,
                    part.posterPath?.takeIf { it.isNotBlank() },
                    isMovie = true
                )
            }
        return if (others.isEmpty()) details else details.copy(
            collection = MediaCollection(
                name,
                others
            )
        )
    }

    private fun staleOr(key: Pair<Int, Boolean>, error: Exception): Result<MediaDetails> =
        cache.getStale(key)?.let { Result.success(it) } ?: Result.failure(error)
}
