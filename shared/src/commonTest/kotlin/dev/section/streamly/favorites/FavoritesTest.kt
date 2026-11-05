package dev.section.streamly.favorites

import dev.section.streamly.favorites.data.DefaultFavoritesRepository
import dev.section.streamly.favorites.data.FavoritesRepository
import dev.section.streamly.favorites.data.FavoritesStore
import dev.section.streamly.home.domain.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryStore(var value: String? = null, var failWrites: Boolean = false) : FavoritesStore {
    override fun read(): String? = value
    override fun write(value: String) {
        if (failWrites) error("disk full")
        this.value = value
    }
}

class FavoritesRepositoryTest {

    private val heat = MediaItem(1, "Heat", "/h.jpg", isMovie = true)
    private val wire = MediaItem(2, "The Wire", null, isMovie = false)

    @Test
    fun toggleAddsThenRemoves() {
        val repo = DefaultFavoritesRepository(InMemoryStore())
        repo.toggle(heat)
        assertEquals(listOf(heat), repo.favorites.value)
        repo.toggle(heat)
        assertTrue(repo.favorites.value.isEmpty())
    }

    @Test
    fun newestFirst() {
        val repo = DefaultFavoritesRepository(InMemoryStore())
        repo.toggle(heat)
        repo.toggle(wire)
        assertEquals(listOf(wire, heat), repo.favorites.value)
    }

    @Test
    fun sameIdAsMovieAndShowAreDifferentFavorites() = runTest {
        val repo = DefaultFavoritesRepository(InMemoryStore())
        repo.toggle(MediaItem(7, "Movie seven", null, true))
        assertTrue(repo.isFavorite(7, isMovie = true).first())
        assertFalse(repo.isFavorite(7, isMovie = false).first())
        repo.toggle(MediaItem(7, "Show seven", null, false))
        assertEquals(2, repo.favorites.value.size)
    }

    @Test
    fun survivesRestartIncludingOfflineFields() {
        val store = InMemoryStore()
        DefaultFavoritesRepository(store).apply { toggle(heat); toggle(wire) }
        // A new instance on the same store is what the next app launch sees.
        val reloaded = DefaultFavoritesRepository(store)
        assertEquals(listOf(wire, heat), reloaded.favorites.value)
        assertEquals("/h.jpg", reloaded.favorites.value.last().posterPath)
    }

    @Test
    fun corruptStoredValueMeansNoFavoritesNotACrash() {
        val repo = DefaultFavoritesRepository(InMemoryStore("{not json"))
        assertTrue(repo.favorites.value.isEmpty())
        repo.toggle(heat) // and it recovers by overwriting
        assertEquals(listOf(heat), repo.favorites.value)
    }

    @Test
    fun failedWriteKeepsTheSessionWorking() {
        val repo = DefaultFavoritesRepository(InMemoryStore(failWrites = true))
        repo.toggle(heat)
        assertEquals(listOf(heat), repo.favorites.value)
    }

    @Test
    fun unknownStoredFieldsAreIgnored() {
        val store = InMemoryStore("""[{"id":1,"isMovie":true,"title":"Heat","extra":1}]""")
        assertEquals("Heat", DefaultFavoritesRepository(store).favorites.value.single().title)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private class FakeRepository(initial: List<MediaItem> = emptyList()) : FavoritesRepository {
        val flow = MutableStateFlow(initial)
        override val favorites = flow
        override fun isFavorite(id: Int, isMovie: Boolean) = MutableStateFlow(false)
        override fun toggle(item: MediaItem) = Unit
    }

    private val heat = MediaItem(1, "Heat", null, true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsWithSavedFavoritesImmediately() = runTest {
        val vm = FavoritesViewModel(FakeRepository(listOf(heat)))
        assertEquals(listOf(heat), vm.state.value.favorites)
    }

    @Test
    fun emptyUntilSomethingIsSaved() = runTest {
        val repo = FakeRepository()
        val vm = FavoritesViewModel(repo)
        advanceUntilIdle()
        assertTrue(vm.state.value.favorites.isEmpty())
        repo.flow.value = listOf(heat)
        advanceUntilIdle()
        assertEquals(listOf(heat), vm.state.value.favorites)
    }

    @Test
    fun clickOpensDetails() = runTest {
        val vm = FavoritesViewModel(FakeRepository(listOf(heat)))
        vm.onIntent(FavoritesIntent.ItemClicked(1, true))
        advanceUntilIdle()
        assertEquals(FavoritesEffect.OpenDetails(1, true), vm.effects.first())
    }

    @Test
    fun tabsSplitMoviesFromShows() = runTest {
        val show = heat.copy(id = 2, isMovie = false)
        val vm = FavoritesViewModel(FakeRepository(listOf(heat, show)))
        assertEquals(FavoritesTab.Movies, vm.state.value.tab)
        assertEquals(listOf(heat), vm.state.value.visible)
        vm.onIntent(FavoritesIntent.SelectTab(FavoritesTab.Shows))
        advanceUntilIdle()
        assertEquals(listOf(show), vm.state.value.visible)
    }
}
