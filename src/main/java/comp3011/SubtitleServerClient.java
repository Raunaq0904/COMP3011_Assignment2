/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends a piece of audio to the subtitle server and returns the speech it found. Every failure (server not running,
 * network down, timeout, an error status, an unreadable reply) is reported as an {@link IOException}, so the caller can
 * handle all of them the same way.
 */
public class SubtitleServerClient {

    public static final URI DEFAULT_ENDPOINT = URI.create("http://localhost:8080/api/v1/subtitles");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI endpoint;
    private final Duration requestTimeout;

    public SubtitleServerClient() {
        this(DEFAULT_ENDPOINT, Duration.ofSeconds(120));
    }

    SubtitleServerClient(URI endpoint, Duration requestTimeout) {
        this.endpoint = endpoint;
        this.requestTimeout = requestTimeout;
    }

    /** @param filename should end in the audio's file extension, such as {@code chunk-3.wav} */
    public List<SpokenSegment> transcribe(byte[] audio, String filename) throws IOException, InterruptedException {
        String boundary = "----comp3011-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, audio, filename)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("The subtitle server answered with status " + response.statusCode());
        }
        return parseSegments(response.body());
    }

    static byte[] multipartBody(String boundary, byte[] audio, String filename) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(audio.length + 256);
        out.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.writeBytes("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.writeBytes(audio);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    static List<SpokenSegment> parseSegments(String json) throws IOException {
        try {
            Object root = MiniJson.parse(json);
            List<SpokenSegment> segments = new ArrayList<>();
            for (Object item : (List<?>) ((Map<?, ?>) root).get("segments")) {
                Map<?, ?> segment = (Map<?, ?>) item;
                segments.add(new SpokenSegment(
                        ((Number) segment.get("start")).doubleValue(),
                        ((Number) segment.get("end")).doubleValue(),
                        (String) segment.get("text")));
            }
            return segments;
        } catch (RuntimeException e) {
            throw new IOException("The subtitle server's reply could not be understood", e);
        }
    }
}