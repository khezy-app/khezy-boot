package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.core.identity.CallerIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.json.InvalidParamsJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.MethodNotFoundJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageRequest;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MethodNotFoundError;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskNotFoundError;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Objects;

/**
 * The A2A protocol's single JSON-RPC endpoint at {@code POST /}. This is the endpoint the
 * SDK's {@code JSONRPCTransport} posts to (the JSON-RPC {@code method} field discriminates
 * {@code SendMessage}/{@code SendStreamingMessage}/{@code GetTask}/{@code CancelTask}), so
 * it speaks the SDK's protobuf-JSON wire format via {@link JSONRPCUtils}/{@link ProtoUtils}.
 * The REST-style path controllers under {@code /message} and {@code /tasks} remain for the
 * legacy v0.3-style routes.
 */
@RestController
public class SdkA2AJsonRpcController {

    private final A2ARequestDispatcher dispatcher;
    private final CallerIdentityResolver identityResolver;

    SdkA2AJsonRpcController(final A2ARequestDispatcher dispatcher,
                            final CallerIdentityResolver identityResolver) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.identityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
    }

    @PostMapping(value = "/", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> handle(final HttpServletRequest request,
                                    @RequestBody final String body) {
        Object id = null;
        try {
            final var parsed = JSONRPCUtils.parseRequestBody(body, null);
            id = parsed.getId();
            if (parsed instanceof final SendMessageRequest send) {
                return json(
                        JSONRPCUtils.toJsonRPCResultResponse(
                                id,
                                sendMessageResult(dispatcher.onMessageSend(send.getParams(), context(request)))
                        )
                );
            }
            if (parsed instanceof final SendStreamingMessageRequest stream) {
                return stream(request, id, stream);
            }
            if (parsed instanceof final GetTaskRequest getTask) {
                final var found = dispatcher.onGetTask(getTask.getParams(), context(request));
                if (found.isEmpty()) {
                    return json(
                            JSONRPCUtils.toJsonRPCErrorResponse(
                                    id,
                                    new TaskNotFoundError(null, Map.of("taskId", getTask.getParams().id()))
                            )
                    );
                }
                return json(JSONRPCUtils.toJsonRPCResultResponse(id, ProtoUtils.ToProto.task(found.get())));
            }
            if (parsed instanceof final CancelTaskRequest cancel) {
                final var params = cancel.getParams();
                final var task = dispatcher.onCancelTask(
                        new TaskIdParams(params.id(), params.tenant()), context(request));
                return json(JSONRPCUtils.toJsonRPCResultResponse(id, ProtoUtils.ToProto.task(task)));
            }
            return json(JSONRPCUtils.toJsonRPCErrorResponse(id, new MethodNotFoundError()));
        } catch (final MethodNotFoundJsonMappingException e) {
            return json(JSONRPCUtils.toJsonRPCErrorResponse(id, new MethodNotFoundError()));
        } catch (final InvalidParamsJsonMappingException e) {
            return json(JSONRPCUtils.toJsonRPCErrorResponse(id, new InvalidParamsError(e.getMessage())));
        } catch (final Exception e) {
            return json(JSONRPCUtils.toJsonRPCErrorResponse(id, JsonRpcEnvelope.toA2AError(e)));
        }
    }

    private ResponseEntity<?> stream(final HttpServletRequest request,
                                     final Object id,
                                     final SendStreamingMessageRequest stream) {
        final var message = stream.getParams().message();
        final var emitter = new SseEmitter(0L);
        final var sink = new SdkSseEventSink(emitter, id, message.taskId(), message.contextId());
        try {
            dispatcher.onMessageStream(stream.getParams(), sink, context(request));
            sink.finishIfOpen();
        } catch (final Exception e) {
            sink.closeWithFailure(e);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    private HttpCallContext context(final HttpServletRequest request) {
        return HttpCallContext.from(request, identityResolver);
    }

    private static ResponseEntity<String> json(final String envelope) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(envelope);
    }

    /**
     * Serializes a blocking {@code SendMessage} result. The SDK's {@code SendMessageResponse}
     * proto only carries a {@code message} or {@code task} oneof, so a final
     * {@link TaskStatusUpdateEvent} is lifted into a {@link Task} result first.
     */
    private static com.google.protobuf.MessageOrBuilder sendMessageResult(final EventKind result) {
        if (result instanceof final Task task) {
            return ProtoUtils.ToProto.taskOrMessage(task);
        }
        if (result instanceof final Message message) {
            return ProtoUtils.ToProto.taskOrMessage(message);
        }
        final var update = (TaskStatusUpdateEvent) result;
        final var task = Task.builder()
                .id(update.taskId())
                .contextId(update.contextId())
                .status(update.status())
                .build();
        return ProtoUtils.ToProto.taskOrMessage(task);
    }
}
