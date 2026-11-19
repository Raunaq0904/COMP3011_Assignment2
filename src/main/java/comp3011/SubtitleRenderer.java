/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Draws subtitle text as white sans-serif letters with a black outline, so it stays legible on any background, and
 * blends the result onto BGR video frames. Rendering uses only local objects, so it is safe to call from any thread.
 */
public final class SubtitleRenderer {

    private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, true);
    private static final float MIN_FONT_SIZE = 10f;
    private static final double MAX_WIDTH_FRACTION = 0.95;
    private static final double BOTTOM_MARGIN_FRACTION = 0.05;

    private final int imageWidth;
    private final int imageHeight;

    public SubtitleRenderer(int imageWidth, int imageHeight) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    /** Renders the lines centred near the bottom of the frame. */
    public SubtitleOverlay render(List<String> lines) {
        float fontSize = Math.max(MIN_FONT_SIZE, imageHeight / 20f);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(fontSize);
        double widest = widestLine(font, lines);
        while (widest + 4 * outlineWidth(fontSize) > imageWidth * MAX_WIDTH_FRACTION && fontSize > MIN_FONT_SIZE) {
            fontSize = Math.max(MIN_FONT_SIZE, fontSize * 0.9f);
            font = font.deriveFont(fontSize);
            widest = widestLine(font, lines);
        }

        float stroke = outlineWidth(fontSize);
        int pad = (int) Math.ceil(stroke) + 2;
        LineMetrics metrics = font.getLineMetrics("Ag", FONT_CONTEXT);
        float lineHeight = metrics.getHeight();

        int blockWidth = Math.min(imageWidth, (int) Math.ceil(widest) + 2 * pad);
        int blockHeight = Math.min(imageHeight, (int) Math.ceil(lines.size() * lineHeight) + 2 * pad);

        BufferedImage image = new BufferedImage(blockWidth, blockHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < lines.size(); i++) {
                GlyphVector glyphs = font.createGlyphVector(FONT_CONTEXT, lines.get(i));
                double lineWidth = glyphs.getLogicalBounds().getWidth();
                float textX = (float) ((blockWidth - lineWidth) / 2);
                float baseline = pad + i * lineHeight + metrics.getAscent();
                Shape outline = glyphs.getOutline(textX, baseline);
                g.setColor(Color.BLACK);
                g.draw(outline);
                g.setColor(Color.WHITE);
                g.fill(outline);
            }
        } finally {
            g.dispose();
        }

        int[] argb = image.getRGB(0, 0, blockWidth, blockHeight, null, 0, blockWidth);
        int x = Math.max(0, (imageWidth - blockWidth) / 2);
        int y = Math.max(0, imageHeight - blockHeight - (int) (imageHeight * BOTTOM_MARGIN_FRACTION));
        return new SubtitleOverlay(x, y, blockWidth, blockHeight, argb);
    }

    /**
     * Blends an overlay onto 8-bit BGR frame bytes. Pixels the overlay does not cover are left exactly as they were.
     */
    public static void blend(ByteBuffer pixels, int stride, int channels, int imageWidth, int imageHeight,
            SubtitleOverlay overlay) {
        for (int row = 0; row < overlay.height(); row++) {
            int frameY = overlay.y() + row;
            if (frameY < 0 || frameY >= imageHeight) {
                continue;
            }
            for (int column = 0; column < overlay.width(); column++) {
                int frameX = overlay.x() + column;
                if (frameX < 0 || frameX >= imageWidth) {
                    continue;
                }
                int argb = overlay.argb()[row * overlay.width() + column];
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }
                int base = frameY * stride + frameX * channels;
                for (int channel = 0; channel < channels; channel++) {
                    // Frames are BGR, so channel 0 is blue, which is the lowest byte of the ARGB value.
                    int source = (argb >> (8 * Math.min(channel, 2))) & 0xFF;
                    int destination = pixels.get(base + channel) & 0xFF;
                    int mixed = (source * alpha + destination * (255 - alpha) + 127) / 255;
                    pixels.put(base + channel, (byte) mixed);
                }
            }
        }
    }

    private static float outlineWidth(float fontSize) {
        return Math.max(2f, fontSize / 7f);
    }

    private static double widestLine(Font font, List<String> lines) {
        double widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, font.getStringBounds(line, FONT_CONTEXT).getWidth());
        }
        return widest;
    }
}