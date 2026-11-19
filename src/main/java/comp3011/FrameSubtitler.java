/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.bytedeco.javacv.Frame;

/**
 * Draws speech-to-text subtitles onto video frames. The cue to show is chosen from the frame's own timestamp, so the
 * result is the same whichever worker thread processes the frame and however playback was paused or seeked. Each cue is
 * rendered once and cached; every later frame only blends the cached image.
 */
public class FrameSubtitler extends FrameProcessor {

    private static final long SECOND = 1_000_000L;

    private final SubtitleTrack track;
    private final Map<SubtitleCue, SubtitleOverlay> overlays = new ConcurrentHashMap<>();
    private SubtitleRenderer renderer;
    private volatile boolean enabled = true; // Flipped by the JavaFX thread, read by the worker threads.

    public FrameSubtitler() {
        this(demoTrack());
    }

    FrameSubtitler(SubtitleTrack track) {
        this.track = track;
    }

    // TEMPORARY: hard-coded cues so the drawing can be checked by eye. Replaced by the server's subtitles.
    private static SubtitleTrack demoTrack() {
        return new SubtitleTrack(List.of(
                new SubtitleCue(1 * SECOND, 4 * SECOND, "Hello, and welcome to the subtitle demo."),
                new SubtitleCue(5 * SECOND, 9 * SECOND, "I went to the shop yesterday, and then I came straight home."),
                new SubtitleCue(10 * SECOND, 13 * SECOND, "Short one.")));
    }

    @Override
    public void initialise(InfoVideo videoInfo) throws Exception {
        super.initialise(videoInfo);
        renderer = new SubtitleRenderer(videoInfo.imageWidth(), videoInfo.imageHeight());
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void process(Frame frame, InfoFrame info) throws Exception {
        if (!enabled) {
            return;
        }
        if (frame.image == null || frame.image.length == 0 || frame.image[0] == null) {
            return;
        }
        if (!(frame.image[0] instanceof ByteBuffer pixels)) {
            throw new Exception("FrameSubtitler requires byte-backed image frames");
        }
        if (videoInfo.imageDepth() != Frame.DEPTH_UBYTE && videoInfo.imageDepth() != Frame.DEPTH_BYTE) {
            throw new Exception("FrameSubtitler requires 8-bit image frames");
        }

        Optional<SubtitleCue> cue = track.cueAt(info.timestampUs());
        if (cue.isEmpty()) {
            return;
        }
        SubtitleOverlay overlay = overlays.computeIfAbsent(cue.get(), this::buildOverlay);
        SubtitleRenderer.blend(pixels, videoInfo.imageStride(), videoInfo.imageChannels(),
                videoInfo.imageWidth(), videoInfo.imageHeight(), overlay);
    }

    // The cue's text is expected to fit on two lines already (see SubtitleText.chunks).
    private SubtitleOverlay buildOverlay(SubtitleCue cue) {
        return renderer.render(SubtitleText.lines(cue.text()));
    }
}