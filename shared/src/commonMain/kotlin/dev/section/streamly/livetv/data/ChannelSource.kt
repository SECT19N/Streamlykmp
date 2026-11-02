package dev.section.streamly.livetv.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import streamlykmp.shared.generated.resources.Res

/** Where the raw channel list comes from. Swap the implementation to load it from a URL. */
interface ChannelSource {
    suspend fun load(): List<ChannelDto>
}

/** Reads the `channels.json` bundled with the app. */
class BundledChannelSource(private val json: Json) : ChannelSource {
    override suspend fun load(): List<ChannelDto> {
        val text = Res.readBytes("files/channels.json").decodeToString()
        return json.decodeFromString(ListSerializer(ChannelDto.serializer()), text)
    }
}
