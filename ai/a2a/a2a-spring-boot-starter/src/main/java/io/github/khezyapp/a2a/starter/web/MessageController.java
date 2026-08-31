package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.core.identity.CallerIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.spec.MethodNotFoundError;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * JSON-RPC endpoints for {@code message/send} and {@code message/stream}. The two
 * protocol methods live on separate HTTP routes so each handler keeps a precise
 * return type; the JSON-RPC {@code method} field is still validated against the route.
 */
@RestController
@RequestMapping("/message")
public class MessageController {

    private static final String METHOD_SEND = "message/send";
    private static final String METHOD_STREAM = "message/stream";

    private final A2ARequestDispatcher dispatcher;
    private final CallerIdentityResolver identityResolver;
    private final ObjectMapper objectMapper;
    private final JsonRpcParamMapper paramMapper;

    MessageController(final A2ARequestDispatcher dispatcher,
                      final CallerIdentityResolver identityResolver,
                      final ObjectMapper objectMapper) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.identityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.paramMapper = new JsonRpcParamMapper(objectMapper);
    }

    @PostMapping(value = "/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonRpcEnvelope.Envelope> send(final HttpServletRequest request,
                                                         @RequestBody final JsonNode body) {
        final var requestId = body.path("id");
        if (!METHOD_SEND.equals(body.path("method").asString())) {
            return ResponseEntity.ok(JsonRpcEnvelope.failure(requestId, new MethodNotFoundError()));
        }
        try {
            final var params = paramMapper.messageSendParams(body.path("params"));
            final var result = dispatcher.onMessageSend(params, HttpCallContext.from(request, identityResolver));
            return ResponseEntity.ok(JsonRpcEnvelope.success(requestId, result));
        } catch (final Exception e) {
            return ResponseEntity.ok(JsonRpcEnvelope.failure(requestId, e));
        }
    }

    @PostMapping(value = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(final HttpServletRequest request,
                             @RequestBody final JsonNode body) {
        final var requestId = body.path("id");
        final var emitter = new SseEmitter(0L);
        final var sink = new SseAgentEventSink(emitter, objectMapper, requestId);
        if (!METHOD_STREAM.equals(body.path("method").asString())) {
            sink.closeWithFailure(new MethodNotFoundError());
            return emitter;
        }
        try {
            final var params = paramMapper.messageSendParams(body.path("params"));
            dispatcher.onMessageStream(params, sink, HttpCallContext.from(request, identityResolver));
            sink.finishIfOpen();
        } catch (final Exception e) {
            sink.closeWithFailure(e);
        }
        return emitter;
    }
}
