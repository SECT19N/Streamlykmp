package dev.section.streamly.home

import dev.section.streamly.home.data.DefaultHomeRepository
import dev.section.streamly.home.data.HomeSource
import dev.section.streamly.home.data.TmdbMediaDto
import dev.section.streamly.home.data.TmdbPageDto
import dev.section.streamly.home.domain.HomeRowType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeSource(
        val movies: TmdbPageDto = TmdbPageDto(),
        val shows: TmdbPageDto = TmdbPageDto(),
        val fail: Boolean = false,
    ) : HomeSource {
        private fun page(p: TmdbPageDto) = if (fail) error("boom") else p
        override suspend fun trendingMovies() = page(movies)
        override suspend fun trendingShows() = page(shows)
        override suspend fun popularMovies() = page(movies)
        override suspend fun popularShows() = page(shows)
    }

    @Test
    fun parsesMovieAndShowPagesIgnoringUnknownKeys() {
        val movie = json.decodeFromString<TmdbPageDto>(
            """{"page":1,"results":[{"id":1,"title":"Heat","poster_path":"/h.jpg","adult":false}]}""",
        ).results.single().toDomain(isMovie = true)
        assertEquals("Heat", movie?.title)
        assertEquals("https://image.tmdb.org/t/p/w342/h.jpg", movie?.posterUrl())

        val show = json.decodeFromString<TmdbPageDto>(
            """{"results":[{"id":2,"name":"Dark","poster_path":null}]}""",
        ).results.single().toDomain(isMovie = false)
        assertEquals("Dark", show?.title)
        assertNull(show?.posterUrl())
    }

    @Test
    fun dropsEntriesWithoutIdOrTitle() {
        assertNull(TmdbMediaDto(id = null, title = "x").toDomain(true))
        assertNull(TmdbMediaDto(id = 1, title = " ").toDomain(true))
        assertNull(TmdbMediaDto(id = 1, title = "Movie title").toDomain(isMovie = false))
    }

    @Test
    fun repositoryBuildsRowsAndDropsEmptyOnes() = runTest {
        val source = FakeSource(
            movies = TmdbPageDto(listOf(TmdbMediaDto(1, "A"), TmdbMediaDto(1, "A dup"))),
            shows = TmdbPageDto(),
        )
        val rows = DefaultHomeRepository(source).getRows().getOrThrow()
        assertEquals(listOf(HomeRowType.TrendingMovies, HomeRowType.PopularMovies), rows.map { it.type })
        assertEquals(1, rows.first().items.size)
    }

    @Test
    fun repositoryFailureBecomesResultFailure() = runTest {
        assertTrue(DefaultHomeRepository(FakeSource(fail = true)).getRows().isFailure)
    }
}
