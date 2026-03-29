package io.github.khezyapp.api.audit.javers.api;

import io.github.khezyapp.api.audit.model.ChangeType;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import org.javers.core.metamodel.object.GlobalId;

import java.util.List;

/**
 * A context object used during the mapping process to maintain state and provide
 * resolution helpers for Javers {@link GlobalId} references.
 * <p>
 * This interface facilitates the translation of hierarchical Javers changes into
 * flattened structures by tracking object identities and managing recursive path
 * resolution.
 * </p>
 */
public interface JaversMappingContext {

    /**
     * Retrieves the actual domain object associated with a specific Javers ID.
     *
     * @param id the global identifier to resolve
     * @return the resolved domain object, or {@code null} if not found
     */
    Object resolveObject(GlobalId id);

    /**
     * Computes the string representation of the parent path for a given object ID
     * within the audit graph.
     *
     * @param id the global identifier to resolve the path for
     * @return the resolved parent path string
     */
    String resolveParentPath(GlobalId id);

    /**
     * Flattens a complex change into a list of field-level audit changes.
     *
     * @param path    the current property path
     * @param type    the type of change (e.g., Added, Removed, Updated)
     * @param from    the previous value or state
     * @param to      the current value or state
     * @param results the list of mapped field changes to populate
     */
    void flatten(String path, ChangeType type, Object from, Object to, List<EntityFieldChange> results);
}
