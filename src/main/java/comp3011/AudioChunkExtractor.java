/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ShortBuffer;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

/**
 * Reads the audio of a media file in pieces, converted to mono 16 kHz 16-bit samples (what speech recognition needs,
 * and far smaller than the original). It owns a grabber of its own, separate from the one the player decodes with, so
 * subtitle work can never disturb playback. Not thread-safe: one thread should use an instance.
 *
 * <p>
 * Every failure (file missing, unreadable, removed mid-read) is reported as an {@link IOException}.
 * </p>
 */
final class AudioChunkExtractor implements AutoCloseable {

    static final int SAMPLE_RATE = 16_000;
    private static final int CHANNELS = 1;
    private static final long MICROS_PER_SECOND = 1_000_000L;

    private final FFmpegFrameGrabber grabber;

    AudioChunkExtractor(File media) throws IOException {
        FFmpegFrameGrabber opened = new FFmpegFrameGrabber(media);
        opened.setSampleMode(FrameGrabber.SampleMode.SHORT);
        opened.setAudioChannels(CHANNELS); // FFmpeg converts to this format while decoding.
        opened.setSampleRate(SAMPLE_RATE);
        try {
            opened.start();
            if (!opened.hasAudio()) {
                throw new IOException("The media has no audio track");
            }
        } catch (IOException | RuntimeException e) {
            closeQuietly(opened);
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException("The media could not be opened", e);
        }
        this.grabber = opened;
    }

    long durationUs() {
        return grabber.getLengthInTime();
    }

    /** Moves the next chunk to start at (about) this position in the media. */
    void seekTo(long positionUs) throws IOException {
        try {
            grabber.setTimestamp(Math.max(0, positionUs));
        } catch (RuntimeException e) {
            throw new IOException("Could not move within the media", e);
        }
    }

    /**
     * Reads about {@code maxDurationUs} of audio (a little more, as whole decoded frames are kept).
     *
     * @return the chunk, or null when the end of the media has been reached
     */
    AudioChunk nextChunk(long maxDurationUs) throws IOException {
        long maxSamples = Math.max(1, maxDurationUs * SAMPLE_RATE / MICROS_PER_SECOND);
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        long samples = 0;
        long startUs = -1;
        try {
            while (samples < maxSamples) {
                Frame frame = grabber.grabSamples(); // Audio only: video is skipped, not decoded.
                if (frame == null) {
                    break;
                }
                if (frame.samples == null || frame.samples.length == 0 || !(frame.samples[0] instanceof ShortBuffer)) {
                    continue;
                }
                if (startUs < 0) {
                    startUs = grabber.getTimestamp();
                }
                ShortBuffer source = ((ShortBuffer) frame.samples[0]).duplicate();
                samples += source.remaining();
                while (source.hasRemaining()) {
                    short sample = source.get();
                    pcm.write(sample & 0xff); // little-endian: low byte first
                    pcm.write((sample >> 8) & 0xff);
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("The media's audio could not be read", e);
        }
        if (samples == 0) {
            return null;
        }
        return new AudioChunk(Math.max(0, startUs), samples * MICROS_PER_SECOND / SAMPLE_RATE,
                WavEncoder.encode(pcm.toByteArray(), SAMPLE_RATE, CHANNELS));
    }

    @Override
    public void close() {
        closeQuietly(grabber);
    }

    private static void closeQuietly(FFmpegFrameGrabber target) {
        try {
            target.stop();
        } catch (Exception ignored) {
            // Already closed or never started: nothing to clean up.
        }
        try {
            target.close();
        } catch (Exception ignored) {
            // As above.
        }
    }
}