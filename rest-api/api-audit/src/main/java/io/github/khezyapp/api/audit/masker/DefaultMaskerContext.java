package io.github.khezyapp.api.audit.masker;

import io.github.khezyapp.api.audit.api.SensitiveMaskerContext;
import io.github.khezyapp.api.audit.api.SensitiveMaskerStrategy;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Default implementation of {@link SensitiveMaskerContext} that maintains state
 * during a single masking operation.
 * <p>
 * This class uses an {@link IdentityHashMap} to track visited objects, ensuring
 * that circular references within an object graph do not cause infinite recursion
 * and that shared object references are handled consistently.
 * </p>
 */
public class DefaultMaskerContext implements SensitiveMaskerContext {
    private final SensitiveMaskerStrategy masker;
    private final Map<Object, Object> visited;

    /**
     * Constructs a new context with a specific masking strategy.
     *
     * @param masker the strategy to be used for processing payloads
     */
    public DefaultMaskerContext(final SensitiveMaskerStrategy masker) {
        this.masker = masker;
        this.visited = new IdentityHashMap<>();
    }

    /**
     * Registers an object as visited to prevent redundant processing or recursion.
     *
     * @param key   the original object being visited
     * @param value the masked or transformed representation of that object
     */
    public void registerVisited(final Object key,
                                final Object value) {
        this.visited.put(key, value);
    }

    /**
     * Processes a payload through the internal strategy while checking
     * for circular references.
     * <p>
     * If the payload has already been visited, the previously registered
     * result is returned. Otherwise, the call is delegated to the
     * {@link SensitiveMaskerStrategy}.
     * </p>
     *
     * @param payload the object to process
     * @return the masked result or the previously cached representation
     */
    public Object processMask(final Object payload) {
        if (visited.containsKey(payload)) {
            return visited.get(payload);
        }
        return masker.mask(payload, this);
    }
}
