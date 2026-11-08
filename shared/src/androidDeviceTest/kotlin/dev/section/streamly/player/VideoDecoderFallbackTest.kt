package dev.section.streamly.player

import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import dev.section.streamly.player.audio.StreamlyRenderersFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

/** Plays a 1080p H.264 clip (Main@4.0, like the live channels) with the app's renderers. */
@OptIn(UnstableApi::class)
class VideoDecoderFallbackTest {

    /**
     * With MediaCodec hidden (what LivePlayerSurface does after a decoder failure) FFmpeg must play
     * the clip. The surface is an RGBA ImageReader, which MediaCodec's YUV output cannot feed, so
     * the test can only pass if FFmpeg did the decoding.
     */
    @Test
    fun ffmpegPlaysWhenMediaCodecIsHidden() = play(softwareOnly = true, PixelFormat.RGBA_8888)

    /** The default path must keep working (MediaCodec where the device supports it). */
    @Test
    fun defaultPathPlays() = play(softwareOnly = false, ImageFormat.YUV_420_888)

    private fun play(softwareOnly: Boolean, readerFormat: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hidden = AtomicBoolean(softwareOnly)
        val ended = CountDownLatch(1)
        val failure = arrayOfNulls<PlaybackException>(1)
        val frames = AtomicInteger()
        val main = Handler(Looper.getMainLooper())
        val reader = ImageReader.newInstance(1920, 1080, readerFormat, 3)
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, main)
        var player: ExoPlayer? = null
        val ready = CountDownLatch(1)
        main.post {
            player = ExoPlayer.Builder(context, StreamlyRenderersFactory(context) { hidden.get() }).build().also { p ->
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) ended.countDown()
                    }

                    override fun onRenderedFirstFrame() {
                        frames.incrementAndGet()
                    }

                    override fun onPlayerError(e: PlaybackException) {
                        failure[0] = e
                        ended.countDown()
                    }
                })
                p.setVideoSurface(reader.surface)
                p.setMediaItem(MediaItem.fromUri("asset:///h264_1080p.ts"))
                p.prepare()
                p.play()
            }
            ready.countDown()
        }
        ready.await(10, TimeUnit.SECONDS)
        val finished = ended.await(40, TimeUnit.SECONDS)
        main.post { player?.release(); reader.close() }
        assertTrue(failure[0] == null, "playback error: ${failure[0]}")
        assertTrue(finished, "playback did not finish")
        assertTrue(frames.get() > 0, "no frame was rendered")
    }
}
