/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Test;

class FrameSubtitlerTest {

    private static final long SECOND = 1_000_000L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private static Frame greyFrame() {
        Frame frame = new Frame(WIDTH, HEIGHT, Frame.DEPTH_UBYTE, 3);
        ByteBuffer pixels = (ByteBuffer) frame.image[0];
        for (int i = 0; i < pixels.capacity(); i++) {
            pixels.put(i, (byte) 128);
        }
        return frame;
    }

    private static boolean changed(Frame frame) {
        ByteBuffer pixels = (ByteBuffer) frame.image[0];
        for (int i = 0; i < pixels.capacity(); i++) {
            if (pixels.get(i) != (byte) 128) {
                return true;
            }
        }
        return false;
    }

    private static InfoVideo infoFor(Frame frame) {
        return new InfoVideo("test.mp4", 100, WIDTH, HEIGHT, Frame.DEPTH_UBYTE, 3, frame.imageStride,
                25.0, 25, 40_000, 0);
    }

    private static SubtitleTrack helloTrack() {
        return new SubtitleTrack(List.of(new SubtitleCue(1 * SECOND, 3 * SECOND, "Hello there.")));
    }

    private static FrameSubtitler subtitlerFor(Frame frame) throws Exception {
        FrameSubtitler subtitler = new FrameSubtitler(helloTrack());
        subtitler.initialise(infoFor(frame));
        return subtitler;
    }

    @Test
    void drawsTheSubtitleWhileACueIsActive() throws Exception {
        Frame frame = greyFrame();

        subtitlerFor(frame).process(frame, new InfoFrame(50, 2 * SECOND));

        assertTrue(changed(frame));
    }

    @Test
    void leavesTheFrameAloneWhenNoCueIsActive() throws Exception {
        Frame frame = greyFrame();

        subtitlerFor(frame).process(frame, new InfoFrame(100, 4 * SECOND));

        assertFalse(changed(frame));
    }

    @Test
    void leavesTheFrameAloneWhenSwitchedOff() throws Exception {
        Frame frame = greyFrame();
        FrameSubtitler subtitler = subtitlerFor(frame);
        subtitler.setEnabled(false);

        subtitler.process(frame, new InfoFrame(50, 2 * SECOND));

        assertFalse(changed(frame));
    }

    @Test
    void drawsCuesFromTheSourceItIsAttachedTo() throws Exception {
        Frame frame = greyFrame();
        FrameSubtitler subtitler = new FrameSubtitler();
        subtitler.initialise(infoFor(frame));
        SubtitleTrack track = helloTrack();
        subtitler.attach(() -> track, timestampUs -> {
        });

        subtitler.process(frame, new InfoFrame(50, 2 * SECOND));

        assertTrue(changed(frame));
    }

    @Test
    void showsNothingUntilItIsAttachedToASource() throws Exception {
        Frame frame = greyFrame();
        FrameSubtitler subtitler = new FrameSubtitler();
        subtitler.initialise(infoFor(frame));

        subtitler.process(frame, new InfoFrame(50, 2 * SECOND));

        assertFalse(changed(frame)); // In particular, no demo text is ever drawn.
    }

    @Test
    void reportsTheFramePositionEvenWhileSwitchedOff() throws Exception {
        Frame frame = greyFrame();
        FrameSubtitler subtitler = new FrameSubtitler();
        subtitler.initialise(infoFor(frame));
        List<Long> positions = Collections.synchronizedList(new ArrayList<>());
        subtitler.attach(() -> SubtitleTrack.EMPTY, positions::add);
        subtitler.setEnabled(false);

        subtitler.process(frame, new InfoFrame(50, 2 * SECOND));

        assertEquals(List.of(2 * SECOND), positions);
    }
}