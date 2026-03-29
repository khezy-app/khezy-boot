package io.github.khezyapp.api.audit.javers.strategy;

import io.github.khezyapp.api.audit.javers.api.ChangeMappingStrategy;
import io.github.khezyapp.api.audit.javers.api.JaversMappingContext;
import io.github.khezyapp.api.audit.model.ChangeType;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import org.javers.core.diff.Change;
import org.javers.core.diff.changetype.container.*;
import org.javers.core.metamodel.object.GlobalId;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link ChangeMappingStrategy} specifically for handling Javers
 * {@link ContainerChange} types, such as Lists, Sets, or Arrays.
 * <p>
 * This strategy iterates through individual element changes (additions, removals, or
 * value updates) within a container and uses the {@link JaversMappingContext} to
 * flatten complex elements into individual field-level audit records.
 * </p>
 */
public class ContainerChangeStrategy implements ChangeMappingStrategy {

    /**
     * Identifies if the change is an instance of {@link ContainerChange}.
     *
     * @param change the Javers change to evaluate
     * @return {@code true} if the change is a container-based change; {@code false} otherwise
     */
    @Override
    public boolean supports(final Change change) {
        return change instanceof ContainerChange<?>;
    }

    /**
     * Maps container element changes into a flattened list of field changes.
     * <p>
     * For each element change, it constructs a precise path (including index notation)
     * and delegates to the context's flattening logic.
     * </p>
     *
     * @param change  the Javers container change
     * @param context the mapping context for object resolution and flattening
     * @return a list of {@link EntityFieldChange} representing each modified element
     */
    @Override
    public List<EntityFieldChange> map(final Change change,
                                       final JaversMappingContext context) {
        final var cc = (ContainerChange<?>) change;
        final var results = new ArrayList<EntityFieldChange>();
        final var propertyPath = cc.getPropertyNameWithPath();

        for (final var elementChange : cc.getChanges()) {
            final var elementPath = propertyPath + "[" + elementChange.getIndex() + "]";

            if (elementChange instanceof ValueAdded va) {
                final var added = resolveValue(va.getAddedValue(), context);
                context.flatten(elementPath, ChangeType.ADDED, null, added, results);
            } else if (elementChange instanceof ValueRemoved vr) {
                final var removed = resolveValue(vr.getRemovedValue(), context);
                context.flatten(elementPath, ChangeType.REMOVED, removed, null, results);
            } else if (elementChange instanceof ElementValueChange evc) {
                final var left = resolveValue(evc.getLeftValue(), context);
                final var right = resolveValue(evc.getRightValue(), context);
                context.flatten(elementPath, ChangeType.VALUE_CHANGES, left, right, results);
            }
        }

        return results;
    }

    /**
     * Resolves the element value, transforming {@link GlobalId} references into
     * actual domain objects via the context.
     *
     * @param value   the element value to resolve
     * @param context the mapping context
     * @return the resolved object or the original value if not a reference
     */
    private Object resolveValue(final Object value,
                                final JaversMappingContext context) {
        if (value instanceof GlobalId globalId) {
            return context.resolveObject(globalId);
        }
        return value;
    }
}
