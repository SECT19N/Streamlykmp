package dev.section.streamly.livetv

import dev.section.streamly.livetv.data.ChannelDto
import dev.section.streamly.livetv.data.ChannelSource
import dev.section.streamly.livetv.data.DefaultChannelRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChannelRepositoryTest {

    private class FakeSource(private val result: () -> List<ChannelDto>) : ChannelSource {
        override suspend fun load(): List<ChannelDto> = result()
    }

    private fun dto(id: String?, name: String? = "Name", url: String? = "http://x/index.m3u8") =
        ChannelDto(id = id, name = name, category = "News", streamUrl = url)

    @Test
    fun parsesChannelsWithoutLogoField() {
        val json =
            """[{"id":"ch_01","name":"360 HD","category":"Entertainment","streamUrl":"http://1.2.3.4/a.m3u8"}]"""
        val dtos = Json {
            ignoreUnknownKeys = true
        }.decodeFromString(ListSerializer(ChannelDto.serializer()), json)
        assertEquals(1, dtos.size)
        assertNull(dtos.single().logo)
    }

    @Test
    fun mapsValidChannelsAndDropsUnplayableOnes() = runTest {
        val repo = DefaultChannelRepository(
            FakeSource {
                listOf(
                    dto("a"),
                    dto(null),
                    dto("b", name = " "),
                    dto("c", url = null),
                    dto("a")
                )
            },
        )
        val channels = repo.getChannels().getOrThrow()
        assertEquals(listOf("a"), channels.map { it.id })
        assertNull(channels.single().logoUrl)
    }

    @Test
    fun missingCategoryFallsBackToOther() = runTest {
        val repo = DefaultChannelRepository(
            FakeSource { listOf(ChannelDto(id = "a", name = "A", streamUrl = "http://x")) },
        )
        assertEquals("Other", repo.getChannels().getOrThrow().single().category)
    }

    @Test
    fun sourceFailureBecomesFailureResult() = runTest {
        val repo = DefaultChannelRepository(FakeSource { error("boom") })
        assertTrue(repo.getChannels().isFailure)
        assertNull(repo.getChannel("a"))
    }

    @Test
    fun initialsUseFirstTwoWords() {
        assertEquals("AH", initials("Al Hadath News"))
        assertEquals("3H", initials("360 HD"))
        assertEquals("K", initials("Kanal"))
        assertEquals("?", initials("  "))
    }
}
