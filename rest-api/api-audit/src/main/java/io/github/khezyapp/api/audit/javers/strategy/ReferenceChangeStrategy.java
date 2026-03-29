package io.github.khezyapp.api.audit.javers.strategy;

import io.github.khezyapp.api.audit.javers.api.ChangeMappingStrategy;
import io.github.khezyapp.api.audit.javers.api.JaversMappingContext;
import io.github.khezyapp.api.audit.model.ChangeType;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import org.javers.core.diff.Change;
import org.javers.core.diff.changetype.ReferenceChange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link ChangeMappingStrategy} for processing Javers {@link ReferenceChange} types.
 * <p>
 * This strategy handles changes in entity-to-entity relationships. It resolves
 * the path by checking for parent context and uses the {@link JaversMappingContext}
 * to fetch the actual domain objects before flattening them into field-level changes.
 * </p>
 */
public class ReferenceChangeStrategy implements ChangeMappingStrategy {

    /**
     * Determines if the change is an instance of {@link ReferenceChange}.
     *
     * @param change the Javers change to evaluate
     * @return {@code true} if the change is a reference-based change; {@code false} otherwise
     */
    @Override
    public boolean supports(final Change change) {
        return change instanceof ReferenceChange;
    }

    /**
     * Maps a reference change between two entities into a flattened list of field changes.
     * <p>
     * It resolves the full property path by combining the parent path from the context
     * with the current property name. It then resolves the left (previous) and
     * right (current) objects using their global identifiers before performing
     * the flattening operation.
     * </p>
     *
     * @param change  the Javers reference change
     * @param context the mapping context for path and object resolution
     * @return a list of flattened {@link EntityFieldChange} records for the referenced entities
     */
    @Override
    public List<EntityFieldChange> map(final Change change,
                                       final JaversMappingContext context) {
        final var rc = (ReferenceChange) change;

        final var propertyName = rc.getPropertyNameWithPath();
        final var parentPath = context.resolveParentPath(rc.getAffectedGlobalId());
        final var fullPath = Objects.nonNull(parentPath) ? parentPath + "." + propertyName : propertyName;

        final var from = rc.getLeftObject().isPresent() ? context.resolveObject(rc.getLeft()) : null;
        final var to = rc.getRightObject().isPresent() ? context.resolveObject(rc.getRight()) : null;

        final var results  = new ArrayList<EntityFieldChange>();
        context.flatten(fullPath, ChangeType.VALUE_CHANGES, from, to, results);

        return results;
    }
}
