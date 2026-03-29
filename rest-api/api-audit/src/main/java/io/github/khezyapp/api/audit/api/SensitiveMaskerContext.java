package io.github.khezyapp.api.audit.api;

/**
 * Contextual object used during a single masking operation to handle recursion and circular references.
 */
public interface SensitiveMaskerContext {

    void registerVisited(Object key,
                         Object value);

    /**
     * Processes a payload through the internal strategy while checking for circular references.
     *
     * @param payload the object to process
     * @return the masked result
     */
    Object processMask(Object payload);
}
