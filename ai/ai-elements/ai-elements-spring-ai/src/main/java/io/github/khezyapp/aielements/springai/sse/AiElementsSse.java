package io.github.khezyapp.aielements.springai.sse;

import io.github.khezyapp.aielements.model.response.SseEvent;
import io.github.khezyapp.aielements.springai.convert.ChatResponseStreamConverter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared SSE facade for both transports (servlet {@link SseEmitter} and WebFlux
 * {@code Flux<ServerSentEvent<String>>}) over the ai-elements {@link SseEvent} model.
 *
 * <p>Both transports render each {@link SseEvent} as its {@code json()} payload and
 * append the {@code [DONE]} sentinel exactly once at the end.</p>
 */
public final class AiElementsSse {

    private static final String DONE = "[DONE]";

    private AiElementsSse() {
    }

    // ---- wire-format string helpers (shared) ----

    /**
     * Renders the full wire stream: every event's {@code data: ...} framing followed by
     * the {@code [DONE]} sentinel.
     */
    public static String toWireFormat(final List<SseEvent> events) {
        final var sb = new StringBuilder();
        for (final var event : events) {
            sb.append(event.toWireFormat());
        }
        sb.append(SseEvent.done());
        return sb.toString();
    }

    /**
     * Renders each event as a wire line ({@code data: ...} framing) without the
     * {@code [DONE]} sentinel.
     */
    public static List<String> toWireLines(final List<SseEvent> events) {
        final var lines = new ArrayList<String>(events.size());
        for (final var event : events) {
            lines.add(event.toWireFormat());
        }
        return List.copyOf(lines);
    }

    // ---- servlet (SseEmitter) ----

    /**
     * Sends a fixed {@link List} of events over the {@link SseEmitter}, appends the
     * {@code [DONE]} sentinel, then completes it.
     */
    public static SseEmitter writeTo(final SseEmitter emitter,
                                     final List<SseEvent> events) {
        try {
            for (final var event : events) {
                emitter.send(SseEmitter.event().data(event.json()));
            }
            emitter.send(SseEmitter.event().data(DONE));
            emitter.complete();
        } catch (final IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * Subscribes to a reactive stream of events, sending each over the
     * {@link SseEmitter}; appends the {@code [DONE]} sentinel before completing, and
     * completes-with-error on error.
     */
    public static SseEmitter writeTo(final SseEmitter emitter,
                                     final Flux<SseEvent> events) {
        events.subscribe(
                event -> {
                    try {
                        emitter.send(SseEmitter.event().data(event.json()));
                    } catch (final IOException e) {
                        throw new IllegalStateException("Failed to write SSE event", e);
                    }
                },
                emitter::completeWithError,
                () -> {
                    try {
                        emitter.send(SseEmitter.event().data(DONE));
                    } catch (final IOException e) {
                        emitter.completeWithError(e);
                    }
                    emitter.complete();
                });
        return emitter;
    }

    /**
     * Convenience: builds an {@link SseEmitter} for a {@code Flux<ChatResponse>} stream
     * in one call, with the given timeout.
     */
    public static SseEmitter streamToEmitter(final Flux<ChatResponse> responses,
                                             final long timeout) {
        final var emitter = new SseEmitter(timeout);
        return writeTo(emitter, ChatResponseStreamConverter.toEvents(responses));
    }

    // ---- WebFlux (Flux<ServerSentEvent<String>>) ----

    /**
     * Maps a fixed {@link List} of events to a WebFlux stream, appending the
     * {@code [DONE]} sentinel.
     */
    public static Flux<ServerSentEvent<String>> toServerSentEvents(final List<SseEvent> events) {
        return toServerSentEvents(Flux.fromIterable(events));
    }

    /**
     * Maps a reactive stream of events to a WebFlux stream, appending the
     * {@code [DONE]} sentinel.
     */
    public static Flux<ServerSentEvent<String>> toServerSentEvents(final Flux<SseEvent> events) {
        return events
                .map(event -> ServerSentEvent.builder(event.json()).build())
                .concatWithValues(ServerSentEvent.builder(DONE).build());
    }

    /**
     * Convenience: full pipeline from {@code Flux<ChatResponse>} to the typed UI-stream
     * SSE {@link Flux}, appending the {@code [DONE]} sentinel.
     */
    public static Flux<ServerSentEvent<String>> streamChat(final Flux<ChatResponse> responses) {
        return toServerSentEvents(ChatResponseStreamConverter.toEvents(responses));
    }

    // ---- required headers (both transports) ----

    /**
     * Applies the headers required for an ai-elements UI stream response.
     */
    public static void applyStreamHeaders(final HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("X-Vercel-AI-UI-Message-Stream", "v1");
        response.setContentType("text/event-stream");
    }
}
