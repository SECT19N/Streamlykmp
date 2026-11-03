package dev.section.streamly.search.data

import dev.section.streamly.home.data.TmdbPageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface SearchSource {
    suspend fun searchMovies(query: String, page: Int = 1): TmdbPageDto
    suspend fun searchShows(query: String, page: Int = 1): TmdbPageDto
}

class TmdbSearchSource(private val client: HttpClient) : SearchSource {
    override suspend fun searchMovies(query: String, page: Int): TmdbPageDto = search("search/movie", query, page)
    override suspend fun searchShows(query: String, page: Int): TmdbPageDto = search("search/tv", query, page)

    private suspend fun search(path: String, query: String, page: Int): TmdbPageDto =
        client.get(path) {
            parameter("query", query)
            parameter("page", page)
        }.body()
}
