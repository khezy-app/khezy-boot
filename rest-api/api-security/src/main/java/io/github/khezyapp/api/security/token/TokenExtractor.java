package io.github.khezyapp.api.security.token;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Strategy interface for extracting raw tokens from HTTP requests.
 * Implementations handle different token transport mechanisms
 * (e.g., Authorization header, cookies, custom headers).
 */
public interface TokenExtractor {

    /**
     * Extracts the raw token value from the given request.
     *
     * @param request the HTTP request to extract from
     * @return an {@link Optional} containing the token, or empty if not present
     */
    Optional<String> extract(HttpServletRequest request);

    /**
     * Returns the scheme this extractor handles (e.g., "Bearer", "Basic").
     *
     * @return the authentication scheme name
     */
    String scheme();
}
