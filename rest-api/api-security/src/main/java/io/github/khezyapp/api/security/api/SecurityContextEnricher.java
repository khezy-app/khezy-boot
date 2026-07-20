package io.github.khezyapp.api.security.api;

import java.util.Map;

/**
 * Functional interface for injecting external data into the security context.
 * Used for multi-tenancy IDs, user department data, or subscription tiers.
 */
@FunctionalInterface
public interface SecurityContextEnricher {

    /**
     * @return A map of attributes to be added to
     * {@link io.github.khezyapp.api.security.expression.SecurityAttributeContext}.
     * */
    Map<String, Object> getAdditionalContext();
}
