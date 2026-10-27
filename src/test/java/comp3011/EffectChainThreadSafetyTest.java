/*
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.CRC32;

import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Test;

/**
 * Evidence for the effect thread-sharing rules in the multi-threaded design: worker threads share ONE instance of
 * each effect and apply it to different frames at the same time, so the result for a frame must not depend on what
 * other threads are doing.
 */
class EffectChainThreadSafetyTest {
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int FRAME_COUNT = 48;
    private static final int THREAD_COUNT = 8;
    private static final String ALL_EFFECT_FLAGS = "nsfwyvdjmbp";

    @Test
    void clonedFrameIsIndependentOfOriginal() {
        Frame original = syntheticFrame(1);
        long before = checksum(original);

        Frame copy = original.clone();
        assertEquals(before, checksum(copy), "clone must contain the same pixels");

        ByteBuffer copyPixels = (ByteBuffer) copy.image[0];
        copyPixels.put(0, (byte) (copyPixels.get(0) + 1));

        assertEquals(before, checksum(original), "changing the clone must not change the original");
    }

    @Test
    void allEffectsGiveSameResultSequentiallyAndConcurrently() throws Exception {
        Frame first = syntheticFrame(0);
        InfoVideo info = new InfoVideo("test-clip", FRAME_COUNT, WIDTH, HEIGHT, first.imageDepth,
                first.imageChannels, first.imageStride, 24.0, 24, 41_708, 0);
        List<FrameProcessor> chain = initialisedChain(info);

        Frame[] sources = new Frame[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            sources[i] = syntheticFrame(i);
        }

        long[] sequential = new long[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            sequential[i] = runChainOnCopy(chain, sources[i], i);
        }

        long[] concurrent = new long[FRAME_COUNT];
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < FRAME_COUNT; i++) {
                final int frameNumber = i;
                futures.add(pool.submit(() -> runChainOnCopy(chain, sources[frameNumber], frameNumber)));
            }
            for (int i = 0; i < FRAME_COUNT; i++) {
                concurrent[i] = futures.get(i).get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertArrayEquals(sequential, concurrent, "concurrent processing changed some frames");
    }

    // One instance of each effect, in the same order the command line would give: this is exactly what the worker
    // pool will share.
    private static List<FrameProcessor> initialisedChain(InfoVideo info) throws Exception {
        List<FrameProcessor> chain = new ArrayList<>();
        for (char flag : ALL_EFFECT_FLAGS.toCharArray()) {
            chain.add(FrameProcessorRegistry.findByShortFlag(flag).orElseThrow().create());
        }
        for (FrameProcessor processor : chain) {
            processor.initialise(info);
        }
        return chain;
    }

    private static long runChainOnCopy(List<FrameProcessor> chain, Frame source, int frameNumber) throws Exception {
        Frame work = source.clone();
        InfoFrame info = new InfoFrame(frameNumber, frameNumber * 41_708L);
        for (FrameProcessor processor : chain) {
            processor.process(work, info);
        }
        return checksum(work);
    }

    private static Frame syntheticFrame(int seed) {
        Frame frame = new Frame(WIDTH, HEIGHT, Frame.DEPTH_UBYTE, 3);
        byte[] noise = new byte[frame.imageStride * HEIGHT];
        new Random(seed).nextBytes(noise);
        ((ByteBuffer) frame.image[0]).put(0, noise);
        return frame;
    }

    private static long checksum(Frame frame) {
        ByteBuffer pixels = ((ByteBuffer) frame.image[0]).duplicate();
        pixels.clear();
        byte[] bytes = new byte[pixels.remaining()];
        pixels.get(bytes);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }
}