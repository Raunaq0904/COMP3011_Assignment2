/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.Test;

class SubtitleRendererTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int CHANNELS = 3;
    private static final int STRIDE = WIDTH * CHANNELS;

    private static final SubtitleRenderer RENDERER = new SubtitleRenderer(WIDTH, HEIGHT);

    @Test
    void textIsWhiteWithABlackOutline() {
        SubtitleOverlay overlay = RENDERER.render(List.of("Hello there"));

        boolean hasWhite = false;
        boolean hasBlack = false;
        for (int argb : overlay.argb()) {
            if (argb == 0xFFFFFFFF) {
                hasWhite = true;
            }
            if (argb == 0xFF000000) {
                hasBlack = true;
            }
        }
        assertTrue(hasWhite, "expected solid white letter pixels");
        assertTrue(hasBlack, "expected solid black outline pixels");
    }

    @Test
    void overlayFitsInsideTheFrame() {
        SubtitleOverlay overlay = RENDERER.render(List.of("A line of subtitle text", "and a second line"));

        assertTrue(overlay.x() >= 0 && overlay.y() >= 0);
        assertTrue(overlay.x() + overlay.width() <= WIDTH);
        assertTrue(overlay.y() + overlay.height() <= HEIGHT);
    }

    @Test
    void twoLinesAreTallerThanOne() {
        SubtitleOverlay one = RENDERER.render(List.of("Just one line"));
        SubtitleOverlay two = RENDERER.render(List.of("Just one line", "and another"));

        assertTrue(two.height() > one.height());
    }

    @Test
    void blendingOnlyChangesPixelsInsideTheOverlay() {
        SubtitleOverlay overlay = RENDERER.render(List.of("Hello there"));
        ByteBuffer pixels = ByteBuffer.allocate(STRIDE * HEIGHT);
        for (int i = 0; i < pixels.capacity(); i++) {
            pixels.put(i, (byte) 128);
        }

        SubtitleRenderer.blend(pixels, STRIDE, CHANNELS, WIDTH, HEIGHT, overlay);

        int changed = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean inside = x >= overlay.x() && x < overlay.x() + overlay.width()
                        && y >= overlay.y() && y < overlay.y() + overlay.height();
                for (int c = 0; c < CHANNELS; c++) {
                    boolean different = pixels.get(y * STRIDE + x * CHANNELS + c) != (byte) 128;
                    if (different) {
                        changed++;
                        assertTrue(inside, "pixel changed outside the overlay at " + x + "," + y);
                    }
                }
            }
        }
        assertTrue(changed > 0, "blending should have changed some pixels");
        assertEquals((byte) 128, pixels.get(0));
    }
}