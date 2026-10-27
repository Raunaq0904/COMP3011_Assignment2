/*
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bytedeco.javacv.Frame;

/**
 * Applies the effect chain to decoded video frames on a pool of worker threads and hands the results back in the
 * order the frames were submitted.
 *
 * <p>
 * Why this exists: the original player decoded a frame and ran every effect on it, one frame at a time, on the GUI
 * thread, so each added effect ate into the 1/frame-rate time budget. Here each worker runs the whole ordered chain
 * on one frame, so several frames are processed at once and the cost of the chain is divided across the CPU cores.
 * </p>
 *
 * <p>
 * Thread roles and the rules that keep it correct:
 * </p>
 * <ul>
 * <li><b>One producer</b> (the decode thread) calls {@link #submit} and then {@link #finishInput}.</li>
 * <li><b>One consumer</b> (the JavaFX thread) calls {@link #pollReady}. That method looks at the head of the queue
 * and then removes it, which is only safe with a single consumer.</li>
 * <li><b>Frame ownership:</b> the decoder reuses its frame buffer on every grab, so {@link #submit} takes a deep
 * copy straight away. After submit returns the caller may overwrite its frame; a worker owns the copy until the
 * consumer receives it.</li>
 * <li><b>Order:</b> results are queued in submission order and only the head is ever examined, so a fast frame
 * never overtakes a slow one even though workers finish out of order.</li>
 * <li><b>The consumer never blocks:</b> {@link #pollReady} returns null if the head frame is not finished yet.</li>
 * <li><b>Bounded memory:</b> a semaphore limits frames in flight. {@link #submit} waits for a free slot, which
 * stops the decoder racing ahead of the display.</li>
 * <li><b>Shared effects:</b> all workers share one instance of each effect, so every effect's process method must
 * be safe to call concurrently (see EffectChainThreadSafetyTest). The effects are initialised once, by the decode
 * thread, through {@link #initialiseEffectsOnce} before the first frame is submitted.</li>
 * </ul>
 */
public final class FrameProcessingPipeline implements AutoCloseable {

    /** A frame with all effects applied, and the metadata it was submitted with. */
    public record ProcessedFrame(Frame frame, InfoFrame info) {
    }

    private final List<FrameProcessor> effectChain;
    private final ExecutorService workers;
    private final Semaphore freeSlots;
    private final Queue<Future<ProcessedFrame>> inFlight = new ConcurrentLinkedQueue<>();
    private volatile boolean inputFinished;
    private boolean effectsInitialised; // Only touched by decode threads, one at a time (see initialiseEffectsOnce).

    /**
     * @param effectChain       effects in the order they must be applied; initialised later, on the first frame
     * @param workerCount       number of threads processing frames at the same time
     * @param maxFramesInFlight most frames that may be submitted but not yet collected
     */
    public FrameProcessingPipeline(List<FrameProcessor> effectChain, int workerCount, int maxFramesInFlight) {
        this.effectChain = List.copyOf(effectChain);
        this.workers = Executors.newFixedThreadPool(workerCount, new WorkerThreadFactory());
        this.freeSlots = new Semaphore(maxFramesInFlight);
    }

    /**
     * Copies the frame and queues the copy for processing. Blocks while the pipeline is full.
     *
     * @throws InterruptedException if the producer is told to stop while waiting for a free slot
     */
    public void submit(Frame decoded, InfoFrame info) throws InterruptedException {
        freeSlots.acquire();
        Frame ownedCopy = decoded.clone();
        inFlight.add(workers.submit(() -> applyEffects(ownedCopy, info)));
    }

    /** Tells the pipeline that no more frames will be submitted. */
    public void finishInput() {
        inputFinished = true;
    }
    
    /**
     * Initialises every effect with the video's properties, the first time it is called. Later calls do nothing, so a
     * seek (which starts a new decoder) never re-initialises effects that workers may still be using. Called only
     * from the decode thread; a stopped decoder is always joined before the next one starts, so two calls never
     * overlap and the flag needs no lock.
     */
    public void initialiseEffectsOnce(InfoVideo videoInfo) throws Exception {
        if (effectsInitialised) {
            return;
        }
        for (FrameProcessor effect : effectChain) {
            effect.initialise(videoInfo);
        }
        effectsInitialised = true;
    }

    /**
     * Throws away every frame that has been submitted but not collected, so playback can continue from a new position
     * after a seek. Call it only while the producer is stopped and from the consumer thread.
     */
    public void discardPending() {
        Future<ProcessedFrame> pending;
        while ((pending = inFlight.poll()) != null) {
            pending.cancel(true);
            freeSlots.release();
        }
        inputFinished = false;
    }

    /**
     * Returns the oldest submitted frame if its effects have finished, otherwise null. Never blocks.
     *
     * @throws Exception if an effect failed on that frame
     */
    public ProcessedFrame pollReady() throws Exception {
        Future<ProcessedFrame> oldest = inFlight.peek();
        if (oldest == null || !oldest.isDone()) {
            return null;
        }
        inFlight.poll();
        freeSlots.release();
        return oldest.get();
    }

    /** True once input has finished and every submitted frame has been collected. */
    public boolean isFinished() {
        return inputFinished && inFlight.isEmpty();
    }

    /**
     * Abandons all queued work and waits briefly for workers to finish the frame they are on. The wait matters: the
     * next playback re-initialises the shared effects, which must not happen while a stale worker is still using
     * them.
     */
    @Override
    public void close() {
        workers.shutdownNow();
        inFlight.clear();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ProcessedFrame applyEffects(Frame ownedCopy, InfoFrame info) throws Exception {
        for (FrameProcessor effect : effectChain) {
            effect.process(ownedCopy, info);
        }
        return new ProcessedFrame(ownedCopy, info);
    }

    // Daemon threads so that a forgotten pipeline can never keep the JVM alive, and readable names for debugging.
    private static final class WorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "frame-worker-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}