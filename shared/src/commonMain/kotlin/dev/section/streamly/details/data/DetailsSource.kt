package dev.section.streamly.details.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface DetailsSource {
    suspend fun movie(id: Int): TmdbDetailsDto
    suspend fun show(id: Int): TmdbDetailsDto
    suspend fun collection(id: Int): TmdbCollectionDto
}

class TmdbDetailsSource(private val client: HttpClient) : DetailsSource {
    override suspend fun movie(id: Int): TmdbDetailsDto =
        client.get("movie/$id") { parameter("append_to_response", "credits,release_dates") }.body()

    override suspend fun show(id: Int): TmdbDetailsDto =
        client.get("tv/$id") { parameter("append_to_response", "credits,content_ratings") }.body()

    override suspend fun collection(id: Int): TmdbCollectionDto =
        client.get("collection/$id").body()
}
