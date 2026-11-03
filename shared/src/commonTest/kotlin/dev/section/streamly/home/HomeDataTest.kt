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
        var fail: Boolean = false,
    ) : HomeSource {
        private fun page(p: TmdbPageDto) = if (fail) error("boom") else p
        var calls = 0
        val requestedPages = mutableListOf<Int>()
        private fun page(p: TmdbPageDto, page: Int): TmdbPageDto {
            calls++
            requestedPages += page
            return page(p)
        }
        override suspend fun trendingMovies(page: Int) = page(movies, page)
        override suspend fun trendingShows(page: Int) = page(shows, page)
        override suspend fun popularMovies(page: Int) = page(movies, page)
        override suspend fun popularShows(page: Int) = page(shows, page)
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
        assertEquals(
            listOf(HomeRowType.TrendingMovies, HomeRowType.PopularMovies),
            rows.map { it.type })
        assertEquals(1, rows.first().items.size)
    }

    @Test
    fun parsesPagingFields() {
        val page = json.decodeFromString<TmdbPageDto>("""{"page":2,"total_pages":7,"results":[]}""")
        assertEquals(2, page.page)
        assertEquals(7, page.totalPages)
        // Absent fields default to a single page, so nothing tries to load more.
        assertEquals(1, json.decodeFromString<TmdbPageDto>("""{"results":[]}""").totalPages)
    }

    @Test
    fun loadMoreAppendsNextPageAndDedupes() = runTest {
        val source = FakeSource(movies = TmdbPageDto(listOf(TmdbMediaDto(1, "A"), TmdbMediaDto(2, "B")), page = 1, totalPages = 3))
        val repo = DefaultHomeRepository(source)
        val row = repo.getRows().getOrThrow().first { it.type == HomeRowType.TrendingMovies }
        assertTrue(row.canLoadMore)
        source.requestedPages.clear()

        val more = repo.loadMore(row).getOrThrow()
        assertEquals(listOf(2), source.requestedPages)
        assertEquals(2, more.page)
        // The fake returns the same ids again, so appending must not duplicate them.
        assertEquals(listOf(1, 2), more.items.map { it.id })
    }

    @Test
    fun lastPageCannotLoadMore() = runTest {
        val row = DefaultHomeRepository(FakeSource(movies = TmdbPageDto(listOf(TmdbMediaDto(1, "A"))))).getRows()
            .getOrThrow().first()
        assertEquals(false, row.canLoadMore)
    }

    @Test
    fun loadMoreFailureIsAResultFailure() = runTest {
        val source = FakeSource(movies = TmdbPageDto(listOf(TmdbMediaDto(1, "A")), totalPages = 2))
        val repo = DefaultHomeRepository(source)
        val row = repo.getRows().getOrThrow().first()
        source.fail = true
        assertTrue(repo.loadMore(row).isFailure)
    }

    @Test
    fun secondCallUsesCacheAndRefreshBypassesIt() = runTest {
        val source = FakeSource(movies = TmdbPageDto(listOf(TmdbMediaDto(1, "A"))))
        val repo = DefaultHomeRepository(source)
        repo.getRows()
        val callsAfterFirst = source.calls
        repo.getRows()
        assertEquals(callsAfterFirst, source.calls)
        repo.getRows(refresh = true)
        assertTrue(source.calls > callsAfterFirst)
    }

    @Test
    fun failedRefreshFallsBackToCachedRows() = runTest {
        val source = FakeSource(movies = TmdbPageDto(listOf(TmdbMediaDto(1, "A"))))
        val repo = DefaultHomeRepository(source)
        val first = repo.getRows().getOrThrow()
        source.fail = true
        assertEquals(first, repo.getRows(refresh = true).getOrThrow())
    }

    @Test
    fun repositoryFailureBecomesResultFailure() = runTest {
        assertTrue(DefaultHomeRepository(FakeSource(fail = true)).getRows().isFailure)
    }
}
