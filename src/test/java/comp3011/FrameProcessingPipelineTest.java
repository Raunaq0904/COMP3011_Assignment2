/*
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Test;

/**
 * Checks the two promises the player depends on: frames come out in the order they went in even though workers finish
 * out of order, and a frame is safe from the decoder reusing its buffer once it has been submitted.
 */
class FrameProcessingPipelineTest {
    private static final int FRAME_COUNT = 40;

    @Test
    void framesComeOutInSubmissionOrderAndAreIsolatedFromTheDecoderBuffer() throws Exception {
        // Writes the frame number into the first pixel after a random delay, so that workers finish out of order.
        FrameProcessor stampAfterRandomDelay = new FrameProcessor() {
            @Override
            public void process(Frame frame, InfoFrame info) throws Exception {
                Thread.sleep(ThreadLocalRandom.current().nextInt(0, 8));
                ((ByteBuffer) frame.image[0]).put(0, (byte) info.frameNumber());
            }
        };

        try (FrameProcessingPipeline pipeline = new FrameProcessingPipeline(List.of(stampAfterRandomDelay), 4, 8)) {
            // The producer runs on its own thread, like the real decode thread: submit blocks when the pipeline
            // is full, so producing and consuming on the same thread would deadlock.
            Frame reusedDecoderFrame = new Frame(16, 16, Frame.DEPTH_UBYTE, 3);
            Thread producer = new Thread(() -> {
                try {
                    for (int i = 0; i < FRAME_COUNT; i++) {
                        pipeline.submit(reusedDecoderFrame, new InfoFrame(i, i * 41_708L));
                        // Simulates the grabber overwriting its single frame buffer on the next grab.
                        ((ByteBuffer) reusedDecoderFrame.image[0]).put(0, (byte) 99);
                    }
                    pipeline.finishInput();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "test-producer");
            producer.start();

            List<Integer> received = new ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!pipeline.isFinished()) {
                FrameProcessingPipeline.ProcessedFrame ready = pipeline.pollReady();
                if (ready == null) {
                    assertTrue(System.nanoTime() < deadline, "timed out waiting for frames");
                    Thread.sleep(1);
                    continue;
                }
                received.add(ready.info().frameNumber());
                assertEquals((byte) ready.info().frameNumber(), ((ByteBuffer) ready.frame().image[0]).get(0),
                        "frame content was disturbed after submit");
            }
            producer.join();

            List<Integer> expected = new ArrayList<>();
            for (int i = 0; i < FRAME_COUNT; i++) {
                expected.add(i);
            }
            assertEquals(expected, received);
        }
    }
}