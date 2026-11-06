package dev.section.streamly.core.cache

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * In-memory cache for the lifetime of the process. Entries are fresh for [ttl]; an expired entry
 * is still available through [getStale] so a failed refresh (e.g. offline) can fall back to it.
 */
class SessionCache<K : Any, V : Any>(
    private val ttl: Duration = 5.minutes,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val maxEntries: Int = 50,
) {
    private class Entry<V>(val value: V, val storedAt: ComparableTimeMark)

    // Insertion-ordered, so the oldest entry is the first key when the cache is full.
    private val entries = LinkedHashMap<K, Entry<V>>()

    fun getFresh(key: K): V? =
        entries[key]?.takeIf { it.storedAt.elapsedNow() < ttl }?.value

    fun getStale(key: K): V? = entries[key]?.value

    fun put(key: K, value: V) {
        entries.remove(key)
        entries[key] = Entry(value, timeSource.markNow())
        if (entries.size > maxEntries) entries.remove(entries.keys.first())
    }
}
