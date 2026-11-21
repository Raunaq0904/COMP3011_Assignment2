/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);

    private final SpeechToTextClient speechToTextClient;
    private final UsageTracker usageTracker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TranscriptionService(SpeechToTextClient speechToTextClient, UsageTracker usageTracker) {
        this.speechToTextClient = speechToTextClient;
        this.usageTracker = usageTracker;
    }

    public SubtitlesResponse transcribe(byte[] audio, String filename) throws IOException {
        String responseBody = speechToTextClient.transcribe(audio, filename);
        WhisperResponse parsed = objectMapper.readValue(responseBody, WhisperResponse.class);

        usageTracker.addSeconds(parsed.duration());

        List<SubtitlesResponse.Segment> kept = new ArrayList<>();
        int skipped = 0;
        if (parsed.segments() != null) {
            for (WhisperResponse.Segment segment : parsed.segments()) {
                String text = segment.text() == null ? "" : segment.text().trim();
                if (SegmentFilter.isNotSpeech(segment.noSpeechProb(), segment.avgLogProb())) {
                    skipped++;
                } else if (!text.isEmpty() && segment.end() > segment.start()) {
                    kept.add(new SubtitlesResponse.Segment(segment.start(), segment.end(), text));
                }
            }
        }
        log.info("Transcribed {} bytes ({}s of audio): kept {} segments, skipped {} as non-speech",
                audio.length, parsed.duration(), kept.size(), skipped);
        return new SubtitlesResponse(kept);
    }
}
