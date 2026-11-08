package dev.section.streamly.ffmpeg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;

/**
 * Decodes real MPEG-1 Layer II channel captures through FFmpeg and compares them with reference
 * PCM that was produced by the ffmpeg command line.
 */
public final class FfmpegAudioDecoderTest {

    private static final int[] BITRATES_KBPS = {
            0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384
    };
    private static final int[] SAMPLE_RATES = {44100, 48000, 32000};

    @Test
    public void libraryIsAvailableAndHasAllDecoders() {
        assertTrue("Native library failed to load", FfmpegLibrary.isAvailable());
        for (String mime :
                new String[]{
                        MimeTypes.AUDIO_MPEG_L2,
                        MimeTypes.AUDIO_MPEG,
                        MimeTypes.AUDIO_AAC,
                        MimeTypes.AUDIO_AC3,
                        MimeTypes.VIDEO_MPEG2,
                        MimeTypes.VIDEO_H264,
                        MimeTypes.VIDEO_H265
                }) {
            assertTrue("No decoder for " + mime, FfmpegLibrary.supportsFormat(mime));
        }
    }

    @Test
    public void decodesMp2ToReferencePcm() throws Exception {
        for (int kbps : new int[]{64, 96, 128, 192, 320}) {
            byte[] stream = readAsset("mp2/sample_" + kbps + "k.mp2");
            byte[] reference = readAsset("mp2/reference_" + kbps + "k.pcm");
            byte[] decoded = decode(stream);

            ByteBuffer ref = ByteBuffer.wrap(reference).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer out = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN);
            // FFmpeg may emit slightly different amounts of leading/trailing samples than the CLI.
            int samples = Math.min(ref.remaining(), out.remaining()) / 2;
            assertTrue(kbps + "k: decoded far less than the reference", samples > reference.length / 2 * 0.9);
            int maxError = 0;
            for (int i = 0; i < samples; i++) {
                maxError = Math.max(maxError, Math.abs(ref.getShort(i * 2) - out.getShort(i * 2)));
            }
            assertTrue(kbps + "k: max error " + maxError + " LSB", maxError <= 16);
        }
    }

    private static byte[] decode(byte[] stream) throws Exception {
        Format format =
                new Format.Builder()
                        .setSampleMimeType(MimeTypes.AUDIO_MPEG_L2)
                        .setChannelCount(2)
                        .setSampleRate(48000)
                        .build();
        FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(format, 4, 4, 4096, /* outputFloat= */ false);
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        try {
            int pos = 0;
            long timeUs = 0;
            while (pos + 4 <= stream.length) {
                int frameLength = frameLength(stream, pos);
                assertTrue("Lost sync at " + pos, frameLength > 0 && pos + frameLength <= stream.length);
                // The decoder runs on its own thread: input buffers come back once it has consumed them.
                DecoderInputBuffer in;
                while ((in = decoder.dequeueInputBuffer()) == null) {
                    drain(decoder, pcm);
                    Thread.sleep(1);
                }
                in.timeUs = timeUs++;
                in.ensureSpaceForWrite(frameLength);
                in.data.put(stream, pos, frameLength);
                in.data.flip();
                decoder.queueInputBuffer(in);
                pos += frameLength;
                drain(decoder, pcm);
            }
            // Let the decoder thread finish what is queued before collecting the tail.
            for (int i = 0; i < 200; i++) {
                drain(decoder, pcm);
                Thread.sleep(5);
            }
        } finally {
            decoder.release();
        }
        return pcm.toByteArray();
    }

    private static void drain(FfmpegAudioDecoder decoder, ByteArrayOutputStream sink) throws Exception {
        SimpleDecoderOutputBuffer out;
        while ((out = decoder.dequeueOutputBuffer()) != null) {
            if (!out.shouldBeSkipped && out.data != null) {
                byte[] chunk = new byte[out.data.remaining()];
                out.data.get(chunk);
                sink.write(chunk);
            }
            out.release();
        }
    }

    /**
     * Length in bytes of the MPEG-1 Layer II frame starting at {@code pos}, or -1.
     */
    private static int frameLength(byte[] b, int pos) {
        int h = ((b[pos] & 0xFF) << 24) | ((b[pos + 1] & 0xFF) << 16) | ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
        boolean sync = (h & 0xFFE00000) == 0xFFE00000;
        boolean mpeg1 = ((h >> 19) & 3) == 3;
        boolean layer2 = ((h >> 17) & 3) == 2;
        int bitrateIndex = (h >> 12) & 0xF;
        int rateIndex = (h >> 10) & 3;
        if (!sync || !mpeg1 || !layer2 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) {
            return -1;
        }
        int padding = (h >> 9) & 1;
        return 144 * BITRATES_KBPS[bitrateIndex] * 1000 / SAMPLE_RATES[rateIndex] + padding;
    }

    private static byte[] readAsset(String name) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (InputStream in = context.getAssets().open(name)) {
            return in.readAllBytes();
        }
    }
}
