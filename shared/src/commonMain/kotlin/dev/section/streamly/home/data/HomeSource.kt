package dev.section.streamly.home.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

interface HomeSource {
    suspend fun trendingMovies(): TmdbPageDto
    suspend fun trendingShows(): TmdbPageDto
    suspend fun popularMovies(): TmdbPageDto
    suspend fun popularShows(): TmdbPageDto
}

class TmdbHomeSource(private val client: HttpClient) : HomeSource {
    override suspend fun trendingMovies(): TmdbPageDto = client.get("trending/movie/week").body()
    override suspend fun trendingShows(): TmdbPageDto = client.get("trending/tv/week").body()
    override suspend fun popularMovies(): TmdbPageDto = client.get("movie/popular").body()
    override suspend fun popularShows(): TmdbPageDto = client.get("tv/popular").body()
}
