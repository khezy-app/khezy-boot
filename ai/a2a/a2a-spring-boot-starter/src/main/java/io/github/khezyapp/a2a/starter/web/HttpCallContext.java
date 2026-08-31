package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.dispatch.CallContext;
import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import io.github.khezyapp.a2a.core.identity.CallerIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable transport-level view of one HTTP call: the resolved caller identity plus
 * a read-only snapshot of request attributes. Built per request by the controllers via
 * {@link #from(HttpServletRequest, CallerIdentityResolver)}.
 */
public final class HttpCallContext implements CallContext {

    private final CallerIdentity caller;
    private final Map<String, Object> attributes;

    public HttpCallContext(final CallerIdentity caller, final Map<String, Object> attributes) {
        this.caller = Objects.requireNonNull(caller, "caller");
        this.attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }

    @Override
    public CallerIdentity caller() {
        return caller;
    }

    @Override
    public Map<String, Object> attributes() {
        return attributes;
    }

    /** Seeds attributes with {@code requestUri}, {@code method} and (when present) {@code remoteAddr}. */
    public static HttpCallContext from(final HttpServletRequest request, final CallerIdentityResolver resolver) {
        Objects.requireNonNull(request, "request");
        final var attributes = new LinkedHashMap<String, Object>();
        attributes.put("requestUri", request.getRequestURI());
        attributes.put("method", request.getMethod());
        final var remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isBlank()) {
            attributes.put("remoteAddr", remoteAddr);
        }
        return new HttpCallContext(resolver.resolve(), attributes);
    }
}
