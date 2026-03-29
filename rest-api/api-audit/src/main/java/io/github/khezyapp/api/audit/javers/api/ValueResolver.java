package io.github.khezyapp.api.audit.javers.api;

/**
 * A strategy interface for resolving and inspecting values during the
 * object graph traversal and flattening process.
 * <p>
 * This interface identifies which objects should be treated as terminal
 * nodes (leaves) and provides a mechanism to extract or transform values
 * before they are recorded in the audit log.
 * </p>
 */
public interface ValueResolver {

    /**
     * Resolves the provided value into its final representation for auditing.
     *
     * @param value the raw value to be resolved
     * @return the resolved object or transformed value
     */
    Object resolve(Object value);

    /**
     * Determines if a value is a leaf node that should not be further
     * traversed or flattened.
     *
     * @param value the value to inspect
     * @return {@code true} if the value is a terminal leaf; {@code false} otherwise
     */
    boolean isLeaf(Object value);
}
