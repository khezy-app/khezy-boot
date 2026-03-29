package io.github.khezyapp.api.audit.javers.api;

import io.github.khezyapp.api.audit.model.EntityFieldChange;
import org.javers.core.diff.Change;

import java.util.List;

/**
 * Strategy interface for mapping a Javers {@link Change} into a list of
 * {@link EntityFieldChange} objects.
 * <p>
 * Implementations are responsible for flattening complex data structures
 * or hierarchical changes into a root-level path representation.
 * </p>
 */
public interface ChangeMappingStrategy {

    /**
     * Determines whether this strategy can handle the given Javers change.
     *
     * @param change the Javers change to evaluate
     * @return {@code true} if this strategy supports the change type; {@code false} otherwise
     */
    boolean supports(Change change);

    /**
     * Maps a Javers change into a flattened list of field-level changes.
     *
     * @param change  the Javers change to be mapped
     * @param context the current mapping context for state and path management
     * @return a list of mapped {@link EntityFieldChange} instances
     */
    List<EntityFieldChange> map(Change change, JaversMappingContext context);
}
