package io.github.khezyapp.api.security.expression;

import jakarta.servlet.http.HttpServletRequest;
import lombok.*;

import java.util.Map;

/**
 * Context container for holding transient security-related metadata.
 * Designed to bridge the gap between HTTP request state and the security evaluation engine.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAttributeContext {
    /** The current HTTP request, providing access to headers and session data. */
    private HttpServletRequest request;

    /**
     * Map of custom attributes populated by {@link io.github.khezyapp.api.security.api.SecurityContextEnricher}
     * implementations.
     * */
    private Map<String, Object> additionalAttributes;
}
