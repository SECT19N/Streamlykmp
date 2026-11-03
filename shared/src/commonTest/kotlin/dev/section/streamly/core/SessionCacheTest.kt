package dev.section.streamly.core

import dev.section.streamly.core.cache.SessionCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class SessionCacheTest {

    @Test
    fun freshWithinTtlThenStaleOnly() {
        val time = TestTimeSource()
        val cache = SessionCache<String, Int>(ttl = 5.minutes, timeSource = time)
        cache.put("a", 1)
        time += 4.minutes
        assertEquals(1, cache.getFresh("a"))
        time += 2.minutes
        assertNull(cache.getFresh("a"))
        assertEquals(1, cache.getStale("a"))
    }

    @Test
    fun putRefreshesTimestamp() {
        val time = TestTimeSource()
        val cache = SessionCache<String, Int>(ttl = 5.minutes, timeSource = time)
        cache.put("a", 1)
        time += 6.minutes
        cache.put("a", 2)
        assertEquals(2, cache.getFresh("a"))
    }

    @Test
    fun evictsOldestWhenFull() {
        val cache = SessionCache<Int, Int>(maxEntries = 2)
        cache.put(1, 1); cache.put(2, 2); cache.put(3, 3)
        assertNull(cache.getStale(1))
        assertEquals(3, cache.getStale(3))
    }
}
