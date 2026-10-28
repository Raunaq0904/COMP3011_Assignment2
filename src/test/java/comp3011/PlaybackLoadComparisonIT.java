/*
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Test;

/**
 * Load test for future developers: compares the original way of working (every effect on every frame, one frame at a
 * time, on a single thread) with the multi-threaded {@link FrameProcessingPipeline}, on identical frames.
 *
 * <p>
 * The measure that matters is the one the player is judged on: how many effects can be stacked before playback falls
 * behind real time. For several effect counts it measures frames per second both ways and compares them with the
 * video's real-time rate. The video is synthetic (random pixels at the size of the sample video) so the test needs no
 * media file; decoding is not included, so this measures the effect processing, which is the part that grows with each
 * added effect. A report is written to target/load-test-report.txt.
 * </p>
 *
 * <p>
 * Named *IT so that Maven's Failsafe plugin runs it during {@code mvn verify} (not during {@code mvn test}).
 * The assertions are deliberately loose so that a slow or small build machine does not fail the build: the exact
 * numbers are in the report, the assertions only catch a real regression.
 * </p>
 */
class PlaybackLoadComparisonIT {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 800;
    private static final int WARM_UP_FRAMES = 6;
    private static final int MEASURED_FRAMES = 48;
    private static final double REALTIME_FPS = 24_000.0 / 1_001.0; // The sample video's frame rate.
    private static final long LIMIT_SECONDS = 180;

    // All eleven effects, cheapest-looking first, so each prefix is a heavier chain than the one before. Beyond
    // eleven the sequence repeats (the command line allows that), which pushes the load past what one thread sustains.
    private static final String EFFECT_ORDER = "wyvdjmbpnsf";
    private static final int[] EFFECT_COUNTS = { 2, 4, 8, 11, 16, 22, 33 };

    // The same sizing rule the player uses: leave cores for the JavaFX and decode threads.
    private static final int WORKERS = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
    private static final int MAX_FRAMES_IN_FLIGHT = 12;

    @Test
    void pipelineSustainsMoreEffectsInRealTimeThanSingleThreadedProcessing() throws Exception {
        Frame source = syntheticFrame();
        InfoVideo video = videoInfoFor(source);
        int cores = Runtime.getRuntime().availableProcessors();

        StringBuilder report = new StringBuilder();
        report.append(String.format("Load test: %dx%d frames, %d measured frames per run, %d worker threads, %d cores%n",
                WIDTH, HEIGHT, MEASURED_FRAMES, WORKERS, cores));
        report.append(String.format("%-8s %-22s %-18s %-8s%n", "effects", "single-threaded fps", "pipeline fps", "speedup"));

        int singleThreadedLimit = 0;
        int pipelineLimit = 0;
        double lastSingleThreadedFps = 0;
        double lastPipelineFps = 0;

        for (int count : EFFECT_COUNTS) {
            String flags = flagsFor(count);
            double singleThreadedFps = singleThreadedFps(flags, source, video);
            double pipelineFps = pipelineFps(flags, source, video);

            report.append(String.format("%-8d %-22.1f %-18.1f %-8.2f%n",
                    count, singleThreadedFps, pipelineFps, pipelineFps / singleThreadedFps));

            if (singleThreadedFps >= REALTIME_FPS) {
                singleThreadedLimit = count;
            }
            if (pipelineFps >= REALTIME_FPS) {
                pipelineLimit = count;
            }
            lastSingleThreadedFps = singleThreadedFps;
            lastPipelineFps = pipelineFps;
        }

        report.append(String.format("Real-time rate: %.2f fps%n", REALTIME_FPS));
        report.append(String.format("Most effects sustained in real time: single-threaded %d, pipeline %d%n",
                singleThreadedLimit, pipelineLimit));

        System.out.println(report);
        Path reportFile = Path.of("target", "load-test-report.txt");
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, report.toString());

        // On a many-core machine the pipeline must sustain strictly more effects; on a small one it must at least
        // not sustain fewer, so a weak build machine cannot fail the build.
        boolean manyCores = cores >= 8;
        assertTrue(manyCores ? pipelineLimit > singleThreadedLimit : pipelineLimit >= singleThreadedLimit,
                "the pipeline must sustain more effects in real time than single-threaded processing");

        double speedup = lastPipelineFps / lastSingleThreadedFps;
        double required = cores >= 4 ? 1.2 : 0.8;
        assertTrue(speedup >= required,
                String.format("with all effects the pipeline was %.2fx single-threaded speed, needed %.2fx",
                        speedup, required));
    }

    // The original design: one thread applies every effect to every frame in turn. The same frame is reused, like the
    // decoder's single frame buffer, and cloning is not charged to this side because the original did not clone.
    private static double singleThreadedFps(String flags, Frame source, InfoVideo video) throws Exception {
        List<FrameProcessor> chain = chainFor(flags);
        for (FrameProcessor effect : chain) {
            effect.initialise(video);
        }

        Frame work = source.clone();
        long start = 0;
        for (int i = 0; i < WARM_UP_FRAMES + MEASURED_FRAMES; i++) {
            if (i == WARM_UP_FRAMES) {
                start = System.nanoTime();
            }
            InfoFrame info = new InfoFrame(i, i * 41_708L);
            for (FrameProcessor effect : chain) {
                effect.process(work, info);
            }
        }
        return framesPerSecond(MEASURED_FRAMES, System.nanoTime() - start);
    }

    // The new design, driven the way the player drives it: a producer thread submits, this thread collects in order.
    private static double pipelineFps(String flags, Frame source, InfoVideo video) throws Exception {
        int totalFrames = WARM_UP_FRAMES + MEASURED_FRAMES;
        AtomicReference<Throwable> producerFailure = new AtomicReference<>();
        long deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIMIT_SECONDS);

        try (FrameProcessingPipeline pipeline =
                new FrameProcessingPipeline(chainFor(flags), WORKERS, MAX_FRAMES_IN_FLIGHT)) {
            Thread producer = new Thread(() -> {
                try {
                    pipeline.initialiseEffectsOnce(video);
                    for (int i = 0; i < totalFrames; i++) {
                        pipeline.submit(source, new InfoFrame(i, i * 41_708L));
                    }
                    pipeline.finishInput();
                } catch (Throwable t) {
                    producerFailure.set(t);
                }
            }, "load-test-producer");
            producer.setDaemon(true);
            producer.start();

            int collected = 0;
            long start = 0;
            while (!pipeline.isFinished()) {
                if (producerFailure.get() != null) {
                    throw new AssertionError("producer failed", producerFailure.get());
                }
                assertTrue(System.nanoTime() < deadlineNs, "load test timed out");

                if (pipeline.pollReady() == null) {
                    LockSupport.parkNanos(200_000);
                    continue;
                }
                collected++;
                if (collected == WARM_UP_FRAMES) {
                    start = System.nanoTime();
                }
            }
            long elapsedNs = System.nanoTime() - start;
            return framesPerSecond(totalFrames - WARM_UP_FRAMES, elapsedNs);
        }
    }

    // Each effect once up to eleven; after that the same order repeats.
    private static String flagsFor(int count) {
        int copies = (count + EFFECT_ORDER.length() - 1) / EFFECT_ORDER.length();
        return EFFECT_ORDER.repeat(copies).substring(0, count);
    }

    private static List<FrameProcessor> chainFor(String flags) {
        List<FrameProcessor> chain = new ArrayList<>();
        for (char flag : flags.toCharArray()) {
            chain.add(FrameProcessorRegistry.findByShortFlag(flag).orElseThrow().create());
        }
        return chain;
    }

    private static double framesPerSecond(int frames, long elapsedNs) {
        return frames * 1_000_000_000.0 / elapsedNs;
    }

    private static Frame syntheticFrame() {
        Frame frame = new Frame(WIDTH, HEIGHT, Frame.DEPTH_UBYTE, 3);
        byte[] noise = new byte[frame.imageStride * HEIGHT];
        new Random(42).nextBytes(noise);
        ((ByteBuffer) frame.image[0]).put(0, noise);
        return frame;
    }

    private static InfoVideo videoInfoFor(Frame frame) {
        return new InfoVideo("load-test", MEASURED_FRAMES + WARM_UP_FRAMES, WIDTH, HEIGHT, frame.imageDepth,
                frame.imageChannels, frame.imageStride, REALTIME_FPS, 24, 41_708, 0);
    }
}