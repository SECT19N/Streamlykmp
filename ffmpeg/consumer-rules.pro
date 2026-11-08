# Keep native method names (registered by name from JNI_OnLoad).
-keep,includedescriptorclasses class dev.section.streamly.ffmpeg.** {
    native <methods>;
}

# Called from native code.
-keep,includedescriptorclasses class dev.section.streamly.ffmpeg.FfmpegAudioDecoder {
    private java.nio.ByteBuffer growOutputBuffer(androidx.media3.decoder.SimpleDecoderOutputBuffer, int);
}
-keepclassmembers class androidx.media3.decoder.VideoDecoderOutputBuffer {
    public long timeUs;
    public java.nio.ByteBuffer data;
    public boolean initForYuvFrame(int, int, int, int, int);
}
