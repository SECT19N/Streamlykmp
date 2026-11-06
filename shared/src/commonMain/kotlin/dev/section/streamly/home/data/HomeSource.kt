package dev.section.streamly.home.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface HomeSource {
    suspend fun trendingMovies(page: Int = 1): TmdbPageDto
    suspend fun trendingShows(page: Int = 1): TmdbPageDto
    suspend fun popularMovies(page: Int = 1): TmdbPageDto
    suspend fun popularShows(page: Int = 1): TmdbPageDto
}

class TmdbHomeSource(private val client: HttpClient) : HomeSource {
    override suspend fun trendingMovies(page: Int): TmdbPageDto = get("trending/movie/week", page)
    override suspend fun trendingShows(page: Int): TmdbPageDto = get("trending/tv/week", page)
    override suspend fun popularMovies(page: Int): TmdbPageDto = get("movie/popular", page)
    override suspend fun popularShows(page: Int): TmdbPageDto = get("tv/popular", page)

    private suspend fun get(path: String, page: Int): TmdbPageDto =
        client.get(path) { parameter("page", page) }.body()
}
