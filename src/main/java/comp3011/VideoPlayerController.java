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

// Assignment 3: the subtitle changes in this file were written with the assistance of Claude (see the "AI acknowledgement" section of README.md).

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.util.ArrayList;

/**
 * The controller part of the video player model-view-controller architecture.
 * 
 * <p>
 * Wires up events from the {@link VideoPlayerView} to the {@link VideoPlayerModel}.
 * This includes button actions, keyboard shortcuts and display resizing.
 * </p>
 */
public class VideoPlayerController {
    private final VideoPlayerModel model;
    private final VideoPlayerView view;
    private final BiConsumer<Integer, Integer> videoSizeChangedHandler;
    private final List<FrameSubtitler> subtitlers = new ArrayList<>();
    private SubtitleFeeder subtitleFeeder; // Created in play(). Used on the JavaFX thread only.

    public VideoPlayerController(
            boolean audioEnabled,
            List<FrameProcessor> frameProcessors,
            BiConsumer<Integer, Integer> videoSizeChangedHandler) {
        this.view = new VideoPlayerView();
        this.videoSizeChangedHandler = videoSizeChangedHandler;

        // Subtitles can be switched on during playback even when -S was not given, so make sure the chain always has
        // a subtitler: one that starts switched off when the user did not ask for subtitles.
        List<FrameProcessor> processors = new ArrayList<>(frameProcessors);
        for (FrameProcessor processor : processors) {
            if (processor instanceof FrameSubtitler subtitler) {
                subtitlers.add(subtitler);
            }
        }
        if (subtitlers.isEmpty()) {
            FrameSubtitler idleSubtitler = new FrameSubtitler();
            idleSubtitler.setEnabled(false);
            processors.add(idleSubtitler);
            subtitlers.add(idleSubtitler);
        }

        model = new VideoPlayerModel(
                audioEnabled,
                processors,
                this::onVideoSizeChanged,
                this::onFrameReady,
                this::onStatusChanged,
                this::onPlaybackStateChanged,
                this::onAudioOutputStateChanged);

        view.getStartOverButton().setOnAction(_ -> startOver());
        view.getBackFiveButton().setOnAction(_ -> backFiveSeconds());
        view.getPausePlayButton().setOnAction(_ -> togglePause());
        view.getForwardFiveButton().setOnAction(_ -> forwardFiveSeconds());
        view.getStopButton().setOnAction(_ -> stopAndExit());
        view.getAudioButton().setOnAction(_ -> toggleAudioOutput());
        view.showAudioEnabled(model.isAudioOutputEnabled());
        view.getSubtitleButton().setOnAction(_ -> toggleSubtitles());
        view.showSubtitlesEnabled(subtitlers.get(0).isEnabled());
    }

    public void installKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stopAndExit();
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE) {
                togglePause();
                event.consume();
            } else if (event.getCode() == KeyCode.LEFT) {
                backFiveSeconds();
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                forwardFiveSeconds();
                event.consume();
            } else if (event.getCode() == KeyCode.HOME) {
                startOver();
                event.consume();
            } else if (event.getCode() == KeyCode.A) {
                toggleAudioOutput();
                event.consume();
            } else if (event.getCode() == KeyCode.S) {
                toggleSubtitles();
                event.consume();
            }
        });
    }

    public VideoPlayerView getView() {
        return view;
    }

    public void play(File file) {
        view.setPlaybackControlsDisabled(false);
        startSubtitleFeeder(file);
        model.play(file);
    }

    public void shutdown() {
        if (subtitleFeeder != null) {
            subtitleFeeder.shutdown();
        }
        model.shutdown();
    }

    // The feeder reads the media's audio with a grabber of its own and talks to the subtitle server, both on its own
    // thread. It does nothing until subtitles are switched on, so a run without -S never touches the server.
    private void startSubtitleFeeder(File media) {
        SubtitleServerClient client = new SubtitleServerClient();
        subtitleFeeder = new SubtitleFeeder(() -> new AudioChunkExtractor(media), client::transcribe);
        for (FrameSubtitler subtitler : subtitlers) {
            subtitler.attach(subtitleFeeder::track, subtitleFeeder::noteFramePosition);
        }
        subtitleFeeder.setEnabled(subtitlers.get(0).isEnabled());
    }

    private void startOver() {
        model.startOver();
    }

    private void backFiveSeconds() {
        model.backFiveSeconds();
    }

    private void togglePause() {
        model.togglePause();
    }

    private void forwardFiveSeconds() {
        model.forwardFiveSeconds();
    }

    private void toggleAudioOutput() {
        model.toggleAudioOutput();
    }

    private void stopAndExit() {
        model.stopPlayback();
        javafx.application.Platform.exit();
    }
    
    private void toggleSubtitles() {
        boolean enable = !subtitlers.get(0).isEnabled();
        for (FrameSubtitler subtitler : subtitlers) {
            subtitler.setEnabled(enable);
        }
        if (subtitleFeeder != null) {
            subtitleFeeder.setEnabled(enable);
        }
        view.showSubtitlesEnabled(enable);
    }

    private void onVideoSizeChanged(int width, int height) {
        videoSizeChangedHandler.accept(width, height);
    }

    private void onFrameReady(Image image) {
        view.setImage(image);
    }

    private void onStatusChanged(String status) {
        view.setStatus(status);
    }

    private void onPlaybackStateChanged(boolean playing, boolean paused) {
        if (playing && !paused) {
            view.showPauseAction();
        } else {
            view.showPlayAction();
        }
    }

    private void onAudioOutputStateChanged(boolean audioOutputEnabled) {
        view.showAudioEnabled(audioOutputEnabled);
    }
}