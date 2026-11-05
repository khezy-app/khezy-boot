package io.github.khezyapp.a2a.starter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.khezyapp.a2a.core.server.A2ACallContextFactory;

/**
 * JSON-RPC and SSE endpoint for the A2A protocol.
 *
 * <p>The controller does not map individual A2A methods: the SDK
 * {@code JSONRPCHandler} routes them. Both {@code application/json} and the SDK 1.4
 * {@code application/a2a+json} content types are accepted. Blocking calls are written as
 * JSON; streaming calls are written as server-sent events by the transport adapter.</p>
 */
@RestController
@RequestMapping("${io.github.khezyapp.a2a.server.path:/a2a}")
public class A2AJsonRpcController {

    /** SDK 1.4 A2A JSON media type. */
    public static final String A2A_JSON_VALUE = "application/a2a+json";

    private final ServletA2AServerJsonRpcHandler handler;
    private final A2ACallContextFactory<HttpServletRequest> callContextFactory;

    /**
     * Creates the controller.
     *
     * @param handler the servlet JSON-RPC adapter
     * @param callContextFactory the call-context factory
     */
    public A2AJsonRpcController(final ServletA2AServerJsonRpcHandler handler,
                                final A2ACallContextFactory<HttpServletRequest> callContextFactory) {
        this.handler = handler;
        this.callContextFactory = callContextFactory;
    }

    /**
     * Handles one JSON-RPC request.
     *
     * @param body the raw JSON-RPC body
     * @param request the servlet request
     * @param response the servlet response
     */
    @PostMapping(path = {"", "/"}, consumes = {MediaType.APPLICATION_JSON_VALUE, A2A_JSON_VALUE})
    public void handle(@RequestBody final String body, final HttpServletRequest request,
                       final HttpServletResponse response) {
        handler.handle(body, response, callContextFactory.build(request));
    }
}
