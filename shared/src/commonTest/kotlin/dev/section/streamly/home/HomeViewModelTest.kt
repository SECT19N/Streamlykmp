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
        override suspend fun getRows() = result
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
