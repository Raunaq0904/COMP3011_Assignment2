/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import org.bytedeco.javacv.Frame;

/**
 * Draws speech-to-text subtitles onto video frames. The cue to show is chosen from the frame's own timestamp, so the
 * result is the same whichever worker thread processes the frame and however playback was paused or seeked. Each cue is
 * rendered once and cached; every later frame only blends the cached image.
 *
 * <p>
 * Where the subtitles come from: a subtitler starts with none. The controller attaches it to the subtitle feeder, whose
 * track grows as the server's answers arrive. Every frame also tells the feeder where the video is, so the feeder
 * knows which part of the media to transcribe first.
 * </p>
 */
public class FrameSubtitler extends FrameProcessor {

    private static final int MAX_CACHED_OVERLAYS = 32; // A cue is on screen for 7 s at most, so few are live at once.

    private final Map<SubtitleCue, SubtitleOverlay> overlays = new ConcurrentHashMap<>();
    private SubtitleRenderer renderer;
    private volatile boolean enabled = true; // Flipped by the JavaFX thread, read by the worker threads.
    private volatile Supplier<SubtitleTrack> trackSource = () -> SubtitleTrack.EMPTY;
    private volatile LongConsumer positionSink = timestampUs -> {
    };

    public FrameSubtitler() {
    }

    FrameSubtitler(SubtitleTrack track) {
        this.trackSource = () -> track;
    }

    /** Connects this subtitler to the source of its cues. Called once, before playback starts. */
    void attach(Supplier<SubtitleTrack> trackSource, LongConsumer positionSink) {
        this.trackSource = trackSource;
        this.positionSink = positionSink;
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
        positionSink.accept(info.timestampUs()); // Even while switched off, so a later switch-on starts in the right place.
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

        Optional<SubtitleCue> cue = trackSource.get().cueAt(info.timestampUs());
        if (cue.isEmpty()) {
            return;
        }
        if (overlays.size() >= MAX_CACHED_OVERLAYS) {
            overlays.clear(); // Simplest bound on memory: anything still needed is simply drawn again.
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