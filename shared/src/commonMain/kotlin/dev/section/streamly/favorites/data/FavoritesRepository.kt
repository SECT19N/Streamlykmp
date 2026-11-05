package dev.section.streamly.favorites.data

import dev.section.streamly.home.domain.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface FavoritesRepository {
    /** Newest first. Titles and poster paths are stored, so the list renders offline. */
    val favorites: StateFlow<List<MediaItem>>

    fun isFavorite(id: Int, isMovie: Boolean): Flow<Boolean>

    /** Adds [item] if it isn't a favorite yet, otherwise removes it. */
    fun toggle(item: MediaItem)
}

class DefaultFavoritesRepository(
    private val store: FavoritesStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FavoritesRepository {

    private val state = MutableStateFlow(load())
    override val favorites: StateFlow<List<MediaItem>> = state.asStateFlow()

    override fun isFavorite(id: Int, isMovie: Boolean): Flow<Boolean> =
        state.map { list -> list.any { it.id == id && it.isMovie == isMovie } }
            .distinctUntilChanged()

    override fun toggle(item: MediaItem) {
        state.update { list ->
            val exists = list.any { it.id == item.id && it.isMovie == item.isMovie }
            val updated = if (exists) {
                list.filterNot { it.id == item.id && it.isMovie == item.isMovie }
            } else {
                listOf(item) + list
            }
            save(updated)
            updated
        }
    }

    /** A missing, corrupt or unreadable value is treated as "no favorites" rather than a crash. */
    private fun load(): List<MediaItem> = try {
        store.read()
            ?.let { json.decodeFromString<List<StoredFavorite>>(it) }
            ?.map { it.toDomain() }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(list: List<MediaItem>) {
        try {
            store.write(json.encodeToString(list.map(StoredFavorite::from)))
        } catch (_: Exception) {
            // Storage failed; the in-memory list still works for this session.
        }
    }
}

@Serializable
private data class StoredFavorite(
    val id: Int,
    val isMovie: Boolean,
    val title: String,
    val posterPath: String? = null,
) {
    fun toDomain() = MediaItem(id, title, posterPath, isMovie)

    companion object {
        fun from(item: MediaItem) = StoredFavorite(item.id, item.isMovie, item.title, item.posterPath)
    }
}
