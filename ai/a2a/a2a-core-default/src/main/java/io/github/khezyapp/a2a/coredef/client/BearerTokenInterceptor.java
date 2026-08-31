package io.github.khezyapp.a2a.coredef.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.spec.AgentCard;

/**
 * Adds {@code Authorization: Bearer <token>} to outgoing requests, resolving the token
 * fresh from the given supplier on every call so short-lived tokens keep working. A
 * per-call Authorization header supplied through {@link io.github.khezyapp.a2a.core.client.CallContext}
 * always wins, which lets callers propagate their own identity over the service token.
 * The interceptor performs no token issuance, caching or refresh scheduling — point the
 * supplier at whatever mints your tokens (an OAuth2 client credentials provider, a
 * cache, a test constant).
 */
public final class BearerTokenInterceptor extends ClientCallInterceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final Supplier<String> tokenSupplier;

    public BearerTokenInterceptor(final Supplier<String> tokenSupplier) {
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier");
    }

    @Override
    public PayloadAndHeaders intercept(final String methodName,
                                       final Object payload,
                                       final Map<String, String> headers,
                                       final AgentCard agentCard,
                                       final ClientCallContext clientCallContext) {
        final var current = Objects.requireNonNullElse(headers, Map.<String, String>of());
        if (current.containsKey(AUTHORIZATION)) {
            return new PayloadAndHeaders(payload, current);
        }
        final var token = Objects.requireNonNullElse(tokenSupplier.get(), "");
        if (token.isBlank()) {
            return new PayloadAndHeaders(payload, current);
        }
        final var withAuth = new HashMap<>(current);
        withAuth.put(AUTHORIZATION, BEARER_PREFIX + token);
        return new PayloadAndHeaders(payload, withAuth);
    }
}
