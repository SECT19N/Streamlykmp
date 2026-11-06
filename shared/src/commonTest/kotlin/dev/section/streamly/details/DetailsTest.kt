package dev.section.streamly.details

import dev.section.streamly.core.network.ConnectivityMonitor
import dev.section.streamly.details.data.DefaultDetailsRepository
import dev.section.streamly.details.data.DetailsRepository
import dev.section.streamly.details.data.DetailsSource
import dev.section.streamly.details.data.TmdbCollectionDto
import dev.section.streamly.details.data.TmdbCollectionPartDto
import dev.section.streamly.details.data.TmdbCollectionRefDto
import dev.section.streamly.details.data.TmdbDetailsDto
import dev.section.streamly.details.domain.Fact
import dev.section.streamly.details.domain.MediaDetails
import dev.section.streamly.details.domain.facts
import dev.section.streamly.details.domain.formatMoney
import dev.section.streamly.favorites.data.FavoritesRepository
import dev.section.streamly.home.domain.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import kotlin.test.assertFalse
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

        var collectionFails = false
        var collectionCalls = 0
        override suspend fun collection(id: Int): TmdbCollectionDto {
            collectionCalls++
            if (collectionFails) error("boom")
            return TmdbCollectionDto(
                listOf(
                    TmdbCollectionPartDto(3, "Part 3", "/3.jpg", "2012-01-01"),
                    TmdbCollectionPartDto(1, "Part 1", null, "2001-01-01"),
                    TmdbCollectionPartDto(4, "Unreleased", null, null),
                    TmdbCollectionPartDto(2, "Part 2", null, "2005-01-01"),
                ),
            )
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
            override suspend fun collection(id: Int) = TmdbCollectionDto()
        }
        assertTrue(DefaultDetailsRepository(source).getDetails(1, true).isFailure)
    }

    @Test
    fun movieCertificationPrefersUsThenAnyCountry() {
        fun cert(body: String) =
            json.decodeFromString<TmdbDetailsDto>(body).toDomain(true)!!.certification

        val head = """{"id":1,"title":"X","release_dates":{"results":["""
        assertEquals(
            "PG-13",
            cert(
                head + """{"iso_3166_1":"DE","release_dates":[{"certification":"12"}]},
                {"iso_3166_1":"US","release_dates":[{"certification":""},{"certification":"PG-13"}]}]}}"""
            ),
        )
        assertEquals(
            "12",
            cert(head + """{"iso_3166_1":"DE","release_dates":[{"certification":"12"}]}]}}"""),
        )
        assertNull(cert(head + """{"iso_3166_1":"US","release_dates":[{"certification":""}]}]}}"""))
        assertNull(cert("""{"id":1,"title":"X"}"""))
    }

    @Test
    fun showCertificationComesFromContentRatings() {
        val d = json.decodeFromString<TmdbDetailsDto>(
            """{"id":2,"name":"S","content_ratings":{"results":[
            {"iso_3166_1":"GB","rating":"15"},{"iso_3166_1":"US","rating":"TV-MA"}]}}""",
        ).toDomain(false)!!
        assertEquals("TV-MA", d.certification)
    }

    @Test
    fun mapsExtraFacts() {
        val movie = json.decodeFromString<TmdbDetailsDto>(
            """{"id":1,"title":"X","status":"Released","original_language":"fr",
            "spoken_languages":[{"iso_639_1":"fr","english_name":"French"}],
            "budget":63000000,"revenue":0}""",
        ).toDomain(true)!!
        assertEquals(
            listOf(
                Fact("Status", "Released"),
                Fact("Original language", "French"),
                Fact("Budget", "$63M"),
            ),
            movie.facts(),
        )
        val show = json.decodeFromString<TmdbDetailsDto>(
            """{"id":2,"name":"S","original_language":"ko","number_of_seasons":3,"number_of_episodes":40}""",
        ).toDomain(false)!!
        assertEquals(
            listOf(Fact("Original language", "KO"), Fact("Seasons", "3"), Fact("Episodes", "40")),
            show.facts(),
        )
    }

    @Test
    fun formatsMoney() {
        assertEquals("$63M", formatMoney(63_000_000))
        assertEquals("$1.2B", formatMoney(1_250_000_000))
        assertEquals("$850K", formatMoney(850_000))
        assertEquals("$900", formatMoney(900))
    }

    @Test
    fun collectionListsOtherFilmsInReleaseOrder() = runTest {
        val source = FakeSource()
        val repo = DefaultDetailsRepository(object : DetailsSource by source {
            override suspend fun movie(id: Int) = TmdbDetailsDto(
                id = 2, title = "Part 2",
                belongsToCollection = TmdbCollectionRefDto(9, "The Saga"),
            )
        })
        val c = repo.getDetails(2, true).getOrThrow().collection!!
        assertEquals("The Saga", c.name)
        // The film being viewed is left out; the undated one goes last.
        assertEquals(listOf("Part 1", "Part 3", "Unreleased"), c.parts.map { it.title })
        assertTrue(c.parts.all { it.isMovie })
    }

    @Test
    fun failedCollectionDoesNotFailTheDetails() = runTest {
        val source = FakeSource().apply { collectionFails = true }
        val repo = DefaultDetailsRepository(object : DetailsSource by source {
            override suspend fun movie(id: Int) = TmdbDetailsDto(
                id = id, title = "M", belongsToCollection = TmdbCollectionRefDto(9, "The Saga"),
            )
        })
        val d = repo.getDetails(1, true).getOrThrow()
        assertEquals("M", d.title)
        assertNull(d.collection)
    }

    @Test
    fun noCollectionMeansNoCollectionRequest() = runTest {
        val source = FakeSource()
        DefaultDetailsRepository(source).getDetails(1, true)
        assertEquals(0, source.collectionCalls)
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

    private class FakeFavorites : FavoritesRepository {
        val flow = MutableStateFlow<List<MediaItem>>(emptyList())
        override val favorites = flow
        override fun isFavorite(id: Int, isMovie: Boolean) =
            flow.map { list -> list.any { it.id == id && it.isMovie == isMovie } }

        override fun toggle(item: MediaItem) {
            flow.value = if (item in flow.value) flow.value - item else flow.value + item
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
        val vm = DetailsViewModel(1, true, repo, FakeFavorites(), FakeConnectivity())
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        assertEquals(details, vm.state.value.details)
        assertEquals(false, vm.state.value.isLoading)
        assertEquals(1 to true, repo.lastRequest)
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() = runTest {
        val repo = FakeRepository(Result.failure(RuntimeException("x")))
        val vm = DetailsViewModel(1, true, repo, FakeFavorites(), FakeConnectivity())
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
        val vm = DetailsViewModel(
            1,
            true,
            FakeRepository(Result.success(details)),
            FakeFavorites(),
            connectivity
        )
        advanceUntilIdle()
        assertTrue(vm.state.value.isOffline)
        connectivity.online.value = true
        advanceUntilIdle()
        assertEquals(false, vm.state.value.isOffline)
    }

    @Test
    fun favoriteStateFollowsRepositoryAndToggles() = runTest {
        val favorites = FakeFavorites()
        val vm = DetailsViewModel(
            1,
            true,
            FakeRepository(Result.success(details)),
            favorites,
            FakeConnectivity()
        )
        advanceUntilIdle()
        assertFalse(vm.state.value.isFavorite)

        vm.onIntent(DetailsIntent.ToggleFavorite)
        advanceUntilIdle()
        assertTrue(vm.state.value.isFavorite)
        // What gets stored is enough to render the Favorites list offline.
        assertEquals(MediaItem(1, "Heat", null, true), favorites.flow.value.single())

        vm.onIntent(DetailsIntent.ToggleFavorite)
        advanceUntilIdle()
        assertFalse(vm.state.value.isFavorite)
    }

    @Test
    fun toggleBeforeDetailsLoadedDoesNothing() = runTest {
        val favorites = FakeFavorites()
        val vm = DetailsViewModel(
            1,
            true,
            FakeRepository(Result.failure(RuntimeException("x"))),
            favorites,
            FakeConnectivity(),
        )
        advanceUntilIdle()
        vm.onIntent(DetailsIntent.ToggleFavorite)
        advanceUntilIdle()
        assertTrue(favorites.flow.value.isEmpty())
    }

    @Test
    fun backEmitsNavigateBack() = runTest {
        val vm =
            DetailsViewModel(
                1,
                true,
                FakeRepository(Result.success(details)),
                FakeFavorites(),
                FakeConnectivity()
            )
        vm.onIntent(DetailsIntent.BackClicked)
        advanceUntilIdle()
        assertEquals(DetailsEffect.NavigateBack, vm.effects.first())
    }

    @Test
    fun openingACollectionPartEmitsDetailsEffect() = runTest {
        val vm = DetailsViewModel(
            1,
            true,
            FakeRepository(Result.success(details)),
            FakeFavorites(),
            FakeConnectivity()
        )
        advanceUntilIdle()
        vm.onIntent(DetailsIntent.OpenItem(7, true))
        advanceUntilIdle()
        assertEquals(DetailsEffect.OpenDetails(7, true), vm.effects.first())
    }
}
