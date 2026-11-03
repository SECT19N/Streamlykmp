package dev.section.streamly.details.data

import dev.section.streamly.details.domain.MediaDetails
import kotlinx.coroutines.CancellationException

interface DetailsRepository {
    /** Fails if the request fails or the record has no usable id or title. */
    suspend fun getDetails(id: Int, isMovie: Boolean): Result<MediaDetails>
}

class DefaultDetailsRepository(private val source: DetailsSource) : DetailsRepository {

    override suspend fun getDetails(id: Int, isMovie: Boolean): Result<MediaDetails> = try {
        val dto = if (isMovie) source.movie(id) else source.show(id)
        dto.toDomain(isMovie)?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Incomplete details for $id"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
