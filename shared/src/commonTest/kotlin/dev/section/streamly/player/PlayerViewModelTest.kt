package dev.section.streamly.player

import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.livetv.data.ChannelRepository
import dev.section.streamly.livetv.domain.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val channels = listOf(
        Channel("1", "One", "News", null, "http://x/1"),
        Channel("2", "Two", "Sports", null, "http://x/2"),
        Channel("3", "Three", "News", null, "http://x/3"),
    )

    private class FakeRepository(var result: Result<List<Channel>>) : ChannelRepository {
        override suspend fun getChannels() = result
        override suspend fun getChannel(id: String) =
            result.getOrNull()?.firstOrNull { it.id == id }
    }

    private class FakeConnectivity(initial: Boolean = true) : ConnectivityMonitor {
        val online = MutableStateFlow(initial)
        override val isOnline: Flow<Boolean> = online
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        channelId: String = "1",
        repository: ChannelRepository = FakeRepository(Result.success(channels)),
        connectivity: FakeConnectivity = FakeConnectivity(),
    ): PlayerViewModel =
        PlayerViewModel(channelId, repository, connectivity).also { advanceUntilIdle() }

    @Test
    fun loadsRequestedChannel() = runTest {
        val vm = viewModel("2")
        assertEquals("2", vm.state.value.channel?.id)
        assertTrue(vm.state.value.canZap)
    }

    @Test
    fun unknownChannelIsNotFoundError() = runTest {
        val vm = viewModel("nope")
        assertEquals(PlaybackStatus.Error(PlayerErrorKind.NotFound), vm.state.value.status)
    }

    @Test
    fun channelListFailureIsAnError() = runTest {
        val vm = viewModel(repository = FakeRepository(Result.failure(RuntimeException("bad"))))
        assertTrue(vm.state.value.status is PlaybackStatus.Error)
    }

    @Test
    fun zappingWrapsAroundBothWays() = runTest {
        val vm = viewModel("3")
        vm.onIntent(PlayerIntent.NextChannel)
        assertEquals("1", vm.state.value.channel?.id)
        vm.onIntent(PlayerIntent.PreviousChannel)
        assertEquals("3", vm.state.value.channel?.id)
        assertEquals(PlaybackStatus.Loading, vm.state.value.status)
    }

    @Test
    fun togglePlayPauseFlipsPlayWhenReady() = runTest {
        val vm = viewModel()
        vm.onIntent(PlayerIntent.TogglePlayPause)
        assertFalse(vm.state.value.playWhenReady)
        vm.onIntent(PlayerIntent.TogglePlayPause)
        assertTrue(vm.state.value.playWhenReady)
    }

    @Test
    fun retryBumpsTokenAndShowsLoading() = runTest {
        val vm = viewModel()
        vm.onIntent(PlayerIntent.PlayerStatusChanged(PlaybackStatus.Error(PlayerErrorKind.Source)))
        val before = vm.state.value.retryToken
        vm.onIntent(PlayerIntent.Retry)
        assertEquals(before + 1, vm.state.value.retryToken)
        assertEquals(PlaybackStatus.Loading, vm.state.value.status)
    }

    @Test
    fun networkErrorWhileOfflineBecomesOfflineError() = runTest {
        val vm = viewModel(connectivity = FakeConnectivity(initial = false))
        vm.onIntent(PlayerIntent.PlayerStatusChanged(PlaybackStatus.Error(PlayerErrorKind.Network)))
        assertEquals(PlaybackStatus.Error(PlayerErrorKind.Offline), vm.state.value.status)
    }

    @Test
    fun reconnectingAfterAnErrorRetriesAutomatically() = runTest {
        val connectivity = FakeConnectivity(initial = false)
        val vm = viewModel(connectivity = connectivity)
        vm.onIntent(PlayerIntent.PlayerStatusChanged(PlaybackStatus.Error(PlayerErrorKind.Offline)))
        val before = vm.state.value.retryToken

        connectivity.online.value = true
        advanceUntilIdle()

        assertEquals(before + 1, vm.state.value.retryToken)
        assertEquals(PlaybackStatus.Loading, vm.state.value.status)
    }

    @Test
    fun controlsAutoHideOnlyDuringHealthyPlayback() = runTest {
        val vm = viewModel()
        vm.onIntent(PlayerIntent.PlayerStatusChanged(PlaybackStatus.Ready))
        assertTrue(vm.state.value.controlsVisible)
        advanceTimeBy(4_500)
        assertFalse(vm.state.value.controlsVisible)

        // Paused: controls come back and stay.
        vm.onIntent(PlayerIntent.TogglePlayPause)
        assertTrue(vm.state.value.controlsVisible)
        advanceTimeBy(10_000)
        assertTrue(vm.state.value.controlsVisible)
    }

    @Test
    fun errorKeepsControlsVisible() = runTest {
        val vm = viewModel()
        vm.onIntent(PlayerIntent.PlayerStatusChanged(PlaybackStatus.Error(PlayerErrorKind.Source)))
        advanceTimeBy(10_000)
        assertTrue(vm.state.value.controlsVisible)
    }

    @Test
    fun backEmitsNavigateBackEffect() = runTest {
        val vm = viewModel()
        vm.onIntent(PlayerIntent.Back)
        advanceUntilIdle()
        assertEquals(PlayerEffect.NavigateBack, vm.effects.first())
    }

    @Test
    fun everyErrorKindHasAMessage() {
        PlayerErrorKind.entries.forEach { assertNotNull(errorMessage(it)) }
    }
}
