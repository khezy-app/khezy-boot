package io.github.khezyapp.aielements.springai.convert;

import io.github.khezyapp.aielements.model.common.Usage;
import io.github.khezyapp.aielements.model.response.SseEvent;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Converts a streaming {@link Flux}{@code <}ChatResponse{@code >} into a
 * {@link Flux}{@code <}SseEvent{@code >} following the ai-elements UI-stream event
 * sequence: {@code start}, {@code start-step}, one or more {@code text-*} blocks,
 * optional {@code tool-input-*} events, then {@code finish-step} + {@code finish}.
 *
 * <p>Each streamed assistant turn gets a unique {@code messageId}: the {@code start}
 * event carries it and it doubles as the text-block id, so the client can key this turn
 * apart from every other and the {@code regenerate-message} trigger can trim precisely
 * to it. Callers that hold a persistent id (e.g. for regeneration) pass it via the
 * {@link #toEvents(Flux, String)} overload.</p>
 *
 * <p>The {@code [DONE]} sentinel is intentionally <em>not</em> emitted here — the
 * transport writer ({@link io.github.khezyapp.aielements.springai.sse.AiElementsSse})
 * appends it so the sentinel is written in exactly one place.</p>
 */
public final class ChatResponseStreamConverter {

    private ChatResponseStreamConverter() {
    }

    /**
     * {@link Flux}{@code <}ChatResponse{@code >} to {@link Flux}{@code <}SseEvent{@code >}
     * with a freshly generated, unique {@code messageId} (also used as the text-block id).
     */
    public static Flux<SseEvent> toEvents(final Flux<ChatResponse> stream) {
        return toEvents(stream, UUID.randomUUID().toString());
    }

    /**
     * Overload where the caller supplies the {@code messageId} (both the {@code start}
     * event's id and the text-block id), so a stored id round-trips for regeneration.
     */
    public static Flux<SseEvent> toEvents(final Flux<ChatResponse> stream,
                                          final String messageId) {
        return Flux.defer(() -> {
            final var state = new StreamState(messageId);
            return Flux.concat(
                    Flux.just(new SseEvent.Start(messageId), new SseEvent.StartStep()),
                    stream.concatMap(response -> Flux.fromIterable(state.eventsFor(response)))
            );
        });
    }

    private static final class StreamState {

        private final String id;
        private final String reasoningId;
        private boolean textBlockOpen;
        private boolean reasoningOpen;
        private String reasoningAccumulated = "";

        private StreamState(final String id) {
            this.id = id;
            this.reasoningId = "reasoning-" + id;
        }

        private List<SseEvent> eventsFor(final ChatResponse response) {
            final var events = new ArrayList<SseEvent>();
            final var generation = response.getResult();
            final var output = generation.getOutput();

            final var reasoning = ReasoningContent.extract(output);
            final var hasReasoning = Objects.nonNull(reasoning) && !reasoning.isBlank();
            final var text = output.getText();
            final var hasText = !hasReasoning && Objects.nonNull(text) && !text.isBlank();
            final var hasToolCalls = !output.getToolCalls().isEmpty();
            final var finishReason = generation.getMetadata().getFinishReason();
            final var hasFinish = Objects.nonNull(finishReason) && !finishReason.isEmpty();

            if (hasReasoning) {
                if (!reasoningOpen) {
                    events.add(new SseEvent.ReasoningStart(reasoningId));
                    reasoningOpen = true;
                }
                final var delta = reasoningDelta(reasoning);
                if (!delta.isEmpty()) {
                    events.add(new SseEvent.ReasoningDelta(reasoningId, delta));
                }
            } else if (reasoningOpen && (hasText || hasToolCalls || hasFinish)) {
                events.add(new SseEvent.ReasoningEnd(reasoningId));
                reasoningOpen = false;
            }

            if (hasText) {
                if (!textBlockOpen) {
                    events.add(new SseEvent.TextStart(id));
                    textBlockOpen = true;
                }
                events.add(new SseEvent.TextDelta(id, text));
            }

            for (final var toolCall : output.getToolCalls()) {
                events.add(new SseEvent.ToolInputStart(toolCall.id(), toolCall.name()));
                events.add(new SseEvent.ToolInputAvailable(toolCall.id(), toolCall.name(), toolCall.arguments()));
            }

            if (hasFinish) {
                if (reasoningOpen) {
                    events.add(new SseEvent.ReasoningEnd(reasoningId));
                    reasoningOpen = false;
                }
                if (textBlockOpen) {
                    events.add(new SseEvent.TextEnd(id));
                    textBlockOpen = false;
                }
                events.add(new SseEvent.FinishStep());
                final var usage = toUsage(response.getMetadata());
                events.add(new SseEvent.Finish(ChatResponseConverter.toFinishReason(finishReason), usage));
            }

            return List.copyOf(events);
        }

        /**
         * Converts a chunk's reasoning into a delta: a cumulative chunk (starts with what was
         * already emitted) yields only the new suffix, a pure-delta chunk is returned as-is.
         */
        private String reasoningDelta(final String reasoning) {
            if (reasoning.startsWith(reasoningAccumulated)) {
                final var delta = reasoning.substring(reasoningAccumulated.length());
                reasoningAccumulated = reasoning;
                return delta;
            }
            reasoningAccumulated = reasoningAccumulated + reasoning;
            return reasoning;
        }

        private static Usage toUsage(final ChatResponseMetadata metadata) {
            if (Objects.isNull(metadata) || Objects.isNull(metadata.getUsage())) {
                return Usage.empty();
            }
            return ChatResponseConverter.toUsage(metadata.getUsage());
        }
    }
}
