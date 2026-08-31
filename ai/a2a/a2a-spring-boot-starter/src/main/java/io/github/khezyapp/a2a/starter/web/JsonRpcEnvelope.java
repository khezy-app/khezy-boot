package io.github.khezyapp.a2a.starter.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.khezyapp.a2a.core.error.TaskNotCancelableException;
import io.github.khezyapp.a2a.core.error.TaskNotFoundException;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TaskNotFoundError;

import java.util.Map;

/**
 * JSON-RPC 2.0 envelope helpers. The SDK's {@code SendMessageResponse} /
 * {@code GetTaskResponse} / {@code CancelTaskResponse} wrappers carry no Jackson creator
 * metadata and their error type is a {@code RuntimeException} (noisy to serialize), so
 * the web layer mirrors their exact wire shape ({@code jsonrpc}/{@code id}/{@code result}
 * /{@code error}) with its own records instead.
 */
public final class JsonRpcEnvelope {

    /** JSON-RPC protocol version stamped onto every envelope. */
    public static final String JSONRPC_VERSION = "2.0";

    private JsonRpcEnvelope() {
    }

    /**
     * JSON-RPC response envelope: either {@code result} or {@code error} is present,
     * never both. Null members are omitted from the serialized body.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Envelope(String jsonrpc, Object id, Object result, ErrorPayload error) {
    }

    /** JSON-RPC {@code error} object: spec code, human-readable message, optional details. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorPayload(Integer code, String message, Map<String, Object> details) {
    }

    public static Envelope success(final Object id, final Object result) {
        return new Envelope(JSONRPC_VERSION, id, result, null);
    }

    public static Envelope failure(final Object id, final Throwable failure) {
        return new Envelope(JSONRPC_VERSION, id, null, payload(toA2AError(failure)));
    }

    /**
     * Maps the core exception taxonomy onto SDK spec errors so envelopes reuse the
     * canonical JSON-RPC codes (-32001 task not found, -32002 not cancelable,
     * -32603 internal). Unknown throwables degrade to {@link InternalError}.
     */
    public static A2AError toA2AError(final Throwable failure) {
        if (failure instanceof final TaskNotFoundException notFound) {
            return new TaskNotFoundError(null, Map.of("taskId", notFound.getTaskId()));
        }
        if (failure instanceof final TaskNotCancelableException notCancelable) {
            return new TaskNotCancelableError(null, null, Map.of("taskId", notCancelable.getTaskId()));
        }
        if (failure instanceof final A2AError a2aError) {
            return a2aError;
        }
        return new InternalError(failure.getMessage());
    }

    private static ErrorPayload payload(final A2AError error) {
        return new ErrorPayload(error.getCode(), error.getMessage(), error.getDetails());
    }
}
