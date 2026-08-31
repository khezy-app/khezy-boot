package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskState;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridges the callback-based {@link AgentEventSink} onto an {@link SseEmitter}: every
 * event becomes one JSON-RPC-shaped SSE data frame ({@code jsonrpc}/{@code id}/
 * {@code result}), mirroring the SDK's streaming response frames. Intermediate events
 * carry a synthetic result object ({@code kind} discriminator + payload); completion
 * and failure frames close the stream. Dispatch is synchronous for v1 — the emitter
 * is completed before the controller returns.
 */
final class SseAgentEventSink implements AgentEventSink {

    private static final String KIND_STATUS_UPDATE = "status-update";
    private static final String KIND_ARTIFACT_UPDATE = "artifact-update";
    private static final String KIND_CHUNK = "chunk";

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final Object requestId;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    SseAgentEventSink(final SseEmitter emitter,
                      final ObjectMapper objectMapper,
                      final Object requestId) {
        this.emitter = Objects.requireNonNull(emitter, "emitter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.requestId = requestId;
    }

    @Override
    public void statusChanged(final TaskState state,
                              final String message) {
        final var payload = new LinkedHashMap<String, Object>();
        payload.put("kind", KIND_STATUS_UPDATE);
        payload.put("state", state);
        payload.put("message", message);
        sendFrame(JsonRpcEnvelope.success(requestId, payload));
    }

    @Override
    public void artifactAdded(final Artifact artifact) {
        sendFrame(JsonRpcEnvelope.success(requestId, framePayload(KIND_ARTIFACT_UPDATE, artifact)));
    }

    @Override
    public void chunk(final Part delta) {
        sendFrame(JsonRpcEnvelope.success(requestId, framePayload(KIND_CHUNK, delta)));
    }

    @Override
    public void completed(final EventKind result) {
        sendTerminal(JsonRpcEnvelope.success(requestId, result));
    }

    @Override
    public void failed(final A2AError error) {
        sendTerminal(
                new JsonRpcEnvelope.Envelope(JsonRpcEnvelope.JSONRPC_VERSION, requestId,
                        null,
                        new JsonRpcEnvelope.ErrorPayload(error.getCode(), error.getMessage(), error.getDetails()))
        );
    }

    /**
     * Sends one failure envelope and completes the stream (controller-side safety net).
     */
    void closeWithFailure(final Throwable failure) {
        sendTerminal(JsonRpcEnvelope.failure(requestId, failure));
    }

    /**
     * Completes the stream when the dispatcher returned without emitting a terminal event.
     */
    void finishIfOpen() {
        if (!closed.get()) {
            close();
        }
    }

    private Map<String, Object> framePayload(final String kind,
                                             final Object body) {
        final var payload = new LinkedHashMap<String, Object>();
        payload.put("kind", kind);
        payload.put("payload", body);
        return payload;
    }

    private void sendFrame(final JsonRpcEnvelope.Envelope frame) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(frame), MediaType.APPLICATION_JSON));
        } catch (final IOException | RuntimeException e) {
            closed.set(true);
        }
    }

    private void sendTerminal(final JsonRpcEnvelope.Envelope frame) {
        if (closed.getAndSet(true)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(frame), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (final IOException | RuntimeException e) {
            completeQuietly(e);
        }
    }

    private void close() {
        closed.set(true);
        try {
            emitter.complete();
        } catch (final IllegalStateException alreadyTerminated) {
            closed.set(true);
        }
    }

    private void completeQuietly(final Exception cause) {
        try {
            emitter.completeWithError(cause);
        } catch (final IllegalStateException alreadyTerminated) {
            closed.set(true);
        }
    }
}
