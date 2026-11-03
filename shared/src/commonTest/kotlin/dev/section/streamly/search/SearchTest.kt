package dev.section.streamly.search

import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.home.data.TmdbMediaDto
import dev.section.streamly.home.data.TmdbPageDto
import dev.section.streamly.home.domain.MediaItem
import dev.section.streamly.search.data.DefaultSearchRepository
import dev.section.streamly.search.data.SearchRepository
import dev.section.streamly.search.data.SearchSource
import dev.section.streamly.search.domain.SearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchRepositoryTest {

    private class FakeSource(
        val movies: List<TmdbMediaDto> = emptyList(),
        val shows: List<TmdbMediaDto> = emptyList(),
        var fail: Boolean = false,
        val totalPages: Int = 1,
    ) : SearchSource {
        var calls = 0
        override suspend fun searchMovies(query: String, page: Int): TmdbPageDto {
            calls++
            return if (fail) error("boom") else TmdbPageDto(movies, page, totalPages)
        }

        override suspend fun searchShows(query: String, page: Int): TmdbPageDto {
            calls++
            return if (fail) error("boom") else TmdbPageDto(shows, page, totalPages)
        }
    }

    @Test
    fun reportsPagingFromTheLongerResultSet() = runTest {
        val source = FakeSource(movies = listOf(TmdbMediaDto(id = 1, title = "A")), totalPages = 4)
        val page = DefaultSearchRepository(source).search("x", page = 2).getOrThrow()
        assertEquals(2, page.page)
        assertEquals(4, page.totalPages)
        assertTrue(page.canLoadMore)
    }

    @Test
    fun cacheIsPerQueryAndPageAndRefreshBypassesIt() = runTest {
        val source = FakeSource(movies = listOf(TmdbMediaDto(id = 1, title = "A")))
        val repo = DefaultSearchRepository(source)
        repo.search("Heat")
        repo.search("heat") // case-insensitive: same entry
        assertEquals(2, source.calls) // one movie + one tv request
        repo.search("heat", page = 2)
        assertEquals(4, source.calls)
        repo.search("heat", refresh = true)
        assertEquals(6, source.calls)
    }

    @Test
    fun failedRefreshFallsBackToCachedPage() = runTest {
        val source = FakeSource(movies = listOf(TmdbMediaDto(id = 1, title = "A")))
        val repo = DefaultSearchRepository(source)
        val first = repo.search("x").getOrThrow()
        source.fail = true
        assertEquals(first, repo.search("x", refresh = true).getOrThrow())
    }

    @Test
    fun interleavesMoviesAndShowsAndDropsBadEntries() = runTest {
        val source = FakeSource(
            movies = listOf(
                TmdbMediaDto(id = 1, title = "M1"),
                TmdbMediaDto(id = 2, title = "M2"),
                TmdbMediaDto(id = 3, title = null),
            ),
            shows = listOf(TmdbMediaDto(id = 1, name = "S1")),
        )
        val items = DefaultSearchRepository(source).search("x").getOrThrow().items
        assertEquals(listOf("M1", "S1", "M2"), items.map { it.title })
        // Same TMDB id can exist as a movie and a show; both are kept.
        assertEquals(listOf(true, false, true), items.map { it.isMovie })
    }

    @Test
    fun dedupesRepeatedEntries() = runTest {
        val source = FakeSource(
            movies = listOf(
                TmdbMediaDto(id = 1, title = "A"),
                TmdbMediaDto(id = 1, title = "A")
            )
        )
        assertEquals(1, DefaultSearchRepository(source).search("x").getOrThrow().items.size)
    }

    @Test
    fun failureOfEitherRequestFails() = runTest {
        assertTrue(DefaultSearchRepository(FakeSource(fail = true)).search("x").isFailure)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val heat = MediaItem(1, "Heat", null, true)

    private class FakeRepository(
        var result: Result<List<MediaItem>>,
        val latency: Long = 0,
        val totalPages: Int = 1,
    ) : SearchRepository {
        val queries = mutableListOf<String>()
        val pages = mutableListOf<Int>()
        val refreshFlags = mutableListOf<Boolean>()
        override suspend fun search(query: String, page: Int, refresh: Boolean): Result<SearchPage> {
            queries += query
            pages += page
            refreshFlags += refresh
            if (latency > 0) delay(latency)
            return result.map { SearchPage(it, page, totalPages) }
        }
    }

    private class FakeConnectivity(initial: Boolean = true) : ConnectivityMonitor {
        val online = MutableStateFlow(initial)
        override val isOnline: Flow<Boolean> = online
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsIdleWithoutRequest() = runTest {
        val repo = FakeRepository(Result.success(listOf(heat)))
        val vm = SearchViewModel(repo, FakeConnectivity())
        advanceUntilIdle()
        assertTrue(repo.queries.isEmpty())
        assertEquals(false, vm.state.value.isLoading)
        assertEquals(false, vm.state.value.hasSearched)
    }

    @Test
    fun debouncesRapidTypingIntoOneRequest() = runTest {
        val repo = FakeRepository(Result.success(listOf(heat)))
        val vm = SearchViewModel(repo, FakeConnectivity())
        runCurrent()
        "heat".forEachIndexed { i, _ ->
            vm.onIntent(SearchIntent.QueryChanged("heat".take(i + 1)))
            advanceTimeBy(100)
        }
        assertTrue(repo.queries.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("heat"), repo.queries)
        assertEquals(listOf(heat), vm.state.value.results)
        assertTrue(vm.state.value.hasSearched)
    }

    @Test
    fun queryIsTrimmedBeforeSearching() = runTest {
        val repo = FakeRepository(Result.success(listOf(heat)))
        val vm = SearchViewModel(repo, FakeConnectivity())
        runCurrent()
        vm.onIntent(SearchIntent.QueryChanged("  heat "))
        advanceUntilIdle()
        assertEquals(listOf("heat"), repo.queries)
    }

    @Test
    fun blankQueryResetsWithoutRequest() = runTest {
        val repo = FakeRepository(Result.success(listOf(heat)))
        val vm = SearchViewModel(repo, FakeConnectivity())
        runCurrent()
        vm.onIntent(SearchIntent.QueryChanged("heat"))
        advanceUntilIdle()
        vm.onIntent(SearchIntent.QueryChanged("   "))
        advanceUntilIdle()
        assertEquals(1, repo.queries.size)
        assertTrue(vm.state.value.results.isEmpty())
        assertEquals(false, vm.state.value.hasSearched)
    }

    @Test
    fun newQueryCancelsStaleRequest() = runTest {
        val repo = FakeRepository(Result.success(listOf(heat)), latency = 1_000)
        val vm = SearchViewModel(repo, FakeConnectivity())
        runCurrent()
        vm.onIntent(SearchIntent.QueryChanged("a"))
        advanceTimeBy(SearchDebounceMillis + 1) // "a" is now in flight
        vm.onIntent(SearchIntent.QueryChanged("ab"))
        advanceUntilIdle()
        assertEquals(listOf("a", "ab"), repo.queries)
        assertEquals(listOf(heat), vm.state.value.results)
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun emptyResultsAreReportedAsSearched() = runTest {
        val vm = SearchViewModel(FakeRepository(Result.success(emptyList())), FakeConnectivity())
        runCurrent()
        vm.onIntent(SearchIntent.QueryChanged("zzz"))
        advanceUntilIdle()
        assertTrue(vm.state.value.hasSearched)
        assertTrue(vm.state.value.results.isEmpty())
        assertNull(vm.state.value.error)
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() = runTest {
        val repo = FakeRepository(Result.failure(RuntimeException("x")))
        val vm = SearchViewModel(repo, FakeConnectivity())
        runCurrent()
        vm.onIntent(SearchIntent.QueryChanged("heat"))
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        repo.result = Result.success(listOf(heat))
        vm.onIntent(SearchIntent.Retry)
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(listOf(heat), vm.state.value.results)
        assertEquals(listOf("heat", "heat"), repo.queries)
    }

    @Test
    fun retryBypassesCache() = runTest {
        val repo = FakeRepository(Result.success(listOf(heat)))
        val vm = SearchViewModel(repo, FakeConnectivity())
        runCurrent()
        vm.onIntent(SearchIntent.QueryChanged("heat"))
        advanceUntilIdle()
        vm.onIntent(SearchIntent.Retry)
        advanceUntilIdle()
        assertEquals(listOf(false, true), repo.refreshFlags)
    }

    @Test
    fun loadMoreAppendsNextPageUntilTheLast() = runTest {
        val repo = FakeRepository(Result.success(listOf(heat)), totalPages = 2)
        val vm = SearchViewModel(repo, FakeConnectivity())
        runCurrent()
        vm.onIntent(SearchIntent.QueryChanged("heat"))
        advanceUntilIdle()
        assertTrue(vm.state.value.canLoadMore)

        val other = MediaItem(2, "Ronin", null, true)
        repo.result = Result.success(listOf(heat, other)) // page 2 repeats one item
        vm.onIntent(SearchIntent.LoadMore)
        vm.onIntent(SearchIntent.LoadMore) // already in flight
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repo.pages)
        assertEquals(listOf(heat, other), vm.state.value.results)
        assertEquals(false, vm.state.value.canLoadMore)
        assertEquals(false, vm.state.value.isLoadingMore)

        vm.onIntent(SearchIntent.LoadMore) // nothing left
        advanceUntilIdle()
        assertEquals(listOf(1, 2), repo.pages)
    }

    @Test
    fun failedLoadMoreKeepsResultsWithoutError() = runTest {
        val repo = FakeRepository(Result.success(listOf(heat)), totalPages = 2)
        val vm = SearchViewModel(repo, FakeConnectivity())
        runCurrent()
        vm.onIntent(SearchIntent.QueryChanged("heat"))
        advanceUntilIdle()
        repo.result = Result.failure(RuntimeException("x"))
        vm.onIntent(SearchIntent.LoadMore)
        advanceUntilIdle()
        assertEquals(listOf(heat), vm.state.value.results)
        assertNull(vm.state.value.error)
        assertEquals(false, vm.state.value.isLoadingMore)
        assertTrue(vm.state.value.canLoadMore) // scrolling again retries
    }

    @Test
    fun newQueryDropsAPageStillLoadingForTheOldOne() = runTest {
        val repo = FakeRepository(Result.success(listOf(heat)), latency = 1_000, totalPages = 3)
        val vm = SearchViewModel(repo, FakeConnectivity())
        runCurrent()
        vm.onIntent(SearchIntent.QueryChanged("heat"))
        advanceUntilIdle()
        vm.onIntent(SearchIntent.LoadMore)
        runCurrent() // page 2 for "heat" is in flight
        repo.result = Result.success(listOf(MediaItem(9, "Other", null, false)))
        vm.onIntent(SearchIntent.QueryChanged("other"))
        advanceUntilIdle()
        assertEquals(listOf(9), vm.state.value.results.map { it.id })
        assertEquals(1, vm.state.value.page)
    }

    @Test
    fun reflectsConnectivity() = runTest {
        val connectivity = FakeConnectivity(initial = false)
        val vm = SearchViewModel(FakeRepository(Result.success(emptyList())), connectivity)
        advanceUntilIdle()
        assertTrue(vm.state.value.isOffline)
    }

    @Test
    fun itemClickOpensDetails() = runTest {
        val vm = SearchViewModel(FakeRepository(Result.success(emptyList())), FakeConnectivity())
        vm.onIntent(SearchIntent.ItemClicked(7, false))
        advanceUntilIdle()
        assertEquals(SearchEffect.OpenDetails(7, false), vm.effects.first())
    }
}
