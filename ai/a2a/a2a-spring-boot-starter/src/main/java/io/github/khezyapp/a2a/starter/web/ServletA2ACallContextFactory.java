package io.github.khezyapp.a2a.starter.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.server.auth.User;
import org.jspecify.annotations.Nullable;

import io.github.khezyapp.a2a.core.server.A2ACallContextFactory;
import io.github.khezyapp.a2a.starter.A2AServerProperties;

/**
 * Servlet flavour of {@link A2ACallContextFactory}.
 *
 * <p>Headers, extensions and protocol version come from the HTTP request. The tenant and
 * the application-owned agent scope are resolved from configurable headers (both disabled
 * by default). Identity is delegated to a user resolver so this class can load without
 * Spring Security on the classpath; {@link SpringSecurityUserResolver} provides the
 * security-backed implementation.</p>
 */
public class ServletA2ACallContextFactory extends A2ACallContextFactory<HttpServletRequest> {

    private final String tenantHeader;
    private final String scopeHeader;
    private final Function<HttpServletRequest, Optional<User>> userResolver;

    /**
     * Creates a factory that treats every request as unauthenticated.
     *
     * @param properties the server properties
     */
    public ServletA2ACallContextFactory(final A2AServerProperties properties) {
        this(properties, request -> Optional.empty());
    }

    /**
     * Creates a factory.
     *
     * @param properties the server properties
     * @param userResolver resolves the caller from the servlet request
     */
    public ServletA2ACallContextFactory(final A2AServerProperties properties,
                                        final Function<HttpServletRequest, Optional<User>> userResolver) {
        final var callContext = Objects.requireNonNull(properties, "properties must not be null").getCallContext();
        this.tenantHeader = normalize(callContext.getTenantHeader());
        this.scopeHeader = normalize(callContext.getScopeHeader());
        this.userResolver = Objects.requireNonNull(userResolver, "userResolver must not be null");
    }

    @Override
    protected Optional<User> resolveUser(final HttpServletRequest request) {
        return userResolver.apply(request);
    }

    @Override
    protected @Nullable String resolveTenant(final HttpServletRequest request) {
        return header(request, tenantHeader);
    }

    @Override
    protected @Nullable String resolveScope(final HttpServletRequest request) {
        return header(request, scopeHeader);
    }

    @Override
    protected Map<String, String> extractHeaders(final HttpServletRequest request) {
        final var headers = new HashMap<String, String>();
        final var names = request.getHeaderNames();
        if (names != null) {
            names.asIterator().forEachRemaining(name -> headers.put(name, request.getHeader(name)));
        }
        return headers;
    }

    @Override
    protected String extractHeader(final HttpServletRequest request, final String name) {
        return request.getHeader(name);
    }

    @Override
    protected List<String> extractHeaderValues(final HttpServletRequest request, final String name) {
        final var values = new ArrayList<String>();
        final var headers = request.getHeaders(name);
        if (headers != null) {
            headers.asIterator().forEachRemaining(value -> addSplit(values, value));
        }
        return values;
    }

    private static void addSplit(final List<String> values, final String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (final String part : value.split(",")) {
            if (!part.isBlank()) {
                values.add(part.trim());
            }
        }
    }

    private static @Nullable String header(final HttpServletRequest request, final @Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        final var value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static @Nullable String normalize(final @Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
