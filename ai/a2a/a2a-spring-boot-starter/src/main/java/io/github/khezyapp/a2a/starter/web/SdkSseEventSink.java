package io.github.khezyapp.a2a.starter.web;

import com.google.protobuf.MessageOrBuilder;
import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridges the callback-based {@link AgentEventSink} onto an {@link SseEmitter} using the
 * SDK's protobuf-JSON streaming wire format: every callback becomes one JSON-RPC envelope
 * {@code data:} frame whose {@code result} is a proto {@code StreamResponse} (oneof
 * {@code message}/{@code task}/{@code statusUpdate}/{@code artifactUpdate}). Built with
 * {@link JSONRPCUtils}/{@link ProtoUtils} so the SDK's own {@code SSEEventListener} can
 * parse the frames byte-for-byte.
 */
final class SdkSseEventSink implements AgentEventSink {

    private final SseEmitter emitter;
    private final Object requestId;
    private final String taskId;
    private final String contextId;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    SdkSseEventSink(final SseEmitter emitter,
                    final Object requestId,
                    final String taskId,
                    final String contextId) {
        this.emitter = Objects.requireNonNull(emitter, "emitter");
        this.requestId = requestId;
        this.taskId = taskId;
        this.contextId = contextId;
    }

    @Override
    public void statusChanged(final TaskState state,
                              final String message) {
        final var event = new TaskStatusUpdateEvent(taskId, new TaskStatus(state), contextId, Map.of());
        send(ProtoUtils.ToProto.taskOrMessageStream(event), false);
    }

    @Override
    public void artifactAdded(final Artifact artifact) {
        final var event = TaskArtifactUpdateEvent.builder()
                .taskId(taskId)
                .contextId(contextId)
                .artifact(artifact)
                .build();
        send(ProtoUtils.ToProto.taskOrMessageStream(event), false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void chunk(final Part delta) {
        final var artifact = Artifact.builder()
                .artifactId(UUID.randomUUID().toString())
                .parts(List.of(delta))
                .build();
        final var event = TaskArtifactUpdateEvent.builder()
                .taskId(taskId)
                .contextId(contextId)
                .artifact(artifact)
                .build();
        send(ProtoUtils.ToProto.taskOrMessageStream(event), false);
    }

    @Override
    public void completed(final EventKind result) {
        send(ProtoUtils.ToProto.taskOrMessageStream((StreamingEventKind) result), true);
    }

    @Override
    public void failed(final A2AError error) {
        sendError(error, true);
    }

    /**
     * Completes the stream when the dispatcher returned without emitting a terminal event.
     */
    void finishIfOpen() {
        if (!closed.get()) {
            closed.set(true);
            try {
                emitter.complete();
            } catch (final IllegalStateException alreadyTerminated) {
                closed.set(true);
            }
        }
    }

    /**
     * Sends one JSON-RPC error envelope and completes the stream (controller-side safety net).
     */
    void closeWithFailure(final Throwable failure) {
        final var error = JsonRpcEnvelope.toA2AError(failure);
        sendError(error, true);
    }

    private void send(final MessageOrBuilder streamResponse,
                      final boolean terminal) {
        if (closed.get()) {
            return;
        }
        try {
            final var frame = JSONRPCUtils.toJsonRPCResultResponse(requestId, streamResponse);
            emitter.send(SseEmitter.event().data(frame, MediaType.APPLICATION_JSON));
            if (terminal) {
                closed.set(true);
                emitter.complete();
            }
        } catch (final IOException | RuntimeException e) {
            closed.set(true);
        }
    }

    private void sendError(final A2AError error,
                           final boolean terminal) {
        if (closed.get()) {
            return;
        }
        try {
            final var frame = JSONRPCUtils.toJsonRPCErrorResponse(requestId, error);
            emitter.send(SseEmitter.event().data(frame, MediaType.APPLICATION_JSON));
            if (terminal) {
                closed.set(true);
                emitter.complete();
            }
        } catch (final IOException | RuntimeException e) {
            closed.set(true);
        }
    }
}
