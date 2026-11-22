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

import java.io.IOException;
import java.util.List;

/**
 * The background thread that produces the subtitles. While subtitles are switched on it repeatedly reads a chunk of the
 * media's audio, sends it to the subtitle server, turns the reply into cues and publishes them as a new
 * {@link SubtitleTrack}. It never runs ahead of what is needed: it starts where the video is playing, works forward,
 * then wraps round to whatever was skipped, and sends nothing while subtitles are switched off.
 *
 * <p>
 * Threads: this class owns one thread of its own and never touches the player's decode thread or its grabber (the audio
 * is read through a separate {@link ChunkSource}). The worker threads that draw the frames only call {@link #track()},
 * which reads one volatile reference to an immutable track, and {@link #noteFramePosition(long)}, which writes one
 * volatile number, so they never wait for the network.
 * </p>
 *
 * <p>
 * Failures: the server being down, the network dropping or an error reply all end up as an {@link IOException}, as does
 * the media file disappearing. The feeder then waits (1 s, 2 s, 4 s ... up to 10 s) and retries the same stretch, so
 * subtitles resume by themselves when the network or the media comes back. If the media was the problem it is opened
 * again for the retry; if only the server was, the media stays open. Playback is never affected.
 * </p>
 */
final class SubtitleFeeder {

    /** Sends audio to the server. {@link SubtitleServerClient#transcribe} matches this. */
    @FunctionalInterface
    interface Transcriber {
        List<SpokenSegment> transcribe(byte[] audio, String filename) throws IOException, InterruptedException;
    }

    /** Opens the media's audio. Called again after a failure, since the media may have come back. */
    @FunctionalInterface
    interface SourceOpener {
        ChunkSource open() throws IOException;
    }

    static final long FIRST_CHUNK_US = 8_000_000L; // short, so subtitles start soon after they are switched on
    static final long CHUNK_US = 30_000_000L; // longer, for the stretches ahead of the viewer
    private static final long NEAR_PLAYBACK_US = 2_000_000L;
    private static final long MIN_GAP_US = 1_000_000L; // smaller gaps are not worth a request
    private static final long MIN_PROGRESS_US = 1_000_000L; // guarantees every request moves forward
    private static final long UNKNOWN_END_US = Long.MAX_VALUE / 4;
    private static final long IDLE_MS = 500;
    private static final long MAX_BACKOFF_MS = 10_000;

    private final SourceOpener opener;
    private final Transcriber transcriber;
    private final long initialBackoffMs;
    private final CoverageMap coverage = new CoverageMap(); // feeder thread only

    private final Object lock = new Object(); // guards enabled, stopped and thread
    private boolean enabled;
    private boolean stopped;
    private Thread thread;

    private volatile SubtitleTrack track = SubtitleTrack.EMPTY;
    private volatile long lastFrameUs;
    private int chunkCount; // feeder thread only

    SubtitleFeeder(SourceOpener opener, Transcriber transcriber) {
        this(opener, transcriber, 1_000);
    }

    SubtitleFeeder(SourceOpener opener, Transcriber transcriber, long initialBackoffMs) {
        this.opener = opener;
        this.transcriber = transcriber;
        this.initialBackoffMs = initialBackoffMs;
    }

    /** The subtitles found so far. Safe to call from any thread, as often as needed. */
    SubtitleTrack track() {
        return track;
    }

    /** Tells the feeder where the video is playing. Only a hint, so races between threads do not matter. */
    void noteFramePosition(long timestampUs) {
        lastFrameUs = timestampUs;
    }

    /** Switches the feeder on or off. The thread is only created the first time it is switched on. */
    void setEnabled(boolean on) {
        synchronized (lock) {
            if (stopped) {
                return;
            }
            enabled = on;
            if (on && thread == null) {
                thread = new Thread(this::run, "subtitle-feeder");
                thread.setDaemon(true);
                thread.start();
            }
            lock.notifyAll();
        }
    }

    /** Ends the feeder thread and waits briefly for it. */
    void shutdown() {
        Thread toStop;
        synchronized (lock) {
            stopped = true;
            toStop = thread;
            lock.notifyAll();
        }
        if (toStop != null) {
            toStop.interrupt();
            try {
                toStop.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run() {
        ChunkSource source = null;
        long backoffMs = initialBackoffMs;
        int failures = 0;
        try {
            while (awaitEnabled()) {
                try {
                    if (source == null) {
                        source = opener.open();
                    }
                    boolean didWork = transcribeNextChunk(source);
                    backoffMs = initialBackoffMs;
                    failures = 0;
                    if (!didWork) {
                        pause(IDLE_MS); // everything is done: wait for a seek or a toggle
                    }
                } catch (IOException | RuntimeException e) {
                    failures++;
                    if (!(e instanceof ServerUnavailableException)) {
                        closeQuietly(source);
                        source = null; // reopened on the next attempt, as the media may have come back
                    }
                    if (failures == 1 || failures % 10 == 0) {
                        System.err.println("Subtitles: " + describe(e) + " (retrying in " + backoffMs / 1000.0
                                + " s)");
                    }
                    pause(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // shutdown
        } finally {
            closeQuietly(source);
        }
    }

    // Returns false when there is nothing left to transcribe.
    private boolean transcribeNextChunk(ChunkSource source) throws IOException, InterruptedException {
        long duration = source.durationUs();
        long end = duration > 0 ? duration : UNKNOWN_END_US;
        long playback = lastFrameUs;

        long start = coverage.nextToDo(playback, end);
        if (start < 0) {
            return false;
        }
        long room = coverage.gapEnd(start, end) - start;
        if (room < MIN_GAP_US) {
            coverage.markCovered(start, start + room);
            return true;
        }
        long length = Math.abs(start - playback) <= NEAR_PLAYBACK_US ? FIRST_CHUNK_US : CHUNK_US;

        source.seekTo(start);
        AudioChunk chunk = source.nextChunk(Math.min(length, room));
        if (chunk == null) {
            coverage.markCovered(start, end); // the media ended here
            return true;
        }
        if (!isEnabled()) {
            return true; // switched off while reading: send nothing, and do not mark it done
        }

        List<SpokenSegment> segments;
        try {
            segments = transcriber.transcribe(chunk.wav(), "chunk-" + (++chunkCount) + ".wav");
        } catch (IOException e) {
            throw new ServerUnavailableException(e); // the media is fine, so it is kept open for the retry
        }
        List<SubtitleCue> cues = SubtitleCueBuilder.build(segments, chunk.startUs());
        if (!cues.isEmpty()) {
            track = track.plus(cues); // swapping one volatile reference publishes it to the drawing threads
        }
        coverage.markCovered(start, Math.max(chunk.startUs() + chunk.durationUs(), start + MIN_PROGRESS_US));
        return true;
    }

    /** A failure of the server or the network, as opposed to a failure of the media. */
    private static final class ServerUnavailableException extends IOException {
        private static final long serialVersionUID = 1L;

        ServerUnavailableException(IOException cause) {
            super(describe(cause), cause);
        }
    }

    // Some exceptions (a refused connection, for one) carry no message, so fall back to the exception's name.
    private static String describe(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    private boolean isEnabled() {
        synchronized (lock) {
            return enabled && !stopped;
        }
    }

    // Blocks while switched off. Returns false once the feeder has been shut down.
    private boolean awaitEnabled() throws InterruptedException {
        synchronized (lock) {
            while (!enabled && !stopped) {
                lock.wait();
            }
            return !stopped;
        }
    }

    private void pause(long millis) throws InterruptedException {
        synchronized (lock) {
            if (!stopped) {
                lock.wait(millis); // ends early if the feeder is switched or shut down
            }
        }
    }

    private static void closeQuietly(ChunkSource source) {
        if (source != null) {
            source.close();
        }
    }
}