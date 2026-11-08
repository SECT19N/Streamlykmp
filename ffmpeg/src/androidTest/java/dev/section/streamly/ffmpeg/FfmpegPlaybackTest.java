package dev.section.streamly.ffmpeg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.test.platform.app.InstrumentationRegistry;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * End-to-end: plays MPEG-TS clips (video + MP2 audio) through ExoPlayer with <em>only</em> the
 * FFmpeg renderers, so MediaCodec cannot mask a failure, into an ImageReader surface.
 */
public final class FfmpegPlaybackTest {

  @Test
  public void playsH264() throws Exception {
    play("h264.ts");
  }

  @Test
  public void playsHevc() throws Exception {
    play("hevc.ts");
  }

  @Test
  public void playsMpeg2() throws Exception {
    play("mpeg2.ts");
  }

  private static void play(String asset) throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    HandlerThread imageThread = new HandlerThread("images");
    imageThread.start();
    ImageReader reader = ImageReader.newInstance(320, 240, PixelFormat.RGBA_8888, 3);
    AtomicInteger frames = new AtomicInteger();
    AtomicInteger distinctColours = new AtomicInteger();
    reader.setOnImageAvailableListener(
        r -> {
          try (Image image = r.acquireLatestImage()) {
            if (image == null) return;
            int count = frames.incrementAndGet();
            if (count == 20) {
              distinctColours.set(countColours(image));
            }
          }
        },
        new Handler(imageThread.getLooper()));

    CountDownLatch ended = new CountDownLatch(1);
    AtomicReference<PlaybackException> error = new AtomicReference<>();
    ExoPlayer[] holder = new ExoPlayer[1];
    CountDownLatch created = new CountDownLatch(1);
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              RenderersFactory factory =
                  (handler, videoListener, audioListener, textOutput, metadataOutput) ->
                      new Renderer[] {
                        new FfmpegVideoRenderer(0, handler, videoListener, 50),
                        new FfmpegAudioRenderer(handler, audioListener)
                      };
              ExoPlayer player = new ExoPlayer.Builder(context, factory).build();
              holder[0] = player;
              player.addListener(
                  new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                      if (state == Player.STATE_ENDED) ended.countDown();
                    }

                    @Override
                    public void onPlayerError(PlaybackException e) {
                      error.set(e);
                      ended.countDown();
                    }
                  });
              player.setVideoSurface(reader.getSurface());
              player.setMediaItem(MediaItem.fromUri("asset:///video/" + asset));
              player.prepare();
              player.play();
              created.countDown();
            });
    assertTrue(created.await(10, TimeUnit.SECONDS));
    boolean finished = ended.await(30, TimeUnit.SECONDS);
    new Handler(Looper.getMainLooper()).post(() -> holder[0].release());
    reader.close();
    imageThread.quitSafely();

    assertNull(asset + ": " + error.get(), error.get());
    assertTrue(asset + ": playback did not finish", finished);
    // 2 s at 25 fps = 50 frames; some may be dropped/coalesced by acquireLatestImage.
    assertTrue(asset + ": only " + frames.get() + " frames rendered", frames.get() >= 20);
    assertTrue(
        asset + ": picture looks blank (" + distinctColours.get() + " colours)",
        distinctColours.get() > 8);
  }

  private static int countColours(Image image) {
    Image.Plane plane = image.getPlanes()[0];
    ByteBuffer buffer = plane.getBuffer();
    java.util.HashSet<Integer> colours = new java.util.HashSet<>();
    for (int y = 0; y < image.getHeight(); y += 8) {
      for (int x = 0; x < image.getWidth(); x += 8) {
        int offset = y * plane.getRowStride() + x * plane.getPixelStride();
        colours.add(buffer.getInt(offset) & 0xF0F0F0F0);
      }
    }
    return colours.size();
  }
}
