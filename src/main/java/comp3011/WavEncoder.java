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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Wraps raw 16-bit little-endian PCM samples in a standard 44-byte WAV header so the server and Whisper can read it. */
final class WavEncoder {

    private static final int HEADER_BYTES = 44;
    private static final int BYTES_PER_SAMPLE = 2;

    private WavEncoder() {
    }

    static byte[] encode(byte[] pcm, int sampleRate, int channels) {
        if (sampleRate <= 0 || channels <= 0) {
            throw new IllegalArgumentException("Sample rate and channel count must be positive");
        }
        ByteBuffer wav = ByteBuffer.allocate(HEADER_BYTES + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        wav.putInt(HEADER_BYTES - 8 + pcm.length);
        wav.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        wav.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        wav.putInt(16); // size of the format block
        wav.putShort((short) 1); // 1 = uncompressed PCM
        wav.putShort((short) channels);
        wav.putInt(sampleRate);
        wav.putInt(sampleRate * channels * BYTES_PER_SAMPLE); // bytes per second
        wav.putShort((short) (channels * BYTES_PER_SAMPLE)); // bytes per sample frame
        wav.putShort((short) (BYTES_PER_SAMPLE * 8)); // bits per sample
        wav.put("data".getBytes(StandardCharsets.US_ASCII));
        wav.putInt(pcm.length);
        wav.put(pcm);
        return wav.array();
    }
}