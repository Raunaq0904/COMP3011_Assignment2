/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. Raunaqdeep Singh Khosla, a1954269
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.JavaFXFrameConverter;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.image.Image;

/**
 * The model part of the video player model-view-controller architecture.
 *
 * <p>
 * Owns the FFmpeg decoder, the playback clock, seeking logic and audio scheduling, and decides which frame is on
 * screen when. Decoding and effect processing no longer happen here: they run on other threads (below), so this
 * class, which runs on the JavaFX thread, only has to show frames that are already finished. It publishes frames and
 * playback state through callbacks supplied by its owning {@link VideoPlayerController}, keeping it decoupled from
 * the JavaFX view.
 * </p>
 *
 * <p>
 * Threading design (the argument for its correctness is in README.md):
 * </p>
 * <ul>
 * <li><b>JavaFX thread</b> (every method in this class, including {@code pumpPlayback}): owns the playback clock,
 * the frame on screen, every call into the view, and writing audio to the sound card. Those fields are never touched
 * by another thread, so they need no locks.</li>
 * <li><b>Decode thread</b> ({@link FrameDecoder}): the only thread that uses the grabber while it is running. It
 * hands video frames to the pipeline and audio to {@code pendingAudio}.</li>
 * <li><b>Worker threads</b> ({@link FrameProcessingPipeline}): apply the effect chain to separate copies of frames.
 * </li>
 * </ul>
 *
 * <p>
 * The hand-over points between threads are: the pipeline's ordered queue (video), the concurrent
 * {@code pendingAudio} queue (audio), the volatile {@code audioOutputEnabled} flag, and start/join of the decode
 * thread (ownership of the grabber). A seek or stop first stops and joins the decode thread, so the JavaFX thread
 * never uses the grabber while the decode thread does. The decode and worker threads never wait for the JavaFX
 * thread, so no cycle of waiting, and therefore no deadlock, is possible.
 * </p>
 */
public class VideoPlayerModel {
    private static final long NO_SEEK_REQUEST = -1; // Sentinel value used when no seek position is active.
    private static final long FIVE_SECONDS_US = 5_000_000L;
    private static final long AUDIO_LEAD_NS = 30_000_000L;

    // Leave cores for the JavaFX thread and the decode thread, but always run at least two frames at once.
    private static final int EFFECT_WORKER_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);

    // About half a second of video at 24 fps. Enough to ride out a slow frame, small enough to bound memory.
    private static final int MAX_FRAMES_IN_FLIGHT = 12;

    private final List<FrameProcessor> frameProcessors;

    // Filled by the decode thread, emptied by the JavaFX thread, so it must be a concurrent queue.
    private final Queue<PendingAudio> pendingAudio = new ConcurrentLinkedQueue<>();
    private final BiConsumer<Integer, Integer> videoSizeChangedHandler;
    private final Consumer<Image> frameReadyHandler;
    private final Consumer<String> statusChangedHandler;
    private final BiConsumer<Boolean, Boolean> playbackStateChangedHandler;
    private final Consumer<Boolean> audioOutputStateChangedHandler;

    // This is the critical wiring that allows the framework (JavaFX) to call
    // into our model logic every time it goes around its event loop. Every GUI
    // system has an event loop so that button clicks, key presses and window
    // resizes can be responded to. Most offer ways of running additional logic
    // in either idle time (when there are no pending UI events to process) or
    // on a regular heart beat, such as with this JavaFX AnimationTimer. Here
    // we just create a little anonymous local subclass and override the handle
    // method to call the method we want run every heart beat.
    private final AnimationTimer playbackTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            pumpPlayback(now);
        }
    };

    private File videoFile; // Path to the media that comes from the command line
    private FFmpegFrameGrabber grabber; // Third party decoder. Used by the decode thread while it runs, else by us.
    private JavaFXFrameConverter converter; // Takes Frame objects to JavaFX Image objects. JavaFX thread only.
    private AudioPlayer audioPlayer; // This is ours. It has some real time buffering smarts.
    private FrameProcessingPipeline pipeline; // Worker threads applying the effects. One per playback.
    private FrameDecoder decoder; // The decode thread. One per start or seek.
    private PreparedFrame preparedFrame; // Ours, but is just an Image with some meta-data added.
    private boolean playbackOpen; // True when playback is happening.
    private boolean pauseRequested;
    private volatile boolean audioOutputEnabled; // Volatile: the decode thread reads it to skip unwanted audio.
    private boolean audioAvailable;
    // True from a start or seek until the first frame after it has been shown, so that a paused player still shows
    // that one frame, as the original did.
    private boolean awaitingFrameAfterSeek;
    private long currentTimestampUs;
    private long videoDurationUs = NO_SEEK_REQUEST;
    private int videoFrameDurationUs;
    private double frameRate;
    private int intFrameRate;
    private int totalVideoFrames;
    private long firstTimestampUs = NO_SEEK_REQUEST;
    private long logicalPlaybackBaseUs;
    private long playbackStartNs;
    private long pauseStartedNs;
    private long relativeSeekBaseUs = NO_SEEK_REQUEST;

    public VideoPlayerModel(
    		boolean audioEnabled,
    		List<FrameProcessor> frameProcessors,
    		BiConsumer<Integer, Integer> videoSizeChangedHandler,
            Consumer<Image> frameReadyHandler,
            Consumer<String> statusChangedHandler,
            BiConsumer<Boolean, Boolean> playbackStateChangedHandler,
            Consumer<Boolean> audioOutputStateChangedHandler) {
        audioOutputEnabled = audioEnabled;
        this.videoSizeChangedHandler = videoSizeChangedHandler;
        this.frameReadyHandler = frameReadyHandler;
        this.statusChangedHandler = statusChangedHandler;
        this.playbackStateChangedHandler = playbackStateChangedHandler;
        this.audioOutputStateChangedHandler = audioOutputStateChangedHandler;
        this.frameProcessors = List.copyOf(frameProcessors);
    }

    public void play(File file) {
        videoFile = file;
        videoDurationUs = NO_SEEK_REQUEST;
        videoFrameDurationUs = 0;
        startPlayback(0, false, NO_SEEK_REQUEST);
    }

    public void startOver() {
        if (videoFile == null) {
            return;
        }

        seekTo(0);
    }

    public void backFiveSeconds() {
        seekRelative(-FIVE_SECONDS_US);
    }

    public void forwardFiveSeconds() {
        seekRelative(FIVE_SECONDS_US);
    }

    public void togglePause() {
        if (!playbackOpen) {
            if (videoFile != null) {
                startPlayback(displayableSeekTimestamp(currentTimestampUs), false, currentTimestampUs);
            }
            return;
        }

        pauseRequested = !pauseRequested;
        relativeSeekBaseUs = NO_SEEK_REQUEST;

        if (pauseRequested) {
            pauseStartedNs = System.nanoTime();
            flushAudioOutput();
        } else {
            resumePlaybackClock(System.nanoTime());
        }

        notifyPlaybackStateChanged();
    }

    public void toggleAudioOutput() {
        audioOutputEnabled = !audioOutputEnabled;
        pendingAudio.clear();
        flushAudioOutput();
        notifyAudioOutputStateChanged();
    }

    public boolean isAudioOutputEnabled() {
        return audioOutputEnabled;
    }

    public void stopPlayback() {
        closePlaybackResources();
        currentTimestampUs = 0;
        pauseRequested = false;
        notifyStatusChanged("Stopped");
        notifyFrameReady(null);
        notifyPlaybackStateChanged();
    }

    public void shutdown() {
        closePlaybackResources();
    }

    private void seekRelative(long offsetUs) {
        if (videoFile == null) {
            return;
        }

        long baseTimestampUs = relativeSeekBaseUs != NO_SEEK_REQUEST
                ? relativeSeekBaseUs
                : currentTimestampUs;
        seekTo(baseTimestampUs + offsetUs);
    }

    private void seekTo(long timestampUs) {
        long logicalTimestampUs = clampSeekTimestamp(timestampUs);
        long grabTimestampUs = displayableSeekTimestamp(logicalTimestampUs);

        if (!playbackOpen) {
            startPlayback(grabTimestampUs, pauseRequested, logicalTimestampUs);
            return;
        }

        try {
            // The decode thread must be stopped before the grabber is repositioned, and the frames it already
            // queued belong to the old position, so they are thrown away.
            stopDecoding();
            grabber.setTimestamp(grabTimestampUs);
            resetPlaybackClock(logicalTimestampUs);
            flushAudioOutput();
            startDecoding();
            notifyPlaybackStateChanged();
        } catch (Exception e) {
            handlePlaybackError(e);
        }
    }

    private void startPlayback(long startTimestampUs, boolean initiallyPaused, long initialRelativeSeekBaseUs) {
        closePlaybackResources();

        pauseRequested = initiallyPaused;
        currentTimestampUs = initialRelativeSeekBaseUs != NO_SEEK_REQUEST
                ? initialRelativeSeekBaseUs
                : startTimestampUs;
        relativeSeekBaseUs = initialRelativeSeekBaseUs;

        notifyStatusChanged(videoFile.getName());

        try {
            openPlaybackResources(startTimestampUs);
            resetPlaybackClock(currentTimestampUs);
            playbackOpen = true;
            playbackTimer.start();
            startDecoding();
            notifyPlaybackStateChanged();
        } catch (Exception e) {
            handlePlaybackError(e);
        }
    }

    private void openPlaybackResources(long startTimestampUs) throws Exception {
        converter = new JavaFXFrameConverter();
        grabber = new FFmpegFrameGrabber(videoFile);
        grabber.setImageMode(FrameGrabber.ImageMode.COLOR);
        grabber.setSampleMode(FrameGrabber.SampleMode.SHORT);
        grabber.start();

        notifyVideoSizeChanged(grabber.getImageWidth(), grabber.getImageHeight());

        frameRate = grabber.getFrameRate();
        intFrameRate = (int) Math.round(frameRate);
        videoFrameDurationUs = frameRate > 0
                ? (int) Math.round(1_000_000.0 / frameRate)
                : 0;
        totalVideoFrames = grabber.getLengthInVideoFrames();

        long durationUs = grabber.getLengthInTime();
        videoDurationUs = durationUs > 0
                ? durationUs
                : NO_SEEK_REQUEST;

        audioAvailable = grabber.hasAudio();
        audioPlayer = new AudioPlayer();
        if (audioAvailable) {
            audioPlayer.open(grabber.getSampleRate(), grabber.getAudioChannels());
        }

        pipeline = new FrameProcessingPipeline(frameProcessors, EFFECT_WORKER_COUNT, MAX_FRAMES_IN_FLIGHT);

        if (startTimestampUs > 0) {
            grabber.setTimestamp(startTimestampUs);
        }
    }

    // Hands the grabber to a new decode thread. From here until stopDecoding returns, the JavaFX thread must not
    // touch the grabber.
    private void startDecoding() {
        decoder = new FrameDecoder(
                grabber,
                pipeline,
                audioPlayer,
                audioAvailable,
                this::isAudioOutputEnabled,
                pendingAudio,
                this::describeVideo,
                this::onDecodeFailed);
        decoder.start();
    }

    // Takes the grabber back from the decode thread and drops every frame it had queued. Only after this returns
    // may the JavaFX thread reposition or close the grabber.
    private void stopDecoding() {
        if (decoder != null) {
            decoder.stopAndWait();
            decoder = null;
        }
        if (pipeline != null) {
            pipeline.discardPending();
        }
    }

    // Runs on the decode thread, once, when the first video frame arrives: that is the earliest the effects can learn
    // the real image size and stride. It only reads fields the JavaFX thread set before starting that thread.
    private InfoVideo describeVideo(Frame frame) {
        return new InfoVideo(
                mediaName(videoFile),
                totalVideoFrames,
                frame.imageWidth,
                frame.imageHeight,
                frame.imageDepth,
                frame.imageChannels,
                frame.imageStride,
                frameRate,
                intFrameRate,
                videoFrameDurationUs,
                grabber.getPixelFormat());
    }

    // Called on the decode thread. It must not block or wait for the JavaFX thread, so it only queues the real
    // handling there.
    private void onDecodeFailed(Exception e) {
        Platform.runLater(() -> handlePlaybackError(e));
    }

    private void resetPlaybackClock(long logicalTimestampUs) {
        pendingAudio.clear();
        preparedFrame = null;
        awaitingFrameAfterSeek = true;
        currentTimestampUs = logicalTimestampUs;
        relativeSeekBaseUs = logicalTimestampUs;
        firstTimestampUs = NO_SEEK_REQUEST;
        logicalPlaybackBaseUs = logicalTimestampUs;
        playbackStartNs = 0;
        pauseStartedNs = pauseRequested ? System.nanoTime() : 0;
    }

    private void resumePlaybackClock(long now) {
        if (pauseStartedNs > 0 && playbackStartNs > 0) {
            playbackStartNs += now - pauseStartedNs;
        }
        pauseStartedNs = 0;
    }

    // Takes the next finished frame from the pipeline, if there is one, and works out when it is due on screen. Never
    // blocks: if the next frame is still being processed, it simply tries again on the next heart beat.
    private void fetchNextFrame() {
        try {
            FrameProcessingPipeline.ProcessedFrame ready = pipeline.pollReady();
            if (ready == null) {
                if (pipeline.isFinished()) {
                    finishPlayback();
                }
                return;
            }
            preparedFrame = prepareForDisplay(ready);
        } catch (Exception e) {
            handlePlaybackError(e);
        }
    }

    private PreparedFrame prepareForDisplay(FrameProcessingPipeline.ProcessedFrame ready) {
        InfoFrame info = ready.info();
        long timestampUs = info.timestampUs();

        // The clock starts when the first frame is ready to show, not when it was decoded, so the time the effects
        // took on that first frame is not counted as lateness.
        if (firstTimestampUs == NO_SEEK_REQUEST) {
            firstTimestampUs = timestampUs;
            playbackStartNs = System.nanoTime();
            if (pauseRequested) {
                pauseStartedNs = playbackStartNs;
            }
        }

        Image image = converter.convert(ready.frame());
        long relativeTimestampUs = Math.max(0, timestampUs - firstTimestampUs);
        long logicalTimestampUs = logicalPlaybackBaseUs + relativeTimestampUs;
        long targetTimeNs = playbackStartNs + relativeTimestampUs * 1_000L;

        return new PreparedFrame(
                image,
                info.frameNumber(),
                timestampUs,
                logicalTimestampUs,
                targetTimeNs,
                System.nanoTime());
    }

    // This is called on a heart beat by the GUI thread. We want to be co-operative here by (a) not blocking, and (b)
    // getting our required work out of the way quickly so that we can return control flow to the JavaFX event loop for
    // handling user interaction with the GUI and rendering. We have three jobs: (1) keep audio flowing if sound is on,
    // (2) display a frame if it is due and (3) pick up the next finished frame once the previous one has gone to the
    // display. The expensive work (decoding and effects) is done on other threads, so a slow effect delays the
    // frames queued behind it but never freezes this thread or the GUI.
    private void pumpPlayback(long now) {
        if (!playbackOpen) {
            return;
        }

        // Task (1)
        writeDueAudio(now);

        // Task (2)
        if (preparedFrame != null && preparedFrame.targetTimeNs() <= now) {
            // We have a prepared frame ready to go and it is due (or just past due!) so get it up on screen ASAP!
            displayPreparedFrame(now);
        }

        // Task (3). While paused we stop picking up frames, except the one frame owed after a seek.
        if (preparedFrame == null && (!pauseRequested || awaitingFrameAfterSeek)) {
            fetchNextFrame();
        }
    }

    private void displayPreparedFrame(long now) {
        PreparedFrame frame = preparedFrame;
        preparedFrame = null;
        awaitingFrameAfterSeek = false;
        currentTimestampUs = frame.logicalTimestampUs();
        relativeSeekBaseUs = NO_SEEK_REQUEST;

        // Dump some logging to the console once per second so that real time performance can be monitored.
        if (intFrameRate > 0 && frame.frameNumber() % intFrameRate == 0) {
            long remainingUs = (frame.targetTimeNs() - frame.preparedAtNs()) / 1_000L;
            if (remainingUs < 0) {
                System.out.printf("Frame headroom is \u001B[31m%5dus\u001B[0m out of %dus spare.%n",
                        remainingUs,
                        videoFrameDurationUs);
            } else {
                System.out.printf("Frame headroom is \u001B[32m%5dus\u001B[0m out of %dus spare.%n",
                        remainingUs,
                        videoFrameDurationUs);
            }
        }

        notifyFrameReady(frame.image());
    }

    private void writeDueAudio(long now) {
        // Handle some obvious early exits
        if (!audioOutputEnabled || audioPlayer == null) {
            pendingAudio.clear();
            return;
        }
        if (pauseRequested || firstTimestampUs == NO_SEEK_REQUEST || playbackStartNs <= 0) {
            return;
        }

        // Here is the real time dependent logic
        long dueTimestampUs = firstTimestampUs + (now + AUDIO_LEAD_NS - playbackStartNs) / 1_000L;
        while (!pendingAudio.isEmpty()) {
            PendingAudio audio = pendingAudio.peek();
            if (audio.timestampUs() > dueTimestampUs) {
                return;
            }

            int written = audioPlayer.write(audio.samples(), audio.offset(), audio.remaining());
            if (written <= 0) {
                return;
            }

            audio.advance(written);
            if (!audio.finished()) {
                return;
            }
            pendingAudio.remove();
        }
    }

    private void finishPlayback() {
        long endTimestampUs = videoDurationUs != NO_SEEK_REQUEST
                ? videoDurationUs
                : currentTimestampUs;

        closePlaybackResources();
        pauseRequested = true;
        currentTimestampUs = endTimestampUs;
        relativeSeekBaseUs = endTimestampUs;
        notifyStatusChanged("Playback finished");
        notifyPlaybackStateChanged();
    }

    private void handlePlaybackError(Exception e) {
        e.printStackTrace();
        closePlaybackResources();
        pauseRequested = false;
        notifyStatusChanged("Error: " + e.getMessage());
        notifyPlaybackStateChanged();
    }

    private void closePlaybackResources() {
        playbackTimer.stop();
        playbackOpen = false;

        // Order matters. The decode thread is using the grabber and audio player closed below, and the workers are
        // using the effects, so they are stopped first.
        if (decoder != null) {
            decoder.stopAndWait();
            decoder = null;
        }
        if (pipeline != null) {
            pipeline.close();
            pipeline = null;
        }

        preparedFrame = null;
        pendingAudio.clear();
        firstTimestampUs = NO_SEEK_REQUEST;
        playbackStartNs = 0;
        pauseStartedNs = 0;
        audioAvailable = false;

        if (audioPlayer != null) {
            audioPlayer.close();
            audioPlayer = null;
        }

        if (grabber != null) {
            try {
                grabber.stop();
            } catch (Exception e) {
                // The resource is being closed; there is no useful recovery action.
            }
            try {
                grabber.close();
            } catch (Exception e) {
                // The resource is being closed; there is no useful recovery action.
            }
            grabber = null;
        }

        if (converter != null) {
            converter.close();
            converter = null;
        }
    }

    private String mediaName(File file) {
        String fileName = file.getName();
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart <= 0) {
            return fileName;
        }
        return fileName.substring(0, extensionStart);
    }

    private long clampSeekTimestamp(long timestampUs) {
        long clampedTimestampUs = Math.max(0, timestampUs);
        long durationUs = videoDurationUs;
        if (durationUs != NO_SEEK_REQUEST) {
            clampedTimestampUs = Math.min(clampedTimestampUs, durationUs);
        }
        return clampedTimestampUs;
    }

    private long displayableSeekTimestamp(long logicalTimestampUs) {
        long durationUs = videoDurationUs;
        if (durationUs == NO_SEEK_REQUEST || logicalTimestampUs < durationUs) {
            return logicalTimestampUs;
        }

        int frameDurationUs = videoFrameDurationUs;
        if (frameDurationUs <= 0) {
            return Math.max(0, durationUs - 1);
        }

        return Math.max(0, durationUs - frameDurationUs);
    }

    private void flushAudioOutput() {
        if (audioPlayer != null) {
            audioPlayer.flush();
        }
    }

    private void notifyVideoSizeChanged(int width, int height) {
        videoSizeChangedHandler.accept(width, height);
    }

    private void notifyFrameReady(Image image) {
        frameReadyHandler.accept(image);
    }

    private void notifyStatusChanged(String status) {
        statusChangedHandler.accept(status);
    }

    private void notifyPlaybackStateChanged() {
        playbackStateChangedHandler.accept(playbackOpen, pauseRequested);
    }

    private void notifyAudioOutputStateChanged() {
        audioOutputStateChangedHandler.accept(audioOutputEnabled);
    }

    private record PreparedFrame(
            Image image,
            int frameNumber,
            long mediaTimestampUs,
            long logicalTimestampUs,
            long targetTimeNs,
            long preparedAtNs) {
    }
}