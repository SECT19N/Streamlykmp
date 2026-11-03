package dev.section.streamly.search.data

import dev.section.streamly.home.data.TmdbPageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface SearchSource {
    suspend fun searchMovies(query: String): TmdbPageDto
    suspend fun searchShows(query: String): TmdbPageDto
}

class TmdbSearchSource(private val client: HttpClient) : SearchSource {
    override suspend fun searchMovies(query: String): TmdbPageDto =
        client.get("search/movie") { parameter("query", query) }.body()

    override suspend fun searchShows(query: String): TmdbPageDto =
        client.get("search/tv") { parameter("query", query) }.body()
}
