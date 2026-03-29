package io.github.khezyapp.api.audit.javers.strategy;

import io.github.khezyapp.api.audit.javers.api.ChangeMappingStrategy;
import io.github.khezyapp.api.audit.javers.api.JaversMappingContext;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import org.javers.core.diff.Change;
import org.javers.core.diff.changetype.map.MapChange;

import java.util.ArrayList;
import java.util.List;

import static io.github.khezyapp.api.audit.model.ChangeType.*;

/**
 * Implementation of {@link ChangeMappingStrategy} for processing Javers {@link MapChange} types.
 * <p>
 * This strategy decomposes map-based changes—including added entries, removed entries,
 * and updated values—into individual field-level records by appending the map key
 * to the property path and delegating to the {@link JaversMappingContext} for flattening.
 * </p>
 */
public class MapChangeStrategy implements ChangeMappingStrategy {

    /**
     * Determines if the change is an instance of {@link MapChange}.
     *
     * @param change the Javers change to evaluate
     * @return {@code true} if the change is a map-based change; {@code false} otherwise
     */
    @Override
    public boolean supports(final Change change) {
        return change instanceof MapChange<?>;
    }

    /**
     * Maps specific map entry changes into flattened {@link EntityFieldChange} records.
     * <p>
     * It handles three distinct types of entry changes:
     * <ul>
     * <li><b>Added:</b> New keys introduced to the map.</li>
     * <li><b>Removed:</b> Existing keys deleted from the map.</li>
     * <li><b>Value Changes:</b> Updates to values for existing keys.</li>
     * </ul>
     * </p>
     *
     * @param change  the Javers map change
     * @param context the mapping context for path management and flattening
     * @return a list of field-level changes for each modified map entry
     */
    @Override
    public List<EntityFieldChange> map(final Change change,
                                       final JaversMappingContext context) {
        final var mc = (MapChange<?>) change;
        final var results = new ArrayList<EntityFieldChange>();
        final var propertyPath = mc.getPropertyNameWithPath();

        for (final var added : mc.getEntryAddedChanges()) {
            context.flatten(propertyPath + "." + added.getKey(), ADDED, null, added.getValue(), results);
        }
        for (final var removed : mc.getEntryRemovedChanges()) {
            context.flatten(propertyPath + "." + removed.getKey(), REMOVED, removed.getValue(), null, results);
        }
        for (final var valueChange : mc.getEntryValueChanges()) {
            context.flatten(propertyPath + "." + valueChange.getKey(), VALUE_CHANGES,
                    valueChange.getLeftValue(), valueChange.getRightValue(), results);
        }

        return results;
    }
}
