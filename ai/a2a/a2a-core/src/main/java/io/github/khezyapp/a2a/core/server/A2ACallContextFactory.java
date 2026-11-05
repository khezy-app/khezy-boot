package io.github.khezyapp.a2a.core.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys;

/**
 * Template method that builds an SDK {@link ServerCallContext} from a transport request.
 *
 * <p>Subclasses provide identity (and optionally tenant, scope and header access) while
 * this class owns the shared state layout: headers, tenant, agent scope, transport,
 * requested extensions and requested protocol version.</p>
 *
 * <p>The factory is Spring-independent by design so it can be reused by any transport
 * adapter.</p>
 *
 * @param <T> the transport-specific request type
 */
public abstract class A2ACallContextFactory<T> {

    /**
     * Builds a call context for the given transport request.
     *
     * @param request the transport request
     * @return the SDK call context
     */
    public final ServerCallContext build(final T request) {
        Objects.requireNonNull(request, "request must not be null");

        final var state = new LinkedHashMap<String, Object>();
        final var headers = extractHeaders(request);
        state.put(JSONRPCContextKeys.HEADERS_KEY, immutableHeaders(headers));

        final var tenant = resolveTenant(request);
        if (Objects.nonNull(tenant) && !tenant.isBlank()) {
            state.put(JSONRPCContextKeys.TENANT_KEY, tenant);
        }

        final var scope = resolveScope(request);
        if (Objects.nonNull(scope) && !scope.isBlank()) {
            state.put(A2AContextKeys.AGENT_SCOPE, scope);
        }

        state.put(ServerCallContext.TRANSPORT_KEY, TransportProtocol.JSONRPC);

        final var extensions = new LinkedHashSet<>(extractHeaderValues(request, A2AHeaders.A2A_EXTENSIONS));
        final var version = extractHeader(request, A2AHeaders.A2A_VERSION);

        return new ServerCallContext(
                resolveUser(request).orElse(UnauthenticatedUser.INSTANCE),
                state,
                extensions,
                version);
    }

    /**
     * Resolves the caller.
     *
     * @param request the transport request
     * @return the caller, or empty for an unauthenticated request
     */
    protected abstract Optional<User> resolveUser(T request);

    /**
     * Resolves the tenant, if the transport supports one.
     *
     * @param request the transport request
     * @return the tenant, or {@code null}
     */
    protected String resolveTenant(final T request) {
        return null;
    }

    /**
     * Resolves the application-owned agent scope, if the transport supports one.
     *
     * @param request the transport request
     * @return the agent scope, or {@code null}
     */
    protected String resolveScope(final T request) {
        return null;
    }

    /**
     * Extracts request headers.
     *
     * @param request the transport request
     * @return the headers, empty when unsupported
     */
    protected Map<String, String> extractHeaders(final T request) {
        return Map.of();
    }

    /**
     * Extracts a single header value.
     *
     * @param request the transport request
     * @param name the header name
     * @return the header value, or {@code null}
     */
    protected String extractHeader(final T request,
                                   final String name) {
        return null;
    }

    /**
     * Extracts all values of a comma-separated header.
     *
     * <p>The default splits {@link #extractHeader(Object, String)} on commas so a
     * subclass only needs to implement header lookup.</p>
     *
     * @param request the transport request
     * @param name the header name
     * @return the header values, empty when absent
     */
    protected List<String> extractHeaderValues(final T request,
                                               final String name) {
        final var value = extractHeader(request, name);
        if (Objects.isNull(value) || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static Map<String, String> immutableHeaders(final Map<String, String> headers) {
        if (Objects.isNull(headers) || headers.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }
}
