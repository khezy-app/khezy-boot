package io.github.khezyapp.a2a.starter.web;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.gson.JsonSyntaxException;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.jsonrpc.common.json.IdJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.InvalidParamsJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.MethodNotFoundJsonMappingException;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2ARequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetExtendedAgentCardRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.NonStreamingJSONRPCRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SubscribeToTaskRequest;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.a2aproject.sdk.spec.JSONParseError;
import org.a2aproject.sdk.spec.MethodNotFoundError;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport-neutral JSON-RPC adapter for the SDK {@link JSONRPCHandler}.
 *
 * <p>The SDK routes every method; this class only parses the envelope, dispatches all
 * non-streaming and streaming methods, and delegates writing to two transport hooks.
 * Request-level failures are reported through an optional {@code onRequestError} consumer
 * <em>after</em> the response or SSE frame has been written.</p>
 *
 * @param <T> the transport request type (unused by the base adapter)
 * @param <R> the transport response context
 */
public abstract class A2AServerJsonRpcHandler<T, R> {

    private static final Logger LOG = LoggerFactory.getLogger(A2AServerJsonRpcHandler.class);

    /** The SDK JSON-RPC handler that owns method routing. */
    protected final JSONRPCHandler jsonRpcHandler;

    /**
     * Creates the adapter.
     *
     * @param jsonRpcHandler the SDK JSON-RPC handler
     */
    protected A2AServerJsonRpcHandler(final JSONRPCHandler jsonRpcHandler) {
        this.jsonRpcHandler = Objects.requireNonNull(jsonRpcHandler, "jsonRpcHandler must not be null");
    }

    /**
     * Handles one JSON-RPC body.
     *
     * @param body the raw request body
     * @param responseContext the transport response context
     * @param callContext the SDK call context
     */
    public void handle(final String body, final R responseContext, final ServerCallContext callContext) {
        handle(body, responseContext, callContext, null);
    }

    /**
     * Handles one JSON-RPC body and reports request-level errors after the response is written.
     *
     * @param body the raw request body
     * @param responseContext the transport response context
     * @param callContext the SDK call context
     * @param onRequestError called after a JSON-RPC error response has been written
     */
    public void handle(final String body, final R responseContext, final ServerCallContext callContext,
                       final Consumer<A2AError> onRequestError) {
        try {
            final var request = parseRequestBody(body, callContext);
            handle(request, responseContext, callContext, onRequestError);
        } catch (final JsonProcessingException e) {
            writeRequestError(responseContext, onRequestError, convertToA2AErrorResponse(e));
        } catch (final RuntimeException e) {
            writeRequestError(responseContext, onRequestError, convertToA2AErrorResponse(e));
        }
    }

    private void writeRequestError(final R responseContext, final Consumer<A2AError> onRequestError,
                                   final A2AErrorResponse error) {
        writeJsonResponse(responseContext, ResponseFormatter.serializeResponse(error));
        notifyRequestError(onRequestError, error.getError(), new AtomicBoolean(false));
    }

    /**
     * Handles a parsed request.
     *
     * @param request the parsed request
     * @param responseContext the transport response context
     * @param callContext the SDK call context
     */
    public void handle(final A2ARequest<?> request, final R responseContext,
                       final ServerCallContext callContext) {
        handle(request, responseContext, callContext, null);
    }

    /**
     * Handles a parsed request.
     *
     * <p>The SDK folds request failures into error responses; they are detected and
     * reported through {@code onRequestError} after the body has been flushed.</p>
     *
     * @param request the parsed request
     * @param responseContext the transport response context
     * @param callContext the SDK call context
     * @param onRequestError called after an error response has been written
     */
    public void handle(final A2ARequest<?> request, final R responseContext,
                       final ServerCallContext callContext, final Consumer<A2AError> onRequestError) {
        A2AResponse<?> nonStreamingResponse = null;
        Flow.Publisher<? extends A2AResponse<?>> streamingResponse = null;
        A2AErrorResponse error = null;
        final var requestError = new AtomicReference<A2AError>();
        final var notified = new AtomicBoolean(false);

        try {
            callContext.getState().put(JSONRPCContextKeys.METHOD_NAME_KEY, request.getMethod());
            if (request instanceof NonStreamingJSONRPCRequest<?> nonStreamingRequest) {
                nonStreamingResponse = processNonStreamingRequest(nonStreamingRequest, callContext);
                if (Objects.nonNull(nonStreamingResponse) && Objects.nonNull(nonStreamingResponse.getError())) {
                    requestError.set(nonStreamingResponse.getError());
                }
            } else {
                streamingResponse = processStreamingRequest(request, callContext);
            }
        } catch (final A2AError e) {
            error = new A2AErrorResponse(e);
            requestError.set(e);
        } catch (final JsonSyntaxException e) {
            error = new A2AErrorResponse(new JSONParseError(e.getMessage()));
            requestError.set(error.getError());
        } catch (final RuntimeException e) {
            LOG.error("Internal error while processing A2A request", e);
            error = new A2AErrorResponse(new InternalError("Internal error"));
            requestError.set(error.getError());
        } finally {
            if (Objects.nonNull(error)) {
                writeJsonResponse(responseContext, ResponseFormatter.serializeResponse(error));
            } else if (Objects.nonNull(streamingResponse)) {
                writeSse(responseContext,
                        monitorStreamingErrors(streamingResponse, requestError, onRequestError, notified));
            } else {
                writeJsonResponse(responseContext, ResponseFormatter.serializeResponse(nonStreamingResponse));
            }
            notifyRequestError(onRequestError, requestError.get(), notified);
        }
    }

    @SuppressWarnings("unchecked")
    private Flow.Publisher<? extends A2AResponse<?>> monitorStreamingErrors(
            final Flow.Publisher<? extends A2AResponse<?>> source,
            final AtomicReference<A2AError> requestError,
            final Consumer<A2AError> onRequestError,
            final AtomicBoolean notified) {
        final var typedSource = (Flow.Publisher<A2AResponse<?>>) source;
        return subscriber -> typedSource.subscribe(new Flow.Subscriber<A2AResponse<?>>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(final A2AResponse<?> item) {
                subscriber.onNext(item);
                if (Objects.nonNull(item) && Objects.nonNull(item.getError())) {
                    requestError.compareAndSet(null, item.getError());
                    notifyRequestError(onRequestError, item.getError(), notified);
                }
            }

            @Override
            public void onError(final Throwable throwable) {
                subscriber.onError(throwable);
                final A2AError a2aError = throwable instanceof A2AError candidate
                        ? candidate
                        : new InternalError("Internal error");
                requestError.compareAndSet(null, a2aError);
                notifyRequestError(onRequestError, a2aError, notified);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }

    /**
     * Writes a serialized JSON-RPC response.
     *
     * @param responseContext the transport response context
     * @param body the serialized body
     */
    protected abstract void writeJsonResponse(R responseContext, String body);

    /**
     * Writes a streaming response as server-sent events.
     *
     * @param responseContext the transport response context
     * @param publisher the response publisher
     */
    protected abstract void writeSse(R responseContext, Flow.Publisher<? extends A2AResponse<?>> publisher);

    /**
     * Dispatches a non-streaming request to the SDK handler.
     *
     * @param request the request
     * @param context the call context
     * @return the SDK response
     */
    protected A2AResponse<?> processNonStreamingRequest(final NonStreamingJSONRPCRequest<?> request,
                                                        final ServerCallContext context) {
        if (request instanceof GetTaskRequest typed) {
            return jsonRpcHandler.onGetTask(typed, context);
        }
        if (request instanceof CancelTaskRequest typed) {
            return jsonRpcHandler.onCancelTask(typed, context);
        }
        if (request instanceof ListTasksRequest typed) {
            return jsonRpcHandler.onListTasks(typed, context);
        }
        if (request instanceof CreateTaskPushNotificationConfigRequest typed) {
            return jsonRpcHandler.setPushNotificationConfig(typed, context);
        }
        if (request instanceof GetTaskPushNotificationConfigRequest typed) {
            return jsonRpcHandler.getPushNotificationConfig(typed, context);
        }
        if (request instanceof SendMessageRequest typed) {
            return jsonRpcHandler.onMessageSend(typed, context);
        }
        if (request instanceof ListTaskPushNotificationConfigsRequest typed) {
            return jsonRpcHandler.listPushNotificationConfigs(typed, context);
        }
        if (request instanceof DeleteTaskPushNotificationConfigRequest typed) {
            return jsonRpcHandler.deletePushNotificationConfig(typed, context);
        }
        if (request instanceof GetExtendedAgentCardRequest typed) {
            return jsonRpcHandler.onGetExtendedCardRequest(typed, context);
        }
        return new A2AErrorResponse(request.getId(), new UnsupportedOperationError());
    }

    /**
     * Dispatches a streaming request to the SDK handler, authorizing task access first.
     *
     * @param request the request
     * @param context the call context
     * @return the response publisher
     * @throws A2AError when task access is denied
     */
    protected Flow.Publisher<? extends A2AResponse<?>> processStreamingRequest(final A2ARequest<?> request,
                                                                               final ServerCallContext context)
            throws A2AError {
        if (request instanceof SendStreamingMessageRequest typed) {
            jsonRpcHandler.authorizeTaskAccess(typed.getParams().message().taskId(), context,
                    TaskOperation.MESSAGE_SEND_STREAM);
            return jsonRpcHandler.onMessageSendStream(typed, context);
        }
        if (request instanceof SubscribeToTaskRequest typed) {
            jsonRpcHandler.authorizeTaskAccess(typed.getParams().id(), context,
                    TaskOperation.SUBSCRIBE_TO_TASK);
            return jsonRpcHandler.onSubscribeToTask(typed, context);
        }
        return FlowSupport.fromItem(new A2AErrorResponse(request.getId(), new UnsupportedOperationError()));
    }

    /**
     * Parses a JSON-RPC body, using the tenant carried in the call context.
     *
     * @param body the raw body
     * @param context the call context, may be {@code null}
     * @return the parsed request
     * @throws JsonProcessingException when the body is not a valid request
     */
    public static A2ARequest<?> parseRequestBody(final String body, final ServerCallContext context)
            throws JsonProcessingException {
        final var tenant = Optional.ofNullable(context)
                .map(callContext -> (String) callContext.getState().get(JSONRPCContextKeys.TENANT_KEY))
                .orElse(null);
        return JSONRPCUtils.parseRequestBody(body, tenant);
    }

    /**
     * Converts a transport exception into a JSON-RPC error response.
     *
     * @param error the exception
     * @return the error response
     */
    public static A2AErrorResponse convertToA2AErrorResponse(final Throwable error) {
        if (error instanceof A2AError a2aError) {
            return new A2AErrorResponse(a2aError);
        }
        if (error instanceof InvalidParamsJsonMappingException invalidParams) {
            return new A2AErrorResponse(invalidParams.getId(), new InvalidParamsError(invalidParams.getMessage()));
        }
        if (error instanceof MethodNotFoundJsonMappingException methodNotFound) {
            return new A2AErrorResponse(methodNotFound.getId(),
                    new MethodNotFoundError(null, methodNotFound.getMessage(), null));
        }
        if (error instanceof IdJsonMappingException idError) {
            return new A2AErrorResponse(idError.getId(), new InvalidRequestError(idError.getMessage()));
        }
        if (error instanceof JsonSyntaxException syntaxError) {
            return new A2AErrorResponse(new InvalidRequestError(syntaxError.getMessage()));
        }
        if (error instanceof com.google.gson.JsonParseException parseError) {
            return new A2AErrorResponse(new InvalidRequestError(parseError.getMessage()));
        }
        if (error instanceof IllegalStateException stateError) {
            return new A2AErrorResponse(new InvalidRequestError(stateError.getMessage()));
        }
        if (error instanceof JsonMappingException mappingError) {
            return new A2AErrorResponse(new InvalidRequestError(mappingError.getMessage()));
        }
        if (error instanceof JsonProcessingException processingError) {
            return new A2AErrorResponse(new InvalidRequestError(processingError.getMessage()));
        }
        return new A2AErrorResponse(new InternalError("Internal error"));
    }

    /**
     * Serializes an SDK response as a JSON-RPC envelope.
     *
     * @param response the response
     * @return the JSON body
     */
    public static String toResponseJson(final A2AResponse<?> response) {
        return ResponseFormatter.serializeResponse(response);
    }

    private static void notifyRequestError(final Consumer<A2AError> onRequestError, final A2AError error,
                                           final AtomicBoolean notified) {
        if (Objects.nonNull(onRequestError) && Objects.nonNull(error) && notified.compareAndSet(false, true)) {
            onRequestError.accept(error);
        }
    }
}
