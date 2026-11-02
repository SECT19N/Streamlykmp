package dev.section.streamly.livetv.data

import dev.section.streamly.livetv.domain.Channel
import kotlinx.coroutines.CancellationException

interface ChannelRepository {
    suspend fun getChannels(): Result<List<Channel>>
    suspend fun getChannel(id: String): Channel?
}

class DefaultChannelRepository(private val source: ChannelSource) : ChannelRepository {

    override suspend fun getChannels(): Result<List<Channel>> = try {
        Result.success(source.load().mapNotNull { it.toDomain() }.distinctBy { it.id })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getChannel(id: String): Channel? =
        getChannels().getOrNull()?.firstOrNull { it.id == id }
}
