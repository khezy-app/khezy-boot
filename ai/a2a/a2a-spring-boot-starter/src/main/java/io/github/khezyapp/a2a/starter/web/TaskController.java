package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.core.error.TaskNotFoundException;
import io.github.khezyapp.a2a.core.identity.CallerIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.spec.MethodNotFoundError;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/** JSON-RPC endpoints for {@code tasks/get} and {@code tasks/cancel}. */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private static final String METHOD_GET = "tasks/get";
    private static final String METHOD_CANCEL = "tasks/cancel";

    private final A2ARequestDispatcher dispatcher;
    private final CallerIdentityResolver identityResolver;
    private final JsonRpcParamMapper paramMapper;

    TaskController(final A2ARequestDispatcher dispatcher,
                   final CallerIdentityResolver identityResolver,
                   final ObjectMapper objectMapper) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.identityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
        this.paramMapper = new JsonRpcParamMapper(Objects.requireNonNull(objectMapper, "objectMapper"));
    }

    @PostMapping(path = "/get", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonRpcEnvelope.Envelope> getTask(final HttpServletRequest request,
                                                            @RequestBody final JsonNode body) {
        final var requestId = body.path("id");
        if (!METHOD_GET.equals(body.path("method").asString())) {
            return ResponseEntity.ok(JsonRpcEnvelope.failure(requestId, new MethodNotFoundError()));
        }
        try {
            final var query = paramMapper.taskQueryParams(body.path("params"));
            final var found = dispatcher.onGetTask(query, HttpCallContext.from(request, identityResolver));
            return found.map(task -> ResponseEntity.ok(JsonRpcEnvelope.success(requestId, task)))
                    .orElseGet(() ->
                            ResponseEntity.ok(
                                    JsonRpcEnvelope.failure(requestId, new TaskNotFoundException(query.id()))
                            )
                    );
        } catch (final Exception e) {
            return ResponseEntity.ok(JsonRpcEnvelope.failure(requestId, e));
        }
    }

    @PostMapping(path = "/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonRpcEnvelope.Envelope> cancelTask(final HttpServletRequest request,
                                                               @RequestBody final JsonNode body) {
        final var requestId = body.path("id");
        if (!METHOD_CANCEL.equals(body.path("method").asString())) {
            return ResponseEntity.ok(JsonRpcEnvelope.failure(requestId, new MethodNotFoundError()));
        }
        try {
            final var params = paramMapper.cancelTaskParams(body.path("params"));
            final var task = dispatcher.onCancelTask(params, HttpCallContext.from(request, identityResolver));
            return ResponseEntity.ok(JsonRpcEnvelope.success(requestId, task));
        } catch (final Exception e) {
            return ResponseEntity.ok(JsonRpcEnvelope.failure(requestId, e));
        }
    }
}
