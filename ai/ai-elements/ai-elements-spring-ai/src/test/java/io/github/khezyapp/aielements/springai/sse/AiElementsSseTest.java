package io.github.khezyapp.aielements.springai.sse;

import io.github.khezyapp.aielements.model.response.SseEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.mock.web.MockHttpServletResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiElementsSseTest {

    @Test
    @DisplayName("toServerSentEvents renders each json payload and appends the DONE sentinel")
    void toServerSentEventsAddsDoneSentinel() {
        final List<SseEvent> events = List.of(new SseEvent.Start(null), new SseEvent.StartStep());

        StepVerifier.create(AiElementsSse.toServerSentEvents(events))
                .consumeNextWith(sse -> assertEquals("{\"type\":\"start\"}", sse.data()))
                .consumeNextWith(sse -> assertEquals("{\"type\":\"start-step\"}", sse.data()))
                .consumeNextWith(sse -> assertEquals("[DONE]", sse.data()))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamChat pipelines ChatResponses into typed UI-stream SSE events")
    void streamChatPipelinesChatResponsesIntoUiEvents() {
        final var gen = new Generation(
                assistant("Khmer text"),
                ChatGenerationMetadata.builder().finishReason("stop").build());
        final var resp = new ChatResponse(List.of(gen));

        StepVerifier.create(AiElementsSse.streamChat(Flux.just(resp)))
                .consumeNextWith(sse -> {
                    assertTrue(sse.data().startsWith("{\"type\":\"start\",\"messageId\":\""));
                })
                .consumeNextWith(sse -> assertEquals("{\"type\":\"start-step\"}", sse.data()))
                .consumeNextWith(sse -> assertTrue(sse.data()
                        .startsWith("{\"type\":\"text-start\",\"id\":\"")))
                .consumeNextWith(sse -> assertTrue(sse.data()
                        .startsWith("{\"type\":\"text-delta\",\"id\":\"")
                        && sse.data().contains("\"delta\":\"Khmer text\"")))
                .consumeNextWith(sse -> assertTrue(sse.data()
                        .startsWith("{\"type\":\"text-end\",\"id\":\"")))
                .consumeNextWith(sse -> assertEquals("{\"type\":\"finish-step\"}", sse.data()))
                .consumeNextWith(sse -> assertTrue(sse.data()
                        .startsWith("{\"type\":\"finish\",\"finishReason\":\"stop\"")))
                .consumeNextWith(sse -> assertEquals("[DONE]", sse.data()))
                .verifyComplete();
    }

    @Test
    @DisplayName("toWireFormat renders events with data framing and the DONE sentinel")
    void toWireFormatRendersDataFramingAndDone() {
        final List<SseEvent> events = List.of(new SseEvent.TextDelta("0", "hi"));

        final var wire = AiElementsSse.toWireFormat(events);

        assertEquals("data: {\"type\":\"text-delta\",\"id\":\"0\",\"delta\":\"hi\"}\n\n" + SseEvent.done(), wire);
    }

    @Test
    @DisplayName("toWireLines renders events without the DONE sentinel")
    void toWireLinesRendersWithoutDone() {
        final List<SseEvent> events = List.of(new SseEvent.Start(null), new SseEvent.StartStep());

        final var lines = AiElementsSse.toWireLines(events);

        assertEquals(2, lines.size());
        assertEquals("data: {\"type\":\"start\"}\n\n", lines.get(0));
        assertEquals("data: {\"type\":\"start-step\"}\n\n", lines.get(1));
    }

    @Test
    @DisplayName("applyStreamHeaders sets the required UI-stream headers")
    void applyStreamHeadersSetsRequiredHeaders() {
        final var response = new MockHttpServletResponse();

        AiElementsSse.applyStreamHeaders(response);

        assertEquals("no-cache", response.getHeader("Cache-Control"));
        assertEquals("no", response.getHeader("X-Accel-Buffering"));
        assertEquals("v1", response.getHeader("X-Vercel-AI-UI-Message-Stream"));
    }

    private static AssistantMessage assistant(final String text) {
        return AssistantMessage.builder().content(text).build();
    }
}
