package io.github.khezyapp.aielements.sample.service;

import io.github.khezyapp.aielements.model.common.FinishReason;
import io.github.khezyapp.aielements.model.common.Usage;
import io.github.khezyapp.aielements.model.response.SseEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default ({@code mock}) chat backend. Replays a canned {@link SseEvent} sequence
 * read from the {@code mock/complex-workflow-stream.jsonl} fixture, so the SSE bytes
 * match the reference ai-elements stream exactly.
 *
 * <p>This is the maximum parity surface: because the fixture exercises {@code
 * reasoning-*} and {@code tool-output-available} events (which the generic
 * {@code Flux<ChatResponse>} converter cannot emit), the controller uses
 * {@link io.github.khezyapp.aielements.springai.sse.AiElementsSse#writeTo} on the
 * returned event list under this profile.</p>
 */
@Service
@Profile("mock")
public class MockChatService {

    private static final String FIXTURE = "mock/complex-workflow-stream.jsonl";

    private final List<SseEvent> events;

    public MockChatService() {
        this.events = List.copyOf(readFixture());
    }

    /**
     * The replayed {@link SseEvent} sequence (immutable).
     */
    public List<SseEvent> events() {
        return events;
    }

    private static List<SseEvent> readFixture() {
        final var mapper = new ObjectMapper();
        final var events = new ArrayList<SseEvent>();
        final var resource = new ClassPathResource(FIXTURE);
        try (var in = resource.getInputStream()) {
            final var lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
            for (final var line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                events.add(parseEvent(mapper.readTree(line)));
            }
            return events;
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to read mock SSE fixture " + FIXTURE, e);
        }
    }

    private static SseEvent parseEvent(final JsonNode node) {
        final var type = node.get("type").asString();
        switch (type) {
            case "start":
                return new SseEvent.Start(text(node, "messageId"));
            case "start-step":
                return new SseEvent.StartStep();
            case "reasoning-start":
                return new SseEvent.ReasoningStart(text(node, "id"));
            case "reasoning-delta":
                return new SseEvent.ReasoningDelta(text(node, "id"), text(node, "delta"));
            case "reasoning-end":
                return new SseEvent.ReasoningEnd(text(node, "id"));
            case "tool-input-start":
                return new SseEvent.ToolInputStart(text(node, "toolCallId"), text(node, "toolName"));
            case "tool-output-available":
                return new SseEvent.ToolOutputAvailable(text(node, "toolCallId"), node.get("output").toString());
            case "text-start":
                return new SseEvent.TextStart(text(node, "id"));
            case "text-delta":
                return new SseEvent.TextDelta(text(node, "id"), text(node, "delta"));
            case "text-end":
                return new SseEvent.TextEnd(text(node, "id"));
            case "finish-step":
                return new SseEvent.FinishStep();
            case "finish":
                return parseFinish(node);
            default:
                throw new IllegalArgumentException("Unsupported mock event type: " + type);
        }
    }

    private static SseEvent parseFinish(final JsonNode node) {
        final var reason = FinishReason.fromString(text(node, "finishReason"));
        final var usage = usageOf(node.get("usage"));
        return new SseEvent.Finish(reason, usage);
    }

    private static Usage usageOf(final JsonNode usageNode) {
        if (usageNode == null) {
            return Usage.empty();
        }
        return new Usage(
                intOf(usageNode, "inputTokens"),
                intOf(usageNode, "outputTokens"),
                intOf(usageNode, "totalTokens"),
                nullableInt(usageNode, "reasoningTokens"),
                nullableInt(usageNode, "cachedInputTokens"));
    }

    private static String text(final JsonNode node,
                               final String field) {
        final var value = node.get(field);
        return Objects.isNull(value) ? null : value.asString();
    }

    private static Integer intOf(final JsonNode node,
                                 final String field) {
        final var value = node.get(field);
        return Objects.isNull(value) ? null : value.asInt();
    }

    private static Integer nullableInt(final JsonNode node,
                                       final String field) {
        final var value = node.get(field);
        return Objects.isNull(value) || value.isNull() ? null : value.asInt();
    }
}
