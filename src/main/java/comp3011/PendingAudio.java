/*
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

/**
 * A chunk of decoded audio waiting to be written to the sound card. The decode thread creates it and queues it; the
 * JavaFX thread then owns it and tracks how much has been written so far. It is never touched by two threads at once:
 * the queue hands it over.
 */
final class PendingAudio {
    private final long timestampUs;
    private final byte[] samples;
    private int offset;

    PendingAudio(long timestampUs, byte[] samples) {
        this.timestampUs = timestampUs;
        this.samples = samples;
    }

    long timestampUs() {
        return timestampUs;
    }

    byte[] samples() {
        return samples;
    }

    int offset() {
        return offset;
    }

    int remaining() {
        return samples.length - offset;
    }

    void advance(int byteCount) {
        offset += byteCount;
    }

    boolean finished() {
        return offset >= samples.length;
    }
}