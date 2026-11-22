# VideoPlayerMultiThreaded

COMP3011 Assignments 2 and 3. Assignment 2 is a JavaFX video player with film-style frame effects, re-engineered from a
single-threaded cooperative design into a multi-threaded one. Assignment 3 adds speech-to-text subtitles (`-S`) through
a small Spring Boot server that calls OpenAI's transcription API. Assignment 3 is described in the section
"Assignment 3: speech-to-text subtitles" below.

Author: Raunaqdeep Singh Khosla, a1954269. The starter code is by Simon Ratcliffe (see the headers of the starter
files). Every file I modified or added carries my name in its header.

## Building, running and testing

All commands run from the project root.

```
mvn validate
mvn compile
mvn package
mvn verify
mvn javafx:run -Djavafx.args="-nssnfwyvdjmbp C:\path\to\video.mp4"
```

- `mvn verify` runs the unit tests (Surefire) and then the load test (Failsafe, `PlaybackLoadComparisonIT`). The load
  test takes about 20 seconds on an idle machine and writes a report to `target/load-test-report.txt`.
- Run `-h` for the options: `-a` audio, `-x` maximise, `-1`/`-2` monitor, and the eleven effect options. Effect options
  can be long or short, stacked (`-jfddn`), repeated (`-ss`) and mixed, and are applied in command-line order.

## Part 1: command line

`CommandLineController` only parses. The effects it can create are listed in one table in
`FrameProcessorRegistry`; each entry is a `FrameProcessorOption` (short flag, long flag, help text and a factory
that makes a fresh effect). The parser looks flags up in that table, so it contains no effect-specific code, and the
help text is generated from the same table, so the two cannot disagree. The resulting list of effects is handed to
`VideoPlayerApp`, then `VideoPlayerController`, then `VideoPlayerModel`, which owns them.

**Adding a new effect:** write a `FrameProcessor` subclass, add one line to the table in `FrameProcessorRegistry`
(choose a short flag not used by the player: `h a x 1 2` are reserved), and make sure its `process` method is safe for
concurrent calls on different frames (see "Effects are shared between threads" below). The registry test fails if a
flag is duplicated or clashes with a player flag.

## Part 2: the multi-threaded design

### Why the original could not scale

The original did everything on the JavaFX thread, one frame at a time: decode, every effect in order, convert, then
display, all inside one frame period (41.7 ms at 23.98 fps). Each added effect used up some of that budget, and the
GUI was frozen while it ran.

### The design

```
 decode thread ---> worker pool (N threads) ---> ordered queue of Futures ---> JavaFX thread
 owns the grabber   each worker runs the FULL    (bounded: backpressure         owns the clock,
 copies audio out   effect chain on ONE frame     and frame order)               shows frames,
                                                                                 writes audio
```

| Class | Thread | Responsibility |
|---|---|---|
| `FrameDecoder` | decode thread (`frame-decoder`) | Reads frames from the grabber, queues audio, submits video frames. The only user of the grabber while it runs. |
| `FrameProcessingPipeline` | worker threads (`frame-worker-N`) | Applies the effect chain to separate copies of frames, returns them in submission order. Has no GUI or decoder dependency, so the load test uses it directly. |
| `VideoPlayerModel` | JavaFX thread | Playback clock, which frame is on screen when, audio writing, pause/seek/stop. |
| `PendingAudio` | handed from decode to JavaFX thread | A chunk of audio and how much has been written. |

Each worker runs the whole ordered chain on one frame, so several frames are processed at once and the work divides
across the CPU cores. I chose this over one thread per effect because a per-effect pipeline is limited by its slowest
effect and by the number of effects, whereas frame-level parallelism scales with the number of frames in flight.
The number of workers is `max(2, cores - 2)`, leaving cores for the JavaFX and decode threads. Up to 12 frames
(about half a second, about 55 MB at 1920x800) can be in flight.

### Why it is correct

**Critical regions and how each is protected**

| Shared thing | Who touches it | Protection |
|---|---|---|
| The decoder's frame buffer (the grabber reuses it on every grab) | decode thread, then workers | `submit()` takes a deep copy (`Frame.clone()`) before returning, so workers only ever see their own copy. Tested in `FrameProcessingPipelineTest` (the test overwrites the source frame after each submit). |
| One instance of each effect, shared by all workers | all workers | Eight effects keep no per-frame state. `FrameJitterer`, `FrameBleeder` and `FrameMottler` reuse scratch arrays or caches between frames, so their `process` methods are `synchronized`. Tested in `EffectChainThreadSafetyTest` (below). |
| Order of frames | workers, JavaFX thread | A FIFO queue of Futures. Workers finish in any order but only the head of the queue is ever examined, so frames come out in submission order. |
| Number of frames in flight | decode thread, JavaFX thread | A semaphore. `submit()` waits for a free slot, `pollReady()` releases one. This bounds memory and stops decoding running ahead of the display. |
| The grabber and `AudioPlayer.copySamples` (which reuses one buffer) | decode thread, JavaFX thread | Ownership rule: while a `FrameDecoder` runs it is the only user. The JavaFX thread uses them again only after `stopAndWait()` has joined the decode thread. A seek or stop always does this first. |
| Pending audio | decode thread (adds), JavaFX thread (reads, clears) | `ConcurrentLinkedQueue`. Each `PendingAudio` is handed over by the queue and then used only by the JavaFX thread. |
| Audio on/off flag | JavaFX thread writes, decode thread reads | `volatile`. |
| Playback clock, frame on screen, all view calls | JavaFX thread only | Confined to one thread, so no locking. |
| One-time initialisation of the effects (needs the real image size) | decode thread | `initialiseEffectsOnce()`. Seeks start a new decoder but never re-initialise. Visibility to workers comes from thread start/join and the executor's hand-off. |
| Teardown | JavaFX thread | Order is fixed: stop and join the decoder, then close the pipeline (which waits up to 2 s for workers), then close audio and the grabber. This stops a stale worker using effects while the next playback re-initialises them. |

**Effects are shared between threads (and the evidence).** The effects are deterministic: the output for a frame
depends only on its pixels, its frame number and the effect's fixed seed, so it does not depend on scheduling.
`EffectChainThreadSafetyTest` runs all eleven effects over 48 frames sequentially and then from 8 threads at once on
the same effect instances, and requires identical pixels. Before the `synchronized` fix this test failed
(`array contents differ at index [0]`), which showed the race was real; after it, the test passes. The same test file
checks that `Frame.clone()` is an independent deep copy.

**No deadlock.** The only locks are the per-effect monitors of those three effects. A worker holds at most one at a
time (effects are called one after another, never nested), and it never waits for anything else while holding it, so
there is no circular wait. The semaphore wait is one-directional: the decode thread may wait for the JavaFX thread to
collect a frame, but the JavaFX thread never waits for the decode thread (`pollReady()` never blocks). The JavaFX
thread does wait in `stopAndWait()` and `close()`, but those wait only for the decode and worker threads, which never
wait for the JavaFX thread (the decode thread reports errors with `Platform.runLater`, which does not block), and
both waits are bounded by a timeout (3 s and 2 s).

**Frame accuracy and functionality.** Each frame carries the timestamp the decoder read from the file. Display time is
computed as the start time plus the frame's offset from the first frame, exactly as in the original, and frames are
never dropped, so playback stays linear in time and a slow frame delays only itself and the frames behind it. Seeking
stops the decode thread, discards the frames it had queued (they belong to the old position), repositions the
grabber and starts a new decode thread. Pause keeps the clock logic of the original; a seek while paused shows exactly
one frame (`awaitingFrameAfterSeek`).

### Results

**Load test** (`mvn verify`, `PlaybackLoadComparisonIT`): synthetic 1920x800 frames, 48 measured frames per run,
16 workers on an 18-thread CPU. "Single-threaded" applies every effect to every frame on one thread, as the original
did. Effects beyond 11 repeat the same sequence, which the command line allows. The table is the best run, made on an
otherwise idle machine (see the limitation on machine load below).

| Effects | Single-threaded fps | Pipeline fps | Speedup |
|---|---|---|---|
| 2 | 158.0 | 484.3 | 3.07 |
| 4 | 81.5 | 270.7 | 3.32 |
| 8 | 29.7 | 67.3 | 2.27 |
| 11 | 31.3 | 65.4 | 2.09 |
| 16 | 25.1 | 62.0 | 2.47 |
| 22 | 17.2 | 62.0 | 3.61 |
| 33 | 11.5 | 59.6 | 5.17 |

Real-time rate is 23.98 fps. **Most effects sustained in real time: single-threaded 16, pipeline 33** (the sweep stops
at 33, so the pipeline's limit is at least that). The assertions are loose on purpose: the pipeline must sustain at
least as many effects in real time as single-threaded processing, and must not be slower than it with all effects, so
a weak or busy build machine cannot fail the build.

**Live playback** (`simpsons.mp4`, 1920x800, 23.98 fps; console line "Frame headroom"):

| Run | Effects | Samples | Min | Median | Max | Negative | Under 10 ms |
|---|---|---|---|---|---|---|---|
| B | `-wy` | 53 | 12.8 ms | 29.6 ms | 37.7 ms | 0 | 0 |
| C | all 11 (`-nssnfwyvdjmbp`) | 11 | 20.0 ms | 29.3 ms | 37.9 ms | 0 | 0 |
| D | `-a -wy` (audio on) | 77 | 11.1 ms | 29.2 ms | 38.6 ms | 0 | 0 |

For comparison, in a short sample of console lines the original player with no effects typically showed 20 to 30 ms
of headroom, and with only two effects (`-wy` and `-yw`) it was often below 10 ms, with some negative (late) frames.

**Manual checks on Windows** (with the 11-effect run, and audio on for the audio check): effects visible and in the
right order; the window stays responsive while resizing during playback; maximise (`-x`); pause and resume; seek
forwards and backwards with the arrow keys; Home (start over); seek while paused; audio toggle and audio in sync;
Escape stops and exits; playback to the end shows "Playback finished". All behaved as in the original.

### Limitations

- **Locked effects cap throughput.** Because `Jitterer`, `Mottler` and `Bleeder` are `synchronized`, only one frame at
  a time can be inside each. In the load test the pipeline levels off at roughly 50 to 68 fps once `Bleeder` is in the
  chain (about 2 to 2.8 times real time, varying a little between runs). Giving those effects per-thread scratch
  buffers would lift the cap; I chose the simple, obviously-correct lock because the player's actual need is 24 fps.
- **The deep copy is on the decode thread.** Each frame is copied once before it is handed to a worker. The grabber
  reuses its buffer, so the copy cannot be done later.
- **Seeking waits for the decode thread to stop**, which takes at most one grab (and at most 3 s in a fault case).
- **The load test measures effect processing only.** It does not include decoding, conversion or display, so it
  does not reproduce the original player's full per-frame cost; the live runs above cover the whole player.
- **The load test is sensitive to machine load.** A second run, made while the machine was busy, gave much lower
  pipeline throughput (real-time limit: single-threaded 8, pipeline 16, instead of 16 and 33) and took 39 s instead
  of 20 s. The pipeline's advantage held but shrank, so the table above is the best run, made on an otherwise idle
  machine.
- **The "Frame headroom" figure is now measured when a finished frame is picked up from the pipeline**, so it shows
  whether frames were ready in time; it can no longer show how far ahead the workers are.
- **The live runs are short and from one video.** Run C has only 11 samples, and the baseline comparison comes from a
  few console lines per run, so they are indications, not precise measurements.
- **The load test and the GUI behaviour checks were run on one machine** (18 hardware threads, Windows), by the author.

## Tests

25 unit test runs (21 test methods, counting each parameterised case separately) and one integration test, chosen for
the areas that carry risk:

- `CommandLineControllerTest`, `FrameProcessorRegistryTest`: the command-line specification (ordering, stacking,
  repeats, errors, help text) and the extension point.
- `EffectChainThreadSafetyTest`: effects give identical results sequentially and from many threads; frame copies are
  independent.
- `FrameProcessingPipelineTest`: frames come out in order when workers finish out of order, and are isolated from the
  decoder reusing its buffer.
- `PlaybackLoadComparisonIT`: the load test above.

The Assignment 3 tests are listed in the Assignment 3 section below.

## Assignment 3: speech-to-text subtitles

### Running it

The subtitle server and the player are two separate programs. Start the server first.

```
set OPENAI_API_KEY=<the key>          (Windows cmd; in PowerShell: $env:OPENAI_API_KEY = "<the key>")
mvn -f server/pom.xml spring-boot:run
mvn javafx:run -Djavafx.args="C:\path\to\video.mp4 -S"
```

- The key is read only from the `OPENAI_API_KEY` environment variable. It is not in the code, the comments, the tests
  or this repository (`key.txt`, `*.key` and `secrets/` are in `.gitignore`).
- Subtitles are switched on by the `-S` / `--subtitle-frames` option, by the button to the right of the mute button,
  or by pressing `s`. The same button and key switch them off again.
- `mvn verify` runs the player's tests. `mvn -f server/pom.xml verify` runs the server's.

### Server (`server/`)

`POST /api/v1/subtitles` takes an audio file, asks OpenAI's `whisper-1` for a `verbose_json` transcription with word
and segment timestamps, drops every segment with `noSpeechProb > 0.6` and `aveLogProb < -1.0`, and returns the
remaining segments. `GET /api/v1/global/usage` returns `{ "usage": <seconds of audio transcribed> }`. Errors are
reported with distinct status codes: 400 bad request, 413 file too large, 502 the OpenAI call failed (the real cause is
logged), 503 no API key configured.

### Player: how subtitles reach the screen

| Class | Thread | Responsibility |
|---|---|---|
| `SubtitleFeeder` | its own daemon thread (`subtitle-feeder`) | Reads audio, calls the server, publishes the result. Started the first time subtitles are switched on. |
| `AudioChunkExtractor` | feeder thread | Reads the media's audio through its own `FFmpegFrameGrabber` (16 kHz mono) and cuts it into WAV chunks. |
| `SubtitleServerClient` | feeder thread | Posts a chunk to the server and parses the reply (`MiniJson`, JDK only). |
| `SubtitleCueBuilder`, `SubtitleText` | feeder thread | Turn segments into cues of 1 to 7 s, at most 2 lines of at most 42 characters, broken after punctuation. |
| `SubtitleTrack` | any thread | Immutable list of cues. |
| `FrameSubtitler`, `SubtitleRenderer` | worker threads | Draw the active cue (white sans-serif text, black outline) onto a frame. |

- **The feeder never touches the playback decoder.** It opens its own grabber on the same file, so the rule from
  Assignment 2 (only the decode thread uses the playback grabber) still holds.
- **Publishing is one volatile reference.** The feeder builds a new immutable `SubtitleTrack` and swaps it in. The worker
  threads only read that reference, so they never wait for the network. They also pass the frame's timestamp back to
  the feeder through one volatile number.
- **Subtitles appear when the words are spoken.** The first chunk is short (8 s) and starts where the video is
  playing, so subtitles show up soon after they are switched on. Later chunks are 30 s. Cues are placed by the
  segment's own start time.
- **Each part of the audio is sent once.** A coverage map records what has been sent. The feeder works forward from
  the playback position and then wraps round to anything skipped. Seeking therefore never causes a repeat request.
- **Switching off.** The feeder sleeps while subtitles are off and checks again before every request, so no audio is
  sent while they are off. Frames that were already processed ahead of the display (up to about half a second) may
  still show a subtitle for a moment. New frames carry none.
- **Switching on.** The cues found so far are used straight away. The feeder wakes up and its next chunk starts from
  the current playback position, so subtitles for what is being spoken now appear quickly.

### When things go wrong

The server being down, the network dropping and an error reply all arrive as an `IOException`. The feeder waits (1, 2,
4, ... up to 10 s), retries the same stretch of audio, and prints a short message on the first failure and on every
tenth after that. When the server comes back, subtitles resume by themselves. A failure of the server keeps the media
open. A failure reading the media (for example a USB drive being removed) closes it and opens it again on the next
retry. Neither stops or slows playback, because the feeder is a separate thread and the drawing code never waits for it.

### Assignment 3 tests

81 player tests and the server tests, all passing. The ones that matter most for this part: `SubtitleFeederTest`
(no requests while off; resumes when on; recovers from server failure with the media kept open; reopens lost media;
starts at the playback position; covers the media exactly once), `SubtitleCueBuilderTest`, `SubtitleTextTest`,
`SubtitleServerClientTest` (against a local test server), `AudioChunkExtractorTest` (real WAV files), `MiniJsonTest`,
`FrameSubtitlerTest`, and on the server `SegmentFilterTest` and `TranscriptionServiceTest`.

I also checked by hand on Windows, with `simpsons.mp4` and a real API key: subtitles appear as the words are spoken and
match the speech; the button and `s` switch them on and off; the server usage figure matches the length of the audio
sent; the server was stopped and restarted during playback, and playback stayed smooth and subtitles came back by
themselves.

### Assignment 3 limitations

- **The whole film is transcribed in the background, not only what the viewer reaches.** That is the reason subtitles are
  ready early and seeking is cheap, but a long film uses more API audio than a viewer who stops early would.
- **Losing the media file is covered by a unit test but I have not tested it by hand** with a real drive being removed.
- **A new video on a USB drive** goes through the same path as `simpsons.mp4`. I only tested a short trailer by hand.
- **There is no caching on the server.** Playing the same file again sends its audio again.
- **Toggling subtitles while the video is paused** was not tested by hand.
- **Opening the feeder's grabber at startup** can cost one slow frame (one negative "Frame headroom" line at start).

## Acknowledgements

Starter code: Simon Ratcliffe, in collaboration with GPT-5.6 Terra, Adelaide University COMP3011.
Assignment 3 uses OpenAI's speech-to-text API through the subtitle server, and Spring Boot for the server.

## AI acknowledgement

The assignment allows generative AI tools for software research, design, development and testing, provided
their use is acknowledged and the submitted code has passed my own scrutiny. I used Claude (Anthropic's generative AI
assistant) throughout Assignment 3. This section sets out how.

### How Claude was used

- **Design:** Claude helped me plan the subtitle track and renderer, the background feeder thread, the
  server API and the error handling.
- **Development:** Claude wrote the code for the new classes and the edits to existing classes. Each new
  file says so in its header comment.
- **Testing:** Claude wrote the unit tests.
- **Debugging and setup:** Claude helped me diagnose problems, including the 401 API key error, the retry
  and logging behaviour, and Maven and Eclipse setup issues.
- **Documentation:** Claude helped me draft this README.

### My own work and scrutiny

- I typed or pasted every change into the project myself and read each one before committing it. I made
  every commit, so the Git history reflects how the project was actually built, step by step.
- I ran the builds and the full test suite myself.
- I tested the player and server end to end on a real video. I checked that subtitles appear and disappear
  correctly, that the S key and the subtitle button work, and that playback copes when the server is
  stopped and restarted.
- I made the decisions about scope and priorities, including what to leave out because of time.
- I can explain what each class does, why it is included, and why it is written the way it is.

### Boundaries

- Claude did not run Git commands or edit my project files directly.
- The API key does not appear anywhere in the code, comments, tests or repository. It is read only from
  the `OPENAI_API_KEY` environment variable.