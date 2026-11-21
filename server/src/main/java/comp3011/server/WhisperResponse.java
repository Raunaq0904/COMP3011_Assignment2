/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The parts of OpenAI's verbose_json transcription response that the server uses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhisperResponse(String text, double duration, List<Segment> segments) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Segment(
            double start,
            double end,
            String text,
            @JsonProperty("avg_logprob") double avgLogProb,
            @JsonProperty("no_speech_prob") double noSpeechProb) {
    }
}
