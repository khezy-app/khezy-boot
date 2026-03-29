package io.github.khezyapp.api.audit.javers.api;

import io.github.khezyapp.api.audit.model.EntityFieldChange;
import org.javers.core.diff.Diff;

import java.util.List;

/**
 * A registry for managing and invoking {@link ChangeMappingStrategy} implementations.
 * <p>
 * This interface acts as the central coordinator for converting a Javers {@link Diff}
 * into a flattened list of {@link EntityFieldChange} objects by delegating to
 * registered strategies.
 * </p>
 */
public interface ChangeMapperRegistry {

    /**
     * Registers a new mapping strategy into the registry.
     *
     * @param strategy the strategy to be added to the registry
     */
    void register(ChangeMappingStrategy strategy);

    /**
     * Iterates through all changes in the provided diff and maps them using
     * the appropriate registered strategies.
     *
     * @param diff the Javers diff containing the changes to be mapped
     * @return a consolidated list of mapped {@link EntityFieldChange} instances
     */
    List<EntityFieldChange> mapAll(Diff diff);
}
