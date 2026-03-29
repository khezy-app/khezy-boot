package io.github.khezyapp.api.audit.api;

/**
 * Main entry point for the masking utility. Encapsulates the context and strategy execution.
 */
public interface SensitiveMasker {

    /**
     * Processes a payload to mask sensitive information based on configured strategies.
     *
     * @param payload the source object
     * @return a new object with sensitive data masked
     */
    Object mask(Object payload);

}
