package io.github.khezyapp.api.security.token.extractor;

import io.github.khezyapp.api.security.token.TokenExtractor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link TokenExtractor} implementation that extracts Bearer tokens
 * from the {@code Authorization} header.
 */
public class BearerTokenExtractor implements TokenExtractor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Optional<String> extract(final HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        final var authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (Objects.nonNull(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            final var token = authorization.substring(BEARER_PREFIX.length()).strip();
            if (!token.isEmpty()) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    @Override
    public String scheme() {
        return "Bearer";
    }
}
