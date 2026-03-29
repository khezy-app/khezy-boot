package io.github.khezyapp.api.audit.javers;

import io.github.khezyapp.api.audit.javers.api.ChangeMapperRegistry;
import io.github.khezyapp.api.audit.javers.api.ChangeMappingStrategy;
import io.github.khezyapp.api.audit.javers.api.ValueResolver;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import lombok.Getter;
import org.javers.core.diff.Diff;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A composite implementation of {@link ChangeMapperRegistry} that orchestrates
 * multiple mapping strategies to process a Javers {@link Diff}.
 * <p>
 * This class coordinates the transformation of structural changes into a flattened
 * list of field-level changes. It utilizes a {@link ValueResolver} to initialize
 * the mapping context and ensures that mapped changes are sorted by property path
 * for consistent audit logs.
 * </p>
 */
public class CompositeChangeMapper implements ChangeMapperRegistry {
    private final List<ChangeMappingStrategy> strategies = new ArrayList<>();
    @Getter
    private final ValueResolver valueResolver;

    /**
     * Constructs a new mapper with a specific value resolution strategy.
     *
     * @param valueResolver the resolver used for masking and leaf identification
     * within the mapping context
     */
    public CompositeChangeMapper(final ValueResolver valueResolver) {
        this.valueResolver = valueResolver;
    }

    /**
     * Adds a mapping strategy to the internal registry.
     *
     * @param strategy the strategy to be registered for handling specific Javers changes
     */
    @Override
    public void register(final ChangeMappingStrategy strategy) {
        strategies.add(strategy);
    }

    /**
     * Processes all changes in a Javers diff by finding and applying the
     * first supporting strategy for each change.
     * <p>
     * This method initializes a {@link DefaultJaversMappingContext}, aggregates
     * all resulting {@link EntityFieldChange} objects, and returns them sorted
     * alphabetically by their property path.
     * </p>
     *
     * @param diff the Javers diff containing the raw changes to be mapped
     * @return a sorted list of flattened and resolved field-level changes
     */
    @Override
    public List<EntityFieldChange> mapAll(final Diff diff) {
        final var context = new DefaultJaversMappingContext(diff, valueResolver);
        final var allChanges = new ArrayList<EntityFieldChange>();

        for (final var change : diff.getChanges()) {
            for (final var strategy : strategies) {
                if (strategy.supports(change)) {
                    allChanges.addAll(strategy.map(change, context));
                    break;
                }
            }
        }

        return allChanges.stream()
                .sorted(Comparator.comparing(EntityFieldChange::getProperty))
                .collect(Collectors.toList());
    }
}
