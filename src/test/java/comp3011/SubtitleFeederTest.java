/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 *
 * AI acknowledgement: this file was developed with the assistance of Claude (Anthropic's generative AI
 * assistant), used as a research, design and development aid. Claude proposed the design and the code,
 * explaining each part step by step. I typed every change in myself, then ran, tested and reviewed it so
 * that I understand what each part does and why it is in the project. All commits were made by me.
 * See the "AI acknowledgement" section of README.md for details.
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

class SubtitleFeederTest {

    private static final long SECOND = 1_000_000L;

    /** Pretends to be a media file's audio: chunks of the right length, no real sound. */
    private static final class FakeSource implements ChunkSource {
        private final long durationUs;
        private long cursor;
        final List<Long> seeks = Collections.synchronizedList(new ArrayList<>());

        FakeSource(long durationUs) {
            this.durationUs = durationUs;
        }

        @Override
        public long durationUs() {
            return durationUs;
        }

        @Override
        public void seekTo(long positionUs) {
            cursor = positionUs;
            seeks.add(positionUs);
        }

        @Override
        public AudioChunk nextChunk(long maxDurationUs) {
            if (cursor >= durationUs) {
                return null;
            }
            long length = Math.min(maxDurationUs, durationUs - cursor);
            AudioChunk chunk = new AudioChunk(cursor, length, new byte[] { 1, 2, 3, 4 });
            cursor += length;
            return chunk;
        }

        @Override
        public void close() {
        }
    }

    private static final List<SpokenSegment> HELLO = List.of(new SpokenSegment(0.5, 2.0, "Hello there."));

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for the feeder");
    }

    @Test
    void publishesCuesForTheSpeechTheServerFinds() throws Exception {
        SubtitleFeeder feeder = new SubtitleFeeder(() -> new FakeSource(20 * SECOND), (audio, name) -> HELLO, 10);
        try {
            feeder.setEnabled(true);
            waitFor(() -> feeder.track().size() > 0);

            assertEquals("Hello there.", feeder.track().cueAt(1 * SECOND).get().text());
        } finally {
            feeder.shutdown();
        }
    }

    @Test
    void makesNoServerRequestsWhileSwitchedOff() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SubtitleFeeder feeder = new SubtitleFeeder(() -> new FakeSource(20 * SECOND),
                (audio, name) -> {
                    calls.incrementAndGet();
                    return HELLO;
                }, 10);
        try {
            Thread.sleep(200);
            assertEquals(0, calls.get());

            feeder.setEnabled(true);
            waitFor(() -> calls.get() > 0);
        } finally {
            feeder.shutdown();
        }
    }

    @Test
    void stopsRequestingWhenSwitchedOffAndResumesWhenSwitchedOn() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SubtitleFeeder feeder = new SubtitleFeeder(() -> new FakeSource(36_000 * SECOND),
                (audio, name) -> {
                    calls.incrementAndGet();
                    Thread.sleep(20);
                    return HELLO;
                }, 10);
        try {
            feeder.setEnabled(true);
            waitFor(() -> calls.get() >= 2);
            feeder.setEnabled(false);
            Thread.sleep(200); // lets a request that was already on its way finish
            int whenOff = calls.get();

            Thread.sleep(300);
            assertEquals(whenOff, calls.get());

            feeder.setEnabled(true);
            waitFor(() -> calls.get() > whenOff);
        } finally {
            feeder.shutdown();
        }
    }

    @Test
    void retriesAfterFailuresAndRecoversByItself() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SubtitleFeeder feeder = new SubtitleFeeder(() -> new FakeSource(20 * SECOND),
                (audio, name) -> {
                    if (calls.incrementAndGet() <= 3) {
                        throw new IOException("network is down");
                    }
                    return HELLO;
                }, 10);
        try {
            feeder.setEnabled(true);
            waitFor(() -> feeder.track().size() > 0);

            assertTrue(calls.get() >= 4);
        } finally {
            feeder.shutdown();
        }
    }

    @Test
    void keepsTheMediaOpenWhenOnlyTheServerFails() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        SubtitleFeeder feeder = new SubtitleFeeder(() -> {
            opens.incrementAndGet();
            return new FakeSource(20 * SECOND);
        }, (audio, name) -> {
            if (calls.incrementAndGet() <= 3) {
                throw new IOException("connection refused");
            }
            return HELLO;
        }, 10);
        try {
            feeder.setEnabled(true);
            waitFor(() -> feeder.track().size() > 0);

            assertTrue(calls.get() >= 4);
            assertEquals(1, opens.get());
        } finally {
            feeder.shutdown();
        }
    }

    @Test
    void opensTheMediaAgainAfterItWasLost() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        SubtitleFeeder feeder = new SubtitleFeeder(() -> {
            if (opens.incrementAndGet() <= 2) {
                throw new IOException("the USB drive was removed");
            }
            return new FakeSource(20 * SECOND);
        }, (audio, name) -> HELLO, 10);
        try {
            feeder.setEnabled(true);
            waitFor(() -> feeder.track().size() > 0);

            assertTrue(opens.get() >= 3);
        } finally {
            feeder.shutdown();
        }
    }

    @Test
    void startsWhereTheVideoIsPlaying() throws Exception {
        FakeSource source = new FakeSource(20 * SECOND);
        SubtitleFeeder feeder = new SubtitleFeeder(() -> source, (audio, name) -> HELLO, 10);
        try {
            feeder.noteFramePosition(15 * SECOND);
            feeder.setEnabled(true);
            waitFor(() -> !source.seeks.isEmpty());

            assertEquals(15 * SECOND, source.seeks.get(0).longValue());
        } finally {
            feeder.shutdown();
        }
    }

    @Test
    void coversTheRestOfTheMediaThenWrapsRoundToTheStartExactlyOnce() throws Exception {
        FakeSource source = new FakeSource(20 * SECOND);
        AtomicInteger calls = new AtomicInteger();
        SubtitleFeeder feeder = new SubtitleFeeder(() -> source,
                (audio, name) -> {
                    calls.incrementAndGet();
                    return HELLO;
                }, 10);
        try {
            feeder.noteFramePosition(10 * SECOND);
            feeder.setEnabled(true);
            waitFor(() -> calls.get() >= 3);
            Thread.sleep(300); // nothing is left, so no further requests may follow

            assertEquals(3, calls.get()); // 10..18 s, 18..20 s, then 0..10 s
            assertEquals(List.of(10 * SECOND, 18 * SECOND, 0L), new ArrayList<>(source.seeks));
        } finally {
            feeder.shutdown();
        }
    }

    @Test
    void shutdownEndsTheFeeder() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SubtitleFeeder feeder = new SubtitleFeeder(() -> new FakeSource(36_000 * SECOND),
                (audio, name) -> {
                    calls.incrementAndGet();
                    Thread.sleep(10);
                    return HELLO;
                }, 10);
        feeder.setEnabled(true);
        waitFor(() -> calls.get() >= 1);

        feeder.shutdown();
        int afterShutdown = calls.get();
        Thread.sleep(200);

        assertEquals(afterShutdown, calls.get());
    }
}