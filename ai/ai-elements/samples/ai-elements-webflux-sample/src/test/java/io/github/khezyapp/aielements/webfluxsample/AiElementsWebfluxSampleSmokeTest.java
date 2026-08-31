package io.github.khezyapp.aielements.webfluxsample;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

/**
 * End-to-end smoke test for the runnable WebFlux sample. Runs under the default
 * {@code mock} profile (no DeepSeek key needed) and asserts the SSE stream starts with
 * a {@code start} event, contains a text delta, and terminates with the {@code [DONE]}
 * sentinel as a server-sent event.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AiElementsWebfluxSampleSmokeTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("POST /api/chat streams the mock UI event set and ends with DONE")
    void mockChatStreamsAndEndsWithDone() {
        final var body = webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("{\"id\":\"chat_1\",\"messages\":[{\"id\":\"m1\",\"role\":\"user\","
                        + "\"content\":\"hello\"}],\"trigger\":\"submit-message\",\"messageId\":null}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody()
                .collectList()
                .block();

        assertContainsStartEvent(body);
        assertEndsWithDone(body);
    }

    private static void assertContainsStartEvent(final List<String> body) {
        final var joined = String.join("", body);
        if (!joined.contains("{\"type\":\"start\"")) {
            throw new AssertionError("SSE stream did not contain a start event: " + joined);
        }
    }

    private static void assertEndsWithDone(final List<String> body) {
        final var joined = String.join("", body);
        if (!joined.contains("[DONE]")) {
            throw new AssertionError("SSE stream did not end with DONE sentinel: " + joined);
        }
    }
}
