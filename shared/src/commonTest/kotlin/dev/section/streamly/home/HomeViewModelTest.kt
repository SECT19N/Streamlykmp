package dev.section.streamly.home

import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.home.data.HomeRepository
import dev.section.streamly.home.domain.HomeRow
import dev.section.streamly.home.domain.HomeRowType
import dev.section.streamly.home.domain.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val rows = listOf(
        HomeRow(HomeRowType.TrendingMovies, listOf(MediaItem(1, "Heat", "/h.jpg", true))),
    )

    private class FakeRepository(var result: Result<List<HomeRow>>) : HomeRepository {
        var loadMoreResult: Result<HomeRow> = Result.failure(RuntimeException("not set"))
        val refreshFlags = mutableListOf<Boolean>()
        val loadMoreCalls = mutableListOf<HomeRow>()
        override suspend fun getRows(refresh: Boolean): Result<List<HomeRow>> {
            refreshFlags += refresh
            return result
        }

        override suspend fun loadMore(row: HomeRow): Result<HomeRow> {
            loadMoreCalls += row
            return loadMoreResult
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
    fun loadsRows() = runTest {
        val vm = HomeViewModel(FakeRepository(Result.success(rows)), FakeConnectivity())
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        assertEquals(false, vm.state.value.isLoading)
        assertEquals(rows, vm.state.value.rows)
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() = runTest {
        val repo = FakeRepository(Result.failure(RuntimeException("x")))
        val vm = HomeViewModel(repo, FakeConnectivity())
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        repo.result = Result.success(rows)
        vm.onIntent(HomeIntent.Retry)
        assertTrue(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        advanceUntilIdle()
        assertEquals(rows, vm.state.value.rows)
    }

    @Test
    fun retryBypassesCacheButInitialLoadDoesNot() = runTest {
        val repo = FakeRepository(Result.success(rows))
        val vm = HomeViewModel(repo, FakeConnectivity())
        advanceUntilIdle()
        vm.onIntent(HomeIntent.Retry)
        advanceUntilIdle()
        assertEquals(listOf(false, true), repo.refreshFlags)
    }

    private val pagedRow = HomeRow(
        HomeRowType.TrendingMovies,
        listOf(MediaItem(1, "Heat", null, true)),
        page = 1,
        totalPages = 3,
    )

    @Test
    fun loadMoreReplacesRowAndIgnoresDuplicateRequests() = runTest {
        val repo = FakeRepository(Result.success(listOf(pagedRow)))
        val more =
            pagedRow.copy(items = pagedRow.items + MediaItem(2, "Ronin", null, true), page = 2)
        repo.loadMoreResult = Result.success(more)
        val vm = HomeViewModel(repo, FakeConnectivity())
        advanceUntilIdle()

        vm.onIntent(HomeIntent.LoadMore(HomeRowType.TrendingMovies))
        vm.onIntent(HomeIntent.LoadMore(HomeRowType.TrendingMovies)) // already in flight
        assertTrue(HomeRowType.TrendingMovies in vm.state.value.loadingMore)
        advanceUntilIdle()

        assertEquals(1, repo.loadMoreCalls.size)
        assertEquals(more, vm.state.value.rows.single())
        assertTrue(vm.state.value.loadingMore.isEmpty())
    }

    @Test
    fun loadMoreOnLastPageDoesNothing() = runTest {
        val repo = FakeRepository(Result.success(rows)) // single page
        val vm = HomeViewModel(repo, FakeConnectivity())
        advanceUntilIdle()
        vm.onIntent(HomeIntent.LoadMore(HomeRowType.TrendingMovies))
        advanceUntilIdle()
        assertTrue(repo.loadMoreCalls.isEmpty())
    }

    @Test
    fun loadMoreFailureKeepsRowsWithoutError() = runTest {
        val repo = FakeRepository(Result.success(listOf(pagedRow)))
        val vm = HomeViewModel(repo, FakeConnectivity())
        advanceUntilIdle()
        vm.onIntent(HomeIntent.LoadMore(HomeRowType.TrendingMovies))
        advanceUntilIdle()
        assertEquals(listOf(pagedRow), vm.state.value.rows)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.loadingMore.isEmpty())
    }

    @Test
    fun tracksOfflineState() = runTest {
        val connectivity = FakeConnectivity(initial = false)
        val vm = HomeViewModel(FakeRepository(Result.success(rows)), connectivity)
        advanceUntilIdle()
        assertTrue(vm.state.value.isOffline)
        connectivity.online.value = true
        advanceUntilIdle()
        assertEquals(false, vm.state.value.isOffline)
    }

    @Test
    fun itemClickEmitsOpenDetails() = runTest {
        val vm = HomeViewModel(FakeRepository(Result.success(rows)), FakeConnectivity())
        advanceUntilIdle()
        vm.onIntent(HomeIntent.ItemClicked(1, true))
        assertEquals(HomeEffect.OpenDetails(1, true), vm.effects.first())
    }
}
