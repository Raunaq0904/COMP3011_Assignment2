/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 *
 * AI acknowledgement: this file was developed with the assistance of Claude (Anthropic's generative AI
 * assistant), used as a research, design and development aid. Claude proposed the design and the code,
 * explaining each part step by step. I typed every change in myself, then ran, tested and reviewed it so
 * that I understand what each part does and why it is in the project. All commits were made by me.
 * See the "AI acknowledgement" section of README.md for details.
 */
package comp3011.server;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Sends audio to OpenAI's transcription API using the whisper-1 model, asking for word and segment timestamps. The API
 * key is read only from the OPENAI_API_KEY environment variable and is never written to a log or a file.
 */
@Service
public class OpenAiSpeechToTextClient implements SpeechToTextClient {

    private static final String ENDPOINT = "https://api.openai.com/v1/audio/transcriptions";

    @Value("${OPENAI_API_KEY:NOT_SET}")
    private String openAiApiKey;

    private final RestClient restClient;

    public OpenAiSpeechToTextClient() {
        // Timeouts so that a lost network connection fails quickly instead of leaving a request hanging forever.
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String transcribe(byte[] audioBytes, String filename) {
        if (openAiApiKey == null || openAiApiKey.isBlank() || "NOT_SET".equals(openAiApiKey)) {
            throw new IllegalStateException("The OPENAI_API_KEY environment variable is not set on the server.");
        }

        ByteArrayResource fileResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("model", "whisper-1");
        body.add("response_format", "verbose_json");
        body.add("timestamp_granularities[]", "word");
        body.add("timestamp_granularities[]", "segment");

        return restClient.post()
                .uri(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}
