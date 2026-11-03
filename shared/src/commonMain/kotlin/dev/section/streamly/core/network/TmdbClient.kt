package dev.section.streamly.core.network

import dev.section.streamly.BuildKonfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"

fun createTmdbClient(json: Json, token: String = BuildKonfig.TMDB_READ_TOKEN): HttpClient =
    HttpClient {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        defaultRequest {
            url(TMDB_BASE_URL)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
