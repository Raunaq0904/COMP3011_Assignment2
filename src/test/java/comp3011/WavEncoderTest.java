/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class WavEncoderTest {

    @Test
    void writesAStandardHeaderFollowedByTheSamples() {
        byte[] pcm = { 1, 2, 3, 4, 5, 6 };

        byte[] wav = WavEncoder.encode(pcm, 16_000, 1);

        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(44 + pcm.length, wav.length);
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(wav.length - 8, buffer.getInt(4));
        assertEquals("WAVEfmt ", new String(wav, 8, 8, StandardCharsets.US_ASCII));
        assertEquals(1, buffer.getShort(20)); // PCM
        assertEquals(1, buffer.getShort(22)); // channels
        assertEquals(16_000, buffer.getInt(24)); // sample rate
        assertEquals(32_000, buffer.getInt(28)); // bytes per second
        assertEquals(2, buffer.getShort(32)); // bytes per sample frame
        assertEquals(16, buffer.getShort(34)); // bits per sample
        assertEquals("data", new String(wav, 36, 4, StandardCharsets.US_ASCII));
        assertEquals(pcm.length, buffer.getInt(40));
        for (int i = 0; i < pcm.length; i++) {
            assertEquals(pcm[i], wav[44 + i]);
        }
    }

    @Test
    void rejectsAnImpossibleFormat() {
        assertThrows(IllegalArgumentException.class, () -> WavEncoder.encode(new byte[2], 0, 1));
        assertThrows(IllegalArgumentException.class, () -> WavEncoder.encode(new byte[2], 16_000, 0));
    }
}
