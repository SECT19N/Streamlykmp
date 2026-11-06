package dev.section.streamly.favorites.data

import com.russhwolf.settings.Settings

/** Where the serialized favorites list lives. Kept tiny so tests can use an in-memory fake. */
interface FavoritesStore {
    fun read(): String?
    fun write(value: String)
}

/** Device-local storage: SharedPreferences on Android, NSUserDefaults on iOS. */
class SettingsFavoritesStore(private val settings: Settings) : FavoritesStore {
    override fun read(): String? = settings.getStringOrNull(KEY)
    override fun write(value: String) = settings.putString(KEY, value)

    private companion object {
        const val KEY = "favorites_v1"
    }
}
