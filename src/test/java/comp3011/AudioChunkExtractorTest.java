/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioChunkExtractorTest {

    private static final long SECOND = 1_000_000L;

    /** Writes a short 440 Hz tone as a WAV file, so the test needs no media file of its own. */
    private static File writeTone(Path directory, int sampleRate, int channels, int seconds) throws IOException {
        int frames = sampleRate * seconds;
        byte[] pcm = new byte[frames * channels * 2];
        for (int i = 0; i < frames; i++) {
            short value = (short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 8000);
            for (int channel = 0; channel < channels; channel++) {
                int index = (i * channels + channel) * 2;
                pcm[index] = (byte) value;
                pcm[index + 1] = (byte) (value >> 8);
            }
        }
        File file = directory.resolve("tone-" + sampleRate + "-" + channels + ".wav").toFile();
        Files.write(file.toPath(), WavEncoder.encode(pcm, sampleRate, channels));
        return file;
    }

    private static List<AudioChunk> readAll(AudioChunkExtractor extractor, long chunkUs) throws IOException {
        List<AudioChunk> chunks = new ArrayList<>();
        AudioChunk chunk;
        while ((chunk = extractor.nextChunk(chunkUs)) != null) {
            chunks.add(chunk);
        }
        return chunks;
    }

    @Test
    void convertsToMono16kHzAndSplitsIntoChunks(@TempDir Path directory) throws Exception {
        File media = writeTone(directory, 44_100, 2, 3);

        try (AudioChunkExtractor extractor = new AudioChunkExtractor(media)) {
            List<AudioChunk> chunks = readAll(extractor, SECOND);

            assertTrue(chunks.size() >= 3, "Expected at least three chunks, got " + chunks.size());
            long total = 0;
            long previousStart = -1;
            for (AudioChunk chunk : chunks) {
                ByteBuffer header = ByteBuffer.wrap(chunk.wav()).order(ByteOrder.LITTLE_ENDIAN);
                assertEquals(1, header.getShort(22), "mono");
                assertEquals(16_000, header.getInt(24), "16 kHz");
                assertTrue(chunk.startUs() > previousStart, "chunks should move forward through the media");
                previousStart = chunk.startUs();
                total += chunk.durationUs();
            }
            assertEquals(0, chunks.get(0).startUs(), 100_000);
            assertEquals(3 * SECOND, total, 150_000);
        }
    }

    @Test
    void seekingStartsLaterInTheMedia(@TempDir Path directory) throws Exception {
        File media = writeTone(directory, 16_000, 1, 4);

        try (AudioChunkExtractor extractor = new AudioChunkExtractor(media)) {
            extractor.seekTo(2 * SECOND);
            List<AudioChunk> chunks = readAll(extractor, SECOND);

            assertTrue(!chunks.isEmpty());
            assertEquals(2 * SECOND, chunks.get(0).startUs(), 200_000);
            long total = chunks.stream().mapToLong(AudioChunk::durationUs).sum();
            assertEquals(2 * SECOND, total, 200_000);
        }
    }

    @Test
    void aMissingFileIsReportedAsAnIoException(@TempDir Path directory) {
        File missing = directory.resolve("no-such-file.wav").toFile();

        assertThrows(IOException.class, () -> new AudioChunkExtractor(missing).close());
    }
}