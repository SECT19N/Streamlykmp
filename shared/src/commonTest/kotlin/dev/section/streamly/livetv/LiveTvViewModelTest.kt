package dev.section.streamly.livetv

import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.livetv.data.ChannelRepository
import dev.section.streamly.livetv.domain.Channel
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
class LiveTvViewModelTest {

    private val channels = listOf(
        Channel("1", "One", "News", null, "http://x/1"),
        Channel("2", "Two", "Sports", null, "http://x/2"),
        Channel("3", "Three", "News", null, "http://x/3"),
    )

    private class FakeRepository(var result: Result<List<Channel>>) : ChannelRepository {
        override suspend fun getChannels() = result
        override suspend fun getChannel(id: String) = result.getOrNull()?.firstOrNull { it.id == id }
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
    fun loadsChannelsAndDerivesCategories() = runTest {
        val vm = LiveTvViewModel(FakeRepository(Result.success(channels)), FakeConnectivity())
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(false, state.isLoading)
        assertEquals(3, state.visibleChannels.size)
        assertEquals(listOf("News", "Sports"), state.categories)
    }

    @Test
    fun selectingCategoryFiltersAndAllResets() = runTest {
        val vm = LiveTvViewModel(FakeRepository(Result.success(channels)), FakeConnectivity())
        advanceUntilIdle()
        vm.onIntent(LiveTvIntent.SelectCategory("News"))
        assertEquals(listOf("1", "3"), vm.state.value.visibleChannels.map { it.id })
        vm.onIntent(LiveTvIntent.SelectCategory(null))
        assertEquals(3, vm.state.value.visibleChannels.size)
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() = runTest {
        val repo = FakeRepository(Result.failure(RuntimeException("bad")))
        val vm = LiveTvViewModel(repo, FakeConnectivity())
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        repo.result = Result.success(channels)
        vm.onIntent(LiveTvIntent.Retry)
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(3, vm.state.value.channels.size)
    }

    @Test
    fun emptyListIsNotAnError() = runTest {
        val vm = LiveTvViewModel(FakeRepository(Result.success(emptyList())), FakeConnectivity())
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.channels.isEmpty())
    }

    @Test
    fun connectivityDrivesOfflineFlag() = runTest {
        val connectivity = FakeConnectivity(initial = false)
        val vm = LiveTvViewModel(FakeRepository(Result.success(channels)), connectivity)
        advanceUntilIdle()
        assertTrue(vm.state.value.isOffline)
        connectivity.online.value = true
        advanceUntilIdle()
        assertEquals(false, vm.state.value.isOffline)
    }

    @Test
    fun clickEmitsOpenPlayerEffect() = runTest {
        val vm = LiveTvViewModel(FakeRepository(Result.success(channels)), FakeConnectivity())
        advanceUntilIdle()
        vm.onIntent(LiveTvIntent.ChannelClicked("2"))
        advanceUntilIdle()
        assertEquals(LiveTvEffect.OpenPlayer("2"), vm.effects.first())
    }
}
