/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class SubtitleServerClientTest {

    private static HttpServer startServer(int status, String body, AtomicReference<String> seenRequest)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/subtitles", exchange -> {
            if (seenRequest != null) {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
                seenRequest.set(contentType + "\n" + request);
            }
            byte[] reply = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static SubtitleServerClient clientFor(HttpServer server, Duration timeout) {
        return new SubtitleServerClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/subtitles"), timeout);
    }

    @Test
    void sendsTheAudioAsAMultipartFileAndReadsTheSegments() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        HttpServer server = startServer(200,
                "{\"segments\":[{\"start\":0.5,\"end\":2.0,\"text\":\"Hello there.\"}]}", seen);
        try {
            List<SpokenSegment> segments =
                    clientFor(server, Duration.ofSeconds(5)).transcribe(new byte[] { 1, 2, 3 }, "chunk-0.wav");

            assertEquals(List.of(new SpokenSegment(0.5, 2.0, "Hello there.")), segments);
            assertTrue(seen.get().startsWith("multipart/form-data; boundary="));
            assertTrue(seen.get().contains("name=\"file\"; filename=\"chunk-0.wav\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void anErrorStatusIsReportedAsAnIoException() throws Exception {
        HttpServer server = startServer(502, "{\"message\":\"down\"}", null);
        try {
            assertThrows(IOException.class,
                    () -> clientFor(server, Duration.ofSeconds(5)).transcribe(new byte[] { 1 }, "a.wav"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void anUnreadableReplyIsReportedAsAnIoException() throws Exception {
        HttpServer server = startServer(200, "this is not json", null);
        try {
            assertThrows(IOException.class,
                    () -> clientFor(server, Duration.ofSeconds(5)).transcribe(new byte[] { 1 }, "a.wav"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aServerThatIsNotRunningIsReportedAsAnIoException() throws Exception {
        HttpServer server = startServer(200, "{}", null);
        SubtitleServerClient client = clientFor(server, Duration.ofSeconds(5));
        server.stop(0); // Now nothing is listening on that port.

        assertThrows(IOException.class, () -> client.transcribe(new byte[] { 1 }, "a.wav"));
    }
}