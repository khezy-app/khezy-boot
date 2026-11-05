package io.github.khezyapp.a2a.starter.web;

import com.google.protobuf.Empty;
import com.google.protobuf.MessageOrBuilder;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetExtendedAgentCardResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.server.util.sse.SseFormatter;

import java.util.Objects;

/**
 * Serializes SDK responses as proto-JSON JSON-RPC envelopes and SSE frames.
 *
 * <p>Jackson serialization does not produce the shape the SDK client accepts; the SDK
 * proto printers are the only correct path.</p>
 */
public final class ResponseFormatter {

    private ResponseFormatter() {
    }

    /**
     * Formats a response as one SSE frame.
     *
     * @param response the response
     * @param eventId the SSE event id
     * @return the formatted frame
     */
    public static String formatResponseAsSse(final A2AResponse<?> response, final long eventId) {
        return SseFormatter.formatResponseAsSSE(response, eventId);
    }

    /**
     * Formats an already serialized JSON body as one SSE frame.
     *
     * @param json the JSON body
     * @param eventId the SSE event id
     * @return the formatted frame
     */
    public static String formatJsonAsSse(final String json, final long eventId) {
        return SseFormatter.formatJsonAsSSE(json, eventId);
    }

    /**
     * Serializes a response as a JSON-RPC envelope.
     *
     * @param response the response
     * @return the JSON body
     */
    public static String serializeResponse(final A2AResponse<?> response) {
        if (response instanceof A2AErrorResponse error) {
            return JSONRPCUtils.toJsonRPCErrorResponse(error.getId(), error.getError());
        }
        if (Objects.nonNull(response.getError())) {
            return JSONRPCUtils.toJsonRPCErrorResponse(response.getId(), response.getError());
        }
        return JSONRPCUtils.toJsonRPCResultResponse(response.getId(), convertToProto(response));
    }

    private static MessageOrBuilder convertToProto(final A2AResponse<?> response) {
        if (response instanceof GetTaskResponse r) {
            return ProtoUtils.ToProto.task(r.getResult());
        }
        if (response instanceof CancelTaskResponse r) {
            return ProtoUtils.ToProto.task(r.getResult());
        }
        if (response instanceof SendMessageResponse r) {
            return ProtoUtils.ToProto.taskOrMessage(r.getResult());
        }
        if (response instanceof ListTasksResponse r) {
            return ProtoUtils.ToProto.listTasksResult(r.getResult());
        }
        if (response instanceof CreateTaskPushNotificationConfigResponse r) {
            return ProtoUtils.ToProto.createTaskPushNotificationConfigResponse(r.getResult());
        }
        if (response instanceof GetTaskPushNotificationConfigResponse r) {
            return ProtoUtils.ToProto.getTaskPushNotificationConfigResponse(r.getResult());
        }
        if (response instanceof ListTaskPushNotificationConfigsResponse r) {
            return ProtoUtils.ToProto.listTaskPushNotificationConfigsResponse(r.getResult());
        }
        if (response instanceof DeleteTaskPushNotificationConfigResponse) {
            return Empty.getDefaultInstance();
        }
        if (response instanceof GetExtendedAgentCardResponse r) {
            return ProtoUtils.ToProto.getExtendedCardResponse(r.getResult());
        }
        if (response instanceof SendStreamingMessageResponse r) {
            return ProtoUtils.ToProto.taskOrMessageStream(r.getResult());
        }
        throw new IllegalArgumentException("Unknown A2A response type: " + response.getClass().getName());
    }
}
