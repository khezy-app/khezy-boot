package io.github.khezyapp.api.audit.api;


import io.github.khezyapp.api.audit.CheckTypes;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Strategy interface for masking sensitive data within different types of payloads.
 */
public interface SensitiveMaskerStrategy {

    /**
     * Determines if this strategy can handle the given payload.
     *
     * @param payload the object to check
     * @return true if compatible
     */
    boolean supports(Object payload);

    /**
     * Executes the masking logic on the payload.
     *
     * @param payload the object to process
     * @param context the current masking context tracking visited objects
     * @return the masked object
     */
    Object mask(Object payload, SensitiveMaskerContext context);

    /**
     * Returns the execution order of this masking strategy.
     * <p>
     * Strategies are evaluated in ascending order. Lower values indicate higher
     * priority, allowing custom implementations to intercept specific types
     * before default fallback strategies (like the Bean strategy) are applied.
     * </p>
     *
     * @return the order value, defaults to {@code 0}
     */
    default int getOrder() {
        return 0;
    }

    default boolean isArray(final Object payload) {
        return Optional.ofNullable(payload)
                .map(Object::getClass)
                .map(Class::isArray)
                .orElse(false);
    }

    default boolean isCollection(final Object payload) {
        return payload instanceof Collection;
    }

    default boolean isMap(final Object payload) {
        return payload instanceof Map;
    }

    default boolean isPrimitive(final Class<?> clz) {
        return CheckTypes.isPrimitive(clz);
    }
}
