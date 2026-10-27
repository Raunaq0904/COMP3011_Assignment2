/*
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.util.Queue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

/**
 * The decode thread. Reads frames from the FFmpeg grabber, copies audio into a queue for the JavaFX thread to play,
 * and submits video frames to the {@link FrameProcessingPipeline}.
 *
 * <p>
 * Why a thread of its own: decoding must be sequential (the grabber is not thread-safe) but it must not run on the
 * JavaFX thread, where it would freeze the GUI. Everything that can be done in parallel (the effects) is left to the
 * pipeline's workers.
 * </p>
 *
 * <p>
 * Ownership rule: while this decoder is running it is the only thread allowed to touch the grabber and
 * {@link AudioPlayer#copySamples}. The JavaFX thread may use them again only after {@link #stopAndWait} returns.
 * This thread never waits for the JavaFX thread (it reports failures with a callback that must not block), so the
 * JavaFX thread waiting for it in {@code stopAndWait} cannot deadlock.
 * </p>
 *
 * <p>
 * One decoder instance runs once. After a stop (seek, stop, finish) a new instance is created.
 * </p>
 */
final class FrameDecoder {
    private static final long STOP_TIMEOUT_MS = 3_000L;

    private final FFmpegFrameGrabber grabber;
    private final FrameProcessingPipeline pipeline;
    private final AudioPlayer audioPlayer;
    private final boolean audioAvailable;
    private final BooleanSupplier audioOutputEnabled;
    private final Queue<PendingAudio> pendingAudio;
    private final Function<Frame, InfoVideo> videoInfoForFrame;
    private final Consumer<Exception> failureHandler;

    private volatile boolean stopRequested;
    private Thread thread;

    FrameDecoder(
            FFmpegFrameGrabber grabber,
            FrameProcessingPipeline pipeline,
            AudioPlayer audioPlayer,
            boolean audioAvailable,
            BooleanSupplier audioOutputEnabled,
            Queue<PendingAudio> pendingAudio,
            Function<Frame, InfoVideo> videoInfoForFrame,
            Consumer<Exception> failureHandler) {
        this.grabber = grabber;
        this.pipeline = pipeline;
        this.audioPlayer = audioPlayer;
        this.audioAvailable = audioAvailable;
        this.audioOutputEnabled = audioOutputEnabled;
        this.pendingAudio = pendingAudio;
        this.videoInfoForFrame = videoInfoForFrame;
        this.failureHandler = failureHandler;
    }

    void start() {
        thread = new Thread(this::decodeUntilEndOrStopped, "frame-decoder");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Asks the decode thread to stop and waits for it. After this returns the caller owns the grabber again. The wait
     * is bounded by one grab (or by the free-slot wait in the pipeline, which the interrupt ends immediately).
     */
    void stopAndWait() {
        stopRequested = true;
        if (thread == null) {
            return;
        }
        thread.interrupt();
        try {
            thread.join(STOP_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void decodeUntilEndOrStopped() {
        try {
            while (!stopRequested) {
                Frame frame = audioAvailable ? grabber.grab() : grabber.grabImage();
                if (frame == null) {
                    pipeline.finishInput();
                    return;
                }

                long timestampUs = grabber.getTimestamp();
                if (audioAvailable && frame.samples != null) {
                    queueAudio(timestampUs, frame);
                }

                if (frame.image == null) {
                    continue;
                }

                // Effects need the video's real dimensions, which are only known once a frame has been decoded.
                pipeline.initialiseEffectsOnce(videoInfoForFrame.apply(frame));
                pipeline.submit(frame, new InfoFrame(grabber.getFrameNumber(), timestampUs));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // The normal way a stop request ends a wait in submit.
        } catch (Exception e) {
            if (!stopRequested) { // Errors caused by tearing down mid-grab are expected and not worth reporting.
                failureHandler.accept(e);
            }
        }
    }

    // Audio is copied here, on the decode thread, because AudioPlayer.copySamples reuses one internal buffer and so
    // must only ever have one caller. Writing to the sound card stays on the JavaFX thread.
    private void queueAudio(long timestampUs, Frame frame) {
        if (!audioOutputEnabled.getAsBoolean() || audioPlayer == null) {
            return;
        }

        byte[] samples = audioPlayer.copySamples(frame);
        if (samples.length > 0) {
            pendingAudio.add(new PendingAudio(timestampUs, samples));
        }
    }
}