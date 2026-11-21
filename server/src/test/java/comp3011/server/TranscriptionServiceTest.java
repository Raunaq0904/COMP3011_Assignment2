/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TranscriptionServiceTest {

    private static final String RESPONSE = """
            {"text":"Hello there. Thank you for watching","duration":12.5,
             "segments":[
               {"id":0,"start":0.0,"end":2.0,"text":" Hello there.","avg_logprob":-0.3,"no_speech_prob":0.05},
               {"id":1,"start":2.0,"end":4.0,"text":" Thank you for watching","avg_logprob":-1.4,"no_speech_prob":0.9}
             ],
             "words":[{"word":"Hello","start":0.0,"end":0.5}]}
            """;

    @Test
    void skipsNonSpeechSegmentsAndTrimsText() throws Exception {
        TranscriptionService service = new TranscriptionService((audio, name) -> RESPONSE, new UsageTracker());

        SubtitlesResponse response = service.transcribe(new byte[] { 1, 2, 3 }, "chunk.wav");

        assertEquals(1, response.segments().size());
        assertEquals("Hello there.", response.segments().get(0).text());
        assertEquals(0.0, response.segments().get(0).start());
        assertEquals(2.0, response.segments().get(0).end());
    }

    @Test
    void recordsTheAudioSecondsOfEveryRequest() throws Exception {
        UsageTracker tracker = new UsageTracker();
        TranscriptionService service = new TranscriptionService((audio, name) -> RESPONSE, tracker);

        service.transcribe(new byte[] { 1 }, "a.wav");
        service.transcribe(new byte[] { 1 }, "b.wav");

        assertEquals(25.0, tracker.totalSeconds());
    }

    @Test
    void aResponseWithNoSegmentsGivesAnEmptyList() throws Exception {
        TranscriptionService service =
                new TranscriptionService((audio, name) -> "{\"text\":\"\",\"duration\":3.0}", new UsageTracker());

        assertTrue(service.transcribe(new byte[] { 1 }, "a.wav").segments().isEmpty());
    }
}
