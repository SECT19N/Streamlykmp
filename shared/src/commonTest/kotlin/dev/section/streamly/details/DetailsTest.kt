package dev.section.streamly.details

import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.details.data.DefaultDetailsRepository
import dev.section.streamly.details.data.DetailsRepository
import dev.section.streamly.details.data.DetailsSource
import dev.section.streamly.details.data.TmdbDetailsDto
import dev.section.streamly.details.domain.MediaDetails
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
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

class DetailsDataTest {

    @Test
    fun mapsMovie() {
        val dto = json.decodeFromString<TmdbDetailsDto>(
            """{"id":1,"title":"Heat","tagline":"A los angeles crime saga","overview":"Cops.",
            "release_date":"1995-12-15","runtime":170,"genres":[{"name":"Crime"},{"name":"Drama"}],
            "vote_average":7.9,"vote_count":100,"poster_path":"/p.jpg","backdrop_path":"/b.jpg",
            "credits":{"cast":[{"name":"Al Pacino","character":"Vincent","profile_path":"/a.jpg"}]}}""",
        )
        val d = assertNotNull(dto.toDomain(isMovie = true))
        assertEquals("Heat", d.title)
        assertEquals("1995", d.year)
        assertEquals(170, d.runtimeMinutes)
        assertEquals(listOf("Crime", "Drama"), d.genres)
        assertEquals(7.9, d.rating)
        assertEquals("Al Pacino", d.cast.single().name)
        assertEquals("https://image.tmdb.org/t/p/w780/b.jpg", d.backdropUrl())
    }

    @Test
    fun mapsShowUsingNameAndEpisodeRuntime() {
        val dto = json.decodeFromString<TmdbDetailsDto>(
            """{"id":2,"name":"The Wire","first_air_date":"2002-06-02","episode_run_time":[60]}""",
        )
        val d = assertNotNull(dto.toDomain(isMovie = false))
        assertEquals("The Wire", d.title)
        assertEquals("2002", d.year)
        assertEquals(60, d.runtimeMinutes)
    }

    @Test
    fun sparseRecordStillMaps() {
        val d = assertNotNull(
            json.decodeFromString<TmdbDetailsDto>("""{"id":3,"title":"X","release_date":"","runtime":0,"vote_average":0.0}""")
                .toDomain(true),
        )
        assertNull(d.year)
        assertNull(d.runtimeMinutes)
        assertNull(d.rating)
        assertNull(d.overview)
        assertTrue(d.cast.isEmpty())
    }

    @Test
    fun missingIdOrTitleIsRejected() {
        assertNull(TmdbDetailsDto(title = "X").toDomain(true))
        assertNull(TmdbDetailsDto(id = 1, title = " ").toDomain(true))
        assertNull(TmdbDetailsDto(id = 1, title = "Movie only").toDomain(false))
    }

    @Test
    fun castIsCapped() {
        val cast = (1..30).joinToString(",") { """{"name":"A$it"}""" }
        val d =
            json.decodeFromString<TmdbDetailsDto>("""{"id":1,"title":"X","credits":{"cast":[$cast]}}""")
                .toDomain(true)!!
        assertEquals(12, d.cast.size)
    }

    private class FakeSource(var fail: Boolean = false) : DetailsSource {
        var calls = 0
        override suspend fun movie(id: Int): TmdbDetailsDto {
            calls++
            return if (fail) error("boom") else TmdbDetailsDto(id = id, title = "M")
        }

        override suspend fun show(id: Int): TmdbDetailsDto {
            calls++
            return if (fail) error("boom") else TmdbDetailsDto(id = id, name = "S")
        }
    }

    @Test
    fun cachedDetailsAvoidSecondRequestUnlessRefreshed() = runTest {
        val source = FakeSource()
        val repo = DefaultDetailsRepository(source)
        repo.getDetails(1, true)
        repo.getDetails(1, true)
        assertEquals(1, source.calls)
        repo.getDetails(1, true, refresh = true)
        assertEquals(2, source.calls)
        // Same id as a show is a different title, so it must not hit the movie entry.
        repo.getDetails(1, false)
        assertEquals(3, source.calls)
    }

    @Test
    fun failedRefreshFallsBackToCachedDetails() = runTest {
        val source = FakeSource()
        val repo = DefaultDetailsRepository(source)
        val first = repo.getDetails(1, true).getOrThrow()
        source.fail = true
        assertEquals(first, repo.getDetails(1, true, refresh = true).getOrThrow())
    }

    @Test
    fun repositoryPicksEndpointByType() = runTest {
        val repo = DefaultDetailsRepository(FakeSource())
        assertEquals("M", repo.getDetails(1, true).getOrThrow().title)
        assertEquals("S", repo.getDetails(1, false).getOrThrow().title)
    }

    @Test
    fun repositoryWrapsFailures() = runTest {
        assertTrue(DefaultDetailsRepository(FakeSource(fail = true)).getDetails(1, true).isFailure)
    }

    @Test
    fun repositoryFailsOnIncompleteRecord() = runTest {
        val source = object : DetailsSource {
            override suspend fun movie(id: Int) = TmdbDetailsDto()
            override suspend fun show(id: Int) = TmdbDetailsDto()
        }
        assertTrue(DefaultDetailsRepository(source).getDetails(1, true).isFailure)
    }

    @Test
    fun formatsRuntimeAndRating() {
        assertEquals("45m", formatRuntime(45))
        assertEquals("2h", formatRuntime(120))
        assertEquals("2h 50m", formatRuntime(170))
        assertEquals("7.9", oneDecimal(7.94))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {

    private val details = MediaDetails(
        id = 1, isMovie = true, title = "Heat", tagline = null, overview = null, year = "1995",
        runtimeMinutes = 170, genres = emptyList(), rating = null, posterPath = null,
        backdropPath = null, cast = emptyList(),
    )

    private class FakeRepository(var result: Result<MediaDetails>) : DetailsRepository {
        var lastRequest: Pair<Int, Boolean>? = null
        val refreshFlags = mutableListOf<Boolean>()
        override suspend fun getDetails(
            id: Int,
            isMovie: Boolean,
            refresh: Boolean
        ): Result<MediaDetails> {
            lastRequest = id to isMovie
            refreshFlags += refresh
            return result
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
    fun loadsDetailsForRouteArguments() = runTest {
        val repo = FakeRepository(Result.success(details))
        val vm = DetailsViewModel(1, true, repo, FakeConnectivity())
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        assertEquals(details, vm.state.value.details)
        assertEquals(false, vm.state.value.isLoading)
        assertEquals(1 to true, repo.lastRequest)
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() = runTest {
        val repo = FakeRepository(Result.failure(RuntimeException("x")))
        val vm = DetailsViewModel(1, true, repo, FakeConnectivity())
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        repo.result = Result.success(details)
        vm.onIntent(DetailsIntent.Retry)
        assertTrue(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        advanceUntilIdle()
        assertEquals(details, vm.state.value.details)
        assertEquals(
            listOf(false, true),
            repo.refreshFlags
        ) // initial load may use cache, retry may not
    }

    @Test
    fun reflectsConnectivity() = runTest {
        val connectivity = FakeConnectivity(initial = false)
        val vm = DetailsViewModel(1, true, FakeRepository(Result.success(details)), connectivity)
        advanceUntilIdle()
        assertTrue(vm.state.value.isOffline)
        connectivity.online.value = true
        advanceUntilIdle()
        assertEquals(false, vm.state.value.isOffline)
    }

    @Test
    fun backEmitsNavigateBack() = runTest {
        val vm =
            DetailsViewModel(1, true, FakeRepository(Result.success(details)), FakeConnectivity())
        vm.onIntent(DetailsIntent.BackClicked)
        advanceUntilIdle()
        assertEquals(DetailsEffect.NavigateBack, vm.effects.first())
    }
}
