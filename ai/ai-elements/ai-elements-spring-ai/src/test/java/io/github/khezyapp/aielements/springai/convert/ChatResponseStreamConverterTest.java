package io.github.khezyapp.aielements.springai.convert;

import io.github.khezyapp.aielements.model.common.FinishReason;
import io.github.khezyapp.aielements.model.common.Usage;
import io.github.khezyapp.aielements.model.response.SseEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChatResponseStreamConverterTest {

    @Test
    @DisplayName("emits start, start-step, text deltas, then finish (no DONE sentinel)")
    void emitsStartThenStartStepThenDeltasThenFinish() {
        final var resp1 = new ChatResponse(List.of(new Generation(assistant("a"))));
        final var gen2 = new Generation(
                assistant("b"),
                ChatGenerationMetadata.builder().finishReason("stop").build());
        final var resp2 = new ChatResponse(
                List.of(gen2),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 20, 30)).build());

        final var events = ChatResponseStreamConverter.toEvents(Flux.just(resp1, resp2))
                .collectList()
                .block();
        assertNotNull(events);

        final var start = (SseEvent.Start) events.get(0);
        assertNotNull(start.messageId());
        assertEquals(new SseEvent.StartStep(), events.get(1));
        assertEquals(new SseEvent.TextStart(start.messageId()), events.get(2));
        assertEquals(new SseEvent.TextDelta(start.messageId(), "a"), events.get(3));
        assertEquals(new SseEvent.TextDelta(start.messageId(), "b"), events.get(4));
        assertEquals(new SseEvent.TextEnd(start.messageId()), events.get(5));
        assertEquals(new SseEvent.FinishStep(), events.get(6));
        assertEquals(
                new SseEvent.Finish(FinishReason.STOP, new Usage(10, 20, 30, null, null)),
                events.get(7));
    }

    @Test
    @DisplayName("emits tool-input events for a tool call, never a tool-output event")
    void emitsToolInputStartAndAvailableForToolCall() {
        final var gen = new Generation(AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "getWeather", "{\"city\":\"Phnom Penh\"}")))
                .build());
        final var resp = new ChatResponse(List.of(gen));

        final var events = ChatResponseStreamConverter.toEvents(Flux.just(resp))
                .collectList()
                .block();
        assertNotNull(events);

        final var start = (SseEvent.Start) events.get(0);
        assertNotNull(start.messageId());
        assertEquals(new SseEvent.StartStep(), events.get(1));
        assertEquals(new SseEvent.ToolInputStart("call-1", "getWeather"), events.get(2));
        assertEquals(new SseEvent.ToolInputAvailable(
                "call-1", "getWeather", "{\"city\":\"Phnom Penh\"}"), events.get(3));
    }

    @Test
    @DisplayName("prefixes start and start-step exactly once for a multi-response stream")
    void prefixesStartExactlyOnce() {
        final var r1 = new ChatResponse(List.of(new Generation(assistant("a"))));
        final var r2 = new ChatResponse(List.of(new Generation(assistant("b"))));
        final var r3 = new ChatResponse(List.of(new Generation(assistant("c"))));

        final var events = ChatResponseStreamConverter.toEvents(Flux.just(r1, r2, r3))
                .collectList()
                .block();
        assertNotNull(events);

        final var starts = events.stream().filter(e -> e.type().equals("start")).count();
        final var startSteps = events.stream().filter(e -> e.type().equals("start-step")).count();
        final var textStarts = events.stream().filter(e -> e.type().equals("text-start")).count();
        final var textDeltas = events.stream().filter(e -> e.type().equals("text-delta")).count();
        assertEquals(1, starts);
        assertEquals(1, startSteps);
        assertEquals(1, textStarts);
        assertEquals(3, textDeltas);

        final var first = events.get(0);
        final var second = events.get(1);
        assertEquals("start", first.type());
        assertEquals("start-step", second.type());
    }

    @Test
    @DisplayName("uses empty usage when the response metadata is absent")
    void finishUsesEmptyUsageWhenMetadataNull() {
        final var gen = new Generation(
                assistant("done"),
                ChatGenerationMetadata.builder().finishReason("stop").build());
        final var resp = new ChatResponse(List.of(gen));

        final var events = ChatResponseStreamConverter.toEvents(Flux.just(resp))
                .collectList()
                .block();
        assertNotNull(events);

        final var start = (SseEvent.Start) events.get(0);
        assertNotNull(start.messageId());
        assertEquals(new SseEvent.TextStart(start.messageId()), events.get(2));
        assertEquals(new SseEvent.TextDelta(start.messageId(), "done"), events.get(3));
        assertEquals(new SseEvent.TextEnd(start.messageId()), events.get(4));
        assertEquals(new SseEvent.FinishStep(), events.get(5));
        assertEquals(new SseEvent.Finish(FinishReason.STOP, Usage.empty()), events.get(6));
    }

    @Test
    @DisplayName("two subscriptions on the no-arg overload get distinct message ids")
    void generatesDistinctMessageIdsPerSubscription() {
        final var stream = Flux.just(new ChatResponse(
                List.of(new Generation(
                        assistant("x"),
                        ChatGenerationMetadata.builder().finishReason("stop").build()))));

        final var first = ChatResponseStreamConverter.toEvents(stream).collectList().block();
        final var second = ChatResponseStreamConverter.toEvents(stream).collectList().block();
        assertNotNull(first);
        assertNotNull(second);

        final var firstStart = (SseEvent.Start) first.get(0);
        final var secondStart = (SseEvent.Start) second.get(0);
        assertNotNull(firstStart.messageId());
        assertNotNull(secondStart.messageId());
        org.junit.jupiter.api.Assertions.assertNotEquals(firstStart.messageId(),
                secondStart.messageId());
    }

    @Test
    @DisplayName("explicit messageId overload round-trips the supplied id into start and text blocks")
    void explicitMessageIdOverloadUsesSuppliedId() {
        final var resp = new ChatResponse(List.of(new Generation(
                assistant("hi"),
                ChatGenerationMetadata.builder().finishReason("stop").build())));

        StepVerifier.create(ChatResponseStreamConverter.toEvents(Flux.just(resp), "m-42"))
                .expectNext(new SseEvent.Start("m-42"))
                .expectNext(new SseEvent.StartStep())
                .expectNext(new SseEvent.TextStart("m-42"))
                .expectNext(new SseEvent.TextDelta("m-42", "hi"))
                .expectNext(new SseEvent.TextEnd("m-42"))
                .expectNext(new SseEvent.FinishStep())
                .expectNext(new SseEvent.Finish(FinishReason.STOP, Usage.empty()))
                .verifyComplete();
    }

    private static AssistantMessage assistant(final String text) {
        return AssistantMessage.builder().content(text).build();
    }

    private static AssistantMessage thinking(final String text) {
        return AssistantMessage.builder().content(text).properties(Map.of("thinking", true)).build();
    }

    private static AssistantMessage reasoningMeta(final String text) {
        return AssistantMessage.builder().properties(Map.of("reasoningContent", text)).build();
    }

    @Test
    @DisplayName("emits reasoning start/delta/end before text without leaking thinking into text")
    void emitsReasoningBeforeText() {
        final var r1 = new ChatResponse(List.of(new Generation(thinking("Let me"))));
        final var r2 = new ChatResponse(List.of(new Generation(thinking(" think"))));
        final var r3 = new ChatResponse(List.of(new Generation(
                assistant("Answer"),
                ChatGenerationMetadata.builder().finishReason("stop").build())));

        final var events = ChatResponseStreamConverter.toEvents(Flux.just(r1, r2, r3))
                .collectList()
                .block();
        assertNotNull(events);

        final var start = (SseEvent.Start) events.get(0);
        final var reasoningId = "reasoning-" + start.messageId();
        assertEquals(new SseEvent.StartStep(), events.get(1));
        assertEquals(new SseEvent.ReasoningStart(reasoningId), events.get(2));
        assertEquals(new SseEvent.ReasoningDelta(reasoningId, "Let me"), events.get(3));
        assertEquals(new SseEvent.ReasoningDelta(reasoningId, " think"), events.get(4));
        assertEquals(new SseEvent.ReasoningEnd(reasoningId), events.get(5));
        assertEquals(new SseEvent.TextStart(start.messageId()), events.get(6));
        assertEquals(new SseEvent.TextDelta(start.messageId(), "Answer"), events.get(7));
        assertEquals(new SseEvent.TextEnd(start.messageId()), events.get(8));
        assertEquals(new SseEvent.FinishStep(), events.get(9));
    }

    @Test
    @DisplayName("emits only the new suffix for cumulative reasoning chunks")
    void emitsOnlyNewSuffixForCumulativeReasoning() {
        final var r1 = new ChatResponse(List.of(new Generation(thinking("Let me"))));
        final var r2 = new ChatResponse(List.of(new Generation(thinking("Let me think"))));

        final var events = ChatResponseStreamConverter.toEvents(Flux.just(r1, r2))
                .collectList()
                .block();
        assertNotNull(events);

        final var start = (SseEvent.Start) events.get(0);
        final var reasoningId = "reasoning-" + start.messageId();
        assertEquals(new SseEvent.ReasoningStart(reasoningId), events.get(2));
        assertEquals(new SseEvent.ReasoningDelta(reasoningId, "Let me"), events.get(3));
        assertEquals(new SseEvent.ReasoningDelta(reasoningId, " think"), events.get(4));
    }

    @Test
    @DisplayName("emits text when cumulative reasoning metadata persists onto a content chunk")
    void emitsTextWhenReasoningMetadataPersists() {
        // Ollama shape: cumulative `reasoningContent` metadata stays attached to the answer chunk.
        final var r1 = new ChatResponse(List.of(new Generation(reasoningMeta("Thinking"))));
        final var r2 = new ChatResponse(List.of(new Generation(reasoningMeta("Thinking Process"))));
        final var r3 = new ChatResponse(List.of(new Generation(
                AssistantMessage.builder()
                        .content("Hello")
                        .properties(Map.of("reasoningContent", "Thinking Process"))
                        .build(),
                ChatGenerationMetadata.builder().finishReason("stop").build())));

        final var events = ChatResponseStreamConverter.toEvents(Flux.just(r1, r2, r3))
                .collectList()
                .block();
        assertNotNull(events);

        final var start = (SseEvent.Start) events.get(0);
        final var reasoningId = "reasoning-" + start.messageId();
        assertEquals(new SseEvent.StartStep(), events.get(1));
        assertEquals(new SseEvent.ReasoningStart(reasoningId), events.get(2));
        assertEquals(new SseEvent.ReasoningDelta(reasoningId, "Thinking"), events.get(3));
        assertEquals(new SseEvent.ReasoningDelta(reasoningId, " Process"), events.get(4));
        assertEquals(new SseEvent.ReasoningEnd(reasoningId), events.get(5));
        assertEquals(new SseEvent.TextStart(start.messageId()), events.get(6));
        assertEquals(new SseEvent.TextDelta(start.messageId(), "Hello"), events.get(7));
        assertEquals(new SseEvent.TextEnd(start.messageId()), events.get(8));
        assertEquals(new SseEvent.FinishStep(), events.get(9));
        assertEquals(new SseEvent.Finish(FinishReason.STOP, Usage.empty()), events.get(10));
    }
}
